package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.MailConstants
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.MailAttachmentData
import tw.tsm.domain.interfaces.repositories.MailRecord
import tw.tsm.domain.interfaces.repositories.MailRepository
import tw.tsm.domain.util.OleDate

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống thư tín — Chịu trách nhiệm đọc thư, nhận đính kèm, xóa, đánh dấu đã đọc
 *
 * Nguồn: protocal.lua protocolTable[83], Social.lua AddMail/ReadMail/TakeMail/RemoveMail
 */
class MailSystem(
    private val mailRepository: MailRepository,
    private val inventorySystem: InventorySystem,
    private val responseSender: ResponseSender
) {

    /**
     * Tải và gửi tất cả các thư chưa hết hạn khi đăng nhập
     *
     * Thư hết hạn sẽ bị tự động xóa.
     */
    suspend fun loadAndSend(session: GameSession) {
        val charId = session.roleId.toLong()
        val allMails = mailRepository.findByCharId(charId)

        // Lọc thư hết hạn
        val now = OleDate.now()
        val expired = allMails.filter { it.expireTime < now }
        val valid = allMails.filter { it.expireTime >= now }

        // Xóa thư hết hạn
        if (expired.isNotEmpty()) {
            mailRepository.batchDelete(charId, expired.map { it.id })
            logger.info { "Tự động xóa ${expired.size} thư hết hạn (charId=$charId)" }
        }

        // Gửi thư hợp lệ cho client
        if (valid.isNotEmpty()) {
            responseSender.sendMails(session, valid)
            logger.info { "Gửi ${valid.size} thư (charId=$charId)" }
        }
    }

    /**
     * Nhận đính kèm (C:083-001)
     *
     * Kiểm tra trạng thái thư, không gian túi đồ, và thêm vật phẩm đính kèm vào túi đồ.
     *
     * @param mailIds Danh sách ID thư cần nhận đính kèm
     */
    suspend fun takeAttachments(session: GameSession, mailIds: List<Int>) {
        val charId = session.roleId.toLong()
        val successIds = mutableListOf<Int>()

        for (mailId in mailIds) {
            val mail = mailRepository.findById(charId, mailId)
            if (mail == null) {
                responseSender.sendMailError(session, MailConstants.ERROR_ALREADY_DELETED)
                continue
            }

            // Đã nhận
            if (mail.state == MailConstants.STATE_TAKEN) {
                responseSender.sendMailError(session, MailConstants.ERROR_ALREADY_TAKEN)
                continue
            }

            // Hết hạn
            if (mail.expireTime < OleDate.now()) {
                responseSender.sendMailError(session, MailConstants.ERROR_EXPIRED)
                continue
            }

            // Thư không có đính kèm trực tiếp đánh dấu là đã nhận
            if (mail.attachments.isEmpty()) {
                successIds.add(mailId)
                continue
            }

            // Thử thêm vật phẩm đính kèm vào túi đồ
            if (!tryAddAttachments(charId, mail.attachments)) {
                responseSender.sendMailError(session, MailConstants.ERROR_BAG_FULL)
                continue
            }

            successIds.add(mailId)
        }

        if (successIds.isNotEmpty()) {
            mailRepository.batchUpdateState(charId, successIds, MailConstants.STATE_TAKEN)
            responseSender.sendMailTakeConfirm(session, successIds)
            logger.info { "Nhận đính kèm của ${successIds.size} thư (charId=$charId)" }
        }
    }

    /**
     * Xóa thư (C:083-002)
     *
     * @param mailIds Danh sách ID thư cần xóa
     */
    suspend fun deleteMails(session: GameSession, mailIds: List<Int>) {
        val charId = session.roleId.toLong()
        mailRepository.batchDelete(charId, mailIds)
        responseSender.sendMailDeleteConfirm(session, mailIds)
        logger.info { "Xóa ${mailIds.size} thư (charId=$charId)" }
    }

    /**
     * Đánh dấu đã đọc (C:083-003)
     *
     * @param mailIds Danh sách ID thư cần đánh dấu đã đọc
     */
    suspend fun readMails(session: GameSession, mailIds: List<Int>) {
        val charId = session.roleId.toLong()
        mailRepository.batchUpdateState(charId, mailIds, MailConstants.STATE_READ)
        responseSender.sendMailReadConfirm(session, mailIds)
    }

    /**
     * Gửi thư hệ thống (cho các hệ thống khác gọi)
     *
     * @param charId ID nhân vật người nhận
     * @param content Nội dung thư (UTF-8, chuyển sang Big5 khi gửi)
     * @param expireDays Số ngày hết hạn (Mặc định 30 ngày)
     * @param attachments Danh sách đính kèm
     * @return ID thư
     */
    suspend fun sendSystemMail(
        charId: Long,
        content: String,
        expireDays: Int = MailConstants.DEFAULT_EXPIRE_DAYS,
        attachments: List<MailAttachmentData> = emptyList()
    ): Int {
        val expireTime = OleDate.daysFromNow(expireDays)
        val mailId = mailRepository.create(charId, content, expireTime, attachments)
        logger.info { "Thư hệ thống đã được gửi: mailId=$mailId, charId=$charId" }
        return mailId
    }

    /**
     * Thử thêm vật phẩm đính kèm vào túi đồ
     *
     * @return true = Đặt vào thành công toàn bộ, false = Không đủ không gian (Túi đầy)
     */
    private suspend fun tryAddAttachments(charId: Long, attachments: List<MailAttachmentData>): Boolean {
        for (attach in attachments) {
            when (attach.kind) {
                MailConstants.ATTACH_KIND_ITEM -> {
                    val thing = attach.thingData ?: continue
                    val result = inventorySystem.addItemToBag(charId, thing.itemId, thing.quantity)
                    if (result == null) {
                        return false
                    }
                }
                MailConstants.ATTACH_KIND_NPC -> {
                    // TODO: Đính kèm NPC võ tướng tạm thời chưa xử lý
                    logger.warn { "Tạm thời không hỗ trợ nhận đính kèm NPC võ tướng (charId=$charId)" }
                }
            }
        }
        return true
    }
}