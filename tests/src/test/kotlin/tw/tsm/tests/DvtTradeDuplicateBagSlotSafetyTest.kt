package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.TradeSystem

/** C025-002 duplicate BAG slots must never reach live item exchange. */
class DvtTradeDuplicateBagSlotSafetyTest {
    @Test
    fun uniqueOriginalU8IndicesAreAccepted() {
        assertTrue(TradeSystem.hasUniqueTradeBagSlots(emptyList()))
        assertTrue(TradeSystem.hasUniqueTradeBagSlots(listOf(0,1,2,255)))
        assertTrue(TradeSystem.hasUniqueTradeBagSlots(listOf(17)))
    }

    @Test
    fun repeatedSlotThatCouldCloneOneStackIsRejected() {
        assertFalse(TradeSystem.hasUniqueTradeBagSlots(listOf(7,7)))
        assertFalse(TradeSystem.hasUniqueTradeBagSlots(listOf(1,2,3,1)))
        assertFalse(TradeSystem.hasUniqueTradeBagSlots(listOf(255,0,255)))
    }

    @Test
    fun forgedOutOfProtocolRangeIndicesCannotPass() {
        assertFalse(TradeSystem.hasUniqueTradeBagSlots(listOf(-1)))
        assertFalse(TradeSystem.hasUniqueTradeBagSlots(listOf(256)))
        assertFalse(TradeSystem.hasUniqueTradeBagSlots(listOf(0,1,65536)))
    }
}
