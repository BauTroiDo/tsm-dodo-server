package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.domain.interfaces.repositories.MiningRepository

/** View đã settle dùng để encode S:170-001/002. */
data class MiningView(
    val record: MiningRecord,
    val remainingOre: Long,
) {
    val isMining: Boolean
        get() = !record.dayLimit && record.machineFull.any { it > 0 }

    val setTimeOaDate: Double
        get() = record.updatedAtMillis / 86_400_000.0 + 25_569.0
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
) {
    suspend fun getView(characterId: Long): MiningView {
        val now = nowMillis()
        val original = repository.load(characterId)
            ?: MiningRecord(updatedAtMillis = now)
        val settled = settle(original, now)
        if (settled != original || repository.load(characterId) == null) {
            repository.save(characterId, settled)
        }
        return MiningView(settled, MAX_MINE)
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
        val expectedItemId = FUEL_ITEM_IDS[machineIndex]
        if (itemId != expectedItemId) return fail(wireIndex)

        val bagItem = inventorySystem.getBagItem(characterId, bagIndex) ?: return fail(wireIndex)
        if (bagItem.itemId != itemId || bagItem.quantity < quantity) return fail(wireIndex)
        val perItem = itemDefs[itemId]?.threshold ?: return fail(wireIndex)
        if (perItem <= 0) return fail(wireIndex)

        val now = nowMillis()
        val original = repository.load(characterId)
            ?: MiningRecord(updatedAtMillis = now)
        val settled = settle(original, now)
        val current = settled.machineFull[machineIndex]
        val room = FULL_MAX - current
        if (room <= 0) return fail(wireIndex)

        val requested = quantity.toLong() * perItem.toLong()
        val added = minOf(room.toLong(), requested).toInt()
        if (added <= 0) return fail(wireIndex)

        if (!inventorySystem.removeBagItemsExact(characterId, listOf(bagIndex to quantity))) {
            return fail(wireIndex)
        }

        val full = settled.machineFull.toMutableList()
        full[machineIndex] = current + added
        val next = settled.copy(
            machineFull = full,
            updatedAtMillis = if (settled.machineFull.none { it > 0 }) now else settled.updatedAtMillis,
        )
        repository.save(characterId, next)
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
