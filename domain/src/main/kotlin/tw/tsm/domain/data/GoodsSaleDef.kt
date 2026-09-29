package tw.tsm.domain.data

/**
 * DVT mall/gacha sale row from GS_C.dat.
 *
 * Field order is authoritative from Lua Data/GoodsItemData.lua::GoodsSaleData.New.
 * [pageItemIndex] is the 1-based insertion index inside
 * goodsSaleData[storeKind][mainKind][subKind], matching Mall.GetPageItemIndex().
 */
data class GoodsSaleDef(
    val storeKind: Int,
    val currencyKind: Int,
    val mainKind: Int,
    val subKind: Int,
    val open: Boolean,
    val itemId: Int,
    val value: Int,
    val showCredit: Int,
    val saleCredit: Int,
    val saleKind: Int,
    val flag: Int,
    val dayCount: Int,
    val memo: Int,
    val smallTag: Long,
    val specialEvent: Long,
    val timeKind: Int,
    val startYear: Int,
    val startMonth: Int,
    val startDay: Int,
    val startHour: Int,
    val endYear: Int,
    val endMonth: Int,
    val endDay: Int,
    val endHour: Int,
    val buyLimitFlag: Int,
    val bitFunction: Int,
    val pageItemIndex: Int,
)
