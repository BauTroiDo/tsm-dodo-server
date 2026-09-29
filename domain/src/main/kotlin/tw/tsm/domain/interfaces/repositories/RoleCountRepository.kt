package tw.tsm.domain.interfaces.repositories

/**
 * Interface truy cập dữ liệu bộ đếm của nhân vật
 *
 * Lớp lưu trữ bền vững tương ứng RoleCount.lua phía client.
 */
interface RoleCountRepository {

    /**
     * Nạp toàn bộ bộ đếm của nhân vật
     *
     * @param charId ID nhân vật
     * @return Map<countIndex, Pair<value, maxValue>>
     */
    suspend fun loadAll(charId: Long): Map<Int, Pair<Int, Int>>

    /**
     * Đặt giá trị bộ đếm
     *
     * @param charId ID nhân vật
     * @param countIndex chỉ số RoleCount
     * @param value giá trị hiện tại
     * @param maxValue giá trị trần
     */
    suspend fun upsert(charId: Long, countIndex: Int, value: Int, maxValue: Int)

    /**
     * Lưu hàng loạt bộ đếm
     *
     * @param charId ID nhân vật
     * @param counts Map<countIndex, Pair<value, maxValue>>
     */
    suspend fun saveAll(charId: Long, counts: Map<Int, Pair<Int, Int>>)
}
