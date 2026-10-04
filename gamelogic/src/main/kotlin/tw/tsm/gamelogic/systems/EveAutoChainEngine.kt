package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.NpcEventData
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession

private val logger = KotlinLogging.logger {}

/**
 * Kết quả Auto-chain (Tự động nối tiếp chuỗi sự kiện)
 */
sealed class AutoChainResult {
    /** Liên kết thành công đến sự kiện mới */
    data class Chained(val firstResult: EveResult) : AutoChainResult()
    /** Không có sự kiện phù hợp */
    data object NoMatch : AutoChainResult()
}

/**
 * Engine Eve Auto-Chain — Tự động nối tiếp sự kiện
 *
 * Nhiệm vụ:
 * - Đánh giá xem sự kiện đã hoàn thành có nên thử auto-chain hay không (shouldAttemptAutoChain)
 * - Đánh giá lại danh sách sự kiện của cùng một NPC/Cửa, tìm kiếm sự kiện phù hợp mới (tryAutoChain)
 * - 4 lớp bảo vệ chống lặp trên cùng một eveNo (mỗi lớp đều là phương thức công khai, có thể unit test độc lập)
 * - Đánh giá mốc tiến độ (shouldSkipEvent)
 *
 * Cho phép auto-chain trên cùng một eveNo (áp dụng cho việc tự động chuyển tiếp trong hướng dẫn/chuyển cảnh),
 * nhưng có 4 lớp bảo vệ để đảm bảo không bị lặp vô hạn.
 */
