package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.gamelogic.systems.QuestSystem

class QuestStepIntegrityTest {
    private class MemoryRepo : QuestRepository {
        val steps = mutableMapOf<Int, Int>()
        val flags = mutableMapOf<Int, Int>()
        override suspend fun getMissions(charId: Long): List<MissionRecord> {
            val snapshot = steps.map { MissionRecord(it.key, it.value) }
            yield()
            return snapshot
        }
        override suspend fun getMissionFlags(charId: Long) = flags.map { MissionFlagRecord(it.key, it.value) }
        override suspend fun upsertMission(charId: Long, missionId: Int, step: Int) { yield(); steps[missionId] = step }
        override suspend fun removeMission(charId: Long, missionId: Int) { yield(); steps.remove(missionId) }
        override suspend fun upsertFlag(charId: Long, bitIndex: Int, bitValue: Int) { flags[bitIndex] = bitValue }
        override suspend fun getCompletedEventCounts(charId: Long, mapId: Int) = emptyMap<Int, Int>()
        override suspend fun insertCompletedEvent(charId: Long, mapId: Int, eveNo: Int) = Unit
        override suspend fun clearCompletedEvents(charId: Long, mapId: Int) = Unit
    }
    private val defs = mapOf(10025 to MarkDef("Leaf", 2, 24, 0, ""))

    @Test fun exactByteBoundarySucceedsAndOverflowDoesNotChangeProgress() = runTest {
        val repo = MemoryRepo(); val system = QuestSystem(repo, defs)
        repo.steps[10025] = 254
        assertEquals(254, system.addMissionStep(31, 10025, 1))
        assertEquals(255, repo.steps[10025])
        assertNull(system.addMissionStep(31, 10025, 1))
        assertEquals(255, repo.steps[10025])
    }

    @Test fun invalidAndPackedDeltasCannotBePersisted() = runTest {
        val repo = MemoryRepo(); val system = QuestSystem(repo, defs)
        for (delta in listOf(-1, 256, 912152, Int.MAX_VALUE)) {
            assertNull(system.addMissionStep(31, 10025, delta))
        }
        assertTrue(repo.steps.isEmpty())
    }

    @Test fun simultaneousIncrementsDoNotLoseSteps() = runTest {
        val repo = MemoryRepo(); val system = QuestSystem(repo, defs)
        coroutineScope { repeat(50) { launch { system.addMissionStep(31, 10025, 1) } } }
        assertEquals(50, repo.steps[10025])
    }

    @Test fun decrementsCompleteOnceAndSurviveSystemReload() = runTest {
        val repo = MemoryRepo(); val system = QuestSystem(repo, defs)
        repo.steps[10025] = 2
        assertFalse(system.decMissionStep(31, 10025, 1).completed)
        assertTrue(system.decMissionStep(31, 10025, 1).completed)
        assertFalse(system.decMissionStep(31, 10025, 1).completed)
        val reloaded = QuestSystem(repo, defs)
        assertTrue(reloaded.getMissions(31).isEmpty())
        assertTrue(reloaded.hasMissionFlag(31, 10025))
    }

    @Test fun completionWithoutBitDoesNotInventFlag() = runTest {
        val repo = MemoryRepo(); val system = QuestSystem(repo, emptyMap())
        repo.steps[12962] = 1
        assertEquals(0, system.completeMission(31, 12962).bitId)
        assertTrue(repo.steps.isEmpty()); assertTrue(repo.flags.isEmpty())
    }

    @Test fun completionUsesSingleRepositoryOperation() = runTest {
        val repo = mockk<QuestRepository>(relaxed = true)
        val system = QuestSystem(repo, defs)
        system.completeMission(31, 10025)
        coVerify(exactly = 1) { repo.finishMission(31, 10025, 24) }
        coVerify(exactly = 0) { repo.removeMission(any(), any()) }
        coVerify(exactly = 0) { repo.upsertFlag(any(), any(), any()) }
    }

    @Test fun gmCannotPersistOutOfRangeStep() = runTest {
        val repo = MemoryRepo(); val system = QuestSystem(repo, defs)
        var rejected = false
        try { system.setMissionStep(31, 10025, 256) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected); assertTrue(repo.steps.isEmpty())
    }
}
