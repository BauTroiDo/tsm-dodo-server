package tw.tsm.domain.interfaces.repositories

/**
 * 角色計數器資料存取介面
 *
 * 對應客戶端 RoleCount.lua 的持久化層。
 */
interface RoleCountRepository {

    /**
     * 載入角色所有計數器
     *
     * @param charId 角色 ID
     * @return Map<countIndex, Pair<value, maxValue>>
     */
    suspend fun loadAll(charId: Long): Map<Int, Pair<Int, Int>>

    /**
     * 設定計數器值
     *
     * @param charId 角色 ID
     * @param countIndex RoleCount 索引
     * @param value 當前值
     * @param maxValue 上限值
     */
    suspend fun upsert(charId: Long, countIndex: Int, value: Int, maxValue: Int)

    /**
     * 批量儲存計數器
     *
     * @param charId 角色 ID
     * @param counts Map<countIndex, Pair<value, maxValue>>
     */
    suspend fun saveAll(charId: Long, counts: Map<Int, Pair<Int, Int>>)
}
