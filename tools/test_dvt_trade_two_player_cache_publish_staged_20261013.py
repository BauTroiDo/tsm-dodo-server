"""Fail closed when staged two-player SQL commit/cache publish is not ready for C025.

All source invariants are guarded; Kotlin tests run real InventorySystem and
MoneySystem under two-player locks. No runtime wiring or new packet behavior.
"""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p): return (ROOT/p).read_text(encoding="utf8")
class DvtTwoPlayerCacheStaged(unittest.TestCase):
 def test_commit_and_both_cache_publications_hold_two_char_locks(self):
  p=read("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeCacheSettlementCoordinator.kt")
  # Preserve original path constraints; new receipt method comes earlier.
  original=p.split("    suspend fun stageCommit(",1)[1]
  self.assertIn("CharacterPersistenceGuard.withLocks(first.characterId, second.characterId)",original)
  self.assertIn("withContext(NonCancellable)",p)
  self.assertIn("repository.commitTwoCharacterTradeAtomic(first, second)",p)
  self.assertIn("inventory.publishCommittedTradeBagSync(",p)
  self.assertIn("money.publishCommittedTradeGoldSync(",p)
  self.assertLess(original.index("commitTwoCharacterTradeAtomic(first, second)"),original.index("publishCommittedTradeBagSync("))
  self.assertLess(original.index("commitTwoCharacterTradeAtomic(first, second)"),original.index("publishCommittedTradeGoldSync("))
  receipt=p.split("    suspend fun stageCommitWithReceipt(",1)[1].split("    suspend fun stageCommit(",1)[0]
  self.assertLess(receipt.index("commitTwoCharacterTradeWithReceiptAtomic("),receipt.index("publishCommittedTradeBagSync("))
  self.assertLess(receipt.index("commitTwoCharacterTradeWithReceiptAtomic("),receipt.index("publishCommittedTradeGoldSync("))
 def test_unloaded_bag_and_unloaded_money_are_rejected_pre_sql(self):
  inv=read("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt")
  money=read("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MoneySystem.kt")
  stage=read("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeCacheSettlementCoordinator.kt")
  self.assertIn("fun getLoadedBagSnapshot(",inv)
  self.assertIn("fun getLoadedMoney(",money)
  self.assertIn("inventory.getLoadedBagSnapshot(side.characterId) != side.beforeBag",stage)
  self.assertIn("money.getLoadedMoney(side.characterId)",stage)
  self.assertIn("check(bags.containsKey(characterId))",inv)
  self.assertIn("bags[characterId] = ConcurrentHashMap(afterBag)",inv)
  self.assertIn("synchronized(arr)",money)
 def test_real_kotlin_cache_and_autosave_logout_cancel_coverage(self):
  test=read("tests/src/test/kotlin/tw/tsm/tests/DvtTradeCacheSettlementCoordinatorTest.kt")
  for case in ("successPublishesBothCachesBeforeEitherAutosave",
      "sqlFalseLeavesBothPlayerBagsAndCurrencyExactlyAsBefore",
      "sqlFailureLeavesCachesUnchangedAndDoesNotSendSuccess",
      "cancellationDuringSqlCannotSkipEitherCachePublication",
      "logoutOfSecondPlayerCannotUnloadCacheDuringTwoPartySqlCommit",
      "staleOrUnloadedCacheBlocksSqlDebitBeforeItStarts"):
   self.assertIn(case,test)
 def test_no_c025_activation_while_unfenced_sync_writers_remain(self):
  trade=read("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt")
  handler=read("app/src/main/kotlin/tw/tsm/app/handlers/TradeHandler.kt")
  app=read("app/src/main/kotlin/tw/tsm/app/ServerModule.kt")
  money=read("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MoneySystem.kt")
  self.assertNotIn("TradeCacheSettlementCoordinator(",trade)
  self.assertNotIn("TradeCacheSettlementCoordinator(",app)
  self.assertNotIn("stageCommit(",handler)
  self.assertIn("fun addGold(",money)
  self.assertIn("fun subtractGold(",money)
  self.assertIn("if (def.kind == 98)",read("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt"))
if __name__=="__main__":unittest.main()
