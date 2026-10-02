package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.StatSystem

class DvtInnWarehouseTransitionTest {
    private val repo = mockk<PetRepository>(relaxed = true)
    private val stat = mockk<StatSystem>(relaxed = true) {
        every { calculateNeedTotalExp(any(), any()) } returns 0L
        every { calculateMaxHp(any(), any(), any(), any(), any(), any(), any()) } returns 50_000L
        every { calculateMaxSp(any(), any(), any(), any(), any(), any()) } returns 5_000
    }
    private val pets = PetSystem(repo, stat)

    private suspend fun load(vararg entries: FollowNpcData) {
        coEvery { repo.findByCharacterId(42) } returns entries.toList()
        pets.load(42)
    }
    @Test
    fun `deployed pet can be stored in inn and returns undeployed`() = runTest {
        val deployed = FollowNpcData(
            slotIndex = 3,
            npcId = 30003,
            isDeployed = true,
        )
        load(deployed)

        val innSlot = pets.storeToInn(42, 3)
        assertEquals(1, innSlot)
        assertNull(pets.getFollowPets(42)[3])
        assertFalse(pets.getInnPets(42)[1]!!.isDeployed)
        assertEquals(0, pets.getCache(42)!!.fightNpcId)

        val followSlot = pets.retrieveFromInn(42, 1)
        assertEquals(1, followSlot)
        assertFalse(pets.getFollowPets(42)[1]!!.isDeployed)
        assertEquals(0, pets.getCache(42)!!.fightNpcId)
    }
    @Test
    fun `deployed pet can be stored in warehouse and returns undeployed`() = runTest {
        val deployed = FollowNpcData(
            slotIndex = 2,
            npcId = 30002,
            isDeployed = true,
        )
        load(deployed)

        assertTrue(pets.storeToWarehouse(42, 2))
        assertNull(pets.getFollowPets(42)[2])
        assertFalse(pets.getWarehousePets(42).single().isDeployed)
        assertEquals(0, pets.getCache(42)!!.fightNpcId)

        val followSlot = pets.retrieveFromWarehouse(42, 30002)
        assertEquals(1, followSlot)
        assertFalse(pets.getFollowPets(42)[1]!!.isDeployed)
        assertEquals(0, pets.getCache(42)!!.fightNpcId)
    }
}
