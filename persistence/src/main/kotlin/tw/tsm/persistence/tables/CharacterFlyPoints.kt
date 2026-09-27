package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色記憶傳送符表 — 對應客戶端 UITeleport.flyPoints
 *
 * 儲存玩家記憶傳送符的已記錄位置。
 * 每個角色最多 10 筆（MaxFlyScroll=5 + MaxFlyScrollExtend=5）。
 */
object CharacterFlyPoints : Table("character_fly_points") {
    /** 角色 ID */
    val charId = long("char_id").index()

    /** 傳送點索引（1-based，1 到 10） */
    val pointIndex = integer("point_index")

    /** 場景 ID */
    val sceneId = integer("scene_id")

    /** X 座標 */
    val x = integer("x")

    /** Y 座標 */
    val y = integer("y")

    override val primaryKey = PrimaryKey(charId, pointIndex)
}
