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

/**
 * All 114 source DVT Treasure kind98 cards are directUse=1, specialAbility245.
 * Without a proven bank transfer a generic item-use result MUST NOT consume
 * the card in C023-015 (including forged followNpc use packets).
 */
class DvtTreasureCardItemUseSafetyRegressionTest {
    private fun definitions() = run {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_Item_C.dat")) }
        ItemDatLoader.load(Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_Item_C.dat")))
    }

    @Test
    fun all114OriginalTreasureCardsAreNeverConsumedAsGenericDirectUse() {
        val items = definitions()
        val cards = items.filterValues { it.kind == 98 }
        assertEquals(114, cards.size)
        val processor = ItemUseProcessor(
            mockk<PlayerStateManager>(relaxed = true),
            mockk<PetSystem>(relaxed = true),
            items,
        )
        for ((itemId, def) in cards) {
            assertEquals(245, def.specialAbility, "original item=$itemId")
            assertEquals(1, def.directUse, "original item=$itemId")
            assertTrue(def.skillLink in 1..114)
            for (followIndex in listOf(0,1)) {
                val result = processor.processUseItem(
                    characterId=10L,
                    itemId=itemId,
                    quantity=1,
                    followIndex=followIndex,
                    useType=0,
                )
                assertFalse(result.consumed, "ItemId=$itemId follow=$followIndex")
                assertFalse(result.success, "ItemId=$itemId follow=$followIndex")
                assertTrue(result.attrChanges.isEmpty())
                assertTrue(result.rewardItems.isEmpty())
            }
        }
    }

    @Test
    fun evenCorruptedTreasureAbilityCannotFallThroughAndDestroyCard() {
        val items = definitions()
        val original = items[25601]!!
        val copy = original.copy(specialAbility=0, directUse=0)
        val processor=ItemUseProcessor(
            mockk<PlayerStateManager>(relaxed=true),
            mockk<PetSystem>(relaxed=true),
            mapOf(25601 to copy),
        )
        for (index in listOf(0,1)) {
            val result=processor.processUseItem(10L,25601,5,index,0)
            assertFalse(result.success)
            assertFalse(result.consumed)
        }
    }

    @Test
    fun nonTreasureCardKindIsNotWronglyCapturedByTheNewGuard() {
        val items=definitions()
        // Soul item 59501 already has its own source-grounded fail-closed path.
        assertNotEquals(98, items[59501]!!.kind)
        assertEquals(240,items[59501]!!.specialAbility)
        // Opening a normal BlissBag still uses a completely different kind.
        assertNotEquals(98,items[34617]!!.kind)
    }
}
