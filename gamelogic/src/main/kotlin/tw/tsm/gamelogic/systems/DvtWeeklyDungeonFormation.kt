package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.DungeonDef

/**
 * Recovered DVT Bách Chiến Thiên Quan enemy formation.
 *
 * Source evidence is entirely client-side DVT data plus independent stage descriptions:
 * IOS_Npc_C.dat contains a contiguous special-NPC block 40258..40447 in groups of ten.
 * Stages 18/19 independently match their public named enemy rosters exactly. Stage 20
 * is the explicit five-element Lữ Bố set; its fifth-element pair is 40480..40481 after
 * an unrelated Lv30 block, so it must never be inferred as 40456..40457.
 *
 * This covers kind=3 IDs 18..37 only. Soul weekly kind=5 is intentionally excluded
 * until its backend grouping is authoritative.
 */
object DvtWeeklyDungeonFormation {
    private const val FIRST_DUNGEON_ID = 18
    private const val LAST_LINEAR_DUNGEON_ID = 36
    private const val FIRST_NPC_ID = 40258

    private val stage20 = listOf(
        40448, 40449, // fire
        40450, 40451, // earth
        40452, 40453, // water
        40454, 40455, // wind
        40480, 40481, // fifth element
    )

    fun enemyIds(dungeonId: Int): List<Int>? = when {
        dungeonId in FIRST_DUNGEON_ID..LAST_LINEAR_DUNGEON_ID -> {
            val first = FIRST_NPC_ID + (dungeonId - FIRST_DUNGEON_ID) * 10
            (first until first + 10).toList()
        }
        dungeonId == 37 -> stage20
        else -> null
    }

    fun objective(def: DungeonDef): WeeklyDungeonObjective {
        val maxRound = def.needPar.firstOrNull { it.kind == NEED_MAX_ROUND }?.parameter ?: 0
        val surviveRound = def.needPar.firstOrNull { it.kind == NEED_LIMIT_ROUND }?.parameter ?: 0
        return WeeklyDungeonObjective(
            winWithinRounds = maxRound.coerceAtLeast(0),
            surviveRounds = surviveRound.coerceAtLeast(0),
        )
    }

    private const val NEED_MAX_ROUND = 6
    private const val NEED_LIMIT_ROUND = 7
}

data class WeeklyDungeonObjective(
    val winWithinRounds: Int = 0,
    val surviveRounds: Int = 0,
)
