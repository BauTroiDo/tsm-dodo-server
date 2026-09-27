package tw.tsm.gamelogic.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EAttackResult
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EBeHitAction
import tw.tsm.domain.data.SkillDef
import kotlin.math.max

private val logger = KotlinLogging.logger {}

/**
 * 技能效果解析器 — 對應 Fight/Skills lua
 *
 * 負責解析技能的範圍、傷害、狀態效果，
 * 並生成完整的 S:050-001 攻擊結果資料。
 */
class BattleSkillResolver(
    private val skillDefs: Map<Int, SkillDef>
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
     * 查詢技能定義（供 NPC AI 判斷技能類型）
     */
    fun getSkillDef(skillId: Int): SkillDef? = skillDefs[skillId]

    /**
     * 解析並執行技能
     *
     * @param attacker 攻擊者
     * @param grid 戰場棋盤
     * @param skillId 使用的技能 ID（10000=普攻）
     * @param targetCol 目標列
     * @param targetRow 目標行
     * @param terrain 地質屬性
     * @return 技能執行結果
     */
    fun resolveSkill(
        attacker: BattleFighter,
        grid: BattleGrid,
        skillId: Int,
        targetCol: Int,
        targetRow: Int,
        terrain: Int,
        comboMultiplier: Double = 1.0,
        currentRound: Int = 0
    ): SkillResolveResult {
        val skill = if (skillId == BattleConstants.HAND_FIGHT_SKILL_ID) null else skillDefs[skillId]
        val skillLv = if (skill == null) 1 else (attacker.skillLevels[skillId] ?: 1).coerceIn(1, 10)
        val fightArea = skill?.let { getFightArea(it, skillLv) } ?: 1
        val isHeal = skill != null && BattleDamageCalc.isHealSkill(skill)
        val targetsFriendlySide = skill != null && skill.fightWay in FRIENDLY_ONLY_FIGHT_WAYS
        val clearGroups = clearGroups(skillId)
        val isPureDispel = clearGroups.isNotEmpty() && skill != null && skill.fightWay in FRIENDLY_ONLY_FIGHT_WAYS

        // Lua SkillData rule: requireSp == 1 means consume ALL current SP.
        val requiredSp = skill?.requireSp ?: 0
        if (skill != null && attacker.sp < requiredSp) {
            logger.debug { "${attacker.name} SP 不足：需要 $requiredSp，擁有 ${attacker.sp}" }
            return SkillResolveResult(emptyList(), spConsumed = 0, fightArea = fightArea)
        }

        val spConsumed = when {
            skill == null || requiredSp <= 0 -> 0
            requiredSp == 1 -> attacker.sp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            else -> requiredSp
        }
        // 決定目標列表 before mutation. A stale/invalid target must not burn SP.
        val targets = if (targetsFriendlySide) {
            // Client target legality is driven by fightWay; friendly utility/dispel is not always HP heal.
            grid.resolveTargets(fightArea, targetCol, targetRow, !attacker.isPlayerSide)
        } else {
            grid.resolveTargets(fightArea, targetCol, targetRow, attacker.isPlayerSide, isCombo = comboMultiplier > 1.0)
        }

        if (targets.isEmpty()) {
            return SkillResolveResult(emptyList(), spConsumed = 0, fightArea = fightArea)
        }

        if (spConsumed > 0) {
            attacker.sp = max(0, attacker.sp - spConsumed)
        }

        /* targets resolved above */
        val resolvedTargets = targets

        // 攻擊次數（howMuchTimes，0 或 1 都算 1 次）
        val hitTimes = max(1, skill?.howMuchTimes ?: 1)

        val targetResults = mutableListOf<TargetResult>()

        for (target in resolvedTargets) {
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
                        attrChanges = emptyList()
                    )
                )
                continue
            }
            var totalDamage = 0
            var lastRawDamage = 0  // 造成死亡那擊的原始傷害（打飛判定用）
            var finalAttackResult = EAttackResult.HIT
            var finalBeHitAction = EBeHitAction.HIT
            val allAttrChanges = mutableListOf<BattleAttrChange>()

            for (hit in 0 until hitTimes) {
                val rawCalcResult = if (isHeal) {
                    BattleDamageCalc.calculateHeal(attacker, target, skill!!)
                } else {
                    BattleDamageCalc.calculateAttack(attacker, target, skill, terrain, skillId)
                }

                // 套用 Combo 倍率：僅對物理攻擊傷害生效（Combo 不影響回復技能）
                val calcResult = if (!isHeal && comboMultiplier != 1.0) {
                    val scaledChanges = rawCalcResult.attrChanges.map { change ->
                        if (!change.isPositive) {
                            // 傷害值 × comboMultiplier，至少 1
                            val scaled = maxOf(1, (change.value * comboMultiplier).toInt())
                            change.copy(value = scaled)
                        } else change
                    }
                    rawCalcResult.copy(attrChanges = scaledChanges)
                } else {
                    rawCalcResult
                }

                finalAttackResult = calcResult.attackResult
                finalBeHitAction = calcResult.beHitAction

                // 套用傷害/回復
                for (change in calcResult.attrChanges) {
                    when (change.attrKind) {
                        EAttribute.HP -> {
                            if (change.isPositive) {
                                // 回復
                                val actual = minOf(change.value.toLong(), target.maxHp - target.hp).toInt()
                                target.hp = (target.hp + actual).coerceAtMost(target.maxHp)
                                if (actual > 0) allAttrChanges.add(BattleAttrChange(EAttribute.HP, actual, true))
                            } else {
                                // 傷害
                                lastRawDamage = change.value  // 保留原始未 cap 傷害
                                val actual = minOf(change.value.toLong(), target.hp).toInt()
                                target.hp = (target.hp - actual).coerceAtLeast(0)
                                totalDamage += actual
                                if (actual > 0) allAttrChanges.add(BattleAttrChange(EAttribute.HP, actual, false))
                            }
                        }
                        EAttribute.SP -> {
                            if (change.isPositive) {
                                val actual = minOf(change.value, (target.maxSp - target.sp).toInt())
                                target.sp = (target.sp + actual).coerceAtMost(target.maxSp)
                                if (actual > 0) allAttrChanges.add(BattleAttrChange(EAttribute.SP, actual, true))
                            }
                        }
                    }
                }

                // 施加狀態異常
                if (calcResult.inflictedStatus != tw.tsm.domain.enums.BattleStatus.NONE) {
                    val group = modeledStatusGroup(calcResult.inflictedStatus)
                    if (group > 0) {
                        target.setGroupedStatus(group, calcResult.inflictedStatus, calcResult.statusRounds, skillId, currentRound)
                    }
                }

                // 死亡判定
                if (target.hp <= 0) {
                    target.isDead = true
                    // 打飛判定：原始傷害 >= maxHp × 閾值倍率
                    if (lastRawDamage >= (target.maxHp * BattleConstants.FLY_OUT_DAMAGE_RATIO).toLong()) {
                        target.isFlyOut = true
                    }
                    break  // 死了就不再繼續打
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
                    targetFlyOut = target.isFlyOut
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
        tw.tsm.domain.enums.BattleStatus.GOLD_SHIELD -> 2
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
        tw.tsm.domain.enums.BattleStatus.SPELL -> 4
        else -> 0
    }

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

/** 技能解析結果 */
data class SkillResolveResult(
    val targets: List<TargetResult>,
    val spConsumed: Int,
    /** 技能範圍（EFightArea）— 客戶端演出腳本據此計算特效覆蓋目標，必須原樣下發 */
    val fightArea: Int = 1
)

/** 單個目標的攻擊結果 */
data class TargetResult(
    val targetCol: Int,
    val targetRow: Int,
    val attackResult: Int,
    val beHitAction: Int,
    val attrChanges: List<BattleAttrChange>,
    val targetDied: Boolean = false,
    /** 是否被打飛（超殺死亡） */
    val targetFlyOut: Boolean = false
)
