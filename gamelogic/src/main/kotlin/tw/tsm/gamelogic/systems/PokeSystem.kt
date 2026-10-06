package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.PokeAward
import tw.tsm.domain.data.PokeCandidate
import tw.tsm.domain.data.PokeStateData
import tw.tsm.domain.interfaces.repositories.PokeRepository
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val pokeLogger = KotlinLogging.logger {}

sealed interface PokeOutcome {
    data class Success(
        val state: PokeStateData,
        val award: PokeAward,
        val changedBagSlots: List<Pair<Int, ThingData>>,
        val currencyBalances: Map<Int, Int>,
    ) : PokeOutcome
    data class Fail(val kind: Int) : PokeOutcome
}

class PokeSystem(
    private val repository: PokeRepository,
    candidates: List<PokeCandidate>,
    private val rates: Map<Int, Int>,
    private val inventory: InventorySystem,
    private val currencies: PlayerCurrencySystem,
    private val itemDefs: Map<Int, ItemDef>,
    private val random: Random = Random.Default,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val candidates = candidates.sortedBy { it.id }
    private val byId = this.candidates.associateBy { it.id }

    init {
        require(this.candidates.isNotEmpty() && this.candidates.size <= 255)
        require(rates.keys == setOf(1, 2, 3, 4))
        require(this.candidates.all { rates[it.kind] != null })
    }

    suspend fun state(characterId: Long): PokeStateData =
        lock(characterId).withLock { sanitize(repository.load(characterId)) }

    fun awards(state: PokeStateData): List<PokeAward> = candidates.map { c ->
        PokeAward(c.id, c.kind, c.itemId, c.count, state.opened[c.id] ?: 0, rates.getValue(c.kind))
    }

    suspend fun depositCoinWithPayment(
        characterId: Long,
        amount: Int,
        payment: suspend () -> Boolean,
    ): PokeStateData? {
        if (amount <= 0) return null
        return lock(characterId).withLock {
            val old = sanitize(repository.load(characterId))
            if (old.coin + amount > MAX_COIN) return@withLock null
            val next = old.copy(coin = old.coin + amount)
            repository.save(characterId, next)
            try {
                if (!payment()) {
                    repository.save(characterId, old)
                    return@withLock null
                }
            } catch (e: Exception) {
                repository.save(characterId, old)
                throw e
            }
            next
        }
    }

    suspend fun resetWithPayment(characterId: Long, payment: suspend () -> Boolean): PokeStateData? =
        lock(characterId).withLock {
            val old = sanitize(repository.load(characterId))
            val next = old.copy(opened = emptyMap())
            repository.save(characterId, next)
            try {
                if (!payment()) {
                    repository.save(characterId, old)
                    return@withLock null
                }
            } catch (e: Exception) {
                repository.save(characterId, old)
                throw e
            }
            next
        }

    suspend fun poke(characterId: Long, gridIndex: Int): PokeOutcome =
        lock(characterId).withLock {
            if (gridIndex !in 1..GRID_COUNT) return@withLock PokeOutcome.Fail(FAIL_BAD_ROLL)
            val old = sanitize(repository.load(characterId))
            if (gridIndex in old.opened.values) return@withLock PokeOutcome.Fail(FAIL_ALREADY)
            if (old.opened.size >= GRID_COUNT) return@withLock PokeOutcome.Fail(FAIL_FULL)
            val cost = old.opened.size * 2 + 1
            if (old.coin < cost) return@withLock PokeOutcome.Fail(FAIL_COIN)

            val available = candidates.filter { it.id !in old.opened }
            if (available.isEmpty()) return@withLock PokeOutcome.Fail(FAIL_BAD_ROLL)
            val selected = pick(available)
            val def = itemDefs[selected.itemId] ?: return@withLock PokeOutcome.Fail(FAIL_DEFAULT)

            var directKind = 0
            var directAmount = 0
            if (def.directUse == 1) {
                if (def.specialAbility != DIRECT_CURRENCY_SPECIAL) return@withLock PokeOutcome.Fail(FAIL_DEFAULT)
                directKind = def.attr1Kind - DIRECT_CURRENCY_OFFSET
                val amount = def.attr1Value.toLong() * selected.count.toLong()
                if (directKind !in PlayerCurrencySystem.MIN_KIND..PlayerCurrencySystem.MAX_KIND ||
                    amount <= 0 || amount > Int.MAX_VALUE
                ) return@withLock PokeOutcome.Fail(FAIL_DEFAULT)
                directAmount = amount.toInt()
                if (currencies.get(characterId, directKind).toLong() + directAmount > PlayerCurrencySystem.MAX_VALUE) {
                    return@withLock PokeOutcome.Fail(FAIL_DEFAULT)
                }
            } else if (!inventory.canAddRewardsToBag(characterId, listOf(selected.itemId to selected.count))) {
                return@withLock PokeOutcome.Fail(FAIL_DEFAULT)
            }

            val next = old.copy(
                coin = old.coin - cost,
                opened = old.opened + (selected.id to gridIndex),
            )
            repository.save(characterId, next)

            if (directKind != 0) {
                if (!currencies.add(characterId, directKind, directAmount)) {
                    repository.save(characterId, old)
                    return@withLock PokeOutcome.Fail(FAIL_DEFAULT)
                }
                return@withLock PokeOutcome.Success(
                    next, toAward(selected, gridIndex), emptyList(),
                    mapOf(directKind to currencies.get(characterId, directKind)),
                )
            }

            val changed = inventory.addRewardsToBagExact(characterId, listOf(selected.itemId to selected.count))
            if (changed == null) {
                repository.save(characterId, old)
                return@withLock PokeOutcome.Fail(FAIL_DEFAULT)
            }
            pokeLogger.info { "Poke reward granted" }
            PokeOutcome.Success(next, toAward(selected, gridIndex), changed, emptyMap())
        }

    private fun pick(pool: List<PokeCandidate>): PokeCandidate {
        val total = pool.sumOf { rates.getValue(it.kind) }
        var roll = random.nextInt(total)
        for (candidate in pool) {
            roll -= rates.getValue(candidate.kind)
            if (roll < 0) return candidate
        }
        return pool.last()
    }

    private fun toAward(c: PokeCandidate, index: Int) =
        PokeAward(c.id, c.kind, c.itemId, c.count, index, rates.getValue(c.kind))

    private fun sanitize(state: PokeStateData): PokeStateData {
        val valid = state.opened
            .filter { (awardId, grid) -> awardId in byId && grid in 1..GRID_COUNT }
            .entries.sortedBy { it.key }.distinctBy { it.value }.take(GRID_COUNT)
            .associate { it.key to it.value }
        return PokeStateData(state.coin.coerceIn(0, MAX_COIN), valid)
    }

    private fun lock(characterId: Long) = locks.computeIfAbsent(characterId) { Mutex() }

    companion object {
        const val MAX_COIN = 999
        const val GRID_COUNT = 25
        const val FAIL_DEFAULT = 2
        const val FAIL_ALREADY = 3
        const val FAIL_COIN = 4
        const val FAIL_FULL = 5
        const val FAIL_BAD_ROLL = 6
        private const val DIRECT_CURRENCY_SPECIAL = 216
        private const val DIRECT_CURRENCY_OFFSET = 8
    }
}
