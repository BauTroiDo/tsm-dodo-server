package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EWar

/**
 * DVT ore NPC encounter pacing.
 *
 * Actual GM battle 29 on 2026-10-09: Khoáng Chì (25032) spawned at 15:58:05,
 * first knockouts at 15:58:28 because all five player-controlled fighters
 * waited for missing C:050 commands until the general 20-second timeout.
 *
 * Keep an initial one-second window for client-selected actions, then let
 * BattleManager.waitForCommands fill only missing actions with its ordinary
 * attack + S:053-005 ACK. Never shorten PvP, mixed NPC, or group battles.
 */
object OreBattlePacingPolicy {
    const val COMMAND_WINDOW_MS = 1_000L

    fun commandWindowMs(warType: Int, playerCount: Int, enemyNames: List<String>): Long {
        val normalWindowMs = BattleConstants.COUNTDOWN_NORMAL * 1_000L
        return if (
            warType == EWar.EVENT &&
            playerCount == 1 &&
            enemyNames.isNotEmpty() &&
            enemyNames.all { it.startsWith("Khoáng ") }
        ) {
            COMMAND_WINDOW_MS
        } else {
            normalWindowMs
        }
    }
}
