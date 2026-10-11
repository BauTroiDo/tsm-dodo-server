package tw.tsm.gamelogic.systems

import java.util.UUID

/**
 * Deterministic payout identities (not game integration yet).
 * The character ID is a SEPARATE DB primary-key column.
 */
object RewardLedgerSourceKeys {
    /**
     * Dungeon runs currently persist dungeonId, startedAtMillis and instanceId
     * in CharacterDungeonRuns. Reconnect/restart must reuse those exact values.
     *
     * Do not create a fresh timestamp during reward handling.
     * The fallback reconstruction of a legacy run cannot prove its identity;
     * it must be reconciled separately before any reward ledger rollout.
     */
    fun dungeon(run: ActiveDungeonRun, rewardStep: String): String {
        require(run.dungeonId > 0 && run.startedAtMillis > 0 && run.instanceId >= 0)
        require(rewardStep in setOf("TERMINAL", "WEEKLY_TERMINAL"))
        return "d:${run.dungeonId}:${run.startedAtMillis}:${run.instanceId}:$rewardStep"
    }

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
