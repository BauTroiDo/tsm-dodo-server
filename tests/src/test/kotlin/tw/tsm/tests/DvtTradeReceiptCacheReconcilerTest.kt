package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*
import java.util.UUID

/** Staged-only reconnect / lost SQL ACK -> guarded TWO online cache repair. */
class DvtTradeReceiptCacheReconcilerTest {
    private val a = 925101L
    private val b = 925102L
    private val bagA = mapOf(3 to ThingData(itemId = 501, quantity = 1))
    private val bagB = mapOf(7 to ThingData(itemId = 502, quantity = 2, mythLevel = 2))
    private val repo = mockk<InventoryRepository>(relaxed = true)
    private val counts = mockk<RoleCountSystem>(relaxed = true)
    private val inventory = InventorySystem(repo, emptyMap(), counts)
    private val money = MoneySystem(repo)
    private val reconciler = TradeReceiptCacheReconciler(repo, inventory, money)
    private val id = UUID.randomUUID().toString()

    private fun plans() =
        TradeAccountSnapshot(a, bagA, mapOf(3 to bagB.getValue(7)), 1000, 1200, 9) to
            TradeAccountSnapshot(b, bagB, mapOf(7 to bagA.getValue(3)), 2000, 1800, 19)

    private fun inspection(
        first: TradeAccountSnapshot,
        second: TradeAccountSnapshot,
        lookup: TradeReceiptLookup = TradeReceiptLookup.MATCHED,
        dbFirst: TradeObservedAccount? = TradeObservedAccount(
            a, first.afterBag, first.afterGold, first.auctionGold
        ),
        dbSecond: TradeObservedAccount? = TradeObservedAccount(
            b, second.afterBag, second.afterGold, second.auctionGold
        ),
    ): TradeReceiptInspection = TradeReceiptInspection(lookup, dbFirst, dbSecond)

    private suspend fun setup() {
        coEvery { repo.loadItems(any(), any()) } coAnswers {
            if (secondArg<Int>() != EThings.BAG) emptyMap()
            else if (firstArg<Long>() == a) bagA else bagB
        }
        coEvery { repo.loadMoney(a) } returns (1000 to 9)
        coEvery { repo.loadMoney(b) } returns (2000 to 19)
        coEvery { repo.saveAllItems(any(), any(), any()) } returns Unit
        coEvery { repo.saveMoney(any(), any(), any()) } returns Unit
        inventory.loadCharacterData(a)
        inventory.loadCharacterData(b)
        money.loadMoney(a)
        money.loadMoney(b)
    }

