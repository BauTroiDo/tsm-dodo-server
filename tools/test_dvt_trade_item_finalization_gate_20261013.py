"""Guard the staged C025 exactly-once SQL finalization boundary.

The real handler is intentionally not wired to SQL until all live BAG and
currency writers and response semantics are proven safe.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeItemFinalizationGate.kt"
KOTLIN_TEST = ROOT / "tests/src/test/kotlin/tw/tsm/tests/DvtTradeItemFinalizationGateTest.kt"


class DvtTradeItemFinalizationGateAudit(unittest.TestCase):
    def test_only_one_elected_settler_and_no_jvm_monitor_during_sql(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("settlementStarted = true", source)
        self.assertIn("if (phase != Phase.SETTLING || settlementStarted)", source)
        self.assertIn("return withContext(NonCancellable)", source)
        self.assertIn("val elected = synchronized(monitor)", source)
        self.assertIn("if (!elected) return Settlement.NOT_ELECTED", source)
        self.assertIn("commitAndPublish()", source)
        self.assertIn("if (!committed) return@withContext Settlement.REJECTED", source)
        self.assertLess(source.index("commitAndPublish()\n"), source.index("notifySuccess()\n"))
        self.assertIn("currentCoroutineContext()[Job]?.isActive == false", source)
        self.assertIn("Phase.SETTLING -> Abort.COMMIT_IN_FLIGHT", source)

    def test_race_sql_rollback_cancellation_and_ack_failure_covered(self):
        test = KOTLIN_TEST.read_text(encoding="utf-8")
        for case in (
            "twoConcurrentConfirmationsElectExactlyOneCommitAndIgnoreReplays",
            "cancelBeforeBothConfirmStopsSqlAndCannotBeReopened",
            "successIsNeverSignalledBeforeCommitAndBothCachesHaveBeenPublished",
            "disconnectCancellationMidSqlStillFinishesCommitAndNotifiesOnce",
            "sqlRejectionAndSqlExceptionNeverEmitSuccess",
            "packetFailureCannotUndoSqlOrStartAnotherCommit",
        ):
            self.assertIn(case, test)

    def test_c025_and_sql_settlement_remain_unwired(self):
        app = (ROOT / "app/src/main/kotlin/tw/tsm/app/handlers/TradeHandler.kt").read_text("utf-8")
        live = (ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt").read_text("utf-8")
        di = (ROOT / "app/src/main/kotlin/tw/tsm/app/ServerModule.kt").read_text("utf-8")
        for body in (app, live, di):
            self.assertNotIn("TradeItemFinalizationGate", body)
            self.assertNotIn("TradeCacheSettlementCoordinator", body)
        # Legacy C025 success packet remains after RAM mutations; this
        # audit deliberately does NOT claim live atomic SQL trade.
        self.assertIn("responseSender.sendTradeCancel(sessionA, REASON_SUCCESS)", live)
        coordinator = (ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeCacheSettlementCoordinator.kt").read_text("utf-8")
        self.assertIn("commitTwoCharacterTradeAtomic(first, second)", coordinator)


if __name__ == "__main__":
    unittest.main()
