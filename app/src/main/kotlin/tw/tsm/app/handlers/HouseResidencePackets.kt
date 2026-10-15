package tw.tsm.app.handlers

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.HouseResidenceSettings
import tw.tsm.domain.interfaces.repositories.HouseFurniturePlacement
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

    fun starterRoom(session: GameSession, ownerId: Long, building: Int, floor: Int, settings: HouseResidenceSettings, placed: List<HouseFurniturePlacement>) {
        val w = packet(1)
            .writeLongLE(ownerId)
            .writeByte(building).writeByte(floor)
            .writeBig5WithByteLen(settings.name)
            .writeBig5WithByteLen(settings.signature)
            .writeByte(1) // HouseExpandCondition level 1: building 1, floor 1, unexpanded
            .writeByte(0).writeByte(0) // claimable welfare (disabled until persisted)
        repeat(4) { w.writeShortLE(0) } // appointed warriors
        w.writeIntLE(placed.size)
        for (f in placed) w.writeShortLE(f.furnitureId).writeByte(f.x).writeByte(f.y)
            .writeByte(f.z).writeByte(f.direction)
        session.send(w.toByteArray())
    }

    fun starterScore(session: GameSession) {
        val w = packet(7).writeByte(1)
            .writeByte(1).writeByte(1).writeShortLE(0)
        repeat(4) { w.writeShortLE(0) }
        session.send(w.toByteArray())
    }

    fun furnitureBag(session: GameSession, owned: Map<Int, Int>, placed: List<HouseFurniturePlacement>) {
        val use = placed.groupingBy { it.furnitureId }.eachCount()
        val items = owned.entries.filter { it.key in 1..65535 && it.value > 0 }
            .sortedBy { it.key }.take(65535)
        val w = packet(8).writeIntLE(items.size)
        for ((id, total) in items) {
            w.writeShortLE(id).writeByte(total.coerceAtMost(255))
                .writeByte(use.getOrDefault(id, 0).coerceAtMost(255))
        }
        session.send(w.toByteArray())
    }

    fun totalLevel(session: GameSession, code: Int) {
        session.send(packet(13).writeByte(code).writeByte(if (code == 1) 1 else 0).toByteArray())
    }
}
