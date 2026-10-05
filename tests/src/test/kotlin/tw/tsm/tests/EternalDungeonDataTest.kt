package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ActivityHandler
import tw.tsm.app.handlers.EternalDungeonHandler
import tw.tsm.data.loaders.EternalDungeonLayerDatLoader
import tw.tsm.data.loaders.EternalDungeonPrizeDatLoader
import java.nio.file.Files
import java.nio.file.Path

class EternalDungeonDataTest {
    private fun file(name: String): ByteArray {
        val direct = Path.of("File.dat", "Data-ios", name)
        val path = if (Files.exists(direct)) direct else Path.of("..", "File.dat", "Data-ios", name)
        return Files.readAllBytes(path)
    }

    @Test
    fun eternalDungeonPacketsMatchLuaProtocol48() {
        assertArrayEquals(
            byteArrayOf(48, 1, 0, 3),
            EternalDungeonHandler.startResultPacket(3),
        )
        assertArrayEquals(
            byteArrayOf(48, 2, 0, 5, 0, 0, 0, 1),
            EternalDungeonHandler.fightResultPacket(5, 1),
        )
        assertArrayEquals(
            byteArrayOf(48, 3, 0),
            EternalDungeonHandler.registrationPacket(),
        )
        assertArrayEquals(
            byteArrayOf(48, 4, 0, 5, 0, 0, 0, 0, 0, 0, 0),
            EternalDungeonHandler.fastestRecordPacket(5),
        )
        assertEquals(15, ActivityHandler.minimumLevel(ActivityHandler.ETERNAL_DUNGEON_ACTIVITY_ID))
    }

    @Test
    fun realDvtEternalDungeonTablesMatchLuaBinaryContracts() {
        val layers = EternalDungeonLayerDatLoader.load(file("IOS_EternalDungeonLayer_C.dat"))
        val prizes = EternalDungeonPrizeDatLoader.load(file("IOS_EternalDungeonPrize_C.dat"))

        assertEquals(19, layers.size)
        assertEquals(30, prizes.size)

        assertEquals(listOf(0, 0, 0), layers.getValue(1).effects)
        assertEquals(2, layers.getValue(1).compare)
        assertEquals(listOf(8, 10), layers.getValue(1).needs)

        assertEquals(listOf(113, 0, 0), layers.getValue(30).effects)
        assertEquals(listOf(8, 10), layers.getValue(30).needs)

        assertEquals(
            listOf(35290 to 1, 33989 to 3),
            prizes.getValue(1).prizes.filter { it.itemId != 0 }.map { it.itemId to it.count },
        )
        assertEquals(
            listOf(35344 to 1, 32708 to 1, 65301 to 200),
            prizes.getValue(30).prizes.filter { it.itemId != 0 }.map { it.itemId to it.count },
        )
    }
}
