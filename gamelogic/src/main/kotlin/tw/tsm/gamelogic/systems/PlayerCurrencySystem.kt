package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.interfaces.repositories.CharacterCurrencyRepository
import tw.tsm.domain.interfaces.repositories.MiningTsAwardCommit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val currencyLogger = KotlinLogging.logger {}

/**
 * DVT currencies 4..16. Serialize writes for each character across suspension:
 * older code mutated cache BEFORE async SQL and could overwrite another
 * concurrent change or rollback a later successful award.
 *
 * A successful verified mining receipt must be applied under this same fence.
 * This is infrastructure only; no mining yield is inferred or generated.
 */
class PlayerCurrencySystem(
    private val repository: CharacterCurrencyRepository
) {
    private val cache = ConcurrentHashMap<Long, MutableMap<Int, Int>>()
    private val writerFences = Array(256) { Mutex() }

    private fun stripe(characterId: Long): Int =
        (characterId xor (characterId ushr 32)).toInt() and 255

    private suspend fun <T> withWriterFence(characterId: Long, action: suspend () -> T): T {
        val callerJob = currentCoroutineContext()[Job]
        // Keep SQL commit and RAM publication together even if the user
        // disconnects/cancels while repository.setValue is suspending.
        return withContext(NonCancellable + Dispatchers.IO) {
            writerFences[stripe(characterId)].withLock {
                callerJob?.ensureActive() // cancelled waiters must not start writes
                action()
            }
        }
    }

    suspend fun load(characterId: Long): Map<Int, Int> = withWriterFence(characterId) {
        val loaded = repository.loadAll(characterId)
            .filterKeys { it in MIN_KIND..MAX_KIND }
            .toMutableMap()
        cache[characterId] = loaded
        loaded.toMap()
    }

    fun get(characterId: Long, currencyKind: Int): Int =
        cache[characterId]?.let { map ->
            synchronized(map) { map[currencyKind] ?: 0 }
        } ?: 0

    fun getAll(characterId: Long): Map<Int, Int> =
        cache[characterId]?.let { map ->
            synchronized(map) { map.toMap() }
        } ?: emptyMap()

    suspend fun add(characterId: Long, currencyKind: Int, amount: Int): Boolean {
        if (currencyKind !in MIN_KIND..MAX_KIND || amount <= 0) return false
        return withWriterFence(characterId) {
            val map = cache[characterId] ?: return@withWriterFence false
            val current = synchronized(map) { map[currencyKind] ?: 0 }
            val next = current.toLong() + amount
            if (current < 0 || next > MAX_VALUE) return@withWriterFence false
            // Persist first. No RAM rollback is needed if SQL fails.
            repository.setValue(characterId, currencyKind, next.toInt())
            synchronized(map) { map[currencyKind] = next.toInt() }
            true
        }
    }

    suspend fun subtract(characterId: Long, currencyKind: Int, amount: Int): Boolean {
        if (currencyKind !in MIN_KIND..MAX_KIND || amount <= 0) return false
        return withWriterFence(characterId) {
            val map = cache[characterId] ?: return@withWriterFence false
            val current = synchronized(map) { map[currencyKind] ?: 0 }
            if (current < amount) return@withWriterFence false
            val next = current - amount
            repository.setValue(characterId, currencyKind, next)
            synchronized(map) { map[currencyKind] = next }
            true
        }
    }

    suspend fun set(characterId: Long, currencyKind: Int, value: Int): Boolean {
        if (currencyKind !in MIN_KIND..MAX_KIND || value !in 0..MAX_VALUE) return false
        return withWriterFence(characterId) {
            val map = cache[characterId] ?: return@withWriterFence false
            repository.setValue(characterId, currencyKind, value)
            synchronized(map) { map[currencyKind] = value }
            true
        }
    }

    /**
     * Future mining settlement entrypoint: this fence covers normal purchases
     * and the atomic DB reserve debit + TS credit + receipt.
     * Returns null without mutation when no loaded character/cache exists.
     * Replayed receipts do NOT overwrite any more recent wallet balance.
     *
     * The callback must verify the DB balance equals expectedTsBalance
     * (optimistic concurrency), and must itself be one SQL transaction.
     */
    suspend fun applyVerifiedMiningTsAward(
        characterId: Long,
        commit: suspend (expectedTsBalance: Int) -> MiningTsAwardCommit?,
    ): MiningTsAwardCommit? {
        if (characterId <= 0) return null
        return withWriterFence(characterId) {
            val map = cache[characterId] ?: return@withWriterFence null
            val current = synchronized(map) { map[TS_CURRENCY_KIND] ?: 0 }
            if (current !in 0..MAX_VALUE) return@withWriterFence null
            val receipt = commit(current) ?: return@withWriterFence null
            if (receipt.newlyApplied) {
                // SQL already committed atomically, so this is publication,
                // not another persistence operation that could duplicate coins.
                check(receipt.tsBalance in current..MAX_VALUE)
                synchronized(map) { map[TS_CURRENCY_KIND] = receipt.tsBalance }
            }
            receipt
        }
    }

    fun unload(characterId: Long) {
        cache.remove(characterId)
        currencyLogger.debug { "Unload DVT currencies: characterId=$characterId" }
    }

    companion object {
        const val MIN_KIND = 4
        const val MAX_KIND = 16
        const val TS_CURRENCY_KIND = 8
        const val MAX_VALUE = 999_999_999
    }
}
