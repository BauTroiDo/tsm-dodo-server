package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.NpcEventData
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender

private val logger = KotlinLogging.logger {}

/**
 * Eve 事件觸發路由器
 *
 * 職責：
 * - 6 種觸發路由（ClickNpc / BumpNpc / ScanNpc / ClickDoor / MeetDoor / ServerBump）
 * - 距離驗證
 * - Fallback Talk 處理
 * - 觸發後委派 [EveFlowController.startEvent] 進入分發流程
 *
 * 從 EveFlowEngine.triggerEvent() / triggerServerBump() 抽取。
 */
class EveTriggerRouter(
    private val stateMachine: EveEventStateMachine,
    private val stateBuilder: EveStateBuilder,
    private val conditionResolver: EveConditionResolver,
    private val flowController: EveFlowController,
    private val responseSender: ResponseSender,
    private val eveData: Map<Int, SceneEveData>
) {

    /**
     * C:020-{1~5,7,8} 事件觸發
     *
     * 觸發路徑：
     * - subKind=4/8（Door）：doors[triggerID].events
     * - subKind=2（BumpNpc）：WhenHappen[1]=true
     * - subKind=3（ScanNpc）：WhenHappen[2]=true
     * - subKind=1（ClickNpc）：npcs[triggerID].events → fallback Talk
     */
    suspend fun triggerEvent(session: GameSession, subKind: Int, triggerID: Int) {
        val charId = session.roleId.toLong()
        val mapId = session.mapId

        val triggerName = TRIGGER_NAMES[subKind] ?: "Unknown"
        logger.info { "${session.characterName} 觸發事件：$triggerName (sub=$subKind) triggerID=$triggerID mapId=$mapId" }

        val sceneData = eveData[mapId]
        if (sceneData == null) {
            logger.debug { "場景 $mapId 無事件資料" }
            responseSender.sendEventEnd(session)
            return
        }

        val state = stateBuilder.buildPlayerState(charId, mapId, session)

        when {
            subKind == 4 || subKind == 8 -> routeDoor(session, charId, mapId, triggerID, subKind, sceneData, state)
            subKind == 2 || subKind == 3 -> routeWhenHappen(session, charId, mapId, triggerID, subKind, sceneData, state)
            else -> routeClickNpc(session, charId, mapId, triggerID, subKind, sceneData, state)
        }
    }

    /** NPC 當前巡邏位置查詢器：mapId, npcId -> (x, y) */
    var npcPositionProvider: ((mapId: Int, npcId: Int) -> Pair<Int, Int>?)? = null

    /**
     * 伺服器碰撞觸發（ServerBump）
     *
     * 由 NpcPatrolSystem 在偵測到玩家進入 NPC traceRadius 範圍時呼叫。
     * 掃描 whenHappen[3]=OnSerStrokeObject 的事件。
     *
     * @return true 如果成功觸發事件
     */
    suspend fun triggerServerBump(session: GameSession, npcClickId: Int): Boolean {
        val charId = session.roleId.toLong()
        val mapId = session.mapId

        val sceneData = eveData[mapId] ?: return false
        val state = stateBuilder.buildPlayerState(charId, mapId, session)

        val npc = sceneData.npcs[npcClickId]
        val candidates = if (npc != null && npc.events.isNotEmpty()) {
            val npcSpecific = npc.events.mapNotNull { sceneData.npcEvents[it] }.filter {
                it.whenHappen.size > 3 && it.whenHappen[3]
            }
            if (npcSpecific.isNotEmpty()) npcSpecific
            else npc.events.mapNotNull { sceneData.npcEvents[it] }
        } else {
            sceneData.npcEvents.values.filter {
                it.whenHappen.size > 3 && it.whenHappen[3]
            }
        }
        if (candidates.isEmpty()) {
            logger.debug { "場景 $mapId 無 ServerBump 事件 (npcClickId=$npcClickId)" }
            return false
        }

        for (eventData in candidates) {
            val result = resolveActivateAndAdvance(charId, mapId, npcClickId, eventData, state, triggerKind = 3)
            if (result != null) {
                logger.info { "${session.characterName} ServerBump 觸發：eveNo=${eventData.eveNo} clickId=$npcClickId mapId=$mapId" }
                flowController.startEvent(session, result)
                return true
            }
        }

        logger.debug { "場景 $mapId ServerBump 無匹配事件（${candidates.size} 個候選, clickId=$npcClickId）" }
        return false
    }

    // ══════════════════════════════════════════════
    //  私有路由
    // ══════════════════════════════════════════════

    /** Door 事件（ClickDoor=4, MeetDoor=8） */
    private suspend fun routeDoor(
        session: GameSession, charId: Long, mapId: Int,
        triggerID: Int, subKind: Int,
        sceneData: SceneEveData, state: PlayerEventState
    ) {
        if (subKind == 8 &&
            session.lastDoorWarpMapId == mapId &&
            System.currentTimeMillis() - session.lastDoorWarpTime < 2000L) {
            logger.info { "MeetDoor 冷卻中（距上次傳送 ${System.currentTimeMillis() - session.lastDoorWarpTime}ms）→ 忽略" }
            responseSender.sendEventEnd(session)
            return
        }
        val door = sceneData.doors[triggerID]
        if (door == null) {
            logger.warn { "場景 $mapId doorId=$triggerID 找不到 Door 擺放資料（doors.keys=${sceneData.doors.keys}）" }
            responseSender.sendEventEnd(session)
            return
        }
        for (eveNo in door.events) {
            val eventData = sceneData.npcEvents[eveNo] ?: continue
            val result = resolveActivateAndAdvance(charId, mapId, triggerID, eventData, state, triggerKind = subKind)
            if (result != null) { flowController.startEvent(session, result); return }
            logger.info { "Door eveNo=$eveNo 不匹配（${eventData.conditions.size} 個條件）" }
        }
        logger.warn { "場景 $mapId doorId=$triggerID Door 無可觸發條件（${door.events.size} 個事件全部失敗）" +
            " events=${door.events} 玩家=(${session.x},${session.y})" }
        responseSender.sendEventEnd(session)
    }

    /** BumpNpc / ScanNpc — WhenHappen 掃描 */
    private suspend fun routeWhenHappen(
        session: GameSession, charId: Long, mapId: Int,
        triggerID: Int, subKind: Int,
        sceneData: SceneEveData, state: PlayerEventState
    ) {
        val whenIdx = if (subKind == 2) 1 else 2
        val npc = sceneData.npcs[triggerID]
        val candidates = if (npc != null && npc.events.isNotEmpty()) {
            val npcSpecific = npc.events.mapNotNull { sceneData.npcEvents[it] }.filter {
                it.whenHappen.size > whenIdx && it.whenHappen[whenIdx]
            }
            if (npcSpecific.isNotEmpty()) {
                npcSpecific
            } else {
                val allNpcEvents = npc.events.mapNotNull { sceneData.npcEvents[it] }
                if (allNpcEvents.isNotEmpty()) allNpcEvents
                else sceneData.npcEvents.values.filter { it.whenHappen.size > whenIdx && it.whenHappen[whenIdx] }
            }
        } else {
            sceneData.npcEvents.values.filter { it.whenHappen.size > whenIdx && it.whenHappen[whenIdx] }
        }
        for (eventData in candidates) {
            val result = resolveActivateAndAdvance(charId, mapId, triggerID, eventData, state, triggerKind = subKind)
            if (result != null) { flowController.startEvent(session, result); return }
        }
        logger.debug { "場景 $mapId sub=$subKind 無可觸發的 WhenHappen 事件 (triggerID=$triggerID)" }
        responseSender.sendEventEnd(session)
    }

    /** ClickNpc — NpcData.events 路徑 */
    private suspend fun routeClickNpc(
        session: GameSession, charId: Long, mapId: Int,
        triggerID: Int, subKind: Int,
        sceneData: SceneEveData, state: PlayerEventState
    ) {
        val npc = sceneData.npcs[triggerID]
        if (npc == null) {
            logger.debug { "場景 $mapId clickId=$triggerID 找不到 NPC 擺放資料" }
            responseSender.sendEventEnd(session)
            return
        }
        val currentPos = npcPositionProvider?.invoke(mapId, triggerID)
        val targetX = currentPos?.first ?: npc.x
        val targetY = currentPos?.second ?: npc.y
        if (subKind == 1 && !stateBuilder.isWithinRange(session.x, session.y, targetX, targetY) && !stateBuilder.isWithinRange(session.x, session.y, npc.x, npc.y)) {
            logger.debug { "NPC 距離過遠：clickId=$triggerID player=(${session.x},${session.y}) npc=($targetX,$targetY)" }
            responseSender.sendEventEnd(session)
            return
        }

        // 1. NPC 自身事件列表 — 評估全部 eveNo，選 stepScore 最高者
        //    stepScore > 0 表示有 cc=2 任務條件，優先於 stepScore=0 的服務事件
        logger.info { "${session.characterName} ClickNpc clickId=$triggerID events=${npc.events} " +
            "玩家狀態：level=${state.level} reborn=${state.rebornCount} " +
            "missions=${state.missionSteps.entries.joinToString { "${it.key}=${it.value}" }} " +
            "flags=${state.missionFlags.entries.joinToString { "${it.key}=${it.value}" }}" }
        var bestEventData: NpcEventData? = null
        var bestResolved: ConditionResolveResult? = null
        var bestStepScore = -1

        for (eveNo in npc.events) {
            val eventData = sceneData.npcEvents[eveNo] ?: continue

            val resolved = conditionResolver.resolveEvent(
                eventData, state.copy(currentEveNo = eveNo), mapId
            )
            if (resolved == null) {
                logger.info { "${session.characterName} eveNo=$eveNo 不匹配（${eventData.conditions.size} 條件）" }
                continue
            }

            // condNo 級別防重複：只擋已完成的特定條件鏈，允許同 eveNo 的不同條件進展
            if (stateMachine.isConditionCompleted(charId, mapId, eveNo, resolved.matchedConditionNo)) {
                logger.info { "${session.characterName} eveNo=$eveNo condNo=${resolved.matchedConditionNo} 已完成，跳過" }
                continue
            }

            logger.debug { "${session.characterName} eveNo=$eveNo 匹配成功 condNo=${resolved.matchedConditionNo} stepScore=${resolved.stepScore} → ${resolved.results.size} 個結果" }

            // 三層排序：stepScore → 結果數量 → 先出現者
            // 結果數量區分任務對話鏈（10+ results）與服務選單（1 result）
            val bestResultCount = bestResolved?.results?.size ?: 0
            val isBetter = resolved.stepScore > bestStepScore ||
                (resolved.stepScore == bestStepScore && resolved.results.size > bestResultCount)

            if (isBetter) {
                bestEventData = eventData
                bestResolved = resolved
                bestStepScore = resolved.stepScore
            }
        }

        val chosenEvent = bestEventData
        val chosenResolved = bestResolved
        if (chosenEvent != null && chosenResolved != null) {
            logger.debug { "${session.characterName} 最佳匹配：eveNo=${chosenEvent.eveNo} stepScore=$bestStepScore" }
            stateMachine.activate(charId, EventSession(
                mapId = mapId, eveNo = chosenEvent.eveNo, npcClickId = triggerID,
                triggerKind = subKind, results = chosenResolved.results,
                matchedConditionNo = chosenResolved.matchedConditionNo
            ))
            val first = stateMachine.advance(charId)
            if (first != null) { flowController.startEvent(session, first); return }
        }

        // 2. Fallback Talk
        val fallbackTalk = stateBuilder.findFallbackTalk(sceneData, npc.events)
        if (fallbackTalk != null) {
            stateMachine.activate(charId, EventSession(
                mapId = mapId, eveNo = 0, npcClickId = triggerID,
                triggerKind = subKind,
                results = listOf(fallbackTalk)
            ))
            val first = stateMachine.advance(charId)
            if (first != null) {
                logger.info { "Fallback Talk：charId=$charId clickId=$triggerID → talkId=${fallbackTalk.resultMeanNo}" }
                flowController.startEvent(session, first)
                return
            }
        }

        logger.debug { "場景 $mapId clickId=$triggerID 無可觸發事件" }
        responseSender.sendEventEnd(session)
    }

    // ══════════════════════════════════════════════
    //  私有工具
    // ══════════════════════════════════════════════

    /**
     * 條件解析 + 啟動 Session + 取得第一個 result
     */
    private fun resolveActivateAndAdvance(
        charId: Long, mapId: Int, npcClickId: Int,
        eventData: NpcEventData,
        state: PlayerEventState,
        triggerKind: Int = 0, chainDepth: Int = 0
    ): EveResult? {
        val resolved = conditionResolver.resolveEvent(eventData, state, mapId) ?: return null

        stateMachine.activate(charId, EventSession(
            mapId = mapId, eveNo = eventData.eveNo, npcClickId = npcClickId,
            triggerKind = triggerKind, chainDepth = chainDepth,
            results = resolved.results,
            matchedConditionNo = resolved.matchedConditionNo
        ))

        logger.debug { "觸發事件：charId=$charId mapId=$mapId clickId=$npcClickId eveNo=${eventData.eveNo} → ${resolved.results.size} 個 result" }
        return stateMachine.advance(charId)
    }

    companion object {
        /** 事件觸發類型名稱對照（EEventTrigger） */
        private val TRIGGER_NAMES = mapOf(
            1 to "ClickNpc",
            2 to "BumpNpc",
            3 to "ScanNpc",
            4 to "ClickDoor",
            5 to "NotInUse",
            7 to "Mine",
            8 to "MeetDoor"
        )
    }
}
