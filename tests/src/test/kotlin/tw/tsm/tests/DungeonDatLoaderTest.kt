package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonDatLoader

class DungeonDatLoaderTest {
    @Test
    fun `DVT IOS Dungeon table matches Lua record contract`() {
        val path = Path.of("..", "File.dat", "Data-ios", "IOS_Dungeon_C.dat")
        assertTrue(Files.isRegularFile(path), "missing $path")

        val defs = DungeonDatLoader.load(Files.readAllBytes(path))
        assertEquals(57, defs.size)

        val multi = defs.getValue(1)
        assertEquals(1, multi.kind)
        assertEquals(20, multi.minLv)
        assertEquals(450, multi.maxLv)
        assertEquals(5, multi.maxPlayer)
        assertEquals(62002, multi.sceneId)
        assertEquals(12332, multi.missionFlag)
        assertEquals(12334, multi.dailyFlag)
        assertEquals(1, multi.dailyCount)

        val single = defs.getValue(2)
        assertEquals(2, single.kind)
        assertEquals(15, single.minLv)
        assertEquals(80, single.maxLv)
        assertEquals(1, single.maxPlayer)
        assertEquals(62001, single.sceneId)
        assertEquals(12322, single.missionFlag)
        assertEquals(12336, single.dailyFlag)
        assertEquals(10, single.timeMinutes)

        val weekly = defs.getValue(18)
        assertEquals(3, weekly.kind)
        assertEquals(62015, weekly.sceneId)
        assertEquals(34366, weekly.allPassPrize)
    }
}
