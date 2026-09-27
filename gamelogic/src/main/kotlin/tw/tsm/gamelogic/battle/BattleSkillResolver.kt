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
        comboMultiplier: Double = 1.0
    ): SkillResolveResult {
        val skill = if (skillId == BattleConstants.HAND_FIGHT_SKILL_ID) null else skillDefs[skillId]
        val fightArea = skill?.fightArea ?: 1
        val isHeal = skill != null && BattleDamageCalc.isHealSkill(skill)

        // SP 消耗檢查
        if (skill != null && attacker.sp < skill.requireSp) {
            logger.debug { "${attacker.name} SP 不足：需要 ${skill.requireSp}，擁有 ${attacker.sp}" }
            return SkillResolveResult(emptyList(), spConsumed = 0, fightArea = fightArea)
        }

        // 消耗 SP
        val spConsumed = skill?.requireSp ?: 0
        if (spConsumed > 0) {
            attacker.sp = max(0, attacker.sp - spConsumed)
        }

        // 決定目標列表
        val targets = if (isHeal) {
            // 回復技能：對友方施放
            grid.resolveTargets(fightArea, targetCol, targetRow, !attacker.isPlayerSide)
        } else {
            grid.resolveTargets(fightArea, targetCol, targetRow, attacker.isPlayerSide, isCombo = comboMultiplier > 1.0)
        }

        if (targets.isEmpty()) {
            return SkillResolveResult(emptyList(), spConsumed, fightArea)
        }

        // 攻擊次數（howMuchTimes，0 或 1 都算 1 次）
        val hitTimes = max(1, skill?.howMuchTimes ?: 1)

        val targetResults = mutableListOf<TargetResult>()

        for (target in targets) {
            var totalDamage = 0
            var lastRawDamage = 0  // 造成死亡那擊的原始傷害（打飛判定用）
            var finalAttackResult = EAttackResult.HIT
            var finalBeHitAction = EBeHitAction.HIT
            val allAttrChanges = mutableListOf<BattleAttrChange>()

            for (hit in 0 until hitTimes) {
                val rawCalcResult = if (isHeal) {
                    BattleDamageCalc.calculateHeal(attacker, target, skill!!)
                } else {
                    BattleDamageCalc.calculateAttack(attacker, target, skill, terrain)
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
                    target.statusEffect = calcResult.inflictedStatus
                    target.statusRounds = calcResult.statusRounds
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
