package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.JiugonggeSystem
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/** DVT C:091 lưới 3x3 / Hằng ngày.*/
class JiugonggeHandler(
    private val system: JiugonggeSystem,
) : PacketHandler {
    override val mainKind: Int = JiugonggeSystem.MAIN_KIND

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            2 -> handleMissionChecks(session, data)
            3 -> handleReward(session, data)
            else -> logger.warn { "Jiugongge subKind chưa hỗ trợ: $subKind" }
        }
    }

    /**
     * C:091-002:
     * count(1) + count * [gridId UInt16 + index Byte + missionId UInt16].
     */
    private suspend fun handleMissionChecks(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val r = PacketReader(data)
        val count = r.readUByte().coerceAtMost(9)
        repeat(count) {
            if (r.remaining < 5) return@repeat
            val gridId = r.readUShortLE()
            val index = r.readUByte()
            val missionId = r.readUShortLE()
            val completed = system.checkMission(session, gridId, index, missionId)
            if (!completed) {
                session.send(
                    PacketWriter()
                        .writeByte(mainKind).writeByte(2).writeByte(0)
                        .writeByte(0)
                        .toByteArray()
                )
            }
        }
    }

    /** C:091-003: gridId UInt16 + rewardIndex Byte + missionId UInt16. */
    private suspend fun handleReward(session: GameSession, data: ByteArray) {
        if (data.size != 5) return
        val r = PacketReader(data)
        val gridId = r.readUShortLE()
        val index = r.readUByte()
        val missionId = r.readUShortLE()
        if (!system.claimReward(session, gridId, index, missionId)) {
            session.send(
                PacketWriter()
                    .writeByte(mainKind).writeByte(3).writeByte(0)
                    .writeByte(0)
                    .toByteArray()
            )
        }
    }
}
