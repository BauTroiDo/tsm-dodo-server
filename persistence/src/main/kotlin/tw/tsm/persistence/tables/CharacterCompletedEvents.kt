package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色已完成事件記錄表
 *
 * 對應 EveEventStateMachine.completedEvents（conditionClass=12 計數用）。
 * 每筆記錄代表某角色在某地圖完成了某 eveNo 的事件及其完成次數。
 *
 * completionCount 追蹤同一事件被完成的次數，用於教學門事件等
 * 需要多次互動才能推進的場景。
 */
object CharacterCompletedEvents : Table("character_completed_events") {
    /** 角色 ID */
    val charId = long("char_id")

    /** 地圖 ID */
    val mapId = integer("map_id")

    /** 事件編號（NpcEventData.eveNo） */
    val eveNo = integer("eve_no")

    /** 完成次數（每次完成 +1，conditionClass=12 param=0 用） */
    val completionCount = integer("completion_count").default(1)

    override val primaryKey = PrimaryKey(charId, mapId, eveNo)
}
