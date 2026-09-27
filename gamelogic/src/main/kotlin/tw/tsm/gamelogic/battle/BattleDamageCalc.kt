package tw.tsm.gamelogic.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttackResult
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EBeHitAction
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.enums.BattleStatus
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * 戰鬥傷害計算器 — 對照 tskeropc `BattleAbstract.cs` CalcDmg + execute()
 *
 * 負責：命中判定、基礎傷害計算、防禦減傷、乘算修正、暴擊判定、狀態異常施加。
 *
 * 傷害管道：
 * 1. CalcDmg（攻擊者端）→ 基礎傷害（含等級加成）
 * 2. execute（目標端）→ 減去目標等級+防禦
 * 3. 乘算修正 → 元素/轉生/亂數/暴擊/PvP/冰封/防禦
 */
object BattleDamageCalc {

    /** 基礎傷害上限（KERO: if (dmgbase > 50000) dmgbase = 50000） */
    private const val MAX_BASE_DAMAGE = 50000

    /**
     * 計算單次攻擊結果
     *
     * @param attacker 攻擊者
     * @param target 目標
     * @param skill 使用的技能（null=普攻）
     * @param terrain 地質（目前未使用，預留）
     * @return 攻擊結果
     */
    fun calculateAttack(
        attacker: BattleFighter,
        target: BattleFighter,
        skill: SkillDef?,
        terrain: Int = 0,
        skillId: Int = 0
    ): AttackCalcResult {
        // 命中判定
        val hitRate = calculateHitRate(attacker.agi, target.agi)
        if (Random.nextInt(100) >= hitRate) {
            return AttackCalcResult(
                attackResult = EAttackResult.MISS,
                beHitAction = EBeHitAction.DODGE,
                damage = 0,
                attrChanges = emptyList()
            )
        }

        // Phase 1: 基礎傷害（CalcDmg 等價物）
        val baseDamage = calculateBaseDamage(attacker, skill)

        // Phase 2: 防禦減傷（KERO execute: dmg - dest.getLvl() - dest.getDef()）
        val reducedDmg = (baseDamage - target.level - target.def).toDouble()

        // Phase 3: 乘算修正因子
        val skillElement = skill?.element ?: attacker.element
        val elementMultiplier = ElementTable.getMultiplier(skillElement, target.element)

        val rbDiff = maxOf(0, attacker.turn - target.turn)
        val rebornMultiplier = 1.0 + 0.1 * rbDiff

        val randomFactor = 0.9 + Random.nextDouble() * 0.2

        val thunderRate = calculateThunderRate(attacker.agi)
        val isThunder = Random.nextInt(100) < thunderRate
        val thunderMultiplier = if (isThunder) 1.5 else 1.0

        val pvpMultiplier = if (target.isHumanPlayer || target.kind == EHuman.FOLLOW_NPC) 0.5 else 1.0

        val statusMultiplier = if (target.statusEffect == BattleStatus.ICE_BOUND) 1.5 else 1.0

        val defendMultiplier = if (target.isDefending) 0.5 else 1.0

        val finalDamage = max(1, (reducedDmg * elementMultiplier * rebornMultiplier * randomFactor *
            thunderMultiplier * pvpMultiplier * statusMultiplier * defendMultiplier).roundToInt())

        logger.debug { "${attacker.name}(lv=${attacker.level},atk=${attacker.atk},int=${attacker.intelligence}) → " +
            "${target.name}(lv=${target.level},def=${target.def}): base=$baseDamage reduced=${reducedDmg.toInt()} " +
            "elem=${"%.2f".format(elementMultiplier)} final=$finalDamage${if (isThunder) " 暴擊!" else ""}" }

        val attackResult = if (isThunder) EAttackResult.THUNDER else EAttackResult.HIT
        val beHitAction = EBeHitAction.HIT

        // 屬性變化列表（HP 減少）
        val attrChanges = mutableListOf(
            BattleAttrChange(EAttribute.HP, finalDamage, false)
        )

        // 狀態異常施加
        var inflictedStatus = BattleStatus.NONE
        var statusRounds = 0
        if (skill != null && skill.hitStatus > 0 && skill.round > 0) {
            val statusChance = 50 + (attacker.intelligence - target.intelligence) / 2
            if (Random.nextInt(100) < statusChance.coerceIn(10, 90)) {
                // Client FightRoleController resolves the concrete status from skillId
                // inside a status group; hitStatus is not a concrete BattleStatus enum.
                inflictedStatus = statusFromSkillId(skillId)
                statusRounds = if (inflictedStatus != BattleStatus.NONE) skill.round else 0
            }
        }

        return AttackCalcResult(
            attackResult = attackResult,
            beHitAction = beHitAction,
            damage = finalDamage,
            attrChanges = attrChanges,
            inflictedStatus = inflictedStatus,
            statusRounds = statusRounds
        )
    }

    /**
     * Concrete status mapping proven by FightRoleController.lua.
     * Keep this deliberately limited to server-modeled statuses.
     */
    private fun statusFromSkillId(skillId: Int): BattleStatus = when {
        skillId in ICE_BOUND_SKILLS -> BattleStatus.ICE_BOUND
        skillId in WHIRL_WIND_SKILLS -> BattleStatus.WHIRL_WIND
        skillId in CLUMP_SKILLS -> BattleStatus.CLUMP
        skillId in TRANCE_SKILLS -> BattleStatus.TRANCE
        skillId in DIZZY_SKILLS -> BattleStatus.DIZZY_ATK
        skillId in POISON_SKILLS -> BattleStatus.POISON
        skillId in CHAOS_SKILLS -> BattleStatus.CHAOS
        skillId in TARDY_SKILLS -> BattleStatus.TARDY
        skillId in LANDSLIP_SKILLS -> BattleStatus.LANDSLIP
        else -> BattleStatus.NONE
    }

