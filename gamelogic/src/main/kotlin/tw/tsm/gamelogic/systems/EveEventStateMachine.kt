package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/**
 * Giai đoạn vòng đời sự kiện (An toàn kiểu, đảm bảo tính loại trừ lẫn nhau/mutually exclusive)
 *
 * Thay thế các cờ boolean awaitingChoice / awaitingBattle ở phiên bản cũ,
 * được đảm bảo tính loại trừ lẫn nhau tại thời điểm biên dịch thông qua sealed class của Kotlin.
 */
sealed class EventPhase {
    /** Đang thực thi các results theo thứ tự */
    data object Executing : EventPhase()
    /** Đang chờ Client chọn Surface (C:020-009) */
    data object AwaitingChoice : EventPhase()
    /** Đang chờ kết thúc chiến đấu */
    data object AwaitingBattle : EventPhase()
    /** Đang thực hiện chuyển cảnh Door; bỏ qua C:020-006 đến sớm từ EventHandler[2]. */
    data object AwaitingWarp : EventPhase()
    /** Sự kiện đã hoàn thành (trạng thái kết thúc) */
    data object Completed : EventPhase()
}

/**
 * Phiên sự kiện đang diễn ra (Bất biến / Immutable)
 *
 * Tất cả các thay đổi trạng thái được cập nhật nguyên tử (atomic update) thông qua copy() + CAS, tránh điều kiện tương tranh.
 *
 * Trường phase cung cấp sự đảm bảo loại trừ lẫn nhau cho các giai đoạn vòng đời:
 * - 5 trạng thái Executing / AwaitingChoice / AwaitingBattle / AwaitingWarp / Completed loại trừ lẫn nhau.
 * - Các trường ngữ cảnh (lastSurfaceId, lastChoiceCode, battleResult) giữ lại lịch sử xuyên suốt các phase,
 *   dùng để đánh giá conditionClass=8/10 và bảo vệ chống lại auto-chain.
 *
 * @property mapId              ID Cảnh/Map kích hoạt
 * @property eveNo              Mã sự kiện được kích hoạt (0 = fallback Talk)
 * @property npcClickId         clickId của NPC/Door được kích hoạt
 * @property triggerKind        Loại kích hoạt (1=ClickNpc, 4=ClickDoor, 8=MeetDoor, v.v.)
 * @property chainDepth         Độ sâu auto-chain (chống vòng lặp vô hạn)
 * @property results            Hàng đợi kết quả của sự kiện lần này
 * @property currentIndex       Index sẽ được gửi vào lần tới
 * @property phase              Giai đoạn vòng đời sự kiện (loại trừ lẫn nhau qua sealed class)
 * @property lastSurfaceId      Surface ID được hiển thị cuối cùng (dùng để đối chiếu conditionClass=10)
 * @property lastChoiceCode     Mã lựa chọn cuối cùng (dùng để đối chiếu conditionClass=10, bảo vệ auto-chain #2)
 * @property battleResult       Kết quả chiến đấu (0=Không có, 1=Thắng, 2=Thua, 3=Bỏ chạy, dùng để đối chiếu conditionClass=8, bảo vệ auto-chain #3)
 * @property fightDataKey       FightData key của trận sự kiện vừa kích hoạt; class8 parameter=0 là wildcard, khác 0 phải khớp key này.
 * @property matchedConditionNo conditionNo đầu tiên của chuỗi AND khớp (dùng để phát hiện vòng lặp auto-chain, 0=chưa thiết lập)
 */
data class EventSession(
    val mapId: Int,
    val eveNo: Int,
    val npcClickId: Int,
    val triggerKind: Int = 0,
    val chainDepth: Int = 0,
    val results: List<EveResult>,
    val currentIndex: Int = 0,
    val phase: EventPhase = EventPhase.Executing,
    val lastSurfaceId: Int = -1,
    val lastChoiceCode: Int = -1,
    /** Đã claim C:020-009 nhưng chưa dispatch xong result đầu tiên; chặn C:020-006 đến sớm. */
    val choiceResolutionInProgress: Boolean = false,
    val battleResult: Int = 0,
    val fightDataKey: Int = 0,
    val matchedConditionNo: Int = 0
) {
    /** Có chứa kết quả dạng thay đổi trạng thái hay không, dùng để quyết định xem có ghi lại log hoàn thành để chống trùng lặp hay không */
    fun hasStateChangingResults(): Boolean = results.any {
        it.resultType == 0 || it.resultType == 3  // 0=Hiệu ứng phía server, 3=Chiến đấu
    }
}

