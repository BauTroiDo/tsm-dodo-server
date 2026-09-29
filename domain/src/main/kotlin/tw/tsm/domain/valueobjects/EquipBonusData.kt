package tw.tsm.domain.valueobjects

/**
 * dữ liệu cộng dồn trang bị — object giá trị tầng domain
 *
 * tổng cộng của 8 khoản cộng thuộc tính từ trang bị.
 * tương ứng các hằng số EAttribute trong `RoleController.lua` của client.
 *
 * mục đích sử dụng:
 * - [PlayerDataDto] mang theo phần cộng dồn trang bị khi đăng nhập
 * - [PlayerState] theo dõi phần cộng dồn trang bị lúc chạy
 * - ItemHandler truyền cập nhật cộng dồn khi mặc/cởi trang bị
 */
data class EquipBonusData(
    /** trang bị tăng HP tối đa (EAttribute 207)*/
    val equipMaxHp: Int = 0,
    /** trang bị tăng SP tối đa (EAttribute 208)*/
    val equipMaxSp: Int = 0,
    /** công tấn công thường của trang bị (EAttribute 210)*/
    val equipAtk: Int = 0,
    /** phòng thủ thường của trang bị (EAttribute 211)*/
    val equipDef: Int = 0,
    /** trí lực của trang bị (EAttribute 212)*/
    val equipInt: Int = 0,
    /** nhanh nhẹn của trang bị (EAttribute 214)*/
    val equipAgi: Int = 0,
    /** HPX của trang bị (EAttribute 218)*/
    val equipHpx: Int = 0,
    /** SPX của trang bị (EAttribute 219)*/
    val equipSpx: Int = 0
) {
    companion object {
        val EMPTY = EquipBonusData()
    }
}
