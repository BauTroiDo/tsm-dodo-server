package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.gamelogic.systems.LimitTimeDungeonSystem

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
    private val responseSender: ResponseSender,
    private val groundItemManager: tw.tsm.gamelogic.systems.GroundItemManager? = null,
    private val limitTimeDungeonSystem: LimitTimeDungeonSystem? = null,
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

        // LimitFightArea: vào mới hoặc relog/restart trong scene giới hạn đều khôi phục timer.
        limitTimeDungeonSystem?.onSceneReady(session)

        // S:022-004 danh sách trống — kích hoạt ContinueNavigation() của client (dự phòng)
        responseSender.sendMapNpcInit(session, emptyList())

        // DVT S:023-004 includes explicit indices, including static Eve Goods.
        groundItemManager?.let { ground ->
            val writer = tw.tsm.protocol.PacketWriter()
                .writeByte(Opcodes.SHOP).writeByte(4).writeByte(0)
            for (item in ground.getItems(session.mapId)) {
                writer.writeByte(item.kind).writeShortLE(item.index).writeIntLE(item.value)
                    .writeShortLE(item.x).writeShortLE(item.y)
            }
            session.send(writer.toByteArray())
        }

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
