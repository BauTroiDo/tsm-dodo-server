package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.tsm.domain.constants.LeaderboardConstants
import tw.tsm.domain.data.LeaderboardDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.LeaderboardEntryDto
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.LeaderboardCharRecord
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/** Số lượng xếp hạng hiển thị mỗi trang */
private const val PAGE_SIZE = 10

/** Khoảng thời gian làm mới định kỳ (mili-giây) — 5 phút */
private const val REFRESH_INTERVAL_MS = 300_000L

/**
 * Hệ thống bảng xếp hạng — Bộ nhớ đệm (cache) thuần túy
 *
 * Định kỳ truy vấn dữ liệu nhân vật từ DB và tính toán thứ hạng, kết quả được lưu đệm trong bộ nhớ.
 * Hỗ trợ 26 loại bảng xếp hạng: Cấp độ Ngũ hành, Chuyển sinh, Tổng cấp độ, Tiền, Tiền đấu giá, Cấp độ võ tướng.
 *
 * Nguồn: protocal.lua protocolTable[88], UILeaderboard.lua
 */
class LeaderboardSystem(
    private val characterRepository: CharacterRepository,
    private val leaderboardDefs: Map<Int, LeaderboardDef>,
    private val responseSender: ResponseSender,
    private val scope: CoroutineScope
) {
    /** Bộ nhớ đệm bảng xếp hạng: leaderboardId → danh sách mục đã được sắp xếp */
    private val cache = ConcurrentHashMap<Int, List<LeaderboardEntry>>()

    /** Xếp hạng lần trước (dùng để tính toán sự lên/xuống hạng) */
    private val previousRanks = ConcurrentHashMap<Int, Map<Long, Int>>()

    /**
     * Khởi động làm mới định kỳ
     */
    fun startPeriodicRefresh() {
        scope.launch {
            delay(5000)
            while (true) {
                try {
                    refresh()
                } catch (e: Exception) {
                    logger.error(e) { "Làm mới bảng xếp hạng thất bại" }
                }
                delay(REFRESH_INTERVAL_MS)
            }
        }
        logger.info { "Làm mới định kỳ bảng xếp hạng đã khởi động (khoảng thời gian ${REFRESH_INTERVAL_MS / 1000}s)" }
    }

    /**
     * Làm mới bảng xếp hạng thủ công
     */
    suspend fun refresh() {
        val start = System.currentTimeMillis()
        val allChars = characterRepository.findAllForLeaderboard()

        // ── Sắp xếp trước (mỗi loại làm một lần, tránh sắp xếp trùng lặp) ──
        val byLevel = allChars.sortedWith(
            compareByDescending<LeaderboardCharRecord> { effectiveLevel(it) }
                .thenByDescending { it.exp }
                .thenBy { it.id }
        )

        val byReborn = allChars.sortedWith(
            compareByDescending<LeaderboardCharRecord> { it.turn }
                .thenByDescending { effectiveLevel(it) }
                .thenByDescending { it.exp }
                .thenBy { it.id }
        )

        val byGold = allChars
            .filter { it.gold > 0 }
            .sortedWith(
                compareByDescending<LeaderboardCharRecord> { it.gold }
                    .thenBy { it.id }
            )

        val byAuctionGold = allChars
            .filter { it.auctionGold > 0 }
            .sortedWith(
                compareByDescending<LeaderboardCharRecord> { it.auctionGold }
                    .thenBy { it.id }
            )

        val byPetLevel = allChars
            .filter { it.petMaxLevel > 0 }
            .sortedWith(
                compareByDescending<LeaderboardCharRecord> { it.petMaxLevel }
                    .thenBy { it.id }
            )

        // ── Phân bổ kết quả sắp xếp theo ID ──
        for ((leaderboardId, def) in leaderboardDefs) {
            val entries = when {
                leaderboardId in LeaderboardConstants.NOT_IMPLEMENTED_IDS -> {
                    cache[leaderboardId] = emptyList()
                    continue
                }

                leaderboardId in LeaderboardConstants.ELEMENT_LEVEL_IDS -> {
                    val element = LeaderboardConstants.resolveElement(def)
                    val filtered = byLevel.filter { it.element == element }
                    buildEntries(leaderboardId, filtered) { listOf(effectiveLevel(it)) }
                }

                leaderboardId == LeaderboardConstants.REBORN_ID -> {
                    buildEntries(leaderboardId, byReborn) {
                        listOf(it.element, it.turn, it.level)
                    }
                }

                leaderboardId == LeaderboardConstants.TOTAL_LEVEL_ID ||
                        leaderboardId == LeaderboardConstants.ACHIEVEMENT_ID -> {
                    buildEntries(leaderboardId, byLevel) { listOf(effectiveLevel(it)) }
                }

                leaderboardId == LeaderboardConstants.GOLD_ID -> {
                    buildEntries(leaderboardId, byGold) { listOf(it.gold) }
                }

                leaderboardId == LeaderboardConstants.AUCTION_GOLD_ID -> {
                    buildEntries(leaderboardId, byAuctionGold) { listOf(it.auctionGold) }
                }

                leaderboardId == LeaderboardConstants.PET_LEVEL_ID -> {
                    buildEntries(leaderboardId, byPetLevel) { listOf(it.petMaxLevel) }
                }

                else -> {
                    // ID bảng xếp hạng không xác định, trả về danh sách trống
                    cache[leaderboardId] = emptyList()
                    continue
                }
            }

            cache[leaderboardId] = entries
        }

        val elapsed = System.currentTimeMillis() - start
        logger.info { "Làm mới bảng xếp hạng hoàn tất (${elapsed}ms, ${allChars.size} nhân vật)" }
        if (elapsed > 1000) {
            logger.warn { "Làm mới bảng xếp hạng quá chậm (${elapsed}ms), hãy kiểm tra nút thắt cổ chai (bottleneck)" }
        }
    }

    /**
     * Truy vấn bảng xếp hạng (C:088-001)
     *
     * @param leaderboardId ID bảng xếp hạng
     * @param page Số trang (hệ cơ số 0 / 0-based)
     */
    suspend fun query(session: GameSession, leaderboardId: Int, page: Int) {
        val entries = cache[leaderboardId] ?: emptyList()
        val maxPage = if (entries.isEmpty()) 0 else (entries.size - 1) / PAGE_SIZE

        val safePage = page.coerceIn(0, maxPage)
        val startIdx = safePage * PAGE_SIZE
        val endIdx = minOf(startIdx + PAGE_SIZE, entries.size)
        val pageEntries = if (startIdx < entries.size) entries.subList(startIdx, endIdx) else emptyList()

        val myRank = entries.indexOfFirst { it.charId == session.roleId.toLong() }
            .let { if (it >= 0) it + 1 else 0 }

        val myScores = entries.find { it.charId == session.roleId.toLong() }?.scores ?: emptyList()

        val dtos = pageEntries.map { entry ->
            LeaderboardEntryDto(
                rank = entry.rank,
                state = entry.state,
                roleId = entry.charId,
                name = entry.name,
                level = entry.level,
                element = entry.element,
                turn3Element = entry.turn3Element,
                turn = entry.turn,
                career = entry.career,
                scoreParams = entry.scores
            )
        }

        responseSender.sendLeaderboardData(
            session, leaderboardId, safePage, maxPage, myRank,
            myScores, dtos
        )
    }

    /**
     * Phương thức chung: Chuyển danh sách nhân vật đã sắp xếp thành các mục của bảng xếp hạng
     *
     * @param leaderboardId ID bảng xếp hạng (dùng để theo dõi lên/xuống hạng)
     * @param sorted Danh sách nhân vật đã được sắp xếp
     * @param scoreExtractor Hàm trích xuất scoreParams từ dữ liệu nhân vật
     */
    private fun buildEntries(
        leaderboardId: Int,
        sorted: List<LeaderboardCharRecord>,
        scoreExtractor: (LeaderboardCharRecord) -> List<Int>
    ): List<LeaderboardEntry> {
        val prevRanks = previousRanks[leaderboardId] ?: emptyMap()
        val entries = sorted.mapIndexed { index, char ->
            val rank = index + 1
            val prevRank = prevRanks[char.id]
            val state = when {
                prevRank == null -> 0   // Mới lên bảng, không đổi
                rank < prevRank -> 1    // Lên hạng
                rank > prevRank -> 2    // Xuống hạng
                else -> 0               // Không đổi
            }
            LeaderboardEntry(
                rank = rank,
                state = state,
                charId = char.id,
                name = char.name,
                level = char.level,
                element = char.element,
                turn3Element = if (char.turn >= 3) char.element else 0,
                turn = char.turn,
                career = 0,
                scores = scoreExtractor(char)
            )
        }
        previousRanks[leaderboardId] = entries.associate { it.charId to it.rank }
        return entries
    }

    /**
     * Tính toán cấp độ hiệu dụng (khi turn >= 3 thì cộng thêm 150)
     *
     * Đối chiếu protocal.lua: if rankInfo.turn >= 3 then rankInfo.level = rankInfo.level + Role.playerMaxLv
     */
    private fun effectiveLevel(char: LeaderboardCharRecord): Int =
        if (char.turn >= 3) char.level + 150 else char.level
}

/**
 * Mục bên trong bảng xếp hạng
 */
data class LeaderboardEntry(
    val rank: Int,
    val state: Int,
    val charId: Long,
    val name: String,
    val level: Int,
    val element: Int,
    val turn3Element: Int,
    val turn: Int,
    val career: Int,
    val scores: List<Int>
)