class EveAutoChainEngine(
    private val stateMachine: EveEventStateMachine,
    private val stateBuilder: EveStateBuilder,
    private val conditionResolver: EveConditionResolver,
    private val eveData: Map<Int, SceneEveData>
) {

    /**
     * Đánh giá xem có nên thử auto-chain hay không
     *
     * Điều kiện: Loại kích hoạt chủ động + Không phải Talk dự phòng (fallback) + Chưa vượt quá độ sâu tối đa
     */
    fun shouldAttemptAutoChain(completedSession: EventSession): Boolean {
        return completedSession.triggerKind in AUTO_CHAIN_TRIGGER_KINDS &&
                completedSession.eveNo != 0 &&
                completedSession.chainDepth < MAX_CHAIN_DEPTH
    }

    /**
     * Thử auto-chain: Đánh giá lại điều kiện sự kiện của cùng một NPC/Cửa
     *
     * Trạng thái người chơi đã thay đổi do các results (bước nhiệm vụ, cờ, v.v.), sau khi buildPlayerState lại
     * sẽ quét danh sách sự kiện của cùng một NPC/Cửa, nếu tìm thấy sự kiện phù hợp mới sẽ tiếp nối liền mạch.
     *
     * Cùng một eveNo cho phép auto-chain, nhưng bị giới hạn bởi 4 lớp bảo vệ:
     * #1 Phát hiện chuỗi điều kiện trùng lặp, #2 Hỏi lại câu hỏi, #3 Đánh lại trận chiến, #4 Trùng lặp vật phẩm
     *
     * @return [AutoChainResult.Chained] nếu liên kết thành công, [AutoChainResult.NoMatch] nếu không có sự kiện phù hợp
     */
    suspend fun tryAutoChain(
        session: GameSession,
        charId: Long,
        completedSession: EventSession
    ): AutoChainResult {
        // Xác nhận bối cảnh (map) đồng nhất
        if (session.mapId != completedSession.mapId) return AutoChainResult.NoMatch

        val sceneData = eveData[completedSession.mapId] ?: return AutoChainResult.NoMatch
        val eventList = getEventList(sceneData, completedSession) ?: return AutoChainResult.NoMatch

        // Tái cấu trúc trạng thái (results đã làm thay đổi trạng thái người chơi)
        val state = stateBuilder.buildPlayerState(charId, completedSession.mapId, session)
            .copy(
                battleResult = completedSession.battleResult,
                lastSurfaceId = completedSession.lastSurfaceId,
                lastChoiceCode = completedSession.lastChoiceCode,
            )
        val newDepth = completedSession.chainDepth + 1

        for (eveNo in eventList) {
            val eventData = sceneData.npcEvents[eveNo] ?: continue
            val sameEvent = eveNo == completedSession.eveNo
            // pStyle=3 của resultClass=2 là COMPLETE_MISSION. Sau khi một chain
            // đã kết thúc mission, không được nối tiếp cùng EveNo; nếu không các
            // condition kiểu step<5 sẽ thấy step đã bị xóa về 0 và mở lại mini-game.
            if (sameEvent && hasTerminalMissionCompletion(completedSession)) continue
            if (!sameEvent && shouldSkipEvent(eventList, eveNo, eventData, state)) {
                logger.debug { "${session.characterName} bỏ qua auto-chain eveNo=$eveNo (Đã hoàn thành hoặc tiến độ đã vượt qua)" }
                continue
            }

            val resolved = if (sameEvent) {
                conditionResolver.resolveEventInDataOrder(
                    eventData = eventData,
                    state = state,
                    mapId = completedSession.mapId,
                    afterConditionNo = completedSession.matchedConditionNo,
                )
            } else {
                conditionResolver.resolveEvent(eventData, state, completedSession.mapId)
            }
            if (resolved != null) {
                // Tạo session ứng viên
                val candidateSession = EventSession(
                    mapId = completedSession.mapId,
                    eveNo = eventData.eveNo,
                    npcClickId = completedSession.npcClickId,
                    triggerKind = completedSession.triggerKind,
                    chainDepth = newDepth,
                    results = resolved.results,
                    matchedConditionNo = resolved.matchedConditionNo
                )

                // 4 lớp bảo vệ cho cùng một eveNo
                if (eveNo == completedSession.eveNo) {
                    // #1 Phát hiện vòng lặp: Cùng chuỗi điều kiện (matchedConditionNo)
                    if (detectSameChain(candidateSession, completedSession)) {
                        logger.debug { "${session.characterName} auto-chain eveNo=$eveNo khớp với chuỗi điều kiện cũ (condNo=${candidateSession.matchedConditionNo}), phát hiện vòng lặp, chấm dứt" }
                        return AutoChainResult.NoMatch
                    }
                    // #2 Hỏi lại câu hỏi
                    if (detectReQuestion(candidateSession, completedSession)) {
                        logger.debug { "${session.characterName} auto-chain eveNo=$eveNo xuất hiện lại Surface sau khi trả lời, bỏ qua" }
                        continue
                    }
                    // #3 Đánh lại trận chiến
                    if (detectReBattle(candidateSession, completedSession)) {
                        logger.debug { "${session.characterName} auto-chain eveNo=$eveNo xuất hiện lại Surface/Battle sau trận chiến, bỏ qua" }
                        continue
                    }
                    // #4 Trùng lặp vật phẩm
                    if (detectDuplicateItems(candidateSession, completedSession)) {
                        logger.info { "${session.characterName} auto-chain eveNo=$eveNo chứa vật phẩm đã được trao, bỏ qua để tránh trùng lặp" }
                        continue
                    }
                }

                // Vượt qua tất cả lớp bảo vệ — Khởi động session mới
                stateMachine.activate(charId, candidateSession)
                val firstResult = stateMachine.advance(charId)
                if (firstResult != null) {
                    logger.info { "${session.characterName} auto-chain thành công: Hoàn thành eveNo=${completedSession.eveNo} → liên kết eveNo=${eventData.eveNo} (depth=$newDepth)" }
                    return AutoChainResult.Chained(firstResult)
                }
            }
        }

        logger.debug { "${session.characterName} auto-chain không tìm thấy kết quả phù hợp (depth=$newDepth), kết thúc sự kiện" }
        return AutoChainResult.NoMatch
    }

    // ══════════════════════════════════════════════
    //  4 Lớp bảo vệ (Phương thức công khai, có thể unit test độc lập)
    // ══════════════════════════════════════════════

    /**
     * #1 Phát hiện vòng lặp: Sự kiện mới khớp với cùng chuỗi điều kiện AND của sự kiện đã hoàn thành
     *
     * Chuyển sang dùng matchedConditionNo (tính xác định) thay vì so sánh results (bị ảnh hưởng bởi tính ngẫu nhiên của GroupData).
     * Khớp trùng lặp cùng một chuỗi điều kiện = Trạng thái người chơi không thay đổi = Vòng lặp vô hạn.
     *
     * Lưu ý: conditionNo bắt đầu từ 0, 0 là giá trị hợp lệ (chuỗi điều kiện đầu tiên của sự kiện).
     * Trong tryAutoChain(), cả hai matchedConditionNo đều lấy từ resolveEvent(), sẽ không có trường hợp chưa được thiết lập.
     */
    fun detectSameChain(newSession: EventSession, completedSession: EventSession): Boolean {
        return newSession.matchedConditionNo == completedSession.matchedConditionNo
    }

    /**
     * #2 Bảo vệ hỏi lại câu hỏi: Cùng eveNo lại xuất hiện Surface sau khi đã trả lời
     *
     * Người chơi vừa trả lời tùy chọn (lastChoiceCode != -1), cùng sự kiện lại tạo ra Surface → Hỏi lại.
     */
    fun detectReQuestion(newSession: EventSession, completedSession: EventSession): Boolean {
        return completedSession.lastChoiceCode != -1 &&
                newSession.results.any { it.resultType == 6 }
    }

    /**
     * #3 Bảo vệ đánh lại trận chiến: Cùng eveNo lại xuất hiện Surface/Battle sau trận chiến
     *
     * Người chơi vừa hoàn thành trận chiến (battleResult != 0), cùng sự kiện lại tạo ra result tương tác → Đánh lại.
     */
    fun detectReBattle(newSession: EventSession, completedSession: EventSession): Boolean {
        return completedSession.battleResult != 0 &&
                newSession.results.any { it.resultType == 6 || it.resultType == 3 }
    }

    /**
     * #4 Bảo vệ trùng lặp vật phẩm: Cùng eveNo lại trao cùng một vật phẩm
     *
     * Sự kiện đã hoàn thành trao vật phẩm A, sự kiện mới lại muốn trao vật phẩm A → Trùng lặp.
     */
    /** Mission pStyle=3 đã kết thúc; cùng EveNo phải dừng ở đây. */
    fun hasTerminalMissionCompletion(completedSession: EventSession): Boolean =
        completedSession.results.any {
            it.resultClass == 2 && it.parameterStyle == 3
        }

    fun detectDuplicateItems(newSession: EventSession, completedSession: EventSession): Boolean {
        val completedGiveItems = completedSession.results
            .filter { it.resultClass == 1 && it.parameterStyle == 1 }
            .map { it.parameter }.toSet()
        if (completedGiveItems.isEmpty()) return false

        val newGiveItems = newSession.results
            .filter { it.resultClass == 1 && it.parameterStyle == 1 }
            .map { it.parameter }.toSet()

        return completedGiveItems.intersect(newGiveItems).isNotEmpty()
    }

    // ══════════════════════════════════════════════
    //  Mốc tiến độ
    // ══════════════════════════════════════════════

    /**
     * Kiểm tra xem sự kiện có nên bị bỏ qua do đã hoàn thành hoặc tiến độ đã vượt qua hay không
     *
     * Quy tắc 1: Đã hoàn thành và không có conditionClass=12 (Điều kiện đếm tự quản lý) → Bỏ qua
     * Quy tắc 2: Chưa hoàn thành nhưng sự kiện tiếp theo đã hoàn thành (Mốc tiến độ) → Bỏ qua
     */
    fun shouldSkipEvent(
        events: List<Int>, eveNo: Int,
        eventData: NpcEventData, state: PlayerEventState
    ): Boolean {
        val completedCount = state.completedEveCounts[eveNo] ?: 0
        val hasCompletionCondition = eventData.conditions.any { it.conditionClass == 12 }
        // 1. Đã hoàn thành và không có điều kiện tự quản lý đếm → Bỏ qua
        if (completedCount > 0 && !hasCompletionCondition) return true
        // 2. Mốc tiến độ: Các sự kiện phía sau đã hoàn thành → Sự kiện này đã bị vượt qua
        if (completedCount == 0 && hasLaterCompletedEvent(events, eveNo, state)) return true
        return false
    }

    /**
     * Kiểm tra trong danh sách sự kiện, sau currentEveNo có sự kiện nào đã hoàn thành chưa
     *
     * Dùng để đánh giá mốc tiến độ: Nếu sự kiện tiếp theo đã hoàn thành, có nghĩa là tiến độ của người chơi đã vượt qua sự kiện hiện tại.
     */
    private fun hasLaterCompletedEvent(
        events: List<Int>, currentEveNo: Int, state: PlayerEventState
    ): Boolean {
        var found = false
        for (e in events) {
            if (e == currentEveNo) { found = true; continue }
            if (found && (state.completedEveCounts[e] ?: 0) > 0) return true
        }
        return false
    }

    /**
     * Lấy danh sách sự kiện tương ứng dựa theo loại kích hoạt
     */
    private fun getEventList(
        sceneData: SceneEveData,
        session: EventSession
    ): List<Int>? = when (session.triggerKind) {
        1 -> sceneData.npcs[session.npcClickId]?.events
        4, 8 -> sceneData.doors[session.npcClickId]?.events
        else -> null
    }

    companion object {
        /** Độ sâu tối đa của auto-chain (Phòng vòng lặp vô hạn) */
        const val MAX_CHAIN_DEPTH = 10

        /** Các loại kích hoạt cho phép auto-chain: ClickNpc=1, ClickDoor=4, MeetDoor=8 */
        val AUTO_CHAIN_TRIGGER_KINDS = setOf(1, 4, 8)
    }
}