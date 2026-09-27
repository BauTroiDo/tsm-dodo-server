package tw.tsm.domain.interfaces.repositories

/**
 * 任務進度記錄 — 對應 character_missions 單筆
 *
 * @param missionId 任務 ID
 * @param step      當前步驟值
 */
data class MissionRecord(val missionId: Int, val step: Int)

/**
 * 任務完成標記記錄 — 對應 character_mission_flags 單筆
 *
 * @param bitIndex 標記位元索引（Mark_C.dat bitId）
 * @param bitValue 標記值
 */
data class MissionFlagRecord(val bitIndex: Int, val bitValue: Int)

/**
 * 任務資料庫存取介面
 *
 * 對應 MarkManager 的持久化層：
 * - [getMissions]：查詢進行中的任務（S:024-006 init 用）
 * - [getMissionFlags]：查詢已完成的任務標記（S:024-007 init 用）
 * - [upsertMission]：新增或更新任務步驟（S:024-001/002 觸發）
 * - [removeMission]：刪除任務（S:024-004 觸發）
 * - [upsertFlag]：設定任務完成標記（S:024-005 觸發）
 */
interface QuestRepository {

    /**
     * 取得角色所有進行中的任務
     *
     * @param charId 角色 ID
     * @return 任務列表（missionId + step）
     */
    suspend fun getMissions(charId: Long): List<MissionRecord>

    /**
     * 取得角色所有已完成任務標記
     *
     * @param charId 角色 ID
     * @return 標記列表（bitIndex + bitValue）
     */
    suspend fun getMissionFlags(charId: Long): List<MissionFlagRecord>

    /**
     * 新增或更新任務步驟
     *
     * @param charId    角色 ID
     * @param missionId 任務 ID
     * @param step      新步驟值
     */
    suspend fun upsertMission(charId: Long, missionId: Int, step: Int)

    /**
     * 刪除進行中任務（任務完成後呼叫）
     *
     * @param charId    角色 ID
     * @param missionId 任務 ID
     */
    suspend fun removeMission(charId: Long, missionId: Int)

    /**
     * 設定任務完成標記
     *
     * @param charId    角色 ID
     * @param bitIndex  標記位元索引
     * @param bitValue  標記值
     */
    suspend fun upsertFlag(charId: Long, bitIndex: Int, bitValue: Int)

    // ── 事件完成追蹤（completedEvents DB 持久化）──

    /**
     * 查詢角色在指定地圖的已完成事件及其完成次數
     *
     * @param charId 角色 ID
     * @param mapId  地圖 ID
     * @return eveNo → 完成次數 映射
     */
    suspend fun getCompletedEventCounts(charId: Long, mapId: Int): Map<Int, Int>

    /**
     * 記錄角色完成指定地圖的事件
     *
     * 使用 UPSERT 語意（已存在則不報錯）。
     *
     * @param charId 角色 ID
     * @param mapId  地圖 ID
     * @param eveNo  事件編號
     */
    suspend fun insertCompletedEvent(charId: Long, mapId: Int, eveNo: Int)

    /**
     * 清除角色在指定地圖的所有完成記錄（進入場景時呼叫）
     *
     * @param charId 角色 ID
     * @param mapId  地圖 ID
     */
    suspend fun clearCompletedEvents(charId: Long, mapId: Int)
}
