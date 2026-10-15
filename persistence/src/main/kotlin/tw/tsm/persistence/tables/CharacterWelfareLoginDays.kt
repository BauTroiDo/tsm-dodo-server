package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object CharacterWelfareLoginDays : Table("character_welfare_login_days") {
    val charId = long("char_id")
    val dayKey = integer("day_key")
    override val primaryKey = PrimaryKey(charId, dayKey)
}
