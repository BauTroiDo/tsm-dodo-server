package tw.tsm.domain.data

/** One authoritative row from DVT IOS_FNpcSlot_C.dat. */
data class FNpcSlotDef(
    val index: Int,
    val exp: Int,
    val icons: List<Int>,
)
