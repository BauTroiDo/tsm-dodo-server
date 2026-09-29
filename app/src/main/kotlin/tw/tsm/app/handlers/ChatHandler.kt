package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.app.gm.GmCommandProcessor
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.ChatChannel
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý trò chuyện (mainKind = 2)
 *
 * Nguồn: protocal.lua protocolTable[2]
 * C→S subKind 1=toàn kênh 2=kênh nhẹ 3=kênh riêng tư 4=GM 5=kênh tổ đội 6=kênh quân đoàn 7=kênh liên minh
 * S→C subKind 0=hệ thống 1=toàn kênh 2=kênh nhẹ 3=kênh riêng tư 4=GM 5=kênh tổ đội 6=kênh quân đoàn 7=kênh liên minh
 *
 * Định dạng C→S chung (không phải kênh riêng tư): +contentLen(1) +content(Big5) +itemCount(1) +[items...] +npcCount(1) +[npcs...]
 * Định dạng C→S kênh riêng tư: +targetId(8) +targetNameLen(1) +targetName(Big5) +contentLen(1) +content(Big5) +itemCount(1) +npcCount(1)
 */
class ChatHandler(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender,
    private val gmCommandProcessor: GmCommandProcessor
) : PacketHandler {

    override val mainKind: Int = Opcodes.CHAT

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        // Chặn lệnh GM: toàn kênh/kênh nhẹ/kênh GM, tin nhắn bắt đầu bằng "/"
        // Lệnh /設權限 không cần quyền GM (Bootstrap: cho quản trị viên đầu tiên tự nâng cấp mình)
        if (subKind in intArrayOf(1, 2, 4)) {
            val content = readChatContent(data)
            if (content != null && content.startsWith("/")) {
                val isGm = session.gmLevel > 0
                val isOpCommand = content.startsWith("/設權限 ") || content.startsWith("/op ") || content.startsWith("/setperm ")
                if (isGm || isOpCommand) {
                    gmCommandProcessor.execute(session, content)
                    return
                }
            }
        }

        when (subKind) {
            1 -> handleWorldChat(session, data)
            2 -> handleSceneChat(session, data)
            3 -> handleWhisperChat(session, data)
            4 -> handleGMChat(session, data)
            5 -> handleTeamChat(session, data)
            6 -> handleOrganizationChat(session, data)
            7 -> handleAllyChat(session, data)
            else -> logger.warn { "Sub-protocol chat chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:002-001 nói trên toàn kênh
     */
    private fun handleWorldChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[Kênh toàn cầu] ${session.characterName}: $content" }

        // Phát rộng cho tất cả người chơi trực tuyến
        responseSender.sendChatMessage(
            session, ChatChannel.WORLD,
            session.playerId, session.characterName, content
        )
    }

    /**
     * C:002-002 nói trên kênh nhẹ (kênh cảnh)
     */
    private fun handleSceneChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[Kênh lân cận] ${session.characterName}: $content" }

        // Phát rộng cho người chơi cùng bản đồ
        responseSender.sendChatMessageToMap(
            session, ChatChannel.SCENE,
            session.playerId, session.characterName, content,
            session.mapId
        )
    }

    /**
     * C:002-003 nói trên kênh riêng tư
     *
     * Định dạng: +targetId(8) +targetNameLen(1) +targetName(Big5) +contentLen(1) +content(Big5) +itemCount(1) +npcCount(1)
     */
    private fun handleWhisperChat(session: GameSession, data: ByteArray) {
        if (data.size < 10) return  // Tối thiểu: playerId(8) + nameLen(1) + contentLen(1)
        val reader = PacketReader(data)
        val targetPlayerId = reader.readULongLE()
        val targetName = reader.readBig5WithByteLen()
        val content = reader.readUnicodeWithByteLen()

        logger.info { "[Kênh riêng] ${session.characterName} → $targetName: $content" }

        // Tìm người chơi mục tiêu
        val target = worldManager.getPlayerById(targetPlayerId)
            ?: worldManager.getPlayerByName(targetName)

        if (target == null) {
            // TODO: gửi thông báo «đối phương không trực tuyến»
            logger.info { "Mục tiêu nhắn riêng $targetName không trực tuyến" }
            return
        }

        // Gửi cho người chơi mục tiêu
        responseSender.sendWhisperMessage(
            target, session.playerId, session.characterName, content, session.titleId
        )
    }

    /**
     * C:002-004 nói trên kênh GM
     */
    private fun handleGMChat(session: GameSession, data: ByteArray) {
        if (session.gmLevel <= 0) return  // Không phải GM không được dùng kênh GM
        val content = readChatContent(data) ?: return
        logger.info { "[Kênh GM] ${session.characterName}: $content" }
    }

    /**
     * C:002-005 nói trên kênh tổ đội
     */
    private fun handleTeamChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[Kênh tổ đội] ${session.characterName}: $content" }
        // TODO: cài đặt phát rộng tổ đội sau khi hệ thống lập nhóm Phase 9 hoàn tất
    }

    /**
     * C:002-006 nói trên kênh quân đoàn
     */
    private fun handleOrganizationChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[Kênh đoàn đội] ${session.characterName}: $content" }
        // TODO: cài đặt sau khi hệ thống công hội Phase 11 hoàn tất
    }

    /**
     * C:002-007 nói trên kênh liên minh
     */
    private fun handleAllyChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[Kênh minh] ${session.characterName}: $content" }
        // TODO: cài đặt sau khi hệ thống công hội Phase 11 hoàn tất
    }

    /**
     * Đọc nội dung chữ của gói tin trò chuyện
     *
     * Định dạng chung: +contentLen(1) +content(UTF-16LE) +itemCount(1) +[items...] +npcCount(1) +[npcs...]
     * Client dùng WriteStringWithByteL(msg, encoding_Unicode) để gửi (code page 1200 = UTF-16LE)
     * Hiện chỉ đọc chữ, bỏ qua liên kết vật phẩm và NPC
     */
    private fun readChatContent(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val reader = PacketReader(data)
        return reader.readUnicodeWithByteLen()
    }
}
