package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.gamelogic.systems.QuestSystem

class DungeonWeeklyResetTest {
    private val defs = mapOf(
        12336 to MarkDef("daily", 8, 0, 0, ""),
        12722 to MarkDef("weekly stage", 9, 0, 1, ""),
        12762 to MarkDef("weekly all clear", 9, 1706, 1, ""),
        13400 to MarkDef("other weekly legacy", 9, 0, 1, ""),
        13409 to MarkDef("other weekly flag", 9, 1935, 1, ""),
    )

    private class Store {
        var week: Int? = null
        var calls = 0
        val steps = mutableMapOf(
            12336 to 1,
            12722 to 1,
            13400 to 1,
        )
        val flags = mutableMapOf(
            1706 to 1,
            1935 to 1,
        )

        val repo = mockk<QuestRepository>().also { r ->
            coEvery { r.refreshDungeonWeek(any(), any(), any(), any()) } coAnswers {
                calls++
                val newWeek = secondArg<Int>()
                val ids = thirdArg<Set<Int>>()
                val bits = arg<Set<Int>>(3)
                yield()

                val oldWeek = week
                if (oldWeek != null && oldWeek > 0 && oldWeek >= newWeek) {
                    DungeonWeekRefreshResult()
                } else {
                    val shouldReset = oldWeek != null && oldWeek > 0 && oldWeek < newWeek
                    val removed = if (shouldReset) steps.keys.filter { it in ids } else emptyList()
                    val cleared = if (shouldReset) flags.filter { (bit, value) ->
                        bit in bits && value != 0
                    }.keys.toList() else emptyList()
                    removed.forEach { steps.remove(it) }
                    cleared.forEach { flags.remove(it) }
                    week = newWeek
                    DungeonWeekRefreshResult(removed, cleared)
                }
            }
            coEvery { r.getMissions(any()) } coAnswers {
                steps.map { MissionRecord(it.key, it.value) }
            }
            coEvery { r.getMissionFlags(any()) } coAnswers {
                flags.map { MissionFlagRecord(it.key, it.value) }
            }
            coEvery { r.upsertMission(any(), any(), any()) } coAnswers {
                steps[secondArg()] = thirdArg()
            }
            coEvery { r.upsertFlag(any(), any(), any()) } coAnswers {
                flags[secondArg()] = thirdArg()
            }
        }
    }

    private fun system(store: Store, week: () -> Int) = QuestSystem(
        repo = store.repo,
        markDefs = defs,
        weeklyDungeonMissionIds = setOf(12722, 13409, 12336),
        weeklyClaimMissionIds = setOf(12762, 13400),
        thisWeek = week,
    )

    @Test fun `first weekly observation preserves legacy progress and claim flag`() = runTest {
        val store = Store()
        val s = system(store) { 20260928 }

        assertEquals(DungeonWeekRefreshResult(), s.refreshDungeonWeekly(1))
        assertEquals(1, store.steps[12722])
        assertEquals(1, store.flags[1706])
        assertEquals(1, store.calls)

        assertEquals(DungeonWeekRefreshResult(), s.refreshDungeonWeekly(1))
        assertEquals(1, store.calls)

        // A server restart in the same week observes the persisted key and still preserves state.
        assertEquals(DungeonWeekRefreshResult(), system(store) { 20260928 }.refreshDungeonWeekly(1))
        assertEquals(1, store.steps[12722])
        assertEquals(1, store.flags[1706])
    }

    @Test fun `new Monday clears only scoped kind9 counter and explicit claim bit`() = runTest {
        val store = Store().also { it.week = 20260928 }
        val s = system(store) { 20261005 }

        val reset = s.refreshDungeonWeekly(1)
        assertEquals(listOf(12722), reset.removedMissionIds)
        assertEquals(listOf(1706), reset.clearedFlagBits)

        assertEquals(1, store.steps[12336], "daily dungeon counter must not be touched")
        assertEquals(1, store.steps[13400], "unscoped legacy weekly mark must not be swept")
        assertEquals(1, store.flags[1935], "unscoped legacy weekly flag must not be swept")
        assertFalse(store.steps.containsKey(12722))
        assertFalse(store.flags.containsKey(1706))
    }

    @Test fun `clock rollback cannot grant another weekly reset`() = runTest {
        val store = Store().also { it.week = 20261005 }
        val s = system(store) { 20260928 }

        assertEquals(DungeonWeekRefreshResult(), s.refreshDungeonWeekly(1))
        assertEquals(1, store.steps[12722])
        assertEquals(1, store.flags[1706])
    }

    @Test fun `concurrent weekly checks reset once and serialize with later mission increment`() = runTest {
        val store = Store().also { it.week = 20260928 }
        val s = system(store) { 20261005 }

        val results = List(20) { async { s.refreshDungeonWeekly(1) } }.awaitAll()
        assertEquals(1, results.sumOf { it.removedMissionIds.size })
        assertEquals(1, results.sumOf { it.clearedFlagBits.size })
        assertEquals(1, store.calls)

        assertEquals(0, s.addMissionStep(1, 12722, 1))
        assertEquals(DungeonWeekRefreshResult(), s.refreshDungeonWeekly(1))
        assertEquals(1, store.steps[12722])
    }

    @Test fun `non-kind9 ids passed by wiring are filtered before persistence`() = runTest {
        val store = Store().also { it.week = 20260928 }
        val s = QuestSystem(
            repo = store.repo,
            markDefs = defs,
            weeklyDungeonMissionIds = setOf(12336),
            weeklyClaimMissionIds = setOf(13400),
            thisWeek = { 20261005 },
        )

        assertEquals(DungeonWeekRefreshResult(), s.refreshDungeonWeekly(1))
        assertEquals(0, store.calls)
        assertEquals(1, store.steps[12336])
    }
}
