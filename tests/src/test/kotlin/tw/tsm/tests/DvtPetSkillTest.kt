package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PetSkillSystem

class DvtPetSkillTest {
    private val repo = mockk<PetRepository>(relaxed = true)
    private val pets = PetSystem(repo, mockk(relaxed = true))
    private val npc = mockk<NpcDef> {
        every { skills } returns intArrayOf(30001, 30002, 30003)
        every { element } returns 1
        every { specialSkill } returns 0
        every { upgradeSkill } returns 0
        every { passiveSkill } returns 0
    }
    private fun skill(prereqs: IntArray = intArrayOf()) = mockk<SkillDef> {
        every { maxLv } returns 10
        every { element } returns 2
        every { learnPoint } returns 3
        every { levelUpPoint } returns 2
        every { preSkills } returns prereqs.toList()
    }
    private val defs = mutableMapOf(30001 to skill(), 30002 to skill(), 30003 to skill())
    private val system = PetSkillSystem(pets, mapOf(100 to npc), defs)
    private suspend fun load(pet: FollowNpcData = FollowNpcData(1, 100, skillPoint = 30)) {
        coEvery { repo.findByCharacterId(42) } returns listOf(pet)
        pets.load(42)
    }
    @Test fun `cross element doubles first point only and replay is free`() = runTest {
        load()
        val result = system.upgrade(42, 1, listOf(30001 to 3))!!
        assertEquals(20, result.pet.skillPoint) // 2*3 + 2 + 2
        assertEquals(3, result.pet.skill1Lv)
        assertEquals(result, system.upgrade(42, 1, listOf(30001 to 3)))
        assertTrue(pets.getCache(42)!!.dirty)
    }
    @Test fun `existing skill levels have no element surcharge`() = runTest {
        load(FollowNpcData(1, 100, skillPoint = 30, skill1Lv = 1))
        assertEquals(26, system.upgrade(42, 1, listOf(30001 to 3))!!.pet.skillPoint)
    }
    @Test fun `insufficient total points reject entire batch`() = runTest {
        load(FollowNpcData(1, 100, skillPoint = 8))
        val result = system.upgrade(42, 1, listOf(30001 to 1, 30002 to 1))!!
        assertEquals(8, result.pet.skillPoint)
        assertEquals(0, result.pet.skill1Lv)
        assertFalse(pets.getCache(42)!!.dirty)
    }
    @Test fun `foreign skills duplicates over cap and downgrade are rejected`() = runTest {
        load(FollowNpcData(1, 100, skillPoint = 30, skill1Lv = 2))
        val original = pets.getFollowPets(42)[1]
        for (request in listOf(listOf(999 to 1), listOf(30001 to 3, 30001 to 4),
            listOf(30001 to 11), listOf(30001 to 1))) {
            assertEquals(original, system.upgrade(42, 1, request)!!.pet)
        }
        assertFalse(pets.getCache(42)!!.dirty)
    }
    @Test fun `special skill cannot be purchased even when present in normal table`() = runTest {
        load()
        every { npc.specialSkill } returns 30001
        assertEquals(0, system.upgrade(42, 1, listOf(30001 to 1))!!.pet.skill1Lv)
    }
    @Test fun `one learned prerequisite is sufficient`() = runTest {
        defs[30003] = skill(intArrayOf(30001, 30002))
        load(FollowNpcData(1, 100, skillPoint = 30, skill1Lv = 1))
        assertEquals(1, system.upgrade(42, 1, listOf(30003 to 1))!!.pet.skill3Lv)
    }
    @Test fun `pending prerequisites resolve regardless of wire order`() = runTest {
        defs[30002] = skill(intArrayOf(30001))
        defs[30003] = skill(intArrayOf(30002))
        load()
        val result = system.upgrade(42, 1, listOf(30003 to 1, 30002 to 1, 30001 to 1))!!
        assertEquals(listOf(1, 1, 1), listOf(result.pet.skill1Lv, result.pet.skill2Lv, result.pet.skill3Lv))
        assertEquals(12, result.pet.skillPoint)
    }
    @Test fun `unlearned prerequisite cycle cannot bootstrap`() = runTest {
        defs[30001] = skill(intArrayOf(30002))
        defs[30002] = skill(intArrayOf(30001))
        load()
        assertEquals(30, system.upgrade(42, 1, listOf(30001 to 1, 30002 to 1))!!.pet.skillPoint)
        assertFalse(pets.getCache(42)!!.dirty)
    }
    @Test fun `only own loaded follow pet is eligible and soul remains unsupported`() = runTest {
        load(FollowNpcData(1, 100, storageType = 2, skillPoint = 30))
        assertNull(system.upgrade(42, 1, listOf(30001 to 1)))
        assertNull(system.upgrade(43, 1, listOf(30001 to 1)))
        load(FollowNpcData(1, 100, skillPoint = 30, soulId = 123))
        assertNull(system.upgrade(42, 1, listOf(30001 to 1)))
        load(FollowNpcData(1, 100, skillPoint = 30, equips = mapOf(3 to ThingData(itemId = 123))))
        assertNull(system.upgrade(42, 1, listOf(30001 to 1)))
    }
    @Test fun `updated points and levels survive existing repository save and reload`() = runTest {
        load()
        val result = system.upgrade(42, 1, listOf(30001 to 3))!!
        val saved = slot<List<FollowNpcData>>()
        coEvery { repo.saveAll(42, capture(saved)) } just Runs
        pets.saveAndUnload(42)
        assertEquals(result.pet, saved.captured.single())
        coEvery { repo.findByCharacterId(42) } returns saved.captured
        pets.load(42)
        assertEquals(result.pet, pets.getFollowPets(42)[1])
    }
}
