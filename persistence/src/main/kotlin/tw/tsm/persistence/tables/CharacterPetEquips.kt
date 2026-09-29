package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng trang bị tướng của nhân vật
 *
 * Mỗi bản ghi là một trang bị trên người tướng, tối đa 6 món (fitType 1-6, tương ứng EItemFitType).
 * Map với bản thể tướng trong CharacterPets qua (characterId, storageType, slotIndex),
 * Trang bị đi theo tướng (khi di chuyển giữa tùy thân/xe ngựa/lữ quán/kho sẽ được PetRepositoryImpl ghi lại cùng).
 */
object CharacterPetEquips : Table("character_pet_equips") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    /** Loại lưu trữ tướng (giống CharacterPets.storageType: 1=tùy thân, 2=xe ngựa, 3=lữ quán, 4=kho tướng)*/
    val storageType = integer("storage_type")
    /** Chỉ số ô của tướng (giống CharacterPets.slotIndex)*/
    val slotIndex = integer("slot_index")
    /** Vị trí trang bị (fitType, 1-6)*/
    val fitType = integer("fit_type")
    /** ID vật phẩm*/
    val itemId = integer("item_id")
    /** Số lượng*/
    val quantity = integer("quantity").default(1)
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
    /** Loại đặc biệt*/
    val specialKind = integer("special_kind").default(0)
    /** Thuộc tính linh thạch*/
    val stoneAttr = integer("stone_attr").default(0)
    /** Cấp linh thạch*/
    val stoneLevel = integer("stone_level").default(0)
    /** Cấp cường hoá vũ khí chuyên thuộc*/
    val enhanceLevel = integer("enhance_level").default(0)
    /** Thời gian xoá*/
    val deleteTime = double("delete_time").default(0.0)
    /** ID vật phẩm trước khi hỏng*/
    val damagedItemId = integer("damaged_item_id").default(0)
    /** Khoá*/
    val isLocked = bool("is_locked").default(false)
    /** Cấp thăng hạng*/
    val reinforced = integer("reinforced").default(0)
    /** Tẩy luyện 1*/
    val affix1 = integer("affix1").default(0)
    /** Tẩy luyện 2*/
    val affix2 = integer("affix2").default(0)
    /** Tẩy luyện 3*/
    val affix3 = integer("affix3").default(0)
    /** Cấp cường hoá thời trang*/
    val styleLevel = integer("style_level").default(0)

    override val primaryKey = PrimaryKey(id)
}
