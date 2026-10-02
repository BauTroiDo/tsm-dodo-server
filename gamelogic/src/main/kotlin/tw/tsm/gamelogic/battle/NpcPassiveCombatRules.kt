package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.EAttackResult
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

    fun isPhysicalAttack(skill: SkillDef?): Boolean =
        skill == null || skill.atkKind == 1
}
