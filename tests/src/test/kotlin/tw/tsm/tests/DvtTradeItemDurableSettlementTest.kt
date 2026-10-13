package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*
import tw.tsm.gamelogic.systems.TradeItemDurableSettlement.ConfirmationOutcome
import tw.tsm.gamelogic.systems.TradeItemFinalizationGate.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integrated, staged-only planner -> winner gate -> receipt SQL -> both RAM
 * cache publications -> DB receipt recovery. No active C025 wire/DI.
 */
class DvtTradeItemDurableSettlementTest {
    private val a = 901101L
    private val b = 901102L
    private val bagA = mapOf(3 to ThingData(itemId = 501, quantity = 1, affix1 = 7))
    private val bagB = mapOf(7 to ThingData(itemId = 502, quantity = 2, damage = 11))
    private val repo = mockk<InventoryRepository>(relaxed = true)
    private val count = mockk<RoleCountSystem>(relaxed = true)
    private val inventory = InventorySystem(repo, emptyMap(), count)
    private val money = MoneySystem(repo)
    private val coordinator = TradeCacheSettlementCoordinator(repo, inventory, money)
    private val reconciler = TradeReceiptCacheReconciler(repo, inventory, money)
    private val id = UUID.randomUUID().toString()
    private val firstPlayer = 4101L
    private val secondPlayer = 4102L

    private fun itemDef() = mockk<ItemDef> {
        every { restrict } returns 0
        every { restrict2 } returns 0
        every { kind } returns 0
        every { fitType } returns 0
    }

    private val planner = TradeItemSnapshotPlanner(
        mapOf(501 to itemDef(), 502 to itemDef())
    )
    private val firstSide = TradeItemSnapshotPlanner.Side(
        a, bagA, bagA, listOf(3), 1000, 100, 9, 250
    )
    private val secondSide = TradeItemSnapshotPlanner.Side(
        b, bagB, bagB, listOf(7), 2000, 300, 19, 250
    )
    private val plan = planner.plan(firstSide, secondSide)
        as TradeItemSnapshotPlanner.Result.Accepted

