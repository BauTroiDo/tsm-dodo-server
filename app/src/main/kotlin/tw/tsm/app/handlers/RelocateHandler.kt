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
 * Bộ xử lý chuyển cảnh (mainKind = 12)
 *
 * Nguồn: protocal.lua protocolTable[12]
 * C→S 0x0C-0x01: client thông báo đã tải cảnh xong — gói tin trống
 * S→C 0x0C-0x00: server thông báo chuyển cảnh — roleId(8) + sceneId(2) + x(2) + y(2) + tag(2) + instanceId(2) + equipCount(1) + [itemId(2)]... + outfitCount(1) + [outfitId(2)]... + titleId(2)
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
            else -> logger.warn { "Sub-protocol chuyển cảnh chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:012-001 Tải cảnh xong
     *
     * Client gửi gói tin này sau khi tải bản đồ xong.
     * 1. Thông báo WorldManager (đánh dậy EveDoorWarpService đang chờ)
     * 2. Gửi S:022-004 danh sách trống (kích hoạt ContinueNavigation của client)
     * 3. Gửi gói tin xuất hiện của những người chơi khác cùng bản đồ
     */
    private suspend fun handleSceneChangeComplete(session: GameSession) {
        logger.info { "${session.characterName} cảnh ${session.mapId} đã nạp xong" }

        // ★ Thông báo đã tải cảnh xong (đánh dậy deferred.await của EveDoorWarpService)
        worldManager.notifySceneLoadComplete(session.playerId)

        // S:022-004 danh sách trống — kích hoạt ContinueNavigation() của client (dự phòng)
        responseSender.sendMapNpcInit(session, emptyList())

        // Gửi gói tin xuất hiện của người chơi khác cùng bản đồ
        val otherPlayers = worldManager.getPlayersInMap(session.mapId)
        for (other in otherPlayers) {
            if (other !== session && other.loginFinished) {
                responseSender.sendOtherPlayerAppear(session, other)
                responseSender.sendOtherPlayerAppear(other, session)
            }
        }
    }
}
