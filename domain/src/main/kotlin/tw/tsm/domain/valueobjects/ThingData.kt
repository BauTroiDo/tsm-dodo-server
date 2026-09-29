package tw.tsm.domain.valueobjects

/**
 * Object giá trị thực thể vật phẩm — tương ứng `Item.lua ThingData.New(data)` dòng 81-100
 *
 * Định dạng truyền mạng khi build DVT NFT: 56 bytes (little-endian).
 * ThingData cơ sở dài 35 bytes; Lua Item.lua đọc thêm 21 bytes Myth/NFT khi bật NFT define.
 * Model này hiện chỉ lưu phần base; codec sẽ gửi phần NFT bằng 0 cho đến khi server hỗ trợ Myth.
 */
data class ThingData(
    /** Số hiệu vật phẩm (UInt16)*/
    val itemId: Int = 0,
    /** Số lượng vật phẩm (Int32)*/
    val quantity: Int = 0,
    /** Độ hỏng (Byte, 0=hoàn hảo, 250=hỏng)*/
    val damage: Int = 0,
    /** Thuộc tính (Byte, 0=không, 1=đất, 2=nước, 3=lửa, 4=gió, 5=tâm, 6=?, 7=ánh sáng, 8=bóng tối)*/
    val element: Int = 0,
    /** Giá trị thuộc tính (Byte)*/
    val elementValue: Int = 0,
    /** Kháng (Byte)*/
    val proofKind: Int = 0,
    /** Cấp trưởng thành (Byte, dùng cho vũ khí linh tính)*/
    val growLevel: Int = 0,
    /** Kinh nghiệm trưởng thành (Int32, dùng cho vũ khí linh tính)*/
    val growExp: Int = 0,
    /** Tác dụng đặc biệt (Byte, 0=không, 1=có thể giao dịch 1 lần)*/
    val specialKind: Int = 0,
    /** Thuộc tính linh thạch (Byte)*/
    val stoneAttr: Int = 0,
    /** Cấp tăng cường linh thạch (Byte)*/
    val stoneLevel: Int = 0,
    /** Cấp tăng cường vũ khí chuyên dụng (Byte)*/
    val enhanceLevel: Int = 0,
    /** Thời gian xoá vật phẩm (Double, định dạng OADate)*/
    val deleteTime: Double = 0.0,
    /** ID vật phẩm trước khi hỏng (UInt16)*/
    val damagedItemId: Int = 0,
    /** Khoá (Boolean)*/
    val isLocked: Boolean = false,
    /** Cấp thăng giai (Byte)*/
    val reinforced: Int = 0,
    /** Cấp tẩy luyện 1 (Byte)*/
    val affix1: Int = 0,
    /** Cấp tẩy luyện 2 (Byte)*/
    val affix2: Int = 0,
    /** Cấp tẩy luyện 3 (Byte)*/
    val affix3: Int = 0,
    /** Cấp tăng cường thời trang (Byte)*/
    val styleLevel: Int = 0
) {
    /** Ô này có trống hay không*/
    val isEmpty: Boolean get() = itemId == 0
}
