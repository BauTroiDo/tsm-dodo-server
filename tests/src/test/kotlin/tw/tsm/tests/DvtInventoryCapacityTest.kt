package tw.tsm.tests

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.interfaces.repositories.RoleCountRepository
import tw.tsm.gamelogic.systems.RoleCountSystem

class DvtInventoryCapacityTest {
    @Test
    fun `DVT inventory base and max capacities match Item lua`() {
        assertEquals(250, ItemConstants.INIT_BAG_SLOTS)
        assertEquals(500, ItemConstants.MAX_BAG_SLOTS)
        assertEquals(250, ItemConstants.INIT_BANK_SLOTS)
        assertEquals(500, ItemConstants.MAX_BANK_SLOTS)
        assertEquals(250, ItemConstants.MAX_STORAGE_SLOTS)
    }

    @Test
    fun `DVT bag capacity starts at 250 and expands five slots per RoleCount`() {
        val system = RoleCountSystem(mockk<RoleCountRepository>(relaxed = true))
        val charId = 31L

        assertEquals(250, system.getBagCapacity(charId))

        system.setValue(charId, RoleCountSystem.BAG_1, 1)
        system.setValue(charId, RoleCountSystem.BAG_2, 2)
        system.setValue(charId, RoleCountSystem.BAG_3, 1)
        assertEquals(270, system.getBagCapacity(charId))

        system.setValue(charId, RoleCountSystem.BAG_1, 100)
        assertEquals(500, system.getBagCapacity(charId))
    }

    @Test
    fun `DVT bank capacity starts at 250 and caps at 500`() {
        val system = RoleCountSystem(mockk<RoleCountRepository>(relaxed = true))
        val charId = 31L

        assertEquals(250, system.getBankCapacity(charId))
        system.setValue(charId, RoleCountSystem.BANK_1, 100)
        assertEquals(500, system.getBankCapacity(charId))
    }

    @Test
    fun `DVT warrior repository starts at 5 adds one per RoleCount822 and caps at 150`() {
        val system = RoleCountSystem(mockk<RoleCountRepository>(relaxed = true))
        val charId = 31L

        assertEquals(5, system.getWarriorRepositoryCapacity(charId))
        system.setValue(charId, RoleCountSystem.WARRIOR_REPOSITORY, 1)
        assertEquals(6, system.getWarriorRepositoryCapacity(charId))
        system.setValue(charId, RoleCountSystem.WARRIOR_REPOSITORY, 999)
        assertEquals(150, system.getWarriorRepositoryCapacity(charId))
        assertEquals(145, RoleCountSystem.WARRIOR_REPOSITORY_MAX_EXPANSIONS)
    }
}
