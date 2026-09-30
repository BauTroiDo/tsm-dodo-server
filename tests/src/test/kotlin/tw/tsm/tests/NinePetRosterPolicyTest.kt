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
    fun progressionDoesNotLockCarriedRoster() {
        val policy = PetRosterPolicy(
            ninePetEnabled = true,
            extensionMaxCarried = 9,
            progressionEnabled = true,
        )

        assertEquals(9, policy.unlockedCapacity(level = 1, turn = 0))
        assertEquals(9, policy.unlockedCapacity(level = 39, turn = 0))
        assertEquals(null, policy.nextUnlockLevel(level = 1, turn = 0))
    }

    @Test
    fun progressionUnlocksBattleFollowersFiveThroughNineAtLevelMilestones() {
        val policy = PetRosterPolicy(
            ninePetEnabled = true,
            extensionMaxCarried = 9,
            progressionEnabled = true,
        )

        assertEquals(4, policy.battleDeploymentCapacity(level = 39, turn = 0))
        assertEquals(5, policy.battleDeploymentCapacity(level = 40, turn = 0))
        assertEquals(6, policy.battleDeploymentCapacity(level = 80, turn = 0))
        assertEquals(7, policy.battleDeploymentCapacity(level = 120, turn = 0))
        assertEquals(8, policy.battleDeploymentCapacity(level = 160, turn = 0))
        assertEquals(9, policy.battleDeploymentCapacity(level = 200, turn = 0))
        assertEquals(40, policy.nextBattleUnlockLevel(level = 39, turn = 0))
        assertEquals(80, policy.nextBattleUnlockLevel(level = 40, turn = 0))
        assertEquals(200, policy.nextBattleUnlockLevel(level = 199, turn = 0))
        assertEquals(null, policy.nextBattleUnlockLevel(level = 200, turn = 0))
    }

    @Test
    fun rebirthMayUnlockAllBattleFollowersImmediately() {
        val policy = PetRosterPolicy(
            ninePetEnabled = true,
            extensionMaxCarried = 9,
            progressionEnabled = true,
            rebirthUnlockAll = true,
        )

        assertEquals(9, policy.unlockedCapacity(level = 1, turn = 0))
        assertEquals(4, policy.battleDeploymentCapacity(level = 1, turn = 0))
        assertEquals(9, policy.battleDeploymentCapacity(level = 1, turn = 1))
        assertEquals(null, policy.nextBattleUnlockLevel(level = 1, turn = 1))
    }

    @Test
    fun progressionDisabledPreservesFullRosterAndLegacySingleBattlePet() {
        val policy = PetRosterPolicy(
            ninePetEnabled = true,
            extensionMaxCarried = 9,
            progressionEnabled = false,
        )

        assertEquals(9, policy.unlockedCapacity(level = 1, turn = 0))
        assertEquals(1, policy.battleDeploymentCapacity(level = 200, turn = 0))
    }

}
