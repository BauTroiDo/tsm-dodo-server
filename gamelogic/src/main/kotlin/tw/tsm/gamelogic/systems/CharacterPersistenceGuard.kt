package tw.tsm.gamelogic.systems

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinate EXP-slot commit/cache publication with whole-character autosave/disconnect snapshots. */
internal object CharacterPersistenceGuard {
    // Bounded lock storage; a collision only serializes unrelated persistence briefly.
    private val locks = Array(256) { Mutex() }

    suspend fun <T> withLock(characterId: Long, action: suspend () -> T): T =
        locks[(characterId xor (characterId ushr 32)).toInt() and 255].withLock { action() }
}
