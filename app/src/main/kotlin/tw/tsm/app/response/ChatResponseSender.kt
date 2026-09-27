package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.ChatChannel
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketWriter

/**
 * 聊天/移動相關封包序列化
 *
 * mainKind = 2（聊天）, 6（移動）
 * 需要 WorldManager 進行廣播
 */
class ChatResponseSender(private val worldManager: WorldManager) {

    /**
     * S:006-001 廣播玩家移動
     *
     * 格式：+roleId(8) +direction(1) +destX(2 LE) +destY(2 LE)
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
     * S:002-{channel} 聊天訊息（全伺服器廣播）
     *
     * 格式：+senderId(8) +titleId(2) +senderNameLen(1) +senderName(Big5)
     *       +messageLen(1) +message(UTF-16LE) +itemCount(1=0) +npcCount(1=0)
     */
    fun sendChatMessage(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String
    ) {
        val data = buildChatPacket(channel, senderId, senderName, content, sender.titleId)
        worldManager.broadcastToAll(data)
    }

    /** S:002-002 輕頻（場景廣播） */
    fun sendChatMessageToMap(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String,
        mapId: Int
    ) {
        val data = buildChatPacket(channel, senderId, senderName, content, sender.titleId)
        worldManager.broadcastToMap(mapId, data, exclude = sender)
    }

    /** S:002-003 密語 */
    fun sendWhisperMessage(
        target: SessionSender, senderId: Long, senderName: String, content: String,
        titleId: Int = 0
    ) {
        target.send(buildChatPacket(ChatChannel.WHISPER, senderId, senderName, content, titleId))
    }

    /** S:002-000 系統訊息（只發給單一玩家） */
    fun sendSystemMessage(session: SessionSender, content: String) {
        session.send(buildChatPacket(ChatChannel.SYSTEM, 0L, "系統", content, 0))
    }

    /** 組裝聊天封包（共用） */
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
