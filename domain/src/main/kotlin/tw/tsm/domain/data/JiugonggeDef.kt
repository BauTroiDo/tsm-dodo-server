package tw.tsm.domain.data

/** Định nghĩa tĩnh lưới 9 ô mà client DVT đọc.*/
data class JiugonggeAwardDef(
    val itemId: Int,
    val quantity: Long,
    val getFlag: Int,
)

data class JiugonggeInfoDef(
    val id: Int,
    val activityId: Int,
    val kind: Int,
    val kindNameStringId: Long,
    val reset: Int,
    val awards: List<JiugonggeAwardDef>,
)

data class JiugonggeMissionDef(
    val id: Int,
    /** Số bàn cờ từ IOS_JiugonggeMission_C.dat.*/
    val boardNumber: Long,
    val kind: Int,
    val kindValue1: Int,
    val descriptionStringId: Long,
    val conditionKind: Int,
    val conditionKindValue: Long,
    val conditionOperator: Int,
    val conditionValue: Long,
)

data class ActivityTimeDef(
    val id: Int,
    val switchKind: Int,
    val startYear: Int,
    val startMonth: Int,
    val startDay: Int,
    val startHour: Int,
    val startMinute: Int,
    val endYear: Int,
    val endMonth: Int,
    val endDay: Int,
    val endHour: Int,
    val endMinute: Int,
    /** Monday bit0 ... Sunday bit6, exactly as UIActivity.intTobit/openDay consumes it. */
    val openDayMask: Int,
    val openHour: Int,
    val openMinute: Int,
    val closeHour: Int,
    val closeMinute: Int,
)
