package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色介面消費計數表
 *
 * 記錄每個角色各 sellId 的累計購買次數，
 * 用於遞進價格（第 N 次購買價）與購買上限判斷。
 * 例：sellId=8 記憶傳送點擴充 → count 即已擴充格數。
 */
object CharacterUiSellCounts : Table("character_ui_sell_counts") {
    /** 角色 ID */
    val charId = long("char_id").index()

    /** 介消編號（UISell_C.dat 的 sellId） */
    val sellId = integer("sell_id")

    /** 累計購買次數 */
    val count = integer("count").default(0)

    override val primaryKey = PrimaryKey(charId, sellId)
}
