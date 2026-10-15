package tw.tsm.tests

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
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
import tw.tsm.gamelogic.systems.OnlineWelfareDaySystem
import tw.tsm.persistence.repositories.WelfareStateRepositoryImpl
import tw.tsm.persistence.tables.CharacterRoleCounts
import tw.tsm.persistence.tables.CharacterBitFlags
import tw.tsm.persistence.tables.CharacterWelfareState

class WelfareOnlineDailySqlTest {
    private val repo = WelfareStateRepositoryImpl()
    @BeforeEach fun setup() {
        Database.connect("jdbc:h2:mem:welfare_online_"+UUID.randomUUID()+
            ";MODE=MySQL;DB_CLOSE_DELAY=-1",driver="org.h2.Driver")
        transaction { SchemaUtils.create(CharacterWelfareState, CharacterRoleCounts, CharacterBitFlags) }
    }
    @Test fun newDayResetsOnlyOnlineSecondsAndFlags() = runBlocking {
        repo.setLastLoginClaimDay(77L,20261015)
        transaction {
            CharacterRoleCounts.insert {
                it[charId]=77L; it[countIndex]=10; it[value]=12345; it[maxValue]=0
            }
            CharacterRoleCounts.insert {
                it[charId]=77L; it[countIndex]=108; it[value]=12; it[maxValue]=0
            }
            for (flag in listOf(1,2,3,4,5,6,7,1500)) {
                CharacterBitFlags.insert { it[charId]=77L;it[flagId]=flag }
            }
        }
        assertTrue(repo.startOnlineDayIfNeeded(77L,20261016))
        transaction {
            val counts=CharacterRoleCounts.selectAll().where { CharacterRoleCounts.charId eq 77L }
                .associate { it[CharacterRoleCounts.countIndex] to it[CharacterRoleCounts.value] }
            assertEquals(0,counts[10])
            assertEquals(12,counts[108])
            val kept=CharacterBitFlags.selectAll().where { CharacterBitFlags.charId eq 77L }
                .map { it[CharacterBitFlags.flagId] }.toSet()
            assertEquals(setOf(1,1500),kept)
        }
        assertEquals(20261015,repo.getLastLoginClaimDay(77L))
    }
    @Test fun sameDayReconnectDoesNotResetAgain() = runBlocking {
        assertTrue(repo.startOnlineDayIfNeeded(77L,20261016))
        transaction {
            CharacterRoleCounts.insert {
                it[charId]=77L;it[countIndex]=10;it[value]=600;it[maxValue]=0
            }
            CharacterBitFlags.insert { it[charId]=77L;it[flagId]=2 }
        }
        assertFalse(repo.startOnlineDayIfNeeded(77L,20261016))
        transaction {
            assertEquals(600,CharacterRoleCounts.selectAll().where {
                (CharacterRoleCounts.charId eq 77L) and
                    (CharacterRoleCounts.countIndex eq 10)
            }.single()[CharacterRoleCounts.value])
            assertTrue(CharacterBitFlags.selectAll().where {
                CharacterBitFlags.charId eq 77L
            }.any { it[CharacterBitFlags.flagId]==2 })
        }
        assertTrue(repo.startOnlineDayIfNeeded(77L,20261017))
        transaction {
            assertEquals(0,CharacterRoleCounts.selectAll().where {
                (CharacterRoleCounts.charId eq 77L) and
                    (CharacterRoleCounts.countIndex eq 10)
            }.single()[CharacterRoleCounts.value])
            assertTrue(CharacterBitFlags.selectAll().where {
                CharacterBitFlags.charId eq 77L
            }.empty())
        }
    }
    @Test fun midnightCarryCountsOnlySecondsAfterMidnight() {
        val zone=ZoneId.of("Asia/Taipei")
        val midnight=LocalDate.of(2026,10,17).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(120,OnlineWelfareDaySystem.secondsSinceStartToday(midnight-900000,midnight+120000))
        assertEquals(90,OnlineWelfareDaySystem.secondsSinceStartToday(midnight+30000,midnight+120000))
        assertEquals(0,OnlineWelfareDaySystem.secondsSinceStartToday(midnight+120000,midnight+30000))
        assertEquals(20261017,OnlineWelfareDaySystem.dayKey(midnight))
    }
}
