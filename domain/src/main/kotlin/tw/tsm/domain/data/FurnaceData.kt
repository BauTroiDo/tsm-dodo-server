package tw.tsm.domain.data

/** DVT Data/FurnaceSlotData.lua + IOS_FurnaceSlot_C.dat. */
data class FurnaceSlotDef(
    val id: Int,
    val kind: Int,
    val awardKind: Int,
    val itemId: Int,
    val timedOpen: Int,
    val openYear: Int,
    val openMonth: Int,
    val openDay: Int,
    val closeYear: Int,
    val closeMonth: Int,
    val closeDay: Int,
)

/** DVT Data/FurnaceSelectData.lua + IOS_FurnaceSelect_C.dat. */
data class FurnaceSelectDef(
    val id: Int,
    val kind: Int,
    val sample: Int,
    val awardCounts: List<Int>,
)

data class FurnaceShopItem(
    val itemId: Int,
    val quantity: Int = 1,
)

data class FurnaceShopCategory(
    val kind: Int,
    val isCrit: Boolean,
    val items: List<FurnaceShopItem>,
)

data class FurnaceShopSnapshot(
    val baseRate: Double,
    val activeRate: Double,
    val categories: List<FurnaceShopCategory>,
)

data class FurnaceDismantleEntry(
    val itemId: Int,
    val furnaceKind: Int,
    val chipCount: Int,
)
