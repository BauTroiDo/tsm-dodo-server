package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 場景/分區處理器（mainKind = 7）
 *
 * 來源：protocal.lua protocolTable[7]
 * C→S 0x07-0x01：要求分區列表 — 空封包
 * C→S 0x07-0x02：切換分區 — areaIndex(2B LE)
 * S→C 0x07-0x00：玩家瞬移 — roleId(8) + sceneId(2) + x(2) + y(2)
 * S→C 0x07-0x01：分區列表 — count(1) + [index(2) + current(2) + max(2)]...
 * S→C 0x07-0x02：換分區結果 — resultCode(1)
 */
class SceneHandler(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.SCENE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleRequestAreaList(session)
            2 -> handleSwitchArea(session, data)
            else -> logger.warn { "未處理的場景子協定：subKind=$subKind" }
        }
    }

    /**
     * C:007-001 要求分區列表
     *
     * 回應 S:007-001 分區列表
     * 單伺服器架構，只回傳一個分區
     */
    private fun handleRequestAreaList(session: GameSession) {
        responseSender.sendAreaList(session, worldManager.onlineCount)
    }

    /**
     * C:007-002 切換分區
     *
     * 格式：+areaIndex(2 LE)
     * 單伺服器架構，固定回傳「同分區無法切換」
     */
    private fun handleSwitchArea(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val areaIndex = reader.readUShortLE()
        logger.info { "${session.characterName} 嘗試切換分區 $areaIndex" }

        // 單伺服器：回傳「不能切換到同一分區」
        responseSender.sendAreaSwitchResult(session, 1)
    }
}
