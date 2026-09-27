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
    /** Sự kiện đã hoàn thành (trạng thái kết thúc) */
    data object Completed : EventPhase()
}

/**
 * Phiên sự kiện đang diễn ra (Bất biến / Immutable)
 *
 * Tất cả các thay đổi trạng thái được cập nhật nguyên tử (atomic update) thông qua copy() + CAS, tránh điều kiện tương tranh.
 *
 * Trường phase cung cấp sự đảm bảo loại trừ lẫn nhau cho các giai đoạn vòng đời:
 * - 4 trạng thái Executing / AwaitingChoice / AwaitingBattle / Completed loại trừ lẫn nhau.
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
    val battleResult: Int = 0,
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
    fun enterAwaitingBattle(charId: Long) {
        val prev = updateSession(charId) {
            if (it.phase !is EventPhase.Executing) {
                logger.warn { "enterAwaitingBattle: charId=$charId không phải Executing (${it.phase::class.simpleName})" }
                return@updateSession it
            }
            it.copy(phase = EventPhase.AwaitingBattle)
        }
        if (prev != null) {
            logger.debug { "Chuyển trạng thái: charId=$charId Executing → AwaitingBattle" }
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
    fun complete(charId: Long): EventSession? {
        val ref = sessions.remove(charId) ?: return null
        val session = ref.get() ?: return null
        if (session.eveNo != 0) {
            val eveMap = completedEvents
                .getOrPut(charId) { ConcurrentHashMap() }
                .getOrPut(session.mapId) { ConcurrentHashMap() }
            eveMap.merge(session.eveNo, 1, Int::plus)

            // Theo dõi cấp condNo — Chỉ các sự kiện dạng thay đổi trạng thái mới cần chống trùng lặp, Talk-only có thể kích hoạt nhiều lần
            if (session.hasStateChangingResults()) {
                completedConditions
                    .getOrPut(charId) { ConcurrentHashMap() }
                    .getOrPut(session.mapId) { ConcurrentHashMap() }
                    .getOrPut(session.eveNo) { ConcurrentHashMap.newKeySet() }
                    .add(session.matchedConditionNo)
            }

            logger.debug { "Sự kiện hoàn thành: charId=$charId mapId=${session.mapId} eveNo=${session.eveNo} condNo=${session.matchedConditionNo} → count=${eveMap[session.eveNo]}" }
        }
        return session
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
        var added = 0
        for ((eveNo, count) in eveCounts) {
            eveMap.putIfAbsent(eveNo, count)?.let {} ?: run { added++ }
        }
        if (added > 0) {
            logger.debug { "Điền trước sự kiện hoàn thành: charId=$charId mapId=$mapId added=$added → total=${eveMap.size}" }
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