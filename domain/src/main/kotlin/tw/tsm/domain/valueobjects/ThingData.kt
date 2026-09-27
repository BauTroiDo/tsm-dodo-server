package tw.tsm.domain.valueobjects

/**
 * 物品實例值物件 — 對應 `Item.lua ThingData.New(data)` 第 81-100 行
 *
 * 網路傳輸格式：35 bytes（小端序）
 * protocal.lua 註解寫「物品資料(18)」但實際是 35 bytes。
 */
data class ThingData(
    /** 物品編號（UInt16） */
    val itemId: Int = 0,
    /** 物品數量（Int32） */
    val quantity: Int = 0,
    /** 毀壞度（Byte，0=完美, 250=壞掉） */
    val damage: Int = 0,
    /** 屬性（Byte，0=無, 1=地, 2=水, 3=火, 4=風, 5=心, 6=?, 7=光, 8=暗） */
    val element: Int = 0,
    /** 屬性值（Byte） */
    val elementValue: Int = 0,
    /** 抵抗（Byte） */
    val proofKind: Int = 0,
    /** 成長等級（Byte，靈性武器用） */
    val growLevel: Int = 0,
    /** 成長經驗值（Int32，靈性武器用） */
    val growExp: Int = 0,
    /** 特殊作用（Byte，0=無, 1=可交易 1 次） */
    val specialKind: Int = 0,
    /** 靈石屬性（Byte） */
    val stoneAttr: Int = 0,
    /** 靈石強化等級（Byte） */
    val stoneLevel: Int = 0,
    /** 專武強化等級（Byte） */
    val enhanceLevel: Int = 0,
    /** 物品刪除時間（Double，OADate 格式） */
    val deleteTime: Double = 0.0,
    /** 損壞前物品 ID（UInt16） */
    val damagedItemId: Int = 0,
    /** 鎖定（Boolean） */
    val isLocked: Boolean = false,
    /** 升階等級（Byte） */
    val reinforced: Int = 0,
    /** 洗鍊等級 1（Byte） */
    val affix1: Int = 0,
    /** 洗鍊等級 2（Byte） */
    val affix2: Int = 0,
    /** 洗鍊等級 3（Byte） */
    val affix3: Int = 0,
    /** 時裝強化等級（Byte） */
    val styleLevel: Int = 0
) {
    /** 此格是否為空 */
    val isEmpty: Boolean get() = itemId == 0
}
