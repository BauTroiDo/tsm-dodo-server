package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.HotkeyRepository
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 快捷鍵 Handler（mainKind=40）
 *
 * C:040-002 設定快捷鍵：dataIndex(1B) + pageIndex(1B) + keyIndex(1B) + useId(2B LE)
 */
class HotkeyHandler(
    private val hotkeyRepository: HotkeyRepository,
    private val responseSender: ResponseSender
) : PacketHandler {
    override val mainKind: Int = Opcodes.HOTKEY

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            2 -> handleSetHotkey(session, data)
            else -> logger.warn { "未處理的快捷鍵子協定：subKind=$subKind" }
        }
    }

    /**
     * C:040-002 設定快捷鍵
     *
     * 格式：dataIndex(1B) + pageIndex(1B) + keyIndex(1B) + useId(2B LE)
     * dataIndex: 1=PlayerMain(技能), 2=PlayerSub(物品)
     * pageIndex: 頁面索引（通常=1）
     * keyIndex: 鍵位 1-5
     * useId: 技能或物品 ID（0=清除）
     */
    private suspend fun handleSetHotkey(session: GameSession, data: ByteArray) {
        if (data.size < 5) return
        val reader = PacketReader(data)
        val dataIndex = reader.readUByte()
        val pageIndex = reader.readUByte()  // 通常=1，不儲存
        val keyIndex = reader.readUByte()
        val useId = reader.readUShortLE()

        // 驗證範圍
        if (dataIndex !in 1..2 || keyIndex !in 1..5) {
            logger.warn { "${session.characterName} 無效快捷鍵參數：dataIndex=$dataIndex keyIndex=$keyIndex" }
            return
        }

        logger.debug { "${session.characterName} 設定快捷鍵：dataIndex=$dataIndex page=$pageIndex key=$keyIndex useId=$useId" }

        // 存入 DB
        hotkeyRepository.save(session.roleId.toLong(), dataIndex, keyIndex, useId)

        // 回應 S:040-002 result=0（成功）+ 完整參數讓客戶端即時更新 UI
        responseSender.sendHotkeySetResult(session, 0, dataIndex, pageIndex, keyIndex, useId)
    }
}
