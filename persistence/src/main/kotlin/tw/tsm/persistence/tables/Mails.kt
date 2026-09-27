package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 信件資料表
 *
 * 存放玩家收到的信件（系統信、活動獎勵、GM 發信等）。
 * mailId 對應客戶端 Social.mails[mailId]。
 */
object Mails : Table("mails") {
    /** 信件 ID（自動遞增，對應客戶端 UInt32） */
    val id = integer("id").autoIncrement()
    /** 收件人角色 ID */
    val charId = long("char_id").index()
    /** 過期時間（OADate 格式 Double） */
    val expireTime = double("expire_time")
    /** 信件狀態（0=未讀, 1=已讀, 2=已領取） */
    val state = integer("state").default(0)
    /** 信件內容（UTF-8 存儲，發送時轉 Big5） */
    val content = text("content")
    /** 建立時間（Unix 毫秒） */
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
