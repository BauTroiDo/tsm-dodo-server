package tw.tsm.app.handlers

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.protocol.PacketWriter

/** Packet payloads from original DVT Lua protocolTable[106]. */
internal object HouseResidencePackets {
    private fun packet(sub: Int) =
        PacketWriter().writeByte(Opcodes.HOUSE_RESIDENCE).writeByte(sub).writeByte(0)

    fun result(session: GameSession, sub: Int, code: Int) {
        session.send(packet(sub).writeByte(code).toByteArray())
    }

    fun entryResult(session: GameSession, code: Int, building: Int, floor: Int) {
        session.send(packet(2).writeByte(code).writeByte(building).writeByte(floor).toByteArray())
    }

    fun starterRoom(session: GameSession, ownerId: Long, building: Int, floor: Int) {
        val w = packet(1)
            .writeLongLE(ownerId)
            .writeByte(building).writeByte(floor)
            .writeBig5WithByteLen("Gia Vien")
            .writeBig5WithByteLen("")
            .writeByte(1) // HouseExpandCondition level 1: building 1, floor 1, unexpanded
            .writeByte(0).writeByte(0) // claimable welfare (disabled until persisted)
        repeat(4) { w.writeShortLE(0) } // appointed warriors
        w.writeIntLE(0) // furniture count
        session.send(w.toByteArray())
    }

    fun starterScore(session: GameSession) {
        val w = packet(7).writeByte(1)
            .writeByte(1).writeByte(1).writeShortLE(0)
        repeat(4) { w.writeShortLE(0) }
        session.send(w.toByteArray())
    }

    fun emptyFurnitureBag(session: GameSession) {
        session.send(packet(8).writeIntLE(0).toByteArray())
    }

    fun totalLevel(session: GameSession, code: Int) {
        session.send(packet(13).writeByte(code).writeByte(if (code == 1) 1 else 0).toByteArray())
    }
}
