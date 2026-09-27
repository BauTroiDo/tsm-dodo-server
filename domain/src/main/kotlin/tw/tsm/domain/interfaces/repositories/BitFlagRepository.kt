package tw.tsm.domain.interfaces.repositories

/**
 * 永標資料庫存取介面 — 對應客戶端 BitFlag.lua
 *
 * 永標（foreverFlags）用於記錄教學完成、收集達成等永久狀態。
 * 行存在 = flag 已設定，行不存在 = 未設定。
 */
interface BitFlagRepository {

    /**
     * 取得角色所有已設定的永標 ID
     *
     * @param charId 角色 ID
     * @return 已設定的 flagId 集合
     */
    suspend fun getSetFlagIds(charId: Long): Set<Int>

    /**
     * 設定永標
     *
     * @param charId 角色 ID
     * @param flagId 永標 ID
     */
    suspend fun setFlag(charId: Long, flagId: Int)

    /**
     * 清除永標
     *
     * @param charId 角色 ID
     * @param flagId 永標 ID
     */
    suspend fun clearFlag(charId: Long, flagId: Int)
}
