package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.EveResult

/**
 * Original DVT scene 12861/Eve6/Fight28 failed battle result contains only
 * a talk (41353) and NPC hide commands (clickIds 9..13).
 * When the client is no longer displaying a valid post-battle dialog,
 * waiting indefinitely for a talk-advance C020-006 leaves player conduct locked.
 *
 * Deliberately require the complete original result shape: no rewards,
 * mission mutation, or other Eve can enter this defeat recovery path.
 */
object DvtBattleDefeatRecoveryPolicy {
    data class ExitPacing(val startupMs: Long, val interFighterMs: Long)

    /** DVT scene12861 Hoang Phu Tung boss loss, and no other battle.
     * FightManager.FightOver already exits client on S011-000; slow serialized
     * S011-001 exits only delay S020-008 that releases event conduct.
     */
    fun exitPacing(
        playerWin: Boolean, mapId: Int, warType: Int,
        opponentNpcIds: Collection<Int>,
    ): ExitPacing = if (!playerWin && mapId == 12861 &&
        warType == tw.tsm.domain.constants.EWar.EVENT && 14671 in opponentNpcIds
    ) {
        ExitPacing(100L, 15L)
    } else {
        ExitPacing(500L, 100L)
    }

    /** Original DVT client: S020-008 endKind 45 stops navigation and raises death UI. */
    const val DEATH_STOP_NAV_END_KIND = 45

    /** Scoped grace for the exact scene that trapped CaoYenDao after a quest loss. */
    fun needsPostDefeatMineGrace(
        mapId: Int, eveNo: Int, fightDataKey: Int, playerWin: Boolean
    ): Boolean = !playerWin && mapId == 12861 && eveNo == 6 && fightDataKey == 28

    fun isCosmeticDefeat(
        mapId: Int,
        eveNo: Int,
        fightDataKey: Int,
        playerWin: Boolean,
        results: List<EveResult>,
    ): Boolean {
        if (playerWin || mapId != 12861 || eveNo != 6 || fightDataKey != 28) return false
        if (results.size != 6) return false
        val first = results.first()
        if (first.resultType != 1 || first.resultClass != 3 ||
            first.parameter != 9 || first.resultMeanNo != 41353
        ) return false
        val hides = results.drop(1)
        return hides.map { it.parameter } == listOf(9, 10, 11, 12, 13) &&
            hides.all {
                it.resultType == 0 &&
                    it.resultClass == 3 &&
                    it.parameterStyle == 3 &&
                    it.resultValue == 2000
            }
    }
}
