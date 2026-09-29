package tw.tsm.domain.constants

/**
 * Mã kết quả yêu cầu PK (S:033-001)
 *
 * Nguồn: protocal.lua protocolTable[33][1] (dòng 7019-7031)
 */
object PkResultCode {
    /** Đối phương chưa bật công tắc PK*/
    const val PK_OFF = 1
    /** Đối phương chưa bật công tắc tham chiến*/
    const val JOIN_OFF = 2
    /** Đối phương đang trong lúc hỏi đáp chiến đấu*/
    const val IN_QUESTION = 3
    /** Chiến đấu đặc biệt không thể tham chiến*/
    const val SPECIAL_BATTLE = 4
    /** Trận cổng thành, khác liên minh không được tham chiến*/
    const val CITY_DIFF_ALLY = 5
    /** Trận cổng thành, khác quân đoàn không được tham chiến*/
    const val CITY_DIFF_GROUP = 6
    /** Người chơi đó hiện đang ở lữ quán*/
    const val RESTING = 7
    /** Cảnh này không thể tham chiến*/
    const val SCENE_NO_PK = 8
    /** Không được PK trong cùng liên minh*/
    const val SAME_ALLY = 9
    /** Đối phương đang ở trạng thái vô địch, không thể tấn công*/
    const val INVINCIBLE = 10
}
