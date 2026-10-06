package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.BattleConstants

/**
 * Offline fallback for normal DVT team dungeons.
 *
 * The DVT client room protocol is authoritative, but no original server-side
 * auto-fill policy is present in the client. Keep this extension deliberately
 * narrow: bots are offered only while exactly one real player occupies a
 * normal multiplayer room. As soon as another real player joins, the room
 * falls back to ordinary multiplayer.
 */
object TeamDungeonBotPolicy {
    const val BOT_FILL_DELAY_MS = 10_000L

    fun visibleBotCount(kind: Int, realCount: Int, roomMaxPlayers: Int): Int {
        if (kind != DungeonSystem.KIND_NORMAL_MULTIPLAYER) return 0
        if (realCount != 1 || roomMaxPlayers <= 1) return 0
        return (roomMaxPlayers - realCount)
            .coerceAtMost(BattleConstants.MAX_ROW - 1)
            .coerceAtLeast(0)
    }
}
