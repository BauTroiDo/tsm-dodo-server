package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EWar
import tw.tsm.gamelogic.systems.DvtBossOwnerExitPolicy

/** Owner flyout is terminal only for the original DVT Eve6/Fight28 loss. */
class DvtBossOwnerExitPolicyTest {
    private fun ends(
        mapId: Int = 12861,
        fightId: Int = 28,
        warType: Int = EWar.EVENT,
        playerSessionCount: Int = 1,
        ownerFlyOut: Boolean = true,
        ownerAlive: Boolean = false,
    ) = DvtBossOwnerExitPolicy.shouldEndAsDefeat(
        mapId, fightId, warType, playerSessionCount, ownerFlyOut, ownerAlive
    )

    @Test
    fun realOwnerFlyoutFinishesLossEvenWithSurvivingFollowers() {
        // Other allies are deliberately not part of this owner-only rule.
        assertTrue(ends())
    }

    @Test
    fun ordinaryDeathWithoutFlyoutMustNotBeForcedToEnd() {
        assertFalse(ends(ownerFlyOut = false))
        assertFalse(ends(ownerFlyOut = false, ownerAlive = true))
        assertFalse(ends(ownerFlyOut = true, ownerAlive = true))
    }

    @Test
    fun noEffectOnOtherBattlesMapsAndMultiPlayer() {
        assertFalse(ends(mapId = 12862))
        assertFalse(ends(fightId = 29))
        assertFalse(ends(fightId = 6))
        assertFalse(ends(warType = EWar.EVENT + 1))
        assertFalse(ends(playerSessionCount = 2))
        assertFalse(ends(playerSessionCount = 0))
    }
}
