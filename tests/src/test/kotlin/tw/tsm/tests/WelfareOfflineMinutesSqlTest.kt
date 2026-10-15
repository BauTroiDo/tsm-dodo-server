package tw.tsm.tests

import java.time.LocalDate
import java.time.ZoneId
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
import tw.tsm.persistence.repositories.OfflineWelfareMinutePolicy as Policy
import tw.tsm.persistence.repositories.WelfareStateRepositoryImpl
import tw.tsm.persistence.tables.CharacterRoleCounts
import tw.tsm.persistence.tables.CharacterBitFlags
import tw.tsm.persistence.tables.CharacterWelfareState

class WelfareOfflineMinutesSqlTest {
    private val repo = WelfareStateRepositoryImpl()
    private val zone = ZoneId.of("Asia/Taipei")
    private fun millis(day: String, hour: Int, minute: Int): Long = LocalDate.parse(day)
        .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @BeforeEach fun setup() {
        Database.connect("jdbc:h2:mem:welfare_offline_" + UUID.randomUUID() +
            ";MODE=MySQL;DB_CLOSE_DELAY=-1",driver="org.h2.Driver")
        transaction { SchemaUtils.create(CharacterWelfareState, CharacterRoleCounts, CharacterBitFlags) }
    }
    private fun count(characterId: Long, index: Int): Int = transaction {
        CharacterRoleCounts.selectAll().where {
            (CharacterRoleCounts.charId eq characterId) and
                (CharacterRoleCounts.countIndex eq index)
        }.singleOrNull()?.get(CharacterRoleCounts.value) ?: 0
    }

    @Test fun sameDaySevenHoursFiftyNineAndEightHourCap() = runBlocking {
        val start=millis("2026-10-16",8,0)
        repo.markCharacterOffline(77L,start)
        repo.resumeCharacterOfflineMinutes(77L,start+479*60_000L)
        assertEquals(479,count(77L,171))
        assertEquals(479,count(77L,172))
        repo.markCharacterOffline(77L,start+479*60_000L)
        repo.resumeCharacterOfflineMinutes(77L,start+520*60_000L)
        assertEquals(480,count(77L,171))
        assertEquals(480,count(77L,172))
    }
    @Test fun reconnectCannotClaimTheSameOfflinePeriodTwice() = runBlocking {
        val start=millis("2026-10-16",12,0)
        repo.markCharacterOffline(77L,start)
        repo.markCharacterOffline(77L,start+30*60_000L)
        repo.resumeCharacterOfflineMinutes(77L,start+120*60_000L)
        assertEquals(120,count(77L,171))
        repo.resumeCharacterOfflineMinutes(77L,start+180*60_000L)
        assertEquals(120,count(77L,171))
    }
    @Test fun acrossMidnightRespectDailyCapAndCumulativeLimit() = runBlocking {
        val start=millis("2026-10-16",19,0)
        repo.markCharacterOffline(77L,start)
        repo.resumeCharacterOfflineMinutes(77L,millis("2026-10-18",9,0))
        assertEquals(1260,count(77L,171)) // 300 + 480 + 480
        assertEquals(480,count(77L,172))
        repo.markCharacterOffline(77L,millis("2026-10-18",9,0))
        repo.resumeCharacterOfflineMinutes(77L,millis("2026-10-19",10,0))
        assertEquals(1440,count(77L,171))
        assertEquals(480,count(77L,172))
    }
    @Test fun independentCharactersAndClockRollbackFailClosed() = runBlocking {
        val start=millis("2026-10-16",8,0)
        repo.markCharacterOffline(77L,start)
        repo.markCharacterOffline(88L,start)
        repo.resumeCharacterOfflineMinutes(77L,start-60_000L)
        assertEquals(0,count(77L,171))
        repo.resumeCharacterOfflineMinutes(88L,start+62*60_000L)
        assertEquals(62,count(88L,171))
        assertEquals(0,count(77L,171))
    }
    @Test fun approvedHistoricExpPercentWithoutCurrencySideEffects() = runBlocking {
        val start=millis("2026-10-16",8,0)
        repo.markCharacterOffline(77L,start)
        repo.resumeCharacterOfflineMinutes(77L,start+480*60_000L)
        assertEquals(24,count(77L,169))
        assertEquals(0,count(77L,170))
        assertEquals(480,count(77L,171))
    }
    @Test fun policyZeroAndMax() {
        val now=millis("2026-10-16",12,0)
        assertEquals(0,Policy.calculate(0L,now,0,0,0).newMinutes)
        assertEquals(0,Policy.calculate(now+60000,now,0,0,0).newMinutes)
        assertEquals(1440,Policy.calculate(now-10L*24*3600*1000,now,0,0,0).unclaimedMinutes)
    }
}
