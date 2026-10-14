package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.HouseFurnitureRedeemPolicy

class DvtHouseFurnitureRedeemPolicyTest {
    @Test fun verifiesExactOwnershipAndCap() {
        assertTrue(HouseFurnitureRedeemPolicy.valid(53892, 53892, 62392, 1, 1, 0))
        assertFalse(HouseFurnitureRedeemPolicy.valid(53892, 53881, 62392, 1, 1, 0))
        assertFalse(HouseFurnitureRedeemPolicy.valid(53892, 53892, 0, 1, 1, 0))
        assertFalse(HouseFurnitureRedeemPolicy.valid(53892, 53892, 62392, 0, 1, 0))
        assertFalse(HouseFurnitureRedeemPolicy.valid(53892, 53892, 62392, 1, 0, 0))
        assertFalse(HouseFurnitureRedeemPolicy.valid(53892, 53892, 62392, 2, 1, 0))
        assertTrue(HouseFurnitureRedeemPolicy.valid(53892, 53892, 62392, 1, 1, 254))
        assertFalse(HouseFurnitureRedeemPolicy.valid(53892, 53892, 62392, 1, 1, 255))
        assertFalse(HouseFurnitureRedeemPolicy.valid(53892, 53892, 62392, 1, 1, -1))
    }
}
