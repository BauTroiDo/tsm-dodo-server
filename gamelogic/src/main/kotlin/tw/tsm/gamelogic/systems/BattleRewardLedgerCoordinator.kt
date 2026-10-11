package tw.tsm.gamelogic.systems

import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import java.util.UUID

/** One reward claim per character and battle, persisted before the first payout effect. */
data class BattleRewardClaim(
    val characterId: Long,
    val sourceKey: String,
    val token: String,
)

/**
 * Independent from EveCount and dungeon terminal rewards. Scope locking in
 * RewardLedgerRepository prevents a crashed payout from being bypassed by a
 * newly created battle with a different UUID.
 *
 * A battle UUID does not need a separate table: reserve persists its identity
 * atomically with the claim, BEFORE any EXP, gold, drops, pet or kill effects.
 */
class BattleRewardLedgerCoordinator(
    private val repository: RewardLedgerRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun reserve(characterId: Long, battleUuid: String): BattleRewardClaim? {
        if (characterId <= 0) return null
        val sourceKey = runCatching {
            RewardLedgerSourceKeys.battle(battleUuid, "GENERIC")
        }.getOrNull() ?: return null
        val token = UUID.randomUUID().toString()
        val inserted = try {
            repository.reserve(characterId, KIND, sourceKey, token, nowMillis())
        } catch (_: Exception) {
            false
        }
        return if (inserted) BattleRewardClaim(characterId, sourceKey, token) else null
    }

    /**
     * Called only after the existing battle checkpoint writes succeeded.
     * False or an unknown acknowledgement leaves the claim pending for audit.
     */
    suspend fun settle(claim: BattleRewardClaim): Boolean = try {
        repository.settle(claim.characterId, KIND, claim.sourceKey, claim.token, nowMillis())
    } catch (_: Exception) {
        false
    }

    companion object {
        const val KIND = "BATTLE_GENERIC"
    }
}
