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
        // DVT cls3/pStyle1 adds a follow NPC (persistent pet roster).
        // Other cls3 variants only affect the client/world NPC view.
        if (result.resultClass == 3) return result.parameterStyle == 1
        if (result.resultClass !in mutatingResultClasses) return false
        if (result.resultClass == 7) {
            // DVT class7 can change Moral (pStyle 1..5, parameter 2)
            // even though these pStyle numbers also represent service UI.
            if (EveResultClassPolicy.moralDelta(
                    result.resultType, result.parameter,
                    result.parameterStyle, result.resultValue
                ) != null
            ) return true
            if (EveResultClassPolicy.directPointPurchase(
                    result.parameterStyle, result.parameter,
                    result.resultValue, result.resultMeanNo
                ) != null
            ) return true
            if (EveResultClassPolicy.isRepeatableServicePlayerOperation(
                    result.parameterStyle, result.resultValue
                )
            ) return false
        }
        return true
    }
}
