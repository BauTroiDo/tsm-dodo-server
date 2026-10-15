package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.ThingData

/** Pure source-backed BAG exchange planner for DVT EAward.Change (C087-002 kind9). */
enum class WelfareExchangeFailure { MISSING_ITEMS, BAG_FULL, OTHER }

object WelfareItemExchangePlanner {
    /** Count only unlocked source stacks, with overflow-safe multiplied costs. */
    fun hasRequiredUnlockedItems(
        bag: Map<Int, ThingData>, costs: List<Pair<Int, Int>>, count: Int,
    ): Boolean {
        if (count !in 1..50 || costs.isEmpty()) return false
        val totals = linkedMapOf<Int, Long>()
        for ((id, qty) in costs) {
            if (id <= 0 || qty <= 0) return false
            val next = (totals[id] ?: 0L) + qty.toLong() * count
            if (next !in 1L..Int.MAX_VALUE.toLong()) return false
            totals[id] = next
        }
        return totals.all { (id, required) ->
            bag.values.asSequence()
                .filter { it.itemId == id && !it.isLocked && it.quantity > 0 }
                .sumOf { it.quantity.toLong() } >= required
        }
    }

    fun plan(
        bag: Map<Int, ThingData>, capacity: Int,
        costs: List<Pair<Int, Int>>, output: Pair<Int, Int>,
        count: Int, itemDefs: Map<Int, ItemDef>,
    ): Map<Int, ThingData?>? {
        if (capacity !in 1..10000 || count !in 1..50 || costs.isEmpty() ||
            output.first <= 0 || output.second <= 0) return null
        val aggregated = linkedMapOf<Int, Long>()
        for ((id, qty) in costs) {
            if (id <= 0 || qty <= 0 || !itemDefs.containsKey(id)) return null
            val sum = (aggregated[id] ?: 0L) + qty.toLong() * count
            if (sum <= 0 || sum > Int.MAX_VALUE) return null
            aggregated[id] = sum
        }
        val outputId = output.first
        val totalOutput = output.second.toLong() * count
        if (totalOutput <= 0 || totalOutput > Int.MAX_VALUE || outputId !in itemDefs) return null
        if (bag.keys.any { it !in 0 until capacity } ||
            bag.values.any { it.quantity <= 0 }) return null
        val next = bag.toMutableMap()
        for ((id, required) in aggregated) {
            var amount = required.toInt()
            for ((slot, thing) in next.toSortedMap()) {
                if (amount == 0) break
                if (thing.itemId != id || thing.isLocked) continue
                val consumed = minOf(amount, thing.quantity)
                if (consumed == thing.quantity) next.remove(slot)
                else next[slot] = thing.copy(quantity = thing.quantity - consumed)
                amount -= consumed
            }
            if (amount != 0) return null
        }
        val def = itemDefs[outputId] ?: return null
        val stackLimit = if (def.fitType == 0) ItemConstants.MAX_STACK else 1
        var remaining = totalOutput.toInt()
        if (stackLimit > 1) {
            for ((slot, thing) in next.toSortedMap()) {
                if (remaining == 0) break
                if (thing.itemId != outputId || thing.isLocked || thing.quantity >= stackLimit) continue
                val add = minOf(remaining, stackLimit - thing.quantity)
                next[slot] = thing.copy(quantity = thing.quantity + add)
                remaining -= add
            }
        }
        while (remaining > 0) {
            val empty = (0 until capacity).firstOrNull { it !in next } ?: return null
            val amount = minOf(remaining, stackLimit)
            next[empty] = ThingData(itemId = outputId, quantity = amount)
            remaining -= amount
        }
        return (bag.keys union next.keys).filter { bag[it] != next[it] }
            .sorted().associateWith { next[it] }
    }
}
