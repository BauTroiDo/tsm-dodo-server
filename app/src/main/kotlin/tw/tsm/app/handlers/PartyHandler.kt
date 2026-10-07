package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.DailyProgressSystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.gamelogic.systems.WorldSimBotSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý tổ đội (mainKind = 13)
 *
 * Nguồn: protocal.lua protocolTable[13]
 * Xử lý tất cả gói tin C→S liên quan đến tổ đội.
 */
class PartyHandler(
    private val partySystem: PartySystem,
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender,
    private val dailyProgressSystem: DailyProgressSystem? = null,
    private val dungeonSystem: tw.tsm.gamelogic.systems.DungeonSystem? = null,
    private val dungeonRooms: tw.tsm.gamelogic.systems.DungeonRoomRegistry? = null,
    private val worldSimBotSystem: WorldSimBotSystem? = null,
) : PacketHandler {

    override val mainKind: Int = Opcodes.PARTY

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        val playerId = session.playerId
        if (playerId <= 0) return

        when (subKind) {
            1 -> handleJoinRequest(session, playerId, data)
            3 -> handleJoinResponse(session, playerId, data)
            4 -> handleLeave(session, playerId, data)
            5 -> handleSetAdviser(session, playerId, data)
            6 -> handleRemoveAdviser(session, playerId, data)
            7 -> handleInvite(session, playerId, data)
            8 -> handleInviteResponse(session, playerId, data)
            9 -> handleChangeLeader(session, playerId, data)
            10 -> handleKick(session, playerId, data)
            15 -> handleMoveStatus(session, playerId, data)
            else -> logger.trace { "Tổ đội chưa xử lý subKind=$subKind" }
        }
    }

    /**
     * C:013-001 yêu cầu lập tổ đội (thành viên → đội trưởng)
     *
     * Định dạng: +playerId(8)
     */
    private fun handleJoinRequest(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        // Mình đã ở trong tổ đội
        if (partySystem.isInParty(playerId)) {
            responseSender.sendPartyMessage(session, 2)  // Số người của đội mình đã đầy
            return
        }

        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession == null) {
            logger.debug { "Yêu cầu tổ đội thất bại: người kia $targetId không trực tuyến" }
            return
        }

        // Đối phương không phải đội trưởng cũng không phải người chơi độc lập (đã là đội viên của người khác)
        if (partySystem.isInParty(targetId) && partySystem.getTeamState(targetId) != tw.tsm.domain.enums.TeamState.LEADER) {
            return
        }

        // Tổ đội của đối phương đã đầy
        if (partySystem.isInParty(targetId) && partySystem.getTeamSize(targetId) >= PartySystem.MAX_MEMBERS) {
            responseSender.sendPartyMessage(session, 1)  // Số người của đội đó đã đầy
            return
        }

        // Chuyển tiếp yêu cầu cho đội trưởng
        responseSender.sendPartyJoinRequest(targetSession, playerId)
        logger.info { "${session.characterName} xin gia nhập tổ đội của ${targetSession.characterName}" }
    }

    /**
     * C:013-003 trả lời yêu cầu tổ đội (đội trưởng → thành viên)
     *
     * Định dạng: +result(1) +playerId(8)
     */
    private suspend fun handleJoinResponse(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val result = reader.readUByte()
        val targetId = reader.readULongLE()

        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession == null) {
            logger.debug { "Phản hồi tổ đội: người kia $targetId không trực tuyến" }
            return
        }

        if (result != 1) {
            // Từ chối hoặc không phản hồi — thông báo cho bên yêu cầu
            responseSender.sendPartyJoinResult(targetSession, result, session.characterName)
            return
        }

        // Đồng ý tham gia
        if (partySystem.isInParty(playerId)) {
            // Đội trưởng đã có tổ đội → tham gia
            val addResult = partySystem.addMember(playerId, targetId)
            if (addResult != 0) {
                responseSender.sendPartyMessage(session, addResult)
                return
            }
        } else {
            // Đội trưởng chưa lập tổ đội → tạo tổ đội mới
            if (!partySystem.createParty(playerId, targetId)) return
        }

        // Thông báo cho bên yêu cầu: đồng ý
        responseSender.sendPartyJoinResult(targetSession, 1, session.characterName)

        // Phát rộng thành viên mới của tổ đội
        broadcastMemberJoin(playerId, targetId)
        dailyProgressSystem?.setConditionFlag(session, DailyProgressSystem.FLAG_FIRST_PARTY)
        dailyProgressSystem?.setConditionFlag(targetSession, DailyProgressSystem.FLAG_FIRST_PARTY)
    }

    /**
     * C:013-004 rời tổ đội
     *
     * Định dạng: +leaderId(8)
     */
    private suspend fun handleLeave(session: GameSession, playerId: Long, data: ByteArray) {
        // Dungeon UI deliberately overloads C:013-004 as "Leave Dungeon".
        // Intercept only while physically inside a DungeonData scene; the system
        // also handles restart/relog recovery through Eve sceneInfo.
        if (dungeonSystem?.exitDungeonFromHud(session) == true) {
            dungeonRooms?.leave(playerId)
            logger.info { "${session.characterName} left dungeon via C:013-004" }
            return
        }

        val result = partySystem.leaveParty(playerId)
        if (result == null) {
            logger.debug { "$playerId không ở trong tổ đội" }
            return
        }

        if (result.disbanded) {
            // Tổ đội giải tán → thông báo cho tất cả thành viên
            for (pid in result.affectedMembers) {
                val s = worldManager.getPlayerById(pid) ?: continue
                responseSender.sendPartyLeave(s, playerId, s.x, s.y)
            }
            // Thông báo thêm tin giải tán
            for (pid in result.affectedMembers) {
                if (pid == playerId) continue
                val s = worldManager.getPlayerById(pid) ?: continue
                responseSender.sendPartyMessage(s, 6)  // Tổ đội đã giải tán
            }
        } else {
            // Thông báo cho tất cả thành viên còn lại
            for (pid in result.affectedMembers) {
                val s = worldManager.getPlayerById(pid) ?: continue
                responseSender.sendPartyLeave(s, playerId, session.x, session.y)
            }
            // Thông báo cho chính người rời đội
            responseSender.sendPartyLeave(session, playerId, session.x, session.y)
        }

        logger.info { "${session.characterName} rời tổ đội giải_tán=${result.disbanded}" }
    }

    /**
     * C:013-005 đặt quân sư
     *
     * Định dạng: +playerId(8)
     */
    private fun handleSetAdviser(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        if (!partySystem.setAdviser(playerId, targetId)) {
            logger.debug { "Đặt quân sư thất bại: đội trưởng=$playerId mục tiêu=$targetId" }
            return
        }

        // Thông báo cho tất cả đội viên
        val members = partySystem.getTeamMembers(playerId)
        for (pid in members) {
            val s = worldManager.getPlayerById(pid) ?: continue
            responseSender.sendPartySetAdviser(s, targetId)
        }
    }

    /**
     * C:013-006 hủy quân sư
     *
     * Định dạng: +playerId(8)
     */
    private fun handleRemoveAdviser(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        if (!partySystem.removeAdviser(playerId, targetId)) {
            logger.debug { "Hủy quân sư thất bại: đội trưởng=$playerId mục tiêu=$targetId" }
            return
        }

        // Thông báo cho mục tiêu
        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession != null) {
            responseSender.sendPartyRemoveAdviser(targetSession)
        }
    }

    /**
     * C:013-007 mời tổ đội (đội trưởng → người chơi đơn lẻ)
     *
     * Định dạng: +playerId(8)
     */
    private suspend fun handleInvite(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        val botName = worldSimBotSystem?.getBotName(targetId)
        if (botName != null) {
            if (!worldSimBotSystem.isBotOnSameMap(targetId, session)) {
                responseSender.sendPartyInviteResult(session, 2, botName)
                return
            }
            if (partySystem.isInParty(targetId)) {
                responseSender.sendPartyInviteResult(session, 2, botName)
                return
            }
            val currentLeaderId = partySystem.getLeaderId(playerId)
            if (currentLeaderId > 0L && partySystem.getTeamSize(currentLeaderId) >= PartySystem.MAX_MEMBERS) {
                responseSender.sendPartyMessage(session, 2)
                return
            }

            val leaderId = currentLeaderId.takeIf { it > 0L } ?: playerId
            val joined = if (currentLeaderId > 0L) {
                partySystem.addMember(leaderId, targetId) == 0
            } else {
                partySystem.createParty(playerId, targetId)
            }
            if (!joined) return

            responseSender.sendPartyInviteResult(session, 1, botName)
            broadcastMemberJoin(leaderId, targetId)
            dailyProgressSystem?.setConditionFlag(session, DailyProgressSystem.FLAG_FIRST_PARTY)
            logger.info { session.characterName + " mời WorldSimBot " + botName + " vào tổ đội" }
            return
        }

        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession == null) {
            logger.debug { "Mời tổ đội: người kia $targetId không trực tuyến" }
            return
        }

        // Đối phương đã ở trong tổ đội
        if (partySystem.isInParty(targetId)) {
            responseSender.sendPartyInviteResult(session, 2, targetSession.characterName)  // Từ chối
            return
        }

        // Tổ đội của mình đã đầy
        if (partySystem.isInParty(playerId) && partySystem.getTeamSize(playerId) >= PartySystem.MAX_MEMBERS) {
            responseSender.sendPartyMessage(session, 2)  // Số người của đội mình đã đầy
            return
        }

        // Chuyển tiếp lời mời
        responseSender.sendPartyInvite(targetSession, playerId)
        logger.info { "${session.characterName} mời ${targetSession.characterName} vào tổ đội" }
    }

    /**
     * C:013-008 Trả lời lời mời lập tổ đội
     *
     * Định dạng: +result(1) +playerId(8)
     */
    private suspend fun handleInviteResponse(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val result = reader.readUByte()
        val inviterId = reader.readULongLE()

        val inviterSession = worldManager.getPlayerById(inviterId)
        if (inviterSession == null) {
            logger.debug { "Phản hồi lời mời: người mời $inviterId không trực tuyến" }
            return
        }

        if (result != 1) {
            // Từ chối hoặc không phản hồi
            responseSender.sendPartyInviteResult(inviterSession, result, session.characterName)
            return
        }

        // Đồng ý tham gia
        if (partySystem.isInParty(inviterId)) {
            val addResult = partySystem.addMember(inviterId, playerId)
            if (addResult != 0) {
                responseSender.sendPartyMessage(inviterSession, addResult)
                return
            }
        } else {
            // Người mời chưa lập tổ đội → tạo tổ đội mới
            if (!partySystem.createParty(inviterId, playerId)) return
        }

        // Thông báo cho người mời: đồng ý
        responseSender.sendPartyInviteResult(inviterSession, 1, session.characterName)

        // Phát rộng thành viên mới của tổ đội
        broadcastMemberJoin(inviterId, playerId)
        dailyProgressSystem?.setConditionFlag(session, DailyProgressSystem.FLAG_FIRST_PARTY)
        dailyProgressSystem?.setConditionFlag(inviterSession, DailyProgressSystem.FLAG_FIRST_PARTY)
    }

    /**
     * C:013-009 Đổi đội trưởng
     *
     * Định dạng: +playerId(8)
     */
    private fun handleChangeLeader(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val newLeaderId = reader.readULongLE()

        val memberList = partySystem.changeLeader(playerId, newLeaderId)
        if (memberList == null) {
            logger.debug { "Đổi đội trưởng thất bại: $playerId → $newLeaderId" }
            return
        }

        // Thông báo cho tất cả thành viên (gồm cả đội trưởng mới)
        val allNotify = memberList + listOf(newLeaderId)
        for (pid in allNotify) {
            val s = worldManager.getPlayerById(pid) ?: continue
            responseSender.sendPartyChangeLeader(s, newLeaderId, memberList)
        }
    }

    /**
     * C:013-010 Đá người
     *
     * Định dạng: +playerId(8)
     */
    private fun handleKick(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        val targetSession = worldManager.getPlayerById(targetId)

        if (!partySystem.kickMember(playerId, targetId)) {
            logger.debug { "Đá người thất bại: đội trưởng=$playerId mục tiêu=$targetId" }
            return
        }

        // Thông báo cho người bị đá
        if (targetSession != null) {
            responseSender.sendPartyLeave(targetSession, targetId, targetSession.x, targetSession.y)
        }

        // Thông báo cho các đội viên còn lại
        val remaining = partySystem.getTeamMembers(playerId)
        for (pid in remaining) {
            val s = worldManager.getPlayerById(pid) ?: continue
            responseSender.sendPartyLeave(s, targetId, targetSession?.x ?: 0, targetSession?.y ?: 0)
        }

        logger.info { "${session.characterName} đá ${targetSession?.characterName ?: targetId}" }
    }

    /**
     * C:013-015 Đổi trạng thái di chuyển khi đang trong tổ đội
     *
     * Định dạng: +playerId(8) +status(1)
     */
    private fun handleMoveStatus(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()
        val status = reader.readUByte()

        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession == null) return

        // Chuyển tiếp cho mục tiêu
        responseSender.sendPartyMoveStatus(targetSession, targetId, status)
    }

    /**
     * Phát rộng thành viên mới của tổ đội cho tất cả đội viên
     */
    private fun broadcastMemberJoin(leaderId: Long, newMemberId: Long) {
        val members = partySystem.getTeamMembers(leaderId)
        for (pid in members) {
            val s = worldManager.getPlayerById(pid) ?: continue
            responseSender.sendPartyMemberJoin(s, leaderId, newMemberId)
        }

        // Gửi toàn bộ dữ liệu tổ đội cho thành viên mới
        val newSession = worldManager.getPlayerById(newMemberId)
        if (newSession != null) {
            responseSender.sendPartyData(newSession, mapOf(leaderId to members))
            // Gửi dữ liệu quân sư
            val adviserId = partySystem.getAdviserId(leaderId)
            if (adviserId > 0) {
                responseSender.sendPartyAdviserData(newSession, listOf(adviserId))
            }
        }
    }
}
