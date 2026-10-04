package tw.tsm.gamelogic.systems

/**
 * High-confidence current-DVT Crazy/Soul weekly enemy blocks for stages 16..20.
 *
 * Kept data-only until multiplayer WeekDungeon battle creation and round semantics
 * have executable regression coverage. These blocks are anchored independently by
 * both live Npc_C/Skill_C data and public combat descriptions.
 */
object SoulWeeklyFormationCatalog {
    fun npcIdsForDungeon(dungeonId: Int): List<Int>? = when (dungeonId) {
        78 -> (40823..40828).toList() // stage16 plague/pig + poison
        79 -> (40829..40835).toList() // stage17 decay/skeleton + Xuanwu
        80 -> (40836..40843).toList() // stage18 violent/wolf + Crazy Dragon
        81 -> (40844..40850).toList() // stage19 cunning + Crazy Dragon
        82 -> (40851..40859).toList() // stage20 BDY/interdimensional bosses
        else -> null
    }
}
