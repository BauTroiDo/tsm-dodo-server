package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng dữ liệu mời kết bạn
 *
 * Lời mời kết bạn đang chờ xử lý. Xoá sau khi chấp nhận/từ chối.
 */
object FriendInvites : Table("friend_invites") {
    /** ID tự tăng*/
    val id = long("id").autoIncrement()
    /** ID nhân vật được mời*/
    val targetCharId = long("target_char_id").index()
    /** ID nhân vật mời*/
    val inviterCharId = long("inviter_char_id")
    /** Thời gian mời (Unix mili giây)*/
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
