package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender

private val logger = KotlinLogging.logger {}

/**
 * Dịch vụ chiến đấu Eve
 *
 * Nhiệm vụ: Thực thi resultType=3 (Kích hoạt chiến đấu).
 *
 * Chiến đấu là một quy trình bất đồng bộ: Sau khi kích hoạt, sự kiện sẽ tạm dừng (phase=AwaitingBattle),
 * khi trận chiến kết thúc sẽ được khôi phục bởi BattleManager → EveFlowController.resumeAfterBattle.
 *
 * Được tách ra từ NpcEventHandler.handleBattleTrigger().
 */
class EveBattleService(
    private val responseSender: ResponseSender,
    private val stateMachine: EveEventStateMachine,
    private val battleManager: BattleManager,
    private val mapTerrains: Map<Int, Int>,
    private val eveData: Map<Int, SceneEveData>
) {

    /**
     * Kích hoạt sự kiện chiến đấu
     *
     * @param session       session người chơi
     * @param result        EveResult có resultType=3 (resultMeanNo = key của fightDatas)
     * @param onNoFightData Callback khi không tìm thấy cấu hình trận chiến (Logic advance + dispatch do EveFlowController cung cấp)
     */
    suspend fun triggerBattle(session: GameSession, result: EveResult, onNoFightData: suspend () -> Unit) {
        val fightDataKey = result.resultMeanNo
        val fightData = eveData[session.mapId]?.fightDatas?.get(fightDataKey)
        if (fightData != null) {
            val charId = session.roleId.toLong()
            logger.info { "${session.characterName} sự kiện kích hoạt chiến đấu: fightDataKey=$fightDataKey" }

            // Gửi S:020-001 để EventHandler[3] của client thực thi SetSession(true)
            responseSender.sendCommonEvent(session, result)

            // Ngăn chặn 020-006 tiến hành/hoàn thành sự kiện trong thời gian diễn ra chiến đấu
            stateMachine.enterAwaitingBattle(charId)

            val terrain = mapTerrains[session.mapId] ?: 101
            battleManager.createPveBattle(session, fightData, terrain)
        } else {
            logger.warn { "${session.characterName} không tìm thấy cấu hình trận chiến: fightDataKey=$fightDataKey — Bỏ qua" }
            onNoFightData()
        }
    }
}