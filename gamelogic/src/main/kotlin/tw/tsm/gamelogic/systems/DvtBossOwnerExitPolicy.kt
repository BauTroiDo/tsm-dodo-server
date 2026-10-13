package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.EWar

/**
 * DVT scene 12861 / Eve 6 / Fight 28: the real player's fly-out closes
 * the native fight UI even when automated followers are still standing.
 * Do not let those followers hold the server fight open after its owner leaves.
 */
object DvtBossOwnerExitPolicy {
    fun shouldEndAsDefeat(
        mapId: Int,
        fightId: Int,
        warType: Int,
        playerSessionCount: Int,
        ownerFlyOut: Boolean,
        ownerAlive: Boolean,
    ): Boolean =
        mapId == 12861 &&
            fightId == 28 &&
            warType == EWar.EVENT &&
            playerSessionCount == 1 &&
            ownerFlyOut &&
            !ownerAlive
}
