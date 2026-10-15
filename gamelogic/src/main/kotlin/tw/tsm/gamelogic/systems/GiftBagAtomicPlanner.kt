package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.ThingData

/**
 * Plan ONE atomic inventory transaction for opening a DVT BlissBag.
 *
 * Never consume the source bag before knowing that *all* rolled prizes fit.
 * The source slot becomes available only if its full stack was consumed.
 * Return null to reject without mutating the input bag; a successful empty
 * change set is valid for an exact source-item replacement.
 */
object GiftBagAtomicPlanner {
    fun plan(
        currentBag: Map<Int, ThingData>,
        capacity: Int,
        sourceSlot: Int,
        sourceItemId: Int,
        consumedQty: Int,
        rewards: List<Pair<Int, Int>>,
        itemDefs: Map<Int, ItemDef>,
    ): Map<Int, ThingData?>? {
        if (capacity <= 0 || sourceSlot !in 0 until capacity ||
            sourceItemId <= 0 || consumedQty <= 0 || rewards.isEmpty()
        ) return null
        val source = currentBag[sourceSlot] ?: return null
        if (source.itemId != sourceItemId || source.quantity < consumedQty || source.isLocked) return null

        val quantities = linkedMapOf<Int, Long>()
        for ((itemId, qty) in rewards) {
            if (itemId <= 0 || qty <= 0 || itemId !in itemDefs) return null
            val total = (quantities[itemId] ?: 0L) + qty.toLong()
            if (total > Int.MAX_VALUE) return null
            quantities[itemId] = total
        }

        val after = currentBag.toMutableMap()
        if (source.quantity == consumedQty) after.remove(sourceSlot)
        else after[sourceSlot] = source.copy(quantity = source.quantity - consumedQty)

        for ((itemId, count) in quantities) {
            val def = itemDefs[itemId] ?: return null
            val limit = if (def.fitType == 0) ItemConstants.MAX_STACK else 1
            var remaining = count.toInt()
            if (limit > 1) {
                for ((slot, current) in after.toSortedMap()) {
                    if (remaining == 0) break
                    if (current.itemId != itemId || current.quantity >= limit) continue
                    val add = minOf(remaining, limit - current.quantity)
                    after[slot] = current.copy(quantity = current.quantity + add)
                    remaining -= add
                }
            }
            while (remaining > 0) {
                val slot = (0 until capacity).firstOrNull { it !in after } ?: return null
                val amount = minOf(remaining, limit)
                after[slot] = ThingData(itemId = itemId, quantity = amount)
                remaining -= amount
            }
        }

        return (currentBag.keys union after.keys)
            .filter { currentBag[it] != after[it] }
            .sorted()
            .associateWith { after[it] }
    }
}
