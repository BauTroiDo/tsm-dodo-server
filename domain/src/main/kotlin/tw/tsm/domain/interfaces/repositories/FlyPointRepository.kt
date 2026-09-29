package tw.tsm.domain.interfaces.repositories

/**
 * Bản ghi điểm dịch chuyển ghi nhớ
 */
data class FlyPointRecord(
    val index: Int,
    val sceneId: Int,
    val x: Int,
    val y: Int
)

/**
 * Interface truy cập dữ liệu bùa dịch chuyển ghi nhớ
 */
interface FlyPointRepository {

    /**
     * Lấy tất cả điểm dịch chuyển ghi nhớ của nhân vật
     *
     * @param charId ID nhân vật
     * @return Map<chỉ số(1-based), FlyPointRecord>
     */
    suspend fun getAll(charId: Long): Map<Int, FlyPointRecord>

    /**
     * Lưu/cập nhật điểm dịch chuyển ghi nhớ
     *
     * @param charId ID nhân vật
     * @param index chỉ số điểm dịch chuyển (1-based)
     * @param sceneId ID cảnh
     * @param x toạ độ X
     * @param y toạ độ Y
     */
    suspend fun save(charId: Long, index: Int, sceneId: Int, x: Int, y: Int)
}
