package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterWelfareState : Table("character_welfare_state") {
    val charId = long("char_id")
    val lastLoginClaimDay = integer("last_login_claim_day").default(0)
    override val primaryKey = PrimaryKey(charId)
}
