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
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import tw.tsm.persistence.repositories.RewardLedgerRepositoryImpl
import tw.tsm.persistence.tables.CharacterRewardLedger
import tw.tsm.persistence.tables.CharacterRewardPendingScopes

/** All tests use isolated in-memory H2. They NEVER connect to production. */
class RewardLedgerRepositoryRegressionTest {
    private fun withRepository(block: suspend (RewardLedgerRepository) -> Unit) {
        val previous = TransactionManager.defaultDatabase
        val database = Database.connect(
            url = "jdbc:h2:mem:reward_ledger_${System.nanoTime()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000;MODE=MySQL",
            driver = "org.h2.Driver",
        )
        TransactionManager.defaultDatabase = database
        try {
            transaction(database) { SchemaUtils.create(CharacterRewardLedger, CharacterRewardPendingScopes) }
            runBlocking { block(RewardLedgerRepositoryImpl()) }
        } finally {
            TransactionManager.defaultDatabase = previous
        }
    }

    @Test fun aPendingRewardNeverGrantsAgainEvenWhenTokenIsIdentical() = withRepository { repo ->
        assertTrue(repo.reserve(101L, "DUNGEON_NORMAL", "run-1:terminal", "attempt-1", 1000L))
        assertFalse(repo.reserve(101L, "DUNGEON_NORMAL", "run-1:terminal", "attempt-1", 1000L))
        assertFalse(repo.reserve(101L, "DUNGEON_NORMAL", "run-1:terminal", "attempt-2", 2000L))
        assertEquals("attempt-1", repo.unresolvedForCharacter(101L).single().attemptToken)
        assertFalse(repo.unresolvedForCharacter(101L).single().isSettled)
    }

    @Test fun pendingClaimSurvivesSimulatedJvmRestart() = withRepository { repo ->
        assertTrue(repo.reserve(102L, "BATTLE_GENERIC", "battle-stable-uuid:player-1", "first", 1000L))
        val afterRestart = RewardLedgerRepositoryImpl()
        assertFalse(afterRestart.reserve(102L, "BATTLE_GENERIC", "battle-stable-uuid:player-1", "second", 9000L))
        assertEquals(1, afterRestart.unresolvedForCharacter(102L).size)
    }

    @Test fun settlingPreservesPermanentReceiptAndCannotRepeatGrant() = withRepository { repo ->
        assertTrue(repo.reserve(103L, "DUNGEON_WEEKLY", "run-7:terminal", "claim-1", 1000L))
        assertFalse(repo.settle(103L, "DUNGEON_WEEKLY", "run-7:terminal", "wrong", 2000L))
        assertTrue(repo.settle(103L, "DUNGEON_WEEKLY", "run-7:terminal", "claim-1", 2000L))
        assertFalse(repo.settle(103L, "DUNGEON_WEEKLY", "run-7:terminal", "claim-1", 3000L))
        assertFalse(repo.reserve(103L, "DUNGEON_WEEKLY", "run-7:terminal", "claim-2", 4000L))
        assertTrue(repo.unresolvedForCharacter(103L).isEmpty())
        assertEquals(2000L, repo.find(103L, "DUNGEON_WEEKLY", "run-7:terminal")?.settledAtEpochMs)
    }

    @Test fun repeatableDungeonGetsDistinctRunKeyAndPlayersRemainIndependent() = withRepository { repo ->
        assertTrue(repo.reserve(104L, "DUNGEON_NORMAL", "run-1:terminal", "a", 1000L))
        // New source key must NOT evade an unresolved earlier payout.
        assertFalse(repo.reserve(104L, "DUNGEON_NORMAL", "run-2:terminal", "b", 2000L))
        assertTrue(repo.settle(104L, "DUNGEON_NORMAL", "run-1:terminal", "a", 3000L))
        // Once the earlier run is fully settled, a new dungeon run is valid.
        assertTrue(repo.reserve(104L, "DUNGEON_NORMAL", "run-2:terminal", "b", 4000L))
        assertTrue(repo.reserve(105L, "DUNGEON_NORMAL", "run-1:terminal", "c", 2000L))
        assertTrue(repo.reserve(104L, "BATTLE_GENERIC", "run-1:terminal", "d", 2000L))
        assertEquals(2, repo.unresolvedForCharacter(104L).size)
        assertEquals(1, repo.unresolvedForCharacter(105L).size)
    }

    @Test fun concurrentParticipantsCompeteForOneSourceKey() = withRepository { repo ->
        val results = coroutineScope {
            (1..8).map { n ->
                async(Dispatchers.IO) {
                    n to repo.reserve(106L, "BATTLE_GENERIC", "stable-battle-1:pay", "a-$n", n * 1000L)
                }
            }.awaitAll()
        }
        assertEquals(1, results.count { it.second })
        assertEquals(1, repo.unresolvedForCharacter(106L).size)
    }

