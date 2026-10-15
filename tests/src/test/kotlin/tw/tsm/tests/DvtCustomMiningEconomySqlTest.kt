package tw.tsm.tests

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
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
import tw.tsm.domain.interfaces.repositories.CustomMiningRules
import tw.tsm.persistence.repositories.MiningRepositoryImpl
import tw.tsm.persistence.tables.CharacterMining
import tw.tsm.persistence.tables.CharacterCurrencies
import tw.tsm.persistence.tables.MiningOreReserves
import tw.tsm.persistence.tables.MiningTsAwardReceipts

/**
 * Custom/private-server production rules, NOT original TS Online DVT server rates.
 * H2 MODE=MySQL exercises the same Exposed SQL settlement and receipt records.
 */
class DvtCustomMiningEconomySqlTest {
    private val repo = MiningRepositoryImpl()
    private lateinit var db: Database
    private val charId = 81L
    private val fullOre = 600_000_000L
    private val tick = 360_000L
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val start: Long = LocalDateTime.of(2026,10,15,10,0)
        .atZone(zone).toInstant().toEpochMilli()
    private fun rules(cap: Int = 120) =
        CustomMiningRules(enabled=true,workerTsPerTick=listOf(1,2,3,4),
            dailyTsCap=cap,zoneId="Asia/Ho_Chi_Minh")

    @BeforeEach fun setup() {
        db = Database.connect("jdbc:h2:mem:custom_dvt_"+UUID.randomUUID()+
            ";MODE=MySQL;DB_CLOSE_DELAY=-1", driver="org.h2.Driver")
        transaction(db) {
            SchemaUtils.create(CharacterMining,CharacterCurrencies,
                MiningOreReserves,MiningTsAwardReceipts)
        }
    }
    private fun initMining(full:List<Int> = listOf(4,4,4,4), at:Long = start) =
        transaction(db) {
            CharacterMining.insert {
                it[characterId]=charId
                it[mineType]=1
                it[full1]=full[0];it[full2]=full[1]
                it[full3]=full[2];it[full4]=full[3]
                it[updatedAtMillis]=at
                it[dayLimit]=false
            }
        }
    private fun miner() = transaction(db) {
        CharacterMining.selectAll().where { CharacterMining.characterId eq charId }
            .single().let { row ->
                listOf(row[CharacterMining.full1],row[CharacterMining.full2],
                       row[CharacterMining.full3],row[CharacterMining.full4])
            }
    }
    private fun limited() = transaction(db) {
        CharacterMining.selectAll().where { CharacterMining.characterId eq charId }
            .single()[CharacterMining.dayLimit]
    }
    private fun wallet() = transaction(db) {
        CharacterCurrencies.selectAll().where {
            (CharacterCurrencies.characterId eq charId) and
            (CharacterCurrencies.currencyKind eq 8)
        }.singleOrNull()?.get(CharacterCurrencies.value) ?: 0
    }
    private fun receipts() = transaction(db) { MiningTsAwardReceipts.selectAll().count() }

    @Test fun fourWorkersPayDifferentRatesOneTickEachWithoutDuplicates() = runBlocking {
        initMining(listOf(1,1,1,1))
        val committed = repo.settleCustomMining(charId,start+4*tick,0,rules())
        assertTrue(committed!!.newlyApplied)
        assertEquals(10,committed.tsBalance)
        assertEquals(fullOre-10,committed.remainingOre)
        assertEquals(listOf(0,0,0,0),miner())
        assertEquals(10,wallet())
        val count=receipts()
        assertTrue(count>=1L)
        val replay=repo.settleCustomMining(charId,start+4*tick,10,rules())
        assertNotNull(replay)
        assertFalse(replay!!.newlyApplied)
        assertEquals(10,wallet())
        assertEquals(fullOre-10,repo.loadRemainingOre(1))
        assertEquals(count,receipts())
    }

