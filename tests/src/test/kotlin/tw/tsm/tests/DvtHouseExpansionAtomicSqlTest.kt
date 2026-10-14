package tw.tsm.tests

import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.persistence.repositories.HouseExpansionRepositoryImpl
import tw.tsm.persistence.repositories.InventoryRepositoryImpl
import tw.tsm.persistence.tables.CharacterHouseExpansion
import tw.tsm.persistence.tables.CharacterHouseFurnitureOwned
import tw.tsm.persistence.tables.CharacterMoney

/** Isolated H2 only: no DEV accounts, currency, or furniture are changed. */
class DvtHouseExpansionAtomicSqlTest {
    private val ownerId = 710031L
    private val house = HouseExpansionRepositoryImpl()
    private lateinit var db: Database

    @BeforeEach
    fun before() {
        db = Database.connect(
            "jdbc:h2:mem:dvt_house_paid_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver"
        )
        transaction(db) {
            SchemaUtils.create(CharacterMoney, CharacterHouseFurnitureOwned, CharacterHouseExpansion)
            CharacterMoney.insert {
                it[characterId] = ownerId
                it[gold] = 981414
                it[auctionGold] = 0
            }
            repeat(6) { n ->
                CharacterHouseFurnitureOwned.insert {
                    it[characterId] = ownerId
                    it[furnitureId] = 62001 + n
                    it[quantity] = 1
                }
            }
        }
    }

    private fun balance(): Int = transaction(db) {
        CharacterMoney.selectAll().where { CharacterMoney.characterId eq ownerId }
            .single()[CharacterMoney.gold]
    }

    @Test
    fun twoUpgradesDebitExactlyOnceAndPersistAcrossRepositoryReload() = runBlocking {
        val wallet = MoneySystem(InventoryRepositoryImpl())
        wallet.loadMoney(ownerId)
        assertTrue(wallet.purchaseHouseExpansionGold(ownerId, 100000) { gold, auction ->
            house.purchaseNextLevel(ownerId, 1, gold, auction, 6, 100000)
        })
        assertEquals(881414, balance())
        assertEquals(2, HouseExpansionRepositoryImpl().loadLevel(ownerId))
        assertFalse(wallet.purchaseHouseExpansionGold(ownerId, 100000) { gold, auction ->
            house.purchaseNextLevel(ownerId, 1, gold, auction, 6, 100000)
        })
        assertEquals(881414, balance())
        assertTrue(wallet.purchaseHouseExpansionGold(ownerId, 500000) { gold, auction ->
            house.purchaseNextLevel(ownerId, 2, gold, auction, 6, 500000)
        })
        assertEquals(381414, balance())
        assertEquals(381414, wallet.getGold(ownerId))
        assertEquals(3, HouseExpansionRepositoryImpl().loadLevel(ownerId))
    }

    @Test
    fun missingKindsOrStaleCashRejectWithoutAnyWrite() = runBlocking {
        assertFalse(house.purchaseNextLevel(ownerId, 1, 981414, 0, 7, 100000))
        assertFalse(house.purchaseNextLevel(ownerId, 1, 981413, 0, 6, 100000))
        assertFalse(house.purchaseNextLevel(ownerId, 1, 981414, 1, 6, 100000))
        assertFalse(house.purchaseNextLevel(ownerId, 1, 981414, 0, 6, 1000000))
        assertEquals(981414, balance())
        assertEquals(1, house.loadLevel(ownerId))
    }

    @Test
    fun sqlConstraintFailureRollsBackBothGoldAndLevel() = runBlocking {
        transaction(db) {
            exec("ALTER TABLE character_money ADD CONSTRAINT dvt_guard_gold CHECK (gold >= 900000)")
        }
        assertTrue(runCatching {
            house.purchaseNextLevel(ownerId, 1, 981414, 0, 6, 100000)
        }.isFailure)
        assertEquals(981414, balance())
        assertEquals(1, house.loadLevel(ownerId))
    }

    @Test
    fun concurrentDuplicateRequestsPayExactlyOnce() = runBlocking {
        val wallet = MoneySystem(InventoryRepositoryImpl())
        wallet.loadMoney(ownerId)
        val attempts = (1..12).map {
            async(Dispatchers.Default) {
                wallet.purchaseHouseExpansionGold(ownerId, 100000) { gold, auction ->
                    house.purchaseNextLevel(ownerId, 1, gold, auction, 6, 100000)
                }
            }
        }.awaitAll()
        assertEquals(1, attempts.count { it })
        assertEquals(2, house.loadLevel(ownerId))
        assertEquals(881414, balance())
        assertEquals(881414, wallet.getGold(ownerId))
    }

    @Test
    fun rejectedPaymentLeavesMoneySystemCacheUnchanged() = runBlocking {
        val wallet = MoneySystem(InventoryRepositoryImpl())
        wallet.loadMoney(ownerId)
        assertFalse(wallet.purchaseHouseExpansionGold(ownerId, 100000) { gold, auction ->
            house.purchaseNextLevel(ownerId, 1, gold, auction, 7, 100000)
        })
        assertEquals(981414, wallet.getGold(ownerId))
        assertEquals(981414, balance())
        assertEquals(1, house.loadLevel(ownerId))
    }
}
