package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý trạng thái người chơi (mainKind = 5)
 *
 * Xử lý các gói tin client gửi lên liên quan đến trạng thái người chơi.
 * Hiện hỗ trợ:
 * - C:005-008 xem xong hướng dẫn người mới
 * - C:005-012 đổi danh hiệu
 */
class PlayerStatusHandler(
    private val bitFlagSystem: BitFlagSystem,
    private val responseSender: ResponseSender,
    private val characterRepository: CharacterRepository,
    private val worldManager: WorldManager
) : PacketHandler {
    override val mainKind: Int = Opcodes.PLAYER_STATUS

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        when (subKind) {
            8 -> handleTeachComplete(session, data)
            12 -> handleChangeTitle(session, data)
            else -> logger.trace { "Chưa xử lý C:005-$subKind" }
        }
    }

    /**
     * C:005-008 Xem xong hướng dẫn người mới
     *
     * Định dạng: +guideId(1B)
     * Client UITeach.lua gửi khi hoàn thành hướng dẫn.
     * Server tra bitFlagId tương ứng, ghi vào cơ sở dữ liệu và trả về S:081-001.
     */
    private suspend fun handleTeachComplete(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val guideId = reader.readUByte()

        val charId = session.roleId.toLong()
        if (charId <= 0) return

        val bitFlagId = bitFlagSystem.completeTeach(charId, guideId)
        if (bitFlagId != null) {
            responseSender.sendBitFlagChanges(session, listOf(bitFlagId to true))
        }
    }

    /**
     * C:005-012 Đổi danh hiệu
     *
     * Định dạng: +titleId(2B UInt16 LE)
     * titleId=0 có nghĩa là tháo danh hiệu.
     * Cập nhật session + DB và phát rộng S:005-012 cho mọi người chơi cùng bản đồ.
     */
    private suspend fun handleChangeTitle(session: GameSession, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        if (data.size < 2) return
        if (session.battleId != 0L) return  // Đang chiến đấu không được đổi danh hiệu

        val reader = PacketReader(data)
        val titleId = reader.readUShortLE()

        // Cập nhật trạng thái session
        session.titleId = titleId

        // Lưu bền dữ liệu vào DB
        characterRepository.updateTitleId(session.roleId.toLong(), titleId)

        // Phát rộng S:005-012 cho mọi người chơi cùng bản đồ (gồm cả mình)
        val players = worldManager.getPlayersInMap(session.mapId, session.instanceId)
        for (player in players) {
            responseSender.sendTitleChange(player, session.playerId, titleId)
        }

        logger.info { "${session.characterName} đổi danh hiệu: $titleId" }
    }
}
