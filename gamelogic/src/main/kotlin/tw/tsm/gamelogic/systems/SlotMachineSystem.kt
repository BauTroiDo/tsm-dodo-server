package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.SlotMachineAward
import tw.tsm.domain.data.SlotMachinePools
import tw.tsm.domain.data.SlotMachineStateData
import tw.tsm.domain.interfaces.repositories.SlotMachineRepository
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val slotSystemLogger = KotlinLogging.logger {}

sealed interface SlotMachineSpinOutcome {
    data class Success(
        val state: SlotMachineStateData,
        val resultSlotIndices: List<Int>,
        val changedBagSlots: List<Pair<Int, ThingData>>,
        val currencyBalances: Map<Int, Int>,
    ) : SlotMachineSpinOutcome

    data object CoinInsufficient : SlotMachineSpinOutcome
    data object BagFull : SlotMachineSpinOutcome
    data object InvalidBoard : SlotMachineSpinOutcome
    data object RewardGrantFailed : SlotMachineSpinOutcome
    data class UnsupportedDirectReward(
        val itemId: Int,
        val state: SlotMachineStateData,
        val resultSlotIndices: List<Int>,
    ) : SlotMachineSpinOutcome
}

/**
 * DVT UISlotMachine / mainKind 72.
 *
 * Authoritative client contract:
 * - board = 16 awards, ordered into probability tiers 1 + 2 + 3 + 10;
 * - client normalizes award-index weights to 1 / 2 / 5 / 8, total exactly 100;
 * - normal costs 1 coin, double costs 2 coins and doubles quantity,
 *   triple costs 6 coins and returns three results;
 * - internal coin range is 0..999.
 *
 * IOS_Slot.dat provides four weighted candidate pools A/B/C/D. The 16-board
 * composition follows the exact 1/2/3/10 tier cardinality exposed by the client.
 */
