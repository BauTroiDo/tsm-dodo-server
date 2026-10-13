"""Source-regression audit for C025 upload-time immutable item metadata and UUID."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
TRADE = ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt"
HELPER = ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeItemOfferSnapshot.kt"
HANDLER = ROOT / "app/src/main/kotlin/tw/tsm/app/handlers/TradeHandler.kt"
KTEST = ROOT / "tests/src/test/kotlin/tw/tsm/tests/DvtTradeFrozenItemOfferTest.kt"


class C025FrozenOfferAudit(unittest.TestCase):
    def test_server_uuid_and_uploaded_metadata_are_bound_to_trade_session(self):
        t = TRADE.read_text("utf-8")
        self.assertIn("val operationId: String = UUID.randomUUID().toString()", t)
        self.assertIn("val expectedItems: Map<Int, ThingData> = emptyMap()", t)
        upload = t.split("suspend fun uploadItems(", 1)[1].split("fun uploadPet(", 1)[0]
        for text in (
            "CharacterPersistenceGuard.withLock(characterId)",
            "synchronized(trade)",
            "if (activeTrades[playerId] !== trade || activeTrades[partnerId] !== trade)",
            "TradeItemOfferSnapshot.capture(bagIndices)",
            "val item = expectedItems.getValue(slot)",
            "bagIndices = bagIndices.toList(), expectedItems = expectedItems",
            "if (myState !is TradePlayerState.WaitingUpload)",
        ):
            self.assertIn(text, upload)
        self.assertLess(upload.index("CharacterPersistenceGuard.withLock(characterId)"),
                        upload.index("        synchronized(trade) {"))
        self.assertIn("private suspend fun handleUploadItems(", HANDLER.read_text("utf-8"))

    def test_partner_sees_frozen_original_and_confirm_rechecks_before_debit(self):
        t = TRADE.read_text("utf-8")
        display = t.split("private fun sendTradeOffers(", 1)[1].split("private fun sendPetTradeOffers(", 1)[0]
        self.assertIn("TradeItemOfferSnapshot.orderedItems(offerA.bagIndices, offerA.expectedItems)", display)
        self.assertIn("TradeItemOfferSnapshot.orderedItems(offerB.bagIndices, offerB.expectedItems)", display)
        self.assertNotIn("mapNotNull { slot ->", display)
        final = t.split("private fun executeTrade(", 1)[1].split("private fun executePetTrade(", 1)[0]
        self.assertIn("TradeItemOfferSnapshot.matches(offerA.bagIndices, offerA.expectedItems)", final)
        self.assertIn("TradeItemOfferSnapshot.matches(offerB.bagIndices, offerB.expectedItems)", final)
        self.assertLess(final.index("TradeItemOfferSnapshot.matches("),
                        final.index("inventorySystem.setBagItemSync("))
        self.assertIn("cancelTradeInternal(trade, trade.playerA, REASON_SYSTEM", final)

    def test_helper_deep_freeze_no_empty_stack_or_duplicate_slot(self):
        s = HELPER.read_text("utf-8")
        for term in (
            "item.copy(myth = item.myth.map { it.copy() })",
            "slots.size != slots.toSet().size",
            "slots.any { it !in 0..255 }",
            "item.isEmpty || item.quantity <= 0",
            "actual == offered[slot]",
            "fun orderedItems(",
        ):
            self.assertIn(term, s)

    def test_original_c025_pet_mode_protocol_and_dormant_sql_preserved(self):
        t = TRADE.read_text("utf-8")
        h = HANDLER.read_text("utf-8")
        self.assertIn("val offer = TradeOffer(money = money, petSlot = petSlot)", t)
        self.assertIn("private fun executePetTrade(", t)
        self.assertIn("sendPetTradeOffers(trade)", t)
        self.assertIn("tradeSystem.uploadPet(session.playerId, money, petSlot)", h)
        self.assertIn("responseSender.sendTradeCancel(sessionA, REASON_SUCCESS)", t)
        for absent in (
            "commitTwoCharacterTradeWithReceiptAtomic(",
            "TradeItemSnapshotPlanner(",
            "TradeCacheSettlementCoordinator(",
            "TradeItemFinalizationGate(",
        ):
            self.assertNotIn(absent, t)

    def test_kotlin_race_forgery_replay_integration_coverage(self):
        s = KTEST.read_text("utf-8")
        for case in (
            "deepCaptureProtectsMutableMythAttributesAndOriginalSlotOrder",
            "duplicateForgedMissingAndChangedOfferMustFailClosed",
            "emptyOfferValidButMissingOrZeroStackInvalid",
            "everyServerTradeSessionGetsIndependentCanonicalUuid",
            "realC025UploadDisplaysFrozenOriginalEvenIfBAGChangedBeforePartnerUpload",
            "realC025FinalConfirmRejectsSwappedStackBeforeAnyRAMMutation",
            "replayedUploadCannotReplacePreviouslyAcceptedFrozenOffer",
        ):
            self.assertIn(case, s)


if __name__ == "__main__":
    unittest.main()
