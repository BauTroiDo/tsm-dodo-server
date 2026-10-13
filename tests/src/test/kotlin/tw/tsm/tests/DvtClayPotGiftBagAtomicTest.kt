package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.BlissBagDatLoader
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.GiftBagAtomicPlanner
import java.nio.file.Files
import java.nio.file.Path

class DvtClayPotGiftBagAtomicTest {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_Item_C.dat")) }
    private val items by lazy {
        ItemDatLoader.load(Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_Item_C.dat")))
    }
    private val bags by lazy {
        BlissBagDatLoader.load(Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_BlissBag_C.dat")))
    }
    private fun plan(
        contents: Map<Int, ThingData>,
        size: Int,
        sourceId: Int = 46285,
        quantity: Int = 1,
        rewards: List<Pair<Int, Int>> = listOf(26460 to 25, 26461 to 25, 32043 to 1),
    ) = GiftBagAtomicPlanner.plan(contents, size, 0, sourceId, quantity, rewards, items)

    @Test
    fun dvtOriginalBagsProvideRealClayPotWithoutInventedDropRate() {
        assertEquals("Nồi Đất", items[32043]?.name)
        assertEquals("Nồi Đất Nhỏ", items[32015]?.name)
        for (bag in listOf(46285, 32919, 32920)) {
            assertEquals(EItemUseKind.GIFT_BOX, items[bag]?.specialAbility)
            assertNotNull(bags[bag])
        }
        val original = requireNotNull(bags[46285])
        assertEquals(3, original.kindCount)
        val group3 = original.items.filter { it.kind == 3 }
        assertEquals(10_000, group3.sumOf { it.pr })
        assertEquals(2500, group3.single { it.itemId == 32043 }.pr)
        assertEquals(1, group3.single { it.itemId == 32043 }.count)
        assertTrue(group3.none { it.itemId == 32015 })
        for (bag in listOf(32919, 32920)) {
            val def = requireNotNull(bags[bag])
            assertEquals(1, def.items.size)
            assertEquals(32043, def.items.single().itemId)
            assertEquals(10_000, def.items.single().pr)
        }
    }

    @Test
    fun fullInventoryMustRejectBeforeConsumingAnyGiftBag() {
        val before = mapOf(0 to ThingData(itemId = 46285, quantity = 1))
        assertNull(plan(before, 1))
        assertEquals(1, before[0]?.quantity)
        assertNull(plan(before, 2))
        assertEquals(1, before[0]?.quantity)
    }

    @Test
    fun exactSlotsAllowAtomicSourceConsumptionAndThreeRewards() {
        val before = mapOf(0 to ThingData(itemId = 46285, quantity = 1))
        val changes = requireNotNull(plan(before, 3))
        val after = before.toMutableMap()
        changes.forEach { (slot, value) -> if (value == null) after.remove(slot) else after[slot] = value }
        assertEquals(3, after.size)
        assertEquals(25, after.values.single { it.itemId == 26460 }.quantity)
        assertEquals(25, after.values.single { it.itemId == 26461 }.quantity)
        assertEquals(1, after.values.single { it.itemId == 32043 }.quantity)
        assertTrue(after.values.none { it.itemId == 46285 })
    }

    @Test
    fun partialBagStackNeverCountsAsVacantSlot() {
        val before = mapOf(0 to ThingData(itemId = 46285, quantity = 2))
        assertNull(plan(before, 3))
        val after = requireNotNull(plan(before, 4))
        assertEquals(1, after[0]?.quantity)
        assertEquals(46285, after[0]?.itemId)
    }

    @Test
    fun alreadyOwnedClayPotsCanStackAndUnrecognizedPrizeRejects() {
        val before = mapOf(
            0 to ThingData(itemId = 46285, quantity = 1),
            1 to ThingData(itemId = 32043, quantity = 29),
        )
        val changes = requireNotNull(plan(before, 4))
        assertEquals(30, changes[1]?.quantity)
        assertEquals(32043, changes[1]?.itemId)
        assertNull(plan(before, 4, rewards = listOf(999999 to 1)))
        assertNull(plan(before, 4, rewards = listOf(32043 to 0)))
        assertEquals(29, before[1]?.quantity)
    }
}
