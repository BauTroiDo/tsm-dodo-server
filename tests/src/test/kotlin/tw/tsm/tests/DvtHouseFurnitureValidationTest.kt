package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.HouseFurniturePolicy
import tw.tsm.app.handlers.HouseSettingsPolicy
import tw.tsm.domain.interfaces.repositories.HouseFurniturePlacement

class DvtHouseFurnitureValidationTest {
    private fun f(id: Int, x: Int = 2, y: Int = 3, z: Int = 0, direction: Int = 0) =
        HouseFurniturePlacement(id, x, y, z, direction)

    @Test
    fun placementRequiresServerOwnedFurnitureAndRejectsForgedCopies() {
        assertTrue(HouseFurniturePolicy.validate(listOf(f(100)), mapOf(100 to 1)))
        assertFalse(HouseFurniturePolicy.validate(listOf(f(100)), emptyMap()))
        assertFalse(HouseFurniturePolicy.validate(listOf(f(100), f(100, x = 5)), mapOf(100 to 1)))
        assertFalse(HouseFurniturePolicy.validate(listOf(f(100), f(100)), mapOf(100 to 2)))
    }

    @Test
    fun rejectsMalformedLocationsAndOversizedPayload() {
        assertFalse(HouseFurniturePolicy.validate(listOf(f(100, x = 120)), mapOf(100 to 1)))
        assertFalse(HouseFurniturePolicy.validate(listOf(f(100, z = 20)), mapOf(100 to 1)))
        assertFalse(HouseFurniturePolicy.validate(listOf(f(100, direction = 7)), mapOf(100 to 1)))
        assertFalse(HouseFurniturePolicy.validate(List(91) { f(100, x = it % 10, y = it / 10) }, mapOf(100 to 100)))
    }

    @Test
    fun loaderHidesStaleFurnitureWhenOwnershipWasLost() {
        val placed = listOf(f(100), f(100, x = 5), f(200, x = 8))
        val visible = HouseFurniturePolicy.visible(placed, mapOf(100 to 1, 200 to 1))
        assertEquals(listOf(f(100), f(200, x = 8)), visible)
    }

    @Test
    fun validatesNameAndSignatureWithoutControlCharacters() {
        assertTrue(HouseSettingsPolicy.valid("My Home", "Hello"))
        assertFalse(HouseSettingsPolicy.valid("", ""))
        assertFalse(HouseSettingsPolicy.valid("a".repeat(9), ""))
        assertFalse(HouseSettingsPolicy.valid("Nice", "\u0000bad"))
    }
}
