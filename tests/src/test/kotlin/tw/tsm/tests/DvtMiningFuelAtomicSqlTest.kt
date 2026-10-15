package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
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
import tw.tsm.persistence.tables.MiningOreReserves
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
            SchemaUtils.create(Inventories, CharacterMining, MiningOreReserves)
            Inventories.insert {
                it[characterId] = character
                it[storageType] = 1
                it[Inventories.slot] = this@DvtMiningFuelAtomicSqlTest.slot
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

    /**
     * Original PC DVT UIMine.lua: UI itemIndex=1..4 maps to C170-010
     * parameter 4-itemIndex (wireIndex 3..0). No cross-worker fuel mixing.
     * Every worker must be checked in SQL, not just worker index 1.
     */
    @Test fun originalClientReversedWireIndexFeedsOnlySelectedWorker() = runBlocking {
        for (worker in 0..3) {
            val desiredFood = MiningSystem.FUEL_ITEM_IDS[worker]
            transaction(db) {
                exec("UPDATE inventories SET item_id=$desiredFood, quantity=3 WHERE character_id=$character AND slot=$slot")
                exec("UPDATE character_mining SET full_1=0, full_2=0, full_3=0, full_4=0 WHERE character_id=$character")
            }
            val definitions = MiningSystem.FUEL_ITEM_IDS.associateWith { item ->
                mockk<ItemDef> { every { threshold } returns 10 }
            }
            val inventory = InventorySystem(
                InventoryRepositoryImpl(), definitions, mockk<RoleCountSystem>(relaxed = true)
            )
            inventory.loadCharacterData(character)
            val system = MiningSystem(
                MiningRepositoryImpl(), inventory, definitions,
                nowMillis = { 1_000_000L }
            )
            val wireIndex = 3 - worker
            val result = system.refill(character, slot, desiredFood, 1, wireIndex)
            assertTrue(result.success, "worker=$worker wire=$wireIndex")
            assertEquals(wireIndex, result.wireIndex)
            assertEquals(10, result.addedFull)
            assertEquals(2, dbFood(), "worker=$worker")
            assertEquals(List(4) { if (it == worker) 10 else 0 },
                MiningRepositoryImpl().load(character)?.machineFull, "worker=$worker")
        }
    }

    @Test fun wrongFoodForWorkerIsRejectedWithoutSpendingBag() = runBlocking {
        val (bag, mining) = service()
        val wrongWorkerIndex = 0 // original item 55431 requires wire index 3
        val result = mining.refill(character, slot, foodId, 1, wrongWorkerIndex)
        assertFalse(result.success)
        assertEquals(3, dbFood())
        assertEquals(3, bag.getBagItem(character, slot)?.quantity)
        assertEquals(listOf(0, 0, 0, 0),
            MiningRepositoryImpl().load(character)?.machineFull)
    }

    @Test fun rejectedMultiFoodOverfillDoesNotPartiallyDebitInventory() = runBlocking {
        transaction(db) {
            exec("UPDATE character_mining SET full_1=65520 WHERE character_id=$character")
        }
        val (bag, mining) = service()
        // Original UI's while loop reduces the proposed quantity until it fits.
        // A forged or stale quantity must not spend 2 food for 15 capacity.
        val result = mining.refill(character, slot, foodId, 2, 3)
        assertFalse(result.success)
        assertEquals(3, dbFood())
        assertEquals(3, bag.getBagItem(character, slot)?.quantity)
        assertEquals(65520, MiningRepositoryImpl().load(character)?.machineFull?.first())
    }

    /**
     * Lua DVT 1.15 UIMine.UpdateTimer sets count=0 when dayLimit=true and
     * forces isMining=false; this is a client-visible STOP signal.
     * Test the persisted SQL round-trip (not merely a mocked MiningRecord).
     * The ORIGINAL server rule that sets/clears dayLimit is still unknown.
     */
    @Test fun persistedDayLimitFreezesWorkerFullAndStopsMining() = runBlocking {
        val start = 1_000_000L
        transaction(db) {
            exec("UPDATE character_mining SET full_1=3, full_2=2, full_3=1, full_4=4, " +
                "day_limit=TRUE, updated_at_ms=$start WHERE character_id=$character")
        }
        val late = start + 3 * 24 * 60 * 60 * 1000L
        val mining = MiningSystem(
            MiningRepositoryImpl(), mockk<InventorySystem>(relaxed = true),
            emptyMap(), nowMillis = { late }
        )
        val view = mining.getView(character)
        assertTrue(view.record.dayLimit)
        assertFalse(view.isMining)
        assertEquals(listOf(3, 2, 1, 4), view.record.machineFull)
        val persisted = MiningRepositoryImpl().load(character)!!
        assertTrue(persisted.dayLimit)
        assertEquals(listOf(3, 2, 1, 4), persisted.machineFull)
        assertEquals(MiningSystem.MAX_MINE, view.remainingOre)
    }

    /** Verify handover in the DATABASE, not just an in-memory Kotlin list. */
    @Test fun originalSequentialWorkerHandoverIsDurableAcrossSqlReload() = runBlocking {
        val start = 1_000_000L
        transaction(db) {
            exec("UPDATE character_mining SET full_1=1, full_2=2, full_3=1, full_4=3, " +
                "day_limit=FALSE, updated_at_ms=$start WHERE character_id=$character")
        }
        val clock = start + 3 * MiningSystem.TICK_MS
        val mining = MiningSystem(
            MiningRepositoryImpl(), mockk<InventorySystem>(relaxed = true),
            emptyMap(), nowMillis = { clock }
        )
        val view = mining.getView(character)
        assertEquals(listOf(0, 0, 1, 3), view.record.machineFull)
        assertEquals(start + 3 * MiningSystem.TICK_MS, view.record.updatedAtMillis)
        assertTrue(view.isMining)
        val reloaded = MiningRepositoryImpl().load(character)!!
        assertEquals(view.record, reloaded)
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
