package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.valueobjects.SoulSkillData
import tw.tsm.domain.valueobjects.SoulThingData
import tw.tsm.gamelogic.systems.SoulSkillLevelMerge

class SoulA4ReadOnlyIntegrationTest {
    @Test
    fun `Soul skill merge keeps higher duplicate levels and adds Soul-only skills`() {
        val base = mapOf(
            1001 to 3,
            1002 to 5,
        )
        val soul = SoulThingData(
            sn = 1L,
            itemId = 59501,
            element = 1,
            equipId = 10012,
            skills = listOf(
                SoulSkillData(1001, 6),
                SoulSkillData(1002, 2),
                SoulSkillData(2001, 4),
                SoulSkillData(0, 0),
            ),
        )

        val merged = SoulSkillLevelMerge.merge(base, soul)

        assertEquals(6, merged[1001])
        assertEquals(5, merged[1002])
        assertEquals(4, merged[2001])
        assertEquals(3, merged.size)
    }

    @Test
    fun `no Soul leaves base skill snapshot unchanged`() {
        val base = mapOf(1001 to 3)
        assertEquals(base, SoulSkillLevelMerge.merge(base, null))
    }
}
