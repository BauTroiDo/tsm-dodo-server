package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.gamelogic.systems.SkillSystem

class DvtSkillRulesTest {
    private val repo = mockk<SkillRepository>()
    private fun skill(prereqs: IntArray = intArrayOf(), skillElement: Int = 2) = mockk<SkillDef> {
        every { maxLv } returns 10
        every { element } returns skillElement
        every { learnPoint } returns 3
        every { levelUpPoint } returns 2
        every { preSkills } returns prereqs.toList()
        every { name } returns "skill"
    }
    @Test fun `player pays doubled learn cost but normal subsequent cost`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns emptyMap()
        val system = SkillSystem(repo, mockk(), mapOf(100 to skill()))
        val cache = system.load(42, 1, 30)
        system.upgradeSkills(42, listOf(100 to 3))
        assertEquals(20, cache.skillPoint)
        system.upgradeSkills(42, listOf(100 to 4))
        assertEquals(18, cache.skillPoint)
    }
    @Test fun `element outside one through four has no learn surcharge`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns emptyMap()
        val system = SkillSystem(repo, mockk(), mapOf(100 to skill(skillElement = 5)))
        val cache = system.load(42, 1, 30)
        system.upgradeSkills(42, listOf(100 to 1))
        assertEquals(27, cache.skillPoint)
    }
    @Test fun `any defined learned prerequisite unlocks first level`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns mapOf(100 to 1)
        val system = SkillSystem(repo, mockk(), mapOf(100 to skill(), 200 to skill(), 300 to skill(intArrayOf(100, 200))))
        system.load(42, 1, 30)
        assertEquals(listOf(300 to 1), system.upgradeSkills(42, listOf(300 to 1)))
    }
    @Test fun `zero level does not satisfy a prerequisite`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns mapOf(100 to 0)
        val system = SkillSystem(repo, mockk(), mapOf(100 to skill(), 300 to skill(intArrayOf(100))))
        system.load(42, 1, 30)
        assertTrue(system.upgradeSkills(42, listOf(300 to 1)).isEmpty())
    }
}
