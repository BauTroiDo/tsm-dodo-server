package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色派遣進度資料表
 *
 * 儲存進行中的派遣任務（完成領獎後刪除）
 */
object CharacterDispatches : Table("character_dispatches") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    /** 進度條索引（1-4） */
    val slotIndex = integer("slot_index")
    /** 派遣種類 */
    val dispatchKind = integer("dispatch_kind")
    /** 客棧武將索引 */
    val innPetIndex = integer("inn_pet_index")
    /** 開始時間（OLE Automation Date） */
    val startTime = double("start_time")
    /** 結束時間（OLE Automation Date） */
    val endTime = double("end_time")
    /** 效果索引 1 */
    val effectIndex1 = integer("effect_index1").default(0)
    /** 效果索引 2 */
    val effectIndex2 = integer("effect_index2").default(0)

    override val primaryKey = PrimaryKey(id)
}
