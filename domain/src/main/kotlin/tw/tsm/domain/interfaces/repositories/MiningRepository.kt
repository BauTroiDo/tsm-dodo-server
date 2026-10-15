package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.ThingData

/** Trạng thái máy đào của một nhân vật trong Mỏ Thôn Thực. */
data class MiningRecord(
    val mineType: Int = 1,
    val machineFull: List<Int> = listOf(0, 0, 0, 0),
    val updatedAtMillis: Long = 0L,
    val dayLimit: Boolean = false,
)

/** One SQL transaction returns the authoritative post-commit BAG snapshot. */
data class MiningFuelCommit(val bagAfter: ThingData?)

/** Source-confirmed TS currency (ECurrency.TS=8), not an inventory ore item.
 * The rate is backend-only and still unknown: callers must supply a verified award.
 */
data class MiningTsAwardCommit(
    val newlyApplied: Boolean,
    val tsBalance: Int,
    val remainingOre: Long,
)
interface MiningRepository {
    /**
     * One receipt = one TS coin credit and shared mine depletion, or neither.
     * Does not generate rewards or choose a yield rate. No gameplay caller yet.
     */
    suspend fun commitVerifiedTsAwardAtomic(
        receiptId: String,
        characterId: Long,
        mineType: Int,
        oreDebit: Long,
        tsCredit: Int,
        maxReserve: Long,
    ): MiningTsAwardCommit? = error("Verified mining TS reward transaction unavailable")

    suspend fun load(charId: Long): MiningRecord?
    suspend fun save(charId: Long, record: MiningRecord)

    /** Null means a mine has never been depleted; treat as original full pool. */
    suspend fun loadRemainingOre(mineType: Int): Long? = null

    /** For an actual, externally verified ore award only; never spend on mere fuel ticks. */
    suspend fun debitVerifiedOre(mineType: Int, quantity: Long, maxReserve: Long): Boolean =
        error("No atomic shared mine-reserve debiting implementation")

    /**
     * Atomically compare old BAG + mining record, consume fuel and save mining
     * durability. Null = rejected without any mutation. Never split the writes.
     */
    suspend fun commitFuelRefillAtomic(
        charId: Long, bagSlot: Int, expectedBag: ThingData, quantity: Int,
        expectedMine: MiningRecord?, updatedMine: MiningRecord,
    ): MiningFuelCommit? = error("Atomic mining refill unavailable")
}
