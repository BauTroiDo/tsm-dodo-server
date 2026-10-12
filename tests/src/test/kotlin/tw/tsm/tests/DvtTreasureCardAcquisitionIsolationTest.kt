package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.data.loaders.TreasureCardSourceIndex
import tw.tsm.data.loaders.TreasureDatLoader
import tw.tsm.domain.interfaces.repositories.TreasureState
import tw.tsm.domain.valueobjects.ThingData
import java.nio.file.Files
import java.nio.file.Path

/** Original client has separate bag item counts and persistent S123 Treasure count. */
class DvtTreasureCardAcquisitionIsolationTest {
    private fun source(name: String): ByteArray {
        val p = Path.of("File.dat", "Data-ios", "IOS_" + name + "_C.dat")
        return Files.readAllBytes(if (Files.exists(p)) p else Path.of("..").resolve(p))
    }
    private fun curves() = TreasureDatLoader.load(
        source("TreasureActivityCriteria"), source("TreasureGroupInfo"),
        source("TreasureGroupLV"), source("TreasureLv"),
        source("TreasureRoleCriteria"), source("TreasureStar"),
    )
    private fun index(catalog: TreasureDatLoader.Catalog) =
        TreasureCardSourceIndex.fromSource(ItemDatLoader.load(source("Item")),catalog)

    @Test
    fun allOriginal114TreasureItemLinksAreExactAndBijective() {
        val catalog=curves()
        val index=index(catalog)
        for (treasureId in 1..114) {
            val itemId=treasureId+25600
            assertEquals(itemId,index.itemIdFor(treasureId))
            assertEquals(treasureId,index.treasureIdFor(itemId))
        }
        assertNull(index.treasureIdFor(29869))
        assertNull(index.treasureIdFor(29870))
        assertNull(index.treasureIdFor(65351))
        assertNull(index.itemIdFor(0))
        assertNull(index.itemIdFor(115))
    }

    @Test
    fun bagCardsAreNotAlsoBankedInS123AndCannotAuthorizeAnUpgrade() {
        val catalog=curves()
        val index=index(catalog)
        val bag=mapOf(
            3 to ThingData(itemId=25601,quantity=100),
            7 to ThingData(itemId=25601,quantity=25),
            8 to ThingData(itemId=29869,quantity=200),
            9 to ThingData(itemId=25602,quantity=1),
        )
        assertEquals(125L,index.unbankedBagCopies(bag,1))
        assertEquals(1L,index.unbankedBagCopies(bag,2))
        assertEquals(0L,index.unbankedBagCopies(bag,3))
        // S123 state not legitimately banked -> no spendable cards, even
        // with 125 Item_C Treasure items in the normal player bag.
        assertEquals(0,index.bankedSpendableCopies(null,1,catalog))
        val state=TreasureState(1,0,0,0,0,0)
        assertEquals(0,index.bankedSpendableCopies(state,1,catalog))
    }

    @Test
    fun bankedCopiesFollowOriginalCumulativeSourceLevelsNotBagItems() {
        val catalog=curves()
        val index=index(catalog)
        val alreadyInvested=TreasureState(1,4,2,0,0,2)
        // Source Lv1 needs 1 copy, Lv2 needs 2; 4 banked => 1 available.
        assertEquals(1,index.bankedSpendableCopies(alreadyInvested,1,catalog))
        assertEquals(0L,index.unbankedBagCopies(emptyMap(),1))
        assertThrows(IllegalArgumentException::class.java) {
            index.bankedSpendableCopies(TreasureState(1,2,2,0,0,2),1,catalog)
        }
        assertThrows(IllegalArgumentException::class.java) {
            index.bankedSpendableCopies(TreasureState(2,4,2,0,0,2),1,catalog)
        }
    }

    @Test
    fun corruptedItemCatalogCannotReassignOrDuplicateTreasureIds() {
        val catalog=curves()
        val definitions=ItemDatLoader.load(source("Item"))
        val src=definitions[25601]!!
        assertThrows(IllegalArgumentException::class.java) {
            TreasureCardSourceIndex.fromSource(definitions - 25601,catalog)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasureCardSourceIndex.fromSource(
                definitions + (25601 to src.copy(skillLink=2)),catalog
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasureCardSourceIndex.fromSource(
                definitions + (25601 to src.copy(kind=97)),catalog
            )
        }
    }
}
