package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EWar
import tw.tsm.gamelogic.battle.OreBattlePacingPolicy

/**
 * Real DVT 1.15 ore event evidence, Oct 9:
 * map 11531, player 31, event battle29, NPC 25032 'Khoáng Chì'.
 * 15:58:05.734 start -> 15:58:28.616 first knockout (~23s).
 * The fallback action is already handled by BattleManager.waitForCommands,
 * so only the ore-event command window should become short.
 */
class OreBattlePacingRegressionTest {
    private val normal = BattleConstants.COUNTDOWN_NORMAL * 1000L

    @Test fun `ore lead gets fast server fallback instead of full 20 second wait`() {
        assertEquals(1000L, OreBattlePacingPolicy.commandWindowMs(
            EWar.EVENT, 1, listOf("Khoáng Chì", "Khoáng Chì", "Khoáng Chì", "Khoáng Chì")))
    }

    @Test fun `other ore kinds get the same fast command window`() {
        assertEquals(1000L, OreBattlePacingPolicy.commandWindowMs(
            EWar.EVENT, 1, listOf("Khoáng Đá", "Khoáng Chì")))
    }

    @Test fun `normal quest battle keeps original timeout`() {
        assertEquals(normal, OreBattlePacingPolicy.commandWindowMs(
            EWar.EVENT, 1, listOf("Trương Phi")))
    }

    @Test fun `mixed ore and non ore enemy encounter keeps manual control`() {
        assertEquals(normal, OreBattlePacingPolicy.commandWindowMs(
            EWar.EVENT, 1, listOf("Khoáng Chì", "Cường Đạo")))
    }

    @Test fun `pvp and multi player battles are unaffected`() {
        assertEquals(normal, OreBattlePacingPolicy.commandWindowMs(
            EWar.PK_PLAYERS, 1, listOf("Khoáng Chì")))
        assertEquals(normal, OreBattlePacingPolicy.commandWindowMs(
            EWar.EVENT, 2, listOf("Khoáng Chì")))
    }

    @Test fun `empty or unlabeled enemy groups cannot enable fast ore mode`() {
        assertEquals(normal, OreBattlePacingPolicy.commandWindowMs(
            EWar.EVENT, 1, emptyList()))
        assertEquals(normal, OreBattlePacingPolicy.commandWindowMs(
            EWar.EVENT, 1, listOf("", "Khoáng Chì")))
    }
}
