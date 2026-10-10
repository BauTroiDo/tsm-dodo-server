package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.EveResult

/**
 * Data-driven DVT PC Eve marker encoded as a nominal mission-2 ADD_STEP.
 *
 * Eve PC uses e.g. resultClass=2, parameter=2, pStyle=1, value=612130
 * in mission 12130's chain. Value = markerCode*100000 + realMissionId.
 *
 * Native Lua EventHandler[0] adds the value only if it fits MaxMissionQuant
 * (255); these markers do NOT change mission 2. The server must not abort
 * the surrounding legitimate chain nor send a truncated S024 mission step.
 */
object EvePackedMissionHintPolicy {
    private const val PACK_BASE = 100_000

    fun isNonMutatingHint(result: EveResult, knownMissionIds: Set<Int>): Boolean {
        if (result.resultType !in setOf(0, 7) ||
            result.resultClass != 2 ||
            result.parameter != 2 ||
            result.parameterStyle != 1 ||
            result.resultValue <= 255
        ) return false
        val category = result.resultValue / PACK_BASE
        val targetMission = result.resultValue % PACK_BASE
        return category in 1..99 && targetMission in knownMissionIds
    }
}
