package tw.tsm.domain.valueobjects

/**
 * 裝備加成資料 — domain 層值物件
 *
 * 8 項裝備屬性加成的累加總值。
 * 對應客戶端 `RoleController.lua` 的 EAttribute 常數。
 *
 * 用途：
 * - [PlayerDataDto] 登入時攜帶裝備加成
 * - [PlayerState] 運行時追蹤裝備加成
 * - ItemHandler 穿脫裝備時傳遞加成更新
 */
data class EquipBonusData(
    /** 裝備提昇最大 HP（EAttribute 207） */
    val equipMaxHp: Int = 0,
    /** 裝備提昇最大 SP（EAttribute 208） */
    val equipMaxSp: Int = 0,
    /** 裝備普通攻擊力（EAttribute 210） */
    val equipAtk: Int = 0,
    /** 裝備普通防禦力（EAttribute 211） */
    val equipDef: Int = 0,
    /** 裝備知力（EAttribute 212） */
    val equipInt: Int = 0,
    /** 裝備敏捷力（EAttribute 214） */
    val equipAgi: Int = 0,
    /** 裝備 HPX（EAttribute 218） */
    val equipHpx: Int = 0,
    /** 裝備 SPX（EAttribute 219） */
    val equipSpx: Int = 0
) {
    companion object {
        val EMPTY = EquipBonusData()
    }
}
