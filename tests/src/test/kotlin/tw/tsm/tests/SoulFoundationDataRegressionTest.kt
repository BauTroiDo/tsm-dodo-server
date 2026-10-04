package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.NpcSoulDatLoader
import tw.tsm.data.loaders.SoulDatLoader
import tw.tsm.data.loaders.SoulLevelDatLoader
import java.nio.file.Files
import java.nio.file.Path

class SoulFoundationDataRegressionTest {
    private val ios = Path.of("File.dat", "Data-ios")

    @Test
    fun realDvtSoulTablesMatchLuaContracts() {
        val soulFile = ios.resolve("IOS_Soul_C.dat")
        val levelFile = ios.resolve("IOS_SoulLevel_C.dat")
        val npcFile = ios.resolve("IOS_NpcSoul_C.dat")
        assumeTrue(Files.isRegularFile(soulFile))
        assumeTrue(Files.isRegularFile(levelFile))
        assumeTrue(Files.isRegularFile(npcFile))

        val souls = SoulDatLoader.load(Files.readAllBytes(soulFile))
        assertEquals(4, souls.size)
        assertEquals(150, souls.getValue(0).startPoint)
        assertEquals(100, souls.getValue(0).basicLvLimit)
        assertEquals(listOf(1000, 1100, 1200, 1300, 1400, 1500), souls.getValue(0).convertRate)
        assertEquals(550, souls.getValue(3).startPoint)
        assertEquals(200, souls.getValue(3).basicLvLimit)
        assertEquals(listOf(3500, 3800, 4100, 4400, 4700, 5000), souls.getValue(3).convertRate)
        assertEquals(5, souls.values.maxOf { it.limitBreakthrough })
        assertEquals(200, souls.getValue(0).levelLimit(5))
        assertEquals(300, souls.getValue(3).levelLimit(5))

        val levels = SoulLevelDatLoader.load(Files.readAllBytes(levelFile))
        assertEquals(300, levels.size)
        assertEquals(2, levels.getValue(1).costForQuality(0).reel)
        assertEquals(2, levels.getValue(1).costForQuality(0).affCoin)
        assertEquals(103, levels.getValue(300).costForQuality(3).reel)
        assertEquals(5, levels.getValue(300).costForQuality(3).coin)
        assertEquals(2, levels.getValue(300).costForQuality(3).affCoin)

        val npcSouls = NpcSoulDatLoader.load(Files.readAllBytes(npcFile))
        assertEquals(94, npcSouls.size)
        assertEquals(46415, npcSouls.getValue(59501).npcId)
        assertEquals(0, npcSouls.getValue(59501).quality)
        assertEquals(46508, npcSouls.getValue(59594).npcId)
        assertEquals(3, npcSouls.getValue(59594).quality)
        assertEquals(mapOf(0 to 41, 1 to 20, 2 to 14, 3 to 19), npcSouls.values.groupingBy { it.quality }.eachCount())
    }

    @Test
    fun rejectsTrailingOrTruncatedSoulTables() {
        val soulFile = ios.resolve("IOS_Soul_C.dat")
        assumeTrue(Files.isRegularFile(soulFile))
        val valid = Files.readAllBytes(soulFile)

        assertThrows(IllegalArgumentException::class.java) {
            SoulDatLoader.load(valid + byteArrayOf(0))
        }
        assertThrows(Exception::class.java) {
            SoulDatLoader.load(valid.copyOf(valid.size - 1))
        }
    }
}
