package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng phím tắt của nhân vật
 *
 * Lưu cấu hình phím tắt kỹ năng/vật phẩm.
 * dataIndex: 1=PlayerMain(kỹ năng), 2=PlayerSub(vật phẩm)
 * keyIndex: 1-5
 */
object CharacterHotkeys : Table("character_hotkeys") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    /** Nhóm phím tắt: 1=PlayerMain (kỹ năng), 2=PlayerSub (vật phẩm)*/
    val dataIndex = integer("data_index")
    /** Chỉ số vị trí phím (1-5)*/
    val keyIndex = integer("key_index")
    /** ID kỹ năng hoặc vật phẩm*/
    val useId = integer("use_id")

    override val primaryKey = PrimaryKey(id)
}
