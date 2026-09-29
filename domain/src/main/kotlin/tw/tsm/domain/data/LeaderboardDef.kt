package tw.tsm.domain.data

/**
 * Định nghĩa bảng xếp hạng — tương ứng `LeaderboardData.lua`
 *
 * Nguồn: `LeaderbordInfo_C.dat`
 * Server chỉ cần id và stateEnable, các thuộc tính còn lại dành cho UI của client.
 */
data class LeaderboardDef(
    /** ID bảng xếp hạng*/
    val id: Int,
    /** ID văn bản nhãn chính (dùng cho client)*/
    val mainTagText: Long,
    /** ID chuỗi văn bản của tab con (dùng cho client)*/
    val subTagText: Long,
    /** ID chuỗi văn bản tên trường điểm số (dùng cho client)*/
    val scoreText: Long,
    /** ID chuỗi văn bản tên trường tên người chơi (dùng cho client)*/
    val nameText: Long,
    /** ID phần thưởng*/
    val awardId: Int,
    /** Mẫu định dạng điểm số*/
    val scoreFormat: String,
    /** Có hiển thị trạng thái tăng/giảm hay không*/
    val stateEnable: Boolean,
    /** flagId phần thưởng top 3*/
    val rankAwardFlagIds: List<Int>,
    /** Cờ bit chức năng*/
    val bitFunction: Int
)
