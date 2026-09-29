package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.SlotMachineSpinOutcome
import tw.tsm.gamelogic.systems.SlotMachineSystem

private val slotLogger = KotlinLogging.logger {}

/** DVT UISlotMachine client requests (mainKind 72). */
class SlotMachineHandler(
    private val system: SlotMachineSystem,
    private val response: ResponseSender
) : PacketHandler {
    override val mainKind: Int = Opcodes.SLOT_MACHINE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            2, 3, 4 -> handleSpin(session, subKind, data)
            5 -> handleOpen(session, data)
            else -> slotLogger.warn {
                "SlotMachine C72 chưa hỗ trợ subKind=" + subKind + " (" + data.size + " bytes)"
            }
        }
    }

    private suspend fun handleOpen(session: GameSession, data: ByteArray) {
        if (data.isNotEmpty()) {
            slotLogger.warn { "C72-5 sai size=" + data.size + ", expected=0" }
            return
        }
        val state = system.open(session.roleId.toLong())
        response.sendSlotMachineAwards(session, state.awards)
        response.sendSlotMachineCoin(session, state.coin)
        slotLogger.debug {
            session.characterName + " mở Vòng quay Thôn Thực: coin=" + state.coin
        }
    }

    private suspend fun handleSpin(session: GameSession, subKind: Int, data: ByteArray) {
        if (data.isNotEmpty()) {
            slotLogger.warn { "C72-" + subKind + " sai size=" + data.size + ", expected=0" }
            return
        }
        if (session.battleId != 0L) {
            response.sendSlotMachineMessage(session, 12)
            return
        }

        when (val result = system.spin(session.roleId.toLong(), subKind)) {
            is SlotMachineSpinOutcome.Success -> {
                result.changedBagSlots.forEach { (slot, thing) ->
                    response.sendSetBagItem(session, slot, thing, showMsg = false)
                }
                result.currencyBalances.forEach { (currencyKind, value) ->
                    response.sendPlayerCurrency(session, currencyKind, value)
                }
                response.sendSlotMachineCoin(session, result.state.coin)
                response.sendSlotMachineSpinResult(session, result.resultSlotIndices)
                slotLogger.info {
                    session.characterName + " quay SlotMachine C72-" + subKind +
                        ": coin=" + result.state.coin + " slots=" + result.resultSlotIndices
                }
            }
            SlotMachineSpinOutcome.CoinInsufficient ->
                response.sendSlotMachineMessage(session, 3)
            SlotMachineSpinOutcome.BagFull ->
                response.sendSlotMachineMessage(session, 6)
            SlotMachineSpinOutcome.InvalidBoard -> {
                slotLogger.error { "SlotMachine board/data không hợp lệ cho " + session.characterName }
                response.sendSlotMachineMessage(session, 11)
            }
            SlotMachineSpinOutcome.RewardGrantFailed -> {
                slotLogger.error { "SlotMachine phát thưởng thất bại cho " + session.characterName }
                response.sendSlotMachineMessage(session, 11)
            }
            is SlotMachineSpinOutcome.UnsupportedDirectReward -> {
                slotLogger.error {
                    "SlotMachine reward direct-use chưa có semantics DVT: item=" +
                        result.itemId + " player=" + session.characterName
                }
                response.sendSlotMachineMessage(session, 11)
            }
        }
    }
}
