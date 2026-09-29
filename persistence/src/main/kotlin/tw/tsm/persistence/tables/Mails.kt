package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng dữ liệu thư
 *
 * Lưu thư người chơi nhận được (thư hệ thống, thưởng hoạt động, GM gửi thư, v.v.).
 * mailId tương ứng Social.mails[mailId] phía client.
 */
object Mails : Table("mails") {
    /** ID thư (tự tăng dần, tương ứng UInt32 phía client)*/
    val id = integer("id").autoIncrement()
    /** ID nhân vật người nhận*/
    val charId = long("char_id").index()
    /** Thời gian hết hạn (Double định dạng OADate)*/
    val expireTime = double("expire_time")
    /** trạng thái thư (0=chưa đọc, 1=đã đọc, 2=đã nhận)*/
    val state = integer("state").default(0)
    /** Nội dung thư (lưu UTF-8, khi gửi chuyển sang Big5)*/
    val content = text("content")
    /** Thời gian tạo (Unix mili giây)*/
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
