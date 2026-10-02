package tw.tsm.domain.interfaces.repositories

/** Trạng thái máy đào của một nhân vật trong Mỏ Thôn Thực. */
data class MiningRecord(
    val mineType: Int = 1,
    val machineFull: List<Int> = listOf(0, 0, 0, 0),
    val updatedAtMillis: Long = 0L,
    val dayLimit: Boolean = false,
)

interface MiningRepository {
    suspend fun load(charId: Long): MiningRecord?
    suspend fun save(charId: Long, record: MiningRecord)
}
