package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*
import java.util.UUID

/**
 * Frozen item uploads in the currently unreceipted RAM-only C025 flow.
 * SQL gate/planner/receipt remain intentionally UNWIRED.
 */
class DvtTradeFrozenItemOfferTest {
    private val firstId = 310101L
    private val secondId = 310102L
    private val firstChar = 310101L
    private val secondChar = 310102L
    private val orig = ThingData(
        itemId = 501, quantity = 2, damage = 11, affix1 = 42, mythLevel = 2,
        myth = listOf(MythAttribute(1, 10, 20), MythAttribute(2, 11, 21),
            MythAttribute(3, 12, 22), MythAttribute(4, 13, 23))
    )
    private val other = ThingData(itemId = 502, quantity = 3, reinforced = 4)

    @Test
    fun deepCaptureProtectsMutableMythAttributesAndOriginalSlotOrder() {
        val originalAttrs = MutableList(4) { MythAttribute(it, it + 10, it + 20) }
        val bag = mutableMapOf(
            7 to orig.copy(myth = originalAttrs),
            3 to other
        )
        val stored = TradeItemOfferSnapshot.capture(listOf(7, 3)) { bag[it] }
        assertNotNull(stored)
        val frozen = stored!!
        val old = frozen.getValue(7)
        originalAttrs[0] = MythAttribute(99, 99, 99)
        assertEquals(MythAttribute(0, 10, 20), old.myth[0])
        assertFalse(TradeItemOfferSnapshot.matches(listOf(7, 3), frozen) { bag[it] })
        assertEquals(listOf(501, 502),
            TradeItemOfferSnapshot.orderedItems(listOf(7, 3), frozen)?.map { it.itemId })
        assertEquals(orig.copy(myth = List(4) { MythAttribute(it, it + 10, it + 20) }), old)
    }

    @Test
    fun duplicateForgedMissingAndChangedOfferMustFailClosed() {
        val bag = mutableMapOf(7 to orig, 3 to other)
        for (invalid in listOf(listOf(7, 7), listOf(-1), listOf(256), listOf(8))) {
            assertNull(TradeItemOfferSnapshot.capture(invalid) { bag[it] })
        }
        val snapshot = TradeItemOfferSnapshot.capture(listOf(7, 3)) { bag[it] }!!
        assertTrue(TradeItemOfferSnapshot.matches(listOf(7, 3), snapshot) { bag[it] })
        for (altered in listOf(
            orig.copy(quantity = 1),
            orig.copy(damage = 99),
            orig.copy(affix1 = 43),
            orig.copy(myth = orig.myth.toMutableList().also {
                it[1] = MythAttribute(2, 99, 21)
            }),
            orig.copy(isLocked = true),
        )) {
            bag[7] = altered
            assertFalse(TradeItemOfferSnapshot.matches(listOf(7, 3), snapshot) { bag[it] })
        }
        assertFalse(TradeItemOfferSnapshot.matches(listOf(7, 7), snapshot) { bag[it] })
        assertFalse(TradeItemOfferSnapshot.matches(listOf(7), snapshot) { bag[it] })
        assertNull(TradeItemOfferSnapshot.orderedItems(listOf(7), snapshot))
        assertEquals(orig, snapshot.getValue(7))
    }

    @Test
    fun emptyOfferValidButMissingOrZeroStackInvalid() {
        assertEquals(emptyMap<Int, ThingData>(),
            TradeItemOfferSnapshot.capture(emptyList()) { null })
        assertTrue(TradeItemOfferSnapshot.matches(emptyList(), emptyMap()) { null })
        assertNull(TradeItemOfferSnapshot.capture(listOf(0)) {
            ThingData(itemId = 501, quantity = 0)
        })
        assertNull(TradeItemOfferSnapshot.capture(listOf(0)) { null })
    }

    @Test
    fun everyServerTradeSessionGetsIndependentCanonicalUuid() {
        val x = TradeSystem.TradeSession(firstId, secondId, TradeSystem.MODE_ITEM)
        val y = TradeSystem.TradeSession(firstId, secondId, TradeSystem.MODE_ITEM)
        assertNotEquals(x.operationId, y.operationId)
        assertEquals(UUID.fromString(x.operationId).toString(), x.operationId)
        assertEquals(UUID.fromString(y.operationId).toString(), y.operationId)
        assertEquals(TradeSystem.MODE_ITEM, x.mode)
    }

