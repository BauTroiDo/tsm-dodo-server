package tw.tsm.domain.data

/**
 * DVT Data/DungeonCriteriaData.lua + IOS_DungeonCriteria_C.dat.
 *
 * playerOrNpc:
 * 1 player, 2 deployed NPC, 3 follow NPCs, 4 player+deployed NPC, 5 player+follow NPCs.
 *
 * compare:
 * 1 ==, 2 <, 3 <=, 4 >, 5 >=, 6 !=.
 */
data class DungeonCriteriaDef(
    val id: Int,
    val playerOrNpc: Int,
    val kind: Int,
    val compare: Int,
    val value: Long,
    val value2: Long,
)
