package tw.tsm.domain.interfaces.repositories

/**
 * 介面消費計數存取介面
 *
 * 記錄角色各 sellId 的累計購買次數（遞進價格/上限判斷用）。
 */
interface UiSellCountRepository {

    /**
     * 取得指定介消的累計購買次數
     *
     * @param charId 角色 ID
     * @param sellId 介消編號
     * @return 累計次數（無記錄回 0）
     */
    suspend fun getCount(charId: Long, sellId: Int): Int

    /**
     * 累計購買次數 +1
     *
     * @return 遞增後的新次數
     */
    suspend fun increment(charId: Long, sellId: Int): Int
}
