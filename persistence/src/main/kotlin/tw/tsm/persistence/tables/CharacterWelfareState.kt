package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterWelfareState : Table("character_welfare_state") {
    val charId = long("char_id")
    val lastLoginClaimDay = integer("last_login_claim_day").default(0)
    /** Last day for which online seconds and online-reward flags were initialized. */
    val onlineDayKey = integer("online_day_key").default(0)
    /** Per-character disconnect timestamp; 0 means active or no verified offline start. */
    val offlineSinceMillis = long("offline_since_ms").default(0L)
    /** Day key associated with RoleCount[172], separate from online/day-login rewards. */
    val offlineCounterDay = integer("offline_counter_day").default(0)
    override val primaryKey = PrimaryKey(charId)
}
