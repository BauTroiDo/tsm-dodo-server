package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 信件附件資料表
 *
 * 附件種類：1=物品（ThingData 欄位）, 2=武將 NPC（npcData blob）
 * 欄位與 Inventories 表 ThingData 部分一致。
 */
object MailAttachments : Table("mail_attachments") {
    /** 所屬信件 ID */
    val mailId = integer("mail_id").index()
    /** 附件序號（1-based，最多 5） */
    val slot = integer("slot")
    /** 附件種類（1=物品, 2=武將 NPC） */
    val kind = integer("kind")

    // ── kind=1 時：ThingData 欄位（與 Inventories 結構一致）──
    val itemId = integer("item_id").default(0)
    val quantity = integer("quantity").default(0)
    val damage = integer("damage").default(0)
    val element = integer("element").default(0)
    val elementValue = integer("element_value").default(0)
    val proofKind = integer("proof_kind").default(0)
    val growLevel = integer("grow_level").default(0)
    val growExp = integer("grow_exp").default(0)
    val specialKind = integer("special_kind").default(0)
    val stoneAttr = integer("stone_attr").default(0)
    val stoneLevel = integer("stone_level").default(0)
    val enhanceLevel = integer("enhance_level").default(0)
    val deleteTime = double("delete_time").default(0.0)
    val damagedItemId = integer("damaged_item_id").default(0)
    val isLocked = bool("is_locked").default(false)
    val reinforced = integer("reinforced").default(0)
    val affix1 = integer("affix1").default(0)
    val affix2 = integer("affix2").default(0)
    val affix3 = integer("affix3").default(0)
    val styleLevel = integer("style_level").default(0)

    // ── kind=2 時：NPC 序列化資料 ──
    val npcData = blob("npc_data").nullable()

    override val primaryKey = PrimaryKey(mailId, slot)
}
