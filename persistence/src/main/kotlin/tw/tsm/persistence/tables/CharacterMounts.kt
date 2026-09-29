package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng dữ liệu vật cưỡi của nhân vật
 *
 * Mỗi nhân vật một bản ghi, lưu cấp vật cưỡi, 6 loại điểm thuộc tính và ID NPC vật cưỡi hiện tại.
 */
object CharacterMounts : Table("character_mounts") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").uniqueIndex()
    /** Cấp vật cưỡi*/
    val level = integer("level").default(1)
    /** Điểm thuộc tính 1 (tấn công)*/
    val attrPoint1 = integer("attr_point_1").default(0)
    /** Điểm thuộc tính 2 (trí tuệ)*/
    val attrPoint2 = integer("attr_point_2").default(0)
    /** Điểm thuộc tính 3 (phòng thủ)*/
    val attrPoint3 = integer("attr_point_3").default(0)
    /** Điểm thuộc tính 4 (HP cộng thêm)*/
    val attrPoint4 = integer("attr_point_4").default(0)
    /** Điểm thuộc tính 5 (SP cộng thêm)*/
    val attrPoint5 = integer("attr_point_5").default(0)
    /** Điểm thuộc tính 6 (dự phòng)*/
    val attrPoint6 = integer("attr_point_6").default(0)
    /** ID NPC vật cưỡi hiện tại (0 = không có)*/
    val currentNpcId = integer("current_npc_id").default(0)

    override val primaryKey = PrimaryKey(id)
}
