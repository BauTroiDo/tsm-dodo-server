package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.gamelogic.systems.GiftHistoryEntry
import tw.tsm.gamelogic.systems.GiftWallEntry
import tw.tsm.gamelogic.systems.HouseBoardView
import tw.tsm.protocol.PacketWriter

/** DVT protocolTable[105] — HouseBoard / Souvenir / Gift Wall. */
class HouseBoardResponseSender {
    fun sendProfile(session: SessionSender, view: HouseBoardView) {
        val profile = view.profile
        val friends = view.friends.take(0xFF)
        val writer = PacketWriter(128)
            .writeByte(Opcodes.HOUSE_BOARD)
            .writeByte(1)
            .writeByte(0)
            .writeLongLE(view.character.id)
            .writeByte(0) // marriage: subsystem chưa có dữ liệu trong server hiện tại
            .writeByte(profile.sign)
            .writeByte(profile.birthMonth)
            .writeByte(profile.birthDay)
            .writeIntLE(profile.charm)
            .writeBig5WithByteLen(profile.signature)
            .writeBig5WithByteLen(profile.impression)
            .writeBig5WithByteLen(profile.favorite)
            .writeBig5WithByteLen(profile.area)
            .writeBig5WithByteLen(profile.hobby)
            .writeByte(friends.size)

        for (friend in friends) {
            writer.writeLongLE(friend.roleId)
                .writeBig5WithByteLen(friend.name)
                .writeIntLE(friend.friendly)
        }
        session.send(writer.toByteArray())
    }

    fun sendProfileSetResult(session: SessionSender, result: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.HOUSE_BOARD)
                .writeByte(2)
                .writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    fun sendGiftResult(session: SessionSender, result: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.HOUSE_BOARD)
                .writeByte(3)
                .writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    fun sendGiftWall(session: SessionSender, entries: List<GiftWallEntry>) {
        val rows = entries.take(0xFF)
        val writer = PacketWriter(4 + rows.size * 16)
            .writeByte(Opcodes.HOUSE_BOARD)
            .writeByte(8)
            .writeByte(0)
            .writeByte(rows.size)

        for (entry in rows) {
            writer.writeBig5WithByteLen(entry.name)
                .writeShortLE(entry.itemId)
                .writeIntLE(entry.quantity)
        }
        session.send(writer.toByteArray())
    }

    fun sendGiftHistory(session: SessionSender, entries: List<GiftHistoryEntry>) {
        val rows = entries.take(100)
        val writer = PacketWriter(4 + rows.size * 24)
            .writeByte(Opcodes.HOUSE_BOARD)
            .writeByte(9)
            .writeByte(0)
            .writeByte(rows.size)

        for (entry in rows) {
            writeGiftHistoryRow(writer, entry)
        }
        session.send(writer.toByteArray())
    }

    fun sendIncomingGift(session: SessionSender, entry: GiftHistoryEntry) {
        val writer = PacketWriter(32)
            .writeByte(Opcodes.HOUSE_BOARD)
            .writeByte(10)
            .writeByte(0)
        writeGiftHistoryRow(writer, entry)
        session.send(writer.toByteArray())
    }

    private fun writeGiftHistoryRow(writer: PacketWriter, entry: GiftHistoryEntry) {
        writer.writeLongLE(entry.senderId)
            .writeBig5WithByteLen(entry.senderName)
            .writeShortLE(entry.itemId)
            .writeByte(entry.quantity)
            .writeDoubleLE(entry.time)
    }
}
