package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Persisted Monday-boundary marker for DVT weekly/crazy-weekly dungeon progress. */
object CharacterDungeonWeeks : Table("character_dungeon_weeks") {
    val charId = long("char_id")
    /** YYYYMMDD key of the Monday that starts the currently observed week. */
    val weekKey = integer("week_key")
    override val primaryKey = PrimaryKey(charId)
}