    /**
     * 計算回復技能效果
     *
     * 回復量 = (知力 × 0.5 + 等級) × 技能數值 / 100
     */
    fun calculateHeal(
        caster: BattleFighter,
        target: BattleFighter,
        skill: SkillDef
    ): AttackCalcResult {
        val healBase = (caster.intelligence * 0.5 + caster.level).toInt()
        val healAmount = max(1, (healBase * skill.numerical / 100).toInt())
        val actualHeal = minOf(healAmount.toLong(), target.maxHp - target.hp).toInt()

        return AttackCalcResult(
            attackResult = EAttackResult.HIT,
            beHitAction = EBeHitAction.NONE,
            damage = 0,
            attrChanges = if (actualHeal > 0) listOf(
                BattleAttrChange(EAttribute.HP, actualHeal, true)
            ) else emptyList()
        )
    }

    // ── 私有計算函數 ──

    /**
     * 基礎傷害計算（CalcDmg 等價物）
     *
     * 對照 tskeropc `BattleAbstract.cs` CalcDmg 第 136-170 行。
     * 等級是傷害的主要來源（NPC .dat 的 ATK 值偏低，設計上依賴等級加成）。
     *
     * 普通攻擊：(atk × 1.6 + level) + level × 2
     * 物理技能：(atk × 1.6 + int × 0.4 + level) × numerical/100 + level × 2
     * 法術技能：(int × 2.0 + level) × numerical/100 + level × 2
     */
    private fun calculateBaseDamage(attacker: BattleFighter, skill: SkillDef?): Int {
        val levelDmg = attacker.level * 2

        val baseDmg = if (skill == null) {
            // 普通攻擊（PC BattleAbstract.cs: skill == 10000 -> initAtk * 1.6）
            val dmgBase = (attacker.atk * 1.6 + attacker.level).toInt()
            dmgBase + levelDmg
        } else if (skill.attribute == EAttribute.INT) {
            // 法術技能（PC BattleAbstract.cs: else 分支 -> initMag * 2.0）
            val dmgBase = (attacker.intelligence * 2.0 + attacker.level).toInt()
            (dmgBase * skill.numerical / 100).toInt() + levelDmg
        } else {
            // 物理技能（PC BattleAbstract.cs: unk17 == 1 -> initAtk * 1.6 + initMag * 0.4）
            val dmgBase = (attacker.atk * 1.6 + attacker.intelligence * 0.4 + attacker.level).toInt()
            (dmgBase * skill.numerical / 100).toInt() + levelDmg
        }

        return baseDmg.coerceAtMost(MAX_BASE_DAMAGE)
    }

    /**
     * 命中率：90% 基礎 + 敏捷差/2
     */
    private fun calculateHitRate(atkAgi: Int, defAgi: Int): Int {
        return (90 + (atkAgi - defAgi) / 2).coerceIn(5, 99)
    }

    /**
     * 暴擊率：5% 基礎 + 敏捷/10
     */
    private fun calculateThunderRate(agi: Int): Int {
        return (5 + agi / 10).coerceIn(1, 50)
    }

    /**
     * 判斷是否為回復技能（水系回復類）
     */
    fun isHealSkill(skill: SkillDef): Boolean {
        return skill.element == 2 && skill.attribute == EAttribute.HP
    }
    private val ICE_BOUND_SKILLS = setOf(11014, 20026, 11027, 11039, 11028, 22016, 22017, 22044)
    private val WHIRL_WIND_SKILLS = setOf(13002, 13015, 13016, 13017, 13018, 13046, 13050, 13052, 20027, 20048, 21042, 23036, 46002)
    private val CLUMP_SKILLS = setOf(10004, 20025, 20049, 10033)
    private val TRANCE_SKILLS = setOf(14008, 23028, 23039)
    private val DIZZY_SKILLS = setOf(13007, 20016, 20020, 13029, 23015, 23027, 13041, 21033, 13044, 22041, 22043, 22052, 10044)
    private val POISON_SKILLS = setOf(14015, 20023, 21008, 20047, 23006, 23011, 23012, 23013, 23014, 21032, 21039, 22042)
    private val CHAOS_SKILLS = setOf(11046, 14021, 20014, 20020, 20055, 23009, 23007, 23022, 20051, 14065, 21041, 20058, 41002)
    private val TARDY_SKILLS = setOf(10016, 10017, 10018, 10019, 11045, 14097, 21001, 21028, 23027)
    private val LANDSLIP_SKILLS = setOf(10025)

}

/**
 * 單次攻擊計算結果
 */
data class AttackCalcResult(
    val attackResult: Int,
    val beHitAction: Int,
    val damage: Int,
    val attrChanges: List<BattleAttrChange>,
    val inflictedStatus: BattleStatus = BattleStatus.NONE,
    val statusRounds: Int = 0
)

/**
 * 戰鬥屬性變化
 */
data class BattleAttrChange(
    val attrKind: Int,
    val value: Int,
    val isPositive: Boolean
)
