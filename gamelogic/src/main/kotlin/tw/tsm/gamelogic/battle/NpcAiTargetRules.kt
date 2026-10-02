package tw.tsm.gamelogic.battle

/**
 * NPC target pools.
 *
 * DVT controls player-side target highlighting. NPC AI itself is server-side, so when
 * Lua does not expose an NPC selector we use PC TSBattleNPC.seekTarget as fallback.
 */
object NpcAiTargetRules {
    private val FRIENDLY_LIVING_FIGHT_WAYS = setOf(4, 6, 7, 14, 19)
    private val ANY_LIVING_FIGHT_WAYS = setOf(5, 18)

    fun candidates(
        npc: BattleFighter,
        grid: BattleGrid,
        fightWay: Int?
    ): List<BattleFighter> {
        if (fightWay == 8) {
            val deadFriendlies =
                if (npc.isPlayerSide) grid.getDeadPlayerSide() else grid.getDeadEnemySide()
            if (deadFriendlies.isNotEmpty()) return deadFriendlies

            // Preserve the existing no-op revive behavior when no corpse exists.
            return if (npc.isPlayerSide) grid.getAlivePlayerSide() else grid.getAliveEnemySide()
        }

        if (fightWay in ANY_LIVING_FIGHT_WAYS) {
            return grid.getAll().filter { it.isAlive }
        }

        if (fightWay in FRIENDLY_LIVING_FIGHT_WAYS) {
            return if (npc.isPlayerSide) grid.getAlivePlayerSide() else grid.getAliveEnemySide()
        }

        return if (npc.isPlayerSide) grid.getAliveEnemySide() else grid.getAlivePlayerSide()
    }
}
