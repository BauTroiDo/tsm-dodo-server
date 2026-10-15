package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.TradeItemSnapshotPlanner
import tw.tsm.gamelogic.systems.TradeItemSnapshotPlanner.Failure
import tw.tsm.gamelogic.systems.TradeItemSnapshotPlanner.Result
import tw.tsm.gamelogic.systems.TradeItemSnapshotPlanner.Side

/** Source-only full BAG+gold planner: no actual C025/SQL/packet integration. */
class DvtTradeItemSnapshotPlannerTest {
    private val a = 100001L
    private val b = 100002L
    private val nft = ThingData(
        itemId = 502,
        quantity = 2,
        damage = 14,
        element = 8,
        reinforced = 7,
        mythLevel = 3,
        myth = listOf(MythAttribute(1, 50, 100), MythAttribute(2, 40, 90),
            MythAttribute(3, 30, 80), MythAttribute(4, 20, 70)),
    )
    private val simple = ThingData(itemId = 501, quantity = 1, affix1 = 77)
    private val coin = ThingData(itemId = 503, quantity = 9999)

    private fun def(
        restrict: Int = 0,
        restrict2: Int = 0,
        kind: Int = 0,
        fitType: Int = 0,
    ): ItemDef {
        val item = mockk<ItemDef>()
        every { item.restrict } returns restrict
        every { item.restrict2 } returns restrict2
        every { item.kind } returns kind
        every { item.fitType } returns fitType
        return item
    }

    private fun planner(
        aDef: ItemDef = def(),
        bDef: ItemDef = def(),
        cDef: ItemDef = def(),
    ) = TradeItemSnapshotPlanner(mapOf(501 to aDef, 502 to bDef, 503 to cDef))

    private fun side(
        id: Long, bag: Map<Int, ThingData>, slots: List<Int>, gold: Int,
        offer: Int = 0, cap: Int = 250, auction: Int = 23,
    ) = Side(id, bag, slots.associateWith { bag.getValue(it) }, slots, gold, offer, auction, cap)

    private fun accepted(result: Result): Result.Accepted {
        assertTrue(result is Result.Accepted, "expected accepted trade, got $result")
        return result as Result.Accepted
    }

    @Test
    fun fullBagSwapSucceedsOnlyAfterBothOutgoingSlotsAreReleased() {
        val first = side(a, mapOf(0 to simple, 1 to coin), listOf(0), 1000, cap = 2)
        val second = side(b, mapOf(0 to nft, 1 to coin), listOf(0), 1200, cap = 2)
        val plan = accepted(planner().plan(first, second))
        assertEquals(nft, plan.first.afterBag[0])
        assertEquals(simple, plan.second.afterBag[0])
        assertEquals(coin, plan.first.afterBag[1])
        assertEquals(coin, plan.second.afterBag[1])
        assertEquals(plan.first.beforeBag, first.beforeBag)
        assertEquals(plan.second.beforeBag, second.beforeBag)
    }

    @Test
    fun expandedBagSlotsBeyondLegacy250AreConsideredForReceiving() {
        val full = (0 until 250).associateWith { coin }
        val first = side(a, full, emptyList(), 200, cap = 255)
        val second = side(b, mapOf(0 to simple), listOf(0), 200)
        val plan = accepted(planner().plan(first, second))
        assertEquals(simple, plan.first.afterBag[250])
        assertEquals(251, plan.first.afterBag.size)
        assertEquals(emptyMap<Int, ThingData>(), plan.second.afterBag)
    }

    @Test
    fun incomingQuantityAndNftMetadataAreNeverMergedOrRecreated() {
        val first = side(a, mapOf(0 to nft), listOf(0), 100, offer = 20)
        val second = side(b, mapOf(1 to simple), listOf(1), 400, offer = 40)
        val plan = accepted(planner().plan(first, second))
        assertEquals(nft, plan.second.afterBag[0])
        assertEquals(simple, plan.first.afterBag[0])
        assertEquals(nft.myth, plan.second.afterBag.getValue(0).myth)
        assertEquals(120, plan.first.afterGold)
        assertEquals(380, plan.second.afterGold)
        assertEquals(23, plan.first.auctionGold)
        assertEquals(23, plan.second.auctionGold)
        assertEquals(100, plan.first.beforeGold)
        assertEquals(400, plan.second.beforeGold)
    }

    @Test
    fun duplicateOutOfRangeAndForgedSlotListsAreRefusedBeforeMutation() {
        val first = side(a, mapOf(7 to simple), listOf(7), 100)
        val second = side(b, emptyMap(), emptyList(), 100)
        val valid = planner()
        for (bad in listOf(
            first.copy(offeredSlots = listOf(7, 7)),
            first.copy(offeredSlots = listOf(257)),
            first.copy(offeredSlots = listOf(3)),
            first.copy(offeredSlots = emptyList()),
        )) {
            assertEquals(Result.Rejected(Failure.INVALID_OFFER, a), valid.plan(bad, second))
        }
        assertEquals(mapOf(7 to simple), first.beforeBag)
        assertEquals(ThingData(itemId = 501, quantity = 1, affix1 = 77), simple)
    }

