package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.FurnaceDismantleEntry
import tw.tsm.domain.data.FurnaceShopSnapshot
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/** Byte-level responses for DVT UIFurnace / protocol 89. */
class FurnaceResponseSender {

    fun sendShopData(
        session: SessionSender,
        snapshot: FurnaceShopSnapshot,
        openUi: Boolean,
    ) {
        var size = 3 + 1 + 8 + 8 + 1 + 1
        snapshot.categories.forEach { category ->
            size += 3 + category.items.size * 6
        }

        val writer = PacketWriter(size)
            .writeByte(Opcodes.FURNACE)
            .writeByte(1)
            .writeByte(0)
            .writeByte(1) // result=success
            .writeDoubleLE(snapshot.baseRate)
            .writeDoubleLE(snapshot.activeRate)
            .writeByte(if (openUi) 1 else 0)
            .writeByte(snapshot.categories.size)

        for (category in snapshot.categories) {
            writer.writeByte(category.kind)
            writer.writeByte(if (category.isCrit) 1 else 0)
            writer.writeByte(category.items.size)
            for (item in category.items) {
                writer.writeShortLE(item.itemId)
                writer.writeIntLE(item.quantity)
            }
        }
        session.send(writer.toByteArray())
    }

    fun sendShopFail(session: SessionSender) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.FURNACE)
                .writeByte(1)
                .writeByte(0)
                .writeByte(2)
                .toByteArray()
        )
    }

    fun sendBuyResult(session: SessionSender, result: Int, itemId: Int = 0) {
        val writer = PacketWriter(if (result == 1) 6 else 4)
            .writeByte(Opcodes.FURNACE)
            .writeByte(2)
            .writeByte(0)
            .writeByte(result)
        if (result == 1) writer.writeShortLE(itemId)
        session.send(writer.toByteArray())
    }

    fun sendDismantleSuccess(
        session: SessionSender,
        entries: List<FurnaceDismantleEntry>,
    ) {
        val writer = PacketWriter(5 + entries.size * 7)
            .writeByte(Opcodes.FURNACE)
            .writeByte(3)
            .writeByte(0)
            .writeByte(0)
            .writeByte(entries.size)
        for (entry in entries) {
            writer.writeShortLE(entry.itemId)
            writer.writeByte(entry.furnaceKind)
            writer.writeIntLE(entry.chipCount)
        }
        session.send(writer.toByteArray())
    }

    fun sendDismantleFail(session: SessionSender, furnaceKind: Int, itemId: Int) {
        session.send(
            PacketWriter(7)
                .writeByte(Opcodes.FURNACE)
                .writeByte(3)
                .writeByte(0)
                .writeByte(1)
                .writeByte(furnaceKind)
                .writeShortLE(itemId)
                .toByteArray()
        )
    }
}
