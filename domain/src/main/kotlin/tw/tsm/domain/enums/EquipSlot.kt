package tw.tsm.domain.enums

/**
 * Liệt kê vị trí trang bị — tương ứng `EItemFitType` của `ItemData.lua`
 *
 * 6 ô trang bị cơ bản. Thời trang (7-11), thời trang hiển thị (12-16), áo choàng (100) xử lý sau.
 */
enum class EquipSlot(val fitType: Int) {
    /** Mũ*/
    HEAD(1),
    /** Áo*/
    BODY(2),
    /** Vũ khí*/
    WEAPON(3),
    /** Vòng hộ cổ tay*/
    WRIST(4),
    /** Giày*/
    BOOTS(5),
    /** Đặc biệt*/
    SPECIAL(6);

    companion object {
        /** Tra vị trí trang bị tương ứng từ fitType, không tìm thấy trả về null*/
        fun fromFitType(ft: Int): EquipSlot? = entries.find { it.fitType == ft }

        /** Kiểm tra fitType có phải trang bị cơ bản (1-6) hay không*/
        fun isBasicEquip(fitType: Int): Boolean = fitType in 1..6
    }
}
