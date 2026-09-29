package tw.tsm.domain.data

/**
 * định nghĩa cường hoá tướng — tương ứng `NpcUpgrade_C.dat` / NpcUpgradeData trong `NpcData.lua`
 *
 * key = cấp sao đích (1~15), tức yêu cầu và hiệu quả khi cường hoá lên sao thứ N.
 *
 * @property requireMoney tiền game cần dùng
 * @property requireDebris mảnh vỡ tướng cần dùng (RoleCount[110] NpcDebris)
 * @property requirePicture số tranh cần dùng (vật phẩm tương ứng với NpcDef.upgradeItemId)
 * @property attributeAdd chỉ số ngẫu nhiên khi cường hoá (tổng điểm chia ngẫu nhiên vào sáu chỉ số)
 * @property skillLv cấp kỹ năng cường hoá (TODO: cách liên động với hệ thống kỹ năng chưa xác minh)
 * @property upgradeLvIcon biểu tượng cấp sao
 */
data class NpcUpgradeDef(
    val requireMoney: Long,
    val requireDebris: Int,
    val requirePicture: Int,
    val attributeAdd: Int,
    val skillLv: Int,
    val upgradeLvIcon: Int
)
