package tw.tsm.domain.data

/**
 * 物品定義 — 對應 `ItemData.New(reader)`
 *
 * 來源：`Item_C.dat`
 * 欄位順序嚴格對照 ItemData.lua 第 300-385 行（共 54 個欄位 + name + description）。
 */
data class ItemDef(
    /** [1] 名稱（UTF-16LE） */
    val name: String,
    /** [2] 類別（EItemKind） */
    val kind: Int,
    /** [4] 圖示 ID */
    val iconId: Int,
    /** [5] 男身圖號 */
    val malePicId: Int,
    /** [6] 女身圖號 */
    val femalePicId: Int,
    /** [7-9] 屬性 1（kind, attrItem, value） */
    val attr1Kind: Int,
    val attr1Item: Int,
    val attr1Value: Int,
    /** [10-12] 屬性 2（kind, attrItem, value） */
    val attr2Kind: Int,
    val attr2Item: Int,
    val attr2Value: Int,
    /** [13] 材質 */
    val material: Int,
    /** [14] 等級 */
    val level: Int,
    /** [15] 裝備方式（EItemFitType） */
    val fitType: Int,
    /** [16] 特別作用 */
    val specialAbility: Int,
    /** [17-24] 色盤（男 4 個 + 女 4 個） */
    val maleColorTints: IntArray,
    val femaleColorTints: IntArray,
    /** [25] 開放否（0=開放, 1=否） */
    val openUsed: Int,
    /** [26] 限制等級 */
    val needLv: Int,
    /** [27] 價格 */
    val price: Int,
    /** [28] 賣價 */
    val sellPrice: Int,
    /** [29] 性別限制（1=男, 2=女） */
    val gender: Int,
    /** [30] 處理限制 */
    val restrict: Int,
    /** [31] 發出數 */
    val threshold: Int,
    /** [32] 屬性（0=無, 1-8） */
    val element: Int,
    /** [33] 屬值 */
    val elementValue: Int,
    /** [34] 參考技能 ID */
    val skillLink: Int,
    /** [35] 轉生限制 */
    val turn: Int,
    /** [36] 戰功兌換點數 */
    val giftDot: Int,
    /** [37] 備用 2 */
    val spare2: Int,
    /** [38] 備用 3 */
    val spare3: Int,
    /** [39] 處理限制 2 */
    val restrict2: Int,
    /** [40] 套裝 ID */
    val suitId: Int,
    /** [41] 備用 5 */
    val spare5: Int,
    /** [42] 直接使用（0=否, 1=是） */
    val directUse: Int,
    /** [43] RoleCount 索引 */
    val roleCountIndex: Int,
    /** [44] RoleCount 值 */
    val roleCountValue: Int,
    /** [45] 排序 */
    val sort: Int,
    /** [46] 男裝備開關 */
    val maleEquipSwitch: Int,
    /** [47] 女裝備開關 */
    val femaleEquipSwitch: Int,
    /** [48] 使用鈕關閉原因 */
    val btnState: Int,
    /** [49] 耐久顯示 */
    val durable: Int,
    /** [50] 熔爐種類 */
    val furnaceKind: Int,
    /** [51] 熔爐碎片數量 */
    val furnaceCount: Long,
    /** [52] 品質（0=白, 1=綠, 2=藍, 3=紫, 4=紅） */
    val quality: Int,
    /** [53] 拍賣主頁 */
    val auctionTag: Int,
    /** [54] 拍賣子類 */
    val auctionSubTag: Int,
    /** 說明（UTF-16LE） */
    val description: String
)
