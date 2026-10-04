package tw.tsm.tests

import io.mockk.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.gamelogic.systems.QuestSystem

class DungeonWeeklyResetTest {
    private val defs = mapOf(
        12722 to MarkDef("weekly stage", 9, 0, 0, ""),
        13328 to MarkDef("soul weekly stage", 9, 0, 0, ""),
        12762 to MarkDef("weekly all pass claim", 9, 1706, 0, ""),
        12799 to MarkDef("unrelated weekly", 9, 0, 0, ""),
        12336 to MarkDef("daily dungeon", 8, 0, 0, ""),
        13001 to MarkDef("other claim", 9, 1800, 0, ""),
    )

    private class Store {
        var week: Int? = null
        var calls = 0
        val steps = mutableMapOf(
            12722 to 1,
            13328 to 1,
            12799 to 1,
            12336 to 1,
        )
        val flags = mutableMapOf(
            1706 to 1,
            1800 to 1,
        )

        val repo = mockk<QuestRepository>().also { r ->
            coEvery { r.refreshDungeonWeek(any(), any(), any(), any()) } coAnswers {
                calls++
                val newWeek = secondArg<Int>()
                val missionIds = thirdArg<Set<Int>>()
                val claimBits = arg<Set<Int>>(3)
                yield()
                if (week != null && week!! >= newWeek) {
                    DungeonWeeklyResetResult()
                } else {
                    val removed = if (week == null) emptyList()
                    else steps.keys.filter { it in missionIds }
                    val cleared = if (week == null) emptyList()
                    else flags.keys.filter { it in claimBits }
                    removed.forEach { steps.remove(it) }
                    cleared.forEach { flags.remove(it) }
                    week = newWeek
                    DungeonWeeklyResetResult(removed, cleared)
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
        }
    }

    private fun system(
        store: Store,
        week: () -> Int,
        stageIds: Set<Int> = setOf(12722, 13328),
        claimIds: Set<Int> = setOf(12762),
    ) = QuestSystem(
        repo = store.repo,
        markDefs = defs,
        weeklyDungeonMissionIds = stageIds,
        weeklyDungeonClaimMissionIds = claimIds,
        currentWeek = week,
    )

    @Test
    fun `first weekly observation relog and restart preserve progress and claim`() = runTest {
        val store = Store()
        fun newSystem() = system(store, { 20260928 })

        assertEquals(DungeonWeeklyResetResult(), newSystem().refreshDungeonWeekly(1))
        assertEquals(1, store.steps[12722])
        assertEquals(1, store.steps[13328])
        assertEquals(1, store.flags[1706])

        assertEquals(DungeonWeeklyResetResult(), newSystem().refreshDungeonWeekly(1))
        assertEquals(1, store.calls)
        assertEquals(1, store.flags[1706])
    }

    @Test
    fun `new Monday clears only scoped weekly counters and explicit claim flag`() = runTest {
        val store = Store().also { it.week = 20260928 }
        var week = 20261005
        val s = system(store, { week })

        val reset = s.refreshDungeonWeekly(1)
        assertEquals(setOf(12722, 13328), reset.removedMissionIds.toSet())
        assertEquals(listOf(1706), reset.clearedFlagBits)
        assertEquals(setOf(12799, 12336), store.steps.keys)
        assertEquals(mapOf(1800 to 1), store.flags)

        // Clock rollback / duplicate checks cannot create another reset.
        store.steps[12722] = 1
        store.flags[1706] = 1
        week = 20260928
        assertEquals(DungeonWeeklyResetResult(), s.refreshDungeonWeekly(1))
        assertEquals(1, store.steps[12722])
        assertEquals(1, store.flags[1706])
    }

    @Test
    fun `concurrent weekly checks reset once and later progress survives`() = runTest {
        val store = Store().also { it.week = 20260928 }
        val s = system(store, { 20261005 })

        val results = List(20) { async { s.refreshDungeonWeekly(1) } }.awaitAll()
        assertEquals(2, results.sumOf { it.removedMissionIds.size })
        assertEquals(1, results.sumOf { it.clearedFlagBits.size })
        assertEquals(1, store.calls)

        assertEquals(0, s.addMissionStep(1, 12722, 1))
        assertEquals(DungeonWeeklyResetResult(), s.refreshDungeonWeekly(1))
        assertEquals(1, store.steps[12722])
    }

    @Test
    fun `week key changes exactly at Monday boundary`() {
        assertEquals(20260928, QuestSystem.dungeonWeekKey(LocalDate.of(2026, 10, 4)))
        assertEquals(20261005, QuestSystem.dungeonWeekKey(LocalDate.of(2026, 10, 5)))
        assertEquals(20261005, QuestSystem.dungeonWeekKey(LocalDate.of(2026, 10, 11)))
        assertEquals(20261012, QuestSystem.dungeonWeekKey(LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun `scope filters non kind9 counters and non explicit claim flags`() = runTest {
        val store = Store().also { it.week = 20260928 }
        val s = system(
            store,
            { 20261005 },
            stageIds = setOf(12722, 12336),
            claimIds = setOf(12762, 13001),
        )
        val reset = s.refreshDungeonWeekly(1)

        assertEquals(listOf(12722), reset.removedMissionIds)
        assertEquals(setOf(1706, 1800), reset.clearedFlagBits.toSet())
        assertTrue(store.steps.containsKey(12336))
        assertTrue(store.steps.containsKey(13328))
    }

    @Test
    fun `empty weekly scope never touches persistence`() = runTest {
        val store = Store()
        val s = QuestSystem(store.repo, defs, currentWeek = { 20261005 })
        assertEquals(DungeonWeeklyResetResult(), s.refreshDungeonWeekly(1))
        assertEquals(0, store.calls)
    }
}
