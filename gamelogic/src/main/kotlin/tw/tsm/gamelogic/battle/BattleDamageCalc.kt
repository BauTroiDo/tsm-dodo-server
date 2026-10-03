package tw.tsm.gamelogic.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttackResult
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EBeHitAction
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.enums.BattleStatus
import kotlin.math.max
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Máy tính sát thương chiến đấu — đối chiếu `BattleAbstract.cs` của tskeropc, CalcDmg + execute()
 *
 * Chịu trách nhiệm: phán đoán trúng, tính sát thương cơ bản, giảm sát thương phòng thủ, hiệu chỉnh nhân, phán đoán chí mạng, gây trạng thái bất thường.
 *
 * Pipeline sát thương:
 * 1. CalcDmg (phía người tấn công) → sát thương cơ bản (bao gồm cộng cấp độ)
 * 2. execute (phía mục tiêu) → trừ đi cấp độ + phòng thủ của mục tiêu
 * 3. Hiệu chỉnh nhân → nguyên tố/chuyển sinh/ngẫu nhiên/chí mạng/PvP/đóng băng/phòng thủ
 */
object BattleDamageCalc {

    /** Giới hạn trên sát thương cơ bản (KERO: if (dmgbase > 50000) dmgbase = 50000)*/
    private const val MAX_BASE_DAMAGE = 50000

