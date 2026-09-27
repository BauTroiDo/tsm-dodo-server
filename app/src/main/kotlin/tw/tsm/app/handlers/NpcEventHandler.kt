package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.EveFlowController
import tw.tsm.gamelogic.systems.EveTriggerRouter
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * NPC 事件處理器（mainKind = 20）
 *
 * 來源：protocal.lua protocolTable[20]
 *
 * C→S 格式：
 * - subKind 1~5, 7, 8（事件觸發）：+triggerID(2B LE)
 * - subKind 6（事件下一步）：空封包
 * - subKind 9（事件選擇）：+code(1B)
 *
 * 薄包裝層：觸發委派 [EveTriggerRouter]，推進/選擇/恢復委派 [EveFlowController]。
 */
class NpcEventHandler(
    private val triggerRouter: EveTriggerRouter,
    private val flowController: EveFlowController
) : PacketHandler {

    override val mainKind: Int = Opcodes.NPC_EVENT

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            in 1..5, 7, 8 -> {
                val triggerID = if (data.size >= 2) PacketReader(data).readUShortLE() else 0
                triggerRouter.triggerEvent(session, subKind, triggerID)
            }
            6 -> flowController.advanceEvent(session)
            9 -> {
                val code = if (data.isNotEmpty()) PacketReader(data).readUByte() else 0
                flowController.chooseEvent(session, code)
            }
            else -> logger.warn { "未處理的 NPC 事件子協定：subKind=$subKind" }
        }
    }
}
