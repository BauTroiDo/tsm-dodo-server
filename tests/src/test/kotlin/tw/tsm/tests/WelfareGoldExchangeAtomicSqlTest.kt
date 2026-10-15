package tw.tsm.tests

import java.util.UUID
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
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.repositories.InventoryRepositoryImpl
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.CharacterWelfareExchangeReceipts
import tw.tsm.persistence.tables.Inventories

class WelfareGoldExchangeAtomicSqlTest {
    private val repo=InventoryRepositoryImpl()
    @BeforeEach fun setUp() {
        Database.connect("jdbc:h2:mem:welfare_gold_"+UUID.randomUUID()+
            ";MODE=MySQL;DB_CLOSE_DELAY=-1",driver="org.h2.Driver")
        transaction {
            SchemaUtils.create(Inventories,CharacterMoney,CharacterWelfareExchangeReceipts)
            CharacterMoney.insert {
                it[characterId]=77L
                it[gold]=200_000
                it[auctionGold]=0
            }
            Inventories.insert {
                it[characterId]=77L
                it[storageType]=EThings.BAG
                it[slot]=1
                it[itemId]=46672
                it[quantity]=20
            }
        }
    }
    private fun gold()=transaction {
        CharacterMoney.selectAll()
            .where { CharacterMoney.characterId eq 77L }
            .single()[CharacterMoney.gold]
    }
    private fun bag(slot:Int)=transaction {
        Inventories.selectAll().where {
            (Inventories.characterId eq 77L) and
            (Inventories.storageType eq EThings.BAG) and
            (Inventories.slot eq slot)
        }.singleOrNull()?.let { it[Inventories.itemId] to it[Inventories.quantity] }
    }
    private fun receipts()=transaction { CharacterWelfareExchangeReceipts.selectAll().toList() }

    @Test fun exactGoldAndBagExchangeCreatesOneReceipt() = runBlocking {
        repo.saveWelfareGoldExchangeAtomic(
            77L,mapOf(1 to null,2 to ThingData(46580,1)),
            201,1,46580,1,200_000,0,100_000)
        assertEquals(100_000,gold())
        assertNull(bag(1))
        assertEquals(46580 to 1,bag(2))
        assertEquals(1,receipts().size)
        assertEquals(100_000,receipts().single()[CharacterWelfareExchangeReceipts.goldSpent])
    }

    @Test fun insufficientFundsFailClosedWithoutBagMutation() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.saveWelfareGoldExchangeAtomic(
                77L,mapOf(1 to null,2 to ThingData(46580,1)),
                201,1,46580,1,381,0,100_000) }
        }
        assertEquals(200_000,gold())
        assertEquals(46672 to 20,bag(1))
        assertNull(bag(2))
        assertEquals(0,receipts().size)
    }

    @Test fun staleWalletDoesNotOverwriteGoldOrGiveGift() = runBlocking {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.saveWelfareGoldExchangeAtomic(
                77L,mapOf(1 to null,2 to ThingData(46580,1)),
                201,1,46580,1,100_000,0,50_000) }
        }
        assertEquals(200_000,gold())
        assertEquals(46672 to 20,bag(1))
        assertEquals(0,receipts().size)
    }

    @Test fun failingReceiptRollsBackGoldAndBag() = runBlocking {
        transaction {
            exec("ALTER TABLE character_welfare_exchange_receipts ADD CONSTRAINT gold_zero CHECK (gold_spent <= 0)")
        }
        assertThrows(Exception::class.java) {
            runBlocking { repo.saveWelfareGoldExchangeAtomic(
                77L,mapOf(1 to null,2 to ThingData(46580,1)),
                201,1,46580,1,200_000,0,100_000) }
        }
        assertEquals(200_000,gold())
        assertEquals(46672 to 20,bag(1))
        assertNull(bag(2))
        assertEquals(0,receipts().size)
    }
}
