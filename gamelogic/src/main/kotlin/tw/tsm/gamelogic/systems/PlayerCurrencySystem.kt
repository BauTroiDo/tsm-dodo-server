package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.interfaces.repositories.CharacterCurrencyRepository
import java.util.concurrent.ConcurrentHashMap

private val currencyLogger = KotlinLogging.logger {}

/**
 * DVT generic currencies backed by Role.playerData.currencys.
 *
 * Core currencies stay authoritative in their existing systems:
 * 1=Point, 2=locked Point, 3=game gold.
 * This cache owns 4..16 (OrgGold/DataFight/furniture/Elf/Soul/tickets/Treasure).
 * Mutations are persisted immediately because these balances are purchase state.
 */
class PlayerCurrencySystem(
    private val repository: CharacterCurrencyRepository
) {
    private val cache = ConcurrentHashMap<Long, MutableMap<Int, Int>>()

    suspend fun load(characterId: Long): Map<Int, Int> {
        val loaded = repository.loadAll(characterId)
            .filterKeys { it in MIN_KIND..MAX_KIND }
            .toMutableMap()
        cache[characterId] = loaded
        return loaded.toMap()
    }

    fun get(characterId: Long, currencyKind: Int): Int =
        cache[characterId]?.get(currencyKind) ?: 0

    fun getAll(characterId: Long): Map<Int, Int> =
        cache[characterId]?.toMap() ?: emptyMap()

    suspend fun add(characterId: Long, currencyKind: Int, amount: Int): Boolean {
        if (currencyKind !in MIN_KIND..MAX_KIND || amount <= 0) return false
        val map = cache[characterId] ?: return false
        val next: Int
        synchronized(map) {
            val current = map[currencyKind] ?: 0
            val longNext = current.toLong() + amount
            if (longNext > MAX_VALUE) return false
            next = longNext.toInt()
            map[currencyKind] = next
        }
        try {
            repository.setValue(characterId, currencyKind, next)
        } catch (e: Exception) {
            synchronized(map) { map[currencyKind] = (next - amount).coerceAtLeast(0) }
            throw e
        }
        return true
    }

    suspend fun subtract(characterId: Long, currencyKind: Int, amount: Int): Boolean {
        if (currencyKind !in MIN_KIND..MAX_KIND || amount <= 0) return false
        val map = cache[characterId] ?: return false
        val previous: Int
        val next: Int
        synchronized(map) {
            previous = map[currencyKind] ?: 0
            if (previous < amount) return false
            next = previous - amount
            map[currencyKind] = next
        }
        try {
            repository.setValue(characterId, currencyKind, next)
        } catch (e: Exception) {
            synchronized(map) { map[currencyKind] = previous }
            throw e
        }
        return true
    }

    suspend fun set(characterId: Long, currencyKind: Int, value: Int): Boolean {
        if (currencyKind !in MIN_KIND..MAX_KIND || value !in 0..MAX_VALUE) return false
        val map = cache[characterId] ?: return false
        val previous = synchronized(map) {
            val old = map[currencyKind] ?: 0
            map[currencyKind] = value
            old
        }
        try {
            repository.setValue(characterId, currencyKind, value)
        } catch (e: Exception) {
            synchronized(map) { map[currencyKind] = previous }
            throw e
        }
        return true
    }

    fun unload(characterId: Long) {
        cache.remove(characterId)
        currencyLogger.debug { "Unload DVT currencies: characterId=$characterId" }
    }

    companion object {
        const val MIN_KIND = 4
        const val MAX_KIND = 16
        const val MAX_VALUE = 999_999_999
    }
}
