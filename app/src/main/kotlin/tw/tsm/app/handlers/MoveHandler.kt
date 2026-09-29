package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.MineEncounterSystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý di chuyển (mainKind = 6)
 *
 * Nguồn: protocal.lua protocolTable[6]
 * C→S 0x06-0x01: người chơi di chuyển — direction(1B) + targetX(2B) + targetY(2B)
 * S→C 0x06-0x01: phát rộng di chuyển — roleId(8B) + direction(1B) + destX(2B) + destY(2B)
 * S→C 0x06-0x02: dừng di chuyển — gói tin rỗng
 */
class MoveHandler(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender,
    private val characterRepository: CharacterRepository,
    private val mineEncounterSystem: MineEncounterSystem
) : PacketHandler {

    companion object {
        /** Khoảng chống dội: mỗi 30 giây ghi DB tối đa một lần*/
        private const val POSITION_SAVE_INTERVAL_MS = 30_000L
    }

    override val mainKind: Int = Opcodes.MOVE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleMove(session, data)
            2 -> handleStopMove(session, data)
            else -> logger.warn { "Sub-protocol di chuyển chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:006-001 người chơi di chuyển
     *
     * Định dạng: +direction(1) +targetX(2 LE) +targetY(2 LE)
     */
    private suspend fun handleMove(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val direction = reader.readUByte()
        val targetX = reader.readUShortLE()
        val targetY = reader.readUShortLE()

        val oldX = session.x
        val oldY = session.y

        // Cập nhật toạ độ session
        session.x = targetX
        session.y = targetY
        session.direction = direction

        // phát rộng cho người chơi khác cùng bản đồ
        responseSender.sendPlayerMove(session, session.playerId, direction, targetX, targetY)

        // lưu vị trí chống dội: mỗi 30 giây ghi DB tối đa một lần
        val now = System.currentTimeMillis()
        if (session.roleId > 0 && session.mapId > 0 &&
            now - session.lastPositionSave >= POSITION_SAVE_INTERVAL_MS) {
            session.lastPositionSave = now
            characterRepository.updatePosition(session.roleId.toLong(), session.mapId, targetX, targetY)
        }

        // DVT MineData: EEventTrigger.Mine=7 hiện do server kích hoạt.
        mineEncounterSystem.onPlayerMove(session, oldX, oldY, targetX, targetY)
    }

    /**
     * C:006-002 dừng di chuyển
     *
     * Định dạng: +direction(1) +currentX(2 LE) +currentY(2 LE)
     * Lưu ý: chú thích trong Lua client ghi 'phía client không gửi gói này', nhưng vẫn giữ xử lý
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
