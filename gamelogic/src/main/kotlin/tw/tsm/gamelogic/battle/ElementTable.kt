package tw.tsm.gamelogic.battle

import tw.tsm.domain.enums.Element

/**
 * Bảng tỷ lệ khắc hệ (nguyên tố) — Tương ứng `define.lua` khắc hệ
 *
 * Quy tắc khắc hệ:
 * - Địa → Thủy (Khắc chế) → Hỏa → Phong → Địa (Khắc chế vòng tròn)
 * - Quang ↔ Ám (Khắc lẫn nhau)
 * - Tâm/Vô = Trung tính (Không có thưởng thêm)
 */
object ElementTable {

    /** Tỷ lệ khắc chế (PC BattleAbstract.cs getSkillEffect) */
    private const val ADVANTAGE = 1.2

    /** Tỷ lệ bị khắc (PC BattleAbstract.cs getSkillEffect) */
    private const val DISADVANTAGE = 0.8

    /** Tỷ lệ trung tính */
    private const val NEUTRAL = 1.0

    /**
     * Lấy tỷ lệ sát thương theo hệ
     *
     * @param attacker Hệ của bên tấn công
     * @param defender Hệ của bên phòng thủ
     * @return Tỷ lệ sát thương
     */
    fun getMultiplier(attacker: Element, defender: Element): Double {
        if (attacker == defender) return NEUTRAL
        if (attacker == Element.NONE || attacker == Element.HEART) return NEUTRAL
        if (defender == Element.NONE || defender == Element.HEART) return NEUTRAL

        return when (attacker) {
            Element.EARTH -> when (defender) {
                Element.WATER -> ADVANTAGE
                Element.WIND -> DISADVANTAGE
                else -> NEUTRAL
            }
            Element.WATER -> when (defender) {
                Element.FIRE -> ADVANTAGE
                Element.EARTH -> DISADVANTAGE
                else -> NEUTRAL
            }
            Element.FIRE -> when (defender) {
                Element.WIND -> ADVANTAGE
                Element.WATER -> DISADVANTAGE
                else -> NEUTRAL
            }
            Element.WIND -> when (defender) {
                Element.EARTH -> ADVANTAGE
                Element.FIRE -> DISADVANTAGE
                else -> NEUTRAL
            }
            Element.LIGHT -> when (defender) {
                Element.DARK -> ADVANTAGE
                else -> NEUTRAL
            }
            Element.DARK -> when (defender) {
                Element.LIGHT -> ADVANTAGE
                else -> NEUTRAL
            }
            else -> NEUTRAL
        }
    }

    /**
     * Lấy tỷ lệ dựa trên ID (tiện lợi để gọi trực tiếp bằng int)
     */
    fun getMultiplier(attackerElement: Int, defenderElement: Int): Double {
        return getMultiplier(Element.fromId(attackerElement), Element.fromId(defenderElement))
    }
}