package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.SacredEquipPrimaryDatLoader
import java.nio.file.Files
import java.nio.file.Path

class SacredEquipPrimaryD8A1RegressionTest {
    @Test
    fun realDvtFileMatchesLuaRecordContract() {
        val file = Path.of("File.dat", "Data-ios", "IOS_SacredEquipPrimaryStatus_C.dat")
        assumeTrue(Files.isRegularFile(file))

        val defs = SacredEquipPrimaryDatLoader.load(Files.readAllBytes(file))
        assertEquals(102, defs.size)

        val first = defs.getValue(1)
        assertEquals(207, first.attributeKind)
        assertEquals(150, first.valueAt(0))
        assertEquals(1480, first.valueAt(15))

        val last = defs.getValue(102)
        assertEquals(107, last.attributeKind)
        assertEquals(10, last.valueAt(0))
        assertEquals(60, last.valueAt(15))
    }

    @Test
    fun rejectsTruncatedPrimaryTable() {
        val valid = ByteArray(4 + 35).also { bytes ->
            bytes[0] = 1
            bytes[4] = 1
            bytes[6] = 107
        }
        SacredEquipPrimaryDatLoader.load(valid)

        assertThrows(IllegalArgumentException::class.java) {
            SacredEquipPrimaryDatLoader.load(valid.copyOf(valid.size - 1))
        }
    }
}
