"""Staged DVT two-character item trade durable SQL receipt contract."""
import unittest
from pathlib import Path

R = Path(__file__).resolve().parents[1]
SQL = R / "persistence/src/main/kotlin/tw/tsm/persistence/repositories/InventoryRepositoryImpl.kt"
DIGEST = R / "persistence/src/main/kotlin/tw/tsm/persistence/repositories/TradeSnapshotDigest.kt"
TABLE = R / "persistence/src/main/kotlin/tw/tsm/persistence/tables/TradeSettlementReceipts.kt"
DOMAIN = R / "domain/src/main/kotlin/tw/tsm/domain/interfaces/repositories/InventoryRepository.kt"
TEST = R / "tests/src/test/kotlin/tw/tsm/tests/DvtTradeDurableReceiptSqlTest.kt"
FACTORY = R / "persistence/src/main/kotlin/tw/tsm/persistence/DatabaseFactory.kt"


class DvtTradeDurableReceiptAudit(unittest.TestCase):
    def test_single_sql_transaction_contains_both_debits_and_receipt(self):
        s = SQL.read_text("utf-8")
        self.assertIn("commitTwoCharacterTradeWithReceiptAtomic(", s)
        self.assertIn("findTwoCharacterTradeReceipt(", s)
        self.assertIn("commitTwoCharacterTradeInternal(", s)
        self.assertIn("return suspendTransaction {", s)
        p = s.index("private suspend fun commitTwoCharacterTradeInternal")
        sub = s[p:s.index("override suspend fun loadMoney(", p)]
        for required in ("CharacterMoney.selectAll()", ".forUpdate()", "Inventories.deleteWhere",
                         "Inventories.batchInsert", "CharacterMoney.update(",
                         "TradeSettlementReceipts.insert", "TradeReceiptCommit.COMMITTED_NOW"):
            self.assertIn(required, sub)
        self.assertLess(sub.index("CharacterMoney.update("), sub.index("TradeSettlementReceipts.insert"))
        self.assertLess(sub.index("TradeSettlementReceipts.insert"), sub.index("TradeReceiptCommit.COMMITTED_NOW"))
        self.assertIn("TradeReceiptCommit.ALREADY_COMMITTED", sub)
        self.assertIn("TradeReceiptCommit.TOKEN_CONFLICT", sub)
        self.assertIn("TradeReceiptCommit.REJECTED", sub)

    def test_receipt_uuid_fingerprint_and_table_registration(self):
        s = TABLE.read_text("utf-8")
        self.assertIn('Table("trade_settlement_receipts")', s)
        self.assertIn("PrimaryKey(operationId)", s)
        self.assertIn('varchar("snapshot_digest", 64)', s)
        self.assertIn("TradeSettlementReceipts", FACTORY.read_text("utf-8"))
        d = DIGEST.read_text("utf-8")
        for x in ("MessageDigest.getInstance(\"SHA-256\")", "sortedBy { it.characterId }",
                  "bag.toSortedMap()", "out.writeInt(t.quantity)", "out.writeInt(t.affix3)",
                  "t.deleteTime.toRawBits()", "out.writeInt(t.mythLevel)", "for (attr in t.myth)"):
            self.assertIn(x, d)
        i = DOMAIN.read_text("utf-8")
        self.assertIn("TradeReceiptCommit { COMMITTED_NOW, ALREADY_COMMITTED, REJECTED, TOKEN_CONFLICT }", i)
        self.assertIn("TradeReceiptLookup { NOT_FOUND, MATCHED, TOKEN_CONFLICT }", i)

    def test_h2_replay_restart_constraint_rollback_and_token_conflict(self):
        t = TEST.read_text("utf-8")
        for x in ("receiptAndBothBagsAndGoldCommitInOneAtomicTransaction",
                  "lostNetworkAcknowledgementCanReconcileAfterRepositoryRecreated",
                  "reversingArgumentsCannotReuseOperationToCreateSecondTransfer",
                  "reusedUuidWithChangedFullSnapshotIsBlockedBeforeDbMutation",
                  "constraintFailureAfterFirstPlayerChangesRollsBackAllAndReceipt",
                  "staleSqlSnapshotAndAnotherOperationIdCannotCreditTwice",
                  "malformedUuidAndInvalidPlanCannotCreateReceipt",
                  "originalUnreceiptedApiRetainsExactPreviousBehavior"):
            self.assertIn(x, t)

    def test_receipt_stays_unwired_to_network(self):
        for relative in (
            "app/src/main/kotlin/tw/tsm/app/handlers/TradeHandler.kt",
            "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt",
            "app/src/main/kotlin/tw/tsm/app/ServerModule.kt",
            "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeCacheSettlementCoordinator.kt",
        ):
            src = (R / relative).read_text("utf-8")
            self.assertNotIn("commitTwoCharacterTradeWithReceiptAtomic", src)
            self.assertNotIn("findTwoCharacterTradeReceipt", src)
            self.assertNotIn("TradeSettlementReceipts", src)


if __name__ == "__main__":
    unittest.main()
