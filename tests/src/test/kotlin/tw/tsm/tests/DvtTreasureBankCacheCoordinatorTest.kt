package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.TreasureRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.TreasureBankCacheCoordinator

/**
 * Proves staged banking/InventorySystem autosave and logout snapshot coherence.
 * Does NOT claim unrelated BAG writers also participate in the mutex.
 */
class DvtTreasureBankCacheCoordinatorTest {
    private val charId = 888004L
    private val slot = 7
    private val inventoryRepository = mockk<InventoryRepository>(relaxed = true)
    private val roleCounts = mockk<RoleCountSystem>(relaxed = true)
    private val treasure = mockk<TreasureRepository>()
    private val system = InventorySystem(inventoryRepository, emptyMap(), roleCounts)
    private val coordinator = TreasureBankCacheCoordinator(
        treasure, system, mapOf(1 to 25601)
    )
    private val snapshots = mutableListOf<Map<Int, ThingData>>()

    private suspend fun loaded(quantity: Int = 5) {
        coEvery { inventoryRepository.loadItems(charId, any()) } coAnswers {
            if (secondArg<Int>() == EThings.BAG) mapOf(slot to ThingData(25601, quantity))
            else emptyMap()
        }
        coEvery {
            inventoryRepository.saveAllItems(charId, EThings.BAG, any())
        } coAnswers {
            snapshots.add(thirdArg<Map<Int, ThingData>>().toMap())
        }
        coEvery { inventoryRepository.saveAllItems(charId, neq(EThings.BAG), any()) } returns Unit
        system.loadCharacterData(charId)
    }

    @Test
    fun successfulCommitPublishesBagBeforePeriodicAutosaveCanSnapshot() = runBlocking {
        loaded()
        val dbEntered = CompletableDeferred<Unit>()
        val releaseSql = CompletableDeferred<Unit>()
        coEvery { treasure.bankCard(charId,1,0,slot,2) } coAnswers {
            dbEntered.complete(Unit)
            releaseSql.await()
            true
        }
        val settlement = async { coordinator.stageBank(charId,1,0,slot,2) }
        dbEntered.await()

        val autosave = async { system.save(charId) }
        repeat(6) { yield() }
        assertEquals(0,snapshots.size, "autosave must not flush old physical stack before bank commit")
        releaseSql.complete(Unit)
        val completed = settlement.await()
        assertEquals(3,completed?.remainingQuantity)
        autosave.await()
        assertEquals(3,snapshots.single()[slot]?.quantity)
        assertEquals(3,system.getBagItem(charId,slot)?.quantity)
        coVerify(exactly=1) { treasure.bankCard(charId,1,0,slot,2) }
    }

    @Test
    fun successfulCommitPublishesBagBeforeLogoutRemovesCachedBag() = runBlocking {
        loaded()
        val dbEntered=CompletableDeferred<Unit>()
        val releaseSql=CompletableDeferred<Unit>()
        coEvery { treasure.bankCard(charId,1,0,slot,5) } coAnswers {
            dbEntered.complete(Unit)
            releaseSql.await()
            true
        }
        val settlement=async { coordinator.stageBank(charId,1,0,slot,5) }
        dbEntered.await()
        val logout=async { system.saveAndUnload(charId) }
        repeat(6) { yield() }
        assertEquals(0,snapshots.size)
        releaseSql.complete(Unit)
        assertEquals(0,settlement.await()?.remainingQuantity)
        logout.await()
        assertNull(snapshots.single()[slot], "fully banked card must never reappear on logout")
        assertNull(system.getBagItem(charId,slot))
    }

    @Test
    fun rejectedOrFailedSqlDoesNotPublishAnyBagChange() = runBlocking {
        loaded()
        coEvery { treasure.bankCard(charId,1,0,slot,2) } returns false
        assertNull(coordinator.stageBank(charId,1,0,slot,2))
        assertEquals(5,system.getBagItem(charId,slot)?.quantity)
        system.save(charId)
        assertEquals(5,snapshots.single()[slot]?.quantity)
    }

    @Test
    fun failedCommitThrowsButInMemoryBagRemainsUnchanged() = runBlocking {
        loaded()
        coEvery { treasure.bankCard(charId,1,0,slot,1) } throws IllegalStateException("rollback")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.stageBank(charId,1,0,slot,1) }
        }
        assertEquals(5,system.getBagItem(charId,slot)?.quantity)
    }

    @Test
    fun cancellationDuringDbCommitDoesNotInterruptCachePublication() = runBlocking {
        loaded()
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        coEvery { treasure.bankCard(charId,1,0,slot,2) } coAnswers {
            entered.complete(Unit)
            release.await()
            true
        }
        val operation=async { coordinator.stageBank(charId,1,0,slot,2) }
        entered.await()
        operation.cancel()
        release.complete(Unit)
        operation.join()
        system.save(charId)
        assertEquals(3,system.getBagItem(charId,slot)?.quantity)
        assertEquals(3,snapshots.single()[slot]?.quantity)
    }

    @Test
    fun wrongCardOrUnloadedBagCannotInvokeSqlBanking() = runBlocking {
        loaded()
        assertNull(coordinator.stageBank(charId,2,0,slot,1))
        assertNull(coordinator.stageBank(charId,1,0,slot+1,1))
        assertNull(coordinator.stageBank(charId,1,0,slot,6))
        assertNull(coordinator.stageBank(charId,1,-1,slot,1))
        assertNull(coordinator.stageBank(charId,1,0,slot,0))
        coVerify(exactly=0) { treasure.bankCard(any(),any(),any(),any(),any()) }
        assertEquals(5,system.getBagItem(charId,slot)?.quantity)
    }

    @Test
    fun trustedCardMapMustBeUniqueAndOriginal() {
        assertThrows(IllegalArgumentException::class.java) {
            TreasureBankCacheCoordinator(treasure,system,mapOf(1 to 25601,2 to 25601))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasureBankCacheCoordinator(treasure,system,mapOf(115 to 25601))
        }
    }
}
