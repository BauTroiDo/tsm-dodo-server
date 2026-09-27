package tw.tsm.domain.enums

/**
 * 戰鬥狀態異常 — 對應 `FightStatus.lua` ESkill_Kind
 *
 * 技能的 `hitStatus` 欄位決定附加的狀態效果。
 */
enum class BattleStatus(val id: Int) {
    NONE(0),
    POOR(1),            // 衰神
    GOD(2),             // 福神
    SPELL(3),           // 符咒
    BOUNDARY(4),        // 結界
    WHIRL_WIND(5),      // 旋風（無法行動）
    ICE_BOUND(6),       // 冰封（無法行動 + 受擊傷害 ×1.5）
    CLUMP(7),           // 樹精（變樹無法行動）
    TRANCE(8),          // 昏睡（無法行動，受擊解除）
    DIZZY_ATK(9),       // 暈擊（無法行動 1 回合）
    GOLD_SHIELD(10),    // 金鐘罩
    SHRINK(11),         // 縮小
    POISON(12),         // 中毒（每回合損失 HP）
    CHAOS(13),          // 混亂（隨機攻擊友軍）
    TARDY(14),          // 遲緩（敏捷減半）
    LANDSLIP(15),
    TAUNT(16);       // 土崩陣

    companion object {
        private val map = entries.associateBy { it.id }

        /** 根據 ID 取得狀態，找不到回傳 NONE */
        fun fromId(id: Int): BattleStatus = map[id] ?: NONE

        /** 判斷是否為行動限制狀態（無法出招） */
        fun isStunStatus(status: BattleStatus): Boolean = when (status) {
            WHIRL_WIND, ICE_BOUND, CLUMP, TRANCE, DIZZY_ATK -> true
            else -> false
        }
    }
}
