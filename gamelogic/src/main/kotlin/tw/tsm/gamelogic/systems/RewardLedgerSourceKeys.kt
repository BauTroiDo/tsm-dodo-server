package tw.tsm.gamelogic.systems

import java.util.UUID

/**
 * Deterministic payout identities (not game integration yet).
 * The character ID is a SEPARATE DB primary-key column.
 */
object RewardLedgerSourceKeys {
    /**
     * Every newly persisted DungeonRunRecord has a stable UUID. Reconnects
     * and crash recovery MUST reuse that runToken. Timestamp/instanceId alone
     * are not collision-proof for fast repeated dungeon entries.
     *
     * Pre-migration rows have an empty token and are NOT payout-eligible.
     */
    fun dungeon(run: ActiveDungeonRun, rewardStep: String): String {
        require(run.dungeonId > 0 && run.startedAtMillis > 0 && run.instanceId >= 0)
        require(rewardStep in setOf("TERMINAL", "WEEKLY_TERMINAL"))
        val persistedUuid = UUID.fromString(run.runToken)
        return "d:${run.dungeonId}:$persistedUuid:$rewardStep"
    }

    /**
     * DB records can contain malformed legacy strings. A settlement handler
     * should fail closed (rather than throw mid-battle) before touching rewards.
     */
    fun verifiedDungeonOrNull(run: ActiveDungeonRun, rewardStep: String): String? =
        runCatching { dungeon(run, rewardStep) }.getOrNull()

    /**
     * A battle payout identity MUST come from a persisted attempt UUID,
     * not BattleManager's reset-on-restart numerical battleId.
     */
    fun battle(persistedBattleAttemptId: String, rewardStep: String = "GENERIC"): String {
        require(rewardStep in setOf("GENERIC", "CAPTURE"))
        val stableUuid = UUID.fromString(persistedBattleAttemptId)
        return "b:$stableUuid:$rewardStep"
    }

    /** Weekly all-pass requires the DVT-defined period identity, not the wall clock at payout. */
    fun weeklyAllPass(weeklyPeriodKey: String, page: Int): String {
        require(weeklyPeriodKey.length in 1..64 && weeklyPeriodKey.matches(Regex("[0-9A-Za-z_-]+")))
        require(page in 0..1000)
        return "w:$weeklyPeriodKey:$page"
    }
}
