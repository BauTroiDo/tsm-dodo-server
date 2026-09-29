package tw.tsm.domain.data

/**
 * Mục thưởng của túi phúc
 *
 * Nguồn: BlissBagData.lua — BlissBagData.New()
 */
data class BlissBagItem(
    val itemId: Int,    // ID vật phẩm thưởng
    val count: Int,     // Số lượng
    val pr: Int,        // Trọng số xác suất
    val kind: Int       // Nhóm loại (khi mở túi mỗi nhóm kind rút một cái)
)

/**
 * Định nghĩa túi phúc (key = ID vật phẩm túi phúc)
 *
 * Nguồn: DataManager.OnLoadBlissBagData — BlissBag_C.dat
 *
 * @param bagItemId ID vật phẩm của túi phúc này
 * @param kindCount số loại vật phẩm nhận khi mở túi (= giá trị kind lớn nhất trong items)
 * @param items danh sách phần thưởng có thể rơi ra
 */
data class BlissBagDef(
    val bagItemId: Int,
    val kindCount: Int,
    val items: List<BlissBagItem>
)
