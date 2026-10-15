package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Durable success-only journal for EAward.Change=9 BAG-backed recipes.
 * Receipt insertion is performed in the SAME SQL transaction as consuming
 * input slots and creating the output item. This is not an artificial
 * daily-claim flag: repeat exchange is legal if materials are paid again.
 */
object CharacterWelfareExchangeReceipts : Table("character_welfare_exchange_receipts") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    val recipeDay = integer("recipe_day")
    val exchangeCount = integer("exchange_count")
    val resultItemId = integer("result_item_id")
    val resultQuantity = integer("result_quantity")
    val claimedAtEpochMs = long("claimed_at_epoch_ms")
    override val primaryKey = PrimaryKey(id)
}
