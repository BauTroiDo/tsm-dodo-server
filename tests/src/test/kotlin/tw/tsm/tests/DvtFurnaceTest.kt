package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.response.FurnaceResponseSender
import tw.tsm.data.loaders.FurnaceSelectDatLoader
import tw.tsm.data.loaders.FurnaceSlotDatLoader
import tw.tsm.data.loaders.UISellDatLoader
import tw.tsm.domain.data.*
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*
import tw.tsm.protocol.PacketReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class DvtFurnaceTest {
    private fun root(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_FurnaceSlot_C.dat")) }

    private fun data(name: String): ByteArray =
        Files.readAllBytes(root().resolve("File.dat/Data-ios/$name"))

    private fun serverData(name: String): ByteArray =
        Files.readAllBytes(root().resolve("File.dat/Data/$name"))

    @Test
    fun `DVT furnace tables have exact distributions`() {
        val slots = FurnaceSlotDatLoader.load(data("IOS_FurnaceSlot_C.dat"))
        val selects = FurnaceSelectDatLoader.load(data("IOS_FurnaceSelect_C.dat"))

        assertEquals(2036, slots.size)
        assertEquals(42, selects.size)
        assertEquals(mapOf(1 to 430, 2 to 771, 3 to 835), slots.groupingBy { it.kind }.eachCount())
        assertTrue(slots.all { it.timedOpen == 0 })

        val selectCounts = selects.groupingBy { it.kind }.eachCount()
        assertEquals(mapOf(1 to 6, 2 to 6, 3 to 8, 4 to 8, 5 to 6, 6 to 8), selectCounts)
        for (kind in 1..6) {
            val rows = selects.filter { it.kind == kind }
            assertEquals(10_000, rows.sumOf { it.sample })
            assertTrue(rows.all { it.awardCounts.size == 6 && it.awardCounts.sum() == 8 })
        }
    }

    @Test
    fun `furnace refresh UISell data follows DVT 2_4_76 and keeps sell60 evidence blocked`() = runBlocking {
        val sellDefs = UISellDatLoader.loadSellDefs(serverData("UISell_C.dat"))
        val formulaDefs = UISellDatLoader.loadFormulaDefs(serverData("UISell_Formula_C.dat"))
        val refreshIds = setOf(24, 25, 33, 34, 59, 60)

        assertTrue(refreshIds.all { it in sellDefs })
        assertTrue(setOf(24, 25, 33, 34, 59).all { it in formulaDefs })
        assertFalse(60 in formulaDefs)

        val system = UISellSystem(
            sellDefs,
            formulaDefs,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        val expectedCosts = mapOf(24 to 100L, 25 to 5L, 33 to 80_000L, 34 to 69L, 59 to 50L)
        for ((sellId, cost) in expectedCosts) {
            val quote = system.query(1L, sellId)
            assertTrue(quote is UiSellQuote.Money, "sellId=$sellId quote=$quote")
            assertEquals(cost, (quote as UiSellQuote.Money).cost)
        }

        val blocked = system.query(1L, 60)
        assertTrue(blocked is UiSellQuote.Fail)
        assertEquals(UISellSystem.REASON_NO_DATA, (blocked as UiSellQuote.Fail).reason)
    }

    @Test
    fun `shop is stable and produces six categories with eight items`() = runBlocking {
        val slots = FurnaceSlotDatLoader.load(data("IOS_FurnaceSlot_C.dat"))
        val selects = FurnaceSelectDatLoader.load(data("IOS_FurnaceSelect_C.dat"))
        val itemDefs = slots.associate { it.itemId to mockk<ItemDef>(relaxed = true) }
        val uiSell = mockk<UISellSystem>()
        coEvery { uiSell.getPurchaseCount(77L, any()) } returns 0

        val system = FurnaceSystem(
            slots,
            selects,
            itemDefs,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            uiSell,
            today = { LocalDate.of(2026, 10, 3) },
        )

        val a = system.snapshot(77L)
        val b = system.snapshot(77L)
        assertEquals(a, b)
        assertEquals(6, a.categories.size)
        assertEquals((1..6).toList(), a.categories.map { it.kind })
        assertTrue(a.categories.all { it.items.size == 8 })
        assertTrue(a.categories.none { it.isCrit })
        assertTrue(a.categories.flatMap { it.items }.all { it.itemId in itemDefs })
    }

    @Test
    fun `buy consumes correct debris and marks purchased flag`() = runBlocking {
        val slots = (1..3).flatMap { kind ->
            (1..8).map { n ->
                val itemId = kind * 1000 + n
                FurnaceSlotDef(itemId, kind, 1, itemId, 0, 0, 0, 0, 0, 0, 0)
            }
        }
        val selects = (1..6).map { kind ->
            FurnaceSelectDef(kind, kind, 10_000, listOf(8, 0, 0, 0, 0, 0))
        }
        val itemDefs = slots.associate {
            it.itemId to mockk<ItemDef>(relaxed = true) {
                every { furnaceCount } returns 10L
            }
        }
        val inventory = mockk<InventorySystem>()
        val counts = mockk<RoleCountSystem>()
        val flags = mockk<BitFlagSystem>()
        val uiSell = mockk<UISellSystem>()
        coEvery { uiSell.getPurchaseCount(1L, any()) } returns 0
        every { counts.getValue(1L, RoleCountSystem.NPC_DEBRIS) } returns 100
        every { counts.getMaxValue(1L, RoleCountSystem.NPC_DEBRIS) } returns 0
        every { counts.setValue(any(), any(), any(), any()) } just Runs
        coEvery { counts.saveCounts(1L) } just Runs
        coEvery { flags.getSetFlagIds(1L) } returns emptySet()
        coEvery { flags.setFlag(any(), any()) } just Runs
        every { inventory.canAddItemToBag(any(), any(), any()) } returns true
        every { inventory.getBag(1L) } returns emptyMap()
        coEvery { inventory.addItemsToBagExact(1L, any(), 1) } answers {
            listOf(0 to ThingData(itemId = secondArg(), quantity = 1))
        }

        val system = FurnaceSystem(
            slots, selects, itemDefs, inventory, counts, flags, uiSell,
        )
        val first = system.snapshot(1L).categories.first { it.kind == 1 }.items.first()
        val result = system.buy(1L, 1, 1, first.itemId)

        assertTrue(result is FurnaceBuyResult.Success)
        val success = result as FurnaceBuyResult.Success
        assertEquals(FurnaceSystem.purchaseFlag(1, 1), success.flagId)
        assertEquals(90, success.roleCountValue)
        verify { counts.setValue(1L, RoleCountSystem.NPC_DEBRIS, 90, 0) }
        coVerify { flags.setFlag(1L, FurnaceSystem.purchaseFlag(1, 1)) }
    }

    @Test
    fun `dismantle consumes quantity and credits furnaceCount per item`() = runBlocking {
        val def = mockk<ItemDef>(relaxed = true) {
            every { furnaceKind } returns 1
            every { furnaceCount } returns 7L
            every { specialAbility } returns 0
        }
        val inventory = mockk<InventorySystem>()
        val counts = mockk<RoleCountSystem>()
        val flags = mockk<BitFlagSystem>(relaxed = true)
        val uiSell = mockk<UISellSystem>(relaxed = true)
        every { inventory.getBagItem(9L, 4) } returns ThingData(itemId = 1234, quantity = 3)
        every { inventory.getBag(9L) } returns mapOf(4 to ThingData(itemId = 1234, quantity = 3))
        coEvery { inventory.removeBagItemsExact(9L, listOf(4 to 2)) } returns true
        every { counts.getValue(9L, RoleCountSystem.NPC_DEBRIS) } returns 10
        every { counts.getMaxValue(9L, RoleCountSystem.NPC_DEBRIS) } returns 0
        every { counts.setValue(any(), any(), any(), any()) } just Runs
        coEvery { counts.saveCounts(9L) } just Runs

        val system = FurnaceSystem(
            emptyList(), emptyList(), mapOf(1234 to def),
            inventory, counts, flags, uiSell,
        )
        val result = system.dismantle(9L, listOf(4 to 2))
        assertTrue(result is FurnaceDismantleResult.Success)
        val success = result as FurnaceDismantleResult.Success
        assertEquals(14, success.entries.single().chipCount)
        assertEquals(24, success.roleCountValue)
        verify { counts.setValue(9L, RoleCountSystem.NPC_DEBRIS, 24, 0) }
    }

    @Test
    fun `S089 packets follow Lua byte contract`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender> {
            every { send(capture(packets)) } just Runs
        }
        val sender = FurnaceResponseSender()
        val snapshot = FurnaceShopSnapshot(
            baseRate = 1.0,
            activeRate = 1.25,
            categories = listOf(
                FurnaceShopCategory(
                    kind = 1,
                    isCrit = false,
                    items = listOf(FurnaceShopItem(51506, 1), FurnaceShopItem(51589, 2)),
                ),
            ),
        )

        sender.sendShopData(session, snapshot, openUi = true)
        var r = PacketReader(packets.removeFirst())
        assertEquals(89, r.readUByte())
        assertEquals(1, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(1, r.readUByte())
        assertEquals(1.0, r.readDoubleLE())
        assertEquals(1.25, r.readDoubleLE())
        assertEquals(1, r.readUByte())
        assertEquals(1, r.readUByte())
        assertEquals(1, r.readUByte())
        assertEquals(0, r.readUByte())
        assertEquals(2, r.readUByte())
        assertEquals(51506, r.readUShortLE())
        assertEquals(1, r.readIntLE())
        assertEquals(51589, r.readUShortLE())
        assertEquals(2, r.readIntLE())
        assertEquals(0, r.remaining)

        sender.sendBuyResult(session, 1, 51506)
        r = PacketReader(packets.removeFirst())
        assertEquals(listOf(89, 2, 0, 1), List(4) { r.readUByte() })
        assertEquals(51506, r.readUShortLE())
        assertEquals(0, r.remaining)

        sender.sendDismantleSuccess(
            session,
            listOf(FurnaceDismantleEntry(51506, 1, 37)),
        )
        r = PacketReader(packets.removeFirst())
        assertEquals(listOf(89, 3, 0, 0, 1), List(5) { r.readUByte() })
        assertEquals(51506, r.readUShortLE())
        assertEquals(1, r.readUByte())
        assertEquals(37L, r.readUIntLE())
        assertEquals(0, r.remaining)
    }
}
