package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.BattleConstants

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

    /** Exact PC NPC AI one-roll slot priority: skill4 -> skill3 -> skill2 -> skill1. */
    fun selectByRoll(skills: IntArray, roll: Int): Int {
        require(roll in 0..99)
        if (skills.size > 3 && skills[3] > 0 && roll % 5 == 0) return skills[3]
        if (skills.size > 2 && skills[2] > 0 && roll % 4 == 0) return skills[2]
        if (skills.size > 1 && skills[1] > 0 && roll % 3 == 0) return skills[1]
        if (skills.isNotEmpty() && skills[0] > 0 && roll % 2 == 0) return skills[0]
        return BattleConstants.HAND_FIGHT_SKILL_ID
    }
}