    private suspend fun loadOnline() {
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

    private fun settlement(
        first: TradeItemSnapshotPlanner.Side = firstSide,
        second: TradeItemSnapshotPlanner.Side = secondSide,
    ) = TradeItemDurableSettlement(
        id, firstPlayer, secondPlayer, first, second, planner, coordinator, reconciler
    )

    private fun dbAfter(): TradeReceiptInspection = TradeReceiptInspection(
        TradeReceiptLookup.MATCHED,
        TradeObservedAccount(a, plan.first.afterBag, 1200, 9),
        TradeObservedAccount(b, plan.second.afterBag, 1800, 19),
    )

    private fun assertOnlineAfter() {
        assertEquals(plan.first.afterBag, inventory.getLoadedBagSnapshot(a))
        assertEquals(plan.second.afterBag, inventory.getLoadedBagSnapshot(b))
        assertEquals(1200 to 9, money.getLoadedMoney(a))
        assertEquals(1800 to 19, money.getLoadedMoney(b))
    }

    @Test
    fun concurrentConfirmOnlyOneReceiptSqlAndPacketAfterTwoCaches() = runBlocking {
        loadOnline()
        coEvery { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) } returns
            TradeReceiptCommit.COMMITTED_NOW
        val session = settlement()
        val signals = AtomicInteger()
        val results = (0 until 24).map { idx ->
            async(Dispatchers.Default) {
                session.confirm(if (idx % 2 == 0) firstPlayer else secondPlayer) {
                    assertOnlineAfter()
                    signals.incrementAndGet()
                }
            }
        }.awaitAll()
        assertEquals(1, results.count {
            it.outcome == ConfirmationOutcome.STARTED_AND_SETTLED &&
                it.settlement == Settlement.COMMITTED
        })
        assertEquals(1, results.count { it.outcome == ConfirmationOutcome.WAITING_FOR_PARTNER })
        assertEquals(22, results.count { it.outcome == ConfirmationOutcome.IGNORED })
        assertEquals(Phase.COMMITTED, session.currentPhase())
        assertEquals(1, signals.get())
        assertOnlineAfter()
        coVerify(exactly = 1) {
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, plan.first, plan.second)
        }
        coVerify(exactly = 0) { repo.inspectTwoCharacterTradeReceiptAtomic(any(), any(), any()) }
    }

    @Test
    fun cancelBeforeSecondConfirmationNeverStartsSql() = runBlocking {
        loadOnline()
        val session = settlement()
        assertEquals(ConfirmationOutcome.WAITING_FOR_PARTNER,
            session.confirm(firstPlayer) { fail("unexpected success") }.outcome)
        assertEquals(Abort.CANCELLED, session.cancelOrDisconnect(secondPlayer))
        assertEquals(ConfirmationOutcome.IGNORED,
            session.confirm(secondPlayer) { fail("unexpected success") }.outcome)
        assertEquals(Phase.CANCELLED, session.currentPhase())
        coVerify(exactly = 0) { repo.commitTwoCharacterTradeWithReceiptAtomic(any(), any(), any()) }
        assertEquals(bagA, inventory.getLoadedBagSnapshot(a))
    }

    @Test
    fun sqlRejectedCannotPublishOneCacheOrNotifySuccess() = runBlocking {
        loadOnline()
        coEvery { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) } returns
            TradeReceiptCommit.REJECTED
        val session = settlement()
        session.confirm(firstPlayer) { fail("unexpected") }
        val answer = session.confirm(secondPlayer) { fail("no packet on SQL rejection") }
        assertEquals(Settlement.REJECTED, answer.settlement)
        assertEquals(Phase.REJECTED, session.currentPhase())
        assertEquals(bagA, inventory.getLoadedBagSnapshot(a))
        assertEquals(bagB, inventory.getLoadedBagSnapshot(b))
        assertEquals(1000, money.getGold(a))
        coVerify(exactly = 0) { repo.inspectTwoCharacterTradeReceiptAtomic(any(), any(), any()) }
    }

    @Test
    fun lostSqlAckWithMatchedReceiptRestoresBothCachesAndNotifiesOnce() = runBlocking {
        loadOnline()
        coEvery { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) } throws
            IllegalStateException("SQL committed but transport ACK was lost")
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, any(), any()) } returns dbAfter()
        val session = settlement()
        session.confirm(firstPlayer) { fail("not yet") }
        val success = AtomicInteger()
        val result = session.confirm(secondPlayer) {
            assertOnlineAfter()
            success.incrementAndGet()
        }
        assertEquals(Settlement.COMMITTED, result.settlement)
        assertEquals(Phase.COMMITTED, session.currentPhase())
        assertEquals(1, success.get())
        assertOnlineAfter()
        coVerify(exactly = 1) { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) }
        coVerify(exactly = 1) { repo.inspectTwoCharacterTradeReceiptAtomic(id, any(), any()) }
    }

    @Test
    fun unknownSqlReceiptIsQuarantinedAndNeverRetriesDebit() = runBlocking {
        loadOnline()
        coEvery { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) } throws
            IllegalStateException("SQL result indeterminate")
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, any(), any()) } returns
            TradeReceiptInspection(TradeReceiptLookup.NOT_FOUND)
        val session = settlement()
        session.confirm(firstPlayer) { fail("no packet") }
        val result = session.confirm(secondPlayer) { fail("no packet") }
        assertEquals(Settlement.INDETERMINATE, result.settlement)
        assertEquals(Phase.INDETERMINATE, session.currentPhase())
        assertOnlineBefore()
        assertEquals(ConfirmationOutcome.IGNORED,
            session.confirm(firstPlayer) { fail("retry blocked") }.outcome)
        coVerify(exactly = 1) { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) }
    }

    private fun assertOnlineBefore() {
        assertEquals(bagA, inventory.getLoadedBagSnapshot(a))
        assertEquals(bagB, inventory.getLoadedBagSnapshot(b))
        assertEquals(1000, money.getGold(a))
        assertEquals(2000, money.getGold(b))
    }

    @Test
    fun alreadyCommittedSqlMustReconcileInsteadOfBlindCacheOverwrite() = runBlocking {
        loadOnline()
        coEvery { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) } returns
            TradeReceiptCommit.ALREADY_COMMITTED
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, any(), any()) } returns dbAfter()
        val session = settlement()
        session.confirm(firstPlayer) { fail("no packet yet") }
        assertEquals(Settlement.COMMITTED,
            session.confirm(secondPlayer) { assertOnlineAfter() }.settlement)
        assertOnlineAfter()
        coVerify(exactly = 1) { repo.inspectTwoCharacterTradeReceiptAtomic(id, any(), any()) }
    }

    @Test
    fun newerGoldAwardBlocksHistoricalSqlReceiptWithoutDataLoss() = runBlocking {
        loadOnline()
        coEvery { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) } throws
            IllegalStateException("commit ACK lost")
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, any(), any()) } coAnswers {
            // A concurrent gold award happened after receipt SQL but before
            // the reconciler could repair caches.
            money.publishCommittedTradeGoldSync(a, 1050, 9)
            dbAfter()
        }
        val session = settlement()
        session.confirm(firstPlayer) { fail("no packet") }
        val result = session.confirm(secondPlayer) { fail("no packet") }
        assertEquals(Settlement.INDETERMINATE, result.settlement)
        assertEquals(1050, money.getGold(a))
        assertEquals(bagA, inventory.getLoadedBagSnapshot(a))
        assertEquals(bagB, inventory.getLoadedBagSnapshot(b))
        coVerify(exactly = 1) { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) }
    }

    @Test
    fun changedOfferedNftMetadataFailsPlannerBeforeSql() = runBlocking {
        loadOnline()
        val changed = firstSide.copy(beforeBag = mapOf(
            3 to bagA.getValue(3).copy(affix1 = 99)
        ))
        val session = settlement(first = changed)
        session.confirm(firstPlayer) { fail("no packet") }
        val result = session.confirm(secondPlayer) { fail("no packet") }
        assertEquals(Settlement.REJECTED, result.settlement)
        assertEquals(TradeItemSnapshotPlanner.Failure.STALE_OFFER, session.lastPlanFailure)
        assertOnlineBefore()
        coVerify(exactly = 0) { repo.commitTwoCharacterTradeWithReceiptAtomic(any(), any(), any()) }
    }

    @Test
    fun packetFailureAfterBothCachesCommittedCannotRepeatSql() = runBlocking {
        loadOnline()
        coEvery { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) } returns
            TradeReceiptCommit.COMMITTED_NOW
        val session = settlement()
        session.confirm(firstPlayer) {}
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                session.confirm(secondPlayer) {
                    assertOnlineAfter()
                    throw IllegalStateException("DVT packet failed")
                }
            }
        }
        assertEquals(Phase.COMMITTED, session.currentPhase())
        assertOnlineAfter()
        assertEquals(ConfirmationOutcome.IGNORED,
            session.confirm(secondPlayer) { fail("retry blocked") }.outcome)
        coVerify(exactly = 1) { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) }
    }

    @Test
    fun failedSqlDuringConcurrentCancelNeverPublishesSuccess() = runBlocking {
        loadOnline()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { repo.commitTwoCharacterTradeWithReceiptAtomic(id, any(), any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            throw IllegalStateException("DB disconnected")
        }
        coEvery { repo.inspectTwoCharacterTradeReceiptAtomic(id, any(), any()) } returns
            TradeReceiptInspection(TradeReceiptLookup.NOT_FOUND)
        val session = settlement()
        session.confirm(firstPlayer) { fail("no success") }
        val running = async(Dispatchers.Default) {
            session.confirm(secondPlayer) { fail("no success") }
        }
        withTimeout(3_000) { entered.await() }
        assertEquals(Abort.COMMIT_IN_FLIGHT, session.cancelOrDisconnect(firstPlayer))
        release.complete(Unit)
        assertEquals(Settlement.INDETERMINATE, withTimeout(3_000) { running.await() }.settlement)
        assertOnlineBefore()
    }
}
