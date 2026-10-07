package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.FNpcSlotDef
import tw.tsm.domain.interfaces.repositories.ExpSlotRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.systems.ExpSlotSystem
import tw.tsm.gamelogic.systems.PetSystem

class ExpSlotSystemTest {
    private fun system(pet: FollowNpcData): ExpSlotSystem {
        val petSystem = mockk<PetSystem>()
        every { petSystem.getFollowPets(42L) } returns mapOf(pet.slotIndex to pet)
        every { petSystem.getCache(42L) } returns null
        return ExpSlotSystem(
            defs = mapOf(1 to FNpcSlotDef(1, 1000, listOf(1, 2, 3))),
            repository = mockk<ExpSlotRepository>(relaxed = true),
            petSystem = petSystem,
            npcDefs = emptyMap(),
        )
    }

    @Test
    fun sellIdsUseAuthoritativeCoins() {
        val s = system(FollowNpcData(slotIndex = 1, npcId = 123))
        assertEquals(ExpSlotSystem.NORMAL_COIN_ITEM_ID to 1, s.requirement(37))
        assertEquals(ExpSlotSystem.NORMAL_COIN_ITEM_ID to 10, s.requirement(38))
        assertEquals(ExpSlotSystem.TURN2_COIN_ITEM_ID to 1, s.requirement(65))
        assertEquals(ExpSlotSystem.TURN2_COIN_ITEM_ID to 10, s.requirement(66))
    }

    @Test
    fun luaCanGrowGateBlocksOnlyZeroAndTwo() {
        val allowed = FollowNpcData(slotIndex = 1, npcId = 123, canGrow = 3, level = 20, turnKind = 0)
        assertTrue(system(allowed).validateTarget(42L, 37, listOf(1, 123)))

        assertFalse(system(allowed.copy(canGrow = 0)).validateTarget(42L, 37, listOf(1, 123)))
        assertFalse(system(allowed.copy(canGrow = 2)).validateTarget(42L, 37, listOf(1, 123)))
    }

    @Test
    fun turnBranchAndLevelGateFollowLua() {
        val normal = FollowNpcData(slotIndex = 1, npcId = 123, level = 179, turnKind = 0)
        assertTrue(system(normal).validateTarget(42L, 37, listOf(1, 123)))
        assertFalse(system(normal.copy(level = 180)).validateTarget(42L, 37, listOf(1, 123)))

        val turn2 = FollowNpcData(slotIndex = 1, npcId = 123, level = 179, turnKind = 2)
        assertTrue(system(turn2).validateTarget(42L, 65, listOf(1, 123)))
        assertFalse(system(turn2).validateTarget(42L, 37, listOf(1, 123)))
    }
    @Test
    fun everyTurnAndLevelBoundaryAndForgedSellId() {
        for (turn in 0..3) for (level in listOf(179, 180, 181)) for (sell in listOf(37, 38, 65, 66)) {
            val pet = FollowNpcData(slotIndex = 1, npcId = 123, level = level, turnKind = turn)
            val expected = (turn >= 2) == (sell >= 65) && !(turn <= 2 && level >= 180)
            assertEquals(expected, system(pet).validateTarget(42, sell, listOf(1, 123)),
                "turn=$turn level=$level sell=$sell")
        }
        val s = system(FollowNpcData(slotIndex = 1, npcId = 123))
        assertFalse(s.validateTarget(42, 999, listOf(1, 123)))
        assertFalse(s.validateTarget(42, 37, listOf(1, 124)))
        assertFalse(s.validateTarget(42, 37, listOf(2, 123)))
        assertFalse(s.validateTarget(42, 37, listOf(1)))
    }

    @Test
    fun npcKind53AndDeployedPetAreBlocked() {
        val pet = FollowNpcData(slotIndex = 1, npcId = 123)
        val pets = mockk<PetSystem>()
        every { pets.getFollowPets(42) } returns mapOf(1 to pet)
        every { pets.getCache(42) } returns PetSystem.PetCache(fightNpcId = 123)
        val npc = mockk<tw.tsm.domain.data.NpcDef>()
        every { npc.kind } returns 31
        val s = ExpSlotSystem(mapOf(1 to FNpcSlotDef(1, 1000, listOf(1,2,3))),
            mockk(relaxed = true), pets, mapOf(123 to npc))
        assertFalse(s.validateTarget(42, 37, listOf(1, 123)))
        every { pets.getCache(42) } returns PetSystem.PetCache()
        every { npc.kind } returns 53
        assertFalse(s.validateTarget(42, 37, listOf(1, 123)))
    }

    @Test
    fun realDvtCoinNamesDescriptionsAndSlotRows() {
        val items = tw.tsm.data.loaders.ItemDatLoader.load(java.io.File("../File.dat/Data-ios/IOS_Item_C.dat").readBytes())
        val normal = items.getValue(31207)
        val turn2 = items.getValue(29877)
        println("EXP_SLOT_COIN 31207: ${normal.name} / ${normal.description}")
        println("EXP_SLOT_COIN 29877: ${turn2.name} / ${turn2.description}")
        assertEquals("ThônLạpBá tệ", normal.name)
        assertEquals("Thôn Thực Lạp Phách Kim Tệ", turn2.name)
        assertTrue(normal.description.contains("BDY"))
        assertTrue(turn2.description.contains("nhị chuyển"))
        val rows = tw.tsm.data.loaders.FNpcSlotDatLoader.load(java.io.File("../File.dat/Data-ios/IOS_FNpcSlot_C.dat").readBytes())
        assertEquals(400, rows.size)
        assertEquals(5_000_000, rows.values.maxOf { it.exp })
        assertTrue(rows.values.all { it.icons.size == 3 })
    }

}
