package tw.tsm.domain.data

/** DVT IOS_LoginAwardData_C.dat fixed 46-byte row. */
data class LoginAwardDef(
    val group: Int,
    val changeKind: Int,
    val day: Int,
    val rewards: List<Pair<Int, Int>>,
    val changeItemId: Int,
    val changeQuantity: Int,
    val roleCount: Int,
    val flag: Int,
)
