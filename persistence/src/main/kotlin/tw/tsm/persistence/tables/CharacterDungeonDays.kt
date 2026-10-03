package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Persisted offline-server calendar boundary for normal dungeon daily mission counters. */
object CharacterDungeonDays : Table("character_dungeon_days") {
    val charId = long("char_id")
    val dayKey = integer("day_key")
    override val primaryKey = PrimaryKey(charId)
}
