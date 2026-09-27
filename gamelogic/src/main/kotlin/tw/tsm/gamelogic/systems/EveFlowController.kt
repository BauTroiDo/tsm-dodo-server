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
 * Eve 事件流程控制器
 *
 * 職責：
 * - 開始分發（startEvent）— 由 [EveTriggerRouter] 觸發後呼叫
 * - 事件推進（advanceEvent）— C:020-006
 * - 事件選擇（chooseEvent）— C:020-009
 * - 戰鬥恢復（resumeAfterBattle）— BattleManager 回呼
 * - 結果分發（dispatchResult）— resultType 分流
 * - 事件完成（completeCurrentEvent / finalCompleteAndEnd）— auto-chain 或結束
 *
 * 從 EveFlowEngine 抽取推進/完成邏輯，協調
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

    /**
     * 開始分發第一個 result（由 [EveTriggerRouter] 呼叫）
     */
    suspend fun startEvent(session: GameSession, firstResult: EveResult) {
        dispatchResult(session, firstResult)
    }

    /**
     * C:020-006 事件下一步
     */
    suspend fun advanceEvent(session: GameSession) {
        val charId = session.roleId.toLong()
        val eventSession = stateMachine.getSession(charId)

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

        // sealed class 狀態檢查
        if (eventSession?.phase is EventPhase.AwaitingBattle) {
            logger.debug { "020-006 收到但戰鬥進行中，忽略" }
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
                logger.debug { "020-006 收到但 Surface 等待選擇中，不完成事件" }
                return
            }
            completeCurrentEvent(session, charId)
        }
    }

    /**
     * C:020-009 事件選擇
     */
    suspend fun chooseEvent(session: GameSession, code: Int) {
        val charId = session.roleId.toLong()
        logger.info { "${session.characterName} 事件選擇：code=$code" }

        stateMachine.setChoiceCode(charId, code)

        val eventSession = stateMachine.getSession(charId)
        if (eventSession == null) {
            logger.warn { "handleChoice：charId=$charId 無進行中事件" }
            responseSender.sendEventEnd(session)
            return
        }

        val eventData = eveData[eventSession.mapId]?.npcEvents?.get(eventSession.eveNo)
        if (eventData == null) {
            logger.warn { "handleChoice：找不到 eveNo=${eventSession.eveNo} 事件資料" }
            completeCurrentEvent(session, charId)
            return
        }

        val state = stateBuilder.buildPlayerState(charId, eventSession.mapId, session)
            .copy(currentEveNo = eventSession.eveNo)
        val resolved = conditionResolver.resolveEvent(
            eventData, state, eventSession.mapId,
            chainFilter = { chain -> chain.any { it.conditionClass == 10 } }
        )

        if (resolved != null) {
            stateMachine.replaceResultsAndResume(charId, resolved.results)
            val first = stateMachine.advance(charId)
            if (first != null) {
                logger.info { "${session.characterName} 選擇匹配：code=$code → ${resolved.results.size} 個 result" }
                dispatchResult(session, first)
            } else {
                completeCurrentEvent(session, charId)
            }
        } else {
            if (code == 40) {
                logger.info { "${session.characterName} Surface 取消（code=40）→ 取消事件（不記錄完成）" }
                cancelAndEnd(session, charId)
            } else {
                logger.info { "${session.characterName} 選擇無匹配（code=$code）→ 取消事件（不記錄完成）" }
                cancelAndEnd(session, charId)
            }
        }
    }

    /**
     * 戰鬥結束後恢復事件流程 — 由 BattleManager 透過回呼呼叫
     */
    suspend fun resumeAfterBattle(session: GameSession, playerId: Long, playerWin: Boolean) {
        val battlePStyle = if (playerWin) 1 else 2
        val charId = session.roleId.toLong()

        stateMachine.setBattleResult(charId, battlePStyle)
        // ★ 修復：先恢復到 Executing，確保 fallback 路徑的 advance() 能正常運作
        stateMachine.resumeFromBattle(charId)

        val eventSession = stateMachine.getSession(charId)
        if (eventSession == null) {
            logger.warn { "resumeAfterBattle：charId=$charId 無進行中事件" }
            responseSender.sendEventEnd(session)
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
            stateMachine.replaceResultsAndResume(charId, resolved.results)
            val first = stateMachine.advance(charId)
            if (first != null) {
                logger.info { "${session.characterName} 戰鬥後匹配：pStyle=$battlePStyle → ${resolved.results.size} 個 result" }
                dispatchResult(session, first)
            } else {
                completeCurrentEvent(session, charId)
            }
        } else {
            if (playerWin) {
                resultExecutor.autoCaptureFromEvent(session, charId, eventData)
                advanceEvent(session)
            } else {
                finalCompleteAndEnd(session, charId)
                logger.info { "${session.characterName} 戰鬥失敗，事件結束" }
            }
        }
    }

    // ══════════════════════════════════════════════
    //  結果分發
    // ══════════════════════════════════════════════

    /**
     * 依 resultType 分流處理
     *
     * 對應客戶端 EventHandler.lua 7 個 handler：
     * type=0：伺服器端效果，自動推進（客戶端 EventHandler[0] → SetConduct(true)）
     * type=1：Talk 對話，送到客戶端等 C:020-006
     * type=2：Door 傳送
     * type=3：戰鬥觸發（客戶端 EventHandler[3] → SetSession(true)）
     * type=4：NPC 移動（客戶端 EventHandler[4] → SetConduct(true)，自動推進）
     * type=5：過場動畫（客戶端 EventHandler[5] → SetConduct(false)，動畫結束後推進）
     * type=6：Surface 選擇介面
     * type=8/9/10：靜默跳過（客戶端無 EventHandler，不處理）
     */
    private suspend fun dispatchResult(session: GameSession, result: EveResult) {
        logger.info { "${session.characterName} dispatch: type=${result.resultType} cls=${result.resultClass} pStyle=${result.parameterStyle} param=${result.parameter} val=${result.resultValue} group=${result.resultGroupNo} no=${result.resultNo}" }

        // ═══ 統一過濾：任務鏈中跳過服務 NPC UI 操作 ═══
        // cls=7 pStyle 1~5 是服務 NPC 的 UI（寶庫/錢莊/商店/客棧/住宿）
        // 判斷依據：結果鏈含 cls 1~3（物品/任務/NPC）= 任務鏈 → 跳過
        if (result.resultClass == 7 && result.parameterStyle in 1..5) {
            val es = stateMachine.getSession(session.roleId.toLong())
            val isQuestChain = es?.results?.any { it.resultClass in 1..3 } == true
            if (isQuestChain) {
                logger.debug { "${session.characterName} 跳過服務 UI：pStyle=${result.parameterStyle}（任務鏈）" }
                advanceOrComplete(session)
                return
            }
        }

        when (result.resultType) {
            0 -> {
                // type=0：伺服器端效果
                if (result.resultClass == 7 && result.parameterStyle == 1) {
                    // pStyle=1 上下文感知：客棧 NPC → 儲存紀錄點 / 錢莊 NPC → 開啟寶庫
                    val charId0 = session.roleId.toLong()
                    val eventSession = stateMachine.getSession(charId0)
                    val npcClickId = eventSession?.npcClickId ?: -1
                    if (isInnServiceNpc(session.mapId, npcClickId)) {
                        saveRecordPoint(session, charId0)
                    } else {
                        resultExecutor.executeResultClass(session, result)
                    }
                    serviceAdvanceOrEnd(session)
                } else if (result.resultClass != 0) {
                    resultExecutor.executeResultClass(session, result)
                    if (result.resultClass == 3) {
                        // NPC 視覺操作（隱形/顯現/變身/追擊）→ 送客戶端
                        responseSender.sendCommonEvent(session, result)
                    } else if (result.resultClass == 7 && result.parameterStyle in 2..5) {
                        // 服務 NPC（錢莊/商店/客棧/住宿）→ 不記錄完成，確保可重複
                        serviceAdvanceOrEnd(session)
                    } else {
                        // resultClass=1,2 等：伺服器已處理，自動推進
                        advanceOrComplete(session)
                    }
                } else {
                    // resultClass=0：無效果，自動推進
                    advanceOrComplete(session)
                }
            }
            1 -> {
                // type=1：Talk 對話 → 送到客戶端，等玩家點擊 C:020-006
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
                // type=4：NPC 移動 → 送到客戶端，等 C:020-006
                // 客戶端 EventHandler[4]：role:Move() + SetConduct(true) → 自動 C:020-006
                logger.debug { "${session.characterName} NPC 移動：param=${result.parameter} value=${result.resultValue}" }
                responseSender.sendCommonEvent(session, result)
            }
            5 -> {
                // type=5：過場動畫 → 送到客戶端，等 C:020-006
                // 客戶端 EventHandler[5]：pStyle=1/2 時 SetConduct(false)，動畫結束後 SetConduct(true) → C:020-006
                logger.debug { "${session.characterName} 過場動畫：movieId=${result.resultValue} pStyle=${result.parameterStyle}" }
                responseSender.sendCommonEvent(session, result)
            }
            6 -> {
                // type=6：Surface 選擇介面
                logger.info { "${session.characterName} Surface resultMeanNo=${result.resultMeanNo} pStyle=${result.parameterStyle}" }
                val charId = session.roleId.toLong()
                stateMachine.enterAwaitingChoice(charId, result.resultMeanNo)

                // optionMode=1（買賣視窗）→ 追蹤商品清單供 C:027-001 查詢
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
                // type=7：伺服器端玩家操作（客戶端無 EventHandler[7]）
                val charId7 = session.roleId.toLong()
                if (result.resultClass == 7 && result.parameterStyle == 1) {
                    // pStyle=1：依上下文區分
                    val eventSession = stateMachine.getSession(charId7)
                    if (eventSession != null && eventSession.lastChoiceCode != -1) {
                        // Surface 選擇後：客棧 NPC → 儲存紀錄點 / 錢莊 NPC → 開啟寶庫
                        if (isInnServiceNpc(session.mapId, eventSession.npcClickId)) {
                            saveRecordPoint(session, charId7)
                        } else {
                            resultExecutor.executeResultClass(session, result)
                        }
                        serviceAdvanceOrEnd(session)
                    } else {
                        // 初次觸發 → pStyle=1 = 開啟 Surface 選擇介面
                        openSurfaceForServiceNpc(session, result, charId7)
                    }
                } else {
                    // 其他 pStyle：直接執行（cls=7 的服務 UI 獨立於事件系統運作）
                    if (result.resultClass != 0) {
                        resultExecutor.executeResultClass(session, result)
                    }
                    // 服務 NPC 可重複使用 — 不記錄完成
                    serviceAdvanceOrEnd(session)
                }
            }
            else -> {
                if (EveResultTypePolicy.shouldSkipWithoutClientHandler(result.resultType)) {
                    logger.debug { "${session.characterName} resultType=${result.resultType} 無客戶端 handler，靜默跳過" }
                    advanceOrComplete(session)
                } else {
                    // 未知 type → 自動推進（送客戶端會因無 handler 而卡死）
                    logger.warn { "${session.characterName} 未知 resultType=${result.resultType} cls=${result.resultClass}，自動跳過" }
                    advanceOrComplete(session)
                }
            }
        }
    }

    /**
     * resultType=7 pStyle=1：開啟 Surface 選擇介面
     *
     * 等同 type=6 行為，但 resultMeanNo=0，需自行找場景 surfaceData。
     * 典型用途：錢莊 NPC 先 Talk 再開選單（錢莊/寶庫/離開）。
     */
    private suspend fun openSurfaceForServiceNpc(session: GameSession, result: EveResult, charId: Long) {
        val sceneEve = eveData[session.mapId]
        if (sceneEve == null || sceneEve.surfaceDatas.isEmpty()) {
            logger.warn { "${session.characterName} type=7 pStyle=1 但場景 ${session.mapId} 無 surfaceData → 自動推進" }
            advanceOrComplete(session)
            return
        }

        val surfaceEntry = sceneEve.surfaceDatas.entries.firstOrNull()
        if (surfaceEntry == null) {
            logger.warn { "${session.characterName} type=7 pStyle=1 但場景 ${session.mapId} 無 surfaceData → 自動推進" }
            advanceOrComplete(session)
            return
        }

        val surfaceId = surfaceEntry.key
        val surfaceData = surfaceEntry.value
        stateMachine.enterAwaitingChoice(charId, surfaceId)

        // optionMode=1（買賣視窗）→ 追蹤商品清單
        if (surfaceData.optionMode == 1) {
            val itemIds = surfaceData.sentences.entries
                .sortedBy { it.key }
                .filter { (_, s) -> s.style == 2 || s.style == 3 }
                .map { (_, s) -> if (s.dataKind == 2) s.dataId else 0 }
            npcShopManager.setShopItems(charId, itemIds)
        }

        // 以 type=6 發送給客戶端，讓 EventHandler[6] 處理 Surface 顯示
        val surfaceResult = result.copy(resultType = 6, resultMeanNo = surfaceId)
        responseSender.sendCommonEvent(session, surfaceResult)
        logger.info { "${session.characterName} type=7 pStyle=1 → 開啟 Surface（surfaceId=$surfaceId）" }
    }

    /**
     * 推進到下一個 result 或完成事件 — 自動推進 resultType 的共用入口
     */
    private suspend fun advanceOrComplete(session: GameSession) {
        val charId = session.roleId.toLong()
        val next = stateMachine.advance(charId)
        if (next != null) dispatchResult(session, next) else completeCurrentEvent(session, charId)
    }

    /**
     * 服務 NPC 結果推進 — 有下一個 result 則繼續，否則取消事件（不記錄完成）
     *
     * 與 [advanceOrComplete] 的差異：結束時走 [cancelAndEnd] 而非 [completeCurrentEvent]，
     * 確保服務事件（寶庫/錢莊/商店/客棧/住宿）可重複使用。
     */
    private suspend fun serviceAdvanceOrEnd(session: GameSession) {
        val charId = session.roleId.toLong()
        val next = stateMachine.advance(charId)
        if (next != null) dispatchResult(session, next) else cancelAndEnd(session, charId)
    }

    // ══════════════════════════════════════════════
    //  Door 傳送後自動 MeetDoor
    // ══════════════════════════════════════════════

    // ══════════════════════════════════════════════
    //  事件完成
    // ══════════════════════════════════════════════

    /**
     * 完成當前事件 — 統一入口，嘗試 auto-chain 或結束
     */
    private suspend fun completeCurrentEvent(session: GameSession, charId: Long) {
        val completedSession = completeEventInternal(charId)

        if (completedSession != null && autoChainEngine.shouldAttemptAutoChain(completedSession)) {
            when (val chainResult = autoChainEngine.tryAutoChain(session, charId, completedSession)) {
                is AutoChainResult.Chained -> {
                    dispatchResult(session, chainResult.firstResult)
                    return
                }
                is AutoChainResult.NoMatch -> { /* 繼續到 sendEventEnd */ }
            }
        }

        responseSender.sendEventEnd(session)
    }

    /**
     * 強制結束事件 — 不嘗試 auto-chain
     *
     * Door 傳送、戰鬥失敗、Surface 取消等情境使用。
     */
    private suspend fun finalCompleteAndEnd(session: GameSession, charId: Long) {
        completeEventInternal(charId)
        responseSender.sendEventEnd(session)
    }

    /**
     * 取消事件 — 清除狀態但不記錄完成
     *
     * Surface 取消（code=40）等玩家主動取消情境使用。
     * 與 finalCompleteAndEnd 的差異：不記錄 condNo、不持久化 DB。
     */
    private fun cancelAndEnd(session: GameSession, charId: Long) {
        npcShopManager.clearShop(charId)
        stateMachine.clear(charId)
        responseSender.sendEventEnd(session)
    }

    /**
     * 判斷 NPC 是否為客棧類型
     *
     * 掃描 NPC 的所有事件結果，若包含 pStyle=4（客棧 UI）或 pStyle=5（住宿）
     * 則判定為客棧 NPC。用於 pStyle=1 上下文分流：客棧=儲存紀錄點 / 錢莊=開啟寶庫。
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
     * 儲存紀錄點 — 將玩家當前位置存入 DB 作為重生點
     */
    private suspend fun saveRecordPoint(session: GameSession, charId: Long) {
        val mapId = session.mapId
        val x = session.x
        val y = session.y
        try {
            characterRepository.updateRecordPoint(charId, mapId, x, y)
            logger.info { "${session.characterName} 儲存紀錄點：mapId=$mapId ($x,$y)" }
        } catch (e: Exception) {
            logger.error(e) { "${session.characterName} 儲存紀錄點失敗：mapId=$mapId ($x,$y)" }
        }
    }

    /**
     * 完成事件內部邏輯 — 記錄完成 + 持久化
     */
    private suspend fun completeEventInternal(charId: Long): EventSession? {
        val completedSession = stateMachine.complete(charId)

        // 事件結束 → 清除商店狀態
        npcShopManager.clearShop(charId)

        // 持久化事件完成記錄（class=12 條件依賴完成次數，Talk-only 事件也需計入）
        if (completedSession != null && completedSession.eveNo != 0) {
            try {
                questRepository.insertCompletedEvent(charId, completedSession.mapId, completedSession.eveNo)
            } catch (e: Exception) {
                logger.error(e) { "持久化事件完成記錄失敗：charId=$charId mapId=${completedSession.mapId} eveNo=${completedSession.eveNo}" }
            }
        }

        return completedSession
    }
}
