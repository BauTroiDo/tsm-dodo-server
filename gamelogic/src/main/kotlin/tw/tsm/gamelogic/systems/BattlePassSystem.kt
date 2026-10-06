package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.ActivityTimeDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.PassAwardDef
import tw.tsm.domain.valueobjects.ThingData
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

private val battlePassLogger = KotlinLogging.logger {}

/** DVT claims only; acquisition/payment and rollover resets require their own evidence. */
class BattlePassSystem(
    private val awards: List<PassAwardDef>,
    private val times: Map<Int, ActivityTimeDef>,
    private val items: Map<Int, ItemDef>,
    private val inventory: InventorySystem,
    private val counts: RoleCountSystem,
    private val flags: BitFlagSystem,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    fun currentSeason(now: LocalDateTime): Int? = awards.map { it.seasonId }.distinct().firstOrNull { id ->
        val def = times[id] ?: return@firstOrNull false
        def.switchKind > 0 && now.isAfter(start(def)) && now.isBefore(end(def)) &&
            (def.openDayMask == 0 || def.openDayMask and (1 shl (now.dayOfWeek.value - 1)) != 0)
    }

    suspend fun isOpen(characterId: Long, now: LocalDateTime): Boolean {
        val season = currentSeason(now) ?: return false
        val bound = inventory.battlePassProgress(characterId)
        if (bound != null && bound.seasonId != season) return false
        if (bound != null) {
            // A stale generic RoleCount autosave must never re-open already paid rewards.
            counts.setValue(characterId, FREE, maxOf(counts.getValue(characterId, FREE), bound.freeLevel), counts.getMaxValue(characterId, FREE))
            counts.setValue(characterId, PAID, maxOf(counts.getValue(characterId, PAID), bound.paidLevel), counts.getMaxValue(characterId, PAID))
        }
        return true
    }

    suspend fun claim(characterId: Long, now: LocalDateTime): Boolean =
        locks.computeIfAbsent(characterId) { Mutex() }.withLock {
            val season = currentSeason(now) ?: return@withLock false
            if (!isOpen(characterId, now)) return@withLock false
            val rows = awards.filter { it.seasonId == season }
            val level = levelForExp(rows, counts.getValue(characterId, EXP))
            val free = counts.getValue(characterId, FREE)
            val paid = counts.getValue(characterId, PAID)
            if (free !in 0..rows.size || paid !in 0..rows.size) return@withLock false
            val entitled = PAID_FLAG in flags.getSetFlagIds(characterId)
            val nextFree = maxOf(free, level)
            val nextPaid = if (entitled) maxOf(paid, level) else paid
            if (nextFree == free && nextPaid == paid) return@withLock false
            val rewards = rows.flatMap { row ->
                buildList {
                    if (row.level in (free + 1)..nextFree) add(row.rewards[0])
                    if (row.level in (paid + 1)..nextPaid) addAll(row.rewards.drop(1))
                }
            }
            val grouped = rewards.filter { it.itemId > 0 && it.quantity > 0 }.groupBy { it.itemId }.mapValues { (_, rs) -> rs.sumOf { it.quantity } }
            if (grouped.values.any { it !in 1..Int.MAX_VALUE.toLong() }) return@withLock false
            val before = inventory.getBag(characterId).toMap()
            val after = planRewards(before, counts.getBagCapacity(characterId), grouped, items)
                ?: return@withLock false
            val changes = after.filter { (slot, thing) -> before[slot] != thing }
            val progress = mapOf(
                FREE to (nextFree to counts.getMaxValue(characterId, FREE)),
                PAID to (nextPaid to counts.getMaxValue(characterId, PAID)),
            )
            try {
                inventory.commitBattlePassClaim(characterId, season, changes, mapOf(FREE to free, PAID to paid), progress)
            } catch (e: Exception) {
                battlePassLogger.error(e) { "BattlePass claim rolled back charId=$characterId season=$season" }
                return@withLock false
            }
            battlePassLogger.info { "BattlePass claim charId=$characterId season=$season level=$level free=$nextFree paid=$nextPaid" }
            true
        }

    companion object {
        const val KIND = 128
        const val EXP = 791
        const val FREE = 792
        const val PAID = 793
        const val PAID_FLAG = 6060
        fun start(d: ActivityTimeDef): LocalDateTime = LocalDateTime.of(d.startYear,d.startMonth,d.startDay,d.startHour,d.startMinute)
        fun end(d: ActivityTimeDef): LocalDateTime = LocalDateTime.of(d.endYear,d.endMonth,d.endDay,d.endHour,d.endMinute)
        fun levelForExp(rows: List<PassAwardDef>, exp: Int): Int =
            rows.lastOrNull { exp > 0 && exp >= it.totalExp }?.level ?: 0

        /** Plan the complete free+paid bundle before persisting anything. */
        fun planRewards(
            before: Map<Int, ThingData>, capacity: Int, rewards: Map<Int, Long>, items: Map<Int, ItemDef>,
        ): Map<Int, ThingData>? {
            val bag = before.toMutableMap()
            for ((id, quantity) in rewards) {
                val def = items[id] ?: return null
                val limit = if (def.fitType == 0) ItemConstants.MAX_STACK else 1
                var remaining = quantity
                if (limit > 1) for ((slot, thing) in bag.toSortedMap()) {
                    if (remaining == 0L) break
                    if (slot in 0 until capacity && thing == ThingData(id, thing.quantity) && thing.quantity in 1 until limit) {
                        val added = minOf(remaining, (limit - thing.quantity).toLong()).toInt()
                        bag[slot] = thing.copy(quantity = thing.quantity + added)
                        remaining -= added
                    }
                }
                while (remaining > 0) {
                    val slot = (0 until capacity).firstOrNull { it !in bag } ?: return null
                    val added = minOf(remaining, limit.toLong()).toInt()
                    bag[slot] = ThingData(id, added)
                    remaining -= added
                }
            }
            return bag
        }
    }
}
