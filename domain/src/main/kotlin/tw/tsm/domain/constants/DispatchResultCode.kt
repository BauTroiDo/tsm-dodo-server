package tw.tsm.domain.constants

/**
 * Mã kết quả hệ thống phái cử
 *
 * Nguồn: protocal.lua protocolTable[86] (dòng 13366-13446)
 */
object DispatchStartResult {
    /** Thành công*/
    const val SUCCESS = 1
    /** Loại phái cử sai*/
    const val INVALID_KIND = 2
    /** Chuỗi phái cử đã đầy (vượt MaxSliderCount=4)*/
    const val SLOT_FULL = 3
    /** Loại phái cử bị trùng*/
    const val KIND_DUPLICATE = 4
    /** Chỉ số tướng sai*/
    const val INVALID_NPC_INDEX = 5
    /** Tướng đang bận*/
    const val NPC_BUSY = 6
    /** Số lượt phái cử đã đạt tối đa*/
    const val COUNT_FULL = 7
}

object DispatchCompleteResult {
    /** Thành công*/
    const val SUCCESS = 1
    /** Loại phái cử sai*/
    const val INVALID_KIND = 2
    /** Chỉ số tướng sai*/
    const val INVALID_NPC_INDEX = 3
    /** Chưa hoàn thành*/
    const val NOT_FINISHED = 4
    /** Không gian túi đồ không đủ*/
    const val BAG_FULL = 5
    /** Hệ thống vật phẩm đang đóng*/
    const val ITEM_DISABLED = 6
}
