"""DVT trade and MoneySystem save must join character persistence fencing.

Scoped invariant only: independent synchronous money writers are not yet
subject to this mutex and trade's cached swap is not cross-DB atomic.
"""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def src(p):return (ROOT/p).read_text(encoding="utf8")
class DvtTradeGoldSnapshotFence(unittest.TestCase):
 def test_pair_stripe_order_and_collision_behavior(self):
  guard=src("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/CharacterPersistenceGuard.kt")
  self.assertIn("suspend fun <T> withLocks(charIdA: Long, charIdB: Long",guard)
  self.assertIn("if (a == b) return locks[a].withLock",guard)
  self.assertIn("val low = minOf(a, b)",guard)
  self.assertIn("val high = maxOf(a, b)",guard)
  self.assertIn("locks[low].withLock",guard)
  self.assertIn("locks[high].withLock",guard)
 def test_trade_confirms_both_characters_under_one_fence(self):
  trade=src("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt")
  block=trade.split("suspend fun confirmOrCancel(",1)[1].split("fun cancelTradeOnDisconnect",1)[0]
  self.assertIn("CharacterPersistenceGuard.withLocks(idA, idB)",block)
  self.assertLess(block.index("CharacterPersistenceGuard.withLocks(idA, idB)"),block.index("synchronized(trade)"))
  self.assertIn("executeTrade(trade)",block)
  self.assertIn("executePetTrade(trade)",block)
  self.assertIn("return@withLocks emptyList()",block)
  handler=src("app/src/main/kotlin/tw/tsm/app/handlers/TradeHandler.kt")
  self.assertIn("private suspend fun handleConfirmTrade(",handler)
  test=src("tests/src/test/kotlin/tw/tsm/tests/CollectionBonusScopeTest.kt")
  self.assertIn("coEvery {\n            tradeSystem.confirmOrCancel(",test)
 def test_money_snapshot_and_logout_share_fence(self):
  money=src("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MoneySystem.kt")
  self.assertIn("CharacterPersistenceGuard.withLock(characterId)",money)
  self.assertIn("private suspend fun saveMoneyUnfenced(characterId: Long)",money)
  self.assertIn("val snapshot = synchronized(arr) { Pair(arr[0].toInt(), arr[1].toInt()) }",money)
  self.assertIn("inventoryRepo.saveMoney(characterId, snapshot.first, snapshot.second)",money)
  self.assertIn("suspend fun saveAndUnload(characterId: Long) {\n        CharacterPersistenceGuard.withLock(characterId)",money)
 def test_coroutine_race_tests_exist_and_transfer_remains_disabled(self):
  test=src("tests/src/test/kotlin/tw/tsm/tests/DvtTradePairAndMoneyAutosaveLockTest.kt")
  for name in ("reversedPlayerLockOrderNeverDeadlocks","twoDistinctCharactersInSameStripeNeverReacquireOwnMutex",
               "moneyAutosaveCannotPassActiveCharacterSettlementLock",
               "moneyLogoutCacheRemovalCannotPrecedeLockedSave"):
   self.assertIn(name,test)
  processor=src("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt")
  self.assertIn("if (def.kind == 98)",processor)
  wiring=src("app/src/main/kotlin/tw/tsm/app/ServerModule.kt")
  self.assertNotIn("TreasureBankCacheCoordinator(",wiring)
if __name__=="__main__":unittest.main()
