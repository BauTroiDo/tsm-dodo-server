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
import tw.tsm.gamelogic.systems.PetRosterPolicy
import tw.tsm.gamelogic.systems.StatSystem

class DvtPetExpTest {
    private val repo = mockk<PetRepository>(relaxed = true)
    private val stat = mockk<StatSystem>(relaxed = true) {
        every { calculateNeedTotalExp(any(), any()) } answers {
            val level = firstArg<Int>()
            if (level == 1) 6L else level.toLong() * 100L
        }
        every { calculateUpExp(1, 0) } returns 6L
        every { calculateMaxHp(any(), any(), any(), any(), any(), any(), any()) } answers {
            if (firstArg<Int>() == 1) 113L else 9999L
        }
        every { calculateMaxSp(any(), any(), any(), any(), any(), any(), any()) } answers {
            if (firstArg<Int>() == 1) 69 else 9999
        }
    }
    private val npc = mockk<NpcDef>(relaxed = true) {
        every { turn } returns 0
        every { level } returns 21
        every { name } returns "Pet"
        every { hpx } returns 8
        every { spx } returns 4
        every { hpBase } returns 98
        every { spBase } returns 95
        every { skills } returns intArrayOf(10000, 0, 0)
    }
    private val pets = PetSystem(repo, stat, mapOf(100 to npc))

    @Test fun `legacy spawn-level seed migrates back to DVT level one once`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns listOf(
            FollowNpcData(slotIndex = 1, npcId = 100, level = 21, exp = 0, seedFixed = false)
        )

        val cache = pets.load(42)
        val migrated = cache.follow.getValue(1)

        assertEquals(1, migrated.level)
        assertEquals(6L, migrated.exp)
        assertEquals(0, migrated.skillPoint)
        assertTrue(migrated.seedFixed)
        assertTrue(cache.dirty)
    }

    @Test fun `legacy pet migration preserves exp actually earned after capture`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns listOf(
            FollowNpcData(slotIndex = 1, npcId = 100, level = 21, exp = 2500, seedFixed = false)
        )

        val migrated = pets.load(42).follow.getValue(1)

        // Old seed was 2100 at spawn LV21; 400 earned EXP survives.
        // Correct LV1 seed is 6 => corrected cumulative EXP 406 => LV4 in this fixture.
        assertEquals(406L, migrated.exp)
        assertEquals(4, migrated.level)
        assertEquals(3, migrated.skillPoint)
        assertTrue(migrated.seedFixed)
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

    @Test fun `new captured pet starts at DVT level one with calculated hp sp`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns emptyList()
        pets.load(42)

        val created = pets.addFollowNpc(42, 100, npc, canGrow = 1)!!.second
        assertEquals(1, created.level)
        assertEquals(6L, created.exp)
        assertEquals(113L, created.hp)
        assertEquals(69, created.sp)
        assertEquals(8, created.hpx)
        assertEquals(4, created.spx)
        assertEquals(1, created.skill1Lv)
        assertEquals(0, created.skill2Lv)
    }

    @Test fun `manual capture may duplicate npc type only when existing copy is in cart`() = runTest {
        coEvery { repo.findByCharacterId(42) } returns listOf(
            FollowNpcData(
                slotIndex = 1,
                npcId = 100,
                storageType = PetSystem.STORAGE_CART,
                level = 21,
                exp = 2100,
                hp = 100,
                sp = 50,
            )
        )
        pets.load(42)

        assertNull(pets.addFollowNpc(42, 100, npc, canGrow = 1))
        val captured = pets.addFollowNpc(
            42, 100, npc,
            canGrow = 1,
            allowDuplicateInCart = true,
        )
        assertNotNull(captured)
        assertEquals(1, captured!!.first)
        assertEquals(100, captured.second.npcId)
        assertEquals(1, captured.second.level)
    }

    @Test fun `nine pet extension allocates the ninth carried slot only when enabled`() = runTest {
        val extended = PetSystem(
            petRepository = repo,
            statCalculator = stat,
            npcDefs = mapOf(100 to npc),
            rosterPolicy = PetRosterPolicy(ninePetEnabled = true, extensionMaxCarried = 9),
        )
        coEvery { repo.findByCharacterId(42) } returns (1..8).map { slot ->
            FollowNpcData(
                slotIndex = slot,
                npcId = 200 + slot,
                storageType = PetSystem.STORAGE_FOLLOW,
                level = 1,
                exp = 6,
                seedFixed = true,
            )
        }
        extended.load(42)

        val added = extended.addFollowNpc(42, 100, npc, canGrow = 1)

        assertNotNull(added)
        assertEquals(9, added!!.first)
        assertEquals(100, extended.getFollowPets(42).getValue(9).npcId)
    }

    @Test fun `pet exp uses same pure battle reward curve without rerolling drops`() {
        val enemy = mockk<NpcDef> {
            every { level } returns 21
        }
        assertEquals(210L, BattleRewardCalc.calculateExp(listOf(900 to enemy), actorLevel = 21))
    }
}
