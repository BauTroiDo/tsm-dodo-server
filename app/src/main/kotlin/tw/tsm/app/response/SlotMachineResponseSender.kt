package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.SlotMachineAward
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/** DVT UISlotMachine responses (mainKind 72). */
class SlotMachineResponseSender {
    /** S72-1: exactly 16 award records. */
    fun sendAwards(session: SessionSender, awards: List<SlotMachineAward>) {
        require(awards.size == 16)
        val writer = PacketWriter()
            .writeByte(Opcodes.SLOT_MACHINE)
            .writeByte(1)
            .writeByte(0)

        for (award in awards.sortedBy { it.awardIndex }) {
            writer.writeShortLE(award.itemId)
            writer.writeByte(award.count)
            writer.writeByte(award.slotIndex)
            writer.writeShortLE(probability(award.awardIndex))
        }
        session.send(writer.toByteArray())
    }

    /** S72-2: result count + winning slot indices. */
    fun sendSpinResult(session: SessionSender, slotIndices: List<Int>) {
        require(slotIndices.isNotEmpty())
        val writer = PacketWriter()
            .writeByte(Opcodes.SLOT_MACHINE)
            .writeByte(2)
            .writeByte(0)
            .writeByte(slotIndices.size)
        slotIndices.forEach { writer.writeByte(it) }
        session.send(writer.toByteArray())
    }

    /** S72-3: slot-machine message kind. */
    fun sendMessage(session: SessionSender, kind: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.SLOT_MACHINE)
                .writeByte(3)
                .writeByte(0)
                .writeByte(kind)
                .toByteArray()
        )
    }

    /** S72-6: client reads the coin balance as UInt16. */
    fun sendCoin(session: SessionSender, coin: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.SLOT_MACHINE)
                .writeByte(6)
                .writeByte(0)
                .writeShortLE(coin.coerceIn(0, 999))
                .toByteArray()
        )
    }

    private fun probability(awardIndex: Int): Int = when (awardIndex) {
        1 -> 1
        in 2..3 -> 2
        in 4..6 -> 5
        in 7..16 -> 8
        else -> 0
    }
}
