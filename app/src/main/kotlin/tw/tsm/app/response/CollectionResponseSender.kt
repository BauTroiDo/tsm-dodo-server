package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

class CollectionResponseSender {
    fun sendStyleData(session: SessionSender, flags: Map<Int, Int>, count: Int) {
        val safeCount = count.coerceIn(0, 255)
        val w = PacketWriter(4 + safeCount)
            .writeByte(Opcodes.COLLECTION).writeByte(4).writeByte(0)
            .writeByte(safeCount)
        for (id in 1..safeCount) {
            w.writeByte(flags[id] ?: 0)
        }
        session.send(w.toByteArray())
    }

    fun sendStyleUpdate(session: SessionSender, styleId: Int, flag: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.COLLECTION).writeByte(5).writeByte(0)
                .writeByte(styleId).writeByte(flag)
                .toByteArray()
        )
    }

    fun sendCardEquipData(session: SessionSender, maxEquip: Int, equippedCardIds: List<Int>) {
        val ids = equippedCardIds.take(maxEquip.coerceAtLeast(0))
        val w = PacketWriter(5 + ids.size)
            .writeByte(Opcodes.COLLECTION).writeByte(9).writeByte(0)
            .writeByte(maxEquip.coerceIn(0,255))
            .writeByte(ids.size)
        ids.forEach { w.writeByte(it) }
        session.send(w.toByteArray())
    }


    private fun sendNftResult(session: SessionSender, subKind: Int, success: Boolean, collectionId: Int) {
        val w = PacketWriter(6)
            .writeByte(Opcodes.COLLECTION).writeByte(subKind).writeByte(0)
            .writeByte(if (success) 1 else 0)
        if (success) w.writeShortLE(collectionId)
        session.send(w.toByteArray())
    }

    fun sendNftCardResult(session: SessionSender, subKind: Int, success: Boolean, collectionId: Int) {
        require(subKind == 12 || subKind == 13)
        sendNftResult(session, subKind, success, collectionId)
    }

    fun sendNftMountResult(session: SessionSender, subKind: Int, success: Boolean, npcId: Int) {
        require(subKind == 14 || subKind == 15)
        sendNftResult(session, subKind, success, npcId)
    }

    fun sendNftShine(session: SessionSender, shine: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.COLLECTION).writeByte(21).writeByte(0)
                .writeShortLE(shine.coerceIn(0, 0xFFFF))
                .toByteArray()
        )
    }
    fun sendCardData(session: SessionSender, levels: Map<Int, Int>) {
        val rows = levels.entries
            .filter { it.value > 0 }
            .sortedBy { it.key }
            .take(255)
        val w = PacketWriter(4 + rows.size * 2)
            .writeByte(Opcodes.COLLECTION).writeByte(10).writeByte(0)
            .writeByte(rows.size)
        for ((id, level) in rows) {
            w.writeByte(id).writeByte(level)
        }
        session.send(w.toByteArray())
    }
}