/**
 * Máy trạng thái (State Machine) sự kiện Eve — Quản lý EventSession đang diễn ra của mỗi người chơi
 *
 * Chức năng:
 * - Duy trì map charId → EventSession
 * - Quản lý việc tịnh tiến (advance) hàng đợi kết quả
 * - Chuyển đổi trạng thái an toàn kiểu (qua sealed class EventPhase)
 * - Theo dõi log hoàn thành sự kiện cảnh (dùng để đếm cho conditionClass=12)
 *
 * An toàn đồng thời (Concurrency safe): EventSession là data class bất biến, cập nhật thông qua AtomicReference + CAS.
 */
class EveEventStateMachine {

    /** charId → Sự kiện đang diễn ra (AtomicReference bọc Session bất biến) */
    private val sessions = ConcurrentHashMap<Long, AtomicReference<EventSession>>()

    /**
     * Theo dõi hoàn thành sự kiện cảnh (cấp độ eveNo)
     *
     * charId → (mapId → (eveNo → số lần hoàn thành))
     * Ngữ nghĩa của conditionClass=12 param=0: Số lần eveNo hiện tại đã được hoàn thành.
     */
    private val completedEvents = ConcurrentHashMap<Long, ConcurrentHashMap<Int, ConcurrentHashMap<Int, Int>>>()

    /**
     * Theo dõi hoàn thành sự kiện cảnh (cấp độ conditionNo)
     *
     * charId → (mapId → (eveNo → tập hợp conditionNo đã hoàn thành))
     * Dùng để ngăn chặn chính xác việc kích hoạt lặp lại của cùng một chuỗi điều kiện, đồng thời cho phép các chuỗi điều kiện khác nhau của cùng một eveNo hoạt động bình thường.
     */
    private val completedConditions = ConcurrentHashMap<Long, ConcurrentHashMap<Int, ConcurrentHashMap<Int, MutableSet<Int>>>>()

    // ══════════════════════════════════════════════
    //  Công cụ cập nhật CAS
    // ══════════════════════════════════════════════

    /**
     * Cập nhật nguyên tử (atomic) EventSession của người chơi chỉ định
     *
     * @param charId  ID nhân vật
     * @param updater Hàm cập nhật (Nhận Session hiện tại, trả về Session mới)
     * @return Session trước khi cập nhật, null = Không có sự kiện đang diễn ra
     */
    private fun updateSession(charId: Long, updater: (EventSession) -> EventSession): EventSession? {
        val ref = sessions[charId] ?: return null
        while (true) {
            val current = ref.get() ?: return null
            val updated = updater(current)
            if (ref.compareAndSet(current, updated)) return current
        }
    }

    // ══════════════════════════════════════════════
    //  Quản lý Session
    // ══════════════════════════════════════════════

    /**
     * Khởi động EventSession mới (phase = Executing)
     *
     * Nếu đã có sự kiện đang diễn ra, nó sẽ bị ghi đè (Thao tác an toàn: kích hoạt sự kiện mới có nghĩa là sự kiện cũ không còn hiệu lực).
     */
    fun activate(charId: Long, session: EventSession) {
        sessions[charId] = AtomicReference(session)
        logger.debug { "Khởi động sự kiện: charId=$charId eveNo=${session.eveNo} mapId=${session.mapId} results=${session.results.size}" }
    }

    /** Chỉ khởi động event nền khi người chơi thật sự idle. */
    fun tryActivateIfIdle(charId: Long, session: EventSession): Boolean {
        val inserted = sessions.putIfAbsent(charId, AtomicReference(session)) == null
        if (inserted) {
            logger.debug { "Khởi động sự kiện nền: charId=$charId eveNo=${session.eveNo} mapId=${session.mapId} results=${session.results.size}" }
        } else {
            val current = sessions[charId]?.get()
            logger.debug {
                "Bỏ qua sự kiện nền vì đang có event: charId=$charId " +
                    "currentEve=${current?.eveNo} currentPhase=${current?.phase?.let { it::class.simpleName }}"
            }
        }
        return inserted
    }

