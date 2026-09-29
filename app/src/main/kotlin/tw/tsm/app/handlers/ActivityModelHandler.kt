package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.enums.SessionState
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * DVT ActivityModel compatibility handler (mainKind 124).
 *
 * Current private server does not publish dynamic ActivityModel definitions via S:124-000,
 * so there are no ActivityModel mission ids whose timed progress can be refreshed.
 *
 * Client UIActivityModel.UpdateRedDot() still sends C:124-004 with no payload on login and
 * every 180 seconds. DVT protocolTable[124][4] expects S:124-004:
 *   count(UInt32) + << missionId(UInt32) + finishCount(UInt32) >>
 *
 * Until the full ActivityModel subsystem is implemented, the authoritative response is an
 * empty snapshot (count=0), not fabricated mission ids or progress.
 */
class ActivityModelHandler : PacketHandler {
    override val mainKind: Int = Opcodes.ACTIVITY_MODEL

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            4 -> handleTimedMissionRefresh(session, data)
            else -> logger.warn { "ActivityModel chưa hỗ trợ subKind=${subKind} (${data.size} bytes)" }
        }
    }

    private fun handleTimedMissionRefresh(session: GameSession, data: ByteArray) {
        if (data.isNotEmpty()) {
            logger.warn { "C:124-004 phải không có payload, nhận ${data.size} bytes" }
            return
        }

        session.send(
            PacketWriter(7)
                .writeByte(Opcodes.ACTIVITY_MODEL)
                .writeByte(4)
                .writeByte(0)
                .writeIntLE(0)
                .toByteArray()
        )
    }
}
