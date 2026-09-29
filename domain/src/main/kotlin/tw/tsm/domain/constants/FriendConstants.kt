package tw.tsm.domain.constants

/**
 * Hằng số hệ thống bạn bè
 *
 * Nguồn: định nghĩa hằng số Social.lua + protocal.lua protocolTable[14]
 */
object FriendConstants {
    /** Số bạn bè tối đa*/
    const val MAX_FRIENDS = 50
    /** Độ tin cậy tối đa*/
    const val MAX_FRIENDLY = 9999
    /** Số quà nhận tối đa mỗi ngày*/
    const val MAX_GIFT_RECEIVE = 10
    /** Số quà tặng tối đa mỗi ngày*/
    const val MAX_GIFT_SEND = 20

    // ── S:014-007 Loại tin nhắn ──
    /** Cảnh đó quá đông người*/
    const val MSG_SCENE_TOO_CROWDED = 1
    /** Mời kết bạn thành công*/
    const val MSG_INVITE_SENT = 2
    /** Mời trùng lặp*/
    const val MSG_DUPLICATE_INVITE = 3
    /** Danh sách lời mời của đối phương đã đầy*/
    const val MSG_TARGET_INVITE_FULL = 4
    /** Danh sách bạn bè của đối phương đã đầy*/
    const val MSG_TARGET_FRIEND_FULL = 5
    /** Danh sách bạn bè của mình đã đầy*/
    const val MSG_MY_FRIEND_FULL = 6
    /** Không tìm thấy người chơi này*/
    const val MSG_PLAYER_NOT_FOUND = 7

    // ── S:014-015 Kết quả nhận quà ──
    /** Nhận quà thành công*/
    const val GIFT_SUCCESS = 1
    /** Không nhận được quà*/
    const val GIFT_NOT_RECEIVED = 2
    /** Đã nhận rồi*/
    const val GIFT_ALREADY_TAKEN = 3
    /** Đã đạt hạn mức nhận*/
    const val GIFT_LIMIT_REACHED = 4
    /** ID vật phẩm sai*/
    const val GIFT_ITEM_ERROR = 5
    /** Không gian túi đồ không đủ*/
    const val GIFT_BAG_FULL = 6
}
