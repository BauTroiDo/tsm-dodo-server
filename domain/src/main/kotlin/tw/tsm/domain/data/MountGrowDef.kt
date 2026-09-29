package tw.tsm.domain.data

/**
 * Định nghĩa tăng trưởng vật cưỡi — tương ứng `MountsGrowData.lua`
 *
 * Nguồn: `MountsGrow_C.dat`
 * key = cấp độ (1-based)
 */
data class MountGrowDef(
    /** Giá trị cộng thêm tốc độ*/
    val speed: Int,
    /** ID vật phẩm cần để thăng cấp*/
    val upItemId: Int,
    /** Số lượng vật phẩm cần để thăng cấp*/
    val upItemCount: Int,
    /** Tiền cần để thăng cấp*/
    val upMoney: Long,
    /** 5 định nghĩa tăng trưởng thuộc tính (index 0-4 = atk/int/def/hp/sp)*/
    val attributes: List<MountAttributeGrow>
)

/**
 * Định nghĩa tăng trưởng đơn thuộc tính của vật cưỡi
 */
data class MountAttributeGrow(
    /** Giá trị cộng thêm thuộc tính của cấp này*/
    val addValue: Int,
    /** ID vật phẩm cần để thêm điểm*/
    val upItemId: Int,
    /** Số lượng vật phẩm cần để thêm điểm*/
    val upItemCount: Int
)
