package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import tw.tsm.data.loaders.TitleDataDatLoader
import tw.tsm.domain.data.TitleDef
import tw.tsm.gamelogic.systems.DvtTitlePolicy

class DvtTitleRegressionTest {
    private fun file(vararg parts: String): ByteArray {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        val path = if (Files.exists(direct)) direct else Path.of("..", *parts)
        return Files.readAllBytes(path)
    }

    @Test
    fun `real DVT title table has exact current rows`() {
        val defs = TitleDataDatLoader.load(file("File.dat", "Data-ios", "IOS_TitleData_C.dat"))

        assertEquals(33, defs.size)
        assertEquals(1558, defs[1]?.flagId)
        assertEquals(7151, defs[4]?.flagId)
        assertEquals(425, defs[101]?.flagId)
        assertEquals(7300, defs[124]?.flagId)
        assertTrue(defs.values.all { it.flagId > 0 })
    }

    @Test
    fun `equip policy mirrors UITitle ownership gate`() {
        val defs = mapOf(
            1 to TitleDef(1, 21931, 1558),
            2 to TitleDef(2, 21932, 0),
        )

        assertTrue(DvtTitlePolicy.canEquip(0, defs, emptySet()))
        assertFalse(DvtTitlePolicy.canEquip(999, defs, setOf(1558)))
        assertFalse(DvtTitlePolicy.canEquip(1, defs, emptySet()))
        assertTrue(DvtTitlePolicy.canEquip(1, defs, setOf(1558)))
        assertTrue(DvtTitlePolicy.canEquip(2, defs, emptySet()))
    }
}
