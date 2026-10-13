"""Staged DVT C025 gate, immutable planner, receipt SQL and cache recovery pipeline."""
from pathlib import Path
import unittest

R = Path(__file__).resolve().parents[1]
FLOW = R / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeItemDurableSettlement.kt"
COORD = R / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeCacheSettlementCoordinator.kt"
TEST = R / "tests/src/test/kotlin/tw/tsm/tests/DvtTradeItemDurableSettlementTest.kt"


class DvtTradeOrchestratorAudit(unittest.TestCase):
    def test_single_immutable_uuid_frozen_full_bags_and_one_winner_gate(self):
        f = FLOW.read_text("utf-8")
        for term in (
            "val operationId: String", "UUID.fromString(operationId).toString() == operationId",
            "private val first = freeze(firstSide)",
            "private val second = freeze(secondSide)",
            "beforeBag = deepCopy(source.beforeBag)",
            "expectedOfferedItems = deepCopy(source.expectedOfferedItems)",
            "offeredSlots = source.offeredSlots.toList()",
            "private val gate = TradeItemFinalizationGate(firstPlayerId, secondPlayerId)",
            "gate.confirm(playerId)",
            "gate.settleOnce(",
            "planner.plan(first, second)",
        ):
            self.assertIn(term, f)
        self.assertNotIn("synchronized(", f)

    def test_receipt_commit_and_reconcile_distinct_post_lock_paths(self):
        c = COORD.read_text("utf-8")
        f = FLOW.read_text("utf-8")
        staged = c.split("suspend fun stageCommitWithReceipt(", 1)[1].split(
            "suspend fun stageCommit(", 1
        )[0]
        for term in (
            "CharacterPersistenceGuard.withLocks(first.characterId, second.characterId)",
            "money.withTradeGoldMutationFence(first.characterId, second.characterId)",
            "inventory.getLoadedBagSnapshot(side.characterId) == side.beforeBag",
            "money.getLoadedMoney(side.characterId)",
            "withContext(NonCancellable)",
            "repository.commitTwoCharacterTradeWithReceiptAtomic(",
            "TradeReceiptCommit.COMMITTED_NOW",
            "TradeReceiptCommit.ALREADY_COMMITTED",
            "ReceiptStage.INDETERMINATE",
            "inventory.publishCommittedTradeBagSync(",
            "money.publishCommittedTradeGoldSync(",
        ):
            self.assertIn(term, staged)
        self.assertLess(staged.index("TradeReceiptCommit.COMMITTED_NOW"),
                        staged.index("inventory.publishCommittedTradeBagSync("))
        self.assertNotIn("reconciler.reconcile(", staged)
        self.assertIn("coordinator.stageCommitWithReceipt(", f)
        self.assertIn("reconciler.reconcile(", f)
        self.assertLess(f.index("coordinator.stageCommitWithReceipt("),
                        f.index("reconciler.reconcile("))
        self.assertNotIn("commitTwoCharacterTradeWithReceiptAtomic(", f)
        self.assertNotIn("commitTwoCharacterTradeAtomic(", f)
        self.assertIn("throw IndeterminateTradeSettlement()", f)

    def test_kotlin_integrated_race_rollback_lost_ack_packet_cases(self):
        t = TEST.read_text("utf-8")
        for name in (
            "concurrentConfirmOnlyOneReceiptSqlAndPacketAfterTwoCaches",
            "cancelBeforeSecondConfirmationNeverStartsSql",
            "sqlRejectedCannotPublishOneCacheOrNotifySuccess",
            "lostSqlAckWithMatchedReceiptRestoresBothCachesAndNotifiesOnce",
            "unknownSqlReceiptIsQuarantinedAndNeverRetriesDebit",
            "alreadyCommittedSqlMustReconcileInsteadOfBlindCacheOverwrite",
            "newerGoldAwardBlocksHistoricalSqlReceiptWithoutDataLoss",
            "changedOfferedNftMetadataFailsPlannerBeforeSql",
            "packetFailureAfterBothCachesCommittedCannotRepeatSql",
            "failedSqlDuringConcurrentCancelNeverPublishesSuccess",
        ):
            self.assertIn(name, t)

    def test_live_c025_handler_and_di_still_unwired(self):
        for rel in (
            "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt",
            "app/src/main/kotlin/tw/tsm/app/handlers/TradeHandler.kt",
            "app/src/main/kotlin/tw/tsm/app/ServerModule.kt",
        ):
            t = (R / rel).read_text("utf-8")
            self.assertNotIn("TradeItemDurableSettlement(", t)
            self.assertNotIn("stageCommitWithReceipt(", t)
        c = COORD.read_text("utf-8")
        self.assertIn("suspend fun stageCommit(", c)
        self.assertIn("repository.commitTwoCharacterTradeAtomic(first, second)", c)


if __name__ == "__main__":
    unittest.main()
