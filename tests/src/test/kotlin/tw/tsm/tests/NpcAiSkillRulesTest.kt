package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.battle.NpcAiSkillRules

class NpcAiSkillRulesTest {
    @Test
    fun `four elemental npc summons map to large variants`() {
        assertEquals(10019, NpcAiSkillRules.normalizeSelectedSkill(10016))
        assertEquals(11019, NpcAiSkillRules.normalizeSelectedSkill(11016))
        assertEquals(12019, NpcAiSkillRules.normalizeSelectedSkill(12016))
        assertEquals(13018, NpcAiSkillRules.normalizeSelectedSkill(13015))
    }

    @Test
    fun `light dark and ordinary skills are not invented into other ids`() {
        assertEquals(22011, NpcAiSkillRules.normalizeSelectedSkill(22011))
        assertEquals(23011, NpcAiSkillRules.normalizeSelectedSkill(23011))
        assertEquals(10000, NpcAiSkillRules.normalizeSelectedSkill(10000))
        assertEquals(14057, NpcAiSkillRules.normalizeSelectedSkill(14057))
    }

    @Test
    fun `slot holes keep PC modulo priority instead of being compressed`() {
        val slots = intArrayOf(14080, 0, 0, 21001)
        assertEquals(21001, NpcAiSkillRules.selectByRoll(slots, 10))
        assertEquals(14080, NpcAiSkillRules.selectByRoll(slots, 2))
        assertEquals(10000, NpcAiSkillRules.selectByRoll(slots, 3))
    }

    @Test
    fun `duplicate slots keep their independent PC gates`() {
        val slots = intArrayOf(20052, 20052, 20052, 0)
        assertEquals(20052, NpcAiSkillRules.selectByRoll(slots, 3))
        assertEquals(20052, NpcAiSkillRules.selectByRoll(slots, 4))
        assertEquals(10000, NpcAiSkillRules.selectByRoll(slots, 5))
    }
}
