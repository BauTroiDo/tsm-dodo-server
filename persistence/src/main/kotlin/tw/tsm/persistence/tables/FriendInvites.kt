package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 好友邀請資料表
 *
 * 待處理的好友邀請。接受/拒絕後刪除。
 */
object FriendInvites : Table("friend_invites") {
    /** 自動遞增 ID */
    val id = long("id").autoIncrement()
    /** 被邀請者角色 ID */
    val targetCharId = long("target_char_id").index()
    /** 邀請者角色 ID */
    val inviterCharId = long("inviter_char_id")
    /** 邀請時間（Unix 毫秒） */
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
