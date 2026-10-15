package tw.tsm.app.handlers

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.protocol.PacketWriter

/**
 * DVT Requiem, protocol 111.
 *
 * Handshake-only boundary. No verified boss generation, point accrual,
 * battle resolution or sacrifice payout exists yet. Never invent loot.
 */
class RequiemHandler : PacketHandler {
    override val mainKind: Int = Opcodes.REQUIEM

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            0 -> if (data.isEmpty()) {
                // Initialize points before the client attempts UIRequiem.UpdateUI.
                session.send(RequiemWire.soulPoints(0))
                session.send(RequiemWire.openAllowed())
                session.send(RequiemWire.emptyBossSnapshot())
            }
            2 -> if (data.size == 1) {
                // No persisted bosses/points: paid refresh is not supported.
                session.send(RequiemWire.result(2, 2))
            }
            4 -> if (data.size == 1) {
                // Fail closed: a nonexistent boss must not start a battle.
                session.send(RequiemWire.result(4, 7))
            }
            5 -> if (data.size == 2) {
                // No sacrifice counter or source-backed award yet.
                session.send(RequiemWire.result(5, 2))
            }
        }
    }
}

internal object RequiemWire {
    fun soulPoints(points: Int): ByteArray = PacketWriter(5)
        .writeByte(Opcodes.REQUIEM).writeByte(3).writeByte(0)
        .writeShortLE(points.coerceIn(0, 3000)).toByteArray()

    fun openAllowed(): ByteArray = PacketWriter(4)
        .writeByte(Opcodes.REQUIEM).writeByte(0).writeByte(0).writeByte(1).toByteArray()

    fun emptyBossSnapshot(): ByteArray = PacketWriter(4)
        .writeByte(Opcodes.REQUIEM).writeByte(1).writeByte(0).writeByte(0).toByteArray()

    fun result(subKind: Int, code: Int): ByteArray = PacketWriter(4)
        .writeByte(Opcodes.REQUIEM).writeByte(subKind).writeByte(0).writeByte(code).toByteArray()
}
