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
}
