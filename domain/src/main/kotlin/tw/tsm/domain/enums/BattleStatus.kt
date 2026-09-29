package tw.tsm.domain.enums

/**
 * Trạng thái chiến đấu bất thường — tương ứng `FightStatus.lua` ESkill_Kind
 *
 * Trường `hitStatus` của kỹ năng quyết định hiệu ứng trạng thái kèm theo.
 */
enum class BattleStatus(val id: Int) {
    NONE(0),
    POOR(1),            // Suy thần
    GOD(2),             // Phúc thần
    SPELL(3),           // Phù chú
    BOUNDARY(4),        // Kết giới
    WHIRL_WIND(5),      // Xoáy (không thể hành động)
    ICE_BOUND(6),       // Băng phong (không thể hành động + sát thương khi bị đánh ×1.5)
    CLUMP(7),           // Thụ yêu (biến thành cây, không thể hành động)
    TRANCE(8),          // Mê ngủ (không thể hành động, bị đánh sẽ giải trừ)
    DIZZY_ATK(9),       // Choáng (không thể hành động 1 hiệp)
    GOLD_SHIELD(10),    // Kim chung tráo
    SHRINK(11),         // Thu nhỏ
    POISON(12),         // Nhiễm độc (mất HP mỗi hiệp)
    CHAOS(13),          // Hỗn loạn (ngẫu nhiên tấn công phe mình)
    TARDY(14),          // Trì trệ (nhạy bén giảm một nửa)
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
    ANGRY_MIND(43), MAGICAL_SHIELD(44), CRAZY_SOUL(45), PITY_SOLDIER(46);       // Thổ băng trận

    companion object {
        private val map = entries.associateBy { it.id }

        /** Lấy trạng thái theo ID, không tìm thấy thì trả về NONE*/
        fun fromId(id: Int): BattleStatus = map[id] ?: NONE

        /** Kiểm tra có phải trạng thái hạn chế hành động (không thể ra chiêu) hay không*/
        fun isStunStatus(status: BattleStatus): Boolean = when (status) {
            WHIRL_WIND, ICE_BOUND, CLUMP, TRANCE, DIZZY_ATK -> true
            else -> false
        }
    }
}
