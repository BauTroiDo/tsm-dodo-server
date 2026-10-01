package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.DispatchStartResult
import tw.tsm.domain.data.DispatchDailyUsage
import tw.tsm.domain.data.DispatchSaveData
import tw.tsm.domain.data.DispatchDef
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.gamelogic.systems.DispatchSystem
import tw.tsm.gamelogic.systems.PetSystem

class DispatchStartIntegrityTest {
    private class MemoryRepository : DispatchRepository {
        val records = mutableListOf<DispatchSaveData>()
        var daily: DispatchDailyUsage? = null
        override suspend fun findByCharacterId(characterId: Long): List<DispatchSaveData> {
            val snapshot = records.toList(); yield(); return snapshot
        }
        override suspend fun insert(characterId: Long, data: DispatchSaveData) { yield(); records.add(data) }
        override suspend fun deleteBySlot(characterId: Long, slotIndex: Int) { records.removeAll { it.slotIndex == slotIndex } }
        override suspend fun getDailyUsage(characterId: Long) = daily
        override suspend fun saveDailyUsage(characterId: Long, usage: DispatchDailyUsage) { daily = usage }
    }
    private fun system(repo: DispatchRepository): DispatchSystem {
        val pets = mockk<PetSystem>()
        every { pets.getInnNpc(31L, any()) } returns mockk(relaxed = true)
        return dispatchSystemForTest(repo, pets, mapOf(1 to (1..5).associateWith { listOf(DispatchDef(0, it, 2, 70000)) }), emptyMap())
    }
    @Test fun simultaneousStartsCannotDispatchSameWarriorTwice() = runTest {
        val repo = MemoryRepository(); val s = system(repo)
        val results = coroutineScope { List(12) { async { s.startDispatch(31L, 1, 45000.0).first } }.awaitAll() }
        assertEquals(1, results.count { it == DispatchStartResult.SUCCESS })
        assertEquals(1, repo.records.count { it.slotIndex > 0 })
    }
    @Test fun simultaneousDifferentWarriorsCannotExceedFourUniqueSlots() = runTest {
        val repo = MemoryRepository(); val s = system(repo)
        coroutineScope { (1..12).map { async { s.startDispatch(31L, it, 45000.0) } }.awaitAll() }
        assertEquals(4, repo.records.count { it.slotIndex > 0 })
        assertEquals((1..4).toSet(), repo.records.filter { it.slotIndex > 0 }.map { it.slotIndex }.toSet())
    }
    @Test fun lockedSlotsCannotBeAllocatedEvenWithSparseExistingRecords() = runTest {
        val repo = MemoryRepository(); val s = system(repo)
        repo.records.add(DispatchSaveData(1, 1.0, 2.0, 1, 1))
        assertEquals(DispatchStartResult.SLOT_FULL, s.startDispatch(31L, 2, 45000.0, maxSlots = 1).first)
        assertEquals(DispatchStartResult.SLOT_FULL, s.startDispatch(31L, 2, 45000.0, maxSlots = 0).first)
        assertEquals(1, repo.records.count { it.slotIndex > 0 })
    }
    @Test fun invalidNpcAndNonFiniteClockCannotCreateRecords() = runTest {
        val repo = MemoryRepository(); val s = system(repo)
        for (index in listOf(-1, 0, 256)) assertNotEquals(DispatchStartResult.SUCCESS, s.startDispatch(31L, index, 45000.0).first)
        for (time in listOf(Double.NaN, Double.POSITIVE_INFINITY)) assertNotEquals(DispatchStartResult.SUCCESS, s.startDispatch(31L, 1, time).first)
        assertTrue(repo.records.isEmpty())
    }
}
