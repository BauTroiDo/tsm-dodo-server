package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.battle.BattleRewardCalc
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.StatSystem

class DvtPetExpTest {
    private val repo = mockk<PetRepository>(relaxed = true)
    private val stat = mockk<StatSystem>(relaxed = true) {
        every { calculateNeedTotalExp(any(), any()) } answers {
            firstArg<Int>().toLong() * 100L
        }
    }
    private val npc = mockk<NpcDef>(relaxed = true) {
        every { turn } returns 0
        every { level } returns 21
        every { name } returns "Pet"
        every { skills } returns intArrayOf(10000, 0, 0)
    }
    private val pets = PetSystem(repo, stat, mapOf(100 to npc))

    @Test fun `legacy zero pet exp migrates to cumulative level floor on load`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns listOf(
            FollowNpcData(slotIndex = 1, npcId = 100, level = 21, exp = 0)
        )

        val cache = pets.load(42)

        assertEquals(2100L, cache.follow.getValue(1).exp)
        assertTrue(cache.dirty)
    }

    @Test fun `battle exp advances pet cumulative gauge and level threshold`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns listOf(
            FollowNpcData(
                slotIndex = 1,
                npcId = 100,
                level = 21,
                exp = 2100,
                skillPoint = 7,
                canGrow = 1
            )
        )
        pets.load(42)

        val first = pets.gainBattleExp(42, 1, 50)!!
        assertEquals(2150L, first.after.exp)
        assertEquals(21, first.after.level)

        val second = pets.gainBattleExp(42, 1, 100)!!
        assertEquals(2250L, second.after.exp)
        assertEquals(22, second.after.level)
        assertEquals(8, second.after.skillPoint)
        assertEquals(1, second.levelGained)
    }

    @Test fun `non growable pet does not receive battle exp`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns listOf(
            FollowNpcData(slotIndex = 1, npcId = 100, level = 21, exp = 2100, canGrow = 0)
        )
        pets.load(42)

        assertNull(pets.gainBattleExp(42, 1, 100))
        assertEquals(2100L, pets.getFollowPets(42).getValue(1).exp)
    }

    @Test fun `new follow pet starts at cumulative exp floor instead of zero`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns emptyList()
        pets.load(42)

        val created = pets.addFollowNpc(42, 100, npc, canGrow = 1)!!.second
        assertEquals(2100L, created.exp)
    }

    @Test fun `pet exp uses same pure battle reward curve without rerolling drops`() {
        val enemy = mockk<NpcDef> {
            every { level } returns 21
        }
        assertEquals(210L, BattleRewardCalc.calculateExp(listOf(900 to enemy), actorLevel = 21))
    }
}
