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
 * Bộ xử lý sự kiện NPC (mainKind = 20)
 *
 * Nguồn: protocal.lua protocolTable[20]
 *
 * Định dạng C→S:
 * - subKind 1~5, 7, 8 (kích hoạt sự kiện): +triggerID(2B LE)
 * - subKind 6 (bước tiếp theo của sự kiện): gói tin rỗng
 * - subKind 9 (lựa chọn sự kiện): +code(1B)
 *
 * Lớp bọc mỏng: kích hoạt giao cho [EveTriggerRouter], thúc đẩy/chọn/hồi phục giao cho [EveFlowController].
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
            else -> logger.warn { "Sub-protocol sự kiện NPC chưa xử lý: subKind=$subKind" }
        }
    }
}
