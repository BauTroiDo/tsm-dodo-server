package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.SoulThingData
import tw.tsm.protocol.PacketWriter

/** DVT protocol 120 Soul packets. */
class SoulResponseSender {
    fun sendEquipResult(session: SessionSender, kind: Int, npcId: Int, sn: Long) {
        val writer = PacketWriter(14)
            .writeByte(Opcodes.SOUL)
            .writeByte(1)
            .writeByte(0)
            .writeByte(kind)
            .writeShortLE(npcId)
            .writeLongLE(sn)
        session.send(writer.toByteArray())
    }

    fun sendError(session: SessionSender, errorCode: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.SOUL)
            .writeByte(255)
            .writeByte(0)
            .writeByte(errorCode)
        session.send(writer.toByteArray())
    }

    fun sendSnapshot(
        session: SessionSender,
        souls: List<SoulThingData>,
        result: Int = RESULT_LOGIN,
    ) {
        require(souls.size <= 0xFFFF)
        val writer = PacketWriter(6 + souls.size * SOUL_WIRE_SIZE)
            .writeByte(Opcodes.SOUL)
            .writeByte(8)
            .writeByte(0)
            .writeShortLE(souls.size)

        for (soul in souls) writeSoul(writer, soul)
        writer.writeByte(result)
        session.send(writer.toByteArray())
    }

    internal fun writeSoul(writer: PacketWriter, soul: SoulThingData) {
        writer.writeLongLE(soul.sn)
            .writeShortLE(soul.itemId)
            .writeByte(soul.element)
            .writeShortLE(soul.equipId)
            .writeShortLE(soul.level)
            .writeByte(soul.starLevel)
        soul.startAtt.forEach { writer.writeShortLE(it) }
        soul.nowAtt.forEach { writer.writeShortLE(it) }
        soul.skills.forEach {
            writer.writeShortLE(it.id)
            writer.writeByte(it.level)
        }
        writer.writeByte(soul.lockFlags)
    }

    companion object {
        const val RESULT_LOGIN = 1
        const val RESULT_GACHA = 2
        const val SOUL_WIRE_SIZE = 53
    }
}
