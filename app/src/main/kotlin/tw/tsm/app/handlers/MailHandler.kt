package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.MailSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Handler hệ thống thư (mainKind=83)
 *
 * Nguồn: protocal.lua protocolTable[83]
 * - C:083-001 nhận tệp đính kèm
 * - C:083-002 xóa thư
 * - C:083-003 đánh dấu thư đã đọc
 */
class MailHandler(
    private val mailSystem: MailSystem
) : PacketHandler {
    override val mainKind: Int = Opcodes.MAIL

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleTakeAttachments(session, data)
            2 -> handleDeleteMails(session, data)
            3 -> handleReadMails(session, data)
            else -> logger.warn { "Sub-protocol thư tín chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:083-001 nhận tệp đính kèm
     *
     * Định dạng: +count(4B LE) <<+mailId(4B LE)>>
     */
    private suspend fun handleTakeAttachments(session: GameSession, data: ByteArray) {
        if (data.size < 4) return
        val reader = PacketReader(data)
        val count = reader.readIntLE()
        if (count <= 0 || data.size < 4 + count * 4) return
        val mailIds = (0 until count).map { reader.readIntLE() }
        mailSystem.takeAttachments(session, mailIds)
    }

    /**
     * C:083-002 xóa thư
     *
     * Định dạng: +count(4B LE) <<+mailId(4B LE)>>
     */
    private suspend fun handleDeleteMails(session: GameSession, data: ByteArray) {
        if (data.size < 4) return
        val reader = PacketReader(data)
        val count = reader.readIntLE()
        if (count <= 0 || data.size < 4 + count * 4) return
        val mailIds = (0 until count).map { reader.readIntLE() }
        mailSystem.deleteMails(session, mailIds)
    }

    /**
     * C:083-003 đánh dấu thư đã đọc
     *
     * Định dạng: +count(4B LE) <<+mailId(4B LE)>>
     */
    private suspend fun handleReadMails(session: GameSession, data: ByteArray) {
        if (data.size < 4) return
        val reader = PacketReader(data)
        val count = reader.readIntLE()
        if (count <= 0 || data.size < 4 + count * 4) return
        val mailIds = (0 until count).map { reader.readIntLE() }
        mailSystem.readMails(session, mailIds)
    }
}
