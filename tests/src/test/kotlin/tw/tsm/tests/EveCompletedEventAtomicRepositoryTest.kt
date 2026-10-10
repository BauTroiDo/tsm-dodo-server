package tw.tsm.tests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.persistence.repositories.QuestRepositoryImpl
import tw.tsm.persistence.tables.CharacterCompletedEvents

/**
 * In-memory transactional integration tests. Never touch production MySQL.
 *
 * MySQL uses INSERT ... ON DUPLICATE KEY UPDATE; H2 uses an equivalent
 * unique-primary-key MERGE. Both ensure the increment is performed by SQL,
 * not calculated from a stale value read in application code.
 */
class EveCompletedEventAtomicRepositoryTest {
    private fun withRepository(test: suspend (QuestRepositoryImpl) -> Unit) {
        val previous = TransactionManager.defaultDatabase
        val database = Database.connect(
            url = "jdbc:h2:mem:quest_evecount_${System.nanoTime()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000",
            driver = "org.h2.Driver",
        )
        TransactionManager.defaultDatabase = database
        try {
            transaction(database) { SchemaUtils.create(CharacterCompletedEvents) }
            runBlocking { test(QuestRepositoryImpl()) }
        } finally {
            TransactionManager.defaultDatabase = previous
        }
    }

    @Test fun insertsFirstCompletionThenAtomicallyIncrementsExactlyOnce() = withRepository { repo ->
        assertEquals(emptyMap<Int, Int>(), repo.getCompletedEventCounts(511L, 10821))
        repeat(4) { repo.insertCompletedEvent(511L, 10821, 3) }
        assertEquals(mapOf(3 to 4), repo.getCompletedEventCounts(511L, 10821))
    }

    @Test fun parallelEveCompletionsDoNotLoseIncrements() = withRepository { repo ->
        val amount=12
        coroutineScope { (1..amount).map {
            async(Dispatchers.IO) { repo.insertCompletedEvent(512L, 10821, 3) }
        }.awaitAll() }
        assertEquals(mapOf(3 to amount), repo.getCompletedEventCounts(512L, 10821))
    }

    @Test fun incrementsAreIsolatedAndSceneResetUsesFreshCountOne() = withRepository { repo ->
        repeat(3) { repo.insertCompletedEvent(513L, 10821, 3) }
        repo.insertCompletedEvent(513L, 10821, 4)
        repo.insertCompletedEvent(513L, 10853, 3)
        repo.insertCompletedEvent(514L, 10821, 3)
        assertEquals(mapOf(3 to 3,4 to 1), repo.getCompletedEventCounts(513L, 10821))
        assertEquals(mapOf(3 to 1), repo.getCompletedEventCounts(513L, 10853))
        assertEquals(mapOf(3 to 1), repo.getCompletedEventCounts(514L, 10821))
        repo.clearCompletedEvents(513L, 10821)
        repo.insertCompletedEvent(513L, 10821, 3)
        assertEquals(mapOf(3 to 1), repo.getCompletedEventCounts(513L, 10821))
    }
}
