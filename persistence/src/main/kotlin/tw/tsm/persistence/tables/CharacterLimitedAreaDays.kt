package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Durable daily entry guard, separate from the client's ERoleCount(27) minute display. */
object CharacterLimitedAreaDays : Table("character_limited_area_days") {
    val charId = long("char_id")
    val sceneId = integer("scene_id")
    val dayKey = integer("day_key")
    val startedAtMillis = long("started_at_millis")
    val closed = bool("closed").default(false)

    override val primaryKey = PrimaryKey(charId, sceneId)
}
