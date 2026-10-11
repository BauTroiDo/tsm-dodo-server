package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Một hàng/nhân vật cho ActiveDungeonRun bền vững qua restart. */
object CharacterDungeonRuns : Table("character_dungeon_runs") {
    val characterId = long("character_id")
    val dungeonId = integer("dungeon_id")
    /** OLE Automation Date gửi cho DVT S:047-018. */
    val startTime = double("start_time")
    /** Unix millis dùng tính deadline server-side. */
    val startedAtMillis = long("started_at_ms")
    val instanceId = integer("instance_id").default(0)
    val originMapId = integer("origin_map_id")
    val originX = integer("origin_x")
    val originY = integer("origin_y")
    val originInstanceId = integer("origin_instance_id").default(0)
    val settled = bool("settled").default(false)
    /** Legacy persisted runs migrate as blank and must be reconciled before payout. */
    val runToken = varchar("run_token", 64).default("")

    override val primaryKey = PrimaryKey(characterId)
}
