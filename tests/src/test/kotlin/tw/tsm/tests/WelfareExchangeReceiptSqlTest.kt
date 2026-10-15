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
import tw.tsm.persistence.tables.CharacterWelfareExchangeReceipts
import tw.tsm.persistence.tables.Inventories

class WelfareExchangeReceiptSqlTest {
    private val repo = InventoryRepositoryImpl()
    private lateinit var database: Database

    @BeforeEach fun initialize() {
        database = Database.connect("jdbc:h2:mem:welfare_receipt_"+UUID.randomUUID()+
            ";MODE=MySQL;DB_CLOSE_DELAY=-1",driver="org.h2.Driver")
        transaction(database) {
            SchemaUtils.create(Inventories,CharacterWelfareExchangeReceipts)
            Inventories.insert {
                it[characterId] = 71L
                it[storageType] = EThings.BAG
                it[slot] = 1
                it[itemId] = 5123
                it[quantity] = 5
            }
        }
    }
    private fun bag(slot: Int) = transaction(database) {
        Inventories.selectAll().where {
            (Inventories.characterId eq 71L) and
                (Inventories.storageType eq EThings.BAG) and
                (Inventories.slot eq slot)
        }.singleOrNull()?.let { it[Inventories.itemId] to it[Inventories.quantity] }
    }
    private fun receipts() = transaction(database) {
        CharacterWelfareExchangeReceipts.selectAll().toList()
    }

    @Test fun paidExchangeCommitsBagAndDurableReceiptTogether() = runBlocking {
        repo.saveWelfareExchangeAtomic(
            71L,mapOf(1 to ThingData(5123,3),2 to ThingData(6123,2)),
            recipeDay=12,exchangeCount=2,resultItemId=6123,resultQuantity=2)
        assertEquals(5123 to 3,bag(1))
        assertEquals(6123 to 2,bag(2))
        val all=receipts()
        assertEquals(1,all.size)
        assertEquals(71L,all[0][CharacterWelfareExchangeReceipts.characterId])
        assertEquals(12,all[0][CharacterWelfareExchangeReceipts.recipeDay])
        assertEquals(2,all[0][CharacterWelfareExchangeReceipts.exchangeCount])
        assertEquals(6123,all[0][CharacterWelfareExchangeReceipts.resultItemId])
    }

    @Test fun failingReceiptConstraintRollsBackEveryBagSlot() = runBlocking {
        transaction(database) {
            exec("ALTER TABLE character_welfare_exchange_receipts ADD CONSTRAINT quantity_zero CHECK (result_quantity <= 0)")
        }
        assertThrows(Exception::class.java) {
            runBlocking {
                repo.saveWelfareExchangeAtomic(
                    71L,mapOf(1 to ThingData(5123,3),2 to ThingData(6123,2)),
                    recipeDay=12,exchangeCount=2,resultItemId=6123,resultQuantity=2)
            }
        }
        assertEquals(5123 to 5,bag(1))
        assertNull(bag(2))
        assertEquals(0,receipts().size)
    }

    @Test fun legitimateRepeatedExchangeWritesDistinctPaidReceipts() = runBlocking {
        repo.saveWelfareExchangeAtomic(
            71L,mapOf(1 to ThingData(5123,4),2 to ThingData(6123,1)),
            recipeDay=12,exchangeCount=1,resultItemId=6123,resultQuantity=1)
        repo.saveWelfareExchangeAtomic(
            71L,mapOf(1 to ThingData(5123,3),2 to ThingData(6123,2)),
            recipeDay=12,exchangeCount=1,resultItemId=6123,resultQuantity=1)
        assertEquals(2,receipts().size)
        assertEquals(5123 to 3,bag(1))
        assertEquals(6123 to 2,bag(2))
    }

    @Test fun invalidRecipeMetadataMustNotMutateBagOrReceipt() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.saveWelfareExchangeAtomic(
                    71L,mapOf(1 to ThingData(5123,4),2 to ThingData(6123,1)),
                    recipeDay=12,exchangeCount=51,resultItemId=6123,resultQuantity=1)
            }
        }
        assertEquals(5123 to 5,bag(1))
        assertNull(bag(2))
        assertEquals(0,receipts().size)
    }
}
