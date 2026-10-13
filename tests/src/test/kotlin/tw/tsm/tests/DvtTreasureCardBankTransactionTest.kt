package tw.tsm.tests

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.persistence.repositories.TreasureRepositoryImpl
import tw.tsm.persistence.tables.CharacterTreasures
import tw.tsm.persistence.tables.Inventories
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Dormant physical Item_C kind98 BAG -> S123 bank SQL transaction tests.
 * Game handler and runtime cache are intentionally NOT wired to this code.
 */
class DvtTreasureCardBankTransactionTest {
    private val charId = 89345L
    private val repo = TreasureRepositoryImpl(
        originalLevelCosts = emptyMap(),
        originalCardItemIds = mapOf(1 to 25601, 2 to 25602),
    )
    private lateinit var db: Database

    @BeforeEach
    fun init() {
        db = Database.connect(
            "jdbc:h2:mem:treasure_bank_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver",
        )
        transaction(db) { SchemaUtils.create(CharacterTreasures, Inventories) }
    }

    private fun place(itemId: Int = 25601, count: Int = 5, slot: Int = 7) {
        transaction(db) {
            Inventories.insert {
                it[Inventories.characterId] = charId
                it[Inventories.storageType] = 1
                it[Inventories.slot] = slot
                it[Inventories.itemId] = itemId
                it[Inventories.quantity] = count
            }
        }
    }

    private fun bagQty(): Int? = transaction(db) {
        Inventories.selectAll().where { Inventories.characterId eq charId }
            .singleOrNull()?.get(Inventories.quantity)
    }

    @Test
    fun firstValidBankedCopyDebitsActualBagAndCreatesExactlyOneS123Row() = runBlocking {
        place(count = 5)
        assertTrue(repo.load(charId).isEmpty())
        assertTrue(repo.bankCard(charId,1,0,7,2))
        assertEquals(3, bagQty())
        val state = repo.load(charId).single()
        assertEquals(1, state.treasureId)
        assertEquals(2, state.cardCopiesTotal)
        assertEquals(0, state.level)
        assertEquals(0, state.star)
        assertEquals(0, state.starExp)
        assertEquals(1L, state.version)
        assertFalse(repo.bankCard(charId,1,0,7,2))
        assertEquals(3,bagQty())
        assertEquals(2,repo.load(charId).single().cardCopiesTotal)
        assertTrue(repo.bankCard(charId,1,1,7,3))
        assertNull(bagQty())
        assertEquals(5,repo.load(charId).single().cardCopiesTotal)
        assertEquals(2L,repo.load(charId).single().version)
    }

    @Test
    fun wrongItemSlotQuantityOrTreasureIdCannotCreditBank() = runBlocking {
        place(itemId=25602,count=2)
        assertFalse(repo.bankCard(charId,1,0,7,1))
        assertFalse(repo.bankCard(charId,2,0,8,1))
        assertFalse(repo.bankCard(charId,2,0,7,3))
        assertFalse(repo.bankCard(charId,114,0,7,1)) // trusted map unavailable
        assertFalse(repo.bankCard(charId,2,0,7,0))
        assertFalse(repo.bankCard(charId,2,0,7,-1))
        assertEquals(2,bagQty())
        assertTrue(repo.load(charId).isEmpty())
    }

    @Test
    fun existingBalanceCanOnlyBeIncrementedOnceAtExpectedVersion() = runBlocking {
        place(count=5)
        assertTrue(repo.bankCard(charId,1,0,7,1))
        assertTrue(repo.bankCard(charId,1,1,7,1))
        assertFalse(repo.bankCard(charId,1,1,7,1))
        assertEquals(3,bagQty())
        assertEquals(2,repo.load(charId).single().cardCopiesTotal)
        assertEquals(2L,repo.load(charId).single().version)
    }

    @Test
    fun failedFinalS123InsertRollsBackPhysicalBagDebit() = runBlocking {
        place(count=5)
        transaction(db) {
            exec("ALTER TABLE character_treasures ADD CONSTRAINT bank_insert_block CHECK (card_copies_total = 0)")
        }
        assertThrows(Exception::class.java) {
            runBlocking { repo.bankCard(charId,1,0,7,2) }
        }
        assertEquals(5,bagQty())
        assertTrue(repo.load(charId).isEmpty())
    }

    @Test
    fun failedFinalS123UpdateRollsBackBagDebitAndPreservesVersion() = runBlocking {
        place(count=5)
        assertTrue(repo.bankCard(charId,1,0,7,1))
        transaction(db) {
            exec("ALTER TABLE character_treasures ADD CONSTRAINT bank_update_block CHECK (card_copies_total <= 1)")
        }
        assertThrows(Exception::class.java) {
            runBlocking { repo.bankCard(charId,1,1,7,2) }
        }
        assertEquals(4,bagQty())
        val state=repo.load(charId).single()
        assertEquals(1,state.cardCopiesTotal)
        assertEquals(1L,state.version)    }

    @Test
    fun cardDefinitionMapMayNotMintDuplicateIdsOrUnknownId() {
        assertThrows(IllegalArgumentException::class.java) {
            TreasureRepositoryImpl(emptyMap(),mapOf(1 to 25601,2 to 25601))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasureRepositoryImpl(emptyMap(),mapOf(115 to 25715))
        }
        val root=generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_Item_C.dat")) }
        val items=ItemDatLoader.load(Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_Item_C.dat")))
        for (i in 1..114) {
            val definition=requireNotNull(items[25600+i])
            assertEquals(98,definition.kind)
            assertEquals(i,definition.skillLink)
        }
    }

    @Test
    fun unwiredEmptyCatalogCanNeverBankCard() = runBlocking {
        val disabled=TreasureRepositoryImpl(emptyMap())
        place()
        assertFalse(disabled.bankCard(charId,1,0,7,1))
        assertEquals(5,bagQty())
        assertTrue(disabled.load(charId).isEmpty())
    }
}
