package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng dữ liệu tệp đính kèm thư
 *
 * Loại đính kèm: 1=vật phẩm (các trường ThingData), 2=tướng NPC (blob npcData)
 * Các trường nhất quán với phần ThingData của bảng Inventories.
 */
object MailAttachments : Table("mail_attachments") {
    /** ID thư sở hữu*/
    val mailId = integer("mail_id").index()
    /** Số thứ tự tệp đính kèm (1-based, tối đa 5)*/
    val slot = integer("slot")
    /** loại tệp đính kèm (1=vật phẩm, 2=NPC tướng)*/
    val kind = integer("kind")

    // ── Khi kind=1: các trường ThingData (nhất quán với cấu trúc Inventories) ──
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

    // ── Khi kind=2: dữ liệu NPC đã tuần tự hoá ──
    val npcData = blob("npc_data").nullable()

    override val primaryKey = PrimaryKey(mailId, slot)
}
