package tw.tsm.domain.interfaces.repositories

/**
 * A persistent, one-way payout fence that is separate from EveCount.
 *
 * Once a claim is inserted, even its original token cannot grant rewards
 * again. This deliberately fails closed after a crash: an operator must
 * compare inventory/currency/mission state with logs before reconciliation.
 */
data class RewardLedgerEntry(
    val characterId: Long,
    val rewardKind: String,
    val sourceKey: String,
    val attemptToken: String,
    val reservedAtEpochMs: Long,
    val settledAtEpochMs: Long?,
) {
    val isSettled: Boolean get() = settledAtEpochMs != null
}

interface RewardLedgerRepository {
    /**
     * True ONLY if a brand-new row was inserted by this call.
     *
     * False for ANY existing key, even the same token in PENDING state:
     * the first attempt may already have delivered some reward effects.
     * Never execute payouts after a false result.
     */
    suspend fun reserve(
        characterId: Long,
        rewardKind: String,
        sourceKey: String,
        attemptToken: String,
        nowEpochMs: Long,
    ): Boolean

    /** Settles exactly one pending claim; never deletes a durable receipt. */
    suspend fun settle(
        characterId: Long,
        rewardKind: String,
        sourceKey: String,
        attemptToken: String,
        nowEpochMs: Long,
    ): Boolean

    /** Read-only inspection to help an administrator reconcile a crash. */
    suspend fun find(
        characterId: Long,
        rewardKind: String,
        sourceKey: String,
    ): RewardLedgerEntry?

    suspend fun unresolvedForCharacter(characterId: Long): List<RewardLedgerEntry>
}
