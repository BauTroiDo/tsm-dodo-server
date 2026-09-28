package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.DailyProgressSystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 隊伍處理器（mainKind = 13）
 *
 * 來源：protocal.lua protocolTable[13]
 * 處理所有隊伍相關的 C→S 封包。
 */
class PartyHandler(
    private val partySystem: PartySystem,
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender,
    private val dailyProgressSystem: DailyProgressSystem? = null,
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
            else -> logger.trace { "未處理的隊伍 subKind=$subKind" }
        }
    }

    /**
     * C:013-001 要求組隊（隊員 → 隊長）
     *
     * 格式：+playerId(8)
     */
    private fun handleJoinRequest(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        // 自己已在隊伍中
        if (partySystem.isInParty(playerId)) {
            responseSender.sendPartyMessage(session, 2)  // 本隊人數已滿
            return
        }

        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession == null) {
            logger.debug { "組隊要求失敗：對方 $targetId 不在線" }
            return
        }

        // 對方不是隊長且不是獨立玩家（已是別人隊員）
        if (partySystem.isInParty(targetId) && partySystem.getTeamState(targetId) != tw.tsm.domain.enums.TeamState.LEADER) {
            return
        }

        // 對方隊伍已滿
        if (partySystem.isInParty(targetId) && partySystem.getTeamSize(targetId) >= PartySystem.MAX_MEMBERS) {
            responseSender.sendPartyMessage(session, 1)  // 該隊人數已滿
            return
        }

        // 轉發要求給隊長
        responseSender.sendPartyJoinRequest(targetSession, playerId)
        logger.info { "${session.characterName} 要求加入 ${targetSession.characterName} 的隊伍" }
    }

    /**
     * C:013-003 回覆組隊要求（隊長 → 隊員）
     *
     * 格式：+result(1) +playerId(8)
     */
    private suspend fun handleJoinResponse(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val result = reader.readUByte()
        val targetId = reader.readULongLE()

        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession == null) {
            logger.debug { "回覆組隊：對方 $targetId 不在線" }
            return
        }

        if (result != 1) {
            // 拒絕或無回應 — 通知要求方
            responseSender.sendPartyJoinResult(targetSession, result, session.characterName)
            return
        }

        // 同意加入
        if (partySystem.isInParty(playerId)) {
            // 隊長已有隊伍 → 加入
            val addResult = partySystem.addMember(playerId, targetId)
            if (addResult != 0) {
                responseSender.sendPartyMessage(session, addResult)
                return
            }
        } else {
            // 隊長尚未組隊 → 建立新隊伍
            if (!partySystem.createParty(playerId, targetId)) return
        }

        // 通知要求方：同意
        responseSender.sendPartyJoinResult(targetSession, 1, session.characterName)

        // 廣播隊伍新增成員
        broadcastMemberJoin(playerId, targetId)
        dailyProgressSystem?.setConditionFlag(session, DailyProgressSystem.FLAG_FIRST_PARTY)
        dailyProgressSystem?.setConditionFlag(targetSession, DailyProgressSystem.FLAG_FIRST_PARTY)
    }

    /**
     * C:013-004 離開隊伍
     *
     * 格式：+leaderId(8)
     */
    private fun handleLeave(session: GameSession, playerId: Long, data: ByteArray) {
        val result = partySystem.leaveParty(playerId)
        if (result == null) {
            logger.debug { "$playerId 不在隊伍中" }
            return
        }

        if (result.disbanded) {
            // 隊伍解散 → 通知所有成員
            for (pid in result.affectedMembers) {
                val s = worldManager.getPlayerById(pid) ?: continue
                responseSender.sendPartyLeave(s, playerId, s.x, s.y)
            }
            // 額外通知解散訊息
            for (pid in result.affectedMembers) {
                if (pid == playerId) continue
                val s = worldManager.getPlayerById(pid) ?: continue
                responseSender.sendPartyMessage(s, 6)  // 隊伍已解散
            }
        } else {
            // 通知所有剩餘成員
            for (pid in result.affectedMembers) {
                val s = worldManager.getPlayerById(pid) ?: continue
                responseSender.sendPartyLeave(s, playerId, session.x, session.y)
            }
            // 通知離隊者自己
            responseSender.sendPartyLeave(session, playerId, session.x, session.y)
        }

        logger.info { "${session.characterName} 離開隊伍 解散=${result.disbanded}" }
    }

    /**
     * C:013-005 設定軍師
     *
     * 格式：+playerId(8)
     */
    private fun handleSetAdviser(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        if (!partySystem.setAdviser(playerId, targetId)) {
            logger.debug { "設定軍師失敗：隊長=$playerId 目標=$targetId" }
            return
        }

        // 通知所有隊員
        val members = partySystem.getTeamMembers(playerId)
        for (pid in members) {
            val s = worldManager.getPlayerById(pid) ?: continue
            responseSender.sendPartySetAdviser(s, targetId)
        }
    }

    /**
     * C:013-006 取消軍師
     *
     * 格式：+playerId(8)
     */
    private fun handleRemoveAdviser(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        if (!partySystem.removeAdviser(playerId, targetId)) {
            logger.debug { "取消軍師失敗：隊長=$playerId 目標=$targetId" }
            return
        }

        // 通知目標
        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession != null) {
            responseSender.sendPartyRemoveAdviser(targetSession)
        }
    }

    /**
     * C:013-007 邀請組隊（隊長 → 單獨玩家）
     *
     * 格式：+playerId(8)
     */
    private fun handleInvite(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession == null) {
            logger.debug { "邀請組隊：對方 $targetId 不在線" }
            return
        }

        // 對方已在隊伍
        if (partySystem.isInParty(targetId)) {
            responseSender.sendPartyInviteResult(session, 2, targetSession.characterName)  // 拒絕
            return
        }

        // 自己隊伍已滿
        if (partySystem.isInParty(playerId) && partySystem.getTeamSize(playerId) >= PartySystem.MAX_MEMBERS) {
            responseSender.sendPartyMessage(session, 2)  // 本隊人數已滿
            return
        }

        // 轉發邀請
        responseSender.sendPartyInvite(targetSession, playerId)
        logger.info { "${session.characterName} 邀請 ${targetSession.characterName} 加入隊伍" }
    }

    /**
     * C:013-008 回覆邀請組隊
     *
     * 格式：+result(1) +playerId(8)
     */
    private suspend fun handleInviteResponse(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val result = reader.readUByte()
        val inviterId = reader.readULongLE()

        val inviterSession = worldManager.getPlayerById(inviterId)
        if (inviterSession == null) {
            logger.debug { "回覆邀請：邀請者 $inviterId 不在線" }
            return
        }

        if (result != 1) {
            // 拒絕或無回應
            responseSender.sendPartyInviteResult(inviterSession, result, session.characterName)
            return
        }

        // 同意加入
        if (partySystem.isInParty(inviterId)) {
            val addResult = partySystem.addMember(inviterId, playerId)
            if (addResult != 0) {
                responseSender.sendPartyMessage(inviterSession, addResult)
                return
            }
        } else {
            // 邀請者尚未組隊 → 建立新隊伍
            if (!partySystem.createParty(inviterId, playerId)) return
        }

        // 通知邀請者：同意
        responseSender.sendPartyInviteResult(inviterSession, 1, session.characterName)

        // 廣播隊伍新增成員
        broadcastMemberJoin(inviterId, playerId)
        dailyProgressSystem?.setConditionFlag(session, DailyProgressSystem.FLAG_FIRST_PARTY)
        dailyProgressSystem?.setConditionFlag(inviterSession, DailyProgressSystem.FLAG_FIRST_PARTY)
    }

    /**
     * C:013-009 換隊長
     *
     * 格式：+playerId(8)
     */
    private fun handleChangeLeader(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val newLeaderId = reader.readULongLE()

        val memberList = partySystem.changeLeader(playerId, newLeaderId)
        if (memberList == null) {
            logger.debug { "換隊長失敗：$playerId → $newLeaderId" }
            return
        }

        // 通知所有成員（含新隊長）
        val allNotify = memberList + listOf(newLeaderId)
        for (pid in allNotify) {
            val s = worldManager.getPlayerById(pid) ?: continue
            responseSender.sendPartyChangeLeader(s, newLeaderId, memberList)
        }
    }

    /**
     * C:013-010 踢人
     *
     * 格式：+playerId(8)
     */
    private fun handleKick(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        val targetSession = worldManager.getPlayerById(targetId)

        if (!partySystem.kickMember(playerId, targetId)) {
            logger.debug { "踢人失敗：隊長=$playerId 目標=$targetId" }
            return
        }

        // 通知被踢者
        if (targetSession != null) {
            responseSender.sendPartyLeave(targetSession, targetId, targetSession.x, targetSession.y)
        }

        // 通知剩餘隊員
        val remaining = partySystem.getTeamMembers(playerId)
        for (pid in remaining) {
            val s = worldManager.getPlayerById(pid) ?: continue
            responseSender.sendPartyLeave(s, targetId, targetSession?.x ?: 0, targetSession?.y ?: 0)
        }

        logger.info { "${session.characterName} 踢出 ${targetSession?.characterName ?: targetId}" }
    }

    /**
     * C:013-015 組隊中換移動狀態
     *
     * 格式：+playerId(8) +status(1)
     */
    private fun handleMoveStatus(session: GameSession, playerId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()
        val status = reader.readUByte()

        val targetSession = worldManager.getPlayerById(targetId)
        if (targetSession == null) return

        // 轉發給目標
        responseSender.sendPartyMoveStatus(targetSession, targetId, status)
    }

    /**
     * 廣播隊伍新增成員給所有隊員
     */
    private fun broadcastMemberJoin(leaderId: Long, newMemberId: Long) {
        val members = partySystem.getTeamMembers(leaderId)
        for (pid in members) {
            val s = worldManager.getPlayerById(pid) ?: continue
            responseSender.sendPartyMemberJoin(s, leaderId, newMemberId)
        }

        // 發送完整隊伍資料給新成員
        val newSession = worldManager.getPlayerById(newMemberId)
        if (newSession != null) {
            responseSender.sendPartyData(newSession, mapOf(leaderId to members))
            // 發送軍師資料
            val adviserId = partySystem.getAdviserId(leaderId)
            if (adviserId > 0) {
                responseSender.sendPartyAdviserData(newSession, listOf(adviserId))
            }
        }
    }
}
