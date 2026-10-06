package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.BattleConstants

/**
 * Offline PvP-bot policy.
 *
 * This is intentionally NOT claimed to be the original DVT server AI.  The client
 * only proves that EHuman.AutomanualPlayer/AutomanualNpc receive server-selected
 * actions.  This policy uses authoritative Skill_C metadata for legality and
 * BattleDamageCalc.isHealSkill() for healing classification.
 */
object PvpBotAiPolicy {
    private const val HEAL_THRESHOLD_PERCENT = 55
    private val FRIENDLY_FIGHT_WAYS = setOf(4, 6, 7, 8, 14, 19)
    /**
     * Live DVT IOS_Skill_C.dat encodes Trị Liệu/Toàn Trị Liệu as fightWay=7.
     * Keep this PvP-bot-only until battle-core heal semantics are audited separately.
     */
    fun isPvpHpHeal(skill: tw.tsm.domain.data.SkillDef): Boolean =
        skill.fightWay == 7

    private val NON_BATTLE_SKILLS = setOf(
        14003, 14004, 14005,
        14009, 14010, 14011,
        14016, 14017, 14018, 14019, 14020,
        14023, 14026, 14027, 14028,
        14032, 14034,
        10020, 11020, 12020, 13019, 14035,
        14038, 14039, 14041, 14043, 14045,
        14047, 14048, 14049, 14050, 14051,
        14052, 14025, 14077
    )

    /**
     * Ordered candidate actions.  BattleInstance still performs the authoritative
     * legality check before accepting one.
     */
    fun candidates(
        fighter: BattleFighter,
        grid: BattleGrid,
        skillResolver: BattleSkillResolver,
    ): List<BattleAction> {
        if (!fighter.isAlive || fighter.isActionBlocked) return emptyList()

        val livingAllies = if (fighter.isPlayerSide) {
            grid.getAlivePlayerSide()
        } else {
            grid.getAliveEnemySide()
        }
        val deadAllies = if (fighter.isPlayerSide) {
            grid.getDeadPlayerSide()
        } else {
            grid.getDeadEnemySide()
        }
        val enemies = if (fighter.isPlayerSide) {
            grid.getAliveEnemySide()
        } else {
            grid.getAlivePlayerSide()
        }
        if (enemies.isEmpty() && deadAllies.isEmpty()) return emptyList()

        val learned = fighter.skills.asSequence()
            .distinct()
            .mapNotNull { id -> skillResolver.getSkillDef(id)?.let { id to it } }
            .filter { (id, def) ->
                id !in NON_BATTLE_SKILLS &&
                    def.fightWay != 0 &&
                    (def.requireSp <= 0 ||
                        (def.requireSp == 1 && fighter.sp > 0) ||
                        (def.requireSp > 1 && fighter.sp >= def.requireSp))
            }
            .sortedWith(
                compareByDescending<Pair<Int, tw.tsm.domain.data.SkillDef>> {
                    fighter.skillLevels[it.first] ?: 1
                }.thenByDescending { it.second.numerical }
                    .thenBy { it.first }
            )
            .toList()

        val result = mutableListOf<BattleAction>()

        // 1) Revive first when Skill_C says fightWay=8 and a corpse exists.
        if (deadAllies.isNotEmpty()) {
            val revive = learned.firstOrNull { (_, def) -> def.fightWay == 8 }
            if (revive != null) {
                val target = deadAllies.minByOrNull { it.maxHp }
                if (target != null) {
                    result += BattleAction(
                        BattleAction.KIND_SKILL,
                        revive.first,
                        target.col,
                        target.row,
                    )
                }
            }
        }

        // 2) Heal a living ally below 55%, based on the same DAT-backed classifier
        // used by BattleSkillResolver.
        val wounded = livingAllies
            .filter { it.maxHp > 0 && it.hp * 100L <= it.maxHp * HEAL_THRESHOLD_PERCENT }
            .minByOrNull { if (it.maxHp <= 0) 100 else (it.hp * 100L / it.maxHp).toInt() }
        if (wounded != null) {
            val heal = learned.firstOrNull { (_, def) ->
                isPvpHpHeal(def)
            }
            if (heal != null) {
                result += BattleAction(
                    BattleAction.KIND_SKILL,
                    heal.first,
                    wounded.col,
                    wounded.row,
                )
            }
        }

        // 3) Offensive learned skills. Friendly utility and revive are excluded.
        val enemyTarget = enemies.minWithOrNull(
            compareBy<BattleFighter> { it.hp }
                .thenBy { it.col }
                .thenBy { it.row }
        )
        if (enemyTarget != null) {
            learned.asSequence()
                .filter { (_, def) -> def.fightWay !in FRIENDLY_FIGHT_WAYS }
                .take(4)
                .forEach { (skillId, _) ->
                    result += BattleAction(
                        BattleAction.KIND_SKILL,
                        skillId,
                        enemyTarget.col,
                        enemyTarget.row,
                    )
                }

            // Always keep ordinary attack as final fallback.
            result += BattleAction(
                BattleAction.KIND_SKILL,
                BattleConstants.HAND_FIGHT_SKILL_ID,
                enemyTarget.col,
                enemyTarget.row,
            )
        }

        return result.distinct()
    }
}
