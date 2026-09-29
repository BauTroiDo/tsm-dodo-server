package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng cờ vĩnh viễn của nhân vật (BitFlag)
 *
 * Tương ứng mảng foreverFlags trong BitFlag.lua của client.
 * Có dòng = flagId đó đã bật (true); không có dòng = chưa bật (false).
 *
 * Mục đích: cờ hoàn thành hướng dẫn, thu thập vật cưỡi, thu thập tướng, thu thập thẻ bài, v.v.
 */
object CharacterBitFlags : Table("character_bit_flags") {
    /** ID nhân vật*/
    val charId = long("char_id")

    /** ID cờ vĩnh viễn (1-7295, tương ứng EBitFlag)*/
    val flagId = integer("flag_id")

    override val primaryKey = PrimaryKey(charId, flagId)
}