class SlotMachineSystem(
    private val repository: SlotMachineRepository,
    private val pools: SlotMachinePools,
    private val inventorySystem: InventorySystem,
    private val playerCurrencySystem: PlayerCurrencySystem,
    private val itemDefs: Map<Int, ItemDef>,
    private val random: Random = Random.Default
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun open(characterId: Long): SlotMachineStateData =
        lock(characterId).withLock { loadOrCreate(characterId) }

    suspend fun getCoin(characterId: Long): Int =
        lock(characterId).withLock { loadOrCreate(characterId).coin }

    suspend fun depositCoin(characterId: Long, amount: Int): SlotMachineStateData? {
        if (amount <= 0) return null
        return lock(characterId).withLock {
            val current = loadOrCreate(characterId)
            if (current.coin + amount > MAX_COIN) return@withLock null
            val updated = current.copy(coin = current.coin + amount)
            repository.save(characterId, updated)
            updated
        }
    }

    /**
     * Nạp xu và thực thi payment trong cùng mutex SlotMachine.
     *
     * Coin được reserve/persist trước, sau đó payment chạy khi lock vẫn còn giữ.
     * Nếu payment trả false/throw, state coin được rollback trước khi trả về.
     * Điều này ngăn một request quay song song tiêu coin vừa reserve trước khi
     * UISell hoàn tất trừ item/Point.
     */
    suspend fun depositCoinWithPayment(
        characterId: Long,
        amount: Int,
        payment: suspend () -> Boolean
    ): SlotMachineStateData? {
        if (amount <= 0) return null
        return lock(characterId).withLock {
            val current = loadOrCreate(characterId)
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

    /** Roll back a coin deposit when the paired payment mutation fails. */
    suspend fun withdrawCoin(characterId: Long, amount: Int): SlotMachineStateData? {
        if (amount <= 0) return null
        return lock(characterId).withLock {
            val current = loadOrCreate(characterId)
            if (current.coin < amount) return@withLock null
            val updated = current.copy(coin = current.coin - amount)
            repository.save(characterId, updated)
            updated
        }
    }

    /**
     * Regenerate the 16-slot board.
     *
     * Payment and the once-per-spin change limit belong to UISell sellId=41.
     * That payment formula is still UNKNOWN and is intentionally not invented here.
     */
    suspend fun regenerateBoard(characterId: Long): SlotMachineStateData =
        lock(characterId).withLock {
            val current = loadOrCreate(characterId)
            val updated = current.copy(awards = generateBoard())
            repository.save(characterId, updated)
            updated
        }

    suspend fun spin(characterId: Long, subKind: Int): SlotMachineSpinOutcome =
        lock(characterId).withLock {
            val cost = when (subKind) {
                SUB_NORMAL -> 1
                SUB_DOUBLE -> 2
                SUB_TRIPLE -> 6
                else -> return@withLock SlotMachineSpinOutcome.InvalidBoard
            }

            val state = loadOrCreate(characterId)
            if (state.coin < cost) return@withLock SlotMachineSpinOutcome.CoinInsufficient
            if (!isValidBoard(state.awards)) return@withLock SlotMachineSpinOutcome.InvalidBoard

            val drawCount = if (subKind == SUB_TRIPLE) 3 else 1
            val multiplier = if (subKind == SUB_DOUBLE) 2 else 1

            // Tam liên đạn returns three indices in one S72-2. DVT client does not
            // require them to be distinct.
            val selected = List(drawCount) { pickBoardAward(state.awards) }
            val selectedSlots = selected.map { it.slotIndex }

            val bagRewards = mutableListOf<Pair<Int, Int>>()
            val currencyRewards = linkedMapOf<Int, Int>()

            for (award in selected) {
                val def = itemDefs[award.itemId]
                    ?: return@withLock SlotMachineSpinOutcome.UnsupportedDirectReward(
                        award.itemId,
                        state,
                        selectedSlots
                    )
                val rewardCount = award.count * multiplier

                if (def.directUse == 1) {
                    if (def.specialAbility == DIRECT_CURRENCY_SPECIAL) {
                        // DVT Item_C direct-use currency tickets encode
                        // attr1Kind = ECurrency + 8:
                        // 18->Soul(10), 19->ColorSoul(11),
                        // 20->WarriorTicket(12), 22->ExclusiveWeaponTicket(14).
                        val currencyKind = def.attr1Kind - DIRECT_CURRENCY_OFFSET
                        val amount = def.attr1Value * rewardCount
                        if (currencyKind !in PlayerCurrencySystem.MIN_KIND..PlayerCurrencySystem.MAX_KIND ||
                            amount <= 0
                        ) {
                            return@withLock SlotMachineSpinOutcome.UnsupportedDirectReward(
                                award.itemId,
                                state,
                                selectedSlots
                            )
                        }
                        currencyRewards[currencyKind] =
                            (currencyRewards[currencyKind] ?: 0) + amount
                    } else {
                        // IOS_Slot currently also contains item 31922
                        // (directUse=1, specialAbility=239). Its server effect is
                        // not proven by DVT Lua/data, so do not fake it as a bag item.
                        return@withLock SlotMachineSpinOutcome.UnsupportedDirectReward(
                            award.itemId,
                            state,
                            selectedSlots
                        )
                    }
                } else {
                    bagRewards += award.itemId to rewardCount
                }
            }

            if (bagRewards.isNotEmpty() &&
                !inventorySystem.canAddRewardsToBag(characterId, bagRewards)
            ) {
                return@withLock SlotMachineSpinOutcome.BagFull
            }

            // Preflight direct-currency overflow before any mutation.
            for ((kind, amount) in currencyRewards) {
                val current = playerCurrencySystem.get(characterId, kind)
                if (current.toLong() + amount > PlayerCurrencySystem.MAX_VALUE) {
                    return@withLock SlotMachineSpinOutcome.RewardGrantFailed
                }
            }

            // Persist coin deduction first. Every following mutation has an explicit
            // rollback path, so a failed reward grant cannot consume the spin coin.
            val deducted = state.copy(coin = state.coin - cost)
            slotSystemLogger.info { "SLOTTRACE COIN_SAVE_BEGIN" }
            repository.save(characterId, deducted)
            slotSystemLogger.info { "SLOTTRACE COIN_SAVE_DONE" }

            val credited = mutableListOf<Pair<Int, Int>>()
            try {
                for ((kind, amount) in currencyRewards) {
                    if (!playerCurrencySystem.add(characterId, kind, amount)) {
                        rollbackCurrencies(characterId, credited)
                        repository.save(characterId, state)
                        return@withLock SlotMachineSpinOutcome.RewardGrantFailed
                    }
                    credited += kind to amount
                }
            } catch (e: Exception) {
                slotSystemLogger.error(e) {
                    "SlotMachine direct-currency grant failed: charId=$characterId rewards=$currencyRewards"
                }
                rollbackCurrencies(characterId, credited)
                repository.save(characterId, state)
                return@withLock SlotMachineSpinOutcome.RewardGrantFailed
            }

            val changed = if (bagRewards.isEmpty()) {
                emptyList()
            } else {
                slotSystemLogger.info { "SLOTTRACE BAG_GRANT_BEGIN" }
                inventorySystem.addRewardsToBagExact(characterId, bagRewards)
                    ?: run {
                        rollbackCurrencies(characterId, credited)
                        repository.save(characterId, state)
                        return@withLock SlotMachineSpinOutcome.BagFull
                    }
            }

            SlotMachineSpinOutcome.Success(
                state = deducted,
                resultSlotIndices = selectedSlots,
                changedBagSlots = changed,
                currencyBalances = currencyRewards.keys.associateWith {
                    playerCurrencySystem.get(characterId, it)
                }
            )
        }

    fun wireProbability(awardIndex: Int): Int = when (awardIndex) {
        1 -> 1
        in 2..3 -> 2
        in 4..6 -> 5
        in 7..16 -> 8
        else -> 0
    }

    private suspend fun rollbackCurrencies(
        characterId: Long,
        credited: List<Pair<Int, Int>>
    ) {
        for ((kind, amount) in credited.asReversed()) {
            try {
                if (!playerCurrencySystem.subtract(characterId, kind, amount)) {
                    slotSystemLogger.error {
                        "SlotMachine currency rollback failed: charId=$characterId kind=$kind amount=$amount"
                    }
                }
            } catch (e: Exception) {
                slotSystemLogger.error(e) {
                    "SlotMachine currency rollback exception: charId=$characterId kind=$kind amount=$amount"
                }
            }
        }
    }

    private suspend fun loadOrCreate(characterId: Long): SlotMachineStateData {
        slotSystemLogger.info { "SLOTTRACE LOAD_BEGIN" }
        val loaded = repository.load(characterId)
        slotSystemLogger.info { "SLOTTRACE LOAD_DONE" }
        if (isValidBoard(loaded.awards)) return loaded

        val created = loaded.copy(
            coin = loaded.coin.coerceIn(0, MAX_COIN),
            awards = generateBoard()
        )
        repository.save(characterId, created)
        return created
    }

    private fun generateBoard(): List<SlotMachineAward> {
        require(
            pools.poolA.isNotEmpty() && pools.poolB.size >= 2 &&
                pools.poolC.size >= 3 && pools.poolD.size >= 10
        ) {
            "IOS_Slot pools cannot build a 16-slot DVT board"
        }

        val entries = buildList {
            addAll(weightedSampleWithoutReplacement(pools.poolA, 1))
            addAll(weightedSampleWithoutReplacement(pools.poolB, 2))
            addAll(weightedSampleWithoutReplacement(pools.poolC, 3))
            addAll(weightedSampleWithoutReplacement(pools.poolD, 10))
        }
        check(entries.size == BOARD_SIZE)

        val slots = (1..BOARD_SIZE).shuffled(random)
        return entries.mapIndexed { index, entry ->
            SlotMachineAward(
                awardIndex = index + 1,
                itemId = entry.itemId,
                count = entry.count,
                slotIndex = slots[index]
            )
        }
    }

    private fun weightedSampleWithoutReplacement(
        source: List<tw.tsm.domain.data.SlotPoolEntry>,
        count: Int
    ): List<tw.tsm.domain.data.SlotPoolEntry> {
        val candidates = source.toMutableList()
        val result = ArrayList<tw.tsm.domain.data.SlotPoolEntry>(count)
        repeat(count) {
            val total = candidates.sumOf { it.weight.coerceAtLeast(0) }
            require(total > 0) { "Slot pool has zero total weight" }
            var roll = random.nextInt(total)
            var selectedIndex = candidates.lastIndex
            for (i in candidates.indices) {
                roll -= candidates[i].weight.coerceAtLeast(0)
                if (roll < 0) {
                    selectedIndex = i
                    break
                }
            }
            result += candidates.removeAt(selectedIndex)
        }
        return result
    }

    private fun pickBoardAward(awards: List<SlotMachineAward>): SlotMachineAward {
        var roll = random.nextInt(100)
        for (award in awards.sortedBy { it.awardIndex }) {
            roll -= wireProbability(award.awardIndex)
            if (roll < 0) return award
        }
        return awards.maxBy { it.awardIndex }
    }

    private fun isValidBoard(awards: List<SlotMachineAward>): Boolean =
        awards.size == BOARD_SIZE &&
            awards.map { it.awardIndex }.toSet() == (1..BOARD_SIZE).toSet() &&
            awards.map { it.slotIndex }.toSet() == (1..BOARD_SIZE).toSet() &&
            awards.all { it.itemId > 0 && it.count > 0 }

    private fun lock(characterId: Long): Mutex =
        locks.computeIfAbsent(characterId) { Mutex() }

    companion object {
        const val MAX_COIN = 999
        const val BOARD_SIZE = 16

        const val SUB_NORMAL = 2
        const val SUB_DOUBLE = 3
        const val SUB_TRIPLE = 4

        private const val DIRECT_CURRENCY_SPECIAL = 216
        private const val DIRECT_CURRENCY_OFFSET = 8
    }
}
