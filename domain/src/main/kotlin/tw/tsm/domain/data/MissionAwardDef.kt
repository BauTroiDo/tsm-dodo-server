package tw.tsm.domain.data

/** Lua MissionAwardData.New / IOS_MissionAward_C.dat. */
data class MissionAwardDef(
    val id: Int,
    val contentId: Long,
    val conditionKind: Int,
    val conditionValue: Int,
    val operator: Int,
    val targetValue: Int,
    val itemId: Int,
    val itemCount: Int,
    val getFlag: Int,
    val activityId: Int,
)
