package tw.tsm.persistence.tables
import org.jetbrains.exposed.v1.core.Table
/** Durable, idempotent proof of a source-verified mining TS currency award. */
object MiningTsAwardReceipts : Table("mining_ts_award_receipts") {
    val receiptId = varchar("receipt_id", 128)
    val characterId = long("character_id")
    val mineType = integer("mine_type")
    val oreDebit = long("ore_debit")
    val tsCredit = integer("ts_credit")
    val tsBalanceAfter = integer("ts_balance_after")
    val oreRemainingAfter = long("ore_remaining_after")
    override val primaryKey = PrimaryKey(receiptId)
}
