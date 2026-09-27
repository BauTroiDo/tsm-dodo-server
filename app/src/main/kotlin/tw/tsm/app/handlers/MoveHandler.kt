package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 移動處理器（mainKind = 6）
 *
 * 來源：protocal.lua protocolTable[6]
 * C→S 0x06-0x01：玩家移動 — direction(1B) + targetX(2B) + targetY(2B)
 * S→C 0x06-0x01：廣播移動 — roleId(8B) + direction(1B) + destX(2B) + destY(2B)
 * S→C 0x06-0x02：停止移動 — 空封包
 */
class MoveHandler(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender,
    private val characterRepository: CharacterRepository
) : PacketHandler {

    companion object {
        /** 防抖間隔：每 30 秒最多寫一次 DB */
        private const val POSITION_SAVE_INTERVAL_MS = 30_000L
    }

    override val mainKind: Int = Opcodes.MOVE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleMove(session, data)
            2 -> handleStopMove(session, data)
            else -> logger.warn { "未處理的移動子協定：subKind=$subKind" }
        }
    }

    /**
     * C:006-001 玩家移動
     *
     * 格式：+direction(1) +targetX(2 LE) +targetY(2 LE)
     */
    private suspend fun handleMove(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val direction = reader.readUByte()
        val targetX = reader.readUShortLE()
        val targetY = reader.readUShortLE()

        // 更新 session 座標
        session.x = targetX
        session.y = targetY
        session.direction = direction

        // 廣播給同地圖其他玩家
        responseSender.sendPlayerMove(session, session.playerId, direction, targetX, targetY)

        // 防抖位置儲存：每 30 秒最多寫一次 DB
        val now = System.currentTimeMillis()
        if (session.roleId > 0 && session.mapId > 0 &&
            now - session.lastPositionSave >= POSITION_SAVE_INTERVAL_MS) {
            session.lastPositionSave = now
            characterRepository.updatePosition(session.roleId.toLong(), session.mapId, targetX, targetY)
        }
    }

    /**
     * C:006-002 停止移動
     *
     * 格式：+direction(1) +currentX(2 LE) +currentY(2 LE)
     * 註：客戶端 Lua 註解「C端不送這一條」，但保留處理
     */
    private fun handleStopMove(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val direction = reader.readUByte()
        val currentX = reader.readUShortLE()
        val currentY = reader.readUShortLE()

        session.x = currentX
        session.y = currentY
        session.direction = direction
    }
}
