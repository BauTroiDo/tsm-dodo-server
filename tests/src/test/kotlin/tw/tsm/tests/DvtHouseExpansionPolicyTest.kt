package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.HouseExpandConditionDatLoader
import tw.tsm.gamelogic.systems.HouseExpansionPolicy
import java.nio.file.Files
import java.nio.file.Path

class DvtHouseExpansionPolicyTest {
    private fun table() = run {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_HouseExpandCondition_C.dat")) }
        HouseExpandConditionDatLoader.load(
            Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_HouseExpandCondition_C.dat")))
    }

    @Test fun exactTwelveOriginalLevelsAndPrices() {
        val entries = table()
        assertEquals(12, entries.size)
        assertEquals(listOf(0, 100_000, 500_000, 800_000, 100_000, 500_000, 800_000,
            100_000, 500_000, 800_000, 100_000, 500_000),
            (1..12).map { entries.getValue(it).goldCost })
        assertEquals(listOf(0, 6, 6, 6, 10, 10, 10, 12, 12, 12, 13, 13),
            (1..12).map { entries.getValue(it).furnitureKindsRequired })
    }

    @Test fun floorsAndBuildingsUnlockedOnlyAfterSequentialMilestones() {
        assertTrue(HouseExpansionPolicy.unlocked(1, 1, 1))
        assertFalse(HouseExpansionPolicy.unlocked(2, 1, 2))
        assertTrue(HouseExpansionPolicy.unlocked(3, 1, 2))
        assertFalse(HouseExpansionPolicy.unlocked(3, 2, 1))
        assertTrue(HouseExpansionPolicy.unlocked(4, 2, 1))
        assertTrue(HouseExpansionPolicy.unlocked(6, 2, 2))
        assertFalse(HouseExpansionPolicy.unlocked(9, 4, 1))
        assertTrue(HouseExpansionPolicy.unlocked(12, 4, 2))
        assertFalse(HouseExpansionPolicy.unlocked(12, 5, 1))
    }

    @Test fun goldAndFurnitureKindsMustBothSatisfyTheExactNextLevel() {
        val data = table()
        assertFalse(HouseExpansionPolicy.eligible(1, 5, 500_000, data))
        assertFalse(HouseExpansionPolicy.eligible(1, 6, 99_999, data))
        assertTrue(HouseExpansionPolicy.eligible(1, 6, 100_000, data))
        assertFalse(HouseExpansionPolicy.eligible(4, 9, 1_000_000, data))
        assertTrue(HouseExpansionPolicy.eligible(4, 10, 100_000, data))
        assertFalse(HouseExpansionPolicy.eligible(12, 20, 2_000_000, data))
        assertNull(HouseExpansionPolicy.next(12, data))
        assertNull(HouseExpansionPolicy.next(1, emptyMap()))
    }
}
