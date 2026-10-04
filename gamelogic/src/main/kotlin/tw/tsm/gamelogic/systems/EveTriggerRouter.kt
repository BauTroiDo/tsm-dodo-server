package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.NpcEventData
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender

private val logger = KotlinLogging.logger {}

/**
 * Bộ định tuyến kích hoạt sự kiện Eve
 *
 * Nhiệm vụ:
 * - 6 loại định tuyến kích hoạt (ClickNpc / BumpNpc / ScanNpc / ClickDoor / MeetDoor / ServerBump)
 * - Kiểm tra khoảng cách
 * - Xử lý Talk fallback
 * - Sau khi kích hoạt, ủy quyền cho [EveFlowController.startEvent] đi vào luồng phân phát
 *
 * Tách khỏi EveFlowEngine.triggerEvent() / triggerServerBump().
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
     * C:020-{1~5,7,8} kích hoạt sự kiện
     *
     * Đường dẫn kích hoạt:
     * - subKind=4/8（Door）：doors[triggerID].events
     * - subKind=2（BumpNpc）：WhenHappen[1]=true
     * - subKind=3（ScanNpc）：WhenHappen[2]=true
     * - subKind=1（ClickNpc）：npcs[triggerID].events → fallback Talk
     */
    suspend fun triggerEvent(session: GameSession, subKind: Int, triggerID: Int) {
        val charId = session.roleId.toLong()
        val mapId = session.mapId

        val triggerName = TRIGGER_NAMES[subKind] ?: "Unknown"
        logger.info { "${session.characterName} kích hoạt sự kiện: $triggerName (sub=$subKind) triggerID=$triggerID mapId=$mapId" }

        val sceneData = eveData[mapId]
        if (sceneData == null) {
            logger.debug { "Cảnh $mapId không có dữ liệu sự kiện" }
            responseSender.sendEventEnd(session)
            return
        }

        val state = stateBuilder.buildPlayerState(charId, mapId, session)

        when {
            subKind == 4 || subKind == 8 -> routeDoor(session, charId, mapId, triggerID, subKind, sceneData, state)
            subKind == 7 -> {
                if (!triggerMine(session, triggerID)) {
                    responseSender.sendEventEnd(session)
                }
            }
            subKind == 2 || subKind == 3 -> routeWhenHappen(session, charId, mapId, triggerID, subKind, sceneData, state)
            else -> routeClickNpc(session, charId, mapId, triggerID, subKind, sceneData, state)
        }
    }

    /** Bộ truy vấn vị trí tuần tra hiện tại của NPC: mapId, npcId -> (x, y)*/
    var npcPositionProvider: ((mapId: Int, npcId: Int) -> Pair<Int, Int>?)? = null

    /**
     * Kích hoạt script mở màn của một DungeonData sau khi client đã tải scene.
     * Chỉ nhận Door event có WhenHappen[1] và condition/result cùng missionFlag
     * của dungeon, nên không đụng Door thường hay chạy lại khi mission đã qua step đầu.
     */
    suspend fun triggerDungeonEntry(session: GameSession, missionFlag: Int): Boolean {
        if (missionFlag <= 0 || session.battleId != 0L) return false
        val charId = session.roleId.toLong()
        if (stateMachine.getSession(charId) != null) return false
        val mapId = session.mapId
        val sceneData = eveData[mapId] ?: return false
        val state = stateBuilder.buildPlayerState(charId, mapId, session)

        for ((doorId, door) in sceneData.doors.toSortedMap()) {
            for (eveNo in door.events) {
                val eventData = sceneData.npcEvents[eveNo] ?: continue
                if (eventData.whenHappen.size <= 1 || !eventData.whenHappen[1]) continue
                if (eventData.conditions.none { it.conditionClass == 2 && it.conditionParameter == missionFlag }) continue

                val resolved = conditionResolver.resolveEvent(eventData, state, mapId) ?: continue
                val matched = eventData.conditions.firstOrNull { it.conditionNo == resolved.matchedConditionNo }
                    ?: continue
                if (matched.conditionClass != 2 || matched.conditionParameter != missionFlag) continue
                if (resolved.results.none {
                        it.resultClass == 2 && it.parameter == missionFlag &&
                            it.parameterStyle == 1 && it.resultValue > 0
                    }) continue

                stateMachine.activate(
                    charId,
                    EventSession(
                        mapId = mapId,
                        eveNo = eveNo,
                        npcClickId = doorId,
                        triggerKind = 4,
                        results = resolved.results,
                        matchedConditionNo = resolved.matchedConditionNo,
                    )
                )
                val first = stateMachine.advance(charId) ?: continue
                logger.info {
                    "${session.characterName} auto-entry dungeon: map=$mapId door=$doorId " +
                        "eve=$eveNo mission=$missionFlag cond=${resolved.matchedConditionNo}"
                }
                flowController.startEvent(session, first)
                return true
            }
        }
        return false
    }

    /**
     * Server-side Mine trigger (EEventTrigger.Mine=7).
     *
     * DVT client EventManager.lua ghi rõ Mine hiện do server kích hoạt. MineData.events
     * trỏ trực tiếp tới EveNo; vì vậy không đi qua NpcData/WhenHappen.
     */
    suspend fun triggerMine(session: GameSession, mineId: Int): Boolean {
        val charId = session.roleId.toLong()
        val mapId = session.mapId
        val sceneData = eveData[mapId] ?: return false
        val mine = sceneData.mines[mineId] ?: return false

        // Không đè một hội thoại/battle/event đang chạy.
        if (stateMachine.getSession(charId) != null || session.battleId != 0L) return false

        val state = stateBuilder.buildPlayerState(charId, mapId, session)
        for (eveNo in mine.events) {
            val eventData = sceneData.npcEvents[eveNo] ?: continue
            val result = resolveActivateAndAdvance(
                charId = charId,
                mapId = mapId,
                npcClickId = mineId,
                eventData = eventData,
                state = state,
                triggerKind = 7,
                onlyIfIdle = true
            )
            if (result != null) {
                logger.info {
                    "${session.characterName} Mine kích hoạt: map=$mapId mineId=$mineId " +
                        "eveNo=$eveNo pos=(${session.x},${session.y})"
                }
                flowController.startEvent(session, result)
                return true
            }
        }

        logger.debug { "Mine không có event khớp: map=$mapId mineId=$mineId events=${mine.events}" }
        return false
    }

    /**
     * Kích hoạt va chạm phía server (ServerBump)
     *
     * Được NpcPatrolSystem gọi khi phát hiện người chơi đi vào phạm vi traceRadius của NPC.
     * Quét các sự kiện whenHappen[3]=OnSerStrokeObject.
     *
     * @return true nếu kích hoạt sự kiện thành công
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
            logger.debug { "Cảnh $mapId không có sự kiện ServerBump (npcClickId=$npcClickId)" }
            return false
        }

        for (eventData in candidates) {
            val result = resolveActivateAndAdvance(
                charId, mapId, npcClickId, eventData, state,
                triggerKind = 3, onlyIfIdle = true
            )
            if (result != null) {
                logger.info { "${session.characterName} ServerBump kích hoạt: eveNo=${eventData.eveNo} clickId=$npcClickId mapId=$mapId" }
                flowController.startEvent(session, result)
                return true
            }
        }

        logger.debug { "Cảnh $mapId ServerBump không có sự kiện khớp (${candidates.size} ứng viên, clickId=$npcClickId)" }
        return false
    }

    // ══════════════════════════════════════════════
    //  Định tuyến private
    // ══════════════════════════════════════════════

    /** Sự kiện Door (ClickDoor=4, MeetDoor=8)*/
    private suspend fun routeDoor(
        session: GameSession, charId: Long, mapId: Int,
        triggerID: Int, subKind: Int,
        sceneData: SceneEveData, state: PlayerEventState
    ) {
        if (subKind == 8 &&
            session.lastDoorWarpMapId == mapId &&
            System.currentTimeMillis() - session.lastDoorWarpTime < 2000L) {
            logger.info { "MeetDoor đang cooldown (cách lần dịch chuyển trước ${System.currentTimeMillis() - session.lastDoorWarpTime}ms) → bỏ qua" }
            responseSender.sendEventEnd(session)
            return
        }
        val door = sceneData.doors[triggerID]
        if (door == null) {
            logger.warn { "Cảnh $mapId doorId=$triggerID không tìm thấy dữ liệu bố trí Door (doors.keys=${sceneData.doors.keys})" }
            responseSender.sendEventEnd(session)
            return
        }
        for (eveNo in door.events) {
            val eventData = sceneData.npcEvents[eveNo] ?: continue
            val result = resolveActivateAndAdvance(charId, mapId, triggerID, eventData, state, triggerKind = subKind)
            if (result != null) { flowController.startEvent(session, result); return }
            logger.info { "Door eveNo=$eveNo không khớp (${eventData.conditions.size} điều kiện)" }
        }
        logger.warn { "Cảnh $mapId doorId=$triggerID Door không có điều kiện kích hoạt được (${door.events.size} sự kiện đều thất bại)" +
            " events=${door.events} người chơi=(${session.x},${session.y})" }
        responseSender.sendEventEnd(session)
    }

    /** BumpNpc / ScanNpc — quét WhenHappen*/
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
        logger.debug { "Cảnh $mapId sub=$subKind không có sự kiện WhenHappen nào kích hoạt được (triggerID=$triggerID)" }
        responseSender.sendEventEnd(session)
    }

    /** ClickNpc — đường dẫn NpcData.events*/
    private suspend fun routeClickNpc(
        session: GameSession, charId: Long, mapId: Int,
        triggerID: Int, subKind: Int,
        sceneData: SceneEveData, state: PlayerEventState
    ) {
        val npc = sceneData.npcs[triggerID]
        if (npc == null) {
            logger.debug { "Cảnh $mapId clickId=$triggerID không tìm thấy dữ liệu bố trí NPC" }
            responseSender.sendEventEnd(session)
            return
        }
        val currentPos = npcPositionProvider?.invoke(mapId, triggerID)
        val targetX = currentPos?.first ?: npc.x
        val targetY = currentPos?.second ?: npc.y
        if (subKind == 1 && !stateBuilder.isWithinRange(session.x, session.y, targetX, targetY) &&
            !stateBuilder.isWithinRange(session.x, session.y, npc.x, npc.y) &&
            !NpcClientPath.isWithinRoute(npc, session.x, session.y)) {
            logger.info { "NPC quá xa: map=$mapId clickId=$triggerID player=(${session.x},${session.y}) npc=($targetX,$targetY)" }
            responseSender.sendEventEnd(session)
            return
        }

        // 1. Danh sách sự kiện của bản thân NPC — đánh giá toàn bộ eveNo, chọn cái có stepScore cao nhất
        //    stepScore > 0 nghĩa là có điều kiện nhiệm vụ cc=2, ưu tiên hơn sự kiện phục vụ stepScore=0
        logger.info { "${session.characterName} ClickNpc clickId=$triggerID events=${npc.events} " +
            "Trạng thái người chơi: level=${state.level} reborn=${state.rebornCount} " +
            "missions=${state.missionSteps.entries.joinToString { "${it.key}=${it.value}" }} " +
            "flags=${state.missionFlags.entries.joinToString { "${it.key}=${it.value}" }}" }
        var bestEventData: NpcEventData? = null
        var bestResolved: ConditionResolveResult? = null
        var bestStepScore = -1

        for (eveNo in npc.events) {
            val eventData = sceneData.npcEvents[eveNo] ?: continue

            val stateWithEve = state.copy(currentEveNo = eveNo)
            val isBattleStateMachine =
                eventData.conditions.any { c -> c.results.any { it.resultType == 3 } } &&
                    eventData.conditions.any { it.conditionClass == 8 }
            val isProgressiveClickStateMachine = isProgressiveClickStateMachine(eventData)
            val isOrderedStateMachine = isBattleStateMachine || isProgressiveClickStateMachine
            val resolved = if (isOrderedStateMachine) {
                conditionResolver.resolveEventInDataOrder(eventData, stateWithEve, mapId)
            } else {
                conditionResolver.resolveEvent(eventData, stateWithEve, mapId)
            }
            if (resolved == null) {
                logger.info { "${session.characterName} eveNo=$eveNo không khớp (${eventData.conditions.size} điều kiện)" }
                continue
            }

            // Chống trùng lặp cấp condNo: chỉ chặn chuỗi điều kiện cụ thể đã hoàn thành, cho phép tiến triển điều kiện khác của cùng eveNo
            if (!isOrderedStateMachine && stateMachine.isConditionCompleted(charId, mapId, eveNo, resolved.matchedConditionNo)) {
                logger.info { "${session.characterName} eveNo=$eveNo condNo=${resolved.matchedConditionNo} đã hoàn thành, bỏ qua" }
                continue
            }

            logger.debug { "${session.characterName} eveNo=$eveNo khớp thành công condNo=${resolved.matchedConditionNo} stepScore=${resolved.stepScore} → ${resolved.results.size} kết quả" }

            // Sắp xếp 3 lớp: stepScore → số lượng kết quả → xuất hiện trước
            // Số lượng kết quả phân biệt chuỗi hội thoại nhiệm vụ (10+ results) với menu dịch vụ (1 result)
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
            logger.debug { "${session.characterName} khớp tốt nhất: eveNo=${chosenEvent.eveNo} stepScore=$bestStepScore" }
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

        logger.debug { "Cảnh $mapId clickId=$triggerID không có sự kiện để kích hoạt" }
        responseSender.sendEventEnd(session)
    }

    // ══════════════════════════════════════════════
    //  Tiện ích private
    // ══════════════════════════════════════════════

    /**
     * Event click nhiều pha kiểu DVT:
     * - một condition sớm tăng mission bằng resultClass=2/pStyle=1;
     * - các condition phía sau đọc lại cùng mission ở step cao hơn để phát thoại,
     *   kết thúc hoặc chuyển pha.
     *
     * Với shape này phải giữ thứ tự Eve.emg. Nếu dùng resolver ưu tiên stepScore,
     * condition hậu kỳ (ví dụ step=3) sẽ thắng condition tăng step, làm progression
     * đứng yên. Scene 62003 treasure events 5..10 là bằng chứng runtime.
     */
    private fun isProgressiveClickStateMachine(eventData: NpcEventData): Boolean {
        for (condition in eventData.conditions) {
            val incrementMissions = condition.results.asSequence()
                .filter {
                    it.resultClass == 2 &&
                        it.parameterStyle == 1 &&
                        it.resultValue > 0
                }
                .map { it.parameter }
                .filter { it > 0 }
                .toSet()

            for (missionId in incrementMissions) {
                val hasLaterStepReader = eventData.conditions.any { later ->
                    later.conditionNo != condition.conditionNo &&
                        later.conditionClass == 2 &&
                        later.conditionParameter == missionId &&
                        later.conditionValue > condition.conditionValue
                }
                if (hasLaterStepReader) return true
            }
        }
        return false
    }

    /**
     * Phân tích điều kiện + khởi tạo Session + lấy result đầu tiên
     */
    private fun resolveActivateAndAdvance(
        charId: Long, mapId: Int, npcClickId: Int,
        eventData: NpcEventData,
        state: PlayerEventState,
        triggerKind: Int = 0, chainDepth: Int = 0,
        onlyIfIdle: Boolean = false
    ): EveResult? {
        val resolved = conditionResolver.resolveEvent(eventData, state, mapId) ?: return null

        val eventSession = EventSession(
            mapId = mapId, eveNo = eventData.eveNo, npcClickId = npcClickId,
            triggerKind = triggerKind, chainDepth = chainDepth,
            results = resolved.results,
            matchedConditionNo = resolved.matchedConditionNo
        )
        if (onlyIfIdle) {
            if (!stateMachine.tryActivateIfIdle(charId, eventSession)) return null
        } else {
            stateMachine.activate(charId, eventSession)
        }

        logger.debug { "Kích hoạt sự kiện: charId=$charId mapId=$mapId clickId=$npcClickId eveNo=${eventData.eveNo} → ${resolved.results.size} result" }
        return stateMachine.advance(charId)
    }

    companion object {
        /** Bảng đối chiếu tên loại kích hoạt sự kiện (EEventTrigger)*/
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
