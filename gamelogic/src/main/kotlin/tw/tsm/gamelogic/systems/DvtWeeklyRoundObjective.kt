package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.DungeonDef

/**
 * Pure DVT weekly-round requirement decoder.
 *
 * Lua UIDungeon.ENeedKindTag defines:
 * - kind 6 = MaxRound, 回合數(上限): upper bound on battle rounds.
 * - kind 7 = LimitRound, 回合數(下限): lower bound on battle rounds.
 *
 * This object only decodes DungeonData.needPar. It deliberately does not decide
 * the exact pass/fail boundary at battle settlement until that server-side
 * comparison is recovered authoritatively.
 */
object DvtWeeklyRoundObjective {
    data class Objective(
        val maxRound: Int? = null,
        val minRound: Int? = null,
    )

    fun from(dungeon: DungeonDef): Objective {
        if (dungeon.kind !in setOf(DungeonSystem.KIND_WEEKLY, DungeonSystem.KIND_SOUL_WEEKLY)) {
            return Objective()
        }

        val maxRound = dungeon.needPar
            .firstOrNull { it.kind == NEED_MAX_ROUND }
            ?.parameter
            ?.takeIf { it > 0 }

        val minRound = dungeon.needPar
            .firstOrNull { it.kind == NEED_MIN_ROUND }
            ?.parameter
            ?.takeIf { it > 0 }

        return Objective(maxRound = maxRound, minRound = minRound)
    }

    private const val NEED_MAX_ROUND = 6
    private const val NEED_MIN_ROUND = 7
}
