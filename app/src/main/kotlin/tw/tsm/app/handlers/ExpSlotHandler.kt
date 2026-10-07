package tw.tsm.app.handlers

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.ExpSlotSystem
import tw.tsm.protocol.PacketWriter

/** DVT warrior EXP slot (UIExpSlotMachine / mainKind 76). */
class ExpSlotHandler(
    private val system: ExpSlotSystem,
) : PacketHandler {
    override val mainKind: Int = Opcodes.EXP_SLOT

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            5 -> if (data.isEmpty()) sendJackpot(session, system.jackpotExp())
        }
    }

    fun sendJackpot(session: GameSession, exp: Long) {
        session.send(
            PacketWriter(7)
                .writeByte(Opcodes.EXP_SLOT).writeByte(5).writeByte(0)
                .writeIntLE(exp.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
                .toByteArray()
        )
    }
}
