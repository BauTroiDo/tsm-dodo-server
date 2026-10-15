"""Source gate for the staged DVT C025 whole-BAG snapshot trade planner."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLANNER = ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeItemSnapshotPlanner.kt"
TEST = ROOT / "tests/src/test/kotlin/tw/tsm/tests/DvtTradeItemSnapshotPlannerTest.kt"


class DvtTradeItemFullBagPlannerAudit(unittest.TestCase):
    def test_full_snapshot_sql_before_after_no_mutating_side_effects(self):
        s = PLANNER.read_text(encoding="utf-8")
        for essential in (
            "data class Side(", "expectedOfferedItems:", "bagCapacity: Int",
            "side.expectedOfferedItems.keys == side.offeredSlots.toSet()",
            "side.beforeBag[slot] != side.expectedOfferedItems[slot]",
            "beforeA = bagSnapshot(first.beforeBag)",
            "beforeB = bagSnapshot(second.beforeBag)",
            "val afterA = beforeA.toMutableMap()",
            "val afterB = beforeB.toMutableMap()",
            "first.offeredSlots.forEach { afterA.remove(it) }",
            "second.offeredSlots.forEach { afterB.remove(it) }",
            "TradeAccountSnapshot(", "identities(beforeA, beforeB)",
            "identities(afterA, afterB)", "thing.copy(myth = thing.myth.map { it.copy() })",
        ):
            self.assertIn(essential, s)
        self.assertNotIn("setBagItemSync(", s)
        self.assertNotIn("addItemDirectSync(", s)
        self.assertNotIn("commitTwoCharacterTradeAtomic(", s)
        self.assertNotIn("saveMoney(", s)
        self.assertNotIn("sendTradeCancel(", s)
        self.assertLess(s.index("first.offeredSlots.forEach"), s.index("if (!place(afterA,"))
        self.assertLess(s.index("second.offeredSlots.forEach"), s.index("if (!place(afterB,"))

    def test_money_caps_original_item_metadata_and_dynamic_capacity(self):
        s = PLANNER.read_text("utf-8")
        for term in (
            "ItemConstants.MAX_BAG_SLOTS",
            "ItemConstants.MAX_GOLD",
            "first.beforeGold.toLong() - first.offeredGold + second.offeredGold",
            "second.beforeGold.toLong() - second.offeredGold + first.offeredGold",
            "thing.myth",
            "def.kind == 53", "def.restrict2 and 1",
            "def.restrict and 2", "item.isLocked",
            "it in 0..255 && it < side.bagCapacity",
            "first.bagCapacity, outgoingB", "second.bagCapacity, outgoingA",
        ):
            self.assertIn(term, s)

    def test_race_and_fail_closed_kotlin_scenarios(self):
        s = TEST.read_text("utf-8")
        for case in (
            "fullBagSwapSucceedsOnlyAfterBothOutgoingSlotsAreReleased",
            "expandedBagSlotsBeyondLegacy250AreConsideredForReceiving",
            "incomingQuantityAndNftMetadataAreNeverMergedOrRecreated",
            "duplicateOutOfRangeAndForgedSlotListsAreRefusedBeforeMutation",
            "staleOfferIncludesQuantityDamageMythAttributesAndLockedFlag",
            "insufficientFundsAndReceivingGoldOverflowFailClosed",
            "receiverBagFullCannotDiscardOrPartiallyTransferAnyStack",
            "restrictedGuildUnknownAndOneTimeSpecialItemsAreFailClosed",
            "negativeForeignSlotInvalidQuantityAndImpossibleCapacityAreRefused",
            "emptyOrInvalidCharactersCannotBeUsedAsTradePeers",
            "unofferedStacksAndEmptyNoopTradeRemainByteForByteUnchanged",
        ):
            self.assertIn(case, s)

    def test_live_c025_remains_unwired_and_ack_is_still_legacy(self):
        live = (ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt").read_text("utf-8")
        handler = (ROOT / "app/src/main/kotlin/tw/tsm/app/handlers/TradeHandler.kt").read_text("utf-8")
        module = (ROOT / "app/src/main/kotlin/tw/tsm/app/ServerModule.kt").read_text("utf-8")
        for s in (live, handler, module):
            self.assertNotIn("TradeItemSnapshotPlanner", s)
            self.assertNotIn("TradeItemFinalizationGate", s)
            self.assertNotIn("TradeCacheSettlementCoordinator", s)
        self.assertIn("responseSender.sendTradeCancel(sessionA, REASON_SUCCESS)", live)


if __name__ == "__main__":
    unittest.main()
