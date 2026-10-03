package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.gamelogic.systems.QuestSystem

class DungeonDailyResetTest {
    private val defs = mapOf(
        12336 to MarkDef("daily", 8, 0, 0, ""),
        12322 to MarkDef("active", 4, 0, 0, ""),
        12323 to MarkDef("reward", 4, 1396, 0, ""),
        12335 to MarkDef("daily flag", 8, 1402, 0, ""),
        13000 to MarkDef("weekly", 8, 0, 0, ""),
    )
    private class Store {
        var day: Int? = null
        var calls = 0
        val steps = mutableMapOf(12336 to 1, 12322 to 1, 13000 to 1, 12335 to 1)
        val repo = mockk<QuestRepository>().also { r ->
            coEvery { r.refreshDungeonDay(any(), any(), any()) } coAnswers {
                calls++
                val newDay = secondArg<Int>()
                val ids = thirdArg<Set<Int>>()
                yield()
                if (day != null && day!! >= newDay) emptyList()
                else {
                    val removed = if (day == null) emptyList() else steps.keys.filter { it in ids }
                    removed.forEach { steps.remove(it) }
                    day = newDay
                    removed
                }
            }
            coEvery { r.getMissions(any()) } coAnswers { steps.map { MissionRecord(it.key, it.value) } }
            coEvery { r.upsertMission(any(), any(), any()) } coAnswers {
                steps[secondArg()] = thirdArg()
            }
        }
    }

    @Test fun `first observation same day relog and restart preserve counters`() = runTest {
        val store = Store()
        fun system() = QuestSystem(store.repo, defs, setOf(12336), { 20261004 })
        val s = system()
        assertTrue(s.refreshDungeonDaily(1).isEmpty())
        assertTrue(s.refreshDungeonDaily(1).isEmpty())
        assertEquals(1, store.calls)
        assertTrue(system().refreshDungeonDaily(1).isEmpty())
        assertEquals(1, store.steps[12336])
    }

    @Test fun `new day clears only scoped kind8 bit0 counters and clock rollback does not clear`() = runTest {
        val store = Store()
        var day = 20261004
        val s = QuestSystem(store.repo, defs, setOf(12336, 12322, 12323, 12335), { day })
        s.refreshDungeonDaily(1)
        day = 20261005
        assertEquals(listOf(12336), s.refreshDungeonDaily(1))
        assertEquals(setOf(12322, 13000, 12335), store.steps.keys)
        store.steps[12336] = 1
        day = 20261004
        assertTrue(s.refreshDungeonDaily(1).isEmpty())
        assertEquals(1, store.steps[12336])
    }

    @Test fun `concurrent checks reset once and serialized new increment survives`() = runTest {
        val store = Store().also { it.day = 20261004 }
        val s = QuestSystem(store.repo, defs, setOf(12336), { 20261005 })
        val results = List(20) { async { s.refreshDungeonDaily(1) } }.awaitAll()
        assertEquals(1, results.sumOf { it.size })
        assertEquals(1, store.calls)
        assertEquals(0, s.addMissionStep(1, 12336, 1))
        assertTrue(s.refreshDungeonDaily(1).isEmpty())
        assertEquals(1, store.steps[12336])
    }

    @Test fun `no daily scope does not touch persistence`() = runTest {
        val store = Store()
        assertTrue(QuestSystem(store.repo, defs).refreshDungeonDaily(1).isEmpty())
        assertEquals(0, store.calls)
    }
}
