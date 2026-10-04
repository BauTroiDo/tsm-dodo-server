package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.interfaces.repositories.DungeonPeriodResetResult
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.QuestSystem

class DungeonWeeklyResetTest {
    private val defs = mapOf(
        12722 to MarkDef("weekly stage", 9, 0, 0, ""),
        12762 to MarkDef("weekly all-pass claim", 9, 1706, 0, ""),
        13328 to MarkDef("soul weekly stage", 9, 0, 0, ""),
        14000 to MarkDef("unrelated weekly stage", 9, 0, 0, ""),
        14001 to MarkDef("unrelated weekly claim", 9, 1900, 0, ""),
        12336 to MarkDef("normal daily", 8, 0, 0, ""),
    )

    private class Store {
        var week: Int? = null
        var calls = 0
        val steps = mutableMapOf(12722 to 1, 13328 to 1, 14000 to 1, 12336 to 1)
        val flags = mutableMapOf(1706 to 1, 1900 to 1)

        val repo = mockk<QuestRepository>().also { r ->
            coEvery { r.refreshDungeonWeek(any(), any(), any(), any()) } coAnswers {
                calls++
                val newWeek = secondArg<Int>()
                val ids = thirdArg<Set<Int>>()
                val bits = arg<Set<Int>>(3)
                kotlinx.coroutines.yield()
                if (week != null && week!! >= newWeek) {
                    DungeonPeriodResetResult()
                } else {
                    val removed = if (week == null) emptyList() else steps.keys.filter { it in ids }
                    val cleared = if (week == null) emptyList() else flags.keys.filter { it in bits }
                    removed.forEach { steps.remove(it) }
                    cleared.forEach { flags.remove(it) }
                    week = newWeek
                    DungeonPeriodResetResult(removed, cleared)
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

    private fun system(store: Store, week: () -> Int) = QuestSystem(
        repo = store.repo,
        markDefs = defs,
        weeklyDungeonMissionIds = setOf(12722, 13328, 14000, 12336),
        weeklyClaimMissionIds = setOf(12762, 14001),
        thisWeek = week,
    )

    @Test
    fun firstObservationPreservesExistingWeeklyProgressAndClaim() = runTest {
        val store = Store()
        val s = system(store) { 20260928 }

        val reset = s.refreshDungeonWeekly(1)
        assertTrue(reset.removedMissionIds.isEmpty())
        assertTrue(reset.clearedFlagBits.isEmpty())
        assertEquals(1, store.steps[12722])
        assertEquals(1, store.steps[13328])
        assertEquals(1, store.flags[1706])
        assertEquals(1, store.calls)

        assertTrue(s.refreshDungeonWeekly(1).removedMissionIds.isEmpty())
        assertEquals(1, store.calls)
    }

    @Test
    fun nextMondayClearsOnlyLoadedDungeonKind9ScopeAndClaim() = runTest {
        val store = Store().also { it.week = 20260928 }
        val s = system(store) { 20261005 }

        val reset = s.refreshDungeonWeekly(1)
        assertEquals(setOf(12722, 13328), reset.removedMissionIds.toSet())
        assertEquals(setOf(1706), reset.clearedFlagBits.toSet())
        assertFalse(store.steps.containsKey(12722))
        assertFalse(store.steps.containsKey(13328))
        assertTrue(store.steps.containsKey(14000))
        assertTrue(store.steps.containsKey(12336))
        assertFalse(store.flags.containsKey(1706))
        assertTrue(store.flags.containsKey(1900))
    }

    @Test
    fun rollbackAndSameWeekNeverClearAgain() = runTest {
        val store = Store().also { it.week = 20261005 }
        var week = 20261005
        val s = system(store) { week }

        assertTrue(s.refreshDungeonWeekly(1).removedMissionIds.isEmpty())
        store.steps[12722] = 1
        store.flags[1706] = 1

        week = 20260928
        assertTrue(s.refreshDungeonWeekly(1).removedMissionIds.isEmpty())
        assertEquals(1, store.steps[12722])
        assertEquals(1, store.flags[1706])
    }

    @Test
    fun concurrentWeeklyChecksResetOnceAndNewProgressSurvives() = runTest {
        val store = Store().also { it.week = 20260928 }
        val s = system(store) { 20261005 }

        val results = List(20) { async { s.refreshDungeonWeekly(1) } }.awaitAll()
        assertEquals(2, results.sumOf { it.removedMissionIds.size })
        assertEquals(1, results.sumOf { it.clearedFlagBits.size })
        assertEquals(1, store.calls)

        assertEquals(0, s.addMissionStep(1, 12722, 1))
        assertTrue(s.refreshDungeonWeekly(1).removedMissionIds.isEmpty())
        assertEquals(1, store.steps[12722])
    }
}
