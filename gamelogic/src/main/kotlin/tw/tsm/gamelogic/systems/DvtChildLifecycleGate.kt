package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.EveResult

/**
 * Active DVT Eve map 12721 invokes child-lifecycle actions which the current
 * Kotlin server does not implement.  They must NOT be silently skipped:
 * skipping the action after mission 11980 moves the quest to the next stage
 * without creating/assigning the second-generation general.
 *
 * Binary scan of the authoritative client Eve archive found exactly five
 * matching instructions in this scene:
 * - type7/class7/param0/style36/value0 (one);
 * - type10/class7/param1/style22,23,24/value0 (two, one, one).
 *
 * S036-023 in the DVT client only contains a commented-out
 * Se_ChildForm.appear; it does not provide an adoption UI.
 * This is a fail-closed guard, not a fabricated child/adoption implementation.
 */
object DvtChildLifecycleGate {
    const val CHILD_QUEST_SCENE = 12721

    /** Pure cheap filter; avoid reading a mocked/current session mapId for unrelated Eve. */
    fun isCandidateShape(result: EveResult): Boolean {
        if (result.resultClass != 7 || result.resultValue != 0 ||
            result.resultMeanNo != 0
        ) return false
        return (result.resultType == 7 && result.parameter == 0 &&
            result.parameterStyle == 36) ||
            (result.resultType == 10 && result.parameter == 1 &&
                result.parameterStyle in 22..24)
    }

    fun hasCandidateShape(results: List<EveResult>): Boolean =
        results.any(::isCandidateShape)

    fun requiresUnavailableChildAction(mapId: Int, result: EveResult): Boolean =
        mapId == CHILD_QUEST_SCENE && isCandidateShape(result)

    /** Evaluate the WHOLE pending chain before applying even its first mission/item effect. */
    fun firstUnavailable(mapId: Int, pendingResults: List<EveResult>): EveResult? =
        pendingResults.firstOrNull { requiresUnavailableChildAction(mapId, it) }
}
