package tw.tsm.domain.interfaces.repositories

/**
 * Giao diện truy cập CSDL cờ vĩnh viễn — tương ứng BitFlag.lua của client
 *
 * Cờ vĩnh viễn (foreverFlags) dùng để ghi trạng thái vĩnh viễn như đã hoàn thành hướng dẫn, đã đạt thu thập.
 * Dòng tồn tại = flag đã được đặt, dòng không tồn tại = chưa đặt.
 */
interface BitFlagRepository {

    /**
     * Lấy tất cả ID cờ vĩnh viễn đã đặt của nhân vật
     *
     * @param charId ID nhân vật
     * @return tập hợp flagId đã được đặt
     */
    suspend fun getSetFlagIds(charId: Long): Set<Int>

    /**
     * Đặt cờ vĩnh viễn
     *
     * @param charId ID nhân vật
     * @param flagId ID cờ vĩnh viễn
     */
    suspend fun setFlag(charId: Long, flagId: Int)

    /**
     * Xoá cờ vĩnh viễn
     *
     * @param charId ID nhân vật
     * @param flagId ID cờ vĩnh viễn
     */
    suspend fun clearFlag(charId: Long, flagId: Int)
}
