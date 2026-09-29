package tw.tsm.domain.interfaces.repositories

/**
 * Mục dữ liệu phím tắt
 */
data class HotkeyEntry(
    val dataIndex: Int,
    val keyIndex: Int,
    val useId: Int
)

/**
 * Giao diện tầng persist của phím tắt
 */
interface HotkeyRepository {
    /** Tải toàn bộ phím tắt của nhân vật*/
    suspend fun findByCharId(charId: Long): List<HotkeyEntry>

    /** Đặt một phím tắt (upsert)*/
    suspend fun save(charId: Long, dataIndex: Int, keyIndex: Int, useId: Int)

    /** Xoá toàn bộ phím tắt của nhân vật*/
    suspend fun deleteByCharId(charId: Long)
}
