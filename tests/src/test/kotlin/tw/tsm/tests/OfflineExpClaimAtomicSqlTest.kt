package tw.tsm.tests

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tw.tsm.persistence.repositories.OfflineClaimCurrency
import tw.tsm.persistence.repositories.OfflineClaimRequest
import tw.tsm.persistence.repositories.OfflineExpClaimRepository
import tw.tsm.persistence.tables.Characters
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.CharacterRoleCounts

class OfflineExpClaimAtomicSqlTest {
    private val repo=OfflineExpClaimRepository()
    @BeforeEach fun setup() {
        Database.connect("jdbc:h2:mem:offlineclaim_"+UUID.randomUUID()+
            ";MODE=MySQL;DB_CLOSE_DELAY=-1", driver="org.h2.Driver")
        transaction {
            SchemaUtils.create(Characters,CharacterMoney,CharacterRoleCounts)
            Characters.insert {
                it[Characters.id]=77L; it[Characters.accountId]=11L; it[Characters.name]="Test77"; it[Characters.createdAt]=1L
                it[Characters.level]=30; it[Characters.turn]=0; it[Characters.exp]=1000L
                it[Characters.attrPoint]=4; it[Characters.skillPoint]=8;it[Characters.pointReal]=30
            }
            CharacterMoney.insert {
                it[CharacterMoney.characterId]=77L;it[CharacterMoney.gold]=10000;it[CharacterMoney.auctionGold]=0
            }
            for ((index,v) in listOf(169 to 24,171 to 480,172 to 480)) {
                CharacterRoleCounts.insert {
                    it[CharacterRoleCounts.charId]=77L;it[CharacterRoleCounts.countIndex]=index;it[CharacterRoleCounts.value]=v;it[CharacterRoleCounts.maxValue]=0
                }
            }
        }
    }

    private fun request(
        currency: OfflineClaimCurrency = OfflineClaimCurrency.FREE,
        fee: Int = 0,
        balance: Int = 0,
        lvl: Int = 31,
    ) = OfflineClaimRequest(77L,480,24,1000L,120L,30,lvl,0,4,8,fee,balance,currency)

    @Test fun freeClaimCommitsExpAndCountersOnlyOnce() = runBlocking {
        assertNotNull(repo.tryCommit(request()))
        assertNull(repo.tryCommit(request()))
        transaction {
            assertEquals(1120L,Characters.selectAll().where{Characters.id eq 77L}
                .single()[Characters.exp])
            val counts=CharacterRoleCounts.selectAll().where{CharacterRoleCounts.charId eq 77L}
                .associate { it[CharacterRoleCounts.countIndex] to it[CharacterRoleCounts.value] }
            assertEquals(0,counts[169]);assertEquals(0,counts[171]);assertEquals(480,counts[172])
        }
    }

    @Test fun paidGoldClaimDebitsExactlyOnce() = runBlocking {
        val request=request(OfflineClaimCurrency.GAME_GOLD,5000,10000)
        val first=repo.tryCommit(request)
        assertEquals(5000,first?.currencyAfter)
        assertNull(repo.tryCommit(request))
        transaction {
            assertEquals(5000,CharacterMoney.selectAll().where{
                CharacterMoney.characterId eq 77L
            }.single()[CharacterMoney.gold])
        }
    }

    @Test fun premiumPointClaimDebitsWithSameExpTransaction() = runBlocking {
        val request=request(OfflineClaimCurrency.PREMIUM_POINTS,5,30)
        assertEquals(25,repo.tryCommit(request)?.currencyAfter)
        assertNull(repo.tryCommit(request))
        transaction {
            assertEquals(25,Characters.selectAll().where{
                Characters.id eq 77L
            }.single()[Characters.pointReal])
        }
    }

    @Test fun failedPaymentRollsBackExpAndAllCounters() = runBlocking {
        assertNull(repo.tryCommit(request(OfflineClaimCurrency.PREMIUM_POINTS,31,30)))
        assertNull(repo.tryCommit(request(OfflineClaimCurrency.GAME_GOLD,10001,10000)))
        transaction {
            val c=Characters.selectAll().where{Characters.id eq 77L}.single()
            assertEquals(1000L,c[Characters.exp])
            val counts=CharacterRoleCounts.selectAll().where{CharacterRoleCounts.charId eq 77L}
                .associate { it[CharacterRoleCounts.countIndex] to it[CharacterRoleCounts.value] }
            assertEquals(24,counts[169]);assertEquals(480,counts[171])
        }
    }

    @Test fun mismatchedWalletAndCharacterExpAreRejected() = runBlocking {
        assertNull(repo.tryCommit(request(OfflineClaimCurrency.GAME_GOLD,100,9000)))
        assertNull(repo.tryCommit(request().copy(expBefore=999L)))
        assertNull(repo.tryCommit(request().copy(percentExpected=25)))
    }

    @Test fun paidClaimCannotBeMadeFreeByZeroPrice() = runBlocking {
        assertNull(repo.tryCommit(request(OfflineClaimCurrency.GAME_GOLD,0,10000)))
        assertNull(repo.tryCommit(request(OfflineClaimCurrency.PREMIUM_POINTS,0,30)))
        assertNull(repo.tryCommit(request(OfflineClaimCurrency.FREE,5,0)))
    }
}
