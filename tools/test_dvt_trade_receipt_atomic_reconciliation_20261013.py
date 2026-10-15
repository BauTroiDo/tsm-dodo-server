"""Source release gate for staged C025 durable receipt and guarded cache recovery."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECONCILER = ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeReceiptCacheReconciler.kt"
REPO = ROOT / "persistence/src/main/kotlin/tw/tsm/persistence/repositories/InventoryRepositoryImpl.kt"
API = ROOT / "domain/src/main/kotlin/tw/tsm/domain/interfaces/repositories/InventoryRepository.kt"
TEST = ROOT / "tests/src/test/kotlin/tw/tsm/tests/DvtTradeReceiptCacheReconcilerTest.kt"
H2 = ROOT / "tests/src/test/kotlin/tw/tsm/tests/DvtTradeDurableReceiptSqlTest.kt"


class ReceiptReconciliationAudit(unittest.TestCase):
    def test_one_sql_transaction_locks_both_players_and_reads_receipt_and_complete_bag(self):
        s = REPO.read_text("utf-8")
        block = s.split("override suspend fun inspectTwoCharacterTradeReceiptAtomic(", 1)[1].split(
            "private fun validOperationId(", 1
        )[0]
        for required in (
            "val ordered = listOf(first, second).sortedBy { it.characterId }",
            "return suspendTransaction {",
            "CharacterMoney.selectAll()",
            ".forUpdate().singleOrNull()",
            "TradeSettlementReceipts.selectAll()",
            "TradeSnapshotDigest.of(first, second)",
            "Inventories.selectAll().where {",
            "item.toThingData()",
            "TradeObservedAccount(",
            "TradeReceiptInspection(",
        ):
            self.assertIn(required, block)
        self.assertLess(block.index("CharacterMoney.selectAll()"), block.index("TradeSettlementReceipts.selectAll()"))
        self.assertLess(block.index("TradeSettlementReceipts.selectAll()"), block.index("Inventories.selectAll()"))
        self.assertIn("data class TradeReceiptInspection(", API.read_text("utf-8"))

    def test_cache_repair_is_fenced_non_cancellable_without_sql_retry(self):
        s = RECONCILER.read_text("utf-8")
        for required in (
            "withContext(NonCancellable)",
            "CharacterPersistenceGuard.withLocks(first.characterId, second.characterId)",
            "money.withTradeGoldMutationFence(first.characterId, second.characterId)",
            "repository.inspectTwoCharacterTradeReceiptAtomic(",
            "TradeReceiptLookup.NOT_FOUND",
            "TradeReceiptLookup.TOKEN_CONFLICT",
            "TradeReceiptLookup.MATCHED",
            "Result.DATABASE_DIVERGED",
            "Result.CACHE_UNLOADED",
            "Result.CACHE_DIVERGED",
            "Result.INDETERMINATE",
            "inventory.publishCommittedTradeBagSync(",
            "money.publishCommittedTradeGoldSync(",
            "Result.RESTORED",
            "Result.ALREADY_SYNCED",
        ):
            self.assertIn(required, s)
        self.assertLess(s.index("Result.CACHE_DIVERGED"), s.index("inventory.publishCommittedTradeBagSync("))
        self.assertLess(s.index("Result.DATABASE_DIVERGED"), s.index("inventory.publishCommittedTradeBagSync("))
        self.assertNotIn("commitTwoCharacterTradeWithReceiptAtomic(", s)
        self.assertNotIn("commitTwoCharacterTradeAtomic(", s)

    def test_kotlin_fault_and_race_cases_present(self):
        s = TEST.read_text("utf-8")
        for name in (
            "matchedReceiptAndExactSqlAfterRepairsBothOldCaches",
            "alreadyPublishedCachesProduceNoSecondPublication",
            "interruptedPublicationWithOneBagAndOneGoldAlreadyUpdatedIsRepairable",
            "changedLocalGoldRewardCannotBeOverwrittenByHistoricalReceipt",
            "changedLocalBagCannotBeClobbered",
            "persistedBalancesAlreadyAdvancedAfterReceiptRequireManualReconciliation",
            "missingOrConflictingReceiptNeverTriggersSecondSqlDebit",
            "missingSqlAccountOrOfflineCacheNeverPublishesPartialTransfer",
            "databaseExceptionReturnsIndeterminateWithoutInventingRollback",
            "concurrentSyncGoldAwardWaitsForSqlInspectionAndCannotBeLost",
        ):
            self.assertIn(name, s)
        h2 = H2.read_text("utf-8")
        for name in (
            "atomicInspectionReadsMatchedReceiptAndBothRealDatabaseAfterStates",
            "atomicInspectionDetectsLaterRealSqlWritesInsteadOfAssumingHistoricalAfter",
            "atomicInspectionForForgedPlanAndInvalidUuidReturnsNoAccountSnapshots",
        ):
            self.assertIn(name, h2)

    def test_not_wired_to_c025_game_handler_or_old_settlement(self):
        for relative in (
            "app/src/main/kotlin/tw/tsm/app/handlers/TradeHandler.kt",
            "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt",
            "app/src/main/kotlin/tw/tsm/app/ServerModule.kt",
        ):
            s = (ROOT / relative).read_text("utf-8")
            self.assertNotIn("TradeReceiptCacheReconciler", s)
            self.assertNotIn("inspectTwoCharacterTradeReceiptAtomic(", s)


if __name__ == "__main__":
    unittest.main()
