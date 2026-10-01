package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.DispatchCompleteResult
import tw.tsm.domain.data.CommonAwardEntry
import tw.tsm.domain.data.DispatchDailyUsage
import tw.tsm.domain.data.DispatchDef
import tw.tsm.domain.data.DispatchSaveData
import tw.tsm.domain.data.LuckyBagDef
import tw.tsm.domain.data.LuckyBagEntry
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*

class DispatchRewardClaimTest {
    private class Repo(
        private val row: DispatchSaveData,
    ) : DispatchRepository {
        val rows = mutableListOf(row)
        var daily: DispatchDailyUsage? = null

        override suspend fun findByCharacterId(characterId: Long) = rows.toList()
        override suspend fun insert(characterId: Long, data: DispatchSaveData) {
            rows.removeAll { it.slotIndex == data.slotIndex }
            rows += data
        }
        override suspend fun deleteBySlot(characterId: Long, slotIndex: Int) {
            rows.removeAll { it.slotIndex == slotIndex }
        }
        override suspend fun getDailyUsage(characterId: Long) = daily
        override suspend fun saveDailyUsage(characterId: Long, usage: DispatchDailyUsage) {
            daily = usage
        }
    }

    private fun commonAwards() = mapOf(
        1 to listOf(
            CommonAwardEntry(1, 65301, 10),
            CommonAwardEntry(1, 65352, 5),
            CommonAwardEntry(2, 10013, 1),
            CommonAwardEntry(2, 10012, 1),
            CommonAwardEntry(2, 10003, 1),
        )
    )

    private fun luckyBags() = mapOf(
        10013 to LuckyBagDef(0, 0, listOf(LuckyBagEntry(1, 55431, 3))),
        10012 to LuckyBagDef(0, 0, listOf(LuckyBagEntry(1, 65303, 1))),
        10003 to LuckyBagDef(0, 0, listOf(LuckyBagEntry(1, 35005, 5))),
    )

    private fun petSystem(): PetSystem {
        val pets = mockk<PetSystem>(relaxed = true)
        every { pets.getInnNpc(31L, 3) } returns FollowNpcData(
            slotIndex = 3,
            npcId = 12020,
            name = "",
            level = 1,
            canGrow = 1,
        )
        every {
            pets.gainInnDispatchExp(31L, 3, 2, 70_000)
        } returns null
        return pets
    }

    private fun system(
        repo: DispatchRepository,
        pets: PetSystem,
        inventory: InventorySystem,
    ) = dispatchSystemForTest(
        repo = repo,
        pets = pets,
        defs = mapOf(1 to mapOf(1 to listOf(DispatchDef(0, 1, 2, 70_000)))),
        bonusDefs = emptyMap(),
        commonAwards = commonAwards(),
        luckyBags = luckyBags(),
        inventory = inventory,
    )

    @Test
    fun completedClaimGrantsAllFixedAndLuckyBagRewardsThenConsumesSlot() = runTest {
        val record = DispatchSaveData(1, 1.0, 2.0, 1, 3)
        val repo = Repo(record)
        val pets = petSystem()
        val inventory = mockk<InventorySystem>()
        val expectedRewards = listOf(
            65301 to 10,
            65352 to 5,
            55431 to 3,
            65303 to 1,
            35005 to 5,
        )
        val changed = listOf(
            0 to ThingData(itemId = 65301, quantity = 10),
            1 to ThingData(itemId = 65352, quantity = 5),
            2 to ThingData(itemId = 55431, quantity = 3),
            3 to ThingData(itemId = 65303, quantity = 1),
            4 to ThingData(itemId = 35005, quantity = 5),
        )
        coEvery {
            inventory.addItemBundleToBagExact(31L, expectedRewards)
        } returns changed

        val outcome = system(repo, pets, inventory)
            .completeDispatchWithRewards(31L, 1, 3.0)

        assertEquals(DispatchCompleteResult.SUCCESS, outcome.resultCode)
        assertEquals(changed, outcome.addedItems)
        assertTrue(repo.rows.isEmpty())
        coVerify(exactly = 1) {
            inventory.addItemBundleToBagExact(31L, expectedRewards)
        }
        verify(exactly = 1) {
            pets.gainInnDispatchExp(31L, 3, 2, 70_000)
        }
        coVerify(exactly = 1) { pets.save(31L) }
    }

    @Test
    fun bagFullDoesNotGrantExpOrConsumeClaim() = runTest {
        val record = DispatchSaveData(1, 1.0, 2.0, 1, 3)
        val repo = Repo(record)
        val pets = petSystem()
        val inventory = mockk<InventorySystem>()
        coEvery {
            inventory.addItemBundleToBagExact(31L, any())
        } returns null

        val outcome = system(repo, pets, inventory)
            .completeDispatchWithRewards(31L, 1, 3.0)

        assertEquals(DispatchCompleteResult.BAG_FULL, outcome.resultCode)
        assertEquals(listOf(record), repo.rows)
        verify(exactly = 0) {
            pets.gainInnDispatchExp(any(), any(), any(), any())
        }
        coVerify(exactly = 0) { pets.save(any()) }
    }
}
