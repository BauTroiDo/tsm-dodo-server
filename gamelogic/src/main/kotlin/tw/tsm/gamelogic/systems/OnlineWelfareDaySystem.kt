package tw.tsm.gamelogic.systems

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.WelfareStateRepository
import tw.tsm.domain.util.OleDate

/**
 * DVT daily online welfare. RoleCount[10] is seconds carried between sessions,
 * while session.onlineStartMillis covers the current session only.
 *
 * SQL resets marker, RoleCount[10] and only online flags 2..7 in one transaction.
 * A per-character mutex coordinates normal close, TCP drop, midnight heartbeat,
 * and concurrent claim packets.
 */
class OnlineWelfareDaySystem(
    private val repo: WelfareStateRepository,
    private val counts: RoleCountSystem,
    private val flags: BitFlagSystem,
    private val sender: ResponseSender,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    private fun lock(id: Long): Mutex = locks.computeIfAbsent(id) { Mutex() }

    /**
     * Consume the verified character-specific offline period in SQL *before*
     * RoleCountSystem.loadCounts. Never infer past sessions from account last_login_at.
     */
    suspend fun beforeRoleCountsLoad(characterId: Long) {
        if (characterId > 0L) {
            lock(characterId).withLock {
                repo.resumeCharacterOfflineMinutes(characterId, System.currentTimeMillis())
            }
        }
    }

    /** Must run after role counts load and before sending S085-001 / S081-002. */
    suspend fun onCharacterEnter(session: GameSession) {
        if (session.roleId <= 0) return
        lock(session.roleId.toLong()).withLock { refreshLocked(session, false) }
    }

    /** Called by the client's 20-second heartbeat; reinitializes an open midnight session. */
    suspend fun onHeartbeat(session: GameSession) {
        if (session.state != SessionState.IN_GAME || session.roleId <= 0) return
        lock(session.roleId.toLong()).withLock { refreshLocked(session, true) }
    }

    /** Serialize daily-claim flag check, reward grant and setting the claimed flag. */
    suspend fun withClaimLock(session: GameSession, claim: suspend () -> Unit) {
        lock(session.roleId.toLong()).withLock {
            refreshLocked(session, true)
            claim()
        }
    }

    /** Idempotent even if explicit C0-0 and channelInactive both fire. */
    suspend fun flushOnDisconnect(session: GameSession) {
        if (session.roleId <= 0) return
        val characterId = session.roleId.toLong()
        lock(characterId).withLock {
            if (session.onlineStartMillis <= 0L) return@withLock
            refreshLocked(session, false)
            val start = session.onlineStartMillis
            if (start <= 0) return@withLock
            val now = System.currentTimeMillis()
            val seconds = secondsSinceStartToday(start, now)
            if (seconds > 0) {
                val before = counts.getValue(characterId, ONLINE_COUNTER).coerceAtLeast(0)
                counts.setValue(characterId, ONLINE_COUNTER,
                    (before.toLong() + seconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            session.onlineStartMillis = 0L
            counts.saveCounts(characterId)
            // Double close will not extend the offline session; SQL guards 0->timestamp.
            repo.markCharacterOffline(characterId, now)
        }
    }

    private suspend fun refreshLocked(session: GameSession, sendUpdate: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val day = dayKey(now)
        val charId = session.roleId.toLong()
        if (!repo.startOnlineDayIfNeeded(charId, day)) return false

        // Preserve only today's elapsed seconds for a session spanning midnight.
        val carry = if (session.onlineStartMillis > 0L) {
            secondsSinceStartToday(session.onlineStartMillis, now)
        } else 0
        counts.setValue(charId, ONLINE_COUNTER, carry)
        if (session.onlineStartMillis > 0L) session.onlineStartMillis = now
        counts.saveCounts(charId)

        if (sendUpdate) {
            sender.sendRoleCountUpdate(session, mapOf(ONLINE_COUNTER to (carry to 0)))
            sender.sendBitFlagChanges(session, (2..7).map { it to false })
            sender.sendOnlineTime(session, OleDate.nowLocal())
        }
        return true
    }

    companion object {
        private const val ONLINE_COUNTER = 10
        private val ZONE = ZoneId.of("Asia/Taipei")
        fun dayKey(timestamp: Long): Int {
            val date = Instant.ofEpochMilli(timestamp).atZone(ZONE).toLocalDate()
            return date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        }
        fun secondsSinceStartToday(startMillis: Long, nowMillis: Long): Int {
            if (startMillis <= 0L || nowMillis <= startMillis) return 0
            val midnight = Instant.ofEpochMilli(nowMillis).atZone(ZONE)
                .toLocalDate().atStartOfDay(ZONE).toInstant().toEpochMilli()
            return ((nowMillis - maxOf(startMillis, midnight)) / 1000L)
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
