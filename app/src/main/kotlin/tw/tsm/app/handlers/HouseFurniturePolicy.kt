package tw.tsm.app.handlers

import tw.tsm.domain.interfaces.repositories.HouseFurniturePlacement

/**
 * Strict, cosmetic-only placement policy. Validates against server-sourced
 * EItemKind.Furniture=89 + ItemDef.spare3 (furniture ID), never client counts.
 */
object HouseFurniturePolicy {
    const val MAX_PLACEMENTS = 90

    fun visible(
        saved: List<HouseFurniturePlacement>,
        owned: Map<Int, Int>,
    ): List<HouseFurniturePlacement> {
        val counts = mutableMapOf<Int, Int>()
        val result = mutableListOf<HouseFurniturePlacement>()
        val occupied = mutableSetOf<Pair<Int, Triple<Int, Int, Int>>>()
        for (f in saved) {
            if (result.size >= MAX_PLACEMENTS || !validCoordinate(f)) continue
            if (!occupied.add(f.furnitureId to Triple(f.x, f.y, f.z))) continue
            if (counts.getOrDefault(f.furnitureId, 0) >= owned.getOrDefault(f.furnitureId, 0)) continue
            counts[f.furnitureId] = counts.getOrDefault(f.furnitureId, 0) + 1
            result += f
        }
        return result
    }

    fun validate(
        proposed: List<HouseFurniturePlacement>,
        owned: Map<Int, Int>,
    ): Boolean {
        if (proposed.size > MAX_PLACEMENTS) return false
        if (proposed.any { !validCoordinate(it) }) return false
        if (proposed.map { it.furnitureId to Triple(it.x, it.y, it.z) }.toSet().size != proposed.size) return false
        return proposed.groupingBy { it.furnitureId }.eachCount().all { (id, count) ->
            id in 1..65535 && count <= owned.getOrDefault(id, 0)
        }
    }

    private fun validCoordinate(f: HouseFurniturePlacement) =
        f.furnitureId in 1..65535 &&
            f.x in 0..10 && f.y in 0..10 && f.z in 0..4 && f.direction in 0..3
}
