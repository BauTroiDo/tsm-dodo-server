package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Persisted offline-server calendar boundaries for dungeon counters.
 *
 * dayKey keeps the existing normal-dungeon daily reset. weekKey is the Monday
 * (yyyyMMdd, Asia/Taipei) used by DVT Mark kind=9 weekly dungeon progress.
 * Zero means "not observed yet" so migration never wipes pre-existing progress.
 */
object CharacterDungeonDays : Table("character_dungeon_days") {
    val charId = long("char_id")
    val dayKey = integer("day_key").default(0)
    val weekKey = integer("week_key").default(0)
    override val primaryKey = PrimaryKey(charId)
}
