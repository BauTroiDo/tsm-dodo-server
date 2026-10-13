package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Permanent idempotency receipt for a fully settled Eve effect run.
 *
 * A claim is deleted at successful settlement to allow legitimately repeatable
 * Eve events. This retained receipt prevents an old run token from reserving
 * again after crash, lost acknowledgement, reconnect, or request replay.
 */
object CharacterEveEffectSettlements : Table("character_eve_effect_settlements") {
    val charId = long("char_id")
    val runToken = varchar("run_token", 64)
    val mapId = integer("map_id")
    val eveNo = integer("eve_no")
    val completionCount = integer("completion_count")
    val settledAtEpochMs = long("settled_at_epoch_ms")

    override val primaryKey = PrimaryKey(charId, runToken)
}