    /**
     * Tính kết quả một lần tấn công
     *
     * @param attacker người tấn công
     * @param target mục tiêu
     * @param skill kỹ năng sử dụng (null=đánh thường)
     * @param terrain địa chất (hiện chưa dùng, dự phòng)
     * @return kết quả tấn công
     */
    fun calculateAttack(
        attacker: BattleFighter,
        target: BattleFighter,
        skill: SkillDef?,
        terrain: Int = 0,
        skillId: Int = 0,
        skillLevel: Int = 1,
        damageDivisor: Int = 1
    ): AttackCalcResult {
        // Ordinary HP-damage hit/miss: giữ baseline PC theo chênh cấp,
        // rồi áp DVT Mobile Hit/Dodge như phần trăm bù trừ. Không dùng AGI.
        val missChance = calculateMissChance(
            attackerLevel = attacker.level,
            targetLevel = target.level,
            hit = attacker.hit,
            dodge = target.dodge
        )
        if (Random.nextDouble() * 100.0 < missChance) {
            return AttackCalcResult(
                attackResult = EAttackResult.MISS,
                beHitAction = EBeHitAction.DODGE,
                damage = 0,
                attrChanges = emptyList()
            )
        }

        // Phase 1: sát thương cơ bản (tương đương CalcDmg)
        val baseDamage = calculateBaseDamage(attacker, skill, skillLevel)
        // PC makeExecutionPacket chia c.dmg theo nb_target trước khi getSkillEffect
        // thực hiện trừ level/DEF, element và random. DVT fightArea chính là nb_target shape.
        val areaScaledBaseDamage = applyMultiTargetDivision(baseDamage, damageDivisor)

        // D6: PC applies classic buff/debuff/defend modifiers to c.dmg BEFORE
        // target level/DEF and element. Use exact source skillId stored in groupedStatuses;
        // do not infer a multiplier from a broad status enum such as ICE_BOUND.
        val statusScaledBaseDamage = applyPreDefenseStatusModifiers(
            areaScaledBaseDamage,
            attacker,
            target
        )

        // Phase 2: giảm sát thương phòng thủ (KERO execute: dmg - dest.getLvl() - dest.getDef())
        val reducedDmg = (statusScaledBaseDamage - target.level - target.def).toDouble()

        // Phase 3: hệ số hiệu chỉnh nhân
        val skillElement = skill?.element ?: attacker.element
        val elementMultiplier = ElementTable.getMultiplier(skillElement, target.element)

        val rbDiff = maxOf(0, attacker.turn - target.turn)
        val rebornMultiplier = 1.0 + 0.1 * rbDiff

        // PC baseline: sau element + reborn mới ép Int rồi cộng dao động {-2,-1,0,+1}.
        // Không dùng ±10%; cũng không tự suy crit từ AGI. Crit Mobile thật xử lý ở D4
        // bằng EAttribute.Crit / CritDamage khi runtime fighter có đủ thuộc tính.
        val variance = Random.nextInt(-2, 2)
        val critChance = attacker.crit.coerceIn(0, 100)
        val isCritical = critChance > 0 && Random.nextInt(100) < critChance
        val critMultiplier = if (isCritical) calculateCritMultiplier(attacker.critDamage) else 1.0
        val rawFinalDamage = calculateFinalDamage(
            reducedDmg = reducedDmg * critMultiplier,
            elementMultiplier = elementMultiplier,
            rebornMultiplier = rebornMultiplier,
            variance = variance,
            pvpOrPet = target.isHumanPlayer || target.kind == EHuman.FOLLOW_NPC,
            defending = target.isDefending
        )

        // DVT FightManager: EAttackResult.Thounder=2 is the Tian-Lei result channel,
        // not the generic critical-hit marker. Critical damage stays a normal HIT.
        // A future evidence-backed Tian-Lei self-hit producer may emit THUNDER explicitly.
        val attackResult = EAttackResult.HIT
        val finalDamage = NpcPassiveCombatRules.applyFinalIncomingDamage(
            damage = rawFinalDamage,
            target = target,
            skill = skill,
            attackResult = attackResult
        )

        logger.debug { "${attacker.name}(lv=${attacker.level},atk=${attacker.atk},int=${attacker.intelligence}) → " +
            "${target.name}(lv=${target.level},def=${target.def}): base=$baseDamage areaBase=$areaScaledBaseDamage " +
            "statusBase=$statusScaledBaseDamage div=$damageDivisor reduced=${reducedDmg.toInt()} " +
            "elem=${"%.2f".format(elementMultiplier)} " +
            "miss=${"%.1f".format(missChance)}% hit=${attacker.hit}% dodge=${target.dodge}% " +
            "crit=$isCritical critRate=${attacker.crit}% critMul=${"%.2f".format(critMultiplier)} " +
            "variance=$variance rawFinal=$rawFinalDamage final=$finalDamage passive=${target.passiveSkillId}" }

        val beHitAction = EBeHitAction.HIT

        // Danh sách biến đổi thuộc tính (HP giảm)
        val attrChanges = mutableListOf(
            BattleAttrChange(EAttribute.HP, finalDamage, false)
        )

        // Gây trạng thái bất thường. DVT SkillData.round is an encoded duration:
        // 0=none, 1/2/3=fixed rounds, 4=continuous three rounds (not four).
        var inflictedStatus = BattleStatus.NONE
        var statusRounds = 0
        val encodedStatusRounds = skill?.let { statusDurationFromSkillRound(it.round) } ?: 0
        if (skill != null && encodedStatusRounds > 0 && statusFromSkillId(skillId) != BattleStatus.NONE) {
            val statusChance = 50 + (attacker.intelligence - target.intelligence) / 2
            if (Random.nextInt(100) < statusChance.coerceIn(10, 90)) {
                // Client FightRoleController resolves the concrete status from skillId
                // inside a status group; hitStatus is not a concrete BattleStatus enum.
                inflictedStatus = statusFromSkillId(skillId)
                statusRounds = if (inflictedStatus != BattleStatus.NONE) encodedStatusRounds else 0
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
     * Enemy status-only execution.
     *
     * SkillData.attribute identifies these as a status attribute, so they must not
     * manufacture HP damage. Keep the server's existing hit/status chance policy
     * here until Lua/data provides a more authoritative formula.
     */
    fun calculateStatusOnly(
        attacker: BattleFighter,
        target: BattleFighter,
        skill: SkillDef,
        skillId: Int
    ): AttackCalcResult {
        val missChance = statusOnlyMissChance(attacker.level, target.level, skill.fightWay)
        if (Random.nextInt(100).toDouble() < missChance) {
            return AttackCalcResult(
                attackResult = EAttackResult.MISS,
                beHitAction = EBeHitAction.DODGE,
                damage = 0,
                attrChanges = emptyList()
            )
        }

        val concreteStatus = statusFromSkillId(skillId)
        val encodedStatusRounds = statusDurationFromSkillRound(skill.round)
        return AttackCalcResult(
            attackResult = EAttackResult.HIT,
            beHitAction = EBeHitAction.NONE,
            damage = 0,
            attrChanges = emptyList(),
            inflictedStatus = if (encodedStatusRounds > 0) concreteStatus else BattleStatus.NONE,
            statusRounds = if (concreteStatus != BattleStatus.NONE) encodedStatusRounds else 0
        )
    }

    /**
     * DVT SkillData.round encoding from live Lua:
     * 0 = no duration, 1/2/3 = exact rounds, 4 = continuous three rounds.
     */
    fun statusDurationFromSkillRound(rawRound: Int): Int = when (rawRound) {
        1, 2, 3 -> rawRound
        4 -> 3
        else -> 0
    }

    /**
     * Concrete status mapping proven by FightRoleController.lua.
     * Keep this deliberately limited to server-modeled statuses.
     */
    fun statusFromSkillId(skillId: Int): BattleStatus = when {
        skillId in ICE_BOUND_SKILLS -> BattleStatus.ICE_BOUND
        skillId in WHIRL_WIND_SKILLS -> BattleStatus.WHIRL_WIND
        skillId in CLUMP_SKILLS -> BattleStatus.CLUMP
        skillId in TRANCE_SKILLS -> BattleStatus.TRANCE
        skillId in DIZZY_SKILLS -> BattleStatus.DIZZY_ATK
        skillId in SHRINK_SKILLS -> BattleStatus.SHRINK
        skillId in POISON_SKILLS -> BattleStatus.POISON
        skillId in CHAOS_SKILLS -> BattleStatus.CHAOS
        skillId in TARDY_SKILLS -> BattleStatus.TARDY
        skillId in LANDSLIP_SKILLS -> BattleStatus.LANDSLIP
        skillId in TAUNT_SKILLS -> BattleStatus.TAUNT
        skillId in TRAITOR_SKILLS -> BattleStatus.TRAITOR
        skillId in STOOL_SKILLS -> BattleStatus.STOOL
        skillId in THUNDER_FIRE_SKILLS -> BattleStatus.THUNDER_FIRE
        skillId in HEALTH_DRAWER_SKILLS -> BattleStatus.HEALTH_DRAWER
        skillId in EW_COMMON_FAINT_SKILLS -> BattleStatus.EW_COMMON_FAINT
        skillId in WEAK_PERCENT_SKILLS -> BattleStatus.WEAK_PERCENT
        skillId in VARY_BOUNDARY_SKILLS -> BattleStatus.BOUNDARY
        skillId in VARY_ICE_WALL_SKILLS -> BattleStatus.ICE_WALL
        skillId in VARY_LIMPID_SKILLS -> BattleStatus.LIMPID
        skillId in VARY_ENLARGE_SKILLS -> BattleStatus.ENLARGE
        skillId in VARY_AVOID_SKILLS -> BattleStatus.AVOID
        skillId in VARY_MIRROR_SKILLS -> BattleStatus.MIRROR
        skillId in VARY_SOUL_MIRROR_SKILLS -> BattleStatus.SOUL_MIRROR
        skillId in VARY_GOD_SKILLS -> BattleStatus.GOD
        skillId in VARY_SAME_HEART_SKILLS -> BattleStatus.SAME_HEART
        skillId in VARY_INSPIRE_SKILLS -> BattleStatus.INSPIRE
        skillId in VARY_FIRE_AMULET_SKILLS -> BattleStatus.FIRE_AMULET
        skillId in VARY_TRANSFER_ATTACK_SKILLS -> BattleStatus.TRANSFER_ATTACK
        skillId in VARY_INVISIBLE_SKILLS -> BattleStatus.INVISIBLE
        skillId in VARY_GOLD_SHIELD_SKILLS -> BattleStatus.GOLD_SHIELD
        skillId in VARY_DOLPHIN_GUARD_SKILLS -> BattleStatus.DOLPHIN_GUARD
        skillId in VARY_IMMUNITY_SHIELD_SKILLS -> BattleStatus.IMMUNITY_SHIELD
        skillId in VARY_ICE_CRYSTALS_SKILLS -> BattleStatus.ICE_CRYSTALS
        skillId in VARY_DRAGON_TRANSFER_SKILLS -> BattleStatus.DRAGON_TRANSFER
        skillId in SPEC_PROTECT_SKILLS -> BattleStatus.PROTECT
        skillId in SPEC_SPELL_SKILLS -> BattleStatus.SPELL
        skillId in SPEC_MAGIC_PROTECT_SKILLS -> BattleStatus.MAGIC_PROTECT
        skillId in STRONG_BRIGHT_MIRROR_SKILLS -> BattleStatus.BRIGHT_MIRROR
        skillId in STRONG_WAR_STEP_SKILLS -> BattleStatus.WAR_STEP
        skillId in STRONG_WILD_FIGHT_SOUL_SKILLS -> BattleStatus.WILD_FIGHT_SOUL
        skillId in STRONG_ANGRY_MIND_SKILLS -> BattleStatus.ANGRY_MIND
        skillId in STRONG_MAGICAL_SHIELD_SKILLS -> BattleStatus.MAGICAL_SHIELD
        skillId in STRONG_CRAZY_SOUL_SKILLS -> BattleStatus.CRAZY_SOUL
        skillId in STRONG_PITY_SOLDIER_SKILLS -> BattleStatus.PITY_SOLDIER
        else -> BattleStatus.NONE
    }

    /**
     * Tính hiệu ứng kỹ năng hồi phục
     *
     * Lượng hồi phục = (trí lực × 0.5 + cấp độ) × giá trị kỹ năng / 100
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

    // ── Hàm tính toán riêng tư ──

    /**
     * Tính sát thương cơ bản theo CalcDmg của BattleAbstract.cs.
     *
     * Mapping nhị phân SkillData PC -> Skill_C Mobile đã đối chiếu:
     * grade -> level, unk17 -> atkKind, unk20 -> learnLimit.
     * numerical là giá trị thuộc tính của skill, không phải hệ số damage chung.
     */
    private fun calculateBaseDamage(attacker: BattleFighter, skill: SkillDef?, skillLevel: Int): Int {
        val lv = skillLevel.coerceIn(1, 10)
        val skillGrade = skill?.level ?: 1
        val skillRb = skill?.learnLimit ?: 0

        val dmgBase = when {
            skill == null -> (attacker.atk * 1.6).toInt()
            skill.atkKind == 1 -> (attacker.atk * 1.6 + attacker.intelligence * 0.4).toInt()
            else -> (attacker.intelligence * 2.0).toInt()
        }

        val skillBaseDamage = if (skill == null) {
            attacker.level * 2
        } else {
            (skillGrade * 15.0 * skillRb * (1.0 + 0.2 * lv)).toInt()
        }

        val growthMultiplier = 1.0 + (skillGrade * 0.1 * skillRb) + (lv * 0.02)
        val baseDamage = ((dmgBase + attacker.level) * growthMultiplier).toInt() + skillBaseDamage

        return baseDamage.coerceAtMost(MAX_BASE_DAMAGE)
    }

    /**
     * Chia base damage theo phạm vi trước DEF/element, đúng vị trí PC makeExecutionPacket.
     */
    private fun applyMultiTargetDivision(baseDamage: Int, damageDivisor: Int): Int {
        return baseDamage / damageDivisor.coerceAtLeast(1)
    }

    /**
     * Modifier cổ điển có bằng chứng DVT + PC, áp theo đúng thứ tự PC calculateHit.
     * Mỗi phép nhân ép Int ngay sau bước đó để giữ semantics cast ushort/int của source gốc.
     *
     * Không áp theo BattleStatus chung: cùng ICE_BOUND nhưng chỉ skill 20026 có ×0.75
     * trong source PC; 11014 Băng Phong thường không có multiplier damage.
     */
    private fun applyPreDefenseStatusModifiers(
        baseDamage: Int,
        attacker: BattleFighter,
        target: BattleFighter
    ): Int {
        var damage = baseDamage
        val attackerSkillIds = attacker.groupedStatuses.values.map { it.skillId }.toSet()
        val targetSkillIds = target.groupedStatuses.values.map { it.skillId }.toSet()

        // Target Vary buff: PC uses if/else-if.
        damage = when {
            11002 in targetSkillIds -> (damage * 0.75).toInt() // Băng tường
            10031 in targetSkillIds -> (damage * 0.50).toInt() // Chung Trạo
            else -> damage
        }

        // Tam Nhân Băng Phong is a special defensive freeze in PC.
        if (20026 in targetSkillIds) {
            damage = (damage * 0.75).toInt()
        }

        // Enlarge and Shrink are mutually prioritized in PC (buff before debuff).
        damage = when {
            13012 in attackerSkillIds -> (damage * 1.25).toInt()
            13011 in attackerSkillIds -> (damage * 0.75).toInt()
            else -> damage
        }

        // Strong/aura group: Cuồng Nộ vs Bá Ý.
        damage = when {
            12025 in attackerSkillIds -> (damage * 1.25).toInt()
            14040 in attackerSkillIds -> (damage * 1.50).toInt()
            else -> damage
        }

        // PC defend has a pre-DEF ×0.75 stage; final /2 remains in calculateFinalDamage.
        if (target.isDefending) {
            damage = (damage * 0.75).toInt()
        }

        return damage.coerceAtLeast(0)
    }

    /**
     * Áp dụng phần hậu phòng thủ theo baseline PC gốc.
     *
     * Thứ tự quan trọng: element/reborn -> Int -> random {-2,-1,0,+1} -> PvP/pet /2
     * -> clamp -> defend /2. Các buff/debuff gây damage đã được xử lý ở
     * applyPreDefenseStatusModifiers trước khi trừ DEF.
     */
    private fun calculateFinalDamage(
        reducedDmg: Double,
        elementMultiplier: Double,
        rebornMultiplier: Double,
        variance: Int,
        pvpOrPet: Boolean,
        defending: Boolean
    ): Int {
        var damage = (reducedDmg * elementMultiplier * rebornMultiplier).toInt() + variance

        if (pvpOrPet) {
            damage /= 2
        }
        if (damage <= 0) {
            damage = 1
        }

        if (defending) {
            damage = if (elementMultiplier == 0.8) 1 else max(1, damage / 2)
        }

        return max(1, damage)
    }

    /**
     * Ordinary hit/miss: PC baseline plus DVT Mobile percentage-point Hit/Dodge.
     * PC BattleAbstract.calculateHit uses miss threshold:
     *   (targetLv - attackerLv + 5) * 0.2
     * DVT exposes Hit/Dodge as independent 0..30% stats; when both are zero
     * this therefore collapses exactly to the PC baseline.
     */
    private fun calculateMissChance(
        attackerLevel: Int,
        targetLevel: Int,
        hit: Int,
        dodge: Int
    ): Double {
        val pcBaseline = (targetLevel - attackerLevel + 5) * 0.2
        val hitPct = hit.coerceIn(0, 30)
        val dodgePct = dodge.coerceIn(0, 30)
        return (pcBaseline + dodgePct - hitPct).coerceIn(0.0, 100.0)
    }

    /** DVT UIStatusDetail: CritDamage has a fixed 50% base plus this attribute. */
    private fun calculateCritMultiplier(critDamage: Int): Double {
        val extraPct = critDamage.coerceAtLeast(0)
        return 1.0 + (50 + extraPct) / 100.0
    }

    /**
     * PC fallback for the DVT-aligned hostile status-only families:
     * skill_type/fightWay 3 (disable) adds 20 miss points; 15 (debuff) adds 15.
     * DVT Lua/data do not expose the server RNG formula, but current Skill_C preserves
     * these exact fightWay categories, so keep the fallback strictly scoped to 3/15.
     */
    fun statusOnlyMissChance(attackerLevel: Int, targetLevel: Int, fightWay: Int): Double {
        val levelPart = max((targetLevel - attackerLevel + 5) * 0.2, 0.0)
        val categoryPenalty = when (fightWay) {
            3 -> 20.0
            15 -> 15.0
            else -> return 100.0
        }
        return (levelPart + categoryPenalty).coerceIn(0.0, 100.0)
    }

    /**
     * Xác định có phải kỹ năng hồi phục không (loại hồi phục hệ thuỷ)
     */
    fun isHealSkill(skill: SkillDef): Boolean {
        return skill.element == 2 && skill.attribute == EAttribute.HP
    }
    private val ICE_BOUND_SKILLS = setOf(11014, 20026, 11027, 11039, 11028, 22016, 22017, 22044)
    private val WHIRL_WIND_SKILLS = setOf(13002, 13015, 13016, 13017, 13018, 13046, 13050, 13052, 20027, 20048, 21042, 23036, 46002)
    private val CLUMP_SKILLS = setOf(10004, 20025, 20049, 10033)
    private val TRANCE_SKILLS = setOf(14008, 23028, 23039)
    private val DIZZY_SKILLS = setOf(13007, 20016, 20020, 13029, 23015, 23027, 13041, 21033, 13044, 22041, 22043, 22052, 10044)
    private val SHRINK_SKILLS = setOf(13011)
    private val POISON_SKILLS = setOf(14015, 20023, 21008, 20047, 23006, 23011, 23012, 23013, 23014, 21032, 21039, 22042)
    private val CHAOS_SKILLS = setOf(11046, 14021, 20014, 20020, 20055, 23009, 23007, 23022, 20051, 14065, 21041, 20058, 41002)
    private val TARDY_SKILLS = setOf(10016, 10017, 10018, 10019, 11045, 14097, 21001, 21028, 23027)
    private val LANDSLIP_SKILLS = setOf(10025)
private val TAUNT_SKILLS = setOf(10042, 10045, 22030)
private val TRAITOR_SKILLS = setOf(14020)
private val STOOL_SKILLS = setOf(14029)
private val THUNDER_FIRE_SKILLS = setOf(12030,12031,12032,21014,22021,12016,12017,12018,12019,21035,21043,22026,13048,21044,41001,41004,23019)
private val HEALTH_DRAWER_SKILLS = setOf(13030,12052,45002)
private val EW_COMMON_FAINT_SKILLS = setOf(11039,13040,14063,14064,14065)
// 11039/13040/14063/14064 are captured by the earlier EWCommonFaint elseif in client Lua.
private val WEAK_PERCENT_SKILLS = setOf(12046,13041,12059)
private val VARY_BOUNDARY_SKILLS = setOf(10010,10041)
private val VARY_ICE_WALL_SKILLS = setOf(11002,11042)
private val VARY_LIMPID_SKILLS = setOf(13005,13032,13042,13049)
private val VARY_ENLARGE_SKILLS = setOf(13012)
private val VARY_AVOID_SKILLS = setOf(13003)
private val VARY_MIRROR_SKILLS = setOf(10015,10039,10038)
private val VARY_SOUL_MIRROR_SKILLS = setOf(10026,10040,11049,22024)
private val VARY_GOD_SKILLS = setOf(20005)
private val VARY_SAME_HEART_SKILLS = setOf(14012)
private val VARY_INSPIRE_SKILLS = setOf(14013)
private val VARY_FIRE_AMULET_SKILLS = setOf(12024)
private val VARY_TRANSFER_ATTACK_SKILLS = setOf(13021)
private val VARY_INVISIBLE_SKILLS = setOf(13025)
private val VARY_GOLD_SHIELD_SKILLS = setOf(10031)
private val VARY_DOLPHIN_GUARD_SKILLS = setOf(21037)
private val VARY_IMMUNITY_SHIELD_SKILLS = setOf(22031,22046)
private val VARY_ICE_CRYSTALS_SKILLS = setOf(22044)
private val VARY_DRAGON_TRANSFER_SKILLS = setOf(13051)
private val SPEC_PROTECT_SKILLS = setOf(20006)
private val SPEC_SPELL_SKILLS = setOf(20008)
private val SPEC_MAGIC_PROTECT_SKILLS = setOf(14056)
private val STRONG_BRIGHT_MIRROR_SKILLS = setOf(11024,11042,23035)
private val STRONG_WAR_STEP_SKILLS = setOf(11032,22006,14046)
private val STRONG_WILD_FIGHT_SOUL_SKILLS = setOf(11049,12025,12045,13020)
private val STRONG_ANGRY_MIND_SKILLS = setOf(14040,23008)
private val STRONG_MAGICAL_SHIELD_SKILLS = setOf(14044)
private val STRONG_CRAZY_SOUL_SKILLS = setOf(14053)
private val STRONG_PITY_SOLDIER_SKILLS = setOf(21026)

}

/**
 * Kết quả tính một lần tấn công
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
 * Biến đổi thuộc tính chiến đấu
 */
data class BattleAttrChange(
    val attrKind: Int,
    val value: Int,
    val isPositive: Boolean
)
