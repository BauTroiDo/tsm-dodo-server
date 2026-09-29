package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng tài khoản
 */
object Accounts : Table("accounts") {
    /** ID tự tăng*/
    val id = long("id").autoIncrement()

    /** Tên tài khoản (index duy nhất)*/
    val account = varchar("account", 64).uniqueIndex()

    /** Hash mật khẩu bằng BCrypt*/
    val passwordHash = varchar("password_hash", 72)

    /** Có bị cấm (ban) hay không*/
    val isSuspended = bool("is_suspended").default(false)

    /** Thời gian đăng nhập gần nhất (Unix millis)*/
    val lastLoginAt = long("last_login_at").nullable()

    /** Thời gian tạo (Unix millis)*/
    val createdAt = long("created_at")

    /** Cấp GM (0=người chơi thường, 1+=GM)*/
    val gmLevel = integer("gm_level").default(0)

    override val primaryKey = PrimaryKey(id)
}
