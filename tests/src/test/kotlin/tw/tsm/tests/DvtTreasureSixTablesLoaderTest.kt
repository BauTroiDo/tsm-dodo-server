package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.TreasureDatLoader
import java.nio.file.Files
import java.nio.file.Path

/** Source data, not player progress: no Treasure unlock/reward side effects. */
class DvtTreasureSixTablesLoaderTest {
    private fun data(name: String): ByteArray {
        val p = Path.of("File.dat", "Data-ios", "IOS_" + name + "_C.dat")
        return Files.readAllBytes(if (Files.exists(p)) p else Path.of("..").resolve(p))
    }

    private fun catalog() = TreasureDatLoader.load(
        data("TreasureActivityCriteria"), data("TreasureGroupInfo"),
        data("TreasureGroupLV"), data("TreasureLv"),
        data("TreasureRoleCriteria"), data("TreasureStar"),
    )

    @Test
    fun exactlySixOriginalTablesDecodeAtPackedWidths() {
        val c = catalog()
        assertEquals(86, c.activities.size)
        assertEquals(29, c.groups.size)
        assertEquals(290, c.groupLevels.size)
        assertEquals(1140, c.levels.size)
        assertEquals(18, c.roles.size)
        assertEquals(684, c.stars.size)
        assertEquals((1..114).toSet(), c.levels.map { it.id }.toSet())
        assertEquals((1..114).toSet(), c.stars.map { it.id }.toSet())
    }

    @Test
    fun originalFirstAndLastRecordsPreserveSignedBonusValuesAndCosts() {
        val c = catalog()
        assertEquals(20446L, c.activities.first().stringId)
        assertEquals(listOf(1,2,3,0,0,0), c.groups.first().cards)
        assertEquals(114, c.levels.last().id)
        assertEquals(10, c.levels.last().level)
        assertEquals(600, c.levels.last().materials)
        assertEquals(1_000_000, c.levels.last().gold)
        assertEquals(114, c.stars.last().id)
        assertEquals(6, c.stars.last().star)
        assertEquals(10, c.stars.last().materials)
        assertEquals(100_000, c.stars.last().gold)
    }

    @Test
    fun levelZeroToOneAndOneToTwoUsePerCardOriginalSourceCosts() {
        val c = catalog()
        val first = c.nextLevel(1,0)!!
        assertEquals(1, first.copies)
        assertEquals(20, first.materials)
        assertEquals(100_000, first.gold)
        val second = c.nextLevel(1,1)!!
        assertEquals(2, second.copies)
        assertEquals(40, second.materials)
        assertEquals(200_000, second.gold)
        assertEquals(1, c.investedCopies(1,1))
        assertEquals(45, c.investedCopies(114,9))
        assertNull(c.nextLevel(1,10))
        assertEquals(1, c.nextStarData(1,0)!!.materials)
        assertNull(c.nextStarData(1,6))
    }

    @Test
    fun corruptTableLayoutFailsClosedBeforeReadingAnyRows() {
        val original=data("TreasureLv")
        assertThrows(IllegalArgumentException::class.java) {
            TreasureDatLoader.levels(original.copyOf(original.size-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasureDatLoader.levels(original+byteArrayOf(0))
        }
    }

    @Test
    fun maliciousOrAccidentalDuplicatedSourceLevelIsRejected() {
        val input=data("TreasureLv")
        // second record level byte at first header4 + fixed row22 + id2
        val bad=input.copyOf()
        bad[4+22+2]=1
        assertThrows(IllegalArgumentException::class.java) {
            TreasureDatLoader.load(
                data("TreasureActivityCriteria"),data("TreasureGroupInfo"),
                data("TreasureGroupLV"),bad,data("TreasureRoleCriteria"),
                data("TreasureStar")
            )
        }
    }
}
