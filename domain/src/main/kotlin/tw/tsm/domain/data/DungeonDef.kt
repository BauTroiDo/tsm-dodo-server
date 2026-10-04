package tw.tsm.domain.data

/** DVT Data/DungeonData.lua + IOS_Dungeon_C.dat. */
data class DungeonNeedDef(
    val kind: Int,
    val parameter: Int,
)

/** DVT Data/DungeonCriteriaData.lua + IOS_DungeonCriteria_C.dat. */
data class DungeonCriteriaDef(
    val id: Int,
    val playerOrNpc: Int,
    val kind: Int,
    val compare: Int,
    val value: Long,
    val value2: Long,
)

data class DungeonDef(
    val id: Int,
    val nameId: Long,
    val kind: Int,
    val minLv: Int,
    val maxLv: Int,
    val maxPlayer: Int,
    val missionFlag: Int,
    val dailyFlag: Int,
    val dailyCount: Int,
    val skipFlag: Int,
    val timeMinutes: Int,
    val sceneId: Int,
    val costPoint: Int,
    val lootTable: Int,
    val extraLoot: Int,
    val infoTextId: Long,
    val needPar: List<DungeonNeedDef>,
    val allPassPrize: Int,
)
