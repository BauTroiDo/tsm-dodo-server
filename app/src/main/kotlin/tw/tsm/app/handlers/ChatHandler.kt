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
 * 聊天處理器（mainKind = 2）
 *
 * 來源：protocal.lua protocolTable[2]
 * C→S subKind 1=全頻 2=輕頻 3=密頻 4=GM 5=隊頻 6=團頻 7=盟頻
 * S→C subKind 0=系統 1=全頻 2=輕頻 3=密頻 4=GM 5=隊頻 6=團頻 7=盟頻
 *
 * C→S 通用格式（非密頻）：+contentLen(1) +content(Big5) +itemCount(1) +[items...] +npcCount(1) +[npcs...]
 * C→S 密頻格式：+targetId(8) +targetNameLen(1) +targetName(Big5) +contentLen(1) +content(Big5) +itemCount(1) +npcCount(1)
 */
class ChatHandler(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender,
    private val gmCommandProcessor: GmCommandProcessor
) : PacketHandler {

    override val mainKind: Int = Opcodes.CHAT

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        // GM 指令攔截：全頻/輕頻/GM頻，訊息以 "/" 開頭
        // /設權限 指令無需 GM 權限（Bootstrap：讓第一個管理員能提升自己）
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
            else -> logger.warn { "未處理的聊天子協定：subKind=$subKind" }
        }
    }

    /**
     * C:002-001 全頻發話
     */
    private fun handleWorldChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[全頻] ${session.characterName}: $content" }

        // 廣播給所有在線玩家
        responseSender.sendChatMessage(
            session, ChatChannel.WORLD,
            session.playerId, session.characterName, content
        )
    }

    /**
     * C:002-002 輕頻發話（場景頻道）
     */
    private fun handleSceneChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[輕頻] ${session.characterName}: $content" }

        // 廣播給同地圖玩家
        responseSender.sendChatMessageToMap(
            session, ChatChannel.SCENE,
            session.playerId, session.characterName, content,
            session.mapId
        )
    }

    /**
     * C:002-003 密頻發話
     *
     * 格式：+targetId(8) +targetNameLen(1) +targetName(Big5) +contentLen(1) +content(Big5) +itemCount(1) +npcCount(1)
     */
    private fun handleWhisperChat(session: GameSession, data: ByteArray) {
        if (data.size < 10) return  // 最小：playerId(8) + nameLen(1) + contentLen(1)
        val reader = PacketReader(data)
        val targetPlayerId = reader.readULongLE()
        val targetName = reader.readBig5WithByteLen()
        val content = reader.readUnicodeWithByteLen()

        logger.info { "[密頻] ${session.characterName} → $targetName: $content" }

        // 找到目標玩家
        val target = worldManager.getPlayerById(targetPlayerId)
            ?: worldManager.getPlayerByName(targetName)

        if (target == null) {
            // TODO：發送「對方不在線」訊息
            logger.info { "密語目標 $targetName 不在線" }
            return
        }

        // 發送給目標玩家
        responseSender.sendWhisperMessage(
            target, session.playerId, session.characterName, content, session.titleId
        )
    }

    /**
     * C:002-004 GM 頻發話
     */
    private fun handleGMChat(session: GameSession, data: ByteArray) {
        if (session.gmLevel <= 0) return  // 非 GM 不可使用 GM 頻
        val content = readChatContent(data) ?: return
        logger.info { "[GM頻] ${session.characterName}: $content" }
    }

    /**
     * C:002-005 隊頻發話
     */
    private fun handleTeamChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[隊頻] ${session.characterName}: $content" }
        // TODO：Phase 9 組隊系統完成後實作隊伍廣播
    }

    /**
     * C:002-006 團頻發話
     */
    private fun handleOrganizationChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[團頻] ${session.characterName}: $content" }
        // TODO：Phase 11 公會系統完成後實作
    }

    /**
     * C:002-007 盟頻發話
     */
    private fun handleAllyChat(session: GameSession, data: ByteArray) {
        val content = readChatContent(data) ?: return
        logger.info { "[盟頻] ${session.characterName}: $content" }
        // TODO：Phase 11 公會系統完成後實作
    }

    /**
     * 讀取聊天封包的文字內容
     *
     * 通用格式：+contentLen(1) +content(UTF-16LE) +itemCount(1) +[items...] +npcCount(1) +[npcs...]
     * 客戶端用 WriteStringWithByteL(msg, encoding_Unicode) 發送（code page 1200 = UTF-16LE）
     * 目前只讀文字，跳過物品和 NPC 連結
     */
    private fun readChatContent(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val reader = PacketReader(data)
        return reader.readUnicodeWithByteLen()
    }
}