    @Test
    fun matchedReceiptAndExactSqlAfterRepairsBothOldCaches() = runBlocking {
        setup()
        val (first, second) = plans()
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second)
        assertEquals(
            TradeReceiptCacheReconciler.Result.RESTORED,
            reconciler.reconcile(id, first, second),
        )
        assertEquals(first.afterBag, inventory.getLoadedBagSnapshot(a))
        assertEquals(second.afterBag, inventory.getLoadedBagSnapshot(b))
        assertEquals(first.afterGold to first.auctionGold, money.getLoadedMoney(a))
        assertEquals(second.afterGold to second.auctionGold, money.getLoadedMoney(b))
        coVerify(exactly = 1) { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) }
        coVerify(exactly = 0) { repo.commitTwoCharacterTradeWithReceiptAtomic(any(), any(), any()) }
    }

    @Test
    fun alreadyPublishedCachesProduceNoSecondPublication() = runBlocking {
        setup()
        val (first, second) = plans()
        inventory.publishCommittedTradeBagSync(a, first.afterBag)
        inventory.publishCommittedTradeBagSync(b, second.afterBag)
        money.publishCommittedTradeGoldSync(a, first.afterGold, first.auctionGold)
        money.publishCommittedTradeGoldSync(b, second.afterGold, second.auctionGold)
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second)
        assertEquals(TradeReceiptCacheReconciler.Result.ALREADY_SYNCED,
            reconciler.reconcile(id, first, second))
        assertEquals(first.afterBag, inventory.getLoadedBagSnapshot(a))
        assertEquals(second.afterBag, inventory.getLoadedBagSnapshot(b))
    }

    @Test
    fun interruptedPublicationWithOneBagAndOneGoldAlreadyUpdatedIsRepairable() = runBlocking {
        setup()
        val (first, second) = plans()
        inventory.publishCommittedTradeBagSync(a, first.afterBag)
        money.publishCommittedTradeGoldSync(b, second.afterGold, second.auctionGold)
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second)
        assertEquals(TradeReceiptCacheReconciler.Result.RESTORED,
            reconciler.reconcile(id, first, second))
        assertEquals(first.afterBag, inventory.getLoadedBagSnapshot(a))
        assertEquals(second.afterBag, inventory.getLoadedBagSnapshot(b))
        assertEquals(1200 to 9, money.getLoadedMoney(a))
        assertEquals(1800 to 19, money.getLoadedMoney(b))
    }

    @Test
    fun changedLocalGoldRewardCannotBeOverwrittenByHistoricalReceipt() = runBlocking {
        setup()
        val (first, second) = plans()
        assertTrue(money.addGold(a, 50))
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second)
        assertEquals(TradeReceiptCacheReconciler.Result.CACHE_DIVERGED,
            reconciler.reconcile(id, first, second))
        assertEquals(1050, money.getGold(a))
        assertEquals(2000, money.getGold(b))
        assertEquals(bagA, inventory.getLoadedBagSnapshot(a))
        assertEquals(bagB, inventory.getLoadedBagSnapshot(b))
    }

    @Test
    fun changedLocalBagCannotBeClobbered() = runBlocking {
        setup()
        val (first, second) = plans()
        val added = ThingData(itemId = 777, quantity = 1)
        inventory.setBagItemSync(a, 9, added)
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second)
        assertEquals(TradeReceiptCacheReconciler.Result.CACHE_DIVERGED,
            reconciler.reconcile(id, first, second))
        assertEquals(added, inventory.getBagItem(a, 9))
        assertEquals(bagB, inventory.getLoadedBagSnapshot(b))
    }

    @Test
    fun persistedBalancesAlreadyAdvancedAfterReceiptRequireManualReconciliation() = runBlocking {
        setup()
        val (first, second) = plans()
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second, dbFirst = TradeObservedAccount(
                a, first.afterBag, first.afterGold + 50, first.auctionGold
            ))
        assertEquals(TradeReceiptCacheReconciler.Result.DATABASE_DIVERGED,
            reconciler.reconcile(id, first, second))
        assertEquals(1000, money.getGold(a))
        assertEquals(bagA, inventory.getLoadedBagSnapshot(a))
    }

    @Test
    fun missingOrConflictingReceiptNeverTriggersSecondSqlDebit() = runBlocking {
        setup()
        val (first, second) = plans()
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second, TradeReceiptLookup.NOT_FOUND)
        assertEquals(TradeReceiptCacheReconciler.Result.RECEIPT_NOT_FOUND,
            reconciler.reconcile(id, first, second))
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second, TradeReceiptLookup.TOKEN_CONFLICT)
        assertEquals(TradeReceiptCacheReconciler.Result.TOKEN_CONFLICT,
            reconciler.reconcile(id, first, second))
        assertEquals(bagA, inventory.getLoadedBagSnapshot(a))
        assertEquals(bagB, inventory.getLoadedBagSnapshot(b))
        coVerify(exactly = 0) { repo.commitTwoCharacterTradeWithReceiptAtomic(any(), any(), any()) }
    }

    @Test
    fun missingSqlAccountOrOfflineCacheNeverPublishesPartialTransfer() = runBlocking {
        setup()
        val (first, second) = plans()
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second, dbSecond = null)
        assertEquals(TradeReceiptCacheReconciler.Result.DATABASE_DIVERGED,
            reconciler.reconcile(id, first, second))
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } returns
            inspection(first, second)
        inventory.saveAndUnload(b)
        money.saveAndUnload(b)
        assertEquals(TradeReceiptCacheReconciler.Result.CACHE_UNLOADED,
            reconciler.reconcile(id, first, second))
        assertEquals(bagA, inventory.getLoadedBagSnapshot(a))
        assertEquals(1000, money.getGold(a))
    }

    @Test
    fun databaseExceptionReturnsIndeterminateWithoutInventingRollback() = runBlocking {
        setup()
        val (first, second) = plans()
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } throws
            IllegalStateException("SQL acknowledgement lost")
        assertEquals(TradeReceiptCacheReconciler.Result.INDETERMINATE,
            reconciler.reconcile(id, first, second))
        assertEquals(bagA, inventory.getLoadedBagSnapshot(a))
        assertEquals(bagB, inventory.getLoadedBagSnapshot(b))
    }

    @Test
    fun concurrentSyncGoldAwardWaitsForSqlInspectionAndCannotBeLost() = runBlocking {
        setup()
        val (first, second) = plans()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second) } coAnswers {
            entered.complete(Unit)
            release.await()
            inspection(first, second)
        }
        val checking = async { reconciler.reconcile(id, first, second) }
        entered.await()
        val reward = async(Dispatchers.Default) { money.addGold(a, 50) }
        repeat(20) { yield() }
        assertFalse(reward.isCompleted)
        release.complete(Unit)
        assertEquals(TradeReceiptCacheReconciler.Result.RESTORED, checking.await())
        assertTrue(withTimeout(3_000) { reward.await() })
        assertEquals(1250, money.getGold(a))
        assertEquals(1800, money.getGold(b))
    }
}
