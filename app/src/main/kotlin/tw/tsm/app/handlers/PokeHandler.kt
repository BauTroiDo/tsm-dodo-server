package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.data.PokeStateData
import tw.tsm.gamelogic.systems.PokeClaimOutcome
import tw.tsm.gamelogic.systems.PokeSystem
import tw.tsm.protocol.PacketWriter

private val pokeHandlerLogger = KotlinLogging.logger {}

object PokePackets {
    fun sendCoin(session: GameSession, coin: Int) {
        session.send(
            PacketWriter(7)
                .writeByte(PokeHandler.MAIN_KIND).writeByte(1).writeByte(0)
                .writeShortLE(coin.coerceIn(0, PokeSystem.MAX_COIN))
                .toByteArray()
        )
    }

    fun sendAwards(session: GameSession, system: PokeSystem, state: PokeStateData) {
        val rewards = system.rewardSnapshot(state)
        val writer = PacketWriter(4 + rewards.size * 11)
            .writeByte(PokeHandler.MAIN_KIND).writeByte(2).writeByte(0)
            .writeByte(rewards.size)
        for (reward in rewards) {
            writer.writeByte(reward.id)
                .writeByte(reward.kind)
                .writeShortLE(reward.itemId)
                .writeIntLE(reward.count)
                .writeByte(state.openedSlots[reward.id] ?: 0)
                .writeShortLE(system.weightFor(reward))
        }
        session.send(writer.toByteArray())
    }

    fun sendSnapshot(session: GameSession, system: PokeSystem, state: PokeStateData) {
        sendCoin(session, state.coin)
        sendAwards(session, system, state)
    }

    fun sendSuccess(session: GameSession, rewardId: Int, slotIndex: Int) {
        session.send(
            PacketWriter(6)
                .writeByte(PokeHandler.MAIN_KIND).writeByte(3).writeByte(0)
                .writeByte(1).writeByte(rewardId).writeByte(slotIndex)
                .toByteArray()
        )
    }

    fun sendFail(session: GameSession, failKind: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(PokeHandler.MAIN_KIND).writeByte(3).writeByte(0)
                .writeByte(2).writeByte(failKind)
                .toByteArray()
        )
    }
}

/** C:092-003 Trạc/Poke draw. */
class PokeHandler(
    private val system: PokeSystem,
    private val response: ResponseSender,
) : PacketHandler {
    override val mainKind: Int = MAIN_KIND

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        if (subKind != 3 || data.size != 1) return
        if (session.battleId != 0L) {
            PokePackets.sendFail(session, 2)
            return
        }

        val slotIndex = data[0].toInt() and 0xFF
        when (val result = system.claim(session.roleId.toLong(), slotIndex)) {
            is PokeClaimOutcome.Success -> {
                result.changedBagSlots.forEach { (slot, thing) ->
                    response.sendSetBagItem(session, slot, thing, showMsg = false)
                }
                result.currencyBalances.forEach { (kind, value) ->
                    response.sendPlayerCurrency(session, kind, value)
                }
                PokePackets.sendCoin(session, result.state.coin)
                PokePackets.sendSuccess(session, result.reward.id, result.slotIndex)
                pokeHandlerLogger.info {
                    "Poke success roleId=" + session.roleId +
                        " grid=" + result.slotIndex +
                        " reward=" + result.reward.id +
                        " item=" + result.reward.itemId + "x" + result.reward.count +
                        " coin=" + result.state.coin
                }
            }
            PokeClaimOutcome.CoinInsufficient -> PokePackets.sendFail(session, 4)
            PokeClaimOutcome.AlreadyOpened -> PokePackets.sendFail(session, 3)
            PokeClaimOutcome.BoardFull -> PokePackets.sendFail(session, 5)
            PokeClaimOutcome.BagFull -> PokePackets.sendFail(session, 2)
            PokeClaimOutcome.InvalidRequest -> PokePackets.sendFail(session, 6)
            PokeClaimOutcome.UnsupportedReward -> {
                pokeHandlerLogger.error { "Poke unsupported reward roleId=" + session.roleId }
                PokePackets.sendFail(session, 2)
            }
        }
    }

    companion object {
        const val MAIN_KIND = 92
    }
}
