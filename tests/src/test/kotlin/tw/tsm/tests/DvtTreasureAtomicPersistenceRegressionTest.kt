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
import tw.tsm.domain.interfaces.repositories.TreasureLevelRequirement
import tw.tsm.persistence.repositories.TreasureRepositoryImpl
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.CharacterTreasures
import tw.tsm.persistence.tables.Inventories
import java.util.UUID

/** Staged SQL transaction tests; no DEV migration or game handler wiring. */
class DvtTreasureAtomicPersistenceRegressionTest {
    private val charId = 9393L
    private val costs = mapOf(
        (1 to 1) to TreasureLevelRequirement(1,1,1,20,100_000),
        (1 to 2) to TreasureLevelRequirement(1,2,2,40,200_000),
    )
    private val repo = TreasureRepositoryImpl(costs)
    private lateinit var db: Database

    @BeforeEach
    fun setup() {
        db = Database.connect(
            "jdbc:h2:mem:treasure_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver="org.h2.Driver",
        )
        transaction(db) {
            SchemaUtils.create(CharacterTreasures,Inventories,CharacterMoney)
        }
    }

    private fun seed(cards: Int = 3, gold: Int = 500_000, materialId: Int = 29869, materialQuantity: Int = 100) {
        transaction(db) {
            CharacterTreasures.insert {
                it[CharacterTreasures.characterId] = charId
                it[CharacterTreasures.treasureId] = 1
                it[CharacterTreasures.cardCopiesTotal] = cards
                it[CharacterTreasures.level] = 0
                it[CharacterTreasures.star] = 0
                it[CharacterTreasures.starExp] = 0
                it[CharacterTreasures.version] = 0L
            }
            CharacterMoney.insert {
                it[CharacterMoney.characterId] = charId
                it[CharacterMoney.gold] = gold
            }
            Inventories.insert {
                it[Inventories.characterId] = charId
                it[Inventories.storageType] = 1
                it[Inventories.slot] = 7
                it[Inventories.itemId] = materialId
                it[Inventories.quantity] = materialQuantity
            }
        }
    }

    private fun snapshot(): Triple<Int, Int, Int?> = transaction(db) {
        val treasure = CharacterTreasures.selectAll().single()
        val gold = CharacterMoney.selectAll().single()[CharacterMoney.gold]
        val quantity = Inventories.selectAll().firstOrNull()?.get(Inventories.quantity)
        Triple(treasure[CharacterTreasures.level],gold,quantity)
    }

    @Test
    fun validSourceLevelPurchaseIsAtomicAndReplayCannotDoubleDebit() = runBlocking {
        seed()
        assertTrue(repo.purchaseLevel(charId,1,0,0,7))
        assertEquals(Triple(1,400_000,80),snapshot())
        assertFalse(repo.purchaseLevel(charId,1,0,0,7))
        assertEquals(Triple(1,400_000,80),snapshot())
        assertTrue(repo.purchaseLevel(charId,1,1,1,7))
        assertEquals(Triple(2,200_000,40),snapshot())
        assertFalse(repo.purchaseLevel(charId,1,1,1,7))
        assertEquals(Triple(2,200_000,40),snapshot())
        val states=repo.load(charId)
        assertEquals(1,states.size)
        assertEquals(2,states.single().level)
        assertEquals(2L,states.single().version)
        assertEquals(3,states.single().cardCopiesTotal)
    }

    @Test
    fun wrongSlotAndIncorrectMaterialIdNeverDebit() = runBlocking {
        seed(materialId=29870)
        assertFalse(repo.purchaseLevel(charId,1,0,0,7))
        assertFalse(repo.purchaseLevel(charId,1,0,0,8))
        assertEquals(Triple(0,500_000,100),snapshot())
    }

    @Test
    fun insufficientCardCopiesOrGoldOrMaterialsNeverPartiallyCharge() = runBlocking {
        seed(cards=0,gold=0,materialQuantity=0)
        assertFalse(repo.purchaseLevel(charId,1,0,0,7))
        assertEquals(Triple(0,0,0),snapshot())
        val state=repo.load(charId).single()
        assertEquals(0L,state.version)
    }

    @Test
    fun missingTreasureOwnershipCannotCreateOrSpendAnything() = runBlocking {
        transaction(db) {
            CharacterMoney.insert {
                it[CharacterMoney.characterId] = charId
                it[CharacterMoney.gold] = 1_000_000
            }
        }
        assertFalse(repo.purchaseLevel(charId,1,0,0,7))
        assertTrue(repo.load(charId).isEmpty())
        assertEquals(1_000_000,transaction(db) { CharacterMoney.selectAll().single()[CharacterMoney.gold] })
    }

    @Test
    fun invalidIdUnknownTrustedCurveAndImpossibleVersionFailClosed() = runBlocking {
        seed()
        assertFalse(repo.purchaseLevel(charId,114,0,0,7))
        assertFalse(repo.purchaseLevel(charId,1,0,999,7))
        assertFalse(repo.purchaseLevel(charId,1,2,0,7))
        assertFalse(repo.purchaseLevel(charId,1,0,0,-1))
        assertEquals(Triple(0,500_000,100),snapshot())
    }

    @Test
    fun fullStackIsDeletedOnlyOnCommittedLevelPurchase() = runBlocking {
        seed(materialQuantity=20)
        assertTrue(repo.purchaseLevel(charId,1,0,0,7))
        assertEquals(Triple(1,400_000,null),snapshot())
    }

    @Test
    fun sourcesCannotBePassedThroughUntrustedCostMapKey() {
        assertThrows(IllegalArgumentException::class.java) {
            TreasureRepositoryImpl(mapOf((1 to 2) to TreasureLevelRequirement(1,1,1,20,100_000)))
        }
    }
}
