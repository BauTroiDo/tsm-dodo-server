package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*

class QuestExchangePlanTest {
    private val repo = mockk<InventoryRepository>(relaxed = true)
    private val counts = mockk<RoleCountSystem>(relaxed = true)
    private val def = mockk<ItemDef>(relaxed = true)
    private val inventory = InventorySystem(repo, mapOf(10001 to def, 10002 to def, 10003 to def), counts)
    private suspend fun load(bag: Map<Int, ThingData>, capacity: Int) {
        every { def.fitType } returns 0
        every { counts.getBagCapacity(31L) } returns capacity
        coEvery { repo.loadItems(31L, any()) } answers { if (secondArg<Int>() == 1) bag else emptyMap() }
        inventory.loadCharacterData(31L)
    }
    @Test fun submissionThatFreesSlotCanReceiveDifferentRewardInFullBag() = runTest {
        val bag = mapOf(0 to ThingData(itemId=10001,quantity=1))
        load(bag, 1)
        assertTrue(inventory.canApplyQuestItemChanges(31, listOf(10001 to -1, 10002 to 1)))
        assertEquals(bag, inventory.getBag(31))
        coVerify(exactly=0) { repo.saveItem(any(), any(), any(), any()) }
    }
    @Test fun submissionLeavingStackCannotFitDifferentRewardAndChangesNothing() = runTest {
        val bag = mapOf(0 to ThingData(itemId=10001,quantity=2))
        load(bag, 1)
        assertFalse(inventory.canApplyQuestItemChanges(31, listOf(10001 to -1, 10002 to 1)))
        assertEquals(bag, inventory.getBag(31))
    }
    @Test fun twoRewardTypesCompeteForTheSameFreedSlot() = runTest {
        val bag = mapOf(0 to ThingData(itemId=10001,quantity=1))
        load(bag, 1)
        assertFalse(inventory.canApplyQuestItemChanges(31, listOf(10001 to -1, 10002 to 1, 10003 to 1)))
        assertEquals(bag, inventory.getBag(31))
    }
    @Test fun repeatedConsumptionCountsEarlierConsumption() = runTest {
        val bag = mapOf(0 to ThingData(itemId=10001,quantity=2), 1 to ThingData(itemId=10001,quantity=2))
        load(bag, 2)
        assertFalse(inventory.canApplyQuestItemChanges(31, listOf(10001 to -3, 10001 to -2)))
        assertTrue(inventory.canApplyQuestItemChanges(31, listOf(10001 to -3, 10001 to -1, 10002 to 1)))
        assertEquals(bag, inventory.getBag(31))
    }
}
