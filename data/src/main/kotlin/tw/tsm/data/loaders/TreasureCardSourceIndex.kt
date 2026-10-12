package tw.tsm.data.loaders

import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.repositories.TreasureState
import tw.tsm.domain.valueobjects.ThingData

/**
 * Original DVT Treasure card item identity, read-only.
 *
 * lua/Logic/Treasure.lua:Initialize maps Item_C kind=98 and skillLink to
 * each Treasure ID. The normal Item.GetItemCount reads BAG stacks; the
 * separate S123-001 snapshot supplies each Treasure's persistent "count".
 * They are NOT interchangeable. A card still in the bag MUST NOT be credited
 * to TreasureState.cardCopiesTotal unless an independently verified,
 * transactional acquisition route transfers it.
 */
class TreasureCardSourceIndex private constructor(
    private val byTreasureId: Map<Int, Int>,
    private val byItemId: Map<Int, Int>,
) {
    fun itemIdFor(treasureId: Int): Int? = byTreasureId[treasureId]

    fun treasureIdFor(itemId: Int): Int? = byItemId[itemId]

    /** Source BAG-only count for audit/display. NEVER add to banked Treasure count. */
    fun unbankedBagCopies(bag: Map<Int, ThingData>, treasureId: Int): Long {
        val itemId = byTreasureId[treasureId] ?: return 0L
        return bag.values.asSequence()
            .filter { it.itemId == itemId && it.quantity > 0 }
            .sumOf { it.quantity.toLong() }
    }

    /**
     * The exact quantity spendable by the separate Treasure progression.
     * Intentionally IGNORES bag contents: granting a card into a bag is not
     * proof of an S123-001 banking transaction.
     */
    fun bankedSpendableCopies(
        state: TreasureState?,
        treasureId: Int,
        source: TreasureDatLoader.Catalog,
    ): Int {
        if (treasureId !in byTreasureId || state == null) return 0
        require(state.treasureId == treasureId) { "Mismatched Treasure ownership" }
        val invested = source.investedCopies(treasureId, state.level)
        require(state.cardCopiesTotal >= invested) {
            "Corrupt Treasure ownership: invested cards exceed banked copies"
        }
        return state.cardCopiesTotal - invested
    }

    companion object {
        const val SOURCE_TREASURE_ITEM_KIND = 98

        fun fromSource(
            itemDefs: Map<Int, ItemDef>,
            source: TreasureDatLoader.Catalog,
        ): TreasureCardSourceIndex {
            val ids = source.levels.map { it.id }.toSet()
            require(ids == (1..114).toSet()) { "Incomplete DVT Treasure source" }
            val cards = itemDefs.asSequence()
                .filter { it.value.kind == SOURCE_TREASURE_ITEM_KIND }
                .toList()
            require(cards.size == ids.size) { "Missing/extra DVT Treasure card item" }
            val forward = mutableMapOf<Int, Int>()
            val reverse = mutableMapOf<Int, Int>()
            for ((itemId, def) in cards) {
                require(def.skillLink in ids) { "Unknown DVT Treasure skillLink" }
                require(forward.putIfAbsent(def.skillLink, itemId) == null) {
                    "Duplicate DVT Treasure skillLink"
                }
                require(reverse.putIfAbsent(itemId, def.skillLink) == null) {
                    "Duplicate DVT Treasure item ID"
                }
            }
            require(forward.keys == ids) { "Incomplete DVT Treasure card catalog" }
            return TreasureCardSourceIndex(forward.toMap(), reverse.toMap())
        }
    }
}
