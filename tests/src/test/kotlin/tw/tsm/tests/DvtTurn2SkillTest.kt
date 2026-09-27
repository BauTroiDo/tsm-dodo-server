package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.Turn2SkillState
import tw.tsm.gamelogic.systems.SkillSystem

class DvtTurn2SkillTest {
    private fun skill(max: Int = 10) = mockk<SkillDef> {
        every { maxLv } returns max
        every { element } returns 0
        every { learnPoint } returns 1
        every { levelUpPoint } returns 1
        every { preSkills } returns emptyList()
        every { turnKind } returns 1
        every { name } returns "turn2"
    }

    @Test fun `C28-1 cannot bypass Turn2 protocol`() = runTest {
        val repo = mockk<SkillRepository>()
        coEvery { repo.findByCharacterId(42) } returns emptyMap()
        coEvery { repo.findTurn2State(42) } returns Turn2SkillState()
        val system = SkillSystem(repo, mockk(), mapOf(14038 to skill()))
        val cache = system.load(42, 1, 20)

        assertTrue(system.upgradeSkills(42, listOf(14038 to 1), playerTurn = 2).isEmpty())
        assertEquals(20, cache.skillPoint)
    }

    @Test fun `Turn2 ball requires career base skill and consumes one available ball`() = runTest {
        val repo = mockk<SkillRepository>()
        coEvery { repo.findByCharacterId(42) } returns emptyMap()
        coEvery { repo.findTurn2State(42) } returns Turn2SkillState(availableBalls = 2)
        val system = SkillSystem(repo, mockk(), mapOf(14038 to skill()))
        val cache = system.load(42, 1, 20)

        assertTrue(system.upgradeTurn2(42, listOf(1), listOf(14038 to 1), emptyList(), 2, 1))
        assertEquals(1, cache.availableTurn2Balls)
        assertEquals(setOf(1), cache.activeTurn2Balls)
        assertEquals(1, cache.skills[14038])
    }

    @Test fun `Turn2 rejects wrong career skill atomically`() = runTest {
        val repo = mockk<SkillRepository>()
        coEvery { repo.findByCharacterId(42) } returns emptyMap()
        coEvery { repo.findTurn2State(42) } returns Turn2SkillState(availableBalls = 2)
        val system = SkillSystem(repo, mockk(), mapOf(14039 to skill()))
        val cache = system.load(42, 1, 20)

        assertFalse(system.upgradeTurn2(42, emptyList(), listOf(14039 to 1), emptyList(), 2, 1))
        assertEquals(20, cache.skillPoint)
        assertTrue(cache.skills.isEmpty())
        assertEquals(2, cache.availableTurn2Balls)
    }

    @Test fun `hero slot is one time and must match submitted level one skill`() = runTest {
        val repo = mockk<SkillRepository>()
        coEvery { repo.findByCharacterId(42) } returns emptyMap()
        coEvery { repo.findTurn2State(42) } returns Turn2SkillState()
        val system = SkillSystem(repo, mockk(), mapOf(10027 to skill(max = 1)))
        val cache = system.load(42, 1, 20)

        assertTrue(system.upgradeTurn2(42, emptyList(), listOf(10027 to 1), listOf(6 to 10027), 2, 1))
        assertEquals(10027, cache.heroSkills[6])
        assertFalse(system.upgradeTurn2(42, emptyList(), listOf(10027 to 1), listOf(7 to 10027), 2, 1))
    }
}
