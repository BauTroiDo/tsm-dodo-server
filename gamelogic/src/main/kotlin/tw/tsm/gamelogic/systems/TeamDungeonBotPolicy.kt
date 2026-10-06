package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.BattleConstants

data class TeamDungeonBotProfile(
    val name: String,
    val hairOffset: Int,
    val color1Xor: Int,
    val color2Xor: Int,
    val petNpcId: Int,
)

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
    const val BOT_FILL_DELAY_MS = 5_000L

    private val PROFILES = listOf(
        TeamDungeonBotProfile("Huyết Kiếm", 1, 0x00060C18, 0x00120804, 40428),
        TeamDungeonBotProfile("Phong Vân", 2, 0x00100408, 0x00081210, 40429),
        TeamDungeonBotProfile("Băng Tâm", 3, 0x000C1404, 0x00100414, 40430),
        TeamDungeonBotProfile("Thiên Long", 4, 0x0014080C, 0x000C1018, 40431),
    )

    fun visibleBotCount(kind: Int, realCount: Int, roomMaxPlayers: Int): Int {
        if (kind != DungeonSystem.KIND_NORMAL_MULTIPLAYER) return 0
        if (realCount != 1 || roomMaxPlayers <= 1) return 0
        return (roomMaxPlayers - realCount)
            .coerceAtMost(BattleConstants.MAX_ROW - 1)
            .coerceAtLeast(0)
    }

    fun profile(index: Int): TeamDungeonBotProfile = PROFILES[index.mod(PROFILES.size)]

    fun assistMatchesInstance(assistInstanceId: Int, currentInstanceId: Int): Boolean =
        assistInstanceId > 0 && assistInstanceId == currentInstanceId
}
