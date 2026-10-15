package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Shared ore pool for a DVT mine type, not per-character machine fuel.
 * A missing row represents the source maximum of 600,000,000.
 * The mining payout formula is NOT known; no background ticker may debit
 * this pool until an actual ore award is source-verified and durable.
 */
object MiningOreReserves : Table("mining_ore_reserves") {
    val mineType = integer("mine_type")
    val remainingOre = long("remaining_ore")
    override val primaryKey = PrimaryKey(mineType)
}
