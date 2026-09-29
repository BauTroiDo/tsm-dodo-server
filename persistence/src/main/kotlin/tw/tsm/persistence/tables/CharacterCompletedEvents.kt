package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng ghi sự kiện nhân vật đã hoàn thành
 *
 * Tương ứng EveEventStateMachine.completedEvents (dùng để đếm cho conditionClass=12).
 * Mỗi bản ghi cho biết nhân vật nào đã hoàn thành sự kiện eveNo nào trên bản đồ nào và số lần hoàn thành.
 *
 * completionCount theo dõi số lần cùng một sự kiện được hoàn thành, dùng cho sự kiện cổng hướng dẫn v.v.
 * Dành cho cảnh cần tương tác nhiều lần mới tiến được.
 */
object CharacterCompletedEvents : Table("character_completed_events") {
    /** ID nhân vật*/
    val charId = long("char_id")

    /** ID bản đồ*/
    val mapId = integer("map_id")

    /** Mã sự kiện (NpcEventData.eveNo)*/
    val eveNo = integer("eve_no")

    /** Số lần hoàn thành (mỗi lần hoàn thành +1, dùng cho conditionClass=12 param=0)*/
    val completionCount = integer("completion_count").default(1)

    override val primaryKey = PrimaryKey(charId, mapId, eveNo)
}