    @Test fun dailyQuotaStopsFuelAndResetsAfterVietnamMidnight() = runBlocking {
        initMining(listOf(20,0,0,0))
        val dayOne=repo.settleCustomMining(charId,start+5*tick,0,rules(cap=2))
        assertEquals(2,dayOne!!.tsBalance)
        assertEquals(listOf(18,0,0,0),miner())
        assertTrue(limited())
        val nextMidnight=LocalDateTime.of(2026,10,16,0,0)
            .atZone(zone).toInstant().toEpochMilli()
        val dayTwo=repo.settleCustomMining(charId,nextMidnight+2*tick,2,rules(cap=2))
        assertEquals(4,dayTwo!!.tsBalance)
        assertEquals(4,wallet())
        assertEquals(listOf(16,0,0,0),miner())
        assertTrue(limited())
        assertEquals(fullOre-4,repo.loadRemainingOre(1))
        assertEquals(2L,receipts())
    }

    @Test fun disabledEconomyCannotDebitFuelOrMintCoin() = runBlocking {
        initMining()
        assertNull(repo.settleCustomMining(charId,start+10*tick,0,
            CustomMiningRules(enabled=false)))
        assertEquals(listOf(4,4,4,4),miner())
        assertEquals(0,wallet())
        assertEquals(0L,receipts())
        assertNull(repo.loadRemainingOre(1))
    }

    @Test fun staleWalletCannotConsumeFuelOrMaterializeOre() = runBlocking {
        initMining()
        assertNull(repo.settleCustomMining(charId,start+tick,2,rules()))
        assertEquals(listOf(4,4,4,4),miner())
        assertEquals(0,wallet())
        assertNull(repo.loadRemainingOre(1))
        assertEquals(0L,receipts())
    }

    @Test fun injectedCurrencySqlFailureRollsBackAllFourChanges() = runBlocking {
        initMining()
        transaction(db) {
            exec("ALTER TABLE character_currencies ADD CONSTRAINT ts_max0 CHECK (\"value\" <= 0)")
        }
        assertThrows(Exception::class.java) {
            runBlocking { repo.settleCustomMining(charId,start+tick,0,rules()) }
        }
        assertEquals(listOf(4,4,4,4),miner())
        assertEquals(0,wallet())
        assertNull(repo.loadRemainingOre(1))
        assertEquals(0L,receipts())
    }

    @Test fun sharedOreExhaustionNeverBurnsUnpaidWorkerFullness() = runBlocking {
        initMining(listOf(3,0,0,0))
        assertTrue(repo.debitVerifiedOre(1,fullOre-1,fullOre))
        val award=repo.settleCustomMining(charId,start+2*tick,0,rules())
        assertEquals(1,award!!.tsBalance)
        assertEquals(0L,award.remainingOre)
        assertEquals(listOf(2,0,0,0),miner())
        assertEquals(1,wallet())
        assertEquals(1L,receipts())
    }

    @Test fun twoCharactersSimultaneouslyDebitTheSameNewSharedOreRow() = runBlocking {
        initMining(listOf(1,0,0,0))
        transaction(db) {
            CharacterMining.insert {
                it[characterId]=82L
                it[mineType]=1
                it[full1]=1;it[full2]=0;it[full3]=0;it[full4]=0
                it[updatedAtMillis]=start
                it[dayLimit]=false
            }
        }
        val result = coroutineScope {
            listOf(charId,82L).map { who ->
                async(Dispatchers.IO) {
                    repo.settleCustomMining(who,start+tick,0,rules())
                }
            }.awaitAll()
        }
        assertEquals(2,result.count { it?.newlyApplied == true })
        assertEquals(2L,receipts())
        assertEquals(fullOre-2,repo.loadRemainingOre(1))
        assertEquals(1,wallet())
        assertEquals(listOf(0,0,0,0),miner())
        val other=transaction(db) {
            CharacterMining.selectAll().where {
                CharacterMining.characterId eq 82L
            }.single()[CharacterMining.full1]
        }
        assertEquals(0,other)
    }

    @Test fun invalidRulesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomMiningRules(enabled=true,workerTsPerTick=listOf(1,2,3),dailyTsCap=120)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomMiningRules(enabled=true,workerTsPerTick=listOf(1,2,3,4),dailyTsCap=0)
        }
    }
}
