package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng tiến độ phái đi của nhân vật
 *
 * Lưu nhiệm vụ phái đi đang thực hiện (xoá sau khi hoàn thành nhận thưởng)
 */
object CharacterDispatches : Table("character_dispatches") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    /** Chỉ số thanh tiến độ (1-4)*/
    val slotIndex = integer("slot_index")
    /** Loại phái đi*/
    val dispatchKind = integer("dispatch_kind")
    /** Chỉ số tướng ở lữ quán*/
    val innPetIndex = integer("inn_pet_index")
    /** Thời điểm bắt đầu (OLE Automation Date)*/
    val startTime = double("start_time")
    /** Thời điểm kết thúc (OLE Automation Date)*/
    val endTime = double("end_time")
    /** Chỉ số hiệu ứng 1*/
    val effectIndex1 = integer("effect_index1").default(0)
    /** Chỉ số hiệu ứng 2*/
    val effectIndex2 = integer("effect_index2").default(0)

    override val primaryKey = PrimaryKey(id)
}
