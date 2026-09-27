package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 物品資料表 — 統一存放背包/裝備/錢莊物品
 *
 * 複合主鍵：(characterId, storageType, slot)
 * storageType 對應 EThings：BAG=1, BANK=4, EQUIP=8
 *
 * 每筆記錄對應一個 ThingData（35 bytes 網路格式的持久化版本）。
 */
object Inventories : Table("inventories") {
    /** 所屬角色 ID */
    val characterId = long("character_id").index()
    /** 儲存類型（1=背包, 4=錢莊, 8=裝備） */
    val storageType = integer("storage_type")
    /** 格位索引 */
    val slot = integer("slot")

    // ── ThingData 欄位（對應 Item.lua:81-100） ──

    /** 物品編號 */
    val itemId = integer("item_id")
    /** 物品數量 */
    val quantity = integer("quantity")
    /** 毀壞度 */
    val damage = integer("damage").default(0)
    /** 屬性 */
    val element = integer("element").default(0)
    /** 屬性值 */
    val elementValue = integer("element_value").default(0)
    /** 抵抗 */
    val proofKind = integer("proof_kind").default(0)
    /** 成長等級 */
    val growLevel = integer("grow_level").default(0)
    /** 成長經驗值 */
    val growExp = integer("grow_exp").default(0)
    /** 特殊作用 */
    val specialKind = integer("special_kind").default(0)
    /** 靈石屬性 */
    val stoneAttr = integer("stone_attr").default(0)
    /** 靈石強化等級 */
    val stoneLevel = integer("stone_level").default(0)
    /** 專武強化等級 */
    val enhanceLevel = integer("enhance_level").default(0)
    /** 物品刪除時間（OADate） */
    val deleteTime = double("delete_time").default(0.0)
    /** 損壞前物品 ID */
    val damagedItemId = integer("damaged_item_id").default(0)
    /** 鎖定 */
    val isLocked = bool("is_locked").default(false)
    /** 升階等級 */
    val reinforced = integer("reinforced").default(0)
    /** 洗鍊等級 1 */
    val affix1 = integer("affix1").default(0)
    /** 洗鍊等級 2 */
    val affix2 = integer("affix2").default(0)
    /** 洗鍊等級 3 */
    val affix3 = integer("affix3").default(0)
    /** 時裝強化等級 */
    val styleLevel = integer("style_level").default(0)

    override val primaryKey = PrimaryKey(characterId, storageType, slot)
}
