package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.data.EveFightEnemy

/**
 * DVT Eve_FightData enemy placement.
 *
 * LocationPos is 1-based within one battle side:
 *   1..5  -> front column rows 0..4
 *   6..10 -> back column rows 0..4
 *
 * Repeated LocationPos records are sequential reinforcements for that slot.
 * A plan is returned only when every record has an authoritative 1..10 slot.
 * Callers must retain their legacy path for data that uses LocationPos=0.
 */
object DvtEveBattlePlacement {
    data class Slot(val col: Int, val row: Int)

    data class PlacedEnemy(
        val enemy: EveFightEnemy,
        val slot: Slot,
    )

    data class Plan(
        val initial: List<PlacedEnemy>,
        val pending: Map<Slot, List<EveFightEnemy>>,
    ) {
        val pendingCount: Int get() = pending.values.sumOf { it.size }
    }

    fun slotFor(locationPos: Int, frontCol: Int, backCol: Int): Slot? =
        when (locationPos) {
            in 1..BattleConstants.MAX_ROW ->
                Slot(frontCol, locationPos - 1)
            in (BattleConstants.MAX_ROW + 1)..(BattleConstants.MAX_ROW * 2) ->
                Slot(backCol, locationPos - BattleConstants.MAX_ROW - 1)
            else -> null
        }

    fun plan(
        enemies: List<EveFightEnemy>,
        frontCol: Int,
        backCol: Int,
    ): Plan? {
        if (enemies.isEmpty()) return Plan(emptyList(), emptyMap())

        val grouped = LinkedHashMap<Slot, MutableList<EveFightEnemy>>()
        for (enemy in enemies) {
            val slot = slotFor(enemy.locationPos, frontCol, backCol) ?: return null
            grouped.getOrPut(slot) { mutableListOf() }.add(enemy)
        }

        val initial = ArrayList<PlacedEnemy>(grouped.size)
        val pending = LinkedHashMap<Slot, List<EveFightEnemy>>()
        for ((slot, queue) in grouped) {
            initial += PlacedEnemy(queue.first(), slot)
            if (queue.size > 1) {
                pending[slot] = queue.drop(1)
            }
        }
        return Plan(initial, pending)
    }
}
