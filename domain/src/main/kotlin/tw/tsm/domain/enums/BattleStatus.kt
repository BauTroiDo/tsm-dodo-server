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
    TAUNT(16),
    TRAITOR(17),
    STOOL(18),
    THUNDER_FIRE(19),
    HEALTH_DRAWER(20),
    WEAK_PERCENT(21),
    EW_COMMON_FAINT(22),
    ICE_WALL(23), LIMPID(24), ENLARGE(25), AVOID(26), MIRROR(27), SOUL_MIRROR(28),
    SAME_HEART(29), INSPIRE(30), FIRE_AMULET(31), TRANSFER_ATTACK(32), INVISIBLE(33),
    DOLPHIN_GUARD(34), IMMUNITY_SHIELD(35), ICE_CRYSTALS(36), DRAGON_TRANSFER(37),
    PROTECT(38), MAGIC_PROTECT(39), BRIGHT_MIRROR(40), WAR_STEP(41), WILD_FIGHT_SOUL(42),
    ANGRY_MIND(43), MAGICAL_SHIELD(44), CRAZY_SOUL(45), PITY_SOLDIER(46);       // 土崩陣

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
