package tw.tsm.gamelogic.battle

/**
 * Server-side NPC skill normalization.
 *
 * DVT Npc_C stores the four elemental summon base IDs on NPCs, while the client
 * contains distinct "large summon" +3 variants. PC TSBattleNPC.seekTarget maps
 * these four base IDs to the large variant before queuing the NPC command.
 */
object NpcAiSkillRules {
    private val LARGE_SUMMON_BY_BASE = mapOf(
        10016 to 10019,
        11016 to 11019,
        12016 to 12019,
        13015 to 13018,
    )

    fun normalizeSelectedSkill(skillId: Int): Int =
        LARGE_SUMMON_BY_BASE[skillId] ?: skillId
}
