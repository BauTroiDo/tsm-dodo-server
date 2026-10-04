package tw.tsm.domain.interfaces.repositories

/**
 * Trạng thái phiên phụ bản đang hoạt động cần sống qua reconnect/server restart.
 *
 * Đây là state backend: client DVT chỉ nhận startTime/dungeonId qua S:047-018,
 * còn điểm quay về phải do server nhớ vì một số scene (ví dụ 62003) không có
 * Eve SceneInfo/resultType=2 để suy ngược lối ra.
 */
data class DungeonRunRecord(
    val characterId: Long,
    val dungeonId: Int,
    val startTime: Double,
    val startedAtMillis: Long,
    val instanceId: Int,
    val originMapId: Int,
    val originX: Int,
    val originY: Int,
    val originInstanceId: Int,
    val settled: Boolean = false,
)

interface DungeonRunRepository {
    suspend fun load(characterId: Long): DungeonRunRecord?
    suspend fun save(record: DungeonRunRecord)
    suspend fun clear(characterId: Long)
}
