package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.SacredEquipSubDatLoader
import tw.tsm.data.loaders.SacredEquipSubDomainDatLoader
import java.nio.file.Files
import java.nio.file.Path

class SacredEquipSubD8C1RegressionTest {
    @Test
    fun `real DVT sub status tables match physical contracts`() {
        val dir = Path.of("File.dat", "Data-ios")
        val sub = dir.resolve("IOS_SacredEquipSubStatus_C.dat")
        val domain = dir.resolve("IOS_SacredEquipSubStatusDomain_C.dat")
        assumeTrue(Files.isRegularFile(sub) && Files.isRegularFile(domain))

        val defs = SacredEquipSubDatLoader.load(Files.readAllBytes(sub))
        assertEquals(18, defs.size)
        assertEquals(207, defs.getValue(1).attributeKind)
        assertEquals(180, defs.getValue(1).randomValues.first())
        assertEquals(450, defs.getValue(1).randomValues.last())
        assertEquals(108, defs.getValue(18).attributeKind)
        assertEquals(3, defs.getValue(18).randomValues.first())
        assertEquals(7, defs.getValue(18).randomValues.last())

        val domains = SacredEquipSubDomainDatLoader.load(Files.readAllBytes(domain))
        assertEquals(73, domains.size)
        assertEquals(1, domains.first().threshold)
        assertEquals(207, domains.first().attributeKind)
        assertEquals(6, domains.last().threshold)
        assertEquals(90, domains.last().attributeKind)
    }

    @Test
    fun `sub loaders reject truncated tables`() {
        val validSub = ByteArray(4 + 23).also { it[0] = 1; it[4] = 1; it[6] = 207.toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            SacredEquipSubDatLoader.load(validSub.copyOf(validSub.size - 1))
        }
        val validDomain = ByteArray(4 + 2).also { it[0] = 1; it[4] = 1; it[5] = 207.toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            SacredEquipSubDomainDatLoader.load(validDomain.copyOf(validDomain.size - 1))
        }
    }
}
