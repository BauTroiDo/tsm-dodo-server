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

interface MiningRepository {
    suspend fun load(charId: Long): MiningRecord?
    suspend fun save(charId: Long, record: MiningRecord)

    /**
     * Atomically compare old BAG + mining record, consume fuel and save mining
     * durability. Null = rejected without any mutation. Never split the writes.
     */
    suspend fun commitFuelRefillAtomic(
        charId: Long, bagSlot: Int, expectedBag: ThingData, quantity: Int,
        expectedMine: MiningRecord?, updatedMine: MiningRecord,
    ): MiningFuelCommit? = error("Atomic mining refill unavailable")
}
