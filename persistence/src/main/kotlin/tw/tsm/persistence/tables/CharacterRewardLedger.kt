package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Independent payout fence for dungeon and per-player battle reward sources.
 *
 * Unlike EveCount, this table NEVER increments a quest-completion counter.
 * Settled rows are permanent idempotency receipts. Pending rows are retained
 * for manual review after crash, timeout or database acknowledgement loss.
 */
object CharacterRewardLedger : Table("character_reward_ledger") {
    val characterId = long("character_id")
    val rewardKind = varchar("reward_kind", 32)
    val sourceKey = varchar("source_key", 128)
    val attemptToken = varchar("attempt_token", 64)
    val reservedAtEpochMs = long("reserved_at_epoch_ms")
    val settledAtEpochMs = long("settled_at_epoch_ms").nullable()

    override val primaryKey = PrimaryKey(characterId, rewardKind, sourceKey)
}
