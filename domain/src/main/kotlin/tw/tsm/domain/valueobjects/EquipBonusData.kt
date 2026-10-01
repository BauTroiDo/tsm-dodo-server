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
    val equipSpx: Int = 0,
    /** Tấn công phần trăm — DVT EAttribute.AtkPercent=101 */
    val atkPercent: Int = 0,
    /** Phòng thủ phần trăm — DVT EAttribute.DefPercent=102 */
    val defPercent: Int = 0,
    /** HP tối đa phần trăm — DVT EAttribute.HpPercent=107 */
    val hpPercent: Int = 0,
    /** Trí lực phần trăm — DVT EAttribute.IntPercent=109 */
    val intPercent: Int = 0,
    /** Tỷ lệ chí mạng cộng thêm (%) — DVT EAttribute.Crit=103 */
    val crit: Int = 0,
    /** Sát thương chí mạng cộng thêm (%) — DVT EAttribute.CritDamage=104 */
    val critDamage: Int = 0,
    /** Tỷ lệ né tránh (%) — DVT EAttribute.Dodge=105 */
    val dodge: Int = 0,
    /** Tỷ lệ mệnh trung (%) — DVT EAttribute.Hit=106 */
    val hit: Int = 0,
    /** Tăng tổng sát thương gây ra (%) — DVT EAttribute.IncreaseTotalDamage=113 */
    val increaseTotalDamage: Int = 0,
    /** Giảm sát thương nhận (%) — DVT EAttribute.DamageReduction=114 */
    val damageReduction: Int = 0,
    /** Giảm tổng sát thương bản thân gây ra (%) — DVT EAttribute.ReduceTotalDamage=117 */
    val reduceTotalDamage: Int = 0,
    /** Tăng tổng sát thương bản thân nhận (%) — DVT EAttribute.IncreaseDamageTakenSelf=118 */
    val increaseDamageTakenSelf: Int = 0
) {
    companion object {
        val EMPTY = EquipBonusData()
    }
}
