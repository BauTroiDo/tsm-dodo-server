package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.EAttackResult
import tw.tsm.domain.data.ExclusiveSkillDef
import tw.tsm.domain.data.SkillDef
import kotlin.math.max

/**
 * Chỉ chứa những passive NPC có contract định lượng đủ bằng chứng từ DVT.
 *
 * Không suy diễn tỉ lệ proc, ngưỡng HP hoặc hệ số từ mô tả định tính.
 */
object NpcPassiveCombatRules {
    const val HALF_PHYSICAL_DAMAGE = 14057
    const val THUNDER_SHIELD = 14061
    const val PHOENIX_REBIRTH = 14080

    /**
     * 14057 - "Giảm một nửa sát thương vật lý".
     *
     * DVT Skill_C cố định semantics 50%. Đòn vật lý theo contract damage hiện tại:
     * đánh thường hoặc skill atkKind=1.
     *
     * Áp trên damage cuối mà mục tiêu chuẩn bị nhận để câu "giảm một nửa sát thương"
     * không làm thay đổi DEF/element/status pipeline đã được khóa ở các batch trước.
     */
    fun applyFinalIncomingDamage(
        damage: Int,
        target: BattleFighter,
        skill: SkillDef?,
        attackResult: Int
    ): Int {
        if (damage <= 0) return 0

        // 14061 - Thiên Lôi Thuẫn:
        // DVT EAttackResult.Thounder=2 (天雷). Nếu một hit đã được server đánh dấu
        // là Thiên Lôi tự dính lên holder thì passive triệt tiêu HP damage.
        // Việc phát sinh Thounder/proc chance là contract riêng và chưa được đoán ở đây.
        if (target.passiveSkillId == THUNDER_SHIELD && attackResult == EAttackResult.THUNDER) {
            return 0
        }

        if (target.passiveSkillId == HALF_PHYSICAL_DAMAGE && isPhysicalAttack(skill)) {
            return max(1, damage / 2)
        }

        return damage
    }

    /**
     * 14080 - Phượng Sồ Chuyển Sinh.
     * DVT ExclusiveSkill effect kind=4 stores 50/70/100 by passive level;
     * official update notes confirm one automatic revive per battle at that HP percentage.
     */
    fun tryAutoRevive(
        target: BattleFighter,
        exclusiveSkillDefs: Map<Int, Map<Int, ExclusiveSkillDef>>
    ): Long {
        if (target.passiveSkillId != PHOENIX_REBIRTH) return 0
        if (target.passiveReviveUsed || target.isFlyOut || target.maxHp <= 0) return 0
        if (target.hp > 0 || !target.isDead) return 0

        val level = target.passiveSkillLevel
        val record = exclusiveSkillDefs[PHOENIX_REBIRTH]?.get(level) ?: return 0
        val effect = record.effects.firstOrNull { it.kind == 4 } ?: return 0
        val percent = effect.value.toInt()
        if (percent !in 1..100) return 0

        val restoredHp = ((target.maxHp * percent) / 100L).coerceIn(1L, target.maxHp)
        target.hp = restoredHp
        target.isDead = false
        target.isFlyOut = false
        target.passiveReviveUsed = true
        return restoredHp
    }

    fun isPhysicalAttack(skill: SkillDef?): Boolean =
        skill == null || skill.atkKind == 1
}
