package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng cờ đánh dấu nhiệm vụ đã hoàn thành của nhân vật
 *
 * Lưu các bit flag được set sau khi hoàn thành nhiệm vụ (MarkManager.flags).
 * Tương ứng MarkManager.bitMarkDatas của client (bitIndex → bitValue).
 */
object CharacterMissionFlags : Table("character_mission_flags") {
    /** ID nhân vật*/
    val charId = long("char_id")

    /** Chỉ số bit flag (tương ứng trường bitId trong Mark_C.dat)*/
    val bitIndex = integer("bit_index")

    /** Giá trị flag (thường là 1)*/
    val bitValue = integer("bit_value").default(1)

    override val primaryKey = PrimaryKey(charId, bitIndex)
}
