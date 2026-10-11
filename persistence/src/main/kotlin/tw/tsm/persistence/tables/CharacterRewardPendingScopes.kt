package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Exactly one unresolved reward source per (character, reward kind).
 *
 * This second unique key prevents a crash survivor from being bypassed by
 * starting a *different* dungeon run or battle and using a new sourceKey.
 * Insert and release MUST share the ledger row transaction.
 *
 * No TTL and no automatic "stale" cleanup: pending rewards require audit.
 */
object CharacterRewardPendingScopes : Table("character_reward_pending_scopes") {
    val characterId = long("character_id")
    val rewardKind = varchar("reward_kind", 32)
    val sourceKey = varchar("source_key", 128)
    val attemptToken = varchar("attempt_token", 64)

    override val primaryKey = PrimaryKey(characterId, rewardKind)
}
