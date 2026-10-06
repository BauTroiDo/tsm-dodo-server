package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Bind existing RoleCount progress to its first claimed season; never guess rollover resets. */
object CharacterBattlePassSeasons : Table("character_battle_pass_seasons") {
    val charId = long("char_id")
    val seasonId = integer("season_id")
    val freeLevel = integer("free_level").default(0)
    val paidLevel = integer("paid_level").default(0)
    override val primaryKey = PrimaryKey(charId)
}
