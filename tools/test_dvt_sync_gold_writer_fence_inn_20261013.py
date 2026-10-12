"""Source-based DVT gold fences & C031 inn payment fix.

After race-safe two-party SQL publication was staged, all direct synchronous
MoneySystem gold writers must join one fence for the duration of the commit.
Do not wire new C025 until packet state and bag writers have been audited.
"""
import unittest,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p):return (ROOT/p).read_text(encoding='utf8')
class DvtGoldWritersFence(unittest.TestCase):
 def test_both_sync_gold_writers_acquire_same_bounded_per_character_fence(self):
  s=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MoneySystem.kt')
  self.assertIn('private val goldMutationFences = Array(256) { Semaphore(1, true) }',s)
  self.assertIn('permit.acquireUninterruptibly()',s)
  self.assertIn('permit.release()',s)
  self.assertRegex(s,r'(?s)fun addGold\(.*?return withGoldMutationFence\(characterId\)')
  self.assertRegex(s,r'(?s)fun subtractGold\(.*?return withGoldMutationFence\(characterId\)')
  self.assertIn('synchronized(arr)',s)
 def test_two_party_gold_window_is_non_cancellable_ordered_and_released(self):
  s=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MoneySystem.kt')
  self.assertIn('suspend fun <T> withTradeGoldMutationFence(',s)
  self.assertIn('minOf(goldStripe(firstId), goldStripe(secondId))',s)
  self.assertIn('maxOf(goldStripe(firstId), goldStripe(secondId))',s)
  self.assertIn('return withContext(NonCancellable + Dispatchers.IO)',s)
  self.assertIn('callerJob?.ensureActive()',s)
  self.assertIn('if (high !== low)',s)
  self.assertIn('low.release()',s)
  self.assertIn('high.release()',s)
 def test_cache_publication_holds_gold_fence_around_sql_and_ram(self):
  s=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeCacheSettlementCoordinator.kt')
  self.assertIn('money.withTradeGoldMutationFence(first.characterId, second.characterId)',s)
  self.assertLess(s.index('money.withTradeGoldMutationFence('),s.index('repository.commitTwoCharacterTradeAtomic('))
  self.assertLess(s.index('repository.commitTwoCharacterTradeAtomic('),s.index('money.publishCommittedTradeGoldSync('))
  self.assertNotIn('TradeCacheSettlementCoordinator(',read('app/src/main/kotlin/tw/tsm/app/ServerModule.kt'))
 def test_inn_rest_fee_deducts_one_positive_amount_and_denies_no_gold(self):
  s=read('app/src/main/kotlin/tw/tsm/app/handlers/InnHandler.kt')
  block=s.split('private suspend fun handleRest(',1)[1].split('private suspend fun handleRetrieve(',1)[0]
  self.assertIn('CharacterPersistenceGuard.withLock(charId)',block)
  self.assertIn('moneySystem.subtractGold(charId, cost)',block)
  self.assertIn('if (!paid)',block)
  self.assertIn('innResponseSender.sendInnRestResult(session, 1)',block)
  self.assertIn('responseSender.sendSubtractMoney(session, cost)',block)
  self.assertNotIn('addGold(charId, -cost)',block)
  self.assertLess(block.index('if (!paid)'),block.index('petSystem.restoreAllFollowHpSp('))
 def test_money_race_kotlin_cases_are_present(self):
  s=read('tests/src/test/kotlin/tw/tsm/tests/DvtTradeCacheSettlementCoordinatorTest.kt')
  for n in ('synchronousGoldAwardWaitsUntilBothPublishedBalancesAreReady',
            'synchronousGoldSpendRechecksNewlyCommittedBalance'):
   self.assertIn(n,s)
  s=read('tests/src/test/kotlin/tw/tsm/tests/DvtTradePairAndMoneyAutosaveLockTest.kt')
  self.assertIn('goldStripeCollisionMustNotDeadlockPairFence',s)
  self.assertIn('reversedGoldStripeOrderMustNeverDeadlock',s)
  self.assertIn('cancelledWaiterCannotLeakPermitsOrStartNewTrade',s)
 def test_inn_writes_are_only_fix_but_pet_trade_and_treasure_remain_unwired(self):
  s=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt')
  self.assertNotIn('commitTwoCharacterTradeAtomic(',s)
  t=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt')
  self.assertIn('if (def.kind == 98)',t)
if __name__=='__main__':unittest.main()
