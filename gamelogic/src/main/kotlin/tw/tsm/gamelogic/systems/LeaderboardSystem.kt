package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.tsm.domain.constants.LeaderboardConstants
import tw.tsm.domain.data.LeaderboardDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.LeaderboardEntryDto
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.LeaderboardCharRecord
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/** Lua UILeaderboard.maxInfo = 5. */
private const val PAGE_SIZE = 5

/** S88-001 rank/page/maxPage/myRank are bytes. */
private const val MAX_RANK = 255

/** Lua Role.playerMaxLv = 200. */
private const val PLAYER_MAX_LEVEL = 200

/** 5 phút. */
private const val REFRESH_INTERVAL_MS = 300_000L

/**
 * DVT protocol 88 leaderboard cache.
 *
 * Current IOS_LeaderbordInfo_C.dat has 19 boards. Only boards whose score source
 * is authoritative in this offline server are populated here:
 * 1..5 level, 6 warrior, 7 gold, 20 achievements.
 * Event/Boss boards stay empty until their own subsystem persists the score.
 */
class LeaderboardSystem(
    private val characterRepository: CharacterRepository,
    private val leaderboardDefs: Map<Int, LeaderboardDef>,
    private val responseSender: ResponseSender,
    private val scope: CoroutineScope,
    private val npcDefs: Map<Int, NpcDef>,
    private val achievementSystem: AchievementSystem,
) {
    private val cache = ConcurrentHashMap<Int, List<LeaderboardEntry>>()
    private val previousRanks = ConcurrentHashMap<Int, Map<Long, Int>>()

    fun startPeriodicRefresh() {
        scope.launch {
            while (true) {
                try {
                    refresh()
                } catch (e: Exception) {
                    logger.error(e) { "Làm mới bảng xếp hạng thất bại" }
                }
                delay(REFRESH_INTERVAL_MS)
            }
        }
        logger.info { "Leaderboard DVT refresh chạy ngay khi khởi động, chu kỳ ${REFRESH_INTERVAL_MS / 1000}s" }
    }

    suspend fun refresh() {
        val start = System.currentTimeMillis()
        val allChars = characterRepository.findAllForLeaderboard()

        val byLevel = allChars.sortedWith(
            compareByDescending<LeaderboardCharRecord> { cumulativeLevel(it) }
                .thenByDescending { it.exp }
                .thenBy { it.id }
        )

        val byGold = allChars
            .filter { it.gold > 0 }
            .sortedWith(
                compareByDescending<LeaderboardCharRecord> { it.gold }
                    .thenBy { it.id }
            )

        val byWarrior = allChars
            .filter { it.petNpcId > 0 && it.petLevel > 0 }
            .sortedWith(
                compareByDescending<LeaderboardCharRecord> { it.petTurn }
                    .thenByDescending { it.petLevel }
                    .thenByDescending { it.petExp }
                    .thenBy { it.id }
            )

        val achievementScores = HashMap<Long, Int>(allChars.size)
        for (char in allChars) {
            achievementScores[char.id] = achievementSystem.calculateTotalScore(char.id)
        }
        val byAchievement = allChars
            .filter { (achievementScores[it.id] ?: 0) > 0 }
            .sortedWith(
                compareByDescending<LeaderboardCharRecord> { achievementScores[it.id] ?: 0 }
                    .thenBy { it.id }
            )

        for ((leaderboardId, _) in leaderboardDefs) {
            val entries = when {
                leaderboardId == LeaderboardConstants.LEVEL_ALL_ID ->
                    buildEntries(leaderboardId, byLevel) { listOf(cumulativeLevel(it)) }

                leaderboardId in LeaderboardConstants.ELEMENT_LEVEL_IDS -> {
                    val element = LeaderboardConstants.ELEMENT_LEVEL_IDS.getValue(leaderboardId)
                    buildEntries(leaderboardId, byLevel.filter { it.element == element }) {
                        listOf(cumulativeLevel(it))
                    }
                }

                leaderboardId == LeaderboardConstants.WARRIOR_LEVEL_ID ->
                    buildEntries(leaderboardId, byWarrior) {
                        listOf(
                            npcDefs[it.petNpcId]?.element ?: 0,
                            it.petTurn,
                            it.petLevel,
                        )
                    }

                leaderboardId == LeaderboardConstants.GOLD_ID ->
                    buildEntries(leaderboardId, byGold) { listOf(it.gold) }

                leaderboardId == LeaderboardConstants.ACHIEVEMENT_ID ->
                    buildEntries(leaderboardId, byAchievement) {
                        listOf(achievementScores[it.id] ?: 0)
                    }

                leaderboardId in LeaderboardConstants.EVENT_SCORE_IDS -> emptyList()

                else -> {
                    logger.warn { "Leaderboard ID $leaderboardId không thuộc IOS_LeaderbordInfo_C.dat hiện tại" }
                    emptyList()
                }
            }
            cache[leaderboardId] = entries
        }

        val elapsed = System.currentTimeMillis() - start
        logger.info {
            "Làm mới leaderboard DVT xong (${elapsed}ms, ${allChars.size} nhân vật, " +
                "boards=${leaderboardDefs.size})"
        }
    }

    /** C88-001: leaderboardId U8 + page U8, page là 1-based theo UILeaderboard.lua. */
    suspend fun query(session: GameSession, leaderboardId: Int, page: Int) {
        val entries = cache[leaderboardId] ?: emptyList()
        val maxPage = maxOf(1, (entries.size + PAGE_SIZE - 1) / PAGE_SIZE)
        val safePage = page.coerceIn(1, maxPage)
        val startIdx = (safePage - 1) * PAGE_SIZE
        val endIdx = minOf(startIdx + PAGE_SIZE, entries.size)
        val pageEntries =
            if (startIdx < entries.size) entries.subList(startIdx, endIdx) else emptyList()

        val myEntry = entries.firstOrNull { it.charId == session.roleId.toLong() }
        val myRank = myEntry?.rank ?: 0
        val myScores = myEntry?.scores ?: emptyList()

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
                scoreParams = entry.scores,
            )
        }

        responseSender.sendLeaderboardData(
            session = session,
            leaderboardId = leaderboardId,
            page = safePage,
            maxPage = maxPage,
            myRank = myRank,
            myScoreParams = myScores,
            entries = dtos,
        )
    }

    private fun buildEntries(
        leaderboardId: Int,
        sorted: List<LeaderboardCharRecord>,
        scoreExtractor: (LeaderboardCharRecord) -> List<Int>,
    ): List<LeaderboardEntry> {
        val prevRanks = previousRanks[leaderboardId] ?: emptyMap()
        val entries = sorted.take(MAX_RANK).mapIndexed { index, char ->
            val rank = index + 1
            val prevRank = prevRanks[char.id]
            val state = when {
                prevRank == null -> 0
                rank < prevRank -> 1
                rank > prevRank -> 2
                else -> 0
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
                career = char.career,
                scores = scoreExtractor(char),
            )
        }
        previousRanks[leaderboardId] = entries.associate { it.charId to it.rank }
        return entries
    }

    companion object {
        fun cumulativeLevel(level: Int, turn: Int): Int = level + turn * PLAYER_MAX_LEVEL

        private fun cumulativeLevel(char: LeaderboardCharRecord): Int =
            cumulativeLevel(char.level, char.turn)

        fun pageBounds(entryCount: Int, requestedPage: Int): Triple<Int, Int, Int> {
            val maxPage = maxOf(1, (entryCount + PAGE_SIZE - 1) / PAGE_SIZE)
            val page = requestedPage.coerceIn(1, maxPage)
            val start = (page - 1) * PAGE_SIZE
            return Triple(page, maxPage, start)
        }
    }
}

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
    val scores: List<Int>,
)