    /**
     * Lấy result hiện tại theo cách nguyên tử (atomic) và tăng index
     *
     * Chỉ có hiệu lực ở trạng thái [EventPhase.Executing].
     *
     * @return EveResult tiếp theo, null = Không có sự kiện đang diễn ra hoặc đã đến cuối hoặc không ở trạng thái Executing
     */
    fun advance(charId: Long): EveResult? {
        val ref = sessions[charId] ?: return null
        while (true) {
            val current = ref.get() ?: return null
            if (current.phase !is EventPhase.Executing) {
                logger.debug { "advance: charId=$charId không ở trạng thái Executing (${current.phase::class.simpleName}), bỏ qua" }
                return null
            }
            if (current.currentIndex >= current.results.size) return null
            val result = current.results[current.currentIndex]
            val updated = current.copy(currentIndex = current.currentIndex + 1)
            if (ref.compareAndSet(current, updated)) {
                logger.debug { "Tiến trình sự kiện: charId=$charId idx=${current.currentIndex}/${current.results.size} type=${result.resultType} meanNo=${result.resultMeanNo}" }
                return result
            }
        }
    }

    // ══════════════════════════════════════════════
    //  Chuyển đổi trạng thái
    // ══════════════════════════════════════════════

    /**
     * Chuyển sang trạng thái AwaitingChoice + Ghi lại surfaceId
     *
     * Chỉ hợp lệ khi chuyển từ trạng thái Executing.
     */
    fun enterAwaitingChoice(charId: Long, surfaceId: Int) {
        val prev = updateSession(charId) {
            if (it.phase !is EventPhase.Executing) {
                logger.warn { "enterAwaitingChoice: charId=$charId không phải Executing (${it.phase::class.simpleName})" }
                return@updateSession it
            }
            it.copy(phase = EventPhase.AwaitingChoice, lastSurfaceId = surfaceId)
        }
        if (prev != null) {
            logger.debug { "Chuyển trạng thái: charId=$charId Executing → AwaitingChoice(surfaceId=$surfaceId)" }
        }
    }

    /**
     * Chuyển sang trạng thái AwaitingBattle
     *
     * Chỉ hợp lệ khi chuyển từ trạng thái Executing.
     */
    fun enterAwaitingBattle(charId: Long, fightDataKey: Int = 0) {
        val prev = updateSession(charId) {
            if (it.phase !is EventPhase.Executing) {
                logger.warn { "enterAwaitingBattle: charId=$charId không phải Executing (${it.phase::class.simpleName})" }
                return@updateSession it
            }
            it.copy(phase = EventPhase.AwaitingBattle, fightDataKey = fightDataKey)
        }
        if (prev != null) {
            logger.debug { "Chuyển trạng thái: charId=$charId Executing → AwaitingBattle(fightDataKey=$fightDataKey)" }
        }
    }

    /**
     * Chuyển sang AwaitingWarp trước khi gửi resultType=2 cho client.
     * EventHandler[2] bật ConductGate và có thể gửi C:020-006 ngay frame kế tiếp;
     * phase này ngăn gói next đến sớm hoàn thành event trước S:020-007/scene load.
     */
    fun enterAwaitingWarp(charId: Long) {
        val prev = updateSession(charId) {
            if (it.phase !is EventPhase.Executing) {
                logger.warn { "enterAwaitingWarp: charId=$charId không phải Executing (${it.phase::class.simpleName})" }
                return@updateSession it
            }
            it.copy(phase = EventPhase.AwaitingWarp)
        }
        if (prev != null) {
            logger.debug { "Chuyển trạng thái: charId=$charId Executing → AwaitingWarp" }
        }
    }

    /**
     * Khôi phục từ AwaitingBattle sang trạng thái Executing
     *
     * Được gọi bởi EveFlowController.resumeAfterBattle,
     * đảm bảo đường dẫn fallback (không có khớp conditionClass=8) có thể advance bình thường.
     */
    fun resumeFromBattle(charId: Long) {
        val prev = updateSession(charId) {
            if (it.phase !is EventPhase.AwaitingBattle) {
                logger.warn { "resumeFromBattle: charId=$charId không phải AwaitingBattle (${it.phase::class.simpleName})" }
                return@updateSession it
            }
            it.copy(phase = EventPhase.Executing)
        }
        if (prev != null) {
            logger.debug { "Chuyển trạng thái: charId=$charId AwaitingBattle → Executing" }
        }
    }

    /**
     * Khôi phục từ AwaitingChoice sang trạng thái Executing
     *
     * Được gọi bởi EveFlowController.chooseEvent trên đường dẫn fallback.
     */
    fun resumeFromChoice(charId: Long) {
        val prev = updateSession(charId) {
            if (it.phase !is EventPhase.AwaitingChoice) {
                logger.warn { "resumeFromChoice: charId=$charId không phải AwaitingChoice (${it.phase::class.simpleName})" }
                return@updateSession it
            }
            it.copy(phase = EventPhase.Executing)
        }
        if (prev != null) {
            logger.debug { "Chuyển trạng thái: charId=$charId AwaitingChoice → Executing" }
        }
    }

