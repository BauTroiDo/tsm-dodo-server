package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.ERolePose
import tw.tsm.domain.constants.HOT_SPRING_MAPS
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * Handler hệ thống biểu cảm/động tác (mainKind = 32)
 *
 * Nguồn: protocal.lua protocolTable[32] (dòng 6969-7010)
 *
 * Định dạng C→S:
 * - subKind 1 (dùng biểu cảm): +ID biểu cảm(1)
 * - subKind 2 (dùng động tác): +ID động tác(1)
 * - subKind 3 (dừng động tác): không có dữ liệu
 */
class EmoteHandler(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.EMOTE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleEmoji(session, data)
            2 -> handleAction(session, data)
            3 -> handleStopAction(session)
            else -> logger.warn { "Sub-protocol biểu cảm/động tác chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:032-001 Dùng biểu tượng biểu cảm
     *
     * Client gửi ID biểu cảm (100-124, UIEmotion.lua:18 → 99+i)
     * Phát rộng S:032-001 cho mọi người chơi cùng bản đồ
     */
    private fun handleEmoji(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val emojiId = reader.readUByte()

        // Kiểm tra hợp lệ: khoảng ID biểu cảm (client gửi 100-124)
        if (emojiId < 100 || emojiId > 124) {
            logger.debug { "${session.characterName} gửi ID biểu cảm không hợp lệ: $emojiId" }
            return
        }

        logger.trace { "${session.characterName} dùng biểu cảm #$emojiId" }

        // Tuần tự hoá trước một lần, phát rộng tới cùng bản đồ (bao gồm chính mình)
        val packet = PacketWriter(12)
            .writeByte(Opcodes.EMOTE).writeByte(1).writeByte(0)
            .writeLongLE(session.playerId)
            .writeByte(emojiId)
            .toByteArray()

        worldManager.broadcastToMap(session.mapId, packet)
    }

    /**
     * C:032-002 Dùng động tác cảm xúc
     *
     * Quy tắc nghiệp vụ (UIAction.lua:68-101):
     * - đang cưỡi ngựa thì cấm
     * - cảnh suối nước nóng (57401/60441) chỉ cho phép Sit/Lie
     */
    private fun handleAction(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val poseId = reader.readUByte()

        // Kiểm tra hợp lệ: ID động tác nằm trong tập hợp hợp lệ
        if (poseId !in ERolePose.USABLE_POSES) {
            logger.debug { "${session.characterName} gửi ID động tác không hợp lệ: $poseId" }
            return
        }

        // Kiểm tra hợp lệ: đang cưỡi ngựa cấm làm động tác
        if (session.mountNpcId != 0) {
            logger.debug { "${session.characterName} đang cưỡi ngựa không dùng được động tác" }
            return
        }

        // Kiểm tra hợp lệ: cảnh suối nước nóng chỉ cho phép Sit/Lie
        if (session.mapId in HOT_SPRING_MAPS && poseId !in ERolePose.HOT_SPRING_ONLY) {
            logger.debug { "${session.characterName} cảnh suối nước nóng chỉ cho phép ngồi/nằm" }
            return
        }

        logger.trace { "${session.characterName} dùng động tác #$poseId" }

        // S:032-002 Định dạng: <<+ID người chơi(8LE) +ID động tác(1)>>
        val packet = PacketWriter(12)
            .writeByte(Opcodes.EMOTE).writeByte(2).writeByte(0)
            .writeLongLE(session.playerId)
            .writeByte(poseId)
            .toByteArray()

        worldManager.broadcastToMap(session.mapId, packet)
    }

    /**
     * C:032-003 Dừng động tác cảm xúc
     *
     * Phát rộng S:032-002 hồi phục tư thế đứng (ERolePose.STAND=8)
     */
    private fun handleStopAction(session: GameSession) {
        logger.trace { "${session.characterName} dừng động tác" }

        val packet = PacketWriter(12)
            .writeByte(Opcodes.EMOTE).writeByte(2).writeByte(0)
            .writeLongLE(session.playerId)
            .writeByte(ERolePose.STAND)
            .toByteArray()

        worldManager.broadcastToMap(session.mapId, packet)
    }
}
