package tw.tsm.domain.constants

/**
 * Hằng số hệ thống thư
 *
 * Nguồn: protocal.lua protocolTable[83], Social.lua EMailState
 */
object MailConstants {
    // ── Trạng thái thư ──
    /** Chưa đọc*/
    const val STATE_UNREAD = 0
    /** Đã đọc*/
    const val STATE_READ = 1
    /** Đã nhận tệp đính kèm*/
    const val STATE_TAKEN = 2

    // ── Loại tệp đính kèm ──
    /** Vật phẩm*/
    const val ATTACH_KIND_ITEM = 1
    /** Tướng NPC*/
    const val ATTACH_KIND_NPC = 2

    // ── Mã lỗi S:083-005 ──
    /** Không gian túi đồ không đủ*/
    const val ERROR_BAG_FULL = 4
    /** Đã nhận tệp đính kèm rồi*/
    const val ERROR_ALREADY_TAKEN = 5
    /** Thư đã bị xóa*/
    const val ERROR_ALREADY_DELETED = 6
    /** Thư đã hết hạn*/
    const val ERROR_EXPIRED = 7
    /** Sức chứa tiền không đủ*/
    const val ERROR_MONEY_FULL = 8
    /** Hệ thống túi đồ đang đóng (đang chiến đấu, v.v.)*/
    const val ERROR_BAG_CLOSED = 9

    /** Số ngày hết hạn thư mặc định*/
    const val DEFAULT_EXPIRE_DAYS = 30
    /** Số lượng tệp đính kèm tối đa*/
    const val MAX_ATTACHMENTS = 5
}
