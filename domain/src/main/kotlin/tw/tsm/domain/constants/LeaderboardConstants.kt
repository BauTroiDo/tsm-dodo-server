package tw.tsm.domain.constants

import tw.tsm.domain.data.LeaderboardDef

/**
 * Hằng số loại bảng xếp hạng
 *
 * Theo kết quả giải mã LeaderbordInfo_C.dat, 26 bảng xếp hạng chia thành 7 loại.
 */
object LeaderboardConstants {

    /** Xếp hạng cấp độ Ngũ Hành (lọc theo nguyên tố) — IDs 1-5, 32-36*/
    val ELEMENT_LEVEL_IDS = setOf(1, 2, 3, 4, 5, 32, 33, 34, 35, 36)

    /** Xếp hạng chuyển sinh*/
    const val REBORN_ID = 6

    /** Xếp hạng tổng cấp độ*/
    const val TOTAL_LEVEL_ID = 7

    /** Xếp hạng cấp độ thành tựu (cùng logic sắp xếp với tổng cấp độ)*/
    const val ACHIEVEMENT_ID = 20

    /** Xếp hạng tiền*/
    const val GOLD_ID = 10

    /** Xếp hạng tiền đấu giá*/
    const val AUCTION_GOLD_ID = 11

    /** Xếp hạng cấp độ tướng*/
    const val PET_LEVEL_ID = 16

    /** Bảng xếp hạng chưa cài đặt (cần hệ thống tương lai hỗ trợ)*/
    val NOT_IMPLEMENTED_IDS = setOf(8, 9, 17, 19, 27, 29, 30, 31, 40, 44)

    /** subTag → ánh xạ nguyên tố Ngũ Hành (Kim Mộc Thuỷ Hoả Thổ = 1-5)*/
    private val SUBTAG_TO_ELEMENT = mapOf(
        20557L to 1, // Kim
        20558L to 2, // Mộc
        20559L to 3, // Thuỷ
        20560L to 4, // Hoả
        20570L to 5  // Thổ
    )

    /**
     * Phân tích nguyên tố Ngũ Hành từ định nghĩa bảng xếp hạng
     *
     * @return ID nguyên tố (1-5), không nhận dạng được thì trả về 0
     */
    fun resolveElement(def: LeaderboardDef): Int = SUBTAG_TO_ELEMENT[def.subTagText] ?: 0
}
