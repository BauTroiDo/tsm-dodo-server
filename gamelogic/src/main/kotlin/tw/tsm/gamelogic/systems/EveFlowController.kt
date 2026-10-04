package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.QuestRepository

private val logger = KotlinLogging.logger {}

/**
 * Bộ điều khiển luồng sự kiện Eve
 *
 * Nhiệm vụ:
 * - Bắt đầu phân phát (startEvent) — được gọi sau khi [EveTriggerRouter] kích hoạt
 * - Tiến sự kiện (advanceEvent) — C:020-006
 * - Chọn sự kiện (chooseEvent) — C:020-009
 * - Tiếp tục sau chiến đấu (resumeAfterBattle) — callback BattleManager
 * - Phân phát kết quả (dispatchResult) — rẽ nhánh theo resultType
 * - Hoàn thành sự kiện (completeCurrentEvent / finalCompleteAndEnd) — auto-chain hoặc kết thúc
 *
 * Tách logic đẩy tiến/hoàn thành khỏi EveFlowEngine, phối hợp
 * EveAutoChainEngine / EveResultExecutor / EveDoorWarpService / EveBattleService。
 */
private const val BEGINNER_UNLOCK_MISSION_ID = 11804

class EveFlowController(
    private val responseSender: ResponseSender,
    private val stateMachine: EveEventStateMachine,
    private val stateBuilder: EveStateBuilder,
    private val conditionResolver: EveConditionResolver,
    private val resultExecutor: EveResultExecutor,
    private val doorWarpService: EveDoorWarpService,
    private val battleService: EveBattleService,
    private val autoChainEngine: EveAutoChainEngine,
    private val questRepository: QuestRepository,
    private val characterRepository: CharacterRepository,
    private val eveData: Map<Int, SceneEveData>,
    private val npcShopManager: NpcShopManager
) {
    /** App-layer hook for terminal Eve completion (dungeon settlement, etc.). */
    var onEventCompleted: (suspend (GameSession) -> Unit)? = null

    /**
     * Bắt đầu phân phát result đầu tiên (được [EveTriggerRouter] gọi)
     */
    suspend fun startEvent(session: GameSession, firstResult: EveResult) {
        dispatchResult(session, firstResult)
    }

    /**
     * C:020-006 bước tiếp theo của sự kiện
     */
    suspend fun advanceEvent(session: GameSession) {
        val charId = session.roleId.toLong()
        val eventSession = stateMachine.getSession(charId)

        // Surface phía DVT có thể phát C:020-006 gần như đồng thời với C:020-009.
        // Sau khi choice đã được claim nhưng chưa dispatch xong result đầu tiên, tuyệt đối
        // không cho gói next đến sớm complete/clear session đang được resolve.
        if (eventSession?.choiceResolutionInProgress == true) {
            logger.debug { "Nhận 020-006 trong lúc đang resolve lựa chọn Surface, bỏ qua" }
            return
        }

        // EventHandler[2] phía DVT bật ConductGate và có thể gửi C:020-006 ngay
        // trước khi S:020-007 làm UILoading hiện lên. Trong lúc Door warp đang chạy,
        // tuyệt đối không advance/complete event từ gói next đến sớm.
        if (eventSession?.phase is EventPhase.AwaitingWarp) {
            logger.debug { "Nhận 020-006 nhưng đang chuyển cảnh Door, bỏ qua" }
            return
        }

        // Cổng rời map tân thủ: client chỉ gửi 020-006 sau hội thoại, không
        // gửi gói cửa chuẩn. Eve.emg định nghĩa cổng này phải thêm mission
        // 11804 ("Vào Trác Quận") trước khi truyền tống. Cờ hoàn thành của
        // mission này là điều kiện hiển thị AI Tìm, Auto và Gợi Ý; nhánh thay
        // thế cũ chỉ warp nên mọi nhân vật mới đều bị thiếu ba chức năng.
        if (session.mapId == 10851 && eventSession?.eveNo == 1 &&
            eventSession.triggerKind == 4) {
            val alreadyStarted = stateBuilder.buildPlayerState(charId, session.mapId, session)
                .missionSteps[BEGINNER_UNLOCK_MISSION_ID] ?: 0
            if (alreadyStarted == 0) {
                val unlockResult = eveData[10851]?.npcEvents?.get(1)?.conditions
                    ?.asSequence()
                    ?.flatMap { it.results.asSequence() }
                    ?.firstOrNull {
                        it.resultType == 0 &&
                            it.resultClass == 2 &&
                            it.parameter == BEGINNER_UNLOCK_MISSION_ID &&
                            it.parameterStyle == 1
                    }
                if (unlockResult != null) {
                    resultExecutor.executeResultClass(session, unlockResult)
                } else {
                    logger.error { "Map tân thủ không tìm thấy kết quả Eve mở mission $BEGINNER_UNLOCK_MISSION_ID" }
                }
            }

            val beginnerWarp = EveResult(
                resultGroupNo = 0, resultNo = 1, resultType = 2,
                resultClass = 2, parameter = BEGINNER_UNLOCK_MISSION_ID, parameterStyle = 1,
                resultValue = 0, resultMeanNo = 0
            )
            doorWarpService.execute(session, beginnerWarp) {
                finalCompleteAndEnd(session, charId)
            }
            return
        }

        // Bạch Mã (map 12001, event 7): sau hội thoại 10166, client bản này
        // không phát tiếp C:020-006 ổn định. Chủ động chuyển sang Surface 11
        // được khai báo trong Eve.emg để người chơi có thể xác nhận nhận ngựa.
        if (session.mapId == 12001 && eventSession?.eveNo == 7 &&
            eventSession.lastChoiceCode == -1) {
            val surface = eventSession.results.firstOrNull { it.resultType == 6 }
                ?: eveData[12001]?.npcEvents?.get(7)?.conditions
                    ?.asSequence()?.flatMap { it.results.asSequence() }
                    ?.firstOrNull { it.resultType == 6 }
            if (surface != null) {
                dispatchResult(session, surface)
                return
            }
        }

        // Kiểm tra trạng thái sealed class
        if (eventSession?.phase is EventPhase.AwaitingBattle) {
            logger.debug { "Nhận 020-006 nhưng đang chiến đấu, bỏ qua" }
            return
        }

        val result = stateMachine.advance(charId)
        if (result != null) {
            dispatchResult(session, result)
        } else {
            // The internet-cafe exit (10801/door 1) runs Eve #2 to set the
            // beginner mission steps, but the original map data stores its
            // destination separately as SceneInfo #1.  Without completing
            // this hand-off, the character remains on the door and triggers
            // the same event again.
            if (eventSession?.mapId == 10801 && eventSession.eveNo == 2 &&
                eventSession.triggerKind in setOf(4, 8) &&
                eveData[10801]?.sceneInfos?.containsKey(1) == true) {
                val cafeExitWarp = EveResult(
                    resultGroupNo = 0, resultNo = 1, resultType = 2,
                    resultClass = 2, parameter = 1, parameterStyle = 1,
                    resultValue = 0, resultMeanNo = 0
                )
                doorWarpService.execute(session, cafeExitWarp) {
                    finalCompleteAndEnd(session, charId)
                }
                return
            }
            if (eventSession?.phase is EventPhase.AwaitingChoice) {
                logger.debug { "Nhận 020-006 nhưng Surface đang chờ lựa chọn, không hoàn tất sự kiện" }
                return
            }
            completeCurrentEvent(session, charId)
        }
    }

    /**
     * C:020-009 chọn sự kiện
     */
    suspend fun chooseEvent(session: GameSession, code: Int) {
        val charId = session.roleId.toLong()
        logger.info { "${session.characterName} chọn sự kiện: code=$code" }

        val eventSession = stateMachine.claimChoice(charId, code) ?: run {
            logger.debug { "Bỏ qua lựa chọn lặp/sai trạng thái: charId=$charId code=$code" }
            return
        }

        val eventData = eveData[eventSession.mapId]?.npcEvents?.get(eventSession.eveNo)
        if (eventData == null) {
            logger.warn { "handleChoice: không tìm thấy dữ liệu sự kiện eveNo=${eventSession.eveNo}" }
            completeCurrentEvent(session, charId)
            return
        }

        val state = stateBuilder.buildPlayerState(charId, eventSession.mapId, session)
            .copy(
                currentEveNo = eventSession.eveNo,
                // claimChoice() trả về snapshot nguyên tử của đúng Surface + lựa chọn vừa nhận.
                // Không đọc lại ngữ cảnh lựa chọn từ state machine sau các suspend/DB read:
                // session có thể đã bị một luồng Eve khác thay/clear, làm class10 chập chờn -1/-1.
                lastSurfaceId = eventSession.lastSurfaceId,
                lastChoiceCode = eventSession.lastChoiceCode,
            )
        val resolved = conditionResolver.resolveEvent(
            eventData, state, eventSession.mapId,
            chainFilter = { chain -> chain.any { it.conditionClass == 10 } }
        )

        if (resolved != null) {
            stateMachine.replaceResultsAndResume(charId, resolved.results)
            val first = stateMachine.advance(charId)
            if (first != null) {
                logger.info { "${session.characterName} khớp lựa chọn: code=$code → ${resolved.results.size} result" }
                try {
                    dispatchResult(session, first)
                } finally {
                    stateMachine.finishChoiceResolution(charId)
                }
            } else {
                stateMachine.finishChoiceResolution(charId)
                completeCurrentEvent(session, charId)
            }
        } else {
            if (code == 40) {
                logger.info { "${session.characterName} Surface hủy (code=40) → hủy sự kiện (không ghi nhận hoàn thành)" }
                cancelAndEnd(session, charId)
            } else {
                logger.info { "${session.characterName} lựa chọn không khớp (code=$code) → hủy sự kiện (không ghi nhận hoàn thành)" }
                cancelAndEnd(session, charId)
            }
        }
    }

    /**
     * Khôi phục luồng sự kiện sau khi chiến đấu kết thúc — BattleManager gọi qua callback
     */
    suspend fun resumeAfterBattle(session: GameSession, playerId: Long, playerWin: Boolean) {
        val battlePStyle = if (playerWin) 1 else 2
        val charId = session.roleId.toLong()

        val eventSession = stateMachine.claimBattleResult(charId, battlePStyle) ?: run {
            logger.debug { "Bỏ qua kết quả trận lặp/sai trạng thái: charId=$charId" }
            return
        }

        val eventData = eveData[eventSession.mapId]?.npcEvents?.get(eventSession.eveNo)
        if (eventData == null) {
            if (playerWin) advanceEvent(session)
            else { responseSender.sendEventEnd(session); stateMachine.clear(charId) }
            return
        }

        val state = stateBuilder.buildPlayerState(charId, eventSession.mapId, session)
            .copy(currentEveNo = eventSession.eveNo)
        val resolved = conditionResolver.resolveEvent(
            eventData, state, eventSession.mapId,
            chainFilter = { chain -> chain.any { it.conditionClass == 8 } }
        )

        if (resolved != null) {
            // Một shape DVT duy nhất (scene 12421/eve2) có nhánh thắng cụ thể
            // cập nhật mission, rồi một class8-win đơn lẻ phía sau mới thực hiện Door warp.
            // Resolver chung cố ý chỉ chọn một best chain, nên nối riêng continuation
            // có shape type=2/class7 để không thay đổi semantics các event khác.
            val doorContinuation = EveChainResolver.findPostBattleDoorContinuation(
                conditions = eventData.conditions,
                evaluator = { cond -> EveConditionEvaluator.evaluate(cond, state) },
                matchedConditionNo = resolved.matchedConditionNo
            )
            val postBattleResults = resolved.results + doorContinuation

            stateMachine.replaceResultsAndResume(charId, postBattleResults)
            val first = stateMachine.advance(charId)
            if (first != null) {
                logger.info {
                    "${session.characterName} khớp sau trận: pStyle=$battlePStyle → " +
                        "${postBattleResults.size} result (doorContinuation=${doorContinuation.size})"
                }
                dispatchResult(session, first)
            } else {
                completeCurrentEvent(session, charId)
            }
        } else {
            if (playerWin) {
                resultExecutor.autoCaptureFromEvent(session, charId, eventData)
                advanceEvent(session)
            } else {
                cancelAndEnd(session, charId)
                logger.info { "${session.characterName} thua trận, hủy sự kiện để có thể thử lại" }
            }
        }
    }

    // ══════════════════════════════════════════════
    //  Phân phát kết quả
    // ══════════════════════════════════════════════

    /**
     * Xử lý rẽ nhánh theo resultType
     *
     * Tương ứng 7 handler của EventHandler.lua phía client:
     * type=0: hiệu ứng phía server, tự động đẩy tiến (client EventHandler[0] → SetConduct(true))
     * type=1: hội thoại Talk, gửi tới client chờ C:020-006
     * type=2: dịch chuyển Door
     * type=3: kích hoạt chiến đấu (client EventHandler[3] → SetSession(true))
     * type=4: NPC di chuyển (client EventHandler[4] → SetConduct(true), tự động đẩy tiến)
     * type=5: hoạt cảnh chuyển cảnh (client EventHandler[5] → SetConduct(false), đẩy tiến sau khi hoạt cảnh kết thúc)
     * type=6: giao diện chọn Surface
     * type=8/9/10: bỏ qua lặng lẽ (client không có EventHandler, không xử lý)
     */
    private suspend fun executeResultOrAbort(session: GameSession, result: EveResult): Boolean {
        if (resultExecutor.executeResultClass(session, result)) return true
        // Do not advance to a mission completion or count this event as completed
        // when the required inventory mutation could not be performed in full.
        stateMachine.clear(session.roleId.toLong())
        responseSender.sendEventEnd(session)
        return false
    }

    private suspend fun dispatchResult(session: GameSession, result: EveResult) {
        val charId = session.roleId.toLong()
        val pendingSession = stateMachine.getSession(charId)
        val pending = pendingSession?.let {
            it.results.drop((it.currentIndex - 1).coerceAtLeast(0))
        }.orEmpty()
        val remaining = if (pending.firstOrNull() == result) pending else listOf(result) + pending
        if (!resultExecutor.canExecuteResults(session, remaining)) {
            stateMachine.clear(charId)
            responseSender.sendEventEnd(session)
            return
        }
        logger.info { "${session.characterName} dispatch: type=${result.resultType} cls=${result.resultClass} pStyle=${result.parameterStyle} param=${result.parameter} val=${result.resultValue} group=${result.resultGroupNo} no=${result.resultNo}" }

        // ═══ Lọc thống nhất: bỏ qua thao tác UI NPC phục vụ trong chuỗi nhiệm vụ ═══
        // cls=7 pStyle 1~5 là UI của NPC phục vụ (kho báu/Tiền Trang/cửa hàng/lữ quán/lưu trú)
        // Căn cứ xác định: chuỗi kết quả chứa cls 1~3 (vật phẩm/nhiệm vụ/NPC) = chuỗi nhiệm vụ → bỏ qua
        if (result.resultClass == 7 &&
            EveResultClassPolicy.isRepeatableServicePlayerOperation(result.parameterStyle, result.resultValue)) {
            val es = stateMachine.getSession(session.roleId.toLong())
            val isQuestChain = es?.results?.any { it.resultClass in 1..3 } == true
            if (isQuestChain) {
                logger.debug { "${session.characterName} bỏ qua UI phục vụ: pStyle=${result.parameterStyle} (chuỗi nhiệm vụ)" }
                advanceOrComplete(session)
                return
            }
        }

        when (result.resultType) {
            0 -> {
                // type=0: hiệu ứng phía server
                if (result.resultClass == 7 && result.parameterStyle == 1 &&
                    EveResultClassPolicy.isRepeatableServicePlayerOperation(result.parameterStyle, result.resultValue)) {
                    // pStyle=1 nhận biết ngữ cảnh: NPC lữ quán → lưu điểm ghi / NPC Tiền Trang → mở kho báu
                    val charId0 = session.roleId.toLong()
                    val eventSession = stateMachine.getSession(charId0)
                    val npcClickId = eventSession?.npcClickId ?: -1
                    if (isInnServiceNpc(session.mapId, npcClickId)) {
                        saveRecordPoint(session, charId0)
                    } else {
                        if (!executeResultOrAbort(session, result)) return
                    }
                    serviceAdvanceOrEnd(session)
                } else if (result.resultClass != 0) {
                    if (!executeResultOrAbort(session, result)) return
                    if (result.resultClass == 3) {
                        // Thao tác hình ảnh NPC (tàng hình/hiện ra/biến hình/truy kích) → gửi client
                        responseSender.sendCommonEvent(session, result)
                    } else if (result.resultClass == 7 &&
                        EveResultClassPolicy.isRepeatableServicePlayerOperation(result.parameterStyle, result.resultValue)) {
                        // NPC phục vụ (Tiền Trang/cửa hàng/lữ quán/lưu trú) → không ghi nhận hoàn thành, đảm bảo dùng lại được
                        serviceAdvanceOrEnd(session)
                    } else {
                        // resultClass=1,2 v.v.: server đã xử lý, tự động đẩy tiến
                        advanceOrComplete(session)
                    }
                } else {
                    // resultClass=0: không có hiệu ứng, tự động đẩy tiến
                    advanceOrComplete(session)
                }
            }
            1 -> {
                // type=1: hội thoại Talk → gửi tới client, chờ người chơi bấm C:020-006
                responseSender.sendCommonEvent(session, result)
            }
            2 -> {
                // resultClass=7 is the client-side switch/door action. It must
                // be delivered as a common event so the client sends the warp
                // request; only class=2 carries a sceneInfo teleport target.
                if (result.resultClass == 7 &&
                    !eveData[session.mapId]?.sceneInfos.orEmpty().containsKey(result.parameter)) {
                    responseSender.sendCommonEvent(session, result)
                } else {
                    doorWarpService.execute(session, result) {
                        val charId = session.roleId.toLong()
                        finalCompleteAndEnd(session, charId)
                    }
                }
            }
            3 -> battleService.triggerBattle(session, result) {
                advanceOrComplete(session)
            }
            4 -> {
                // type=4: NPC di chuyển → gửi tới client, chờ C:020-006
                // EventHandler[4] client: role:Move() + SetConduct(true) → tự động C:020-006
                logger.debug { "${session.characterName} NPC di chuyển: param=${result.parameter} value=${result.resultValue}" }
                responseSender.sendCommonEvent(session, result)
            }
            5 -> {
                // type=5: hoạt cảnh chuyển cảnh → gửi tới client, chờ C:020-006
                // EventHandler[5] client: khi pStyle=1/2 SetConduct(false), sau khi hoạt cảnh kết thúc SetConduct(true) → C:020-006
                logger.debug { "${session.characterName} hoạt cảnh chuyển cảnh: movieId=${result.resultValue} pStyle=${result.parameterStyle}" }
                responseSender.sendCommonEvent(session, result)
            }
            6 -> {
                // type=6: giao diện chọn Surface
                logger.info { "${session.characterName} Surface resultMeanNo=${result.resultMeanNo} pStyle=${result.parameterStyle}" }
                val charId = session.roleId.toLong()
                stateMachine.enterAwaitingChoice(charId, result.resultMeanNo)

                // optionMode=1 (cửa sổ mua bán) → theo dõi danh sách hàng hóa để C:027-001 truy vấn
                val surfaceData = eveData[session.mapId]?.surfaceDatas?.get(result.resultMeanNo)
                if (surfaceData != null && surfaceData.optionMode == 1) {
                    val itemIds = surfaceData.sentences.entries
                        .sortedBy { it.key }
                        .filter { (_, s) -> s.style == 2 || s.style == 3 }
                        .map { (_, s) -> if (s.dataKind == 2) s.dataId else 0 }
                    npcShopManager.setShopItems(charId, itemIds)
                }

                responseSender.sendCommonEvent(session, result)
            }
            7 -> {
                // type=7: thao tác người chơi phía server (client không có EventHandler[7])
                val charId7 = session.roleId.toLong()
                if (result.resultClass == 7 && result.parameterStyle == 1 &&
                    EveResultClassPolicy.isRepeatableServicePlayerOperation(result.parameterStyle, result.resultValue)) {
                    // pStyle=1: phân biệt theo ngữ cảnh
                    val eventSession = stateMachine.getSession(charId7)
                    if (eventSession != null && eventSession.lastChoiceCode != -1) {
                        // Sau khi chọn Surface: NPC lữ quán → lưu điểm ghi / NPC Tiền Trang → mở kho báu
                        if (isInnServiceNpc(session.mapId, eventSession.npcClickId)) {
                            saveRecordPoint(session, charId7)
                        } else {
                            if (!executeResultOrAbort(session, result)) return
                        }
                        serviceAdvanceOrEnd(session)
                    } else {
                        // Kích hoạt lần đầu → pStyle=1 = mở giao diện chọn Surface
                        openSurfaceForServiceNpc(session, result, charId7)
                    }
                } else {
                    // pStyle khác: thực hiện trực tiếp (UI phục vụ cls=7 hoạt động độc lập với hệ thống sự kiện)
                    if (result.resultClass != 0) {
                        if (!executeResultOrAbort(session, result)) return
                    }
                    // NPC phục vụ dùng được nhiều lần — không ghi nhận hoàn thành
                    serviceAdvanceOrEnd(session)
                }
            }
            else -> {
                if (EveResultTypePolicy.shouldSkipWithoutClientHandler(result.resultType)) {
                    logger.debug { "${session.characterName} resultType=${result.resultType} không có handler phía client, âm thầm bỏ qua" }
                    advanceOrComplete(session)
                } else {
                    // type không xác định → tự động đẩy tiến (gửi client sẽ bị kẹt vì không có handler)
                    logger.warn { "${session.characterName} resultType=${result.resultType} không xác định cls=${result.resultClass}, tự động bỏ qua" }
                    advanceOrComplete(session)
                }
            }
        }
    }

    /**
     * resultType=7 pStyle=1: mở giao diện chọn Surface
     *
     * Tương đương hành vi type=6, nhưng resultMeanNo=0, phải tự tìm surfaceData của cảnh.
     * Ứng dụng điển hình: NPC Tiền Trang nói Talk trước rồi mở menu (Tiền Trang/kho báu/rời đi).
     */
    private suspend fun openSurfaceForServiceNpc(session: GameSession, result: EveResult, charId: Long) {
        val sceneEve = eveData[session.mapId]
        if (sceneEve == null || sceneEve.surfaceDatas.isEmpty()) {
            logger.warn { "${session.characterName} type=7 pStyle=1 nhưng cảnh ${session.mapId} không có surfaceData → tự đẩy tới" }
            advanceOrComplete(session)
            return
        }

        val surfaceEntry = sceneEve.surfaceDatas.entries.firstOrNull()
        if (surfaceEntry == null) {
            logger.warn { "${session.characterName} type=7 pStyle=1 nhưng cảnh ${session.mapId} không có surfaceData → tự đẩy tới" }
            advanceOrComplete(session)
            return
        }

        val surfaceId = surfaceEntry.key
        val surfaceData = surfaceEntry.value
        stateMachine.enterAwaitingChoice(charId, surfaceId)

        // optionMode=1 (cửa sổ mua bán) → theo dõi danh sách hàng hóa
        if (surfaceData.optionMode == 1) {
            val itemIds = surfaceData.sentences.entries
                .sortedBy { it.key }
                .filter { (_, s) -> s.style == 2 || s.style == 3 }
                .map { (_, s) -> if (s.dataKind == 2) s.dataId else 0 }
            npcShopManager.setShopItems(charId, itemIds)
        }

        // Gửi cho client theo type=6, để EventHandler[6] xử lý hiển thị Surface
        val surfaceResult = result.copy(resultType = 6, resultMeanNo = surfaceId)
        responseSender.sendCommonEvent(session, surfaceResult)
        logger.info { "${session.characterName} type=7 pStyle=1 → mở Surface (surfaceId=$surfaceId)" }
    }

    /**
     * Đẩy tiến sang result kế tiếp hoặc hoàn thành sự kiện — lối vào dùng chung cho resultType tự động đẩy tiến
     */
    private suspend fun advanceOrComplete(session: GameSession) {
        val charId = session.roleId.toLong()
        val next = stateMachine.advance(charId)
        if (next != null) dispatchResult(session, next) else completeCurrentEvent(session, charId)
    }

    /**
     * Đẩy tiến kết quả NPC phục vụ — có result kế tiếp thì tiếp tục, ngược lại hủy sự kiện (không ghi nhận hoàn thành)
     *
     * Khác với [advanceOrComplete]: khi kết thúc đi qua [cancelAndEnd] thay vì [completeCurrentEvent],
     * Đảm bảo các sự kiện phục vụ (kho báu/Tiền Trang/cửa hàng/lữ quán/lưu trú) dùng được nhiều lần.
     */
    private suspend fun serviceAdvanceOrEnd(session: GameSession) {
        val charId = session.roleId.toLong()
        val next = stateMachine.advance(charId)
        if (next != null) dispatchResult(session, next) else cancelAndEnd(session, charId)
    }

    // ══════════════════════════════════════════════
    //  Sau dịch chuyển Door tự động MeetDoor
    // ══════════════════════════════════════════════

    // ══════════════════════════════════════════════
    //  Hoàn thành sự kiện
    // ══════════════════════════════════════════════

    /**
     * Hoàn thành sự kiện hiện tại — lối vào thống nhất, thử auto-chain hoặc kết thúc
     */
    private suspend fun completeCurrentEvent(session: GameSession, charId: Long) {
        val completedSession = completeEventInternal(charId)

        if (completedSession != null && autoChainEngine.shouldAttemptAutoChain(completedSession)) {
            when (val chainResult = autoChainEngine.tryAutoChain(session, charId, completedSession)) {
                is AutoChainResult.Chained -> {
                    dispatchResult(session, chainResult.firstResult)
                    return
                }
                is AutoChainResult.NoMatch -> { /* Tiếp tục đến sendEventEnd*/ }
            }
        }

        if (completedSession != null) notifyEventCompleted(session)
        responseSender.sendEventEnd(session)
    }

    /**
     * Buộc kết thúc sự kiện — không thử auto-chain
     *
     * Dùng cho các tình huống dịch chuyển Door, thua trận, hủy Surface, v.v.
     */
    private suspend fun finalCompleteAndEnd(session: GameSession, charId: Long) {
        val completedSession = completeEventInternal(charId)
        if (completedSession != null) notifyEventCompleted(session)
        responseSender.sendEventEnd(session)
    }

    /**
     * Hủy sự kiện — xóa trạng thái nhưng không ghi nhận hoàn thành
     *
     * Dùng cho các tình huống người chơi chủ động hủy như Surface (code=40).
     * Khác với finalCompleteAndEnd: không ghi condNo, không lưu bền DB.
     */
    /** Release a failed battle continuation without awarding/completing its event. */
    fun abortAfterBattle(session: GameSession) {
        cancelAndEnd(session, session.roleId.toLong())
    }

    private fun cancelAndEnd(session: GameSession, charId: Long) {
        npcShopManager.clearShop(charId)
        stateMachine.clear(charId)
        responseSender.sendEventEnd(session)
    }

    /**
     * Xác định NPC có thuộc loại lữ quán không
     *
     * Quét mọi kết quả sự kiện của NPC, nếu chứa pStyle=4 (UI lữ quán) hoặc pStyle=5 (lưu trú)
     * thì được xác định là NPC lữ quán. Dùng cho rẽ nhánh ngữ cảnh pStyle=1: lữ quán=lưu điểm ghi / Tiền Trang=mở kho báu.
     */
    private fun isInnServiceNpc(mapId: Int, npcClickId: Int): Boolean {
        val sceneData = eveData[mapId] ?: return false
        val npc = sceneData.npcs[npcClickId] ?: return false
        for (eveNo in npc.events) {
            val eventData = sceneData.npcEvents[eveNo] ?: continue
            for (condition in eventData.conditions) {
                for (result in condition.results) {
                    if (result.resultClass == 7 && result.parameterStyle in listOf(4, 5)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Lưu điểm ghi — lưu vị trí hiện tại của người chơi vào DB làm điểm hồi sinh
     */
    private suspend fun saveRecordPoint(session: GameSession, charId: Long) {
        val mapId = session.mapId
        val x = session.x
        val y = session.y
        try {
            characterRepository.updateRecordPoint(charId, mapId, x, y)
            logger.info { "${session.characterName} lưu điểm kiểm tra: mapId=$mapId ($x,$y)" }
        } catch (e: Exception) {
            logger.error(e) { "${session.characterName} lưu điểm kiểm tra thất bại: mapId=$mapId ($x,$y)" }
        }
    }

    private suspend fun notifyEventCompleted(session: GameSession) {
        val callback = onEventCompleted ?: return
        try {
            callback(session)
        } catch (e: Exception) {
            logger.error(e) { "${session.characterName} callback hoàn tất Eve thất bại" }
        }
    }

    /**
     * Logic nội bộ hoàn thành sự kiện — ghi nhận hoàn thành + lưu bền
     */
    private suspend fun completeEventInternal(charId: Long): EventSession? {
        val completedSession = stateMachine.complete(charId)

        // Sự kiện kết thúc → xóa trạng thái cửa hàng
        npcShopManager.clearShop(charId)

        // Lưu bền bản ghi hoàn thành sự kiện (điều kiện class=12 phụ thuộc số lần hoàn thành, sự kiện chỉ Talk cũng phải tính)
        if (completedSession != null && completedSession.eveNo != 0) {
            try {
                questRepository.insertCompletedEvent(charId, completedSession.mapId, completedSession.eveNo)
            } catch (e: Exception) {
                logger.error(e) { "Lưu hoàn thành sự kiện xuống DB thất bại: charId=$charId mapId=${completedSession.mapId} eveNo=${completedSession.eveNo}" }
            }
        }

        return completedSession
    }
}
