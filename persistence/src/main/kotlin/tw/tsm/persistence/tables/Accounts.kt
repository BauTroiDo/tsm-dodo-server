package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 帳號資料表
 */
object Accounts : Table("accounts") {
    /** 自動遞增 ID */
    val id = long("id").autoIncrement()

    /** 帳號名稱（唯一索引） */
    val account = varchar("account", 64).uniqueIndex()

    /** BCrypt 密碼雜湊 */
    val passwordHash = varchar("password_hash", 72)

    /** 是否封鎖 */
    val isSuspended = bool("is_suspended").default(false)

    /** 最後登入時間（Unix millis） */
    val lastLoginAt = long("last_login_at").nullable()

    /** 建立時間（Unix millis） */
    val createdAt = long("created_at")

    /** GM 等級（0=普通玩家, 1+=GM） */
    val gmLevel = integer("gm_level").default(0)

    override val primaryKey = PrimaryKey(id)
}
