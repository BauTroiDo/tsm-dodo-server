package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.HouseExpandConditionDef

/** DVT next-level-only home unlock. Never trust a level or cost from C106-004 (empty packet). */
object HouseExpansionPolicy {
    const val FIRST_LEVEL = 1
    const val LAST_LEVEL = 12

    fun unlocked(level: Int, building: Int, floor: Int): Boolean {
        if (level !in 1..LAST_LEVEL || building !in 1..4 || floor !in 1..2) return false
        val firstFloorLevel = (building - 1) * 3 + 1
        val targetLevel = if (floor == 1) firstFloorLevel else firstFloorLevel + 2
        return level >= targetLevel
    }

    fun next(level: Int, table: Map<Int, HouseExpandConditionDef>): HouseExpandConditionDef? {
        if (level !in 1 until LAST_LEVEL || table.size != LAST_LEVEL) return null
        val target = table[level + 1] ?: return null
        if (target.level != level + 1 || target.goldCost <= 0) return null
        return target
    }

    /** DVT Lua compares FurnitureKindCount, not total placement count nor décor score. */
    fun eligible(level: Int, ownedKinds: Int, gold: Int,
                 table: Map<Int, HouseExpandConditionDef>): Boolean {
        val next = next(level, table) ?: return false
        return ownedKinds >= next.furnitureKindsRequired && gold >= next.goldCost
    }

    fun currentBuilding(level: Int): Int = ((level - 1) / 3) + 1

    fun mapForBuilding(building: Int): Int? =
        if (building in 1..4) 54963 + building else null
}
