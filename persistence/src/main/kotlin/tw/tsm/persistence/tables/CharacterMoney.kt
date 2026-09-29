package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng tiền của nhân vật
 *
 * Khoá chính = characterId, mỗi nhân vật một bản ghi.
 */
object CharacterMoney : Table("character_money") {
    /** ID nhân vật sở hữu*/
    val characterId = long("character_id")
    /** Tiền*/
    val gold = integer("gold").default(0)
    /** Tiền đấu giá*/
    val auctionGold = integer("auction_gold").default(0)

    override val primaryKey = PrimaryKey(characterId)
}
