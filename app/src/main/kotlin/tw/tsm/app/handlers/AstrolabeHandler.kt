package tw.tsm.app.handlers

import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.AstrolabeSystem

/** C:078-001 +nextStar(1). */
class AstrolabeHandler(
    private val astrolabeSystem: AstrolabeSystem,
    private val responseSender: ResponseSender
) : PacketHandler {
    override val mainKind: Int = Opcodes.ASTROLABE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME || subKind != 1 || data.size != 1) return

        val requestedStar = data[0].toInt() and 0xFF
        val result = astrolabeSystem.upgrade(session.roleId.toLong(), requestedStar)

        if (result.success) {
            responseSender.sendSubtractMoney(session, result.goldCost)
            responseSender.sendSetAttribute(
                session,
                EAttribute.ASTROLABE_POINT,
                result.state.natalPoint,
                true
            )
        }

        // DVT isLevelUp chỉ điều khiển presentation; levels luôn là authoritative snapshot.
        responseSender.sendAstrolabeLevels(
            session,
            isLevelUp = result.success,
            levels = result.state.starLevels
        )
    }
}
