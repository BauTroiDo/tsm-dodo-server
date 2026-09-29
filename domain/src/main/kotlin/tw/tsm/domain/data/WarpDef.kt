package tw.tsm.domain.data

/**
 * Định nghĩa cổng dịch chuyển — tương ứng `WarpData.New(reader)`
 *
 * Nguồn: `Warp_C.dat`
 * Mỗi bản ghi 14 bytes: name(4) + scene(2) + mark(2) + x(4) + y(4) = 16 bytes (bao gồm ReadUInt32)
 */
data class WarpDef(
    /** ID tên (để đối chiếu với string.Get)*/
    val name: Long,
    /** ID cảnh đích dịch chuyển*/
    val scene: Int,
    /** Cờ đánh dấu trên người*/
    val mark: Int,
    /** Toạ độ X mục tiêu*/
    val x: Int,
    /** Toạ độ Y mục tiêu*/
    val y: Int
)
