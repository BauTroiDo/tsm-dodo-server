package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng bùa dịch chuyển ký ức của nhân vật — tương ứng UITeleport.flyPoints của client
 *
 * Lưu các vị trí đã ghi của bùa dịch chuyển ký ức người chơi.
 * Mỗi nhân vật tối đa 10 bản ghi (MaxFlyScroll=5 + MaxFlyScrollExtend=5).
 */
object CharacterFlyPoints : Table("character_fly_points") {
    /** ID nhân vật*/
    val charId = long("char_id").index()

    /** Chỉ số điểm dịch chuyển (1-based, 1 đến 10)*/
    val pointIndex = integer("point_index")

    /** ID cảnh*/
    val sceneId = integer("scene_id")

    /** Toạ độ X*/
    val x = integer("x")

    /** Toạ độ Y*/
    val y = integer("y")

    override val primaryKey = PrimaryKey(charId, pointIndex)
}
