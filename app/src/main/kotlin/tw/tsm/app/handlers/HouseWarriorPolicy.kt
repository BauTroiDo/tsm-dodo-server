package tw.tsm.app.handlers

import tw.tsm.domain.data.HouseFurnitureDef
import tw.tsm.domain.interfaces.repositories.HouseFurniturePlacement

object HouseWarriorPolicy {
    fun floorScore(placed: List<HouseFurniturePlacement>, defs: Map<Int, HouseFurnitureDef>): Int =
        placed.sumOf { defs[it.furnitureId]?.decorateScore ?: 0 }.coerceIn(0, 65535)

    fun slots(score: Int, thresholds: Map<Int, Int>): Int =
        (1..4).filter { score >= (thresholds[it] ?: Int.MAX_VALUE) }.maxOrNull() ?: 0

    fun valid(ids: List<Int>, maxSlots: Int, eligible: Set<Int>): Boolean {
        if (ids.size != 4 || maxSlots !in 0..4) return false
        if (ids.drop(maxSlots).any { it != 0 }) return false
        val selected = ids.filter { it != 0 }
        return selected.size == selected.distinct().size && selected.all { it in eligible }
    }

    fun visible(ids: List<Int>, maxSlots: Int, eligible: Set<Int>): List<Int> {
        if (ids.size != 4) return List(4) { 0 }
        val seen = HashSet<Int>()
        return ids.mapIndexed { index, id ->
            if (index >= maxSlots || id !in eligible || !seen.add(id)) 0 else id
        }
    }
}
