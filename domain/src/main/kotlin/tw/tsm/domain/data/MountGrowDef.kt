package tw.tsm.domain.data

/**
 * 坐騎成長定義 — 對應 `MountsGrowData.lua`
 *
 * 來源：`MountsGrow_C.dat`
 * key = 等級（1-based）
 */
data class MountGrowDef(
    /** 速度加成值 */
    val speed: Int,
    /** 升級所需物品 ID */
    val upItemId: Int,
    /** 升級所需物品數量 */
    val upItemCount: Int,
    /** 升級所需金錢 */
    val upMoney: Long,
    /** 5 種屬性成長定義（index 0-4 = atk/int/def/hp/sp） */
    val attributes: List<MountAttributeGrow>
)

/**
 * 坐騎單屬性成長定義
 */
data class MountAttributeGrow(
    /** 此等級的屬性加成值 */
    val addValue: Int,
    /** 投點所需物品 ID */
    val upItemId: Int,
    /** 投點所需物品數量 */
    val upItemCount: Int
)
