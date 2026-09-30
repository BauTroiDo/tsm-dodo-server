package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.PetRosterPolicy

class NinePetRosterPolicyTest {
    @Test
    fun `nine pet extension is legacy four by default`() {
        assertEquals(4, PetRosterPolicy().carriedCapacity())
    }

    @Test
    fun `nine pet extension exposes nine only when explicitly enabled`() {
        assertEquals(
            9,
            PetRosterPolicy(
                ninePetEnabled = true,
                extensionMaxCarried = 9,
            ).carriedCapacity()
        )
    }

    @Test
    fun `extension cannot silently exceed audited nine-slot ceiling`() {
        assertThrows(IllegalArgumentException::class.java) {
            PetRosterPolicy(ninePetEnabled = true, extensionMaxCarried = 10)
        }
    }

    @Test
    fun progressionUnlocksSlotsFiveThroughNineAtLevelMilestones() {
        val policy = PetRosterPolicy(
            ninePetEnabled = true,
            extensionMaxCarried = 9,
            progressionEnabled = true,
        )

        assertEquals(4, policy.unlockedCapacity(level = 39, turn = 0))
        assertEquals(5, policy.unlockedCapacity(level = 40, turn = 0))
        assertEquals(6, policy.unlockedCapacity(level = 80, turn = 0))
        assertEquals(7, policy.unlockedCapacity(level = 120, turn = 0))
        assertEquals(8, policy.unlockedCapacity(level = 160, turn = 0))
        assertEquals(9, policy.unlockedCapacity(level = 200, turn = 0))
    }

    @Test
    fun rebirthMayUnlockAllExtensionSlotsImmediately() {
        val policy = PetRosterPolicy(
            ninePetEnabled = true,
            extensionMaxCarried = 9,
            progressionEnabled = true,
            rebirthUnlockAll = true,
        )

        assertEquals(4, policy.unlockedCapacity(level = 1, turn = 0))
        assertEquals(9, policy.unlockedCapacity(level = 1, turn = 1))
    }

    @Test
    fun progressionDisabledPreservesFullExtensionCapacity() {
        val policy = PetRosterPolicy(
            ninePetEnabled = true,
            extensionMaxCarried = 9,
            progressionEnabled = false,
        )

        assertEquals(9, policy.unlockedCapacity(level = 1, turn = 0))
    }

}
