package tw.tsm.domain.interfaces.repositories

/**
 * 快捷鍵資料項
 */
data class HotkeyEntry(
    val dataIndex: Int,
    val keyIndex: Int,
    val useId: Int
)

/**
 * 快捷鍵持久層介面
 */
interface HotkeyRepository {
    /** 載入角色所有快捷鍵 */
    suspend fun findByCharId(charId: Long): List<HotkeyEntry>

    /** 設定單一快捷鍵（upsert） */
    suspend fun save(charId: Long, dataIndex: Int, keyIndex: Int, useId: Int)

    /** 刪除角色所有快捷鍵 */
    suspend fun deleteByCharId(charId: Long)
}
