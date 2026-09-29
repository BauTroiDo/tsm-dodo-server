package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng nhiệm vụ đang thực hiện của nhân vật
 *
 * Lưu các nhiệm vụ nhân vật đang có và tiến độ bước của từng nhiệm vụ.
 * Tương ứng MarkManager.missions của client (missionId → step).
 */
object CharacterMissions : Table("character_missions") {
    /** ID nhân vật (ngoại khoá tương ứng characters.id)*/
    val charId = long("char_id")

    /** ID nhiệm vụ (tương ứng trường id trong Mark_C.dat)*/
    val missionId = integer("mission_id")

    /** Giá trị bước hiện tại*/
    val step = integer("step").default(1)

    override val primaryKey = PrimaryKey(charId, missionId)
}
