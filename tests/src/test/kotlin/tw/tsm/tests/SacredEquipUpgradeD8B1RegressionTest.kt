package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.SacredEquipUpgradeDatLoader
import java.nio.file.Files
import java.nio.file.Path

class SacredEquipUpgradeD8B1RegressionTest {
    @Test
    fun realDvtFileMatchesLuaContract() {
        val file = Path.of("File.dat", "Data-ios", "IOS_SacredEquipUpgrade_C.dat")
        assumeTrue(Files.isRegularFile(file))
        val defs = SacredEquipUpgradeDatLoader.load(Files.readAllBytes(file))

        assertEquals(15, defs.size)
        assertEquals(100, defs.getValue(1).successPercent)
        assertEquals(3, defs.getValue(1).currencyKind)
        assertEquals(listOf(1000, 2000, 3000, 4000, 5000, 6000), defs.getValue(1).spendByThreshold)

        assertEquals(70, defs.getValue(6).successPercent)
        assertEquals(3, defs.getValue(6).currencyKind)
        assertEquals(65, defs.getValue(7).successPercent)
        assertEquals(8, defs.getValue(7).currencyKind)
        assertEquals(listOf(4, 7, 13, 19, 25, 33), defs.getValue(7).spendByThreshold)

        assertEquals(25, defs.getValue(15).successPercent)
        assertEquals(8, defs.getValue(15).currencyKind)
        assertEquals(57, defs.getValue(15).spendForThreshold(6))
        assertEquals(null, defs.getValue(15).spendForThreshold(0))
        assertEquals(null, defs.getValue(15).spendForThreshold(7))
    }

    @Test
    fun rejectsTruncatedUpgradeTable() {
        val bytes = ByteArray(4 + 15).also {
            it[0] = 1
            it[4] = 1
            it[5] = 100.toByte()
            it[6] = 3
        }
        assertThrows(IllegalArgumentException::class.java) {
            SacredEquipUpgradeDatLoader.load(bytes.copyOf(bytes.size - 1))
        }
    }
}
