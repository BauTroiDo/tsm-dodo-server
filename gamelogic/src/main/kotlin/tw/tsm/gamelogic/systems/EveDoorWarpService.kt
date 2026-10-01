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
 * Dịch vụ dịch chuyển Door Eve
 *
 * Nhiệm vụ: thực hiện toàn bộ luồng resultType=2 (dịch chuyển Door).
 *
 * Thứ tự gói tin: S:020-001 → S:020-007 → leaveMap/enterMap →
 *          S:012-000 → S:007-000 → [chờ C:012-001] → completeAndEnd
 *
 * Tách ra từ NpcEventHandler.handleDoorWarp().
 */
class EveDoorWarpService(
    private val responseSender: ResponseSender,
    private val worldManager: WorldManager,
    private val characterRepository: CharacterRepository,
    private val stateMachine: EveEventStateMachine,
    private val eveData: Map<Int, SceneEveData>
) {

    /**
     * Thực hiện dịch chuyển Door
     *
     * @param session     session của người chơi
     * @param result      EveResult có resultType=2
     * @param onComplete  callback sau khi dịch chuyển hoàn tất (thường là completeAndEnd)
     */
    suspend fun execute(session: GameSession, result: EveResult, onComplete: suspend () -> Unit) {
        val charId = session.roleId.toLong()
        // EventHandler[2] bật ConductGate ngay khi nhận S:020-001 và có thể gửi
        // C:020-006 ở frame kế tiếp, trước khi S:020-007/UILoading kịp có hiệu lực.
        // Khóa phase trước packet đầu tiên để gói next đến sớm không complete event.
        stateMachine.enterAwaitingWarp(charId)
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
            logger.warn { "${session.characterName} Door sceneInfoKey=$sceneInfoKey không ở cảnh ${session.mapId} — kết thúc sự kiện" }
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
        logger.info { "${session.characterName} Door dịch chuyển: sceneInfo[$sceneInfoKey] → scene=$targetScene ($targetX,$targetY)" }

        responseSender.sendEventChangeScene(session)
        worldManager.leaveMap(session)
        worldManager.enterMap(session, targetScene, targetX, targetY)

        if (charId > 0) {
            characterRepository.updatePosition(charId, targetScene, targetX, targetY)
        }

        // C:020-008 (MeetDoor) được client gửi ngay sau khi đã vào map đích.
        // Vì vậy phải ghi map đích, không phải map nguồn; nếu ghi map nguồn,
        // cổng ở điểm spawn sẽ lập tức đưa nhân vật quay ngược trở lại.
        session.lastDoorWarpMapId = targetScene
        session.lastDoorWarpTime = System.currentTimeMillis()

        // Phải đăng ký bộ chờ trước rồi mới gửi gói cảnh. Nếu không client trả về rất nhanh
        // Khi C:012-001 không có waiter, nhân vật sẽ dừng ở trạng thái màn hình đen/đang tải.
        val deferred = worldManager.createSceneLoadDeferred(session.playerId)
        responseSender.sendPlayerTeleport(session, session.playerId, targetScene, targetX, targetY)
        responseSender.sendSceneChange(session, session.playerId, targetScene, targetX, targetY, session.equipItemIds, session.titleId)

        // Treo để chờ C:012-001 (client tải cảnh xong)
        logger.debug { "${session.characterName} cập nhật cảnh: $oldMapId → $targetScene (chờ C:012-001)" }

        withTimeoutOrNull(10_000L) { deferred.await() }
            ?: logger.warn { "${session.characterName} chờ C:012-001 quá hạn (10s), buộc hoàn tất sự kiện" }

        onComplete()
    }
}
