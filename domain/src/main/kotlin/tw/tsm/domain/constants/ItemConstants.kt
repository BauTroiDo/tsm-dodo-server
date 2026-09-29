package tw.tsm.domain.constants

/**
 * Hằng số hệ thống vật phẩm — tương ứng `Item.lua:187-220`
 */
object ItemConstants {
    /** Số ô tối đa của túi đồ*/
    const val MAX_BAG_SLOTS = 250
    /** Số ô ban đầu của túi đồ*/
    const val INIT_BAG_SLOTS = 50
    /** Số ô mỗi trang của túi đồ*/
    const val MAX_PAGE_ITEM = 25
    /** Đơn vị mở rộng túi đồ*/
    const val ADD_BAG_SLOTS = 5

    /** Số slot trang bị cơ bản (đầu/áo/vũ khí/vòng tay/giày/đặc biệt)*/
    const val MAX_EQUIP_SLOTS = 6
    /** Số lượng thời trang tối đa*/
    const val MAX_STYLE = 5

    /** Số ô tối đa của Tiền Trang*/
    const val MAX_BANK_SLOTS = 250
    /** Số ô ban đầu của Tiền Trang*/
    const val INIT_BANK_SLOTS = 25
    /** Đơn vị mở rộng của Tiền Trang*/
    const val ADD_BANK_SLOTS = 5

    /** Số ô tối đa của kho báu — Item.lua:188 MaxStorageGrid*/
    const val MAX_STORAGE_SLOTS = 70
    /** Số ô ban đầu của kho báu (mở rộng hoàn toàn bằng RoleCount)*/
    const val INIT_STORAGE_SLOTS = 0
    /** Đơn vị mở rộng của kho báu (giống Tiền Trang)*/
    const val ADD_STORAGE_SLOTS = 5

    /** Số stack tối đa của vật phẩm trong một ô*/
    const val MAX_STACK = 9999
    /** Giới hạn độ bền của vật phẩm*/
    const val MAX_DURABILITY = 250
    /** Giới hạn tiền*/
    const val MAX_GOLD = 999_999_999

    /** Kích thước truyền qua mạng của ThingData (bytes)*/
    const val THING_DATA_WIRE_SIZE = 35
}

/**
 * Loại vật phẩm trên mặt đất — tương ứng `GdThingManager.lua:3-9`
 */
object EGdThing {
    const val NONE = 0
    /** Vật trên mặt đất là vật phẩm*/
    const val ITEM = 1
    /** Vật trên mặt đất là tiền*/
    const val MONEY = 2
    /** Vật mọc trên mặt đất*/
    const val BORN_ITEM = 3
    /** Bao lì xì*/
    const val CASH_GIFT = 4
}

/**
 * Loại vị trí lưu vật phẩm — tương ứng `EThings` của `Item.lua`
 */
object EThings {
    /** Túi đồ*/
    const val BAG = 1
    /** Tiền Trang*/
    const val BANK = 4
    /** Khe trang bị*/
    const val EQUIP = 8
    /** Kho báu*/
    const val STORAGE = 30
}

/**
 * Mã kết quả thao tác Tiền Trang/kho báu — tương ứng `protocal.lua` S:030-007 / S:102-007
 */
object BankResult {
    /** Thành công*/
    const val SUCCESS = 0
    /** Túi đồ đã đầy (lĩnh thất bại) hoặc túi đồ không có vật phẩm này (lưu thất bại)*/
    const val BAG_FULL = 3
    /** Tiền Trang/kho báu đã đầy*/
    const val BANK_FULL = 13
    /** Kho báu: vật phẩm không stack được (trang bị/vật phẩm hết hạn)*/
    const val NOT_STACKABLE = 14
}
