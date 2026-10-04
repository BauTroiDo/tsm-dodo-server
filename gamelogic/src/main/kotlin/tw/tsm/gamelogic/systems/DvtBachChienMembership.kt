package tw.tsm.gamelogic.systems

/**
 * Reconstructed DVT Bách Chiến Thiên Quan NPC membership, not battle placement.
 *
 * Client IOS_Npc_C.dat contains stages 1..19 as consecutive ten-NPC blocks
 * beginning at 40258. Stage 20 is the five-element Lv200 Lữ Bố set whose
 * fifth-element pair is 40480/40481.
 *
 * This helper deliberately exposes only unordered stage membership semantics:
 * callers must not infer front/back columns or row positions from list order.
 */
object DvtBachChienMembership {
    fun enemyIds(stage: Int): Set<Int>? = when (stage) {
        in 1..19 -> {
            val first = FIRST_NPC_ID + (stage - 1) * NPCS_PER_STAGE
            (first until first + NPCS_PER_STAGE).toSet()
        }
        20 -> STAGE_20
        else -> null
    }

    private const val FIRST_NPC_ID = 40258
    private const val NPCS_PER_STAGE = 10

    private val STAGE_20 = setOf(
        40448, 40449,
        40450, 40451,
        40452, 40453,
        40454, 40455,
        40480, 40481,
    )
}
