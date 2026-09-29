package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.protocol.PacketWriter
import tw.tsm.protocol.writeThingData

/**
 * Tuần tự hoá gói tin liên quan tổ đội/tướng
 *
 * mainKind = 13（tổ đội）, 15（tướng）, 19（tướng tham chiến）
 */
class PartyPetResponseSender {

    // ── Hệ thống tổ đội (mainKind=13) ──

    /** S:013-001 Thông báo đội trưởng có người chơi xin gia nhập*/
    fun sendPartyJoinRequest(session: SessionSender, requesterId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.PARTY).writeByte(1).writeByte(0)
                .writeLongLE(requesterId)
                .toByteArray()
        )
    }

    /** S:013-003 Thông báo phản hồi của đội trưởng bên yêu cầu*/
    fun sendPartyJoinResult(session: SessionSender, result: Int, leaderName: String) {
        session.send(
            PacketWriter(16)
                .writeByte(Opcodes.PARTY).writeByte(3).writeByte(0)
                .writeByte(result)
                .writeBig5WithByteLen(leaderName)
                .toByteArray()
        )
    }

    /** S:013-004 Thông báo rời đội*/
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

    /** S:013-005 Thông báo gia nhập tổ đội*/
    fun sendPartyMemberJoin(session: SessionSender, leaderId: Long, newMemberId: Long) {
        session.send(
            PacketWriter(19)
                .writeByte(Opcodes.PARTY).writeByte(5).writeByte(0)
                .writeLongLE(leaderId)
                .writeLongLE(newMemberId)
                .toByteArray()
        )
    }

    /** S:013-006 Dữ liệu đầy đủ tổ đội*/
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

    /** S:013-007 Thông báo đặt quân sư*/
    fun sendPartySetAdviser(session: SessionSender, adviserId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.PARTY).writeByte(7).writeByte(0)
                .writeLongLE(adviserId)
                .toByteArray()
        )
    }

    /** S:013-008 Thông báo huỷ quân sư*/
    fun sendPartyRemoveAdviser(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.PARTY).writeByte(8).writeByte(0)
                .toByteArray()
        )
    }

    /** S:013-009 Mời lập đội*/
    fun sendPartyInvite(session: SessionSender, inviterId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.PARTY).writeByte(9).writeByte(0)
                .writeLongLE(inviterId)
                .toByteArray()
        )
    }

    /** S:013-010 Kết quả mời lập đội*/
    fun sendPartyInviteResult(session: SessionSender, result: Int, targetName: String) {
        session.send(
            PacketWriter(16)
                .writeByte(Opcodes.PARTY).writeByte(10).writeByte(0)
                .writeByte(result)
                .writeBig5WithByteLen(targetName)
                .toByteArray()
        )
    }

    /** S:013-011 Dữ liệu quân sư（đồng bộ khi đăng nhập）*/
    fun sendPartyAdviserData(session: SessionSender, adviserIds: List<Long>) {
        val writer = PacketWriter(3 + adviserIds.size * 8)
            .writeByte(Opcodes.PARTY).writeByte(11).writeByte(0)
        for (id in adviserIds) {
            writer.writeLongLE(id)
        }
        session.send(writer.toByteArray())
    }

    /** S:013-013 Thông báo lập đội*/
    fun sendPartyMessage(session: SessionSender, kind: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.PARTY).writeByte(13).writeByte(0)
                .writeByte(kind)
                .toByteArray()
        )
    }

    /** S:013-014 Thông báo đổi đội trưởng*/
    fun sendPartyChangeLeader(session: SessionSender, newLeaderId: Long, memberIds: List<Long>) {
        val writer = PacketWriter(11 + memberIds.size * 8)
            .writeByte(Opcodes.PARTY).writeByte(14).writeByte(0)
            .writeLongLE(newLeaderId)
        for (pid in memberIds) {
            writer.writeLongLE(pid)
        }
        session.send(writer.toByteArray())
    }

    /** S:013-015 Lập đội đổi trạng thái di chuyển*/
    fun sendPartyMoveStatus(session: SessionSender, playerId: Long, status: Int) {
        session.send(
            PacketWriter(12)
                .writeByte(Opcodes.PARTY).writeByte(15).writeByte(0)
                .writeLongLE(playerId)
                .writeByte(status)
                .toByteArray()
        )
    }

    // ── Hệ thống tướng (mainKind=15) ──

    /**
     * S:015-008 Dữ liệu đầy đủ tướng tuỳ thân của nhân vật chính
     *
     * Định dạng: +count(1) <<+followIndex(1) +npcId(2LE) +data...>>
     */
    fun sendFollowNpcFullData(session: SessionSender, pets: List<tw.tsm.domain.valueobjects.FollowNpcData>) {
        val writer = PacketWriter(512)
            .writeByte(Opcodes.FOLLOW_NPC).writeByte(8).writeByte(0)
            .writeByte(pets.size)

        for (pet in pets) {
            writer.writeByte(pet.slotIndex)
            writer.writeShortLE(pet.npcId)
            // Dữ liệu tướng（thứ tự đọc Role.lua dòng 830-866）
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
            // Client gốc TSNPCVN Role.FollowNpcAppear đọc tên bằng
            // ReadString(len, DataManager.encoding_Unicode) => UTF-16LE.
            writer.writeUnicodeWithByteLen(pet.name)
            // Cấp kỹ năng ×3
            writer.writeByte(pet.skill1Lv)
            writer.writeByte(pet.skill2Lv)
            writer.writeByte(pet.skill3Lv)
            // Vật phẩm trang bị ×6（cố định 6 ô ThingData，Role.lua:851-853 không có tiền tố số lượng）
            for (fitType in 1..6) {
                writer.writeThingData(pet.equips[fitType] ?: ThingData())
            }
            // Client gốc TSNPCVN dùng UInt16 cho sublime/hpPill/spPill.
            writer.writeShortLE(pet.ascendPillCount)
            writer.writeBoolean(pet.canUseSpecialSkill)
            writer.writeIntLE(pet.soulId)
            writer.writeShortLE(pet.hpPillCount)
            writer.writeShortLE(pet.spPillCount)
            writer.writeByte(pet.upgradeLv)
        }

        session.send(writer.toByteArray())
    }

    /** S:015-001 Thêm tướng tuỳ thân*/
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

    /** S:015-002 Xoá tướng tuỳ thân*/
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
     * S:015-010 Dữ liệu tướng xe ngựa
     *
     * Định dạng: <<+index(1) +npcId(2) +level(1) +hp(4) +nameLen(1) +name(Big5)>>
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

    /** S:015-011 Xoá tướng xe ngựa*/
    fun sendCartRemove(session: SessionSender, slotIndex: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.FOLLOW_NPC).writeByte(11).writeByte(0)
                .writeByte(slotIndex)
                .toByteArray()
        )
    }

    // ── Tướng tham chiến (mainKind=19) ──

    /** S:019-001 Đăng nhập/khởi tạo tướng tham chiến（Client Role.SetFightNpc UInt16）*/
    fun sendFollowNpcDeployInit(session: SessionSender, npcId: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.FOLLOW_NPC_DEPLOY).writeByte(1).writeByte(0)
                .writeShortLE(npcId)
                .toByteArray()
        )
    }

    /** S:019-004 Tướng tham chiến（phiên bản UInt16）*/
    fun sendFollowNpcDeploy(session: SessionSender, npcId: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.FOLLOW_NPC_DEPLOY).writeByte(4).writeByte(0)
                .writeShortLE(npcId)
                .toByteArray()
        )
    }

    /** S:019-002 Thu hồi tướng（gói trống）*/
    fun sendFollowNpcRecall(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.FOLLOW_NPC_DEPLOY).writeByte(2).writeByte(0)
                .toByteArray()
        )
    }

    // ── Cường hóa tướng (mainKind=74, 8, 15) ──

    /**
     * S:008-011 Dữ liệu tạm thao tác tướng（xem trước thuộc tính tăng cường/thoát thai hoán cốt）
     *
     * Thứ tự dòng thuộc tính: int, atk, def, agi, hpx, spx
     * （client Battle.lua S008_011 đọc lần lượt rồi hiển thị ở xem trước tăng cường UIStatus）
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

    /** S:015-020 Cập nhật sao tăng cường tướng*/
    fun sendPetUpgradeLv(session: SessionSender, followIndex: Int, upgradeLv: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.FOLLOW_NPC).writeByte(20).writeByte(0)
                .writeByte(followIndex)
                .writeByte(upgradeLv)
                .toByteArray()
        )
    }

    /** S:074-002 Tăng cường thành công / S:074-003 Huỷ tăng cường（đều trống payload）*/
    fun sendPetUpgradeResult(session: SessionSender, success: Boolean) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_UPGRADE).writeByte(if (success) 2 else 3).writeByte(0)
                .toByteArray()
        )
    }

    // ── Chuyển sinh tướng (mainKind=44) ──

    /** S:044-001 Thông báo chuyển sinh tướng（+loại(1)，tương ứng string 21070~21079 của client）*/
    fun sendPetRebornMessage(session: SessionSender, msgKind: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.NPC_REINCARNATION).writeByte(1).writeByte(0)
                .writeByte(msgKind)
                .toByteArray()
        )
    }
}
