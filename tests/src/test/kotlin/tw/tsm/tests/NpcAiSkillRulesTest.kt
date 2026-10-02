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
}
