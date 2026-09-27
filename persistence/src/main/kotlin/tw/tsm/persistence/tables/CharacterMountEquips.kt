package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色坐騎裝備資料表
 *
 * 每筆代表一件坐騎裝備，最多 5 件（fitType 1-5）。
 * ThingData 的各欄位獨立存儲。
 */
object CharacterMountEquips : Table("character_mount_equips") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    /** 裝備部位（fitType，1-5） */
    val fitType = integer("fit_type")
    /** 物品 ID */
    val itemId = integer("item_id")
    /** 數量 */
    val quantity = integer("quantity").default(1)
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
    /** 成長經驗 */
    val growExp = integer("grow_exp").default(0)
    /** 特殊類型 */
    val specialKind = integer("special_kind").default(0)
    /** 靈石屬性 */
    val stoneAttr = integer("stone_attr").default(0)
    /** 靈石等級 */
    val stoneLevel = integer("stone_level").default(0)
    /** 專武強化等級 */
    val enhanceLevel = integer("enhance_level").default(0)
    /** 刪除時間 */
    val deleteTime = double("delete_time").default(0.0)
    /** 損壞前物品 ID */
    val damagedItemId = integer("damaged_item_id").default(0)
    /** 鎖定 */
    val isLocked = bool("is_locked").default(false)
    /** 升階等級 */
    val reinforced = integer("reinforced").default(0)
    /** 洗鍊 1 */
    val affix1 = integer("affix1").default(0)
    /** 洗鍊 2 */
    val affix2 = integer("affix2").default(0)
    /** 洗鍊 3 */
    val affix3 = integer("affix3").default(0)
    /** 時裝強化等級 */
    val styleLevel = integer("style_level").default(0)

    override val primaryKey = PrimaryKey(id)
}
