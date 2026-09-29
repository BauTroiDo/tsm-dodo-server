package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng bộ đếm nhân vật (RoleCount)
 *
 * Tương ứng enum ERoleCount trong RoleCount.lua của client.
 * Theo dõi các bộ đếm luỹ kế/hàng ngày: số lần bắt, số lần chết, số lượng bạn bè, v.v.
 *
 * Mục đích: điều kiện thành tựu (ECondition.RoleCount=14, FriendCount=18), số ô túi đồ/Tiền Trang, số ngày đăng nhập, v.v.
 */
object CharacterRoleCounts : Table("character_role_counts") {
    /** ID nhân vật*/
    val charId = long("char_id")

    /** Chỉ số RoleCount (tương ứng giá trị enum ERoleCount)*/
    val countIndex = integer("count_index")

    /** Giá trị hiện tại*/
    val value = integer("value").default(0)

    /** Giá trị trần*/
    val maxValue = integer("max_value").default(0)

    override val primaryKey = PrimaryKey(charId, countIndex)
}
