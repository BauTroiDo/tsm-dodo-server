package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.FriendDataDto
import tw.tsm.domain.interfaces.FriendInviteDto
import tw.tsm.domain.interfaces.PlayerInfoDto
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter
import tw.tsm.protocol.PlayerInfoCodec.writePlayerCard
import tw.tsm.protocol.PlayerInfoCodec.writeFriendExtra
import tw.tsm.protocol.writeThingData

/**
 * tuần tự hoá gói tin hệ thống bạn bè
 *
 * mainKind = 14
 * Nguồn: protocal.lua protocolTable[14], PlayerInfo.lua
 */
class FriendResponseSender {

    /**
     * S:014-003 kết quả mời kết bạn
     *
     * Định dạng: +nameLen(1B) +name(Big5) +result(1B)
     */
    fun sendFriendInviteResult(session: SessionSender, name: String, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.FRIEND).writeByte(3).writeByte(0)
                .writeBig5WithByteLen(name)
                .writeByte(result)
                .toByteArray()
        )
    }

    /**
     * S:014-004 thông báo xoá bạn bè
     *
     * Định dạng: +playerId(8B LE)
     */
    fun sendFriendRemove(session: SessionSender, playerId: Long) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.FRIEND).writeByte(4).writeByte(0)
                .writeLongLE(playerId)
                .toByteArray()
        )
    }

    /**
     * S:014-005 thêm bạn bè
     *
     * Định dạng: +count(2B LE) <<+playerId(8B LE) +danh thiếp +trực tuyến(1B) +độ tin cậy(2B LE)
     *       +cờ đánh dấu(1B) +thời gian kết giao(8B) +thời gian offline(8B)>>
     */
    fun sendFriendAdd(session: SessionSender, friends: List<FriendDataDto>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.FRIEND).writeByte(5).writeByte(0)
            .writeShortLE(friends.size)

        for (f in friends) {
            writer.writeLongLE(f.roleId)
            writer.writePlayerCard(
                name = f.name, level = f.level, element = f.element,
                turn3Element = f.turn3Element, turn = f.turn, career = f.career,
                sex = f.sex, hair = f.hair, color1 = f.color1, color2 = f.color2
            )
            writer.writeFriendExtra(
                online = f.online, friendly = f.friendly,
                functionFlag = f.functionFlag, addTime = f.addTime,
                offlineTime = f.offlineTime
            )
        }

        session.send(writer.toByteArray())
    }

    /**
     * S:014-006 hộp thư bạn bè đã đầy
     */
    fun sendFriendMailFull(session: SessionSender) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.FRIEND).writeByte(6).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:014-007 tin nhắn bạn bè
     *
     * Định dạng: +kind(1B)
     */
    fun sendFriendMessage(session: SessionSender, kind: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.FRIEND).writeByte(7).writeByte(0)
                .writeByte(kind)
                .toByteArray()
        )
    }

    /**
     * S:014-008 danh sách lời mời bạn bè
     *
     * Định dạng: +count(2B LE) <<+playerId(8B LE) +danh thiếp +msg(1B)>>
     */
    fun sendFriendInvites(session: SessionSender, invites: List<FriendInviteDto>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.FRIEND).writeByte(8).writeByte(0)
            .writeShortLE(invites.size)

        for (inv in invites) {
            writer.writeLongLE(inv.roleId)
            writer.writePlayerCard(
                name = inv.name, level = inv.level, element = inv.element,
                turn3Element = inv.turn3Element, turn = inv.turn, career = inv.career,
                sex = inv.sex, hair = inv.hair, color1 = inv.color1, color2 = inv.color2
            )
            writer.writeByte(0) // msg (cố định 0)
        }

        session.send(writer.toByteArray())
    }

    /**
     * S:014-009 xoá lời mời kết bạn
     *
     * Định dạng: +playerId(8B LE)
     */
    fun sendFriendRemoveInvite(session: SessionSender, playerId: Long) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.FRIEND).writeByte(9).writeByte(0)
                .writeLongLE(playerId)
                .toByteArray()
        )
    }

    /**
     * S:014-012 cập nhật cờ chức năng bạn bè
     *
     * Định dạng: +count(1B) <<+playerId(8B LE) +flag(1B)>>
     */
    fun sendFriendFunctionFlags(session: SessionSender, updates: List<Pair<Long, Int>>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.FRIEND).writeByte(12).writeByte(0)
            .writeByte(updates.size)

        for ((playerId, flag) in updates) {
            writer.writeLongLE(playerId).writeByte(flag)
        }

        session.send(writer.toByteArray())
    }

    /**
     * S:014-013 cập nhật độ tin cậy bạn bè
     *
     * Định dạng: +playerId(8B LE) +friendly(2B LE)
     */
    fun sendFriendFriendly(session: SessionSender, playerId: Long, friendly: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.FRIEND).writeByte(13).writeByte(0)
                .writeLongLE(playerId)
                .writeShortLE(friendly)
                .toByteArray()
        )
    }

    /**
     * S:014-014 thông tin người chơi
     *
     * Định dạng: +playerId(8B) +danh thiếp +maxHp(4B) +maxSp(2B) +lục chỉ số(4B)×6
     *       +equipCount(1B) <<+ThingData>> +outfitCount(1B) <<+itemId(2B)>>
     *       +titleId(2B) +marriage(1B)
     */
    fun sendPlayerInfo(session: SessionSender, data: PlayerInfoDto) {
        val writer = PacketWriter()
            .writeByte(Opcodes.FRIEND).writeByte(14).writeByte(0)
            .writeLongLE(data.roleId)

        writer.writePlayerCard(
            name = data.name, level = data.level, element = data.element,
            turn3Element = data.turn3Element, turn = data.turn, career = data.career,
            sex = data.sex, hair = data.hair, color1 = data.color1, color2 = data.color2
        )

        writer
            .writeIntLE(data.maxHp.toInt())
            .writeShortLE(data.maxSp)
            .writeIntLE(data.intelligence)
            .writeIntLE(data.atk)
            .writeIntLE(data.def)
            .writeIntLE(data.agi)
            .writeIntLE(data.hpx)
            .writeIntLE(data.spx)

        // trang bị
        writer.writeByte(data.equips.size)
        for (equip in data.equips) {
            writer.writeThingData(equip)
        }

        // trang bị ngoại hình
        writer.writeByte(data.outfitItemIds.size)
        for (itemId in data.outfitItemIds) {
            writer.writeShortLE(itemId)
        }

        writer
            .writeShortLE(data.titleId)
            .writeByte(data.marriage)

        session.send(writer.toByteArray())
    }

    /**
     * S:014-015 kết quả nhận quà
     *
     * Định dạng: +result(1B)
     */
    fun sendGiftReceiveResult(session: SessionSender, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.FRIEND).writeByte(15).writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    /**
     * S:014-016 cập nhật trạng thái trực tuyến của bạn bè
     *
     * Định dạng: +playerId(8B LE) +online(1B)
     */
    fun sendFriendOnlineStatus(session: SessionSender, playerId: Long, online: Boolean) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.FRIEND).writeByte(16).writeByte(0)
                .writeLongLE(playerId)
                .writeByte(if (online) 1 else 0)
                .toByteArray()
        )
    }

    /**
     * S:014-017 số lượng bạn bè lịch sử tối đa
     *
     * Định dạng: +count(4B LE)
     */
    fun sendMaxRecordFriendCount(session: SessionSender, count: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.FRIEND).writeByte(17).writeByte(0)
                .writeIntLE(count)
                .toByteArray()
        )
    }
}
