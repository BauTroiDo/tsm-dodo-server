package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.DungeonDef

/**
 * DVT weekly/Soul stage roster plus deterministic offline placement contract.
 *
 * Authoritative membership evidence:
 * - Bách Chiến kind3: dungeon 18..36 map to ten-NPC blocks 40258..40447.
 *   dungeon37 (Ải20) uses the explicit five-element Lữ Bố set.
 * - Soul kind5: dungeon63..82 map exactly to the contiguous 200-NPC block
 *   40687..40886, ten NPCs per stage.
 *
 * The original backend-only WeekDungeon placement file is missing. DVT client
 * battle indexing is GetIdx(col,row)=col*5+row and this server's battle grid is
 * enemy front col0 / enemy back col1. Therefore offline placement keeps roster
 * order deterministically as first five -> col0 rows0..4 and next five ->
 * col1 rows0..4. Do not relabel this reconstructed placement as original data.
 */
object DvtWeeklyDungeonFormation {
    fun enemyIds(dungeonId: Int): List<Int>? = when (dungeonId) {
        in 18..36 -> {
            val first = 40258 + (dungeonId - 18) * 10
            (first until first + 10).toList()
        }
        37 -> listOf(
            40448, 40449, 40450, 40451, 40452,
            40453, 40454, 40455, 40480, 40481,
        )
        in 63..82 -> {
            val first = 40687 + (dungeonId - 63) * 10
            (first until first + 10).toList()
        }
        else -> null
    }

    fun objective(def: DungeonDef): DvtWeeklyRoundObjective.Objective =
        DvtWeeklyRoundObjective.from(def)

    fun stageNumber(dungeonId: Int): Int? = when (dungeonId) {
        in 18..37 -> dungeonId - 17
        in 63..82 -> dungeonId - 62
        else -> null
    }
}
