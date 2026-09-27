package tw.tsm.domain.interfaces.repositories

/**
 * 帳號資料存取介面
 *
 * domain 定義介面，persistence 提供實作。
 */
interface AccountRepository {
    /**
     * 根據帳號名稱查詢帳號 ID
     *
     * @return 帳號 ID，不存在回傳 null
     */
    suspend fun findByAccount(account: String): AccountRecord?

    /**
     * 建立新帳號
     *
     * @return 新帳號的 ID
     */
    suspend fun create(account: String, passwordHash: String): Long

    /**
     * 更新密碼
     */
    suspend fun updatePassword(accountId: Long, passwordHash: String)

    /**
     * 更新 GM 等級
     *
     * @param accountId 帳號 ID
     * @param gmLevel 新的 GM 等級（0=普通玩家, 1+=GM）
     */
    suspend fun updateGmLevel(accountId: Long, gmLevel: Int)
}

/**
 * 帳號記錄值物件
 */
data class AccountRecord(
    val id: Long,
    val account: String,
    val passwordHash: String,
    val isSuspended: Boolean = false,
    /** GM 等級（0=普通玩家, 1+=GM） */
    val gmLevel: Int = 0
)
