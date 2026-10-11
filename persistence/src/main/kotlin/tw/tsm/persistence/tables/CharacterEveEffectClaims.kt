package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Durable, fail-closed reservation for one potentially state-changing Eve run.
 *
 * The (character, scene, EveNo) key deliberately permits only one unresolved
 * run at a time, including across JVM restarts. Successful event completion
 * must delete its reservation in the SAME transaction as EveCount increment.
 *
 * Do NOT delete abandoned claims on login, scene load or a timer: a reward may
 * have been delivered before a process crash. Manual reconciliation is needed.
 */
object CharacterEveEffectClaims : Table("character_eve_effect_claims") {
    val charId = long("char_id")
    val mapId = integer("map_id")
    val eveNo = integer("eve_no")
    val runToken = varchar("run_token", 64)
    val reservedAtEpochMs = long("reserved_at_epoch_ms")

    override val primaryKey = PrimaryKey(charId, mapId, eveNo)
}
