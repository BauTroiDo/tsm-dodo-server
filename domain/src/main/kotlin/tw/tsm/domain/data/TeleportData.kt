package tw.tsm.domain.data

/** DVT Data/LimitTimeDungeonData.lua + IOS_LimitTimeDungeon_C.dat. */
data class LimitTimeDungeonDef(
    val index: Int,
    val messageId: Long,
    val sceneId: Int,
    val limitIndex: Int,
    val limitTimeMinutes: Int,
    val limitLevel: Int,
    val missionId: Int,
)

/** DVT Data/SceneFightData.lua + IOS_SceneFight_C.dat. */
data class SceneFightDef(
    val group: Int,
    val groupName: Long,
    val sceneId: Int,
    val x: Int,
    val y: Int,
    val npcLow: Int,
    val npcHigh: Int,
    val element: Int,
    val infoStringId: Long,
    val mark: Int,
    val open: Int,
    val areaId: Int,
    val info: Int,
)
