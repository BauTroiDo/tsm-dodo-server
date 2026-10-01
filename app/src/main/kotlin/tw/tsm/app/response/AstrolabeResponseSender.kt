package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/** S:078-001 +isLevelUp(1) +starLv(1)*7. */
class AstrolabeResponseSender {
    fun sendLevels(session: SessionSender, isLevelUp: Boolean, levels: IntArray) {
        require(levels.size == 7)
        val writer = PacketWriter(11)
            .writeByte(Opcodes.ASTROLABE).writeByte(1).writeByte(0)
            .writeBoolean(isLevelUp)
        for (level in levels) {
            writer.writeByte(level.coerceIn(0, 10))
        }
        session.send(writer.toByteArray())
    }
}
