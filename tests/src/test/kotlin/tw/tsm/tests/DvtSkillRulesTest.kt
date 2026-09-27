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
        every { turnKind } returns 0
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
    @Test fun `turn skill requires matching rebirth count`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns emptyMap()
        val turnSkill = skill().also { every { it.turnKind } returns 1 }
        val system = SkillSystem(repo, mockk(), mapOf(10022 to turnSkill))
        val cache = system.load(42, 1, 30)

        assertTrue(system.upgradeSkills(42, listOf(10022 to 1), playerTurn = 0).isEmpty())
        assertEquals(30, cache.skillPoint)
        assertEquals(listOf(10022 to 1), system.upgradeSkills(42, listOf(10022 to 1), playerTurn = 1))
    }

    @Test fun `turn1 root requires a previously learned base branch skill`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns emptyMap()
        val root = skill().also { every { it.turnKind } returns 1 }
        val base = skill(skillElement = 1)
        val system = SkillSystem(repo, mockk(), mapOf(10020 to root, 10013 to base))
        val cache = system.load(42, 1, 30)

        assertTrue(system.upgradeSkills(42, listOf(10020 to 1), playerTurn = 1).isEmpty())
        assertEquals(30, cache.skillPoint)
    }

    @Test fun `turn1 root accepts one previously learned branch endpoint`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns mapOf(10013 to 1)
        val root = skill().also { every { it.turnKind } returns 1 }
        val base = skill(skillElement = 1)
        val system = SkillSystem(repo, mockk(), mapOf(10020 to root, 10013 to base))
        system.load(42, 1, 30)

        assertEquals(listOf(10020 to 1), system.upgradeSkills(42, listOf(10020 to 1), playerTurn = 1))
    }

    @Test fun `turn1 root cannot satisfy special branch requirement inside same batch`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns emptyMap()
        val root = skill().also { every { it.turnKind } returns 1 }
        val base = skill(skillElement = 1)
        val system = SkillSystem(repo, mockk(), mapOf(10020 to root, 10013 to base))
        val cache = system.load(42, 1, 30)

        assertTrue(system.upgradeSkills(42, listOf(10013 to 1, 10020 to 1), playerTurn = 1).isEmpty())
        assertEquals(30, cache.skillPoint)
        assertTrue(cache.skills.isEmpty())
    }

}