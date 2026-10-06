package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.PokeOutcome
import tw.tsm.gamelogic.systems.PokeSystem

private val pokeHandlerLogger = KotlinLogging.logger {}

class PokeHandler(
    private val system: PokeSystem,
    private val response: ResponseSender,
) : PacketHandler {
    override val mainKind: Int = Opcodes.POKE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        if (subKind != 3 || data.size != 1) {
            pokeHandlerLogger.warn { "Unsupported Poke request" }
            return
        }
        if (session.battleId != 0L) {
            response.sendPokeFailure(session, PokeSystem.FAIL_DEFAULT)
            return
        }
        val grid = data[0].toInt() and 255
        when (val out = system.poke(session.roleId.toLong(), grid)) {
            is PokeOutcome.Success -> {
                out.changedBagSlots.forEach { (slot, thing) ->
                    response.sendSetBagItem(session, slot, thing, showMsg = false)
                }
                out.currencyBalances.forEach { (kind, value) ->
                    response.sendPlayerCurrency(session, kind, value)
                }
                response.sendPokeCoin(session, out.state.coin)
                response.sendPokeSuccess(session, out.award.id, grid)
            }
            is PokeOutcome.Fail -> response.sendPokeFailure(session, out.kind)
        }
    }
}
