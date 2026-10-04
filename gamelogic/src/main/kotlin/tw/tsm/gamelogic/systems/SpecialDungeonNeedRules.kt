package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.DungeonNeedDef

/**
 * Classifies DungeonData.needPar without inventing backend semantics.
 *
 * UIDungeon.GetNeedParStr proves the need kind meanings directly. The parameter
 * is the condition value itself; it is NOT a DungeonCriteriaData id.
 */
object SpecialDungeonNeedRules {
    enum class Phase {
        NONE,
        ENTRY,
        BATTLE_ROUND,
        SOUL_ENTRY,
        UNKNOWN,
    }

    fun phase(need: DungeonNeedDef): Phase = when (need.kind) {
        0 -> Phase.NONE
        in 1..5 -> Phase.ENTRY
        6, 7 -> Phase.BATTLE_ROUND
        8, 9, 10 -> Phase.SOUL_ENTRY
        else -> Phase.UNKNOWN
    }

    fun isServerEvaluableWithoutSoul(need: DungeonNeedDef): Boolean =
        phase(need) in setOf(Phase.NONE, Phase.ENTRY, Phase.BATTLE_ROUND)
}