    @Test fun differentPendingSourceKeysWithinSameKindAreSerialized() = withRepository { repo ->
        val contenders = coroutineScope {
            (1..12).map { n ->
                async(Dispatchers.IO) {
                    n to repo.reserve(108L, "DUNGEON_NORMAL", "different-run-$n", "token-$n", n * 1000L)
                }
            }.awaitAll()
        }
        val winner = contenders.single { it.second }.first
        assertEquals(1, repo.unresolvedForCharacter(108L).size)
        assertFalse(repo.reserve(108L, "DUNGEON_NORMAL", "another-run", "other", 20_000L))
        assertFalse(repo.settle(108L, "DUNGEON_NORMAL", "different-run-$winner", "other", 21_000L))
        assertTrue(repo.settle(108L, "DUNGEON_NORMAL", "different-run-$winner", "token-$winner", 22_000L))
        assertTrue(repo.reserve(108L, "DUNGEON_NORMAL", "another-run", "other", 23_000L))
        assertEquals(1, repo.unresolvedForCharacter(108L).size)
    }

    @Test fun settledReplayMustNotPoisonNewPendingScope() = withRepository { repo ->
        assertTrue(repo.reserve(109L, "DUNGEON_WEEKLY", "week-1", "a", 1000L))
        assertTrue(repo.settle(109L, "DUNGEON_WEEKLY", "week-1", "a", 2000L))
        assertFalse(repo.reserve(109L, "DUNGEON_WEEKLY", "week-1", "a", 3000L))
        assertTrue(repo.reserve(109L, "DUNGEON_WEEKLY", "week-2", "b", 4000L))
        assertFalse(repo.reserve(109L, "DUNGEON_WEEKLY", "week-3", "c", 5000L))
        assertFalse(repo.settle(109L, "DUNGEON_WEEKLY", "week-1", "a", 6000L))
        assertTrue(repo.settle(109L, "DUNGEON_WEEKLY", "week-2", "b", 7000L))
    }

    @Test fun partyMembersHaveIndependentBattleReceiptsAfterRestart() = withRepository { repo ->
        val previousBattle = "b:adcdbcc9-a2e1-4268-ac6e-e601be73b27f:GENERIC"
        val nextBattle = "b:37ff4e2f-f389-4b0b-b651-d3e2daf45d55:GENERIC"
        assertTrue(repo.reserve(211L, "BATTLE_GENERIC", previousBattle, "a", 1000L))
        assertTrue(repo.reserve(212L, "BATTLE_GENERIC", previousBattle, "b", 1000L))
        val afterCrash = RewardLedgerRepositoryImpl()
        assertFalse(afterCrash.reserve(211L, "BATTLE_GENERIC", nextBattle, "a2", 2000L))
        assertTrue(afterCrash.settle(212L, "BATTLE_GENERIC", previousBattle, "b", 3000L))
        // Another member's finished award does not unblock the pending member.
        assertFalse(afterCrash.reserve(211L, "BATTLE_GENERIC", nextBattle, "a2", 4000L))
        assertTrue(afterCrash.reserve(212L, "BATTLE_GENERIC", nextBattle, "b2", 4000L))
        assertFalse(afterCrash.reserve(212L, "BATTLE_GENERIC", previousBattle, "replay", 5000L))
        assertEquals(1, afterCrash.unresolvedForCharacter(211L).size)
        assertEquals(1, afterCrash.unresolvedForCharacter(212L).size)
    }

    @Test fun weeklyAllPassReceiptPreventsSameWeekReplayButAllowsNextCalendarWeek() = withRepository { repo ->
        val current = "w:20261005:0"
        val next = "w:20261012:0"
        assertTrue(repo.reserve(110L, "DUNGEON_ALL_PASS", current, "claim-1", 1000L))
        assertFalse(repo.reserve(110L, "DUNGEON_ALL_PASS", current, "claim-replay", 2000L))
        assertFalse(repo.reserve(110L, "DUNGEON_ALL_PASS", next, "new-week", 3000L))
        assertTrue(repo.settle(110L, "DUNGEON_ALL_PASS", current, "claim-1", 4000L))
        assertFalse(repo.reserve(110L, "DUNGEON_ALL_PASS", current, "claim-replay", 5000L))
        assertTrue(repo.reserve(110L, "DUNGEON_ALL_PASS", next, "new-week", 6000L))
        assertTrue(repo.settle(110L, "DUNGEON_ALL_PASS", next, "new-week", 7000L))
        assertTrue(repo.unresolvedForCharacter(110L).isEmpty())
    }

    @Test fun rejectedIdentifiersCannotPoisonTheLedger() = withRepository { repo ->
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.reserve(0L, "BATTLE_GENERIC", "run", "a", 1L) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.reserve(107L, "invalid-type", "run", "a", 1L) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.reserve(107L, "BATTLE_GENERIC", "", "a", 1L) }
        }
        assertTrue(repo.unresolvedForCharacter(107L).isEmpty())
    }
}
