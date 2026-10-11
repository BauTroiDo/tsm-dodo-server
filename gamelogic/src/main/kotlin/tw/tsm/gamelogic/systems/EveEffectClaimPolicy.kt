package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.EveResult

/**
 * Explicitly identify Eve server effects that may mutate persistent player state.
 *
 * Talk (1), choice (6), cutscene (5), and generic warp (2) must not reserve
 * reward claims. A battle outcome may auto-capture a warrior, and is fenced
 * separately at that mutation site by the flow controller.
 *
 * This policy is intentionally independent of EventSession.hasStateChangingResults():
 * that broader method also counts resultClass=0 and non-reward NPC animation.
 */
object EveEffectClaimPolicy {
    private val mutatingResultClasses = setOf(1, 2, 4, 5, 7, 8, 14)

    fun requiresReservation(result: EveResult): Boolean {
        if (result.resultType != 0 && result.resultType != 7) return false
        if (result.resultClass !in mutatingResultClasses) return false
        if (result.resultClass == 7 &&
            EveResultClassPolicy.isRepeatableServicePlayerOperation(
                result.parameterStyle, result.resultValue
            )
        ) return false
        return true
    }
}
