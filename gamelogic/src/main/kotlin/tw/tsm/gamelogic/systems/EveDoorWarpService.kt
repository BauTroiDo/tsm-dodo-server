package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withTimeoutOrNull
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository

private val logger = KotlinLogging.logger {}

/**
 * Eve Door 傳送服務
 *
 * 職責：執行 resultType=2（Door 傳送）的完整流程。
 *
 * 封包順序：S:020-001 → S:020-007 → leaveMap/enterMap →
 *          S:012-000 → S:007-000 → [等待 C:012-001] → completeAndEnd
 *
 * 從 NpcEventHandler.handleDoorWarp() 抽取。
 */
class EveDoorWarpService(
    private val responseSender: ResponseSender,
    private val worldManager: WorldManager,
    private val characterRepository: CharacterRepository,
    private val stateMachine: EveEventStateMachine,
    private val eveData: Map<Int, SceneEveData>
) {

    /**
     * 執行 Door 傳送
     *
     * @param session     玩家 session
     * @param result      resultType=2 的 EveResult
     * @param onComplete  傳送完成後的回呼（通常為 completeAndEnd）
     */
    suspend fun execute(session: GameSession, result: EveResult, onComplete: suspend () -> Unit) {
        responseSender.sendCommonEvent(session, result)

        val sceneInfoKey = result.parameter
        val sceneInfo = eveData[session.mapId]?.sceneInfos?.get(sceneInfoKey)
        // Bản PC xử lý riêng cổng map tân thủ: warpId=2 từ map 10851
        // đi tới map 12003, tọa độ 555/555. Dữ liệu Eve của bản CDN
        // dùng mã 11804 nhưng không có sceneInfo tương ứng nên trước đây
        // server kết thúc sự kiện mà không đổi map.
        val beginnerGate = session.mapId == 10851 && sceneInfoKey == 11804
        // Cổng rời Tiệm Nét: SceneInfo chỉ mô tả map đích; tọa độ cutscene
        // thực tế của client là 810/510, không phải tọa độ xuất hiện mặc định.
        val internetCafeExit = session.mapId == 10801 && sceneInfoKey == 1
        if (sceneInfo == null && !beginnerGate) {
            logger.warn { "${session.characterName} Door sceneInfoKey=$sceneInfoKey 不在場景 ${session.mapId} — 結束事件" }
            responseSender.sendEventEnd(session)
            stateMachine.complete(session.roleId.toLong())
            return
        }

        val targetScene = if (beginnerGate) 12003 else sceneInfo!!.backgroundNo
        // WorldManager/PC source dùng trực tiếp tọa độ 555 cho cổng này;
        // nhân 20 sẽ đẩy nhân vật ra ngoài màn hình ở góc dưới phải.
        val targetX = when {
            beginnerGate -> 555
            internetCafeExit -> 850
            else -> sceneInfo!!.playerAppearX * 20
        }
        val targetY = when {
            beginnerGate -> 555
            internetCafeExit -> 510
            else -> sceneInfo!!.playerAppearY * 20
        }
        val oldMapId = session.mapId
        logger.info { "${session.characterName} Door 傳送：sceneInfo[$sceneInfoKey] → scene=$targetScene ($targetX,$targetY)" }

        responseSender.sendEventChangeScene(session)
        worldManager.leaveMap(session)
        worldManager.enterMap(session, targetScene, targetX, targetY)

        val charId = session.roleId.toLong()
        if (charId > 0) {
            characterRepository.updatePosition(charId, targetScene, targetX, targetY)
        }

        // C:020-008 (MeetDoor) được client gửi ngay sau khi đã vào map đích.
        // Vì vậy phải ghi map đích, không phải map nguồn; nếu ghi map nguồn,
        // cổng ở điểm spawn sẽ lập tức đưa nhân vật quay ngược trở lại.
        session.lastDoorWarpMapId = targetScene
        session.lastDoorWarpTime = System.currentTimeMillis()

        // 必須先註冊等待器，再发送场景包。否则客户端很快返回
        // C:012-001 时没有 waiter，角色会停在黑屏/加载状态。
        val deferred = worldManager.createSceneLoadDeferred(session.playerId)
        responseSender.sendPlayerTeleport(session, session.playerId, targetScene, targetX, targetY)
        responseSender.sendSceneChange(session, session.playerId, targetScene, targetX, targetY, session.equipItemIds, session.titleId)

        // 掛起等待 C:012-001（客戶端場景載入完畢）
        logger.debug { "${session.characterName} 場景更新：$oldMapId → $targetScene（等待 C:012-001）" }

        withTimeoutOrNull(10_000L) { deferred.await() }
            ?: logger.warn { "${session.characterName} 等待 C:012-001 超時（10s），強制完成事件" }

        onComplete()
    }
}
