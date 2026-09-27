package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.protocol.PacketWriter
import tw.tsm.protocol.writeThingData

/**
 * 隊伍/武將相關封包序列化
 *
 * mainKind = 13（隊伍）, 15（武將）, 19（武將出戰）
 */
class PartyPetResponseSender {

    // ── 隊伍系統 (mainKind=13) ──

    /** S:013-001 通知隊長有玩家要求加入 */
    fun sendPartyJoinRequest(session: SessionSender, requesterId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.PARTY).writeByte(1).writeByte(0)
                .writeLongLE(requesterId)
                .toByteArray()
        )
    }

    /** S:013-003 通知要求方隊長的回覆 */
    fun sendPartyJoinResult(session: SessionSender, result: Int, leaderName: String) {
        session.send(
            PacketWriter(16)
                .writeByte(Opcodes.PARTY).writeByte(3).writeByte(0)
                .writeByte(result)
                .writeBig5WithByteLen(leaderName)
                .toByteArray()
        )
    }

    /** S:013-004 離隊通知 */
    fun sendPartyLeave(session: SessionSender, playerId: Long, x: Int, y: Int) {
        session.send(
            PacketWriter(15)
                .writeByte(Opcodes.PARTY).writeByte(4).writeByte(0)
                .writeLongLE(playerId)
                .writeShortLE(x)
                .writeShortLE(y)
                .toByteArray()
        )
    }

    /** S:013-005 加入隊伍通知 */
    fun sendPartyMemberJoin(session: SessionSender, leaderId: Long, newMemberId: Long) {
        session.send(
            PacketWriter(19)
                .writeByte(Opcodes.PARTY).writeByte(5).writeByte(0)
                .writeLongLE(leaderId)
                .writeLongLE(newMemberId)
                .toByteArray()
        )
    }

    /** S:013-006 隊伍完整資料 */
    fun sendPartyData(session: SessionSender, parties: Map<Long, List<Long>>) {
        val writer = PacketWriter(64)
            .writeByte(Opcodes.PARTY).writeByte(6).writeByte(0)
        for ((leaderId, memberList) in parties) {
            writer.writeLongLE(leaderId)
            writer.writeByte(memberList.size)
            for (pid in memberList) {
                writer.writeLongLE(pid)
            }
        }
        session.send(writer.toByteArray())
    }

    /** S:013-007 設定軍師通知 */
    fun sendPartySetAdviser(session: SessionSender, adviserId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.PARTY).writeByte(7).writeByte(0)
                .writeLongLE(adviserId)
                .toByteArray()
        )
    }

    /** S:013-008 取消軍師通知 */
    fun sendPartyRemoveAdviser(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.PARTY).writeByte(8).writeByte(0)
                .toByteArray()
        )
    }

    /** S:013-009 邀請組隊 */
    fun sendPartyInvite(session: SessionSender, inviterId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.PARTY).writeByte(9).writeByte(0)
                .writeLongLE(inviterId)
                .toByteArray()
        )
    }

    /** S:013-010 邀請組隊結果 */
    fun sendPartyInviteResult(session: SessionSender, result: Int, targetName: String) {
        session.send(
            PacketWriter(16)
                .writeByte(Opcodes.PARTY).writeByte(10).writeByte(0)
                .writeByte(result)
                .writeBig5WithByteLen(targetName)
                .toByteArray()
        )
    }

    /** S:013-011 軍師資料（登入同步） */
    fun sendPartyAdviserData(session: SessionSender, adviserIds: List<Long>) {
        val writer = PacketWriter(3 + adviserIds.size * 8)
            .writeByte(Opcodes.PARTY).writeByte(11).writeByte(0)
        for (id in adviserIds) {
            writer.writeLongLE(id)
        }
        session.send(writer.toByteArray())
    }

    /** S:013-013 組隊訊息 */
    fun sendPartyMessage(session: SessionSender, kind: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.PARTY).writeByte(13).writeByte(0)
                .writeByte(kind)
                .toByteArray()
        )
    }

    /** S:013-014 換隊長通知 */
    fun sendPartyChangeLeader(session: SessionSender, newLeaderId: Long, memberIds: List<Long>) {
        val writer = PacketWriter(11 + memberIds.size * 8)
            .writeByte(Opcodes.PARTY).writeByte(14).writeByte(0)
            .writeLongLE(newLeaderId)
        for (pid in memberIds) {
            writer.writeLongLE(pid)
        }
        session.send(writer.toByteArray())
    }

    /** S:013-015 組隊換移動狀態 */
    fun sendPartyMoveStatus(session: SessionSender, playerId: Long, status: Int) {
        session.send(
            PacketWriter(12)
                .writeByte(Opcodes.PARTY).writeByte(15).writeByte(0)
                .writeLongLE(playerId)
                .writeByte(status)
                .toByteArray()
        )
    }

    // ── 武將系統 (mainKind=15) ──

    /**
     * S:015-008 主角隨身武將完整資料
     *
     * 格式：+count(1) <<+followIndex(1) +npcId(2LE) +data...>>
     */
    fun sendFollowNpcFullData(session: SessionSender, pets: List<tw.tsm.domain.valueobjects.FollowNpcData>) {
        val writer = PacketWriter(512)
            .writeByte(Opcodes.FOLLOW_NPC).writeByte(8).writeByte(0)
            .writeByte(pets.size)

        for (pet in pets) {
            writer.writeByte(pet.slotIndex)
            writer.writeShortLE(pet.npcId)
            // 武將資料（Role.lua 行 830-866 讀取順序）
            writer.writeIntLE(pet.exp.toInt())
            writer.writeByte(pet.level)
            writer.writeUIntLE(pet.hp)
            writer.writeShortLE(pet.sp)
            writer.writeShortLE(pet.intelligence)
            writer.writeShortLE(pet.atk)
            writer.writeShortLE(pet.def)
            writer.writeShortLE(pet.agi)
            writer.writeShortLE(pet.hpx)
            writer.writeShortLE(pet.spx)
            writer.writeByte(pet.deathCount)
            writer.writeByte(pet.loyalty)
            writer.writeByte(pet.canGrow)
            writer.writeShortLE(pet.skillPoint)
            writer.writeBig5WithByteLen(pet.name)
            // 技能等級 ×3
            writer.writeByte(pet.skill1Lv)
            writer.writeByte(pet.skill2Lv)
            writer.writeByte(pet.skill3Lv)
            // 裝備物品 ×6（固定 6 格 ThingData，Role.lua:851-853 無數量前綴）
            for (fitType in 1..6) {
                writer.writeThingData(pet.equips[fitType] ?: ThingData())
            }
            writer.writeByte(pet.ascendPillCount)
            writer.writeBoolean(pet.canUseSpecialSkill)
            writer.writeIntLE(pet.soulId)
            writer.writeByte(pet.hpPillCount)
            writer.writeByte(pet.spPillCount)
            writer.writeByte(pet.upgradeLv)
        }

        session.send(writer.toByteArray())
    }

    /** S:015-001 新增隨身武將 */
    fun sendFollowNpcAdd(session: SessionSender, playerId: Long, slotIndex: Int, npcId: Int, canGrow: Int, showMsg: Boolean) {
        session.send(
            PacketWriter(15)
                .writeByte(Opcodes.FOLLOW_NPC).writeByte(1).writeByte(0)
                .writeLongLE(playerId)
                .writeByte(slotIndex)
                .writeIntLE(npcId)
                .writeByte(canGrow)
                .writeBoolean(showMsg)
                .toByteArray()
        )
    }

    /** S:015-002 刪除隨身武將 */
    fun sendFollowNpcRemove(session: SessionSender, playerId: Long, slotIndex: Int, showMsg: Boolean) {
        session.send(
            PacketWriter(13)
                .writeByte(Opcodes.FOLLOW_NPC).writeByte(2).writeByte(0)
                .writeLongLE(playerId)
                .writeByte(slotIndex)
                .writeBoolean(showMsg)
                .toByteArray()
        )
    }

    /**
     * S:015-010 馬車武將資料
     *
     * 格式：<<+index(1) +npcId(2) +level(1) +hp(4) +nameLen(1) +name(Big5)>>
     */
    fun sendCartData(session: SessionSender, pets: List<tw.tsm.domain.valueobjects.FollowNpcData>) {
        val writer = PacketWriter(3 + pets.size * 32)
            .writeByte(Opcodes.FOLLOW_NPC).writeByte(10).writeByte(0)
        for (pet in pets) {
            writer.writeByte(pet.slotIndex)
            writer.writeShortLE(pet.npcId)
            writer.writeByte(pet.level)
            writer.writeUIntLE(pet.hp)
            writer.writeBig5WithByteLen(pet.name)
        }
        session.send(writer.toByteArray())
    }

    /** S:015-011 刪除馬車武將 */
    fun sendCartRemove(session: SessionSender, slotIndex: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.FOLLOW_NPC).writeByte(11).writeByte(0)
                .writeByte(slotIndex)
                .toByteArray()
        )
    }

    // ── 武將出戰 (mainKind=19) ──

    /** S:019-004 武將出戰（UInt16 版本） */
    fun sendFollowNpcDeploy(session: SessionSender, npcId: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.FOLLOW_NPC_DEPLOY).writeByte(4).writeByte(0)
                .writeShortLE(npcId)
                .toByteArray()
        )
    }

    /** S:019-002 武將收回（空包） */
    fun sendFollowNpcRecall(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.FOLLOW_NPC_DEPLOY).writeByte(2).writeByte(0)
                .toByteArray()
        )
    }

    // ── 武將強化 (mainKind=74, 8, 15) ──

    /**
     * S:008-011 武將操作暫存資料（強化/脫胎換骨屬性預覽）
     *
     * 屬性線序：int, atk, def, agi, hpx, spx
     * （客戶端 Battle.lua S008_011 依序讀入後顯示於 UIStatus 強化預覽）
     */
    fun sendPetTempUpgradeData(session: SessionSender, kind: Int, npcId: Int, gains: IntArray) {
        val writer = PacketWriter(12)
            .writeByte(Opcodes.BATTLE).writeByte(11).writeByte(0)
            .writeByte(kind)
            .writeShortLE(npcId)
        for (gain in gains) {
            writer.writeByte(gain)
        }
        session.send(writer.toByteArray())
    }

    /** S:015-020 武將強化星等更新 */
    fun sendPetUpgradeLv(session: SessionSender, followIndex: Int, upgradeLv: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.FOLLOW_NPC).writeByte(20).writeByte(0)
                .writeByte(followIndex)
                .writeByte(upgradeLv)
                .toByteArray()
        )
    }

    /** S:074-002 強化成功 / S:074-003 取消強化（皆空 payload） */
    fun sendPetUpgradeResult(session: SessionSender, success: Boolean) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_UPGRADE).writeByte(if (success) 2 else 3).writeByte(0)
                .toByteArray()
        )
    }

    // ── 武將轉生 (mainKind=44) ──

    /** S:044-001 武將轉生訊息（+種類(1)，對應客戶端 string 21070~21079） */
    fun sendPetRebornMessage(session: SessionSender, msgKind: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.NPC_REINCARNATION).writeByte(1).writeByte(0)
                .writeByte(msgKind)
                .toByteArray()
        )
    }
}
