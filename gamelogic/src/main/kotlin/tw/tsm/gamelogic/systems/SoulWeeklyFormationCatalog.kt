package tw.tsm.gamelogic.systems

/**
 * Reconstructed current-DVT Crazy/Soul weekly enemy catalog.
 *
 * Evidence is entirely data-side: the dedicated activity-NPC block 40687..40859
 * contains 173 records and its physical Npc_C record order partitions naturally
 * into exactly twenty themed stage groups. Independent public combat descriptions
 * anchor stage8 and stages16..20, while Skill_C confirms their signature skills.
 *
 * This remains DATA ONLY. It is deliberately not wired into BattleManager until
 * multiplayer WeekDungeon creation, slot order, Soul entry rules and round rules
 * have executable regression coverage.
 */
object SoulWeeklyFormationCatalog {
    const val FIRST_DUNGEON_ID = 63
    const val LAST_DUNGEON_ID = 82

    private val stages: Map<Int, List<Int>> = mapOf(
        63 to (40687..40693).toList(),
        64 to (40694..40700).toList(),
        65 to (40701..40707).toList(),
        66 to (40708..40717).toList(),
        67 to (40718..40727).toList(),
        68 to (40728..40737).toList(),
        69 to (40738..40747).toList(),
        // Npc_C record order places 40763 with the six Gongsun NPCs + three supports.
        70 to ((40748..40756).toList() + 40763),
        // The complementary stage9 block resumes after the interleaved record above.
        71 to ((40757..40762).toList() + (40764..40767).toList()),
        72 to (40768..40777).toList(),
        73 to (40778..40781).toList(),
        74 to (40782..40791).toList(),
        75 to (40792..40802).toList(),
        76 to (40803..40812).toList(),
        77 to (40813..40822).toList(),
        78 to (40823..40828).toList(),
        79 to (40829..40835).toList(),
        80 to (40836..40843).toList(),
        81 to (40844..40850).toList(),
        82 to (40851..40859).toList(),
    )

    fun npcIdsForDungeon(dungeonId: Int): List<Int>? = stages[dungeonId]

    fun allNpcIdsInStageOrder(): List<Int> =
        (FIRST_DUNGEON_ID..LAST_DUNGEON_ID).flatMap { stages.getValue(it) }
}
