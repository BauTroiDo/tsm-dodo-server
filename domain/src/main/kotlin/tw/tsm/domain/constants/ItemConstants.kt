package tw.tsm.domain.constants

/**
 * 物品系統常數 — 對應 `Item.lua:187-220`
 */
object ItemConstants {
    /** 背包最大格數 */
    const val MAX_BAG_SLOTS = 250
    /** 背包初始格數 */
    const val INIT_BAG_SLOTS = 50
    /** 背包每頁格數 */
    const val MAX_PAGE_ITEM = 25
    /** 背包擴充單位 */
    const val ADD_BAG_SLOTS = 5

    /** 基本裝備槽位數（頭/衣/武/腕/鞋/特殊） */
    const val MAX_EQUIP_SLOTS = 6
    /** 時裝最大數量 */
    const val MAX_STYLE = 5

    /** 錢莊最大格數 */
    const val MAX_BANK_SLOTS = 250
    /** 錢莊初始格數 */
    const val INIT_BANK_SLOTS = 25
    /** 錢莊擴充單位 */
    const val ADD_BANK_SLOTS = 5

    /** 寶庫最大格數 — Item.lua:188 MaxStorageGrid */
    const val MAX_STORAGE_SLOTS = 70
    /** 寶庫初始格數（完全靠 RoleCount 擴充） */
    const val INIT_STORAGE_SLOTS = 0
    /** 寶庫擴充單位（與錢莊相同） */
    const val ADD_STORAGE_SLOTS = 5

    /** 單格物品最大堆疊數 */
    const val MAX_STACK = 9999
    /** 物品耐久上限 */
    const val MAX_DURABILITY = 250
    /** 金錢上限 */
    const val MAX_GOLD = 999_999_999

    /** ThingData 網路傳輸大小（bytes） */
    const val THING_DATA_WIRE_SIZE = 35
}

/**
 * 地面物品種類 — 對應 `GdThingManager.lua:3-9`
 */
object EGdThing {
    const val NONE = 0
    /** 地上物為物品 */
    const val ITEM = 1
    /** 地上物為金錢 */
    const val MONEY = 2
    /** 地上長出物 */
    const val BORN_ITEM = 3
    /** 紅包 */
    const val CASH_GIFT = 4
}

/**
 * 物品存放位置種類 — 對應 `Item.lua` 的 `EThings`
 */
object EThings {
    /** 背包 */
    const val BAG = 1
    /** 錢莊 */
    const val BANK = 4
    /** 裝備欄 */
    const val EQUIP = 8
    /** 寶庫 */
    const val STORAGE = 30
}

/**
 * 錢莊/寶庫操作結果碼 — 對應 `protocal.lua` S:030-007 / S:102-007
 */
object BankResult {
    /** 成功 */
    const val SUCCESS = 0
    /** 背包已滿（領取失敗）或背包無此物品（存入失敗） */
    const val BAG_FULL = 3
    /** 錢莊/寶庫已滿 */
    const val BANK_FULL = 13
    /** 寶庫：非可堆疊物品（裝備/過期物品） */
    const val NOT_STACKABLE = 14
}
