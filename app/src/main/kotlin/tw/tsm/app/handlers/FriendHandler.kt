package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.FriendSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 好友系統 Handler (mainKind=14)
 *
 * 來源：protocal.lua protocolTable[14]
 * - C:014-004 刪除好友
 * - C:014-005 邀請好友
 * - C:014-006 回覆邀請
 * - C:014-007 送禮
 * - C:014-008 收禮
 * - C:014-009 查詢玩家
 * - C:014-018 一鍵送禮
 * - C:014-019 一鍵收禮
 */
class FriendHandler(
    private val friendSystem: FriendSystem
) : PacketHandler {
    override val mainKind: Int = Opcodes.FRIEND

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            4 -> handleRemove(session, data)
            5 -> handleInvite(session, data)
            6 -> handleReplyInvite(session, data)
            7 -> handleSendGift(session, data)
            8 -> handleReceiveGift(session, data)
            9 -> handleQueryPlayer(session, data)
            18 -> handleSendAllGifts(session, data)
            19 -> handleReceiveAllGifts(session, data)
            else -> logger.warn { "未處理的好友子協定：subKind=$subKind" }
        }
    }

    /**
     * C:014-004 刪除好友
     *
     * 格式：+playerId(8B LE)
     */
    private suspend fun handleRemove(session: GameSession, data: ByteArray) {
        if (data.size < 8) return
        val reader = PacketReader(data)
        val playerId = reader.readULongLE()
        friendSystem.removeFriend(session, playerId)
    }

    /**
     * C:014-005 邀請好友
     *
     * 格式：+nameLen(1B) +name(Big5) +msg(1B)
     */
    private suspend fun handleInvite(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val name = reader.readBig5WithByteLen()
        // msg 忽略（客戶端固定發 0）
        friendSystem.inviteFriend(session, name)
    }

    /**
     * C:014-006 回覆邀請
     *
     * 格式：+playerId(8B LE) +result(1B) [1=接受, 2=拒絕]
     */
    private suspend fun handleReplyInvite(session: GameSession, data: ByteArray) {
        if (data.size < 9) return
        val reader = PacketReader(data)
        val playerId = reader.readULongLE()
        val result = reader.readUByte()
        friendSystem.replyInvite(session, playerId, accept = result == 1)
    }

    /**
     * C:014-007 送禮
     *
     * 格式：+playerId(8B LE)
     */
    private suspend fun handleSendGift(session: GameSession, data: ByteArray) {
        if (data.size < 8) return
        val reader = PacketReader(data)
        val playerId = reader.readULongLE()
        friendSystem.sendGift(session, playerId)
    }

    /**
     * C:014-008 收禮
     *
     * 格式：+playerId(8B LE)
     */
    private suspend fun handleReceiveGift(session: GameSession, data: ByteArray) {
        if (data.size < 8) return
        val reader = PacketReader(data)
        val playerId = reader.readULongLE()
        friendSystem.receiveGift(session, playerId)
    }

    /**
     * C:014-009 查詢玩家資訊
     *
     * 格式：+playerId(8B LE) +nameLen(1B) +name(Big5)
     */
    private suspend fun handleQueryPlayer(session: GameSession, data: ByteArray) {
        if (data.size < 8) return
        val reader = PacketReader(data)
        val playerId = reader.readULongLE()
        friendSystem.queryPlayerInfo(session, playerId)
    }

    /**
     * C:014-018 一鍵送禮
     *
     * 格式：+count(1B) <<+playerId(8B LE)>>
     */
    private suspend fun handleSendAllGifts(session: GameSession, data: ByteArray) {
        if (data.size < 1) return
        val reader = PacketReader(data)
        val count = reader.readUByte()
        if (data.size < 1 + count * 8) return
        val friendIds = (0 until count).map { reader.readULongLE() }
        friendSystem.sendAllGifts(session, friendIds)
    }

    /**
     * C:014-019 一鍵收禮
     *
     * 格式：+count(1B) <<+playerId(8B LE)>>
     */
    private suspend fun handleReceiveAllGifts(session: GameSession, data: ByteArray) {
        if (data.size < 1) return
        val reader = PacketReader(data)
        val count = reader.readUByte()
        if (data.size < 1 + count * 8) return
        val friendIds = (0 until count).map { reader.readULongLE() }
        friendSystem.receiveAllGifts(session, friendIds)
    }
}
