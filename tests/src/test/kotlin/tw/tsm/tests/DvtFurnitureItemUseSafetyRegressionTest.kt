package tw.tsm.tests

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.gamelogic.systems.ItemUseProcessor
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import java.nio.file.Files
import java.nio.file.Path

/** DVT Gia Vien furniture remains an entitlement, not a generic consumable. */
class DvtFurnitureItemUseSafetyRegressionTest {
    private fun definitions() = run {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_Item_C.dat")) }
        ItemDatLoader.load(Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_Item_C.dat")))
    }

    @Test
    fun allOriginalFurnitureItemsNeverDisappearThroughGenericUse() {
        val items = definitions()
        val furniture = items.filterValues { it.kind == 89 }
        assertEquals(397, furniture.size)
        val processor = ItemUseProcessor(
            mockk<PlayerStateManager>(relaxed = true),
            mockk<PetSystem>(relaxed = true),
            items,
        )
        for ((itemId, def) in furniture) {
            assertTrue(def.spare3 in 1..65535, "Missing furniture ID item=$itemId")
            for (petSlot in listOf(0, 1)) {
                val result = processor.processUseItem(31L, itemId, 1, petSlot, 0)
                assertFalse(result.success, "item=$itemId petSlot=$petSlot")
                assertFalse(result.consumed, "item=$itemId petSlot=$petSlot")
                assertTrue(result.rewardItems.isEmpty())
                assertTrue(result.attrChanges.isEmpty())
            }
        }
    }

    @Test
    fun fixtureGiftPackageRemainsKind38GiftBoxNotFurniture() {
        val items = definitions()
        assertEquals(38, items[33083]?.kind)
        assertEquals(48, items[33083]?.specialAbility)
        assertEquals(89, items[53881]?.kind)
        assertEquals(62381, items[53881]?.spare3)
    }
}
