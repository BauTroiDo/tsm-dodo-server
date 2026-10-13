package tw.tsm.tests

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.repositories.TradeAccountSnapshot
import tw.tsm.domain.interfaces.repositories.TradeReceiptCommit
import tw.tsm.domain.interfaces.repositories.TradeReceiptLookup
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.repositories.InventoryRepositoryImpl
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.Inventories
import tw.tsm.persistence.tables.TradeSettlementReceipts
import java.util.UUID

/**
 * STAGED ONLY, H2 SQL: the permanent receipt and 2-party BAG+gold mutation
 * must COMMIT OR ROLLBACK TOGETHER, including transport-ACK loss.
 */
class DvtTradeDurableReceiptSqlTest {
    private val a = 202101L
    private val b = 202102L
    private val repo = InventoryRepositoryImpl()
    private lateinit var db: Database
    private val bagA = mapOf(3 to ThingData(itemId = 501, quantity = 1, mythLevel = 2))
    private val bagB = mapOf(7 to ThingData(itemId = 502, quantity = 2, damage = 11))

    @BeforeEach
    fun setup() {
        db = Database.connect(
            "jdbc:h2:mem:dvt_durable_trade_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver",
        )
        transaction(db) {
            SchemaUtils.create(Inventories, CharacterMoney, TradeSettlementReceipts)
            for ((id, gold) in listOf(a to 1000, b to 2000)) {
                CharacterMoney.insert {
                    it[CharacterMoney.characterId] = id
                    it[CharacterMoney.gold] = gold
                    it[CharacterMoney.auctionGold] = 15
                }
            }
            Inventories.insert {
                it[Inventories.characterId] = a
                it[Inventories.storageType] = 1
                it[Inventories.slot] = 3
                it[Inventories.itemId] = 501
                it[Inventories.quantity] = 1
                it[Inventories.mythLevel] = 2
            }
            Inventories.insert {
                it[Inventories.characterId] = b
                it[Inventories.storageType] = 1
                it[Inventories.slot] = 7
                it[Inventories.itemId] = 502
                it[Inventories.quantity] = 2
                it[Inventories.damage] = 11
            }
        }
    }

    private fun plans(): Pair<TradeAccountSnapshot, TradeAccountSnapshot> =
        TradeAccountSnapshot(a, bagA, mapOf(3 to bagB.getValue(7)), 1000, 1200, 15) to
            TradeAccountSnapshot(b, bagB, mapOf(7 to bagA.getValue(3)), 2000, 1800, 15)

    private fun gold(character: Long) = transaction(db) {
        CharacterMoney.selectAll().where { CharacterMoney.characterId eq character }
            .single()[CharacterMoney.gold]
    }

    private fun items(character: Long) = transaction(db) {
        Inventories.selectAll().where {
            (Inventories.characterId eq character) and (Inventories.storageType eq 1)
        }.associate {
            it[Inventories.slot] to
                ThingData(itemId = it[Inventories.itemId], quantity = it[Inventories.quantity],
                    damage = it[Inventories.damage], mythLevel = it[Inventories.mythLevel])
        }
    }

    private fun receipts() = transaction(db) {
        TradeSettlementReceipts.selectAll().count().toInt()
    }

