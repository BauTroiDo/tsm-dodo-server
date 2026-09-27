package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色金錢資料表
 *
 * 主鍵 = characterId，每角色一筆。
 */
object CharacterMoney : Table("character_money") {
    /** 所屬角色 ID */
    val characterId = long("character_id")
    /** 金錢 */
    val gold = integer("gold").default(0)
    /** 拍賣金錢 */
    val auctionGold = integer("auction_gold").default(0)

    override val primaryKey = PrimaryKey(characterId)
}
