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
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.repositories.InventoryRepositoryImpl
import tw.tsm.persistence.tables.Inventories
import tw.tsm.persistence.tables.CharacterMoney
import java.util.UUID

/**
 * Source-only cross-character BAG+GOLD SQL settlement.
 * NO live TradeSystem, item handlers or Treasure handlers call this method.
 */
class DvtTradeTwoPlayerAtomicRollbackTest {
    private val a=201101L
    private val b=201102L
    private val repository=InventoryRepositoryImpl()
    private lateinit var db: Database
    private val bagA=mapOf(3 to ThingData(itemId=501,quantity=1,mythLevel=2))
    private val bagB=mapOf(7 to ThingData(itemId=502,quantity=2,damage=11))

    @BeforeEach
    fun setup() {
        db=Database.connect(
            "jdbc:h2:mem:dvt_cross_trade_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver="org.h2.Driver",
        )
        transaction(db) {
            SchemaUtils.create(Inventories,CharacterMoney)
            for ((id,gold) in listOf(a to 1000,b to 2000)) {
                CharacterMoney.insert {
                    it[CharacterMoney.characterId]=id
                    it[CharacterMoney.gold]=gold
                    it[CharacterMoney.auctionGold]=15
                }
            }
            Inventories.insert {
                it[Inventories.characterId]=a
                it[Inventories.storageType]=1
                it[Inventories.slot]=3
                it[Inventories.itemId]=501
                it[Inventories.quantity]=1
                it[Inventories.mythLevel]=2
            }
            Inventories.insert {
                it[Inventories.characterId]=b
                it[Inventories.storageType]=1
                it[Inventories.slot]=7
                it[Inventories.itemId]=502
                it[Inventories.quantity]=2
                it[Inventories.damage]=11
            }
        }
    }

    private fun plans(): Pair<TradeAccountSnapshot,TradeAccountSnapshot> =
        TradeAccountSnapshot(a,bagA,mapOf(3 to bagB.getValue(7)),1000,1200,15) to
        TradeAccountSnapshot(b,bagB,mapOf(7 to bagA.getValue(3)),2000,1800,15)

    private fun gold(id: Long): Int=transaction(db) {
        CharacterMoney.selectAll().where { CharacterMoney.characterId eq id }
            .single()[CharacterMoney.gold]
    }
    private fun items(id: Long): Map<Int, Pair<Int, Int>> = transaction(db) {
        Inventories.selectAll().where {
            (Inventories.characterId eq id) and (Inventories.storageType eq 1)
        }.associate { it[Inventories.slot] to (it[Inventories.itemId] to it[Inventories.quantity]) }
    }

    @Test
    fun bothBagsAndBothGoldBalancesCommitInOneTransaction()=runBlocking {
        val (first,second)=plans()
        assertTrue(repository.commitTwoCharacterTradeAtomic(first,second))
        assertEquals(mapOf(3 to (502 to 2)),items(a))
        assertEquals(mapOf(7 to (501 to 1)),items(b))
        assertEquals(1200,gold(a))
        assertEquals(1800,gold(b))
        assertFalse(repository.commitTwoCharacterTradeAtomic(first,second),
            "Replayed stale before snapshot must not credit twice")
    }

    @Test
    fun firstPlayerNeverCommitsWhenSecondPlayerSqlConstraintFails()=runBlocking {
        val (first,second)=plans()
        transaction(db) {
            // Existing B item502 passes. Attempt to replace it with A item501 fails.
            exec("ALTER TABLE inventories ADD CONSTRAINT reject_second_trade CHECK (NOT (character_id = $b AND item_id = 501))")
        }
        assertThrows(Exception::class.java) {
            runBlocking { repository.commitTwoCharacterTradeAtomic(first,second) }
        }
        assertEquals(mapOf(3 to (501 to 1)),items(a))
        assertEquals(mapOf(7 to (502 to 2)),items(b))
        assertEquals(1000,gold(a))
        assertEquals(2000,gold(b))
    }

    @Test
    fun invalidAndStalePlayerSnapshotsLeaveAllRowsUntouched()=runBlocking {
        val (first,second)=plans()
        assertFalse(repository.commitTwoCharacterTradeAtomic(
            first.copy(beforeGold=999),second))
        assertFalse(repository.commitTwoCharacterTradeAtomic(
            first.copy(beforeBag=mapOf(3 to ThingData(501,2))),second))
        assertFalse(repository.commitTwoCharacterTradeAtomic(
            first.copy(auctionGold=16),second))
        assertFalse(repository.commitTwoCharacterTradeAtomic(first,second.copy(characterId=a)))
        assertEquals(mapOf(3 to (501 to 1)),items(a))
        assertEquals(mapOf(7 to (502 to 2)),items(b))
        assertEquals(1000,gold(a))
        assertEquals(2000,gold(b))
    }

    @Test
    fun forgedDuplicationAndUnbalancedGoldCannotReachSql()=runBlocking {
        val (first,second)=plans()
        assertFalse(repository.commitTwoCharacterTradeAtomic(
            first.copy(afterBag=mapOf(3 to ThingData(502,20,damage=11))),second))
        assertFalse(repository.commitTwoCharacterTradeAtomic(
            first.copy(afterGold=1300),second))
        assertFalse(repository.commitTwoCharacterTradeAtomic(
            first.copy(afterBag=mapOf(3 to ThingData(502,2,damage=12))),second))
        assertFalse(repository.commitTwoCharacterTradeAtomic(
            first.copy(afterGold=-1),second))
        assertEquals(mapOf(3 to (501 to 1)),items(a))
        assertEquals(mapOf(7 to (502 to 2)),items(b))
        assertEquals(1000,gold(a))
    }

    @Test
    fun missingOrDifferentActualDatabaseRowNeverMintsInventory()=runBlocking {
        val (first,second)=plans()
        transaction(db) {
            exec("DELETE FROM inventories WHERE character_id=$b")
        }
        assertFalse(repository.commitTwoCharacterTradeAtomic(first,second))
        assertEquals(mapOf(3 to (501 to 1)),items(a))
        assertTrue(items(b).isEmpty())
        assertEquals(1000,gold(a))
        assertEquals(2000,gold(b))
    }
}