    @Test
    fun receiptAndBothBagsAndGoldCommitInOneAtomicTransaction() = runBlocking {
        val id = UUID.randomUUID().toString()
        val (first, second) = plans()
        assertEquals(
            TradeReceiptCommit.COMMITTED_NOW,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, first, second),
        )
        assertEquals(1, receipts())
        assertEquals(mapOf(3 to bagB.getValue(7)), items(a))
        assertEquals(mapOf(7 to bagA.getValue(3)), items(b))
        assertEquals(1200, gold(a))
        assertEquals(1800, gold(b))
        assertEquals(
            TradeReceiptLookup.MATCHED,
            repo.findTwoCharacterTradeReceipt(id, first, second),
        )
    }

    @Test
    fun lostNetworkAcknowledgementCanReconcileAfterRepositoryRecreated() = runBlocking {
        val id = UUID.randomUUID().toString()
        val (first, second) = plans()
        assertEquals(
            TradeReceiptLookup.NOT_FOUND,
            repo.findTwoCharacterTradeReceipt(id, first, second),
        )
        assertEquals(TradeReceiptCommit.COMMITTED_NOW,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, first, second))
        val freshRepository = InventoryRepositoryImpl()
        assertEquals(TradeReceiptLookup.MATCHED,
            freshRepository.findTwoCharacterTradeReceipt(id, first, second))
        assertEquals(TradeReceiptCommit.ALREADY_COMMITTED,
            freshRepository.commitTwoCharacterTradeWithReceiptAtomic(id, first, second))
        assertEquals(1, receipts())
        assertEquals(1200, gold(a))
        assertEquals(1800, gold(b))
    }

    @Test
    fun reversingArgumentsCannotReuseOperationToCreateSecondTransfer() = runBlocking {
        val id = UUID.randomUUID().toString()
        val (first, second) = plans()
        assertEquals(TradeReceiptCommit.COMMITTED_NOW,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, first, second))
        assertEquals(TradeReceiptCommit.ALREADY_COMMITTED,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, second, first))
        assertEquals(TradeReceiptLookup.MATCHED,
            repo.findTwoCharacterTradeReceipt(id, second, first))
        assertEquals(1, receipts())
    }

    @Test
    fun reusedUuidWithChangedFullSnapshotIsBlockedBeforeDbMutation() = runBlocking {
        val id = UUID.randomUUID().toString()
        val (first, second) = plans()
        assertEquals(TradeReceiptCommit.COMMITTED_NOW,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, first, second))
        val changed = first.copy(auctionGold = 14)
        assertEquals(TradeReceiptCommit.TOKEN_CONFLICT,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, changed, second))
        assertEquals(TradeReceiptLookup.TOKEN_CONFLICT,
            repo.findTwoCharacterTradeReceipt(id, changed, second))
        assertEquals(1, receipts())
        assertEquals(1200, gold(a))
        assertEquals(1800, gold(b))
    }

    @Test
    fun constraintFailureAfterFirstPlayerChangesRollsBackAllAndReceipt() = runBlocking {
        val id = UUID.randomUUID().toString()
        val (first, second) = plans()
        transaction(db) {
            exec("ALTER TABLE inventories ADD CONSTRAINT reject_second_receipt CHECK (NOT (character_id = $b AND item_id = 501))")
        }
        assertThrows(Exception::class.java) {
            runBlocking {
                repo.commitTwoCharacterTradeWithReceiptAtomic(id, first, second)
            }
        }
        assertEquals(0, receipts())
        assertEquals(1000, gold(a))
        assertEquals(2000, gold(b))
        assertEquals(mapOf(3 to bagA.getValue(3)), items(a))
        assertEquals(mapOf(7 to bagB.getValue(7)), items(b))
        assertEquals(TradeReceiptLookup.NOT_FOUND,
            repo.findTwoCharacterTradeReceipt(id, first, second))
    }

    @Test
    fun staleSqlSnapshotAndAnotherOperationIdCannotCreditTwice() = runBlocking {
        val id = UUID.randomUUID().toString()
        val replayId = UUID.randomUUID().toString()
        val (first, second) = plans()
        assertEquals(TradeReceiptCommit.COMMITTED_NOW,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, first, second))
        assertEquals(TradeReceiptCommit.REJECTED,
            repo.commitTwoCharacterTradeWithReceiptAtomic(replayId, first, second))
        assertEquals(TradeReceiptLookup.NOT_FOUND,
            repo.findTwoCharacterTradeReceipt(replayId, first, second))
        assertEquals(1, receipts())
        assertEquals(1200, gold(a))
        assertEquals(1800, gold(b))
    }

    @Test
    fun malformedUuidAndInvalidPlanCannotCreateReceipt() = runBlocking {
        val (first, second) = plans()
        assertEquals(TradeReceiptCommit.REJECTED,
            repo.commitTwoCharacterTradeWithReceiptAtomic("not-a-uuid", first, second))
        assertEquals(TradeReceiptLookup.TOKEN_CONFLICT,
            repo.findTwoCharacterTradeReceipt("not-a-uuid", first, second))
        assertEquals(TradeReceiptCommit.REJECTED,
            repo.commitTwoCharacterTradeWithReceiptAtomic(UUID.randomUUID().toString(),
                first.copy(afterGold = 1201), second))
        assertEquals(0, receipts())
        assertEquals(1000, gold(a))
        assertEquals(2000, gold(b))
    }

    @Test
    fun atomicInspectionReadsMatchedReceiptAndBothRealDatabaseAfterStates() = runBlocking {
        val id = UUID.randomUUID().toString()
        val (first, second) = plans()
        val none = repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second)
        assertEquals(TradeReceiptLookup.NOT_FOUND, none.receipt)
        assertNull(none.first)
        assertNull(none.second)

        assertEquals(TradeReceiptCommit.COMMITTED_NOW,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, first, second))
        val observed = repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second)
        assertEquals(TradeReceiptLookup.MATCHED, observed.receipt)
        assertEquals(first.afterBag, observed.first?.bag)
        assertEquals(second.afterBag, observed.second?.bag)
        assertEquals(1200, observed.first?.gold)
        assertEquals(1800, observed.second?.gold)
        assertEquals(first.auctionGold, observed.first?.auctionGold)
        assertEquals(second.auctionGold, observed.second?.auctionGold)
        val reversed = repo.inspectTwoCharacterTradeReceiptAtomic(id, second, first)
        assertEquals(second.characterId, reversed.first?.characterId)
        assertEquals(first.characterId, reversed.second?.characterId)
        assertEquals(TradeReceiptLookup.MATCHED, reversed.receipt)
    }

    @Test
    fun atomicInspectionDetectsLaterRealSqlWritesInsteadOfAssumingHistoricalAfter() = runBlocking {
        val id = UUID.randomUUID().toString()
        val (first, second) = plans()
        assertEquals(TradeReceiptCommit.COMMITTED_NOW,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, first, second))
        transaction(db) {
            exec("UPDATE character_money SET gold = 1250 WHERE character_id = $a")
        }
        val observed = repo.inspectTwoCharacterTradeReceiptAtomic(id, first, second)
        assertEquals(TradeReceiptLookup.MATCHED, observed.receipt)
        assertEquals(1250, observed.first?.gold)
        assertEquals(1800, observed.second?.gold)
        assertNotEquals(first.afterGold, observed.first?.gold)
        assertEquals(1, receipts())
    }

    @Test
    fun atomicInspectionForForgedPlanAndInvalidUuidReturnsNoAccountSnapshots() = runBlocking {
        val id = UUID.randomUUID().toString()
        val (first, second) = plans()
        assertEquals(TradeReceiptCommit.COMMITTED_NOW,
            repo.commitTwoCharacterTradeWithReceiptAtomic(id, first, second))
        val mismatch = repo.inspectTwoCharacterTradeReceiptAtomic(
            id, first.copy(afterGold = 1201), second
        )
        assertEquals(TradeReceiptLookup.TOKEN_CONFLICT, mismatch.receipt)
        assertNull(mismatch.first)
        assertNull(mismatch.second)
        val malformed = repo.inspectTwoCharacterTradeReceiptAtomic("invalid", first, second)
        assertEquals(TradeReceiptLookup.TOKEN_CONFLICT, malformed.receipt)
        assertNull(malformed.first)
        assertNull(malformed.second)
        assertEquals(1200, gold(a))
        assertEquals(1800, gold(b))
    }

    @Test
    fun originalUnreceiptedApiRetainsExactPreviousBehavior() = runBlocking {
        val (first, second) = plans()
        assertTrue(repo.commitTwoCharacterTradeAtomic(first, second))
        assertFalse(repo.commitTwoCharacterTradeAtomic(first, second))
        assertEquals(0, receipts())
        assertEquals(1200, gold(a))
        assertEquals(1800, gold(b))
    }
}
