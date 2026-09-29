package tw.tsm.domain.interfaces.repositories

/**
 * Interface truy cập dữ liệu tài khoản
 *
 * domain định nghĩa interface, persistence cung cấp phần triển khai.
 */
interface AccountRepository {
    /**
     * Truy vấn ID tài khoản theo tên tài khoản
     *
     * @return ID tài khoản, trả về null nếu không tồn tại
     */
    suspend fun findByAccount(account: String): AccountRecord?

    /**
     * Tạo tài khoản mới
     *
     * @return ID của tài khoản mới
     */
    suspend fun create(account: String, passwordHash: String): Long

    /**
     * Cập nhật mật khẩu
     */
    suspend fun updatePassword(accountId: Long, passwordHash: String)

    /**
     * Cập nhật cấp GM
     *
     * @param accountId ID tài khoản
     * @param gmLevel cấp GM mới (0=người chơi thường, 1+=GM)
     */
    suspend fun updateGmLevel(accountId: Long, gmLevel: Int)
}

/**
 * Đối tượng giá trị bản ghi tài khoản
 */
data class AccountRecord(
    val id: Long,
    val account: String,
    val passwordHash: String,
    val isSuspended: Boolean = false,
    /** Cấp GM (0=người chơi thường, 1+=GM)*/
    val gmLevel: Int = 0
)
