package tw.tsm.domain.interfaces.repositories

/**
 * Bản ghi tiến độ nhiệm vụ — tương ứng một bản ghi character_missions
 *
 * @param missionId ID nhiệm vụ
 * @param step      giá trị bước hiện tại
 */
data class MissionRecord(val missionId: Int, val step: Int)

/**
 * Bản ghi cờ hoàn thành nhiệm vụ — tương ứng một bản ghi character_mission_flags
 *
 * @param bitIndex chỉ số bit của cờ (Mark_C.dat bitId)
 * @param bitValue giá trị cờ
 */
data class MissionFlagRecord(val bitIndex: Int, val bitValue: Int)

/** Atomic weekly dungeon calendar reset result. */
data class DungeonWeekRefreshResult(
    val removedMissionIds: List<Int> = emptyList(),
    val clearedFlagBits: List<Int> = emptyList(),
)

/**
 * Interface truy cập cơ sở dữ liệu nhiệm vụ
 *
 * Tương ứng tầng lưu bền của MarkManager:
 * - [getMissions]: truy vấn nhiệm vụ đang tiến hành (dùng cho init S:024-006)
 * - [getMissionFlags]: truy vấn cờ nhiệm vụ đã hoàn thành (dùng cho init S:024-007)
 * - [upsertMission]: thêm mới hoặc cập nhật bước nhiệm vụ (kích hoạt S:024-001/002)
 * - [removeMission]: xoá nhiệm vụ (kích hoạt S:024-004)
 * - [upsertFlag]: đặt cờ hoàn thành nhiệm vụ (kích hoạt S:024-005)
 */
interface QuestRepository {

    /** Atomically clear only supplied daily counters and advance their persisted day marker.
     * First observation preserves existing counters; same/older days never reset them.
     */
    suspend fun refreshDungeonDay(charId: Long, dayKey: Int, missionIds: Set<Int>): List<Int> =
        error("Dungeon day persistence is not implemented by this repository")

    /**
     * Atomically clear only supplied weekly counters/claim flags and advance the
     * persisted Monday key. First observation preserves legacy progress because
     * the previous server reset boundary is unknown.
     */
    suspend fun refreshDungeonWeek(
        charId: Long,
        weekKey: Int,
        missionIds: Set<Int>,
        flagBits: Set<Int>,
    ): DungeonWeekRefreshResult =
        error("Dungeon week persistence is not implemented by this repository")


    /**
     * Lấy tất cả nhiệm vụ đang tiến hành của nhân vật
     *
     * @param charId ID nhân vật
     * @return danh sách nhiệm vụ (missionId + step)
     */
    suspend fun getMissions(charId: Long): List<MissionRecord>

    /**
     * Lấy tất cả cờ nhiệm vụ đã hoàn thành của nhân vật
     *
     * @param charId ID nhân vật
     * @return danh sách cờ (bitIndex + bitValue)
     */
    suspend fun getMissionFlags(charId: Long): List<MissionFlagRecord>

    /**
     * Thêm mới hoặc cập nhật bước nhiệm vụ
     *
     * @param charId    ID nhân vật
     * @param missionId ID nhiệm vụ
     * @param step      giá trị bước mới
     */
    suspend fun upsertMission(charId: Long, missionId: Int, step: Int)

    /**
     * Xoá nhiệm vụ đang tiến hành (gọi sau khi hoàn thành nhiệm vụ)
     *
     * @param charId    ID nhân vật
     * @param missionId ID nhiệm vụ
     */
    suspend fun removeMission(charId: Long, missionId: Int)

    /**
     * Đặt cờ hoàn thành nhiệm vụ
     *
     * @param charId    ID nhân vật
     * @param bitIndex  chỉ số bit của cờ
     * @param bitValue  giá trị cờ
     */
    suspend fun upsertFlag(charId: Long, bitIndex: Int, bitValue: Int)

    /** Remove active progress and persist its completion flag as one operation. */
    suspend fun finishMission(charId: Long, missionId: Int, bitId: Int) {
        removeMission(charId, missionId)
        if (bitId > 0) upsertFlag(charId, bitId, 1)
    }

    // ── Theo dõi hoàn thành sự kiện (lưu bền completedEvents DB)──

    /**
     * Truy vấn các sự kiện đã hoàn thành và số lần hoàn thành của nhân vật trên bản đồ chỉ định
     *
     * @param charId ID nhân vật
     * @param mapId  ID bản đồ
     * @return ánh xạ eveNo → số lần hoàn thành
     */
    suspend fun getCompletedEventCounts(charId: Long, mapId: Int): Map<Int, Int>

    /**
     * Ghi lại việc nhân vật hoàn thành sự kiện trên bản đồ chỉ định
     *
     * Dùng ngữ nghĩa UPSERT (đã tồn tại thì không báo lỗi).
     *
     * @param charId ID nhân vật
     * @param mapId  ID bản đồ
     * @param eveNo  số hiệu sự kiện
     */
    suspend fun insertCompletedEvent(charId: Long, mapId: Int, eveNo: Int)

    /**
     * Xoá mọi bản ghi hoàn thành của nhân vật trên bản đồ chỉ định (gọi khi vào cảnh)
     *
     * @param charId ID nhân vật
     * @param mapId  ID bản đồ
     */
    suspend fun clearCompletedEvents(charId: Long, mapId: Int)
}
