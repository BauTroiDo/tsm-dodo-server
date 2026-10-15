package tw.tsm.tests

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.persistence.repositories.MiningRepositoryImpl
import tw.tsm.persistence.tables.CharacterMining
import tw.tsm.persistence.tables.CharacterCurrencies
import tw.tsm.persistence.tables.MiningOreReserves
import tw.tsm.persistence.tables.MiningTsAwardReceipts

/** Amounts are synthetic fixture numbers; NOT source-proven DVT TS yields. */
class DvtMiningTsSettlementAtomicSqlTest {
    private val repo = MiningRepositoryImpl()
    private lateinit var db: Database
    private val cap = MiningSystem.MAX_MINE
    private val charId = 77L
    private val before = MiningRecord(
        mineType=1, machineFull=listOf(2,1,0,0),
        updatedAtMillis=1_000_000L, dayLimit=false
    )
    private val after = before.copy(
        machineFull=listOf(1,1,0,0), updatedAtMillis=1_360_000L
    )

    @BeforeEach fun setup() {
        db = Database.connect(
            "jdbc:h2:mem:dvt_ts_state_" + UUID.randomUUID() +
            ";MODE=MySQL;DB_CLOSE_DELAY=-1", driver="org.h2.Driver")
        transaction(db) {
            SchemaUtils.create(
                CharacterMining, CharacterCurrencies, MiningOreReserves, MiningTsAwardReceipts
            )
            CharacterMining.insert {
                it[characterId]=charId
                it[mineType]=1
                it[full1]=before.machineFull[0]
                it[full2]=before.machineFull[1]
                it[full3]=before.machineFull[2]
                it[full4]=before.machineFull[3]
                it[updatedAtMillis]=before.updatedAtMillis
                it[dayLimit]=false
            }
        }
    }

    private fun ts(): Int = transaction(db) {
        CharacterCurrencies.selectAll().where {
            (CharacterCurrencies.characterId eq charId) and
            (CharacterCurrencies.currencyKind eq 8)
        }.singleOrNull()?.get(CharacterCurrencies.value) ?: 0
    }
    private fun receipts(): Long = transaction(db) {
        MiningTsAwardReceipts.selectAll().count()
    }
    private suspend fun commit(
        id:String, expected:MiningRecord=before, updated:MiningRecord=after,
        expectedWallet:Int=0, debit:Long=100L, credit:Int=7
    ) = repo.commitVerifiedTsAwardWithMineStateAtomic(
        id,charId,1,debit,credit,cap,expectedWallet,expected,updated
    )

    @Test fun oneVerifiedEventCommitsMineStateWalletOreAndReceiptTogether() = runBlocking {
        val award=commit("source:1:77:e1")
        assertTrue(award!!.newlyApplied)
        assertEquals(7, award.tsBalance)
        assertEquals(cap-100, award.remainingOre)
        assertEquals(after,repo.load(charId))
        assertEquals(7,ts())
        assertEquals(cap-100,repo.loadRemainingOre(1))
        assertEquals(1L,receipts())
    }

    @Test fun replayNeverSpendsHungerAgainAndNeverRecreditsTs() = runBlocking {
        assertTrue(commit("source:1:77:e2")!!.newlyApplied)
        val replay=commit("source:1:77:e2",expected=after,
            updated=after.copy(updatedAtMillis=after.updatedAtMillis+1L))
        // Optimistic state no longer matches initial state: caller must not
        // regenerate a reward for an event that was already settled.
        assertNotNull(replay)
        assertFalse(replay!!.newlyApplied)
        assertNull(commit("source:1:77:e2", expected=after,
            updated=after.copy(updatedAtMillis=after.updatedAtMillis+1L),
            credit=8))
        assertEquals(after,repo.load(charId))
        assertEquals(7,ts())
        assertEquals(cap-100,repo.loadRemainingOre(1))
        assertEquals(1L,receipts())
    }

    @Test fun oldMiningSnapshotOrNonSourceFoodIncreaseCannotProduceTs() = runBlocking {
        assertNull(commit("source:1:77:stale",
            expected=before.copy(machineFull=listOf(9,9,0,0))))
        assertNull(commit("source:1:77:food",
            updated=before.copy(machineFull=listOf(3,1,0,0))))
        assertEquals(before,repo.load(charId))
        assertEquals(0,ts())
        assertEquals(0L,receipts())
    }

    @Test fun walletMismatchRejectsAwardAndKeepsMinerFullness() = runBlocking {
        assertNull(commit("source:1:77:wallet",expectedWallet=55))
        assertEquals(before,repo.load(charId))
        assertEquals(0,ts())
        assertNull(repo.loadRemainingOre(1))
        assertEquals(0L,receipts())
    }

    @Test fun sharedOreDepletionRejectsRewardWithoutWorkerOrWalletChanges() = runBlocking {
        assertTrue(repo.debitVerifiedOre(1,cap-4,cap))
        assertNull(commit("source:1:77:depleted", debit=5))
        assertEquals(before,repo.load(charId))
        assertEquals(0,ts())
        assertEquals(4L,repo.loadRemainingOre(1))
        assertEquals(0L,receipts())
    }

    @Test fun failedSqlWalletConstraintRollsBackMineOreAndReceipt() = runBlocking {
        transaction(db) {
            exec("ALTER TABLE character_currencies ADD CONSTRAINT ts_max5 CHECK (\"value\" <= 5)")
        }
        var failed=false
        try { commit("source:1:77:bad") } catch(e:Exception){ failed=true }
        assertTrue(failed,"Injected database error must rollback the whole settlement")
        assertEquals(before,repo.load(charId))
        assertEquals(0,ts())
        assertNull(repo.loadRemainingOre(1))
        assertEquals(0L,receipts())
    }

    @Test fun stopFlagPreventsGrantingAnyNewVerifiedAward() = runBlocking {
        val stopped=before.copy(dayLimit=true)
        transaction(db) {
            CharacterMining.update({ CharacterMining.characterId eq charId }) {
                it[CharacterMining.dayLimit]=true
            }
        }
        assertNull(commit("source:1:77:stopped",expected=stopped,updated=stopped))
        assertEquals(0,ts())
        assertEquals(0L,receipts())
    }
}
