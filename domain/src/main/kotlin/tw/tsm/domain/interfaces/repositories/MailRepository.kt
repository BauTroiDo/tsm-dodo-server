package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.ThingData

/**
 * 信件資料庫操作介面
 *
 * 來源：protocal.lua protocolTable[83], Social.lua AddMail/ReadMail/TakeMail/RemoveMail
 */
interface MailRepository {

    /** 查詢角色的所有未過期信件（含附件） */
    suspend fun findByCharId(charId: Long): List<MailRecord>

    /** 新增一封信件（含附件），回傳信件 ID */
    suspend fun create(
        charId: Long,
        content: String,
        expireTime: Double,
        attachments: List<MailAttachmentData> = emptyList()
    ): Int

    /** 批量更新信件狀態 */
    suspend fun batchUpdateState(charId: Long, mailIds: List<Int>, state: Int)

    /** 批量刪除信件（含附件） */
    suspend fun batchDelete(charId: Long, mailIds: List<Int>)

    /** 查詢指定信件（含附件），驗證所有權 */
    suspend fun findById(charId: Long, mailId: Int): MailRecord?
}

/**
 * 信件記錄
 */
data class MailRecord(
    /** 信件 ID */
    val id: Int,
    /** 收件人角色 ID */
    val charId: Long,
    /** 過期時間（OADate） */
    val expireTime: Double,
    /** 信件狀態（0=未讀, 1=已讀, 2=已領取） */
    val state: Int,
    /** 信件內容 */
    val content: String,
    /** 附件列表 */
    val attachments: List<MailAttachmentData> = emptyList()
)

/**
 * 信件附件資料
 */
data class MailAttachmentData(
    /** 附件序號（1-based） */
    val slot: Int,
    /** 附件種類（1=物品, 2=武將 NPC） */
    val kind: Int,
    /** kind=1 時的物品資料 */
    val thingData: ThingData? = null,
    /** kind=2 時的 NPC 序列化資料 */
    val npcData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MailAttachmentData) return false
        return slot == other.slot && kind == other.kind
    }

    override fun hashCode(): Int = 31 * slot + kind
}
