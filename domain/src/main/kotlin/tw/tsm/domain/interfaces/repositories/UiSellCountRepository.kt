package tw.tsm.domain.interfaces.repositories

/**
 * Giao diện truy xuất bộ đếm lượt mua 介消
 *
 * Ghi số lượt mua luỹ kế theo từng sellId của nhân vật (dùng để xét bậc giá/giới hạn trần).
 */
interface UiSellCountRepository {

    /**
     * Lấy số lượt mua luỹ kế của 介消 chỉ định
     *
     * @param charId ID nhân vật
     * @param sellId mã 介消
     * @return số lượt luỹ kế (không có bản ghi trả 0)
     */
    suspend fun getCount(charId: Long, sellId: Int): Int

    /**
     * Tăng số lượt mua luỹ kế thêm 1
     *
     * @return số mới sau khi tăng
     */
    suspend fun increment(charId: Long, sellId: Int): Int
}
