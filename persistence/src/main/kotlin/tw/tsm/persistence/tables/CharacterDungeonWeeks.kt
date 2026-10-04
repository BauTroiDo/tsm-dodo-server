package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Persisted Monday boundary for DVT weekly-dungeon stage counters and claim flags. */
object CharacterDungeonWeeks : Table("character_dungeon_weeks") {
    val charId = long("char_id")
    val weekKey = integer("week_key")
    override val primaryKey = PrimaryKey(charId)
}
