package tw.tsm.tests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.persistence.repositories.EveEffectClaimRepositoryImpl
import tw.tsm.persistence.tables.CharacterCompletedEvents
import tw.tsm.persistence.tables.CharacterEveEffectClaims
import tw.tsm.persistence.tables.CharacterEveEffectSettlements

/**
 * Isolated in-memory H2 integration tests; never connect to production MySQL.
 * A pending claim is a fail-closed fence, NOT proof of a paid-out reward.
 */
class EveEffectClaimRepositoryTest {
    private fun withRepository(block: suspend (EveEffectClaimRepositoryImpl) -> Unit) {
        val previous = TransactionManager.defaultDatabase
        val database = Database.connect(
            url = "jdbc:h2:mem:quest_effect_claim_${System.nanoTime()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000",
            driver = "org.h2.Driver",
        )
        TransactionManager.defaultDatabase = database
        try {
            transaction(database) {
                SchemaUtils.create(CharacterEveEffectClaims, CharacterCompletedEvents, CharacterEveEffectSettlements)
            }
            runBlocking { block(EveEffectClaimRepositoryImpl()) }
        } finally {
            TransactionManager.defaultDatabase = previous
        }
    }

    @Test
    fun sameRunCanContinueButNewRunCannotReplayPendingEffects() = withRepository { repo ->
        assertTrue(repo.reserve(501L, 10821, 3, "first-run", 1000L))
        assertTrue(repo.reserve(501L, 10821, 3, "first-run", 3000L))
        assertFalse(repo.reserve(501L, 10821, 3, "second-run", 4000L))
        assertEquals(
            listOf("first-run" to 1000L),
            repo.unresolvedForCharacter(501L).map { it.runToken to it.reservedAtEpochMs }
        )
    }

    @Test
    fun unresolvedClaimSurvivesRepositoryRecreationAndBlocksReplay() = withRepository { repo ->
        assertTrue(repo.reserve(502L, 10821, 3, "aborted-run", 1000L))
        val afterSimulatedRestart = EveEffectClaimRepositoryImpl()
        assertFalse(afterSimulatedRestart.reserve(502L, 10821, 3, "new-run", 9000L))
        assertEquals("aborted-run", afterSimulatedRestart.unresolvedForCharacter(502L).single().runToken)
        assertEquals(null, afterSimulatedRestart.settle(502L, 10821, 3, "new-run"))
        assertEquals(1, afterSimulatedRestart.unresolvedForCharacter(502L).size)
    }

    @Test
    fun settlementAtomicallyIncrementsCountAndReleasesFenceOnlyOnce() = withRepository { repo ->
        assertTrue(repo.reserve(503L, 10821, 3, "run-one", 1000L))
        assertNull(repo.settle(503L, 10821, 3, "wrong-run"))
        assertEquals(1, repo.unresolvedForCharacter(503L).size)

        assertEquals(1, repo.settle(503L, 10821, 3, "run-one"))
        assertTrue(repo.unresolvedForCharacter(503L).isEmpty())
        assertNull(repo.settle(503L, 10821, 3, "run-one"))
        // A lost success ACK must NOT permit a replay of the same run token.
        assertFalse(repo.reserve(503L, 10821, 3, "run-one", 2000L))
        assertFalse(repo.reserve(503L, 10853, 9, "run-one", 2000L))
        assertTrue(repo.reserve(503L, 10821, 3, "run-two", 2000L))
        assertEquals(2, repo.settle(503L, 10821, 3, "run-two"))
        assertTrue(repo.unresolvedForCharacter(503L).isEmpty())
    }

    @Test
    fun independentEveKeysDoNotBlockOneAnother() = withRepository { repo ->
        assertTrue(repo.reserve(504L, 10821, 3, "run-a", 1000L))
        assertTrue(repo.reserve(504L, 10821, 4, "run-b", 1000L))
        assertTrue(repo.reserve(504L, 10853, 3, "run-c", 1000L))
        assertTrue(repo.reserve(505L, 10821, 3, "run-d", 1000L))
        assertEquals(3, repo.unresolvedForCharacter(504L).size)
        assertEquals(1, repo.unresolvedForCharacter(505L).size)
    }

    @Test
    fun parallelDifferentTokensCannotStealTheSameReservation() = withRepository { repo ->
        val results = coroutineScope {
            (1..8).map { index ->
                async(Dispatchers.IO) {
                    index to repo.reserve(506L, 10821, 3, "run-$index", index * 1000L)
                }
            }.awaitAll()
        }
        assertEquals(1, results.count { it.second })
        val winner = results.single { it.second }.first
        assertEquals("run-$winner", repo.unresolvedForCharacter(506L).single().runToken)
    }

    @Test
    fun invalidClaimsAreRejectedBeforeWriting() = withRepository { repo ->
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.reserve(0L, 10821, 3, "run", 1L) }
        }
        assertTrue(repo.unresolvedForCharacter(0L).isEmpty())
    }
}
