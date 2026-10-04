package tw.tsm.gamelogic.systems

/**
 * Reconstructed Bách Chiến Thiên Quan enemy catalog from the live DVT Npc_C block.
 *
 * This is deliberately data-only and is NOT wired into battle creation yet.
 * Evidence:
 * - stages 1..19 occupy contiguous 10-NPC blocks beginning at 40258;
 * - stage 18/19 names match independent community descriptions;
 * - stage 20 is ten Lv200 Lữ Bố, exactly two of each element 1..5.
 *
 * Exact backend slot order/round-result semantics are still treated as evidence-blocked.
 */
object WeeklyDungeonFormationCatalog {
    const val FIRST_DUNGEON_ID = 18
    const val LAST_DUNGEON_ID = 37

    private const val FIRST_NPC_ID = 40258
    private const val STAGE_SIZE = 10

    private val stage20 = (40448..40455).toList() + listOf(40480, 40481)

    fun npcIdsForDungeon(dungeonId: Int): List<Int>? {
        if (dungeonId !in FIRST_DUNGEON_ID..LAST_DUNGEON_ID) return null
        val stage = dungeonId - FIRST_DUNGEON_ID + 1
        if (stage == 20) return stage20
        val first = FIRST_NPC_ID + (stage - 1) * STAGE_SIZE
        return (first until first + STAGE_SIZE).toList()
    }
}
