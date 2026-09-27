package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.WorldManager

private val logger = KotlinLogging.logger {}

/**
 * 場景切換處理器（mainKind = 12）
 *
 * 來源：protocal.lua protocolTable[12]
 * C→S 0x0C-0x01：客戶端通知場景載入完畢 — 空封包
 * S→C 0x0C-0x00：伺服器通知場景切換 — roleId(8) + sceneId(2) + x(2) + y(2) + tag(2) + instanceId(2) + equipCount(1) + [itemId(2)]... + outfitCount(1) + [outfitId(2)]... + titleId(2)
 */
class RelocateHandler(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender
) : PacketHandler {

    /** mainKind = 12 (0x0C) */
    override val mainKind: Int = Opcodes.RELOCATE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleSceneChangeComplete(session)
            else -> logger.warn { "未處理的場景切換子協定：subKind=$subKind" }
        }
    }

    /**
     * C:012-001 場景載入完畢
     *
     * 客戶端完成地圖載入後發送此封包。
     * 1. 通知 WorldManager（喚醒等待中的 EveDoorWarpService）
     * 2. 發送 S:022-004 空列表（觸發客戶端 ContinueNavigation）
     * 3. 發送同地圖其他玩家出現封包
     */
    private suspend fun handleSceneChangeComplete(session: GameSession) {
        logger.info { "${session.characterName} 場景 ${session.mapId} 載入完畢" }

        // ★ 通知場景載入完畢（喚醒 EveDoorWarpService 的 deferred.await）
        worldManager.notifySceneLoadComplete(session.playerId)

        // S:022-004 空列表 — 觸發客戶端 ContinueNavigation()（備用）
        responseSender.sendMapNpcInit(session, emptyList())

        // 發送同地圖其他玩家的出現封包
        val otherPlayers = worldManager.getPlayersInMap(session.mapId)
        for (other in otherPlayers) {
            if (other !== session && other.loginFinished) {
                responseSender.sendOtherPlayerAppear(session, other)
                responseSender.sendOtherPlayerAppear(other, session)
            }
        }
    }
}
