package tw.tsm.domain.constants

/**
 * 屬性常數 — 對應 `RoleController.lua` EAttribute
 *
 * 用於 S:008-001 屬性修改封包的 attrKind 欄位，
 * 以及戰鬥中 S:050-001 / S:051-001 的屬性變化。
 */
object EAttribute {
    const val NONE = 0

    // ── 基礎屬性 ──
    const val ELEMENT = 24      // 元素（1地 2水 3火 4風）
    const val HP = 25           // 體力值
    const val SP = 26           // 技力值
    const val INT = 27          // 知力
    const val ATK = 28          // 攻擊力
    const val DEF = 29          // 防禦力
    const val AGI = 30          // 敏捷
    const val HPX = 31          // HP 上升幅度
    const val SPX = 32          // SP 上升幅度

    // ── 等級/經驗 ──
    const val LV = 35           // 等級
    const val EXP = 36          // 經驗值
    const val SKILL_POINT = 37  // 技能點數
    const val ATTR_POINT = 38   // 屬性點數
    const val PRAC_EXP = 39     // 修練經驗值
    const val TURN2_BALL = 40   // 天珠點數

    // ── 陣營/榮譽 ──
    const val MORAL = 50        // 陣營（1魏 2蜀 3吳 4黃巾 5遊俠）
    const val HONOR = 62        // 官勳
    const val KILL_NUM = 63     // 殺敵點
    const val FAITH = 64        // 忠誠度
    const val BATTLE_HONOR = 66 // 戰役官勳

    // ── 轉生相關 ──
    const val TURN = 70         // 轉生狀態

    // ── BUFF 屬性 ──
    const val BUFF_INT = 71     // BUFF 知力
    const val BUFF_ATK = 72     // BUFF 攻擊力
    const val BUFF_DEF = 73     // BUFF 防禦力
    const val BUFF_AGI = 74     // BUFF 敏捷
    const val BUFF_HPX = 75     // BUFF HP上升幅度
    const val BUFF_SPX = 76     // BUFF SP上升幅度

    // ── 三轉屬性 ──
    const val TURN3_INT = 81
    const val TURN3_ATK = 82
    const val TURN3_DEF = 83
    const val TURN3_HPX = 84
    const val TURN3_SPX = 85
    const val TURN3_AGI = 86

    // ── 強化/特殊 ──
    const val UPGRADE_LV = 87   // 強化等級
    const val STEAL_HP = 88     // 攻擊時 HP 回復
    const val STEAL_SP = 89     // 攻擊時 SP 回復

    // ── 百分比屬性（RoleController.lua L77-86） ──
    const val ATK_PERCENT = 101     // 攻擊力(%)
    const val DEF_PERCENT = 102     // 防禦力(%)
    const val CRITICAL = 103        // 暴擊率(%)
    const val CRIT_DAMAGE = 104     // 暴擊傷害(%)
    const val DODGE = 105           // 閃躲(1~30%)
    const val HIT = 106             // 命中(1~30%)
    const val HP_PERCENT = 107      // 最大HP(%)
    const val SP_PERCENT = 108      // 最大SP(%)
    const val INT_PERCENT = 109     // 知力(%)

    // ── 技能 ──
    const val SKILL = 201           // 技能

    // ── 最大值（RoleController.lua L96-97） ──
    const val MAX_HP = 205          // 最大體力值
    const val MAX_SP = 206          // 最大技力值

    // ── 裝備加成（RoleController.lua L98-109，嚴格對照 Lua） ──
    const val EQUIP_MAX_HP = 207    // 裝備提昇最大 HP
    const val EQUIP_MAX_SP = 208    // 裝備提昇最大 SP
    const val BUFF = 209            // 增益 buff
    const val EQUIP_ATK = 210       // 裝備普通攻擊力
    const val EQUIP_DEF = 211       // 裝備普通防禦力
    const val EQUIP_INT = 212       // 裝備知力
    const val EQUIP_AGI = 214       // 裝備敏捷力（注意：跳過 213）
    const val EXTRA_HP = 215        // 額外 HP（Client 不用）
    const val EXTRA_SP = 216        // 額外 SP（Client 不用）
    const val BOAT_SPEED = 217      // 裝備提升船速
    const val EQUIP_HPX = 218       // 裝備 HPX
    const val EQUIP_SPX = 219       // 裝備 SPX

    // ── 戰鬥狀態 ──
    const val SEAL = 221        // 封印
    const val VARY = 222        // 變身
    const val FAINT = 223       // 昏迷
    const val SPEC = 224        // 特殊
    const val STRONG = 225      // 強化
    const val EQUIP_CRITICLE = 226  // 裝備提升爆擊機率
    const val MOUNTS_LV = 227       // 座騎等級
    const val DIVERSITY = 228       // 多樣性狀態

    // ── 職業/轉生資訊 ──
    const val CAREER = 251      // 職業
    const val TURN3_LV = 252    // 三轉等級
    const val MARRIAGE = 255    // 婚姻狀態
}
