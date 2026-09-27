package tw.tsm.domain.enums

/**
 * 裝備部位列舉 — 對應 `ItemData.lua` 的 `EItemFitType`
 *
 * 6 個基本裝備槽位。時裝(7-11)、外顯時裝(12-16)、披風(100) 延後處理。
 */
enum class EquipSlot(val fitType: Int) {
    /** 帽 */
    HEAD(1),
    /** 衣 */
    BODY(2),
    /** 武器 */
    WEAPON(3),
    /** 護腕 */
    WRIST(4),
    /** 鞋子 */
    BOOTS(5),
    /** 特殊 */
    SPECIAL(6);

    companion object {
        /** 由 fitType 查找對應的裝備部位，找不到回傳 null */
        fun fromFitType(ft: Int): EquipSlot? = entries.find { it.fitType == ft }

        /** 判斷 fitType 是否為基本裝備（1-6） */
        fun isBasicEquip(fitType: Int): Boolean = fitType in 1..6
    }
}
