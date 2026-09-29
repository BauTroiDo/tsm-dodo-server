package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.ThingData

/**
 * giao diện thao tác cơ sở dữ liệu thư
 *
 * nguồn: protocal.lua protocolTable[83], Social.lua AddMail/ReadMail/TakeMail/RemoveMail
 */
interface MailRepository {

    /** truy vấn mọi thư chưa hết hạn của nhân vật (kèm tệp đính kèm)*/
    suspend fun findByCharId(charId: Long): List<MailRecord>

    /** thêm một lá thư (kèm tệp đính kèm), trả về ID thư*/
    suspend fun create(
        charId: Long,
        content: String,
        expireTime: Double,
        attachments: List<MailAttachmentData> = emptyList()
    ): Int

    /** cập nhật hàng loạt trạng thái thư*/
    suspend fun batchUpdateState(charId: Long, mailIds: List<Int>, state: Int)

    /** xoá hàng loạt thư (kèm tệp đính kèm)*/
    suspend fun batchDelete(charId: Long, mailIds: List<Int>)

    /** truy vấn thư chỉ định (kèm tệp đính kèm), kiểm tra quyền sở hữu*/
    suspend fun findById(charId: Long, mailId: Int): MailRecord?
}

/**
 * bản ghi thư
 */
data class MailRecord(
    /** ID thư*/
    val id: Int,
    /** ID nhân vật người nhận*/
    val charId: Long,
    /** thời gian hết hạn (OADate)*/
    val expireTime: Double,
    /** trạng thái thư (0=chưa đọc, 1=đã đọc, 2=đã nhận)*/
    val state: Int,
    /** nội dung thư*/
    val content: String,
    /** danh sách tệp đính kèm*/
    val attachments: List<MailAttachmentData> = emptyList()
)

/**
 * dữ liệu tệp đính kèm của thư
 */
data class MailAttachmentData(
    /** số thứ tự tệp đính kèm (đánh số từ 1)*/
    val slot: Int,
    /** loại tệp đính kèm (1=vật phẩm, 2=NPC tướng)*/
    val kind: Int,
    /** dữ liệu vật phẩm khi kind=1*/
    val thingData: ThingData? = null,
    /** dữ liệu NPC đã tuần tự hoá khi kind=2*/
    val npcData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MailAttachmentData) return false
        return slot == other.slot && kind == other.kind
    }

    override fun hashCode(): Int = 31 * slot + kind
}
