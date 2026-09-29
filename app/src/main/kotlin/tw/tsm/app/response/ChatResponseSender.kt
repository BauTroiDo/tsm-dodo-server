package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.ChatChannel
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketWriter

/**
 * tuần tự hoá gói tin liên quan trò chuyện/di chuyển
 *
 * mainKind = 2 (trò chuyện), 6 (di chuyển)
 * cần WorldManager để phát rộng
 */
class ChatResponseSender(private val worldManager: WorldManager) {

    /**
     * S:006-001 phát rộng di chuyển của người chơi
     *
     * Định dạng: +roleId(8) +direction(1) +destX(2 LE) +destY(2 LE)
     */
    fun sendPlayerMove(sender: GameSession, playerId: Long, direction: Int, x: Int, y: Int) {
        val data = PacketWriter()
            .writeByte(Opcodes.MOVE).writeByte(1).writeByte(0)
            .writeLongLE(playerId)
            .writeByte(direction)
            .writeShortLE(x)
            .writeShortLE(y)
            .toByteArray()

        worldManager.broadcastToMap(sender.mapId, data, exclude = sender)
    }

    /**
     * S:002-{channel} tin nhắn trò chuyện (phát rộng toàn server)
     *
     * Định dạng: +senderId(8) +titleId(2) +senderNameLen(1) +senderName(Big5)
     *       +messageLen(1) +message(UTF-16LE) +itemCount(1=0) +npcCount(1=0)
     */
    fun sendChatMessage(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String
    ) {
        val data = buildChatPacket(channel, senderId, senderName, content, sender.titleId)
        worldManager.broadcastToAll(data)
    }

    /** S:002-002 kênh nhẹ (phát rộng cảnh)*/
    fun sendChatMessageToMap(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String,
        mapId: Int
    ) {
        val data = buildChatPacket(channel, senderId, senderName, content, sender.titleId)
        worldManager.broadcastToMap(mapId, data, exclude = sender)
    }

    /** S:002-003 nhắn riêng*/
    fun sendWhisperMessage(
        target: SessionSender, senderId: Long, senderName: String, content: String,
        titleId: Int = 0
    ) {
        target.send(buildChatPacket(ChatChannel.WHISPER, senderId, senderName, content, titleId))
    }

    /** S:002-000 tin nhắn hệ thống (chỉ gửi cho một người chơi)*/
    fun sendSystemMessage(session: SessionSender, content: String) {
        session.send(buildChatPacket(ChatChannel.SYSTEM, 0L, "Hệ thống", content, 0))
    }

    /** ghép gói tin trò chuyện (dùng chung)*/
    private fun buildChatPacket(
        channel: ChatChannel, senderId: Long, senderName: String, content: String,
        titleId: Int = 0
    ): ByteArray {
        return PacketWriter()
            .writeByte(Opcodes.CHAT)
            .writeByte(channel.code)
            .writeByte(0)
            .writeLongLE(senderId)
            .writeShortLE(titleId)
            .writeBig5WithByteLen(senderName)
            .writeUnicodeWithByteLen(content)
            .writeByte(0)                            // itemCount = 0
            .writeByte(0)                            // npcCount = 0
            .toByteArray()
    }
}
