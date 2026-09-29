package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý cài đặt PK (mainKind = 33)
 *
 * Nguồn: protocal.lua protocolTable[33]
 *
 * Định dạng C→S:
 * - C:033-001 — công tắc PK: +công tắc PK(1)
 * - C:033-002 — công tắc tham chiến: +công tắc tham chiến(1)
 */
class PkSettingHandler(
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.PK_SETTING

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handlePkSwitch(session, data)
            2 -> handleJoinSwitch(session, data)
            else -> logger.warn { "Sub-protocol cài đặt PK chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:033-001 — bật/tắt công tắc PK
     */
    private fun handlePkSwitch(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val enabled = reader.readUByte() != 0
        session.pkEnabled = enabled
        logger.info { "${session.characterName} ${if (enabled) "開啟" else "關閉"} công tắc PK" }
        responseSender.sendPkSwitch(session, session.pkEnabled, session.joinEnabled)
    }

    /**
     * C:033-002 — bật/tắt công tắc tham chiến
     */
    private fun handleJoinSwitch(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val enabled = reader.readUByte() != 0
        session.joinEnabled = enabled
        logger.info { "${session.characterName} ${if (enabled) "開啟" else "關閉"} công tắc tham chiến" }
        responseSender.sendPkSwitch(session, session.pkEnabled, session.joinEnabled)
    }
}
