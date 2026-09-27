package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色已完成任務標記表
 *
 * 儲存任務完成後設定的 bit 標記（MarkManager.flags）。
 * 對應客戶端 MarkManager.bitMarkDatas（bitIndex → bitValue）。
 */
object CharacterMissionFlags : Table("character_mission_flags") {
    /** 角色 ID */
    val charId = long("char_id")

    /** 標記位元索引（對應 Mark_C.dat 的 bitId 欄位） */
    val bitIndex = integer("bit_index")

    /** 標記值（通常為 1） */
    val bitValue = integer("bit_value").default(1)

    override val primaryKey = PrimaryKey(charId, bitIndex)
}
