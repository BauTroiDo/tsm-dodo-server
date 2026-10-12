package tw.tsm.tests

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.gamelogic.systems.CharacterPersistenceGuard
import tw.tsm.gamelogic.systems.MoneySystem

/** Two-player source inventory fences and money snapshot/save protection. */
class DvtTradePairAndMoneyAutosaveLockTest {
    private val id=99030L

    @Test
    fun reversedPlayerLockOrderNeverDeadlocks() = runBlocking {
        val acquired=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val first=async {
            CharacterPersistenceGuard.withLocks(1001L,1002L) {
                acquired.complete(Unit)
                release.await()
            }
        }
        acquired.await()
        val reversed=async {
            CharacterPersistenceGuard.withLocks(1002L,1001L) { "reversed" }
        }
        repeat(8) { yield() }
        assertFalse(reversed.isCompleted)
        release.complete(Unit)
        withTimeout(3_000) { first.await(); assertEquals("reversed",reversed.await()) }
    }

    @Test
    fun twoDistinctCharactersInSameStripeNeverReacquireOwnMutex() = runBlocking {
        // 1 and 257 collide in the original 256-stripe CharacterPersistenceGuard.
        withTimeout(3_000) {
            assertEquals("one acquisition",
                CharacterPersistenceGuard.withLocks(1L,257L) { "one acquisition" })
        }
    }

    @Test
    fun goldStripeCollisionMustNotDeadlockPairFence() = runBlocking {
        val inventory=mockk<InventoryRepository>(relaxed=true)
        val money=MoneySystem(inventory)
        // Both character IDs map to physical semaphore stripe 1.
        withTimeout(3_000) {
            assertEquals("same-stripe",
                money.withTradeGoldMutationFence(1L,257L) { "same-stripe" })
        }
    }

    @Test
    fun reversedGoldStripeOrderMustNeverDeadlock() = runBlocking {
        val inventory=mockk<InventoryRepository>(relaxed=true)
        val money=MoneySystem(inventory)
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val first=async {
            money.withTradeGoldMutationFence(1001L,1002L) {
                entered.complete(Unit)
                release.await()
                true
            }
        }
        entered.await()
        val second=async {
            money.withTradeGoldMutationFence(1002L,1001L) { true }
        }
        repeat(10) { yield() }
        assertFalse(second.isCompleted)
        release.complete(Unit)
        withTimeout(3_000) {
            assertTrue(first.await())
            assertTrue(second.await())
        }
    }

    @Test
    fun moneyAutosaveCannotPassActiveCharacterSettlementLock() = runBlocking {
        val inventory=mockk<InventoryRepository>(relaxed=true)
        coEvery { inventory.loadMoney(id) } returns (450_000 to 17)
        val money=MoneySystem(inventory)
        money.loadMoney(id)
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val transfer=async {
            CharacterPersistenceGuard.withLock(id) {
                entered.complete(Unit)
                release.await()
                money.subtractGold(id,50_000)
            }
        }
        entered.await()
        val autosave=async { money.saveMoney(id) }
        repeat(8) { yield() }
        assertFalse(autosave.isCompleted)
        coVerify(exactly=0) { inventory.saveMoney(id,any(),any()) }
        release.complete(Unit)
        assertTrue(withTimeout(3_000) { transfer.await() })
        withTimeout(3_000) { autosave.await() }
        coVerify(exactly=1) { inventory.saveMoney(id,400_000,17) }
        assertEquals(400_000,money.getGold(id))
    }

    @Test
    fun moneyLogoutCacheRemovalCannotPrecedeLockedSave() = runBlocking {
        val inventory=mockk<InventoryRepository>(relaxed=true)
        coEvery { inventory.loadMoney(id) } returns (201_000 to 1)
        val money=MoneySystem(inventory)
        money.loadMoney(id)
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val transaction=async {
            CharacterPersistenceGuard.withLock(id) {
                entered.complete(Unit)
                release.await()
                money.subtractGold(id,1000)
            }
        }
        entered.await()
        val logout=async { money.saveAndUnload(id) }
        repeat(8) { yield() }
        assertFalse(logout.isCompleted)
        assertEquals(201_000,money.getGold(id))
        release.complete(Unit)
        transaction.await()
        withTimeout(3_000) { logout.await() }
        coVerify(exactly=1) { inventory.saveMoney(id,200_000,1) }
        assertEquals(0,money.getGold(id))
    }
}
