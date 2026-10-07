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
}
