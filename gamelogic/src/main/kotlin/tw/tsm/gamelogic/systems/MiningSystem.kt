package tw.tsm.gamelogic.systems

import java.time.Instant
import java.time.ZoneId
import tw.tsm.domain.util.OleDate

import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.domain.interfaces.repositories.MiningRepository
import tw.tsm.domain.interfaces.repositories.CustomMiningRules

/**
 * DVT compares S170-001 setTime against CGTimer.serverTime, which comes from
 * LoginService.currentServerTime() = OleDate.nowLocal(). Persist durations in
 * UTC epoch millis, but encode protocol dates in the SERVER'S local wall clock.
 * A UTC OADate makes a Vietnam client immediately consume seven hours of fuel.
 */
object MiningClock {
    fun toDvtWallClockOaDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Double =
        OleDate.fromLocalDateTime(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime())
}

/** View đã settle dùng để encode S:170-001/002. */
data class MiningView(
    val record: MiningRecord,
    val remainingOre: Long,
    val tsBalanceUpdate: Int? = null,
) {
    val isMining: Boolean
        get() = !record.dayLimit && remainingOre > 0 && record.machineFull.any { it > 0 }

    val setTimeOaDate: Double
        get() = MiningClock.toDvtWallClockOaDate(record.updatedAtMillis)
}

data class MiningRefillResult(
    val success: Boolean,
    val wireIndex: Int,
    val addedFull: Int,
)

class MiningSystem(
    private val repository: MiningRepository,
    private val inventorySystem: InventorySystem,
    private val itemDefs: Map<Int, ItemDef>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val currencySystem: PlayerCurrencySystem? = null,
    private val customRules: CustomMiningRules = CustomMiningRules(),
) {
    suspend fun getView(characterId: Long): MiningView =
        CharacterPersistenceGuard.withLock(characterId) {
            val now = nowMillis()
            val awarded = if (customRules.enabled) {
                currencySystem?.applyVerifiedMiningTsAward(characterId) { oldTs ->
                    repository.settleCustomMining(characterId, now, oldTs, customRules)
                }
            } else null
            val persisted = repository.load(characterId)
            val original = persisted ?: MiningRecord(updatedAtMillis = now)
            val settled = if (customRules.enabled) original else settle(original, now)
            if (!customRules.enabled && (settled != original || persisted == null)) {
                repository.save(characterId, settled)
            }
            val remaining = repository.loadRemainingOre(settled.mineType)
                ?.coerceIn(0L, MAX_MINE) ?: MAX_MINE
            MiningView(settled, remaining, awarded?.takeIf { it.newlyApplied }?.tsBalance)
        }

    suspend fun refill(
        characterId: Long,
        bagIndex: Int,
        itemId: Int,
        quantity: Int,
        wireIndex: Int,
    ): MiningRefillResult {
        if (wireIndex !in 0..3 || quantity <= 0) return fail(wireIndex)
        val machineIndex = 3 - wireIndex
        // Settle old time under the currency writer fence BEFORE adding fuel.
        // Never burn old fuel without paying in custom economy mode.
        if (customRules.enabled) getView(characterId)
        val expectedItemId = FUEL_ITEM_IDS[machineIndex]
        if (itemId != expectedItemId) return fail(wireIndex)

        val perItem = itemDefs[itemId]?.threshold ?: return fail(wireIndex)
        if (perItem <= 0) return fail(wireIndex)

        var added = 0
        val result = inventorySystem.consumeMiningFuelAtomic(
            characterId, bagIndex, itemId, quantity,
        ) { bagBefore ->
            val now = nowMillis()
            val persisted = repository.load(characterId)
            val original = persisted ?: MiningRecord(updatedAtMillis = now)
            val settled = if (customRules.enabled) original else settle(original, now)
            val current = settled.machineFull[machineIndex]
            val room = FULL_MAX - current
            val requested = quantity.toLong() * perItem.toLong()
            // Never consume all requested items for a partially fitting refill.
            if (room <= 0 || requested <= 0 || requested > room) return@consumeMiningFuelAtomic null

            val full = settled.machineFull.toMutableList()
            full[machineIndex] = current + requested.toInt()
            val next = settled.copy(
                machineFull = full,
                updatedAtMillis = if (settled.machineFull.none { it > 0 }) now else settled.updatedAtMillis,
            )
            val receipt = repository.commitFuelRefillAtomic(
                characterId, bagIndex, bagBefore, quantity, persisted, next,
            )
            if (receipt != null) added = requested.toInt()
            receipt
        } ?: return fail(wireIndex)

        return MiningRefillResult(true, wireIndex, added)
    }

    internal fun settle(record: MiningRecord, now: Long): MiningRecord {
        val start = record.updatedAtMillis.takeIf { it > 0L } ?: now
        if (record.dayLimit || record.machineFull.none { it > 0 }) {
            return record.copy(updatedAtMillis = now)
        }
        if (now <= start) return record.copy(updatedAtMillis = start)

        var ticks = (now - start) / TICK_MS
        if (ticks <= 0) return record.copy(updatedAtMillis = start)

        val full = record.machineFull.toMutableList()
        for (i in full.indices) {
            if (ticks <= 0) break
            val take = minOf(full[i].toLong(), ticks).toInt()
            full[i] -= take
            ticks -= take.toLong()
        }

        val totalBefore = record.machineFull.sumOf { it.toLong() }
        val totalAfter = full.sumOf { it.toLong() }
        val consumed = totalBefore - totalAfter
        val nextTime = if (totalAfter == 0L) now else start + consumed * TICK_MS
        return record.copy(machineFull = full, updatedAtMillis = nextTime)
    }

    private fun fail(wireIndex: Int) = MiningRefillResult(false, wireIndex, 0)

    companion object {
        const val MINE_SCENE_ID = 49996
        const val MAX_MINE = 600_000_000L
        const val FULL_MAX = 65_535
        const val TICK_MS = 360_000L

        val FUEL_ITEM_IDS = listOf(55431, 55432, 55433, 55434)
        val MACHINE_SKIN_IDS = listOf(22120, 22121, 22122, 22123)
    }
}