    /** Claim an awaited input once, recording its value in the same CAS. */
    private fun claimInput(charId: Long, choice: Int? = null, battle: Int? = null): EventSession? {
        val ref = sessions[charId] ?: return null
        while (true) {
            val current = ref.get() ?: return null
            val awaiting = if (choice != null) current.phase is EventPhase.AwaitingChoice
                else current.phase is EventPhase.AwaitingBattle
            if (!awaiting) return null
            val updated = current.copy(
                phase = EventPhase.Executing,
                lastChoiceCode = choice ?: current.lastChoiceCode,
                choiceResolutionInProgress = if (choice != null) true else current.choiceResolutionInProgress,
                battleResult = battle ?: current.battleResult,
            )
            if (ref.compareAndSet(current, updated)) return updated
        }
    }

    fun claimChoice(charId: Long, code: Int): EventSession? = claimInput(charId, choice = code)
    fun claimBattleResult(charId: Long, result: Int): EventSession? = claimInput(charId, battle = result)

    /** Hạ guard sau khi result đầu tiên của lựa chọn đã được dispatch an toàn. */
    fun finishChoiceResolution(charId: Long) {
        updateSession(charId) { current ->
            if (!current.choiceResolutionInProgress) current
            else current.copy(choiceResolutionInProgress = false)
        }
    }

    /**
     * Ghi lại mã lựa chọn (Được gọi trong trạng thái AwaitingChoice)
     */
    fun setChoiceCode(charId: Long, code: Int) {
        updateSession(charId) {
            if (it.phase !is EventPhase.AwaitingChoice) {
                logger.warn { "setChoiceCode: charId=$charId không phải AwaitingChoice (${it.phase::class.simpleName})" }
                return@updateSession it
            }
            it.copy(lastChoiceCode = code)
        }
    }

    /**
     * Ghi lại kết quả chiến đấu (Được gọi trong trạng thái AwaitingBattle)
     */
    fun setBattleResult(charId: Long, result: Int) {
        updateSession(charId) {
            if (it.phase !is EventPhase.AwaitingBattle) {
                logger.warn { "setBattleResult: charId=$charId không phải AwaitingBattle (${it.phase::class.simpleName})" }
                return@updateSession it
            }
            it.copy(battleResult = result)
        }
    }

    /**
     * Thay thế hàng đợi kết quả và quay lại trạng thái Executing
     *
     * Dùng để đánh giá lại (reEvaluate) sau khi chọn Surface hoặc sau trận chiến:
     * Giữ lại mapId/eveNo/npcClickId + ngữ cảnh lịch sử của session, thay thế results và reset index.
     */
    fun replaceResultsAndResume(charId: Long, newResults: List<EveResult>) {
        val prev = updateSession(charId) {
            it.copy(results = newResults, currentIndex = 0, phase = EventPhase.Executing)
        }
        if (prev != null) {
            logger.debug { "Thay thế kết quả: charId=$charId eveNo=${prev.eveNo} newResults=${newResults.size} → Executing" }
        }
    }

    // ══════════════════════════════════════════════
    //  Truy vấn
    // ══════════════════════════════════════════════

    /** Lấy snapshot Session đang diễn ra (chỉ dùng để truy vấn đọc/read-only) */
    fun getSession(charId: Long): EventSession? = sessions[charId]?.get()

    // ══════════════════════════════════════════════
    //  Hoàn thành và Dọn dẹp
    // ══════════════════════════════════════════════

    /**
     * Sự kiện hoàn thành bình thường — Ghi lại eveNo đã hoàn thành và dọn dẹp Session
     *
     * eveNo=0 là fallback Talk (sự kiện ảo), không tính vào theo dõi hoàn thành.
     * Các sự kiện dạng dịch vụ thuần túy (Ngân hàng/Cửa hàng/Nhà trọ/Hội thoại) không ghi lại hoàn thành, đảm bảo có thể sử dụng lặp lại.
     *
     * @return EventSession đã hoàn thành, null = Không có sự kiện đang diễn ra
     */
    /**
     * Legacy in-memory completion. Persistence-backed callers should use
     * takeCompletion() then recordPersistedCompletion() only after DB success.
     */
    fun complete(charId: Long): EventSession? {
        val completed = takeCompletion(charId) ?: return null
        recordPersistedCompletion(charId, completed)
        return completed
    }

