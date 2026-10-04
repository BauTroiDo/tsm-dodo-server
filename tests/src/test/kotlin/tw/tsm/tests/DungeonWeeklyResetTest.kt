package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.interfaces.repositories.DungeonWeekRefreshResult
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.QuestSystem

class DungeonWeeklyResetTest {
    private val defs = mapOf(
        12722 to MarkDef("week stage", 9, 0, 0, ""),
        12724 to MarkDef("week stage 2", 9, 0, 0, ""),
        12762 to MarkDef("week claim", 9, 1706, 0, ""),
        13328 to MarkDef("crazy week stage", 9, 0, 0, ""),
        12336 to MarkDef("daily", 8, 0, 0, ""),
        15000 to MarkDef("other week", 9, 1900, 0, ""),
    )

    private class Store {
        var week: Int? = null
        var calls = 0
        val steps = mutableMapOf(12722 to 1, 12724 to 1, 13328 to 1, 12336 to 1)
        val flags = mutableMapOf(1706 to 1, 1900 to 1)

        val repo = mockk<QuestRepository>().also { r ->
            coEvery { r.refreshDungeonWeek(any(), any(), any(), any()) } coAnswers {
                calls++
                val newWeek = secondArg<Int>()
                val ids = thirdArg<Set<Int>>()
                val claimBits = arg<Set<Int>>(3)
                kotlinx.coroutines.yield()
                if (week != null && week!! >= newWeek) {
                    DungeonWeekRefreshResult()
                } else {
                    val removedMissions = if (week == null) emptyList() else steps.keys.filter { it in ids }
                    val removedFlags = if (week == null) emptyList() else flags.keys.filter { it in claimBits }
                    removedMissions.forEach { steps.remove(it) }
                    removedFlags.forEach { flags.remove(it) }
                    week = newWeek
                    DungeonWeekRefreshResult(removedMissions, removedFlags)
                }
            }
            coEvery { r.getMissions(any()) } coAnswers {
                steps.map { MissionRecord(it.key, it.value) }
            }
            coEvery { r.getMissionFlags(any()) } coAnswers {
                flags.map { MissionFlagRecord(it.key, it.value) }
            }
        }
    }

    private fun system(store: Store, currentWeek: () -> Int) = QuestSystem(
        repo = store.repo,
        markDefs = defs,
        weeklyDungeonMissionIds = setOf(12722, 12724, 13328, 12336, 12762),
        weeklyClaimBitIds = setOf(1706, 1900),
        currentWeek = currentWeek,
    )

    @Test
    fun firstObservationPreservesExistingWeeklyProgressAndClaim() = runTest {
        val store = Store()
        val result = system(store) { 20260928 }.refreshDungeonWeekly(1)
        assertTrue(result.removedMissionIds.isEmpty())
        assertTrue(result.removedFlagBits.isEmpty())
        assertEquals(1, store.steps[12722])
        assertEquals(1, store.flags[1706])
        assertEquals(1, store.calls)
    }

    @Test
    fun laterMondayClearsOnlyScopedCountersAndExplicitClaimBit() = runTest {
        val store = Store().also { it.week = 20260928 }
        var week = 20261005
        val s = system(store) { week }

        val result = s.refreshDungeonWeekly(1)
        assertEquals(setOf(12722, 12724, 13328), result.removedMissionIds.toSet())
        assertEquals(listOf(1706), result.removedFlagBits)
        assertEquals(setOf(12336), store.steps.keys)
        assertEquals(setOf(1900), store.flags.keys)

        store.steps[12722] = 1
        store.flags[1706] = 1
        week = 20260928
        assertEquals(DungeonWeekRefreshResult(), s.refreshDungeonWeekly(1))
        assertEquals(1, store.steps[12722])
        assertEquals(1, store.flags[1706])
    }

    @Test
    fun concurrentChecksResetOnce() = runTest {
        val store = Store().also { it.week = 20260928 }
        val s = system(store) { 20261005 }
        val results = List(20) { async { s.refreshDungeonWeekly(1) } }.awaitAll()
        assertEquals(3, results.sumOf { it.removedMissionIds.size })
        assertEquals(1, results.sumOf { it.removedFlagBits.size })
        assertEquals(1, store.calls)
    }

    @Test
    fun emptyWeeklyScopeDoesNotTouchPersistence() = runTest {
        val store = Store()
        val s = QuestSystem(store.repo, defs, currentWeek = { 20261005 })
        assertEquals(DungeonWeekRefreshResult(), s.refreshDungeonWeekly(1))
        assertEquals(0, store.calls)
    }
}
