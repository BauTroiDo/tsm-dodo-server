package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色永標表（BitFlag）
 *
 * 對應客戶端 BitFlag.lua 的 foreverFlags 陣列。
 * 行存在 = 該 flagId 已設定（true）；行不存在 = 未設定（false）。
 *
 * 用途：教學完成標記、坐騎收集、武將收集、卡片收集等。
 */
object CharacterBitFlags : Table("character_bit_flags") {
    /** 角色 ID */
    val charId = long("char_id")

    /** 永標 ID（1-7295，對應 EBitFlag） */
    val flagId = integer("flag_id")

    override val primaryKey = PrimaryKey(charId, flagId)
}
