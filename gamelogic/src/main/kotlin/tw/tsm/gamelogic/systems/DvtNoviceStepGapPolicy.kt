package tw.tsm.gamelogic.systems

/**
 * Active DVT Eve/IOS_MarkStep_C.dat novice tutorial (mission 12280):
 *
 * The first tutorial branch has steps 1..5. Its verified completion is an
 * Eve resultClass2 pStyle3 at scene10851/event4/condition7. A different
 * beginner branch starts mission 12280 at step11 (scene10821/event1) and
 * continues through 15. There is NO MarkStep record or Eve mission-step
 * condition for 6..10 in the active client/server data.
 *
 * Reject only attempted ADD_STEP landing in that unsupported gap. Do not
 * reinterpret it as a completion, jump to step11, award items, or repair
 * existing character rows without checking their actual event history.
 */
object DvtNoviceStepGapPolicy {
    const val MISSION_ID = 12280

    fun isUnsupportedAddDestination(missionId: Int, targetStep: Int): Boolean =
        missionId == MISSION_ID && targetStep in 6..10
}
