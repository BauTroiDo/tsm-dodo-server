package tw.tsm.gamelogic.systems

import tw.tsm.domain.valueobjects.ThingData

/**
 * Source-only immutable view of a DVT C025-002 item offer. InventorySystem
 * owns mutable BAG maps and ThingData.myth is a List which could be mutable;
 * freeze both values and their original slot mapping when the upload arrives.
 *
 * The live C025 RAM-only exchange may use this for integrity checks; it
 * deliberately does not start the staged SQL receipt settlement.
 */
object TradeItemOfferSnapshot {
    fun freeze(item: ThingData): ThingData =
        item.copy(myth = item.myth.map { it.copy() })

    /**
     * A malformed BAG slot, repeated slot, absent stack or empty item cannot
     * be represented as an accepted offer. Existing live trade validation
     * separately enforces item restrictions, money and user permissions.
     */
    fun capture(
        slots: List<Int>,
        readSlot: (Int) -> ThingData?,
    ): Map<Int, ThingData>? {
        if (slots.size != slots.toSet().size || slots.any { it !in 0..255 }) return null
        val stored = LinkedHashMap<Int, ThingData>()
        for (slot in slots) {
            val item = readSlot(slot) ?: return null
            if (item.isEmpty || item.quantity <= 0) return null
            stored[slot] = freeze(item)
        }
        return stored.toMap()
    }

    /** Distinguish a switched BAG stack/quantity/affix/NFT attribute at confirm. */
    fun matches(
        slots: List<Int>,
        offered: Map<Int, ThingData>,
        readSlot: (Int) -> ThingData?,
    ): Boolean {
        if (slots.size != slots.toSet().size ||
            slots.any { it !in 0..255 } ||
            offered.keys != slots.toSet()
        ) return false
        return slots.all { slot ->
            val actual = readSlot(slot)
            actual != null && !actual.isEmpty && actual == offered[slot]
        }
    }

    /** Preserve C025-003 original BAG selection order when showing partner. */
    fun orderedItems(slots: List<Int>, offered: Map<Int, ThingData>): List<ThingData>? {
        if (offered.keys != slots.toSet() || slots.size != slots.toSet().size) return null
        return slots.map { slot -> offered[slot] ?: return null }
    }
}
