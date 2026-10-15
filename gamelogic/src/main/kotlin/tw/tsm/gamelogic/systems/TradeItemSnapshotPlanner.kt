package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.repositories.TradeAccountSnapshot
import tw.tsm.domain.valueobjects.ThingData

/**
 * STAGED ONLY: pure C025 BAG + GOLD transfer planner. Not wired to TradeSystem.
 *
 * All supplied BAG/gold values must be captured under BOTH per-character
 * persistence locks. The expected selected items must be frozen at C025-002
 * upload, so a changed stack/affix/NFT item cannot be silently substituted
 * after the counterparty saw its offered contents in S025-003.
 *
 * Produces full optimistic SQL before/after snapshots, never touches RAM/DB,
 * and never sends packets. Do not make live until TradeItemFinalizationGate
 * and TradeCacheSettlementCoordinator are safely integrated.
 */
class TradeItemSnapshotPlanner(private val itemDefs: Map<Int, ItemDef>) {
    data class Side(
        val characterId: Long,
        val beforeBag: Map<Int, ThingData>,
        val expectedOfferedItems: Map<Int, ThingData>,
        val offeredSlots: List<Int>,
        val beforeGold: Int,
        val offeredGold: Int,
        val auctionGold: Int,
        val bagCapacity: Int,
    )

    enum class Failure {
        INVALID_PARTICIPANT,
        INVALID_BAG_OR_MONEY,
        INVALID_OFFER,
        STALE_OFFER,
        ITEM_NOT_TRADEABLE,
        INSUFFICIENT_GOLD,
        GOLD_OVERFLOW,
        BAG_FULL,
        CONSERVATION_FAILURE,
    }

    sealed interface Result {
        data class Accepted(
            val first: TradeAccountSnapshot,
            val second: TradeAccountSnapshot,
        ) : Result
        data class Rejected(val reason: Failure, val characterId: Long? = null) : Result
    }

    private fun frozen(thing: ThingData): ThingData =
        thing.copy(myth = thing.myth.map { it.copy() })

    private fun bagSnapshot(input: Map<Int, ThingData>): Map<Int, ThingData> =
        input.mapValues { (_, thing) -> frozen(thing) }

    private fun fullBagIsValid(side: Side): Boolean {
        if (side.bagCapacity !in 1..ItemConstants.MAX_BAG_SLOTS ||
            side.beforeGold !in 0..ItemConstants.MAX_GOLD ||
            side.auctionGold < 0 || side.offeredGold < 0
        ) return false
        return side.beforeBag.all { (slot, thing) ->
            slot in 0 until side.bagCapacity &&
                thing.itemId in 1..65535 &&
                thing.quantity in 1..ItemConstants.MAX_STACK &&
                thing.myth.size == 4
        }
    }

    private fun slotsAreValid(side: Side): Boolean =
        side.offeredSlots.size == side.offeredSlots.toSet().size &&
            side.offeredSlots.all { it in 0..255 && it < side.bagCapacity } &&
            side.expectedOfferedItems.keys == side.offeredSlots.toSet()

    /**
     * Tradeability needs server source parity. Fail closed for currently
     * unresolved same-guild restrictions and "trade once" exception semantics.
     * ItemDef.restrict & 2 is a transfer ban; specialKind & 1 in the original
     * UI permits one transfer, but its consumption semantics are not decoded.
     */
    private fun canTransfer(item: ThingData): Boolean {
        val def = itemDefs[item.itemId] ?: return false
        if (item.isLocked) return false
        if (def.kind == 53 || (def.restrict2 and 1) != 0) return false
        if ((def.restrict and 2) != 0) return false
        if (def.fitType != 0 && item.quantity != 1) return false
        return true
    }

    private fun identities(bagA: Map<Int, ThingData>, bagB: Map<Int, ThingData>): Map<ThingData, Long> =
        (bagA.values + bagB.values)
            .groupingBy { it.copy(quantity = 1) }
            .fold(0L) { sum, thing -> sum + thing.quantity.toLong() }

    fun plan(first: Side, second: Side): Result {
        if (first.characterId <= 0 || second.characterId <= 0 ||
            first.characterId == second.characterId
        ) return Result.Rejected(Failure.INVALID_PARTICIPANT)
        for (side in listOf(first, second)) {
            if (!fullBagIsValid(side)) {
                return Result.Rejected(Failure.INVALID_BAG_OR_MONEY, side.characterId)
            }
            if (!slotsAreValid(side)) {
                return Result.Rejected(Failure.INVALID_OFFER, side.characterId)
            }
            if (side.offeredGold > side.beforeGold) {
                return Result.Rejected(Failure.INSUFFICIENT_GOLD, side.characterId)
            }
            if (side.offeredSlots.any { slot ->
                    side.beforeBag[slot] != side.expectedOfferedItems[slot]
                }
            ) return Result.Rejected(Failure.STALE_OFFER, side.characterId)
            if (side.expectedOfferedItems.values.any { !canTransfer(it) }) {
                return Result.Rejected(Failure.ITEM_NOT_TRADEABLE, side.characterId)
            }
        }

        // Subtract and add on Long to avoid Int overflow or silent rounding.
        val goldA = first.beforeGold.toLong() - first.offeredGold + second.offeredGold
        val goldB = second.beforeGold.toLong() - second.offeredGold + first.offeredGold
        if (goldA !in 0..ItemConstants.MAX_GOLD.toLong() ||
            goldB !in 0..ItemConstants.MAX_GOLD.toLong()
        ) return Result.Rejected(Failure.GOLD_OVERFLOW)

        val beforeA = bagSnapshot(first.beforeBag)
        val beforeB = bagSnapshot(second.beforeBag)
        val afterA = beforeA.toMutableMap()
        val afterB = beforeB.toMutableMap()

        // Remove BOTH outgoing offers first; otherwise a full-bag swap would
        // be incorrectly refused even though the outgoing slots become free.
        val outgoingA = first.offeredSlots.map { beforeA.getValue(it) }
        val outgoingB = second.offeredSlots.map { beforeB.getValue(it) }
        first.offeredSlots.forEach { afterA.remove(it) }
        second.offeredSlots.forEach { afterB.remove(it) }

        // Original direct addItemDirectSync creates a new slot, never merges
        // stacks. Preserve the exact ThingData metadata and quantity.
        fun place(
            after: MutableMap<Int, ThingData>,
            bagCapacity: Int,
            incoming: List<ThingData>,
        ): Boolean {
            for (item in incoming) {
                val freeSlot = (0 until bagCapacity).firstOrNull { it !in after }
                    ?: return false
                after[freeSlot] = frozen(item)
            }
            return true
        }

        if (!place(afterA, first.bagCapacity, outgoingB)) {
            return Result.Rejected(Failure.BAG_FULL, first.characterId)
        }
        if (!place(afterB, second.bagCapacity, outgoingA)) {
            return Result.Rejected(Failure.BAG_FULL, second.characterId)
        }
        if (identities(beforeA, beforeB) != identities(afterA, afterB) ||
            first.beforeGold.toLong() + second.beforeGold.toLong() != goldA + goldB
        ) return Result.Rejected(Failure.CONSERVATION_FAILURE)

        return Result.Accepted(
            first = TradeAccountSnapshot(
                first.characterId, beforeA, afterA.toMap(),
                first.beforeGold, goldA.toInt(), first.auctionGold,
            ),
            second = TradeAccountSnapshot(
                second.characterId, beforeB, afterB.toMap(),
                second.beforeGold, goldB.toInt(), second.auctionGold,
            ),
        )
    }
}
