package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.LeaderboardSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Handler bảng xếp hạng (mainKind=88)
 *
 * Nguồn: protocal.lua protocolTable[88]
 * - C:088-001 tra cứu bảng xếp hạng
 */
class LeaderboardHandler(
    private val leaderboardSystem: LeaderboardSystem
) : PacketHandler {
    override val mainKind: Int = Opcodes.LEADERBOARD

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleQuery(session, data)
            else -> logger.warn { "Sub-protocol bảng xếp hạng chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:088-001 tra cứu bảng xếp hạng
     *
     * Định dạng: +leaderboardId(1B) +page(1B)
     */
    private suspend fun handleQuery(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val leaderboardId = reader.readUByte()
        val page = reader.readUByte()
        leaderboardSystem.query(session, leaderboardId, page)
    }
}
