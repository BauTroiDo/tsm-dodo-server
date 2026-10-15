package tw.tsm.tests

import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.persistence.repositories.MiningRepositoryImpl
import tw.tsm.persistence.tables.CharacterMining
import tw.tsm.persistence.tables.MiningOreReserves

/**
 * Shared ore ledger, not a fabricated mining reward policy.
 * Only VERIFIED ore-award events may call debitVerifiedOre().
 */
class DvtMineOreReserveSqlTest {
    private val repo = MiningRepositoryImpl()
    private lateinit var db: Database

    @BeforeEach fun setup() {
        db = Database.connect(
            "jdbc:h2:mem:dvt_shared_mine_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(db) { SchemaUtils.create(CharacterMining, MiningOreReserves) }
    }

    @Test fun untouchedMineUsesSourceMaximumWithoutInventingDepletion() = runBlocking {
        assertNull(repo.loadRemainingOre(1))
        val view = MiningSystem(
            repo, mockk<InventorySystem>(relaxed = true), emptyMap(),
            nowMillis = { 1_000_000L },
        ).getView(31)
        assertEquals(600_000_000L, view.remainingOre)
        assertNull(repo.loadRemainingOre(1))
    }

    @Test fun verifiedDebitPersistsAndChangesTheRealDisplayedReserve() = runBlocking {
        val maxOre = MiningSystem.MAX_MINE
        assertTrue(repo.debitVerifiedOre(1, 30_000_000L, maxOre))
        assertEquals(570_000_000L, MiningRepositoryImpl().loadRemainingOre(1))
        val view = MiningSystem(
            MiningRepositoryImpl(), mockk<InventorySystem>(relaxed = true), emptyMap(),
            nowMillis = { 1_000_000L },
        ).getView(31)
        assertEquals(570_000_000L, view.remainingOre)
        assertEquals(95L, view.remainingOre * 100L / maxOre)
        assertEquals(570_000_000L, MiningRepositoryImpl().loadRemainingOre(1))
    }

    @Test fun zeroExcessAndInvalidDebitsDoNotChangeTheSharedReserve() = runBlocking {
        val maxOre = MiningSystem.MAX_MINE
        assertFalse(repo.debitVerifiedOre(1, 0, maxOre))
        assertFalse(repo.debitVerifiedOre(1, maxOre + 1, maxOre))
        assertNull(repo.loadRemainingOre(1))
        assertTrue(repo.debitVerifiedOre(1, maxOre - 9, maxOre))
        assertFalse(repo.debitVerifiedOre(1, 10, maxOre))
        assertEquals(9L, repo.loadRemainingOre(1))
        assertTrue(repo.debitVerifiedOre(1, 9, maxOre))
        assertEquals(0L, repo.loadRemainingOre(1))
        assertFalse(repo.debitVerifiedOre(1, 1, maxOre))
        assertEquals(0L, repo.loadRemainingOre(1))
    }

    @Test fun concurrentRequestsCannotOverdrawTheGlobalOrePool() = runBlocking {
        val maxOre = MiningSystem.MAX_MINE
        assertTrue(repo.debitVerifiedOre(1, maxOre - 60, maxOre))
        val attempts = (1..12).map {
            async(Dispatchers.Default) { repo.debitVerifiedOre(1, 10, maxOre) }
        }.awaitAll()
        assertEquals(6, attempts.count { it })
        assertEquals(0L, repo.loadRemainingOre(1))
        assertNull(repo.loadRemainingOre(2))
    }
}
