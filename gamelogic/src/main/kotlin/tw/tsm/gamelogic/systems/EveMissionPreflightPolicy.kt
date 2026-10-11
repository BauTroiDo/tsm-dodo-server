package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.EveResult

/** Preflight the COMPLETE ordered quest-step segment before mutating any persistent state.
 * Prevents a stale Eve choice or resumed battle from sending S024 decrement for a mission
 * that the server does not have, or applying following quest/reward results after an error.
 */
object EveMissionPreflightPolicy {
    enum class Failure { MISSING_MISSION, INVALID_STEP, UNDEFINED_DVT_TUTORIAL_STEP }

    data class Rejection(val missionId: Int, val reason: Failure)

    fun validate(
        results: List<EveResult>,
        missionSteps: Map<Int, Int>,
    ): Rejection? {
        val simulated = missionSteps.toMutableMap()
        for (result in results) {
            if (result.resultType !in setOf(0, 7) || result.resultClass != 2) continue
            val missionId = result.parameter
            val current = simulated[missionId] ?: 0
            if (missionId !in 1..65535 || current !in 0..255) {
                return Rejection(missionId, Failure.INVALID_STEP)
            }
            when (result.parameterStyle) {
                1 -> {
                    if (result.resultValue !in 0..255 ||
                        current.toLong() + result.resultValue > 255
                    ) return Rejection(missionId, Failure.INVALID_STEP)
                    val nextStep = current + result.resultValue
                    // Reject the entire Eve result chain before item, gold,
                    // follower or quest mutation if a stale novice ADD would
                    // persist a tutorial stage absent from active DVT data.
                    if (DvtNoviceStepGapPolicy.isUnsupportedAddDestination(missionId, nextStep)) {
                        return Rejection(missionId, Failure.UNDEFINED_DVT_TUTORIAL_STEP)
                    }
                    simulated[missionId] = nextStep
                }
                2 -> {
                    if (result.resultValue !in 0..255) {
                        return Rejection(missionId, Failure.INVALID_STEP)
                    }
                    if (current == 0 && result.resultValue > 0) {
                        return Rejection(missionId, Failure.MISSING_MISSION)
                    }
                    simulated[missionId] = (current - result.resultValue).coerceAtLeast(0)
                }
                3, 5 -> simulated.remove(missionId)
                // pStyle4 sets a separate bit/flag and does not change current step.
            }
        }
        return null
    }
}
