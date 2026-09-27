package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色進行中任務表
 *
 * 儲存每個角色目前持有的任務及其步驟進度。
 * 對應客戶端 MarkManager.missions（missionId → step）。
 */
object CharacterMissions : Table("character_missions") {
    /** 角色 ID（外鍵對應 characters.id） */
    val charId = long("char_id")

    /** 任務 ID（對應 Mark_C.dat 的 id 欄位） */
    val missionId = integer("mission_id")

    /** 當前步驟值 */
    val step = integer("step").default(1)

    override val primaryKey = PrimaryKey(charId, missionId)
}
