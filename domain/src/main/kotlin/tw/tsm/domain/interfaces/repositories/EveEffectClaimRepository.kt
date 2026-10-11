package tw.tsm.domain.interfaces.repositories

/** Unresolved reward reservation. An operator must reconcile before release. */
data class UnresolvedEveEffectClaim(
    val charId: Long,
    val mapId: Int,
    val eveNo: Int,
    val runToken: String,
    val reservedAtEpochMs: Long,
)

/**
 * Durable pre-effect claim contract for Eve's future payout coordinator.
 *
 * This interface is intentionally NOT used by the live EveFlowController yet.
 * A caller must reserve BEFORE any non-idempotent reward effect; settlement
 * is the only normal path that removes a reservation after successful payout.
 */
interface EveEffectClaimRepository {
    /** Same run may resume; a different run or a previously settled run is denied. */
    suspend fun reserve(
        charId: Long,
        mapId: Int,
        eveNo: Int,
        runToken: String,
        nowEpochMs: Long,
    ): Boolean

    /** Read-only operation for admin reconciliation; no timeout-based deletion. */
    suspend fun unresolvedForCharacter(charId: Long): List<UnresolvedEveEffectClaim>

    /** Atomic EveCount increment, permanent receipt and claim removal; null on stale token. */
    suspend fun settle(charId: Long, mapId: Int, eveNo: Int, runToken: String): Int?
}
