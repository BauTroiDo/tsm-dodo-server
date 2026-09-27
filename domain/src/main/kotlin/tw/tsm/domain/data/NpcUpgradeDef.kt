package tw.tsm.domain.data

/**
 * 武將強化定義 — 對應 `NpcUpgrade_C.dat` / `NpcData.lua` NpcUpgradeData
 *
 * key = 目標星等（1~15），即「強化到第 N 星」的需求與效果。
 *
 * @property requireMoney 需求遊戲幣
 * @property requireDebris 需求武將碎片（RoleCount[110] NpcDebris）
 * @property requirePicture 需求畫冊數（NpcDef.upgradeItemId 對應物品）
 * @property attributeAdd 強化隨機數值（隨機分配到六圍的總點數）
 * @property skillLv 強化技等級（TODO：與技能系統連動方式待查證）
 * @property upgradeLvIcon 星等圖示
 */
data class NpcUpgradeDef(
    val requireMoney: Long,
    val requireDebris: Int,
    val requirePicture: Int,
    val attributeAdd: Int,
    val skillLv: Int,
    val upgradeLvIcon: Int
)
