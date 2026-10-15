package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import tw.tsm.domain.data.ItemDef
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.persistence.repositories.InventoryRepositoryImpl
import tw.tsm.persistence.repositories.MiningRepositoryImpl
import tw.tsm.persistence.tables.CharacterMining
import tw.tsm.persistence.tables.Inventories

/** Standalone in-memory H2. Never uses DEV MySQL or the user's items. */
class DvtMiningFuelAtomicSqlTest {
    private val character = 740031L
    private val slot = 7
    private val foodId = MiningSystem.FUEL_ITEM_IDS[0]
    private lateinit var db: Database

    @BeforeEach fun setup() {
        db = Database.connect(
            "jdbc:h2:mem:dvt_mining_fuel_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(db) {
            SchemaUtils.create(Inventories, CharacterMining)
            Inventories.insert {
                it[characterId] = character
                it[storageType] = 1
                it[Inventories.slot] = slot
                it[itemId] = foodId
                it[quantity] = 3
            }
            CharacterMining.insert {
                it[characterId] = character
                it[mineType] = 1
                it[full1] = 0
                it[full2] = 0
                it[full3] = 0
                it[full4] = 0
                it[updatedAtMillis] = 1_000_000L
                it[dayLimit] = false
            }
        }
    }

    private fun dbFood(): Int = transaction(db) {
        Inventories.selectAll().where {
            (Inventories.characterId eq character) and (Inventories.slot eq slot)
        }.singleOrNull()?.get(Inventories.quantity) ?: 0
    }

    private suspend fun service(): Pair<InventorySystem, MiningSystem> {
        val food = mockk<ItemDef> { every { threshold } returns 10 }
        val inventory = InventorySystem(
            InventoryRepositoryImpl(), mapOf(foodId to food), mockk<RoleCountSystem>(relaxed = true)
        )
        inventory.loadCharacterData(character)
        return inventory to MiningSystem(
            MiningRepositoryImpl(), inventory, mapOf(foodId to food), nowMillis = { 1_000_000L }
        )
    }

    @Test fun successfulRefillDebitsExactlyOneFoodAndPersistsFull() = runBlocking {
        val (bag, miner) = service()
        val result = miner.refill(character, slot, foodId, 1, 3)
        assertTrue(result.success)
        assertEquals(10, result.addedFull)
        assertEquals(2, dbFood())
        assertEquals(2, bag.getBagItem(character, slot)?.quantity)
        assertEquals(listOf(10, 0, 0, 0), MiningRepositoryImpl().load(character)?.machineFull)
    }

    @Test fun rejectedOverfillDoesNotConsumeFood() = runBlocking {
        transaction(db) {
            exec("UPDATE character_mining SET full_1=65530 WHERE character_id=$character")
        }
        val (bag, miner) = service()
        val result = miner.refill(character, slot, foodId, 1, 3)
        assertFalse(result.success)
        assertEquals(3, dbFood())
        assertEquals(3, bag.getBagItem(character, slot)?.quantity)
        assertEquals(65530, MiningRepositoryImpl().load(character)?.machineFull?.first())
    }

    @Test fun failedMinePersistenceRollsBackBAGAndCache() = runBlocking {
        transaction(db) {
            exec("ALTER TABLE character_mining ADD CONSTRAINT fuel_rollback CHECK (full_1 <= 0)")
        }
        val (bag, miner) = service()
        assertTrue(runCatching { miner.refill(character, slot, foodId, 1, 3) }.isFailure)
        assertEquals(3, dbFood())
        assertEquals(3, bag.getBagItem(character, slot)?.quantity)
        assertEquals(0, MiningRepositoryImpl().load(character)?.machineFull?.first())
    }

    @Test fun concurrentRequestsNeverSpendMoreFoodThanAvailable() = runBlocking {
        val (bag, miner) = service()
        val attempts = (1..12).map {
            async(Dispatchers.Default) { miner.refill(character, slot, foodId, 1, 3) }
        }.awaitAll()
        assertEquals(3, attempts.count { it.success })
        assertEquals(0, dbFood())
        assertNull(bag.getBagItem(character, slot))
        assertEquals(30, MiningRepositoryImpl().load(character)?.machineFull?.first())
    }
}
