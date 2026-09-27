package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色快捷鍵資料表
 *
 * 儲存技能/物品快捷鍵配置。
 * dataIndex: 1=PlayerMain(技能), 2=PlayerSub(物品)
 * keyIndex: 1-5
 */
object CharacterHotkeys : Table("character_hotkeys") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    /** 快捷鍵組別：1=PlayerMain(技能), 2=PlayerSub(物品) */
    val dataIndex = integer("data_index")
    /** 鍵位索引（1-5） */
    val keyIndex = integer("key_index")
    /** 技能或物品 ID */
    val useId = integer("use_id")

    override val primaryKey = PrimaryKey(id)
}
