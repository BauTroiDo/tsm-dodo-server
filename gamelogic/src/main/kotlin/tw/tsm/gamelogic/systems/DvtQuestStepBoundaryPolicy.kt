package tw.tsm.gamelogic.systems

/**
 * Precisely source-backed DVT MarkStep/Eve mission boundaries.
 *
 * Mission 10120: MarkStep stages 1 (Black Mountain battle) and 2 (government
 * officer NPC14216 in scene11301/Eve3). The real NPC turn-in checks stage2.
 * Scene11801 battle/door event results also include ADD_STEP +1; a repeated
 * post-battle continuation from stage2 could otherwise create orphan stage3.
 *
 * Mission 10164: MarkStep stages 1 and 2 (scene12012 altar). Real turn-in
 * NPC20001 (Eve21) checks stage2. Door event19/20 ADD_STEP +1 can repeat,
 * creating stage3 which that NPC cannot turn in.
 *
 * This is NOT a global "max MarkStep" heuristic. Marker missions, dungeon
 * counters and other quests can legitimately have intermediate server-only
 * steps; reject only the two independently verified terminal quest shapes.
 */
object DvtQuestStepBoundaryPolicy {
    private val terminalTurnInMissions = setOf(10120, 10164)

    fun isUnsupportedAddDestination(missionId: Int, newStep: Int): Boolean =
        DvtNoviceStepGapPolicy.isUnsupportedAddDestination(missionId, newStep) ||
            (missionId in terminalTurnInMissions && newStep > 2)
}
