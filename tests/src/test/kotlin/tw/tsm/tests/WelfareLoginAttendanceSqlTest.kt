package tw.tsm.tests

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.WelfareLoginDayPolicy
import tw.tsm.persistence.repositories.WelfareStateRepositoryImpl
import tw.tsm.persistence.tables.CharacterWelfareLoginDays
import tw.tsm.persistence.tables.CharacterWelfareState

class WelfareLoginAttendanceSqlTest {
    private val repo = WelfareStateRepositoryImpl()
    @BeforeEach fun setup() {
        Database.connect("jdbc:h2:mem:welfare_days_"+UUID.randomUUID()+
            ";MODE=MySQL;DB_CLOSE_DELAY=-1",driver="org.h2.Driver")
        transaction { SchemaUtils.create(CharacterWelfareState,CharacterWelfareLoginDays) }
    }
    @Test fun sameDayReconnectDoesNotCountTwice() = runBlocking {
        assertEquals(1,repo.recordLoginDay(77L,20261015))
        assertEquals(1,repo.recordLoginDay(77L,20261015))
        assertEquals(1,repo.getLoginDayCount(77L))
    }
    @Test fun distinctCalendarDaysUnlockOpenServerMilestones() = runBlocking {
        assertEquals(1,repo.recordLoginDay(77L,20261015))
        assertEquals(2,repo.recordLoginDay(77L,20261016))
        assertEquals(3,repo.recordLoginDay(77L,20261017))
        assertEquals(3,repo.getLoginDayCount(77L))
    }
    @Test fun separateCharactersAndClaimHistoryRemainIntact() = runBlocking {
        assertEquals(1,repo.recordLoginDay(77L,20261015))
        assertEquals(1,repo.recordLoginDay(88L,20261015))
        repo.setLastLoginClaimDay(77L,20261015)
        assertEquals(1,repo.recordLoginDay(77L,20261015))
        assertEquals(1,repo.getLoginDayCount(88L))
        assertEquals(20261015,repo.getLastLoginClaimDay(77L))
    }
    @Test fun legacyVerifiedLoginClaimsAreSafeLowerBound() {
        assertEquals(5,WelfareLoginDayPolicy.visibleDay(2,5))
        assertEquals(2,WelfareLoginDayPolicy.visibleDay(2,0))
        assertEquals(1,WelfareLoginDayPolicy.visibleDay(0,0))
        assertEquals(65535,WelfareLoginDayPolicy.visibleDay(999999,0))
    }
}
