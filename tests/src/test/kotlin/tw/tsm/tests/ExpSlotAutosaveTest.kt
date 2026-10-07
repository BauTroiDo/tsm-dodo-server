package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.data.FNpcSlotDef
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.domain.valueobjects.*
import tw.tsm.gamelogic.systems.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExpSlotAutosaveTest {
    @Test fun `old autosave snapshot finishes before slot debit and cache publication`() = runTest {
        val inventoryRepo = mockk<InventoryRepository>(relaxed=true)
        val coin = ThingData(itemId=31207,quantity=10)
        var durableCoin: ThingData? = coin
        coEvery { inventoryRepo.loadItems(42,any()) } answers {
            if (secondArg<Int>() == EThings.BAG) mapOf(7 to coin) else emptyMap()
        }
        val inventory = InventorySystem(inventoryRepo,emptyMap(),mockk(relaxed=true))
        inventory.loadCharacterData(42)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { inventoryRepo.saveAllItems(42,EThings.BAG,any()) } coAnswers {
            val snapshot = thirdArg<Map<Int,ThingData>>().toMap()
            entered.complete(Unit)
            release.await()
            durableCoin = snapshot[7]
        }
        val pets = mockk<PetSystem>()
        val before = FollowNpcData(1,123,level=20,exp=1000)
        every { pets.getFollowPets(42) } returns mapOf(1 to before)
        every { pets.getCache(42) } returns null
        every { pets.planExpSlotGain(42,1,1000) } returns PetSystem.PetExpGain(1,before,before.copy(exp=2000))
        every { pets.publishExpSlotGain(42,any()) } just Runs
        val repo = mockk<ExpSlotRepository>(relaxed=true)
        var commits = 0
        coEvery { repo.settleSpin(42,7,any(),1,any(),any(),any(),any()) } coAnswers {
            durableCoin = durableCoin!!.copy(quantity=9)
            commits++
        }
        val slot = ExpSlotSystem(mapOf(1 to FNpcSlotDef(1,1000,listOf(1,2,3))),repo,pets,emptyMap(),inventory) { 0 }
        val save = async { inventory.save(42) }
        entered.await()
        val spin = async { slot.spin(42,37,listOf(1,123),7) }
        runCurrent()
        assertEquals(0,commits)
        release.complete(Unit)
        save.await()
        assertNotNull(spin.await())
        assertEquals(9,durableCoin!!.quantity)
        assertEquals(durableCoin,inventory.getBagItem(42,7))
    }

    @Test fun `disconnect waits for committed slot before unloading and saving bag`() = runTest {
        val inventoryRepo = mockk<InventoryRepository>(relaxed=true)
        val coin = ThingData(itemId=31207,quantity=10)
        coEvery { inventoryRepo.loadItems(42,any()) } answers {
            if (secondArg<Int>() == EThings.BAG) mapOf(7 to coin) else emptyMap()
        }
        var savedCoin: ThingData? = null
        coEvery { inventoryRepo.saveAllItems(42,EThings.BAG,any()) } coAnswers {
            savedCoin = thirdArg<Map<Int,ThingData>>()[7]
        }
        val inventory = InventorySystem(inventoryRepo,emptyMap(),mockk(relaxed=true))
        inventory.loadCharacterData(42)
        val pets = mockk<PetSystem>()
        val before = FollowNpcData(1,123,level=20,exp=1000)
        every { pets.getFollowPets(42) } returns mapOf(1 to before)
        every { pets.getCache(42) } returns null
        every { pets.planExpSlotGain(42,1,1000) } returns PetSystem.PetExpGain(1,before,before.copy(exp=2000))
        every { pets.publishExpSlotGain(42,any()) } just Runs
        val repo = mockk<ExpSlotRepository>(relaxed=true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { repo.settleSpin(42,7,any(),1,any(),any(),any(),any()) } coAnswers {
            entered.complete(Unit)
            release.await()
        }
        val slot = ExpSlotSystem(mapOf(1 to FNpcSlotDef(1,1000,listOf(1,2,3))),repo,pets,emptyMap(),inventory) { 0 }
        val spin = async { slot.spin(42,37,listOf(1,123),7) }
        entered.await()
        val disconnect = async { inventory.saveAndUnload(42) }
        runCurrent()
        assertEquals(coin,inventory.getBagItem(42,7))
        assertNull(savedCoin)
        release.complete(Unit)
        assertNotNull(spin.await())
        disconnect.await()
        assertEquals(9,savedCoin!!.quantity)
        assertNull(inventory.getBagItem(42,7))
    }
}
