package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.data.PokeRewardDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.PokeStateData
import tw.tsm.domain.interfaces.repositories.PokeRepository
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val pokeLogger = KotlinLogging.logger {}

sealed interface PokeClaimOutcome {
    data class Success(
        val reward: PokeRewardDef,
        val slotIndex: Int,
        val state: PokeStateData,
        val changedBagSlots: List<Pair<Int, ThingData>>,
        val currencyBalances: Map<Int, Int> = emptyMap(),
    ) : PokeClaimOutcome
    data object CoinInsufficient : PokeClaimOutcome
    data object AlreadyOpened : PokeClaimOutcome
    data object BoardFull : PokeClaimOutcome
    data object BagFull : PokeClaimOutcome
    data object InvalidRequest : PokeClaimOutcome
    data object UnsupportedReward : PokeClaimOutcome
}

/** DVT UIPoke / Trạc, mainKind 92. */
class PokeSystem(
    private val repository: PokeRepository,
    private val rewards: List<PokeRewardDef>,
    private val rateByKind: Map<Int, Int>,
    private val inventorySystem: InventorySystem,
    private val itemDefs: Map<Int, ItemDef> = emptyMap(),
    private val playerCurrencySystem: PlayerCurrencySystem? = null,
    private val random: Random = Random.Default,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    init {
        require(rewards.isNotEmpty())
        require(rewards.map { it.id }.toSet().size == rewards.size)
        require(rateByKind.keys.containsAll(setOf(1, 2, 3, 4)))
        require(rewards.all { (rateByKind[it.kind] ?: 0) > 0 })
    }

    suspend fun open(characterId: Long): PokeStateData =
        lock(characterId).withLock { loadSanitized(characterId) }

    suspend fun getCoin(characterId: Long): Int =
        lock(characterId).withLock { loadSanitized(characterId).coin }

    fun rewardSnapshot(state: PokeStateData): List<PokeRewardDef> = rewards.sortedBy { it.id }

    fun weightFor(reward: PokeRewardDef): Int = rateByKind.getValue(reward.kind)

    suspend fun depositCoinWithPayment(
        characterId: Long,
        amount: Int,
        payment: suspend () -> Boolean,
    ): PokeStateData? {
        if (amount <= 0) return null
        return lock(characterId).withLock {
            val current = loadSanitized(characterId)
            if (current.coin + amount > MAX_COIN) return@withLock null
            val updated = current.copy(coin = current.coin + amount)
            repository.save(characterId, updated)
            try {
                if (!payment()) {
                    repository.save(characterId, current)
                    return@withLock null
                }
            } catch (e: Exception) {
                repository.save(characterId, current)
                throw e
            }
            updated
        }
    }

    suspend fun resetBoardWithPayment(
        characterId: Long,
        payment: suspend () -> Boolean,
    ): PokeStateData? = lock(characterId).withLock {
        val current = loadSanitized(characterId)
        val updated = current.copy(openedSlots = emptyMap())
        repository.save(characterId, updated)
        try {
            if (!payment()) {
                repository.save(characterId, current)
                return@withLock null
            }
        } catch (e: Exception) {
            repository.save(characterId, current)
            throw e
        }
        updated
    }

    suspend fun claim(characterId: Long, slotIndex: Int): PokeClaimOutcome =
        lock(characterId).withLock {
            if (slotIndex !in 1..BOARD_SIZE) return@withLock PokeClaimOutcome.InvalidRequest
            val state = loadSanitized(characterId)
            if (slotIndex in state.openedSlots.values) return@withLock PokeClaimOutcome.AlreadyOpened
            if (state.openedSlots.size >= BOARD_SIZE) return@withLock PokeClaimOutcome.BoardFull

            val cost = state.openedSlots.size * 2 + 1
            if (state.coin < cost) return@withLock PokeClaimOutcome.CoinInsufficient

            val candidates = rewards.filterNot { state.openedSlots.containsKey(it.id) }
            if (candidates.isEmpty()) return@withLock PokeClaimOutcome.BoardFull
            val selected = weightedPick(candidates)

            val itemDef = itemDefs[selected.itemId]
                ?: return@withLock PokeClaimOutcome.UnsupportedReward
            val directCurrency = if (itemDef.directUse == 1) {
                val currencySystem = playerCurrencySystem
                    ?: return@withLock PokeClaimOutcome.UnsupportedReward
                if (itemDef.specialAbility != DIRECT_CURRENCY_SPECIAL) {
                    return@withLock PokeClaimOutcome.UnsupportedReward
                }
                val kind = itemDef.attr1Kind - DIRECT_CURRENCY_OFFSET
                val amountLong = itemDef.attr1Value.toLong() * selected.count.toLong()
                if (kind !in PlayerCurrencySystem.MIN_KIND..PlayerCurrencySystem.MAX_KIND ||
                    amountLong <= 0 || amountLong > Int.MAX_VALUE
                ) {
                    return@withLock PokeClaimOutcome.UnsupportedReward
                }
                val amount = amountLong.toInt()
                val current = currencySystem.get(characterId, kind)
                if (current.toLong() + amount > PlayerCurrencySystem.MAX_VALUE) {
                    return@withLock PokeClaimOutcome.UnsupportedReward
                }
                kind to amount
            } else {
                null
            }

            if (directCurrency == null &&
                !inventorySystem.canAddItemToBag(characterId, selected.itemId, selected.count)
            ) {
                return@withLock PokeClaimOutcome.BagFull
            }

            val updated = state.copy(
                coin = state.coin - cost,
                openedSlots = state.openedSlots + (selected.id to slotIndex),
            )
            repository.save(characterId, updated)

            if (directCurrency != null) {
                val (kind, amount) = directCurrency
                val currencySystem = playerCurrencySystem!!
                val granted = try {
                    currencySystem.add(characterId, kind, amount)
                } catch (e: Exception) {
                    pokeLogger.error(e) {
                        "Poke direct-currency grant failed: charId=$characterId item=${selected.itemId}"
                    }
                    false
                }
                if (!granted) {
                    try {
                        repository.save(characterId, state)
                    } catch (rollback: Exception) {
                        pokeLogger.error(rollback) { "Poke state rollback failed after currency grant failure" }
                    }
                    return@withLock PokeClaimOutcome.UnsupportedReward
                }
                return@withLock PokeClaimOutcome.Success(
                    reward = selected,
                    slotIndex = slotIndex,
                    state = updated,
                    changedBagSlots = emptyList(),
                    currencyBalances = mapOf(kind to currencySystem.get(characterId, kind)),
                )
            }

            val changed = try {
                inventorySystem.addItemsToBagExact(characterId, selected.itemId, selected.count)
            } catch (e: Exception) {
                null
            }
            if (changed == null) {
                try {
                    repository.save(characterId, state)
                } catch (rollback: Exception) {
                    pokeLogger.error(rollback) { "Poke state rollback failed" }
                }
                return@withLock PokeClaimOutcome.BagFull
            }

            PokeClaimOutcome.Success(selected, slotIndex, updated, changed)
        }

    private suspend fun loadSanitized(characterId: Long): PokeStateData {
        val loaded = repository.load(characterId)
        val validRewardIds = rewards.mapTo(hashSetOf()) { it.id }
        val cleaned = linkedMapOf<Int, Int>()
        for ((rewardId, slot) in loaded.openedSlots) {
            if (rewardId in validRewardIds && slot in 1..BOARD_SIZE &&
                slot !in cleaned.values && cleaned.size < BOARD_SIZE
            ) {
                cleaned[rewardId] = slot
            }
        }
        val state = PokeStateData(loaded.coin.coerceIn(0, MAX_COIN), cleaned)
        if (state != loaded) repository.save(characterId, state)
        return state
    }

    private fun weightedPick(candidates: List<PokeRewardDef>): PokeRewardDef {
        val total = candidates.sumOf { weightFor(it) }
        var roll = random.nextInt(total)
        for (reward in candidates) {
            roll -= weightFor(reward)
            if (roll < 0) return reward
        }
        return candidates.last()
    }

    private fun lock(characterId: Long): Mutex =
        locks.computeIfAbsent(characterId) { Mutex() }

    companion object {
        const val MAX_COIN = 999
        const val BOARD_SIZE = 25
        const val COIN_ITEM_ID = 29507
        const val COIN_POINT_PRICE = 9
        const val SELL_COIN = 44
        const val SELL_RESET = 46
        private const val DIRECT_CURRENCY_SPECIAL = 216
        private const val DIRECT_CURRENCY_OFFSET = 8
    }
}
