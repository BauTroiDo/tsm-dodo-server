package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.FriendConstants
import tw.tsm.domain.interfaces.*
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.FriendRepository
import tw.tsm.domain.util.OleDate

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống bạn bè — Chịu trách nhiệm quản lý quan hệ bạn bè, lời mời, tặng và nhận quà
 *
 * Nguồn: protocal.lua protocolTable[14], Social.lua
 */
class FriendSystem(
    private val friendRepository: FriendRepository,
    private val characterRepository: CharacterRepository,
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender,
    private val roleCountSystem: RoleCountSystem? = null,
) {

    /**
     * Tải danh sách bạn bè và các lời mời đang chờ xử lý khi đăng nhập
     */
    suspend fun loadAndSend(session: GameSession) {
        val charId = session.roleId.toLong()

        // Tải danh sách bạn bè
        val friends = friendRepository.findFriends(charId)
        if (friends.isNotEmpty()) {
            val dtos = friends.mapNotNull { record ->
                val char = characterRepository.findById(record.friendId) ?: return@mapNotNull null
                val online = worldManager.getPlayerById(record.friendId) != null
                FriendDataDto(
                    roleId = record.friendId,
                    name = char.name,
                    level = char.level,
                    element = char.element,
                    turn3Element = if (char.turn >= 3) char.element else 0,
                    turn = char.turn,
                    career = 0,
                    sex = char.sex,
                    hair = char.hair,
                    color1 = char.color1,
                    color2 = char.color2,
                    online = online,
                    friendly = record.friendly,
                    functionFlag = record.functionFlag,
                    addTime = record.addTime,
                    offlineTime = 0.0
                )
            }
            responseSender.sendFriendAdd(session, dtos)
        }

        // S:014-017 is ECondition.FriendCount in the DVT client.
        // It is a historical maximum (maxRecordFriendCount), not current friends.size.
        syncHistoricalFriendCount(session, charId, friends.size)

        // Tải các lời mời đang chờ xử lý
        val invites = friendRepository.findPendingInvites(charId)
        if (invites.isNotEmpty()) {
            val inviteDtos = invites.mapNotNull { invite ->
                val char = characterRepository.findById(invite.inviterCharId) ?: return@mapNotNull null
                FriendInviteDto(
                    roleId = invite.inviterCharId,
                    name = char.name,
                    level = char.level,
                    element = char.element,
                    turn3Element = if (char.turn >= 3) char.element else 0,
                    turn = char.turn,
                    career = 0,
                    sex = char.sex,
                    hair = char.hair,
                    color1 = char.color1,
                    color2 = char.color2
                )
            }
            responseSender.sendFriendInvites(session, inviteDtos)
        }

        // Thông báo cho tất cả bạn bè đang online rằng mình đã online
        notifyOnlineStatus(charId, online = true)

        logger.info { "Tải dữ liệu bạn bè hoàn tất: ${friends.size} bạn bè, ${invites.size} lời mời (charId=$charId)" }
    }

    /**
     * Mời kết bạn (C:014-005)
     */
    suspend fun inviteFriend(session: GameSession, targetName: String) {
        val charId = session.roleId.toLong()

        // Kiểm tra số lượng bạn bè của bản thân
        val myCount = friendRepository.countFriends(charId)
        if (myCount >= FriendConstants.MAX_FRIENDS) {
            responseSender.sendFriendMessage(session, FriendConstants.MSG_MY_FRIEND_FULL)
            return
        }

        // Tìm người chơi theo tên (hiện tại chỉ có thể tìm người đang online)
        val targetSession = worldManager.getPlayerByName(targetName)
        if (targetSession == null) {
            responseSender.sendFriendMessage(session, FriendConstants.MSG_PLAYER_NOT_FOUND)
            return
        }

        val targetId = targetSession.roleId.toLong()
        if (targetId == charId) return

        // Đã là bạn bè
        if (friendRepository.isFriend(charId, targetId)) {
            responseSender.sendFriendMessage(session, FriendConstants.MSG_DUPLICATE_INVITE)
            return
        }

        // Đã có lời mời từ trước
        if (friendRepository.hasInvite(charId, targetId)) {
            responseSender.sendFriendMessage(session, FriendConstants.MSG_DUPLICATE_INVITE)
            return
        }

        // Kiểm tra số lượng bạn bè của đối phương
        val targetCount = friendRepository.countFriends(targetId)
        if (targetCount >= FriendConstants.MAX_FRIENDS) {
            responseSender.sendFriendMessage(session, FriendConstants.MSG_TARGET_FRIEND_FULL)
            return
        }

        // Thêm lời mời mới
        friendRepository.addInvite(charId, targetId)

        // Thông báo cho người gửi lời mời
        responseSender.sendFriendMessage(session, FriendConstants.MSG_INVITE_SENT)

        // Thông báo cho người được mời (nếu đang online)
        val inviterChar = characterRepository.findById(charId) ?: return
        val inviteDto = FriendInviteDto(
            roleId = charId,
            name = inviterChar.name,
            level = inviterChar.level,
            element = inviterChar.element,
            turn3Element = if (inviterChar.turn >= 3) inviterChar.element else 0,
            turn = inviterChar.turn,
            career = 0,
            sex = inviterChar.sex,
            hair = inviterChar.hair,
            color1 = inviterChar.color1,
            color2 = inviterChar.color2
        )
        responseSender.sendFriendInvites(targetSession, listOf(inviteDto))

        logger.info { "Lời mời kết bạn: $charId → $targetId ($targetName)" }
    }

    /**
     * Trả lời lời mời kết bạn (C:014-006)
     *
     * @param inviterId ID nhân vật của người mời
     * @param accept true=Chấp nhận, false=Từ chối
     */
    suspend fun replyInvite(session: GameSession, inviterId: Long, accept: Boolean) {
        val charId = session.roleId.toLong()

        // Xóa lời mời
        friendRepository.removeInvite(inviterId, charId)
        responseSender.sendFriendRemoveInvite(session, inviterId)

        if (!accept) {
            // Thông báo cho người mời rằng đã bị từ chối
            val myChar = characterRepository.findById(charId) ?: return
            val inviterSession = worldManager.getPlayerById(inviterId)
            if (inviterSession != null) {
                responseSender.sendFriendInviteResult(inviterSession, myChar.name, 2)
            }
            return
        }

        // Chấp nhận — Thiết lập quan hệ bạn bè
        val addTime = OleDate.now()
        friendRepository.addFriend(charId, inviterId, addTime)

        // Thông báo cho cả hai bên rằng đã thêm bạn
        sendFriendAddToPlayer(session, charId, inviterId, addTime)
        val inviterSession = worldManager.getPlayerById(inviterId)
        if (inviterSession != null) {
            sendFriendAddToPlayer(inviterSession, inviterId, charId, addTime)
            val myChar = characterRepository.findById(charId)
            if (myChar != null) {
                responseSender.sendFriendInviteResult(inviterSession, myChar.name, 1)
            }
        }

        // DVT S:014-017 updates Social.maxRecordFriendCount and emits
        // CheckCondition(ECondition.FriendCount). Keep the historical maximum for both sides.
        syncHistoricalFriendCount(session, charId, friendRepository.countFriends(charId))
        if (inviterSession != null) {
            syncHistoricalFriendCount(
                inviterSession,
                inviterId,
                friendRepository.countFriends(inviterId),
            )
        }

        logger.info { "Thiết lập bạn bè: $charId ↔ $inviterId" }
    }

    private fun syncHistoricalFriendCount(
        session: GameSession,
        charId: Long,
        currentCount: Int,
    ): Int {
        val stored = roleCountSystem?.getValue(charId, RoleCountSystem.FRIEND_COUNT) ?: 0
        val historical = maxOf(stored, currentCount)
        if (roleCountSystem != null && historical != stored) {
            val max = roleCountSystem.getMaxValue(charId, RoleCountSystem.FRIEND_COUNT)
            roleCountSystem.setValue(charId, RoleCountSystem.FRIEND_COUNT, historical, max)
        }
        responseSender.sendMaxRecordFriendCount(session, historical)
        return historical
    }

    /**
     * Xóa bạn bè (C:014-004)
     */
    suspend fun removeFriend(session: GameSession, friendId: Long) {
        val charId = session.roleId.toLong()
        friendRepository.removeFriend(charId, friendId)
        responseSender.sendFriendRemove(session, friendId)

        // Thông báo cho đối phương (nếu đang online)
        val friendSession = worldManager.getPlayerById(friendId)
        if (friendSession != null) {
            responseSender.sendFriendRemove(friendSession, charId)
        }

        logger.info { "Xóa bạn bè: $charId ↔ $friendId" }
    }

    /**
     * Tặng quà (C:014-007)
     */
    suspend fun sendGift(session: GameSession, friendId: Long) {
        val charId = session.roleId.toLong()

        if (!friendRepository.isFriend(charId, friendId)) return

        // Cập nhật functionFlag bit1 (Đã tặng quà)
        val record = friendRepository.findFriends(charId).find { it.friendId == friendId } ?: return
        val newFlag = record.functionFlag or 0x01  // bit1 = Đã tặng quà
        friendRepository.updateFunctionFlag(charId, friendId, newFlag)

        // Đối phương được thiết lập bit2 (Đã được tặng quà)
        val targetRecord = friendRepository.findFriends(friendId).find { it.friendId == charId }
        if (targetRecord != null) {
            val targetNewFlag = targetRecord.functionFlag or 0x02
            friendRepository.updateFunctionFlag(friendId, charId, targetNewFlag)
        }

        // Thông báo cho bản thân cập nhật cờ (flag)
        responseSender.sendFriendFunctionFlags(session, listOf(friendId to newFlag))

        // Thông báo cho đối phương (nếu đang online)
        val friendSession = worldManager.getPlayerById(friendId)
        if (friendSession != null && targetRecord != null) {
            responseSender.sendFriendFunctionFlags(
                friendSession,
                listOf(charId to (targetRecord.functionFlag or 0x02))
            )
        }

        // Tăng độ thân mật
        val newFriendly = minOf(record.friendly + 1, FriendConstants.MAX_FRIENDLY)
        if (newFriendly != record.friendly) {
            friendRepository.updateFriendly(charId, friendId, newFriendly)
            friendRepository.updateFriendly(friendId, charId, newFriendly)
            responseSender.sendFriendFriendly(session, friendId, newFriendly)
            if (friendSession != null) {
                responseSender.sendFriendFriendly(friendSession, charId, newFriendly)
            }
        }
    }

    /**
     * Nhận quà (C:014-008)
     */
    suspend fun receiveGift(session: GameSession, friendId: Long) {
        val charId = session.roleId.toLong()

        if (!friendRepository.isFriend(charId, friendId)) return

        val record = friendRepository.findFriends(charId).find { it.friendId == friendId } ?: return

        // Kiểm tra bit2 (Đã được tặng quà)
        if (record.functionFlag and 0x02 == 0) {
            responseSender.sendGiftReceiveResult(session, FriendConstants.GIFT_NOT_RECEIVED)
            return
        }

        // Kiểm tra bit3 (Đã nhận quà)
        if (record.functionFlag and 0x04 != 0) {
            responseSender.sendGiftReceiveResult(session, FriendConstants.GIFT_ALREADY_TAKEN)
            return
        }

        // Thiết lập bit3 (Đã nhận quà)
        val newFlag = record.functionFlag or 0x04
        friendRepository.updateFunctionFlag(charId, friendId, newFlag)
        responseSender.sendFriendFunctionFlags(session, listOf(friendId to newFlag))
        responseSender.sendGiftReceiveResult(session, FriendConstants.GIFT_SUCCESS)
    }

    /**
     * Tặng quà nhanh / Tặng tất cả (C:014-018)
     */
    suspend fun sendAllGifts(session: GameSession, friendIds: List<Long>) {
        for (friendId in friendIds) {
            sendGift(session, friendId)
        }
    }

    /**
     * Nhận quà nhanh / Nhận tất cả (C:014-019)
     */
    suspend fun receiveAllGifts(session: GameSession, friendIds: List<Long>) {
        for (friendId in friendIds) {
            receiveGift(session, friendId)
        }
    }

    /**
     * Truy vấn thông tin người chơi (C:014-009)
     */
    suspend fun queryPlayerInfo(session: GameSession, playerId: Long) {
        val char = characterRepository.findById(playerId) ?: return

        val dto = PlayerInfoDto(
            roleId = playerId,
            name = char.name,
            level = char.level,
            element = char.element,
            turn3Element = if (char.turn >= 3) char.element else 0,
            turn = char.turn,
            career = 0,
            sex = char.sex,
            hair = char.hair,
            color1 = char.color1,
            color2 = char.color2,
            maxHp = 0,
            maxSp = 0,
            intelligence = char.int_,
            atk = char.str,
            def = char.vit,
            hpx = 0,
            spx = 0,
            agi = char.dex,
            equips = emptyList(),
            titleId = char.titleId
        )
        responseSender.sendPlayerInfo(session, dto)
    }

    /**
     * Khi ngắt kết nối, thông báo cho bạn bè trạng thái offline
     */
    suspend fun handleDisconnect(charId: Long) {
        notifyOnlineStatus(charId, online = false)
    }

    // ── Các phương thức nội bộ ──

    /**
     * Gửi thông báo thêm bạn bè cho người chơi được chỉ định
     */
    private suspend fun sendFriendAddToPlayer(
        session: SessionSender, charId: Long, newFriendId: Long, addTime: Double
    ) {
        val char = characterRepository.findById(newFriendId) ?: return
        val online = worldManager.getPlayerById(newFriendId) != null
        val dto = FriendDataDto(
            roleId = newFriendId,
            name = char.name,
            level = char.level,
            element = char.element,
            turn3Element = if (char.turn >= 3) char.element else 0,
            turn = char.turn,
            career = 0,
            sex = char.sex,
            hair = char.hair,
            color1 = char.color1,
            color2 = char.color2,
            online = online,
            friendly = 0,
            functionFlag = 0,
            addTime = addTime,
            offlineTime = 0.0
        )
        responseSender.sendFriendAdd(session, listOf(dto))
    }

    /**
     * Thông báo trạng thái online của bản thân cho tất cả bạn bè đang online
     */
    private suspend fun notifyOnlineStatus(charId: Long, online: Boolean) {
        val friends = friendRepository.findFriends(charId)
        for (friend in friends) {
            val friendSession = worldManager.getPlayerById(friend.friendId)
            if (friendSession != null) {
                responseSender.sendFriendOnlineStatus(friendSession, charId, online)
            }
        }
    }
}