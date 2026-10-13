package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng dữ liệu tướng của nhân vật
 *
 * Lưu dữ liệu tướng trong tùy thân, xe ngựa, lữ quán và kho tướng.
 * storageType: 1=tùy thân, 2=xe ngựa, 3=lữ quán, 4=kho tướng
 */
object CharacterPets : Table("character_pets") {
    val id = long("id").autoIncrement()
    /**
     * Durable pet-instance UUID. Legacy rows start blank and are backfilled
     * atomically on first repository load (not inferred from row id or slot).
     */
    val instanceId = varchar("instance_id", 36).default("")
    val characterId = long("character_id").index()
    /** Loại lưu trữ: 1=mang theo người, 2=xe ngựa, 3=lữ quán, 4=kho tướng*/
    val storageType = integer("storage_type").default(1)
    /** Chỉ số ô*/
    val slotIndex = integer("slot_index")
    /** NPC ID */
    val npcId = integer("npc_id")
    /** Tên*/
    val name = varchar("name", 64).default("")
    /** Cấp*/
    val level = integer("level").default(1)
    /** Kinh nghiệm*/
    val exp = long("exp").default(0)
    /** Lượng máu*/
    val hp = long("hp").default(0)
    /** Ma lực*/
    val sp = integer("sp").default(0)
    /** Tấn công*/
    val atk = integer("atk").default(0)
    /** Phòng thủ*/
    val def = integer("def").default(0)
    /** Nhanh nhẹn*/
    val agi = integer("agi").default(0)
    /** Trí tuệ*/
    val intelligence = integer("intelligence").default(0)
    /** Mức tăng HP*/
    val hpx = integer("hpx").default(0)
    /** Mức tăng SP*/
    val spx = integer("spx").default(0)
    /** Số lần tử trận*/
    val deathCount = integer("death_count").default(0)
    /** Lòng trung thành*/
    val loyalty = integer("loyalty").default(60)
    /** Điểm kỹ năng*/
    val skillPoint = integer("skill_point").default(0)
    /** Cấp kỹ năng 1*/
    val skill1Lv = integer("skill1_lv").default(0)
    /** Cấp kỹ năng 2*/
    val skill2Lv = integer("skill2_lv").default(0)
    /** Cấp kỹ năng 3*/
    val skill3Lv = integer("skill3_lv").default(0)
    /** Loại phát triển: 0=không thể phát triển, 1=có thể phát triển*/
    val canGrow = integer("can_grow").default(1)
    /** ID hồn ngọc*/
    val soulId = integer("soul_id").default(0)
    /** Cấp cường hóa*/
    val upgradeLv = integer("upgrade_lv").default(0)
    /** Số lần dùng đan thăng hoa*/
    val ascendPillCount = integer("ascend_pill_count").default(0)
    /** Có dùng được kỹ năng đặc hữu hay không*/
    val canUseSpecialSkill = bool("can_use_special_skill").default(false)
    /** Số lượng đan HP*/
    val hpPillCount = integer("hp_pill_count").default(0)
    /** Số lượng đan SP*/
    val spPillCount = integer("sp_pill_count").default(0)
    /** Cấp kỹ năng 4*/
    val skill4Lv = integer("skill4_lv").default(0)
    /** Tăng trưởng pháp lực*/
    val conjureGrow = integer("conjure_grow").default(0)
    /** Số lần chuyển sinh*/
    val turnKind = integer("turn_kind").default(0)
    /** Trạng thái còn sống*/
    val surplus = integer("surplus").default(0)
    /** Timestamp lên cấp*/
    val lvUpTime = double("lv_up_time").default(0.0)
    /** Đã migrate seed pet legacy sang LV1 chuẩn DVT. Record cũ mặc định false. */
    val seedFixed = bool("seed_fixed").default(false)
    /** Được người chơi chọn để cùng xuất chiến. */
    val isDeployed = bool("is_deployed").default(false)

    override val primaryKey = PrimaryKey(id)
}
