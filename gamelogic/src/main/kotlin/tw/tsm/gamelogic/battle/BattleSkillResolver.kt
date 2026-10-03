package tw.tsm.gamelogic.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EAttackResult
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EBeHitAction
import tw.tsm.domain.data.ExclusiveSkillDef
import tw.tsm.domain.data.SkillDef
import kotlin.math.max

private val logger = KotlinLogging.logger {}

/**
 * Trình phân tích hiệu ứng kỹ năng — tương ứng với Fight/Skills lua
 *
 * Chịu trách nhiệm phân tích phạm vi, sát thương, hiệu ứng trạng thái của kỹ năng,
 * và tạo toàn bộ dữ liệu kết quả tấn công S:050-001.
 */
class BattleSkillResolver(
    private val skillDefs: Map<Int, SkillDef>,
    private val exclusiveSkillDefs: Map<Int, Map<Int, ExclusiveSkillDef>> = emptyMap()
) {
    private val FRIENDLY_ONLY_FIGHT_WAYS = setOf(6, 7, 8, 14)

    /** Mirrors SkillData:GetFightArea(skillLv) from the Lua client. */
    private fun getFightArea(skill: SkillDef, skillLv: Int): Int {
        return when (skill.fightWayGrowType) {
            1 -> when (skillLv) {
                1 -> 1
                in 2..3 -> 2
                in 4..6 -> 5
                in 7..9 -> 6
                10 -> 8
                else -> skill.fightArea
            }
            2 -> when (skillLv) {
                in 1..3 -> 3
                in 4..6 -> 5
                in 7..9 -> 6
                10 -> 8
                else -> skill.fightArea
            }
            else -> skill.fightArea
        }
    }

    /**
     * PC makeExecutionPacket chia damage theo nb_target (mapping với DVT fightArea):
     * 2/3/4 -> /2, 5/6/7 -> /3, 8 -> /4. Area 1 và 255 không có chia ở baseline PC.
     */
    private fun getDamageDivisor(fightArea: Int): Int = when (fightArea) {
        2, 3, 4 -> 2
        5, 6, 7 -> 3
        8 -> 4
        else -> 1
    }

    /**
     * Tra cứu định nghĩa kỹ năng (để NPC AI phán định loại kỹ năng)
     */
    fun getSkillDef(skillId: Int): SkillDef? = skillDefs[skillId]

    /**
     * Phân tích và thực thi kỹ năng
     *
     * @param attacker người tấn công
     * @param grid bàn cờ chiến trường
     * @param skillId ID kỹ năng được sử dụng (10000=tấn công thường)
     * @param targetCol cột mục tiêu
     * @param targetRow hàng mục tiêu
     * @param terrain thuộc tính địa chất
     * @return kết quả thực thi kỹ năng
     */
    fun resolveSkill(
        attacker: BattleFighter,
        grid: BattleGrid,
        skillId: Int,
        targetCol: Int,
        targetRow: Int,
        terrain: Int,
        comboMultiplier: Double = 1.0,
        currentRound: Int = 0,
        allowInsufficientSp: Boolean = false
    ): SkillResolveResult {
        val skill = if (skillId == BattleConstants.HAND_FIGHT_SKILL_ID) null else skillDefs[skillId]
        val skillLv = if (skill == null) 1 else (attacker.skillLevels[skillId] ?: 1).coerceIn(1, 10)
        val fightArea = skill?.let { getFightArea(it, skillLv) } ?: 1
        val damageDivisor = getDamageDivisor(fightArea)
        val isHeal = skill != null && BattleDamageCalc.isHealSkill(skill)
        val targetsFriendlySide = skill != null && skill.fightWay in FRIENDLY_ONLY_FIGHT_WAYS
        val targetsDeadFriendly = skill?.fightWay == 8
        val clearGroups = clearGroups(skillId)
        val isPureDispel = clearGroups.isNotEmpty() && skill != null && skill.fightWay in FRIENDLY_ONLY_FIGHT_WAYS

        // Lua SkillData rule: requireSp == 1 means consume ALL current SP.
        val requiredSp = skill?.requireSp ?: 0
        if (skill != null && attacker.sp < requiredSp && !allowInsufficientSp) {
            logger.debug { "${attacker.name} thiếu SP: cần $requiredSp, đang có ${attacker.sp}" }
            return SkillResolveResult(emptyList(), spConsumed = 0, fightArea = fightArea)
        }

        val spConsumed = when {
            skill == null || requiredSp <= 0 -> 0
            requiredSp == 1 -> attacker.sp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            allowInsufficientSp -> minOf(attacker.sp, requiredSp.toLong()).toInt()
            else -> requiredSp
        }
        // PC server re-checks the queued coordinate at AGI execution time and distinguishes
        // an occupied corpse from an empty cell. An empty cell is retargeted on the originally
        // selected side for every ordinary skill type; an occupied corpse is only retargeted
        // for hostile HandFight/fightWay 1-2. DVT fightWay 8 is stronger evidence than the PC
        // fallback: a missing revive corpse stays a zero-target no-op instead of selecting life.
        var effectiveTargetCol = targetCol
        var effectiveTargetRow = targetRow
        val selectedTarget = grid.getAt(targetCol, targetRow)
        val selectedExistingSameSide = selectedTarget != null &&
            selectedTarget.isPlayerSide == attacker.isPlayerSide
        val canRetargetOccupiedCorpse = skill == null || skill.fightWay <= 2
        val shouldRetarget = when {
            selectedTarget == null -> !targetsDeadFriendly
            selectedTarget.isAlive -> false
            selectedExistingSameSide -> false
            else -> canRetargetOccupiedCorpse
        }

        if (shouldRetarget) {
            val fallback = grid.firstAliveOnSelectedSidePcOrder(targetCol)
                ?: return SkillResolveResult(emptyList(), spConsumed = 0, fightArea = fightArea)
            effectiveTargetCol = fallback.col
            effectiveTargetRow = fallback.row
        }

        // If an ordinary non-revive skill keeps an occupied corpse as its queued centre,
        // PC still serializes that coordinate as a miss/no-effect target. DVT uses the first
        // S:050-001 target as the animation anchor, so preserving it prevents multi-target
        // areas (especially 3/5/6) from shifting to the first surviving neighbour.
        val keptDeadAnchor = selectedTarget != null && !selectedTarget.isAlive &&
            !shouldRetarget && !targetsDeadFriendly

        // Quyết định danh sách mục tiêu trước khi biến đổi. Mục tiêu cũ/không hợp lệ không được tiêu tốn SP.
        val targets = if (targetsFriendlySide) {
            // Client target legality is driven by fightWay; friendly utility/dispel is not always HP heal.
            grid.resolveTargets(
                fightArea,
                effectiveTargetCol,
                effectiveTargetRow,
                !attacker.isPlayerSide,
                deadTargetsOnly = targetsDeadFriendly
            )
        } else {
            grid.resolveTargets(
                fightArea,
                effectiveTargetCol,
                effectiveTargetRow,
                attacker.isPlayerSide,
                isCombo = comboMultiplier > 1.0
            )
        }

        val needsDeadAnchorResult = keptDeadAnchor && targets.none { it === selectedTarget }
        if (targets.isEmpty() && !needsDeadAnchorResult) {
            return SkillResolveResult(emptyList(), spConsumed = 0, fightArea = fightArea)
        }

        if (spConsumed > 0) {
            attacker.sp = max(0, attacker.sp - spConsumed)
        }

        /* targets resolved above */
        val resolvedTargets = targets

        // Số lần tấn công (howMuchTimes, 0 hay 1 đều tính là 1 lần)
        val hitTimes = max(1, skill?.howMuchTimes ?: 1)

        val targetResults = mutableListOf<TargetResult>()
        if (needsDeadAnchorResult) {
            targetResults.add(
                TargetResult(
                    targetCol = targetCol,
                    targetRow = targetRow,
                    attackResult = EAttackResult.MISS,
                    beHitAction = EBeHitAction.NONE,
                    attrChanges = emptyList()
                )
            )
        }

        for (target in resolvedTargets) {
            val dispelAttrChanges = clearGroups.mapNotNull { group ->
                modeledStatusAttribute(group).takeIf { it != EAttribute.NONE }?.let {
                    // Lua RunEffect dispatches status/dispel exclusively from S:050-001 Attr_Kind.
                    // The action skillId is then passed to HandleStatus(group, skillId), where
                    // the client clear-skill lists resolve that group to EStatus.None.
                    BattleAttrChange(it, 0, true)
                }
            }
            if (clearGroups.isNotEmpty()) {
                clearModeledStatus(target, clearGroups)
            }
            if (isPureDispel) {
                targetResults.add(
                    TargetResult(
                        targetCol = target.col,
                        targetRow = target.row,
                        attackResult = EAttackResult.HIT,
                        beHitAction = EBeHitAction.NONE,
                        attrChanges = dispelAttrChanges
                    )
                )
                continue
            }

            // Friendly status/buff skills are not HP heals and must not fall through to attack damage.
            // Client FightRoleController derives their concrete status from skillId.
            val utilityStatus = if (targetsFriendlySide && skill != null && skill.round > 0) {
                BattleDamageCalc.statusFromSkillId(skillId)
            } else tw.tsm.domain.enums.BattleStatus.NONE
            if (utilityStatus != tw.tsm.domain.enums.BattleStatus.NONE) {
                val group = modeledStatusGroup(utilityStatus)
                if (group > 0) {
                    target.setGroupedStatus(group, utilityStatus, skill!!.round, skillId, currentRound)
                }
                val statusAttr = modeledStatusAttribute(utilityStatus)
                targetResults.add(
                    TargetResult(
                        targetCol = target.col,
                        targetRow = target.row,
                        attackResult = EAttackResult.HIT,
                        beHitAction = EBeHitAction.NONE,
                        // Lua FightRoleController.RunEffect only calls HandleStatus when
                        // S:050-001 carries Seal/Vary/Faint/Spec/Strong as Attr_Kind.
                        attrChanges = if (statusAttr != EAttribute.NONE) {
                            listOf(BattleAttrChange(statusAttr, 0, true))
                        } else emptyList()
                    )
                )
                continue
            }

            // SkillData.attribute is the client-declared attribute kind. If it is itself
            // a modeled status attribute, this is a status-only enemy skill: do not
            // fabricate HP damage merely because the target is on the enemy side.
            val enemyStatusOnly = !targetsFriendlySide && skill != null &&
                skill.attribute in MODELED_STATUS_ATTRIBUTES &&
                skill.round > 0 &&
                BattleDamageCalc.statusFromSkillId(skillId) != tw.tsm.domain.enums.BattleStatus.NONE

            var totalDamage = 0
            var lastRawDamage = 0  // Sát thương gốc của đòn gây tử vong (dùng cho phán định đánh bay)
            var passiveReviveHp = 0L
            var finalAttackResult = EAttackResult.HIT
            var finalBeHitAction = EBeHitAction.HIT
            val allAttrChanges = dispelAttrChanges.toMutableList()

            for (hit in 0 until hitTimes) {
                val rawCalcResult = when {
                    isHeal -> BattleDamageCalc.calculateHeal(attacker, target, skill!!)
                    enemyStatusOnly -> BattleDamageCalc.calculateStatusOnly(attacker, target, skill!!, skillId)
                    else -> BattleDamageCalc.calculateAttack(
                        attacker, target, skill, terrain, skillId, skillLv, damageDivisor
                    )
                }

                // Áp hệ số Combo: chỉ có hiệu lực với sát thương tấn công vật lý (Combo không ảnh hưởng kỹ năng hồi phục)
                val calcResult = if (!isHeal && comboMultiplier != 1.0) {
                    val scaledChanges = rawCalcResult.attrChanges.map { change ->
                        if (!change.isPositive) {
                            // Giữ nguyên zero-damage immunity (ví dụ Thiên Lôi Thuẫn);
                            // chỉ các hit đã có damage dương mới bị combo scale và clamp tối thiểu 1.
                            val scaled = if (change.value <= 0) {
                                0
                            } else {
                                maxOf(1, (change.value * comboMultiplier).toInt())
                            }
                            change.copy(value = scaled)
                        } else change
                    }
                    rawCalcResult.copy(attrChanges = scaledChanges)
                } else {
                    rawCalcResult
                }

                finalAttackResult = calcResult.attackResult
                finalBeHitAction = calcResult.beHitAction

                // Áp sát thương/hồi phục
                for (change in calcResult.attrChanges) {
                    when (change.attrKind) {
                        EAttribute.HP -> {
                            if (change.isPositive) {
                                // Hồi phục
                                val actual = minOf(change.value.toLong(), target.maxHp - target.hp).toInt()
                                target.hp = (target.hp + actual).coerceAtMost(target.maxHp)
                                if (actual > 0) {
                                    allAttrChanges.add(BattleAttrChange(EAttribute.HP, actual, true))
                                    if (targetsDeadFriendly && target.hp > 0) {
                                        target.isDead = false
                                        target.isFlyOut = false
                                    }
                                }
                            } else {
                                // Sát thương
                                lastRawDamage = change.value  // Giữ sát thương gốc chưa cap
                                val actual = minOf(change.value.toLong(), target.hp).toInt()
                                target.hp = (target.hp - actual).coerceAtLeast(0)
                                totalDamage += actual
                                if (actual > 0) allAttrChanges.add(BattleAttrChange(EAttribute.HP, actual, false))
                            }
                        }
                        EAttribute.SP -> {
                            if (change.isPositive) {
                                val actual = minOf(change.value.toLong(), target.maxSp - target.sp).toInt()
                                target.sp = (target.sp + actual).coerceAtMost(target.maxSp)
                                if (actual > 0) allAttrChanges.add(BattleAttrChange(EAttribute.SP, actual, true))
                            } else {
                                // S:050-001 uses the same Attr_Kind/Value/Sign contract for SP.
                                // Clamp to the target's current SP so server state and serialized
                                // client delta cannot underflow or diverge.
                                val actual = minOf(change.value.toLong(), target.sp).toInt()
                                target.sp = (target.sp - actual).coerceAtLeast(0)
                                if (actual > 0) allAttrChanges.add(BattleAttrChange(EAttribute.SP, actual, false))
                            }
                        }
                    }
                }

                // Gây trạng thái bất thường
                if (calcResult.inflictedStatus != tw.tsm.domain.enums.BattleStatus.NONE) {
                    val group = modeledStatusGroup(calcResult.inflictedStatus)
                    if (group > 0) {
                        target.setGroupedStatus(group, calcResult.inflictedStatus, calcResult.statusRounds, skillId, currentRound)
                    }
                    val statusAttr = modeledStatusAttribute(calcResult.inflictedStatus)
                    if (statusAttr != EAttribute.NONE) {
                        // S:050-001 Attr_Kind is how the Lua client learns which status
                        // group to apply; value/sign are ignored for status attributes.
                        allAttrChanges.add(BattleAttrChange(statusAttr, 0, true))
                    }
                }

                // Phán định tử vong
                if (target.hp <= 0) {
                    target.isDead = true
                    // Phán định đánh bay: sát thương gốc >= maxHp × hệ số ngưỡng
                    if (lastRawDamage >= (target.maxHp * BattleConstants.FLY_OUT_DAMAGE_RATIO).toLong()) {
                        target.isFlyOut = true
                    }
                    // 14080 hồi sinh xảy ra sau hit gây chết nhưng trước hành động kế tiếp.
                    // Fly-out không thể hồi sinh trên chiến trường.
                    if (!target.isFlyOut) {
                        passiveReviveHp = NpcPassiveCombatRules.tryAutoRevive(target, exclusiveSkillDefs)
                        if (passiveReviveHp > 0) {
                            logger.info {
                                "${target.name} kích hoạt passive 14080: hồi sinh ${passiveReviveHp}/${target.maxHp} HP"
                            }
                        }
                    }
                    break  // Hit gây chết kết thúc chuỗi multi-hit hiện tại
                }
            }

            targetResults.add(
                TargetResult(
                    targetCol = target.col,
                    targetRow = target.row,
                    attackResult = finalAttackResult,
                    beHitAction = finalBeHitAction,
                    attrChanges = allAttrChanges,
                    targetDied = target.isDead,
                    targetFlyOut = target.isFlyOut,
                    passiveReviveHp = passiveReviveHp
                )
            )
        }

        return SkillResolveResult(targetResults, spConsumed, fightArea)
    }
    /**
     * Client clear skills set concrete status to None in one or more status groups.
     * With the current single-status server model, clear only when the active modeled
     * status belongs to a group this skill clears.
     */
    private fun clearModeledStatus(target: BattleFighter, groups: Set<Int>) {
        for (group in groups) {
            target.clearGroupedStatus(group)
        }
    }

    private fun modeledStatusGroup(status: tw.tsm.domain.enums.BattleStatus): Int = when (status) {
        tw.tsm.domain.enums.BattleStatus.WHIRL_WIND,
        tw.tsm.domain.enums.BattleStatus.ICE_BOUND,
        tw.tsm.domain.enums.BattleStatus.CLUMP,
        tw.tsm.domain.enums.BattleStatus.TRANCE,
        tw.tsm.domain.enums.BattleStatus.DIZZY_ATK -> 1
        tw.tsm.domain.enums.BattleStatus.BOUNDARY,
        tw.tsm.domain.enums.BattleStatus.GOD,
        tw.tsm.domain.enums.BattleStatus.GOLD_SHIELD,
        tw.tsm.domain.enums.BattleStatus.ICE_WALL,
        tw.tsm.domain.enums.BattleStatus.LIMPID,
        tw.tsm.domain.enums.BattleStatus.ENLARGE,
        tw.tsm.domain.enums.BattleStatus.AVOID,
        tw.tsm.domain.enums.BattleStatus.MIRROR,
        tw.tsm.domain.enums.BattleStatus.SOUL_MIRROR,
        tw.tsm.domain.enums.BattleStatus.SAME_HEART,
        tw.tsm.domain.enums.BattleStatus.INSPIRE,
        tw.tsm.domain.enums.BattleStatus.FIRE_AMULET,
        tw.tsm.domain.enums.BattleStatus.TRANSFER_ATTACK,
        tw.tsm.domain.enums.BattleStatus.INVISIBLE,
        tw.tsm.domain.enums.BattleStatus.DOLPHIN_GUARD,
        tw.tsm.domain.enums.BattleStatus.IMMUNITY_SHIELD,
        tw.tsm.domain.enums.BattleStatus.ICE_CRYSTALS,
        tw.tsm.domain.enums.BattleStatus.DRAGON_TRANSFER -> 2
        tw.tsm.domain.enums.BattleStatus.POOR,
        tw.tsm.domain.enums.BattleStatus.SHRINK,
        tw.tsm.domain.enums.BattleStatus.POISON,
        tw.tsm.domain.enums.BattleStatus.CHAOS,
        tw.tsm.domain.enums.BattleStatus.TARDY,
        tw.tsm.domain.enums.BattleStatus.LANDSLIP,
        tw.tsm.domain.enums.BattleStatus.TAUNT,
        tw.tsm.domain.enums.BattleStatus.TRAITOR,
        tw.tsm.domain.enums.BattleStatus.STOOL,
        tw.tsm.domain.enums.BattleStatus.THUNDER_FIRE,
        tw.tsm.domain.enums.BattleStatus.HEALTH_DRAWER,
        tw.tsm.domain.enums.BattleStatus.WEAK_PERCENT,
        tw.tsm.domain.enums.BattleStatus.EW_COMMON_FAINT -> 3
        tw.tsm.domain.enums.BattleStatus.SPELL,
        tw.tsm.domain.enums.BattleStatus.PROTECT,
        tw.tsm.domain.enums.BattleStatus.MAGIC_PROTECT -> 4
        tw.tsm.domain.enums.BattleStatus.BRIGHT_MIRROR,
        tw.tsm.domain.enums.BattleStatus.WAR_STEP,
        tw.tsm.domain.enums.BattleStatus.WILD_FIGHT_SOUL,
        tw.tsm.domain.enums.BattleStatus.ANGRY_MIND,
        tw.tsm.domain.enums.BattleStatus.MAGICAL_SHIELD,
        tw.tsm.domain.enums.BattleStatus.CRAZY_SOUL,
        tw.tsm.domain.enums.BattleStatus.PITY_SOLDIER -> 5
        else -> 0
    }

    private fun modeledStatusAttribute(status: tw.tsm.domain.enums.BattleStatus): Int =
        modeledStatusAttribute(modeledStatusGroup(status))

    private fun modeledStatusAttribute(group: Int): Int = when (group) {
        1 -> EAttribute.SEAL
        2 -> EAttribute.VARY
        3 -> EAttribute.FAINT
        4 -> EAttribute.SPEC
        5 -> EAttribute.STRONG
        else -> EAttribute.NONE
    }

    private val MODELED_STATUS_ATTRIBUTES = setOf(
        EAttribute.SEAL,
        EAttribute.VARY,
        EAttribute.FAINT,
        EAttribute.SPEC,
        EAttribute.STRONG
    )

    private fun clearGroups(skillId: Int): Set<Int> = buildSet {
        if (skillId in CLEAR_SEAL_SKILLS) add(1)
        if (skillId in CLEAR_VARY_SKILLS) add(2)
        if (skillId in CLEAR_FAINT_SKILLS) add(3)
        if (skillId in CLEAR_SPEC_SKILLS) add(4)
        if (skillId in CLEAR_STRONG_SKILLS) add(5)
    }

    private val CLEAR_SEAL_SKILLS = setOf(10009,10014,11012,11015,14007,11016,11017,11018,11019,20015,20019,11025,11031,14056,20050,10041,11040,11038,20054)
    private val CLEAR_VARY_SKILLS = setOf(10009,10014,11012,11015,11016,11017,11018,11019,20015,20019,11025,11031,20050,20054,22011,22012,22013,22014,10041,11040,11038)
    private val CLEAR_FAINT_SKILLS = setOf(11012,11015,14014,14022,11016,11017,11018,11019,20015,20019,11025,11031,14056,20050,20054,22005,11040,11038)
    private val CLEAR_SPEC_SKILLS = setOf(20007)
    private val CLEAR_STRONG_SKILLS = setOf(11012,11016,11017,11018,11019,20015,20019,11025,11031,14056,20050,20054,11040,11038)
    private val DISPEL_SKILLS = CLEAR_SEAL_SKILLS + CLEAR_VARY_SKILLS + CLEAR_FAINT_SKILLS + CLEAR_SPEC_SKILLS + CLEAR_STRONG_SKILLS

}

/** Kết quả phân tích kỹ năng*/
data class SkillResolveResult(
    val targets: List<TargetResult>,
    val spConsumed: Int,
    /** Phạm vi kỹ năng (EFightArea) — script biểu diễn của client dựa vào đây tính các mục tiêu bị hiệu ứng phủ, phải gửi xuống nguyên dạng*/
    val fightArea: Int = 1
)

/** Kết quả tấn công trên một mục tiêu đơn lẻ*/
data class TargetResult(
    val targetCol: Int,
    val targetRow: Int,
    val attackResult: Int,
    val beHitAction: Int,
    val attrChanges: List<BattleAttrChange>,
    val targetDied: Boolean = false,
    /** Có bị đánh bay hay không (chết kiểu overkill)*/
    val targetFlyOut: Boolean = false,
    /** 14080: HP tuyệt đối sau hồi sinh; 0 nếu passive không kích hoạt. */
    val passiveReviveHp: Long = 0
)
