package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * STAGED / UNWIRED C025 item trade durable commit receipt.
 * Written in the SAME SQL transaction as the two BAG+GOLD transfers.
 * A canonical UUID identifies one immutable pair of full snapshots.
 * Rows must NOT be purged by session expiry or normal logout.
 */
object TradeSettlementReceipts : Table("trade_settlement_receipts") {
    val operationId = varchar("operation_id", 36)
    val firstCharacterId = long("first_character_id")
    val secondCharacterId = long("second_character_id")
    val snapshotDigest = varchar("snapshot_digest", 64)
    val committedAtEpochMs = long("committed_at_epoch_ms")

    override val primaryKey = PrimaryKey(operationId)
}
