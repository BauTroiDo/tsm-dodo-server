package tw.tsm.tests
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.persistence.repositories.MiningRepositoryImpl
import tw.tsm.persistence.tables.CharacterCurrencies
import tw.tsm.persistence.tables.MiningOreReserves
import tw.tsm.persistence.tables.MiningTsAwardReceipts
/** Infrastructure only; test quantities do NOT define DVT gameplay yields. */
class DvtMiningTsAwardAtomicSqlTest {
    private val repo = MiningRepositoryImpl()
    private lateinit var db: Database
    private val cap = MiningSystem.MAX_MINE
    @BeforeEach fun setup() {
        db = Database.connect(
            "jdbc:h2:mem:dvt_ts_mine_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(db) {
            SchemaUtils.create(CharacterCurrencies, MiningOreReserves, MiningTsAwardReceipts)
        }
    }
    private fun tsBalance(charId: Long) = transaction(db) {
        CharacterCurrencies.selectAll().where {
            (CharacterCurrencies.characterId eq charId) and (CharacterCurrencies.currencyKind eq 8)
        }.singleOrNull()?.get(CharacterCurrencies.value) ?: 0
    }
    @Test fun verifiedAwardCreditsTsAndDebitsOreWithOneReceipt() = runBlocking {
        val result = repo.commitVerifiedTsAwardAtomic("mine:1:31:tick100",31,1,100,7,cap,0)
        assertNotNull(result)
        assertTrue(result!!.newlyApplied)
        assertEquals(7,result.tsBalance)
        assertEquals(cap - 100,result.remainingOre)
        assertEquals(7,tsBalance(31))
        assertEquals(cap - 100,repo.loadRemainingOre(1))
        assertEquals(1L, transaction(db) { MiningTsAwardReceipts.selectAll().count() })
    }
    @Test fun replayDoesNotCreditTwiceAndDifferentPayloadFailsClosed() = runBlocking {
        val initial=repo.commitVerifiedTsAwardAtomic("mine:1:31:tick101",31,1,10,3,cap,0)
        val replay=repo.commitVerifiedTsAwardAtomic("mine:1:31:tick101",31,1,10,3,cap,0)
        val tamper=repo.commitVerifiedTsAwardAtomic("mine:1:31:tick101",31,1,10,4,cap,0)
        assertTrue(initial!!.newlyApplied)
        assertFalse(replay!!.newlyApplied)
        assertNull(tamper)
        assertEquals(3,tsBalance(31))
        assertEquals(cap - 10,repo.loadRemainingOre(1))
        assertEquals(1L, transaction(db) { MiningTsAwardReceipts.selectAll().count() })
    }
    @Test fun depletionAndInvalidCreditCannotMintTs() = runBlocking {
        assertTrue(repo.debitVerifiedOre(1,cap - 5,cap))
        assertNull(repo.commitVerifiedTsAwardAtomic("mine:1:31:no",31,1,6,1,cap,0))
        assertNull(repo.commitVerifiedTsAwardAtomic("mine:1:31:zero",31,1,1,0,cap,0))
        assertNull(repo.commitVerifiedTsAwardAtomic("bad id",31,1,1,1,cap,0))
        assertEquals(0,tsBalance(31))
        assertEquals(5L,repo.loadRemainingOre(1))
        assertEquals(0L, transaction(db) { MiningTsAwardReceipts.selectAll().count() })
    }
    @Test fun staleWalletSnapshotCannotOverwriteOtherTsChanges() = runBlocking {
        val first=repo.commitVerifiedTsAwardAtomic("mine:1:31:tick500",31,1,10,3,cap,0)
        assertTrue(first!!.newlyApplied)
        val stale=repo.commitVerifiedTsAwardAtomic("mine:1:31:tick501",31,1,10,4,cap,0)
        assertNull(stale)
        assertEquals(3,tsBalance(31))
        assertEquals(cap - 10,repo.loadRemainingOre(1))
        assertEquals(1L, transaction(db) { MiningTsAwardReceipts.selectAll().count() })
    }

    @Test fun sqlFailureRollsBackBothAwardAndReserveAndReceipt() = runBlocking {
        transaction(db) {
            exec("ALTER TABLE character_currencies ADD CONSTRAINT ts_cap CHECK (\"value\" <= 5)")
        }
        var failed=false
        try { repo.commitVerifiedTsAwardAtomic("mine:1:31:bad",31,1,100,7,cap,0) }
        catch (e: Exception) { failed=true }
        assertTrue(failed,"Injected SQL constraint must reject entire transaction")
        assertEquals(0,tsBalance(31))
        assertNull(repo.loadRemainingOre(1))
        assertEquals(0L, transaction(db) { MiningTsAwardReceipts.selectAll().count() })
    }
}