    @Test
    fun staleOfferIncludesQuantityDamageMythAttributesAndLockedFlag() {
        val orig = side(a, mapOf(0 to nft), listOf(0), 500)
        val empty = side(b, emptyMap(), emptyList(), 500)
        for (changed in listOf(
            nft.copy(quantity = 1), nft.copy(damage = 13),
            nft.copy(myth = nft.myth.toMutableList().also { it[0] = MythAttribute(1, 51, 100) }),
            nft.copy(isLocked = true),
        )) {
            assertEquals(
                Result.Rejected(Failure.STALE_OFFER, a),
                planner().plan(orig.copy(beforeBag = mapOf(0 to changed)), empty),
            )
        }
    }

    @Test
    fun insufficientFundsAndReceivingGoldOverflowFailClosed() {
        val emptyA = side(a, emptyMap(), emptyList(), 10, offer = 11)
        val emptyB = side(b, emptyMap(), emptyList(), 10)
        assertEquals(
            Result.Rejected(Failure.INSUFFICIENT_GOLD, a),
            planner().plan(emptyA, emptyB),
        )
        val rich = emptyA.copy(beforeGold = ItemConstants.MAX_GOLD, offeredGold = 0)
        val payer = emptyB.copy(offeredGold = 5)
        assertEquals(
            Result.Rejected(Failure.GOLD_OVERFLOW),
            planner().plan(rich, payer),
        )
        assertEquals(10, emptyA.beforeGold)
    }

    @Test
    fun receiverBagFullCannotDiscardOrPartiallyTransferAnyStack() {
        val first = side(a, mapOf(0 to coin), emptyList(), 200, cap = 1)
        val second = side(b, mapOf(0 to simple), listOf(0), 200)
        assertEquals(
            Result.Rejected(Failure.BAG_FULL, a),
            planner().plan(first, second),
        )
        assertEquals(coin, first.beforeBag[0])
        assertEquals(simple, second.beforeBag[0])
    }

    @Test
    fun restrictedGuildUnknownAndOneTimeSpecialItemsAreFailClosed() {
        val offered = side(a, mapOf(0 to simple), listOf(0), 100)
        val other = side(b, emptyMap(), emptyList(), 100)
        for (blocked in listOf(
            planner(aDef = def(restrict = 2)),
            planner(aDef = def(restrict2 = 1)),
            planner(aDef = def(kind = 53)),
        )) assertEquals(
            Result.Rejected(Failure.ITEM_NOT_TRADEABLE, a),
            blocked.plan(offered, other),
        )
        assertEquals(
            Result.Rejected(Failure.ITEM_NOT_TRADEABLE, a),
            planner(aDef = def(restrict = 2)).plan(
                offered.copy(beforeBag = mapOf(0 to simple.copy(specialKind = 1)),
                    expectedOfferedItems = mapOf(0 to simple.copy(specialKind = 1))), other,
            ),
        )
        val locked = offered.copy(beforeBag = mapOf(0 to simple.copy(isLocked = true)),
            expectedOfferedItems = mapOf(0 to simple.copy(isLocked = true)))
        assertEquals(Result.Rejected(Failure.ITEM_NOT_TRADEABLE, a), planner().plan(locked, other))
    }

    @Test
    fun negativeForeignSlotInvalidQuantityAndImpossibleCapacityAreRefused() {
        val valid = side(a, mapOf(0 to simple), listOf(0), 100)
        val other = side(b, emptyMap(), emptyList(), 100)
        for (bag in listOf(
            mapOf(-1 to simple),
            mapOf(251 to simple),
            mapOf(0 to simple.copy(quantity = 0)),
            mapOf(0 to simple.copy(quantity = ItemConstants.MAX_STACK + 1)),
            mapOf(0 to simple.copy(itemId = 0)),
        )) assertEquals(
            Result.Rejected(Failure.INVALID_BAG_OR_MONEY, a),
            planner().plan(valid.copy(beforeBag = bag), other),
        )
        assertEquals(
            Result.Rejected(Failure.INVALID_BAG_OR_MONEY, a),
            planner().plan(valid.copy(bagCapacity = 501), other),
        )
    }

    @Test
    fun emptyOrInvalidCharactersCannotBeUsedAsTradePeers() {
        val first = side(a, emptyMap(), emptyList(), 0)
        assertEquals(
            Result.Rejected(Failure.INVALID_PARTICIPANT),
            planner().plan(first, first),
        )
        assertEquals(
            Result.Rejected(Failure.INVALID_PARTICIPANT),
            planner().plan(first.copy(characterId = -1), side(b, emptyMap(), emptyList(), 0)),
        )
    }

    @Test
    fun unofferedStacksAndEmptyNoopTradeRemainByteForByteUnchanged() {
        val first = side(a, mapOf(0 to nft, 3 to coin), emptyList(), 1000)
        val second = side(b, mapOf(7 to simple), emptyList(), 2000)
        val plan = accepted(planner().plan(first, second))
        assertEquals(first.beforeBag, plan.first.afterBag)
        assertEquals(second.beforeBag, plan.second.afterBag)
        assertEquals(1000, plan.first.afterGold)
        assertEquals(2000, plan.second.afterGold)
    }
}
