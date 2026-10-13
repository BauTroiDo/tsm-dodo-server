package tw.tsm.gamelogic.systems

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinate EXP-slot commit/cache publication with whole-character autosave/disconnect snapshots. */
object CharacterPersistenceGuard {
    // Bounded lock storage; a collision only serializes unrelated persistence briefly.
    private val locks = Array(256) { Mutex() }

    private fun stripe(characterId: Long): Int =
        (characterId xor (characterId ushr 32)).toInt() and 255

    suspend fun <T> withLock(characterId: Long, action: suspend () -> T): T =
        locks[stripe(characterId)].withLock { action() }

    /**
     * Two-player item trade uses direct synchronous BAG cache updates. Both
     * characters must be fenced against autosave/logoff/other BAG writers.
     * Acquire by physical stripe order; same-stripe collisions acquire ONCE.
     * Acquiring by playerA/playerB order would deadlock for reversed trades.
     */
    suspend fun <T> withLocks(charIdA: Long, charIdB: Long, action: suspend () -> T): T {
        val a = stripe(charIdA)
        val b = stripe(charIdB)
        if (a == b) return locks[a].withLock { action() }
        val low = minOf(a, b)
        val high = maxOf(a, b)
        return locks[low].withLock {
            locks[high].withLock { action() }
        }
    }
}
