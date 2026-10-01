package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.protocol.PacketWriter

/** DVT UIGodskin.lua response serializer for mainKind=108. */
class GodskinResponseSender {
    fun sendSimpleResult(session: SessionSender, subKind: Int, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.GODSKIN)
                .writeByte(subKind)
                .writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    fun sendUpgradeResult(
        session: SessionSender,
        result: Int,
        mythSlot: Int,
        mythThing: ThingData?
    ) {
        val writer = PacketWriter()
            .writeByte(Opcodes.GODSKIN)
            .writeByte(1)
            .writeByte(0)
            .writeByte(result)

        if (result == 1 && mythThing != null) {
            writer.writeShortLE(mythSlot)
                .writeByte(mythThing.mythLevel)
            for (attr in mythThing.myth) {
                writer.writeByte(attr.kind)
                    .writeShortLE(attr.value)
                    .writeShortLE(attr.extra)
            }
        }
        session.send(writer.toByteArray())
    }

    fun sendEnchantResult(
        session: SessionSender,
        result: Int,
        mythSlot: Int,
        runeSlot: Int,
        mythThing: ThingData?
    ) {
        val writer = PacketWriter()
            .writeByte(Opcodes.GODSKIN)
            .writeByte(2)
            .writeByte(0)
            .writeByte(result)

        if (result == 1 && mythThing != null) {
            // DVT UIGodskin.RecEnchantResult reads bagIndex + newLevel, NOT rune slot.
            writer.writeShortLE(mythSlot)
                .writeShortLE(mythThing.mythLevel)
            for (attr in mythThing.myth) {
                writer.writeByte(attr.kind)
                    .writeShortLE(attr.value)
                    .writeShortLE(attr.extra)
            }
        }
        session.send(writer.toByteArray())
    }
}