    /**
     * Atomically claim and remove the active event without changing RAM EveCount
     * or the completed-condition flags. Once consumed, delayed packets cannot
     * replay its rewards while the persistence write is in progress.
     */
    fun takeCompletion(charId: Long): EventSession? {
        val ref = sessions[charId] ?: return null
        if (!sessions.remove(charId, ref)) return null
        return ref.get()
    }

    /** Publish completion to RAM only after its EveCount write committed. */
    fun recordPersistedCompletion(charId: Long, session: EventSession, persistedCount: Int? = null) {
        if (session.eveNo == 0) return

        val eveMap = completedEvents
            .getOrPut(charId) { ConcurrentHashMap() }
            .getOrPut(session.mapId) { ConcurrentHashMap() }
        if (persistedCount != null) {
            require(persistedCount > 0) { "Persisted EveCount must be positive" }
            // A reconnect may already have hydrated the just-committed count.
            // Merge absolute DB value, never increment that same event twice.
            eveMap.merge(session.eveNo, persistedCount) { cached, stored ->
                maxOf(cached, stored)
            }
        } else {
            // Legacy in-memory-only complete() retains atomic +1 semantics.
            eveMap.merge(session.eveNo, 1, Int::plus)
        }

        if (session.hasStateChangingResults()) {
            completedConditions
                .getOrPut(charId) { ConcurrentHashMap() }
                .getOrPut(session.mapId) { ConcurrentHashMap() }
                .getOrPut(session.eveNo) { ConcurrentHashMap.newKeySet() }
                .add(session.matchedConditionNo)
        }

        logger.debug { "Sự kiện đã lưu và hoàn thành: charId=$charId mapId=${session.mapId} eveNo=${session.eveNo} condNo=${session.matchedConditionNo} → count=${eveMap[session.eveNo]}" }
    }

    /** Xóa sự kiện đang diễn ra (mất kết nối/buộc chấm dứt, không tính vào log hoàn thành) */
    fun clear(charId: Long) {
        sessions.remove(charId)
    }

    /** Xóa log hoàn thành sự kiện của nhân vật trong map chỉ định (gọi khi vào map) */
    fun clearSceneEvents(charId: Long, mapId: Int) {
        completedEvents[charId]?.remove(mapId)
        completedConditions[charId]?.remove(mapId)
    }

    /**
     * Điền trước (pre-populate) các sự kiện đã hoàn thành và số lần hoàn thành (Tải từ DB)
     *
     * Khi người chơi đăng nhập lại hoặc vào map, tải log hoàn thành sự kiện từ DB.
     * Chỉ thêm mới, không ghi đè (nếu trong bộ nhớ đã có bộ đếm cao hơn thì giữ nguyên).
     */
    fun prePopulateCompleted(charId: Long, mapId: Int, eveCounts: Map<Int, Int>) {
        if (eveCounts.isEmpty()) return
        val eveMap = completedEvents
            .getOrPut(charId) { ConcurrentHashMap() }
            .getOrPut(mapId) { ConcurrentHashMap() }

        // DB can be ahead of a cached count after reconnect or rehydration.
        // merge(max) is atomic with complete()'s increment and never
        // rolls an observed count backwards. Explicit scene reset still
        // uses clearSceneEvents() before reloading a new baseline.
        for ((eveNo, persistedCount) in eveCounts) {
            if (eveNo <= 0 || persistedCount <= 0) continue
            eveMap.merge(eveNo, persistedCount) { cached, persisted ->
                maxOf(cached, persisted)
            }
        }
    }
    /**
     * Lấy map số lần hoàn thành sự kiện của nhân vật ở map chỉ định (dùng để xây dựng PlayerEventState)
     *
     * @return eveNo → số lần hoàn thành
     */
    fun getCompletedEveCounts(charId: Long, mapId: Int): Map<Int, Int> {
        return completedEvents[charId]?.get(mapId) ?: emptyMap()
    }

    /**
     * Kiểm tra xem chuỗi điều kiện chỉ định đã hoàn thành chưa (phòng ngừa lặp lại chính xác ở cấp độ condNo)
     *
     * Dùng trong routeClickNpc để ngăn chặn việc kích hoạt lặp lại cùng một chuỗi điều kiện,
     * đồng thời cho phép các chuỗi điều kiện khác nhau (các bước nhiệm vụ khác nhau) của cùng một eveNo hoạt động bình thường.
     */
    fun isConditionCompleted(charId: Long, mapId: Int, eveNo: Int, condNo: Int): Boolean {
        return completedConditions[charId]?.get(mapId)?.get(eveNo)?.contains(condNo) ?: false
    }
}