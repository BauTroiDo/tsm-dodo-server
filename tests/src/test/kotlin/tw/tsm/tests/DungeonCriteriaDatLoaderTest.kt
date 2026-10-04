package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonCriteriaDatLoader

class DungeonCriteriaDatLoaderTest {
    private val path = Path.of("..", "File.dat", "Data-ios", "IOS_DungeonCriteria_C.dat")

    @Test
    fun `DVT dungeon criteria table matches Lua record contract`() {
        assertTrue(Files.isRegularFile(path), "missing $path")
        val bytes = Files.readAllBytes(path)
        val defs = DungeonCriteriaDatLoader.load(bytes)

        assertEquals(446, bytes.size)
        assertEquals(34, defs.size)
        assertEquals((1..34).toList(), defs.keys.toList())

        val pairedElement = defs.getValue(6)
        assertEquals(3, pairedElement.playerOrNpc)
        assertEquals(2, pairedElement.kind)
        assertEquals(1, pairedElement.compare)
        assertEquals(1L, pairedElement.value)
        assertEquals(3L, pairedElement.value2)

        val requireSkill = defs.getValue(15)
        assertEquals(5, requireSkill.playerOrNpc)
        assertEquals(4, requireSkill.kind)
        assertEquals(1, requireSkill.compare)
        assertEquals(10001L, requireSkill.value)

        val forbidSkill = defs.getValue(17)
        assertEquals(3, forbidSkill.playerOrNpc)
        assertEquals(4, forbidSkill.kind)
        assertEquals(6, forbidSkill.compare)
        assertEquals(10002L, forbidSkill.value)

        val roundGreaterThan = defs.getValue(25)
        assertEquals(7, roundGreaterThan.kind)
        assertEquals(4, roundGreaterThan.compare)
        assertEquals(10L, roundGreaterThan.value)

        val roundLessThan = defs.getValue(26)
        assertEquals(7, roundLessThan.kind)
        assertEquals(2, roundLessThan.compare)
        assertEquals(10L, roundLessThan.value)
    }

    @Test
    fun `loader rejects truncated and trailing criteria data`() {
        val bytes = Files.readAllBytes(path)
        assertThrows(IllegalArgumentException::class.java) {
            DungeonCriteriaDatLoader.load(bytes.copyOf(bytes.size - 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DungeonCriteriaDatLoader.load(bytes + byteArrayOf(0))
        }
    }
}
