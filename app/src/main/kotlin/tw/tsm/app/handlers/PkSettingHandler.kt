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
 * PK 設定處理器（mainKind = 33）
 *
 * 來源：protocal.lua protocolTable[33]
 *
 * C→S 格式：
 * - C:033-001 — PK 開關：+PK開關(1)
 * - C:033-002 — 參戰開關：+參戰開關(1)
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
            else -> logger.warn { "未處理的 PK 設定子協定：subKind=$subKind" }
        }
    }

    /**
     * C:033-001 — 切換 PK 開關
     */
    private fun handlePkSwitch(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val enabled = reader.readUByte() != 0
        session.pkEnabled = enabled
        logger.info { "${session.characterName} ${if (enabled) "開啟" else "關閉"} PK 開關" }
        responseSender.sendPkSwitch(session, session.pkEnabled, session.joinEnabled)
    }

    /**
     * C:033-002 — 切換參戰開關
     */
    private fun handleJoinSwitch(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val enabled = reader.readUByte() != 0
        session.joinEnabled = enabled
        logger.info { "${session.characterName} ${if (enabled) "開啟" else "關閉"} 參戰開關" }
        responseSender.sendPkSwitch(session, session.pkEnabled, session.joinEnabled)
    }
}
