package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng dữ liệu vật phẩm — lưu chung vật phẩm túi đồ/trang bị/Tiền Trang
 *
 * Khoá chính phức hợp: (characterId, storageType, slot)
 * storageType tương ứng EThings: BAG=1, BANK=4, EQUIP=8
 *
 * Mỗi bản ghi lưu giữ các trường base của ThingData; DVT NFT wire còn có 21 bytes Myth metadata hiện chưa lưu DB.
 */
object Inventories : Table("inventories") {
    /** ID nhân vật sở hữu*/
    val characterId = long("character_id").index()
    /** Loại lưu trữ (1=túi đồ, 4=Tiền Trang, 8=trang bị)*/
    val storageType = integer("storage_type")
    /** Chỉ số ô*/
    val slot = integer("slot")

    // ── Các trường ThingData (tương ứng Item.lua:81-100) ──

    /** Số hiệu vật phẩm*/
    val itemId = integer("item_id")
    /** Số lượng vật phẩm*/
    val quantity = integer("quantity")
    /** Mức độ hư hỏng*/
    val damage = integer("damage").default(0)
    /** Thuộc tính*/
    val element = integer("element").default(0)
    /** Giá trị thuộc tính*/
    val elementValue = integer("element_value").default(0)
    /** Kháng*/
    val proofKind = integer("proof_kind").default(0)
    /** Cấp tăng trưởng*/
    val growLevel = integer("grow_level").default(0)
    /** EXP tăng trưởng*/
    val growExp = integer("grow_exp").default(0)
    /** Tác dụng đặc biệt*/
    val specialKind = integer("special_kind").default(0)
    /** Thuộc tính linh thạch*/
    val stoneAttr = integer("stone_attr").default(0)
    /** Cấp cường hoá linh thạch*/
    val stoneLevel = integer("stone_level").default(0)
    /** Cấp cường hoá vũ khí chuyên thuộc*/
    val enhanceLevel = integer("enhance_level").default(0)
    /** Thời gian xoá vật phẩm (OADate)*/
    val deleteTime = double("delete_time").default(0.0)
    /** ID vật phẩm trước khi hỏng*/
    val damagedItemId = integer("damaged_item_id").default(0)
    /** Khoá*/
    val isLocked = bool("is_locked").default(false)
    /** Cấp thăng hạng*/
    val reinforced = integer("reinforced").default(0)
    /** Cấp tẩy luyện 1*/
    val affix1 = integer("affix1").default(0)
    /** Cấp tẩy luyện 2*/
    val affix2 = integer("affix2").default(0)
    /** Cấp tẩy luyện 3*/
    val affix3 = integer("affix3").default(0)
    /** Cấp cường hoá thời trang*/
    val styleLevel = integer("style_level").default(0)

    // ── DVT NFT/Myth extension: mythLv + 4 x (kind,value,extra) ──
    val mythLevel = integer("myth_level").default(0)
    val myth1Kind = integer("myth1_kind").default(0)
    val myth1Value = integer("myth1_value").default(0)
    val myth1Extra = integer("myth1_extra").default(0)
    val myth2Kind = integer("myth2_kind").default(0)
    val myth2Value = integer("myth2_value").default(0)
    val myth2Extra = integer("myth2_extra").default(0)
    val myth3Kind = integer("myth3_kind").default(0)
    val myth3Value = integer("myth3_value").default(0)
    val myth3Extra = integer("myth3_extra").default(0)
    val myth4Kind = integer("myth4_kind").default(0)
    val myth4Value = integer("myth4_value").default(0)
    val myth4Extra = integer("myth4_extra").default(0)

    override val primaryKey = PrimaryKey(characterId, storageType, slot)
}
