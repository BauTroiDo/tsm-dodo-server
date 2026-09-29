package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng dữ liệu quan hệ bạn bè
 *
 * Mỗi cặp quan hệ bạn bè lưu hai bản (hai chiều), tiện cho mỗi bên tra cứu và cập nhật cờ chức năng độc lập.
 */
object Friends : Table("friends") {
    /** ID nhân vật người chơi*/
    val charId = long("char_id").index()
    /** ID nhân vật bạn bè*/
    val friendId = long("friend_id").index()
    /** Độ tin cậy (0-9999)*/
    val friendly = integer("friendly").default(0)
    /** Cờ chức năng (bit1=đã tặng quà, bit2=được tặng quà, bit3=đã nhận quà)*/
    val functionFlag = integer("function_flag").default(0)
    /** Thời điểm kết bạn (OADate)*/
    val addTime = double("add_time")

    override val primaryKey = PrimaryKey(charId, friendId)
}
