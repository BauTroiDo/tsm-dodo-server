package tw.tsm.domain.data

/**
 * 機關盒啟動設定 — 客戶端 C:065-001 上傳的參數
 *
 * 對應 MachineBox.lua SetAutoFight() 的封包欄位。
 */
data class MacroBoxSettings(
    /** 玩家 HP 補品觸發下限（百分比，例如 50 = 50%） */
    val playerHpPercent: Int,
    /** 玩家 SP 補品觸發下限（百分比） */
    val playerSpPercent: Int,
    /** HP 補品不足時回城 */
    val hpReturnTown: Boolean,
    /** SP 補品不足時回城 */
    val spReturnTown: Boolean,
    /** 玩家死亡時回城 */
    val playerDeadReturn: Boolean,
    /** 武將死亡時回城 */
    val npcDeadReturn: Boolean,
    /** 自動使用經驗道具 */
    val autoExp: Boolean,
    /** 出戰武將自動換鉛錘 */
    val autoShotput: Boolean
)

/**
 * 機關盒晶石槽位
 */
data class SparSlot(
    /** 晶石物品 ID */
    val sparId: Int,
    /** 累計使用次數 */
    var count: Int
)

/**
 * 機關盒經驗瓶槽位
 */
data class ExpBottleSlot(
    /** 經驗瓶物品 ID */
    val bottleId: Int,
    /** 累計經驗值 */
    var exp: Int
)

/**
 * 玩家機關盒運行時狀態
 *
 * 不持久化到 DB，登入時從背包重新初始化。
 */
data class MacroBoxState(
    /** 機關盒是否啟動中 */
    var isActive: Boolean = false,
    /** 啟動設定 */
    var settings: MacroBoxSettings? = null,
    /** 晶石槽位（index → SparSlot），上限 MaxSparBase + sparExtendNum */
    val sparSlots: MutableMap<Int, SparSlot> = mutableMapOf(),
    /** 已擴充晶石槽數 */
    var sparExtendNum: Int = 0,
    /** 經驗瓶槽位（index → ExpBottleSlot） */
    val expBottles: MutableMap<Int, ExpBottleSlot> = mutableMapOf(),
    /** 已擴充經驗瓶槽數 */
    var expExtendNum: Int = 0,
    /** 目前優先使用的經驗瓶索引 */
    var expPriority: Int = 0
) {
    companion object {
        /** 基本晶石槽數 */
        const val MAX_SPAR_BASE = 3
        /** 最大可擴充晶石槽數 */
        const val MAX_SPAR_EXT = 2
        /** 同種晶石最多累加次數 */
        const val MAX_SPAR_COUNT = 60000
        /** 基本經驗瓶槽數 */
        const val BASE_EXP_BOTTLE = 3
        /** 最大可擴充經驗瓶槽數 */
        const val MAX_EXP_EXT = 5
    }
}