    private data class Harness(
        val trade: TradeSystem,
        val sender: ResponseSender,
        val bagFirst: MutableMap<Int, ThingData>,
        val bagSecond: MutableMap<Int, ThingData>,
        val a: GameSession,
        val b: GameSession,
        val inventory: InventorySystem,
    )

    private fun harness(): Harness {
        val bagFirst = mutableMapOf(7 to orig)
        val bagSecond = mutableMapOf(3 to other)
        val a = mockk<GameSession>(relaxed = true) {
            every { playerId } returns firstId
            every { roleId } returns firstChar.toInt()
            every { mapId } returns 12001
            every { characterName } returns "First"
        }
        val b = mockk<GameSession>(relaxed = true) {
            every { playerId } returns secondId
            every { roleId } returns secondChar.toInt()
            every { mapId } returns 12001
            every { characterName } returns "Second"
        }
        val world = mockk<WorldManager>(relaxed = true)
        every { world.getPlayerById(firstId) } returns a
        every { world.getPlayerById(secondId) } returns b

        val sender = mockk<ResponseSender>(relaxed = true)
        val inventory = mockk<InventorySystem>(relaxed = true)
        every { inventory.getBagItem(firstChar, any()) } answers {
            bagFirst[secondArg<Int>()]
        }
        every { inventory.getBagItem(secondChar, any()) } answers {
            bagSecond[secondArg<Int>()]
        }
        val money = mockk<MoneySystem>(relaxed = true)
        every { money.getGold(any()) } returns 1000
        val battle = mockk<BattleManager>(relaxed = true)
        every { battle.isInBattle(any()) } returns false
        val def501 = mockk<ItemDef> { every { restrict } returns 0 }
        val def502 = mockk<ItemDef> { every { restrict } returns 0 }
        val trade = TradeSystem(
            inventory, money, world, sender,
            mapOf(501 to def501, 502 to def502),
            mockk<PetSystem>(relaxed = true),
            battle, mockk<StatCalculator>(relaxed = true)
        )
        trade.requestTrade(a, secondId)
        return Harness(trade, sender, bagFirst, bagSecond, a, b, inventory)
    }

    @Test
    fun realC025UploadDisplaysFrozenOriginalEvenIfBAGChangedBeforePartnerUpload() = runBlocking {
        val h = harness()
        h.trade.uploadItems(firstId, 15, listOf(7))
        h.bagFirst[7] = orig.copy(quantity = 99, affix1 = 97)
        h.trade.uploadItems(secondId, 30, listOf(3))
        verify(exactly = 1) { h.sender.sendTradeConfirm(h.b, 15, listOf(orig)) }
        verify(exactly = 1) { h.sender.sendTradeConfirm(h.a, 30, listOf(other)) }
        assertTrue(h.trade.isInTrade(firstId))
    }

    @Test
    fun realC025FinalConfirmRejectsSwappedStackBeforeAnyRAMMutation() = runBlocking {
        val h = harness()
        h.trade.uploadItems(firstId, 15, listOf(7))
        h.trade.uploadItems(secondId, 30, listOf(3))
        h.bagFirst[7] = orig.copy(mythLevel = 3, quantity = 1)
        h.trade.confirmOrCancel(firstId, 1)
        h.trade.confirmOrCancel(secondId, 1)
        verify(exactly = 1) { h.sender.sendTradeCancel(h.a, TradeSystem.REASON_SYSTEM) }
        verify(exactly = 1) { h.sender.sendTradeCancel(h.b, TradeSystem.REASON_SYSTEM) }
        verify(exactly = 0) { h.inventory.setBagItemSync(any(), any(), any()) }
        verify(exactly = 0) { h.inventory.addItemDirectSync(any(), any()) }
        assertFalse(h.trade.isInTrade(firstId))
        assertFalse(h.trade.isInTrade(secondId))
    }

    @Test
    fun replayedUploadCannotReplacePreviouslyAcceptedFrozenOffer() = runBlocking {
        val h = harness()
        h.trade.uploadItems(firstId, 15, listOf(7))
        h.bagFirst[7] = orig.copy(quantity = 8)
        // This upload is out of phase (already Uploaded) and must not change
        // the frozen original offer or the amount sent to partner.
        h.trade.uploadItems(firstId, 400, listOf(7))
        h.trade.uploadItems(secondId, 20, listOf(3))
        verify(exactly = 1) { h.sender.sendTradeConfirm(h.b, 15, listOf(orig)) }
    }
}
