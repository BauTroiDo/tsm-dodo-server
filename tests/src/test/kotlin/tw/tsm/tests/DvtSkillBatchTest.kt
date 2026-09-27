package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.Turn2SkillState
import tw.tsm.gamelogic.systems.SkillSystem

class DvtSkillBatchTest {
    private val repo = mockk<SkillRepository> {
        coEvery { findByCharacterId(42) } returns emptyMap()
    }
    private fun skill(pre: List<Int> = emptyList(), elementValue: Int = 2, price: Int = 3) = mockk<SkillDef> {
        every { preSkills } returns pre
        every { element } returns elementValue
        every { learnPoint } returns price
        every { levelUpPoint } returns 2
        every { maxLv } returns 10
        every { turnKind } returns 0
    }
    @Test fun `reverse dependency order matches forward order`() = runTest {
        val defs = mapOf(100 to skill(), 200 to skill(listOf(100)), 300 to skill(listOf(200)))
        val system = SkillSystem(repo, mockk(), defs)
        system.load(42, 1, 30)
        system.upgradeSkills(42, listOf(300 to 1, 200 to 1, 100 to 1))
        assertEquals(mapOf(100 to 1, 200 to 1, 300 to 1), system.getCache(42)!!.skills)
        assertEquals(12, system.getCache(42)!!.skillPoint)
    }
    @Test fun `cycle and insufficient batch leave all state unchanged`() = runTest {
        for (defs in listOf(mapOf(100 to skill(listOf(200)), 200 to skill(listOf(100))),
            mapOf(100 to skill(), 200 to skill()))) {
            val system = SkillSystem(repo, mockk(), defs)
            val cache = system.load(42, 1, 8)
            assertTrue(system.upgradeSkills(42, listOf(100 to 1, 200 to 1)).isEmpty())
            assertEquals(8, cache.skillPoint)
            assertTrue(cache.skills.isEmpty())
            assertFalse(cache.dirty)
        }
    }
    @Test fun `invalid trailing skill cannot partially spend valid prefix`() = runTest {
        val system = SkillSystem(repo, mockk(), mapOf(100 to skill()))
        val cache = system.load(42, 1, 30)
        assertTrue(system.upgradeSkills(42, listOf(100 to 1, 999 to 1)).isEmpty())
        assertEquals(30, cache.skillPoint)
        assertTrue(cache.skills.isEmpty())
    }
    @Test fun `summon and special ids cannot be bought`() = runTest {
        for (id in listOf(14026, 14077, 10016, 11016, 12016, 13015, 22011, 23011)) {
            val system = SkillSystem(repo, mockk(), mapOf(id to skill()))
            system.load(42, 1, 30)
            assertTrue(system.upgradeSkills(42, listOf(id to 1)).isEmpty())
        }
    }
    @Test fun `all four opposed element pairs are blocked`() = runTest {
        for ((own, other) in listOf(1 to 3, 2 to 4, 3 to 1, 4 to 2)) {
            val system = SkillSystem(repo, mockk(), mapOf(100 to skill(elementValue = other)))
            system.load(42, own, 30)
            assertTrue(system.upgradeSkills(42, listOf(100 to 1)).isEmpty())
        }
    }
    @Test fun `large or negative cost never credits points`() = runTest {
        for (price in listOf(Int.MAX_VALUE, -1)) {
            val system = SkillSystem(repo, mockk(), mapOf(100 to skill(price = price)))
            val cache = system.load(42, 1, 30)
            assertTrue(system.upgradeSkills(42, listOf(100 to 2)).isEmpty())
            assertEquals(30, cache.skillPoint)
            assertFalse(cache.dirty)
        }
    }
    @Test fun `duplicate requests reject and replay does not spend again`() = runTest {
        val system = SkillSystem(repo, mockk(), mapOf(100 to skill()))
        val cache = system.load(42, 1, 30)
        assertTrue(system.upgradeSkills(42, listOf(100 to 1, 100 to 2)).isEmpty())
        assertEquals(30, cache.skillPoint)
        system.upgradeSkills(42, listOf(100 to 2))
        assertEquals(22, cache.skillPoint)
        assertTrue(system.upgradeSkills(42, listOf(100 to 2)).isEmpty())
        assertEquals(22, cache.skillPoint)
    }
}