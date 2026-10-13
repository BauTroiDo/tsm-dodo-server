package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EWar
import tw.tsm.gamelogic.systems.DvtBattleDefeatRecoveryPolicy

/** Do not alter win/other battle pacing while reducing post-defeat conduct latency. */
class DvtHoangPhuTungDefeatExitPacingTest {
    @Test
    fun bossDefeatRemovesUnnecessarySerializedWaitButKeepsOrderedExitPackets() {
        val pacing = DvtBattleDefeatRecoveryPolicy.exitPacing(
            playerWin = false,
            mapId = 12861,
            warType = EWar.EVENT,
            opponentNpcIds = listOf(14671, 17501, 17501, 17501, 17501),
        )
        assertEquals(100L, pacing.startupMs)
        assertEquals(15L, pacing.interFighterMs)
        val fighters = 11
        assertEquals(265L, pacing.startupMs + fighters * pacing.interFighterMs)
    }

    @Test
    fun everyOtherBattleRetainsOriginal500And100MillisecondGaps() {
        val cases = listOf(
            Triple(true, 12861, EWar.EVENT),
            Triple(false, 12862, EWar.EVENT),
            Triple(false, 12861, EWar.PK_MAP_NPC),
        )
        for ((won, map, kind) in cases) {
            val actual = DvtBattleDefeatRecoveryPolicy.exitPacing(
                playerWin = won, mapId = map, warType = kind,
                opponentNpcIds = listOf(14671, 17501),
            )
            assertEquals(500L, actual.startupMs)
            assertEquals(100L, actual.interFighterMs)
        }
        val unrelatedEnemies = DvtBattleDefeatRecoveryPolicy.exitPacing(
            playerWin = false, mapId = 12861, warType = EWar.EVENT,
            opponentNpcIds = listOf(17501, 17501, 17501),
        )
        assertEquals(500L, unrelatedEnemies.startupMs)
        assertEquals(100L, unrelatedEnemies.interFighterMs)
    }
}
