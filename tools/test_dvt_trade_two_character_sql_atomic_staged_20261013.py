"""DVT two-player BAG/GOLD SQL transaction remains staged and fail closed.

The existing C025 handler still uses cache-only changes; runtime activation
is forbidden until transaction and both online caches can be synchronized.
"""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p):return (ROOT/p).read_text(encoding='utf8')
class DvtTwoPartyTradeAtomic(unittest.TestCase):
 def test_repository_rechecks_both_persisted_bags_and_gold_before_change(self):
  txt=read('persistence/src/main/kotlin/tw/tsm/persistence/repositories/InventoryRepositoryImpl.kt')
  segment=txt.split('override suspend fun commitTwoCharacterTradeAtomic(',1)[1].split('override suspend fun loadMoney(',1)[0]
  self.assertIn('val sides = listOf(first, second).sortedBy { it.characterId }',segment)
  self.assertIn('return suspendTransaction {',segment)
  self.assertGreaterEqual(segment.count('.forUpdate()'),2)
  self.assertIn('if (persistedBag != side.beforeBag)',segment)
  self.assertIn('persistedMoney[CharacterMoney.gold] != side.beforeGold',segment)
  self.assertIn('persistedMoney[CharacterMoney.auctionGold] != side.auctionGold',segment)
  self.assertIn('Inventories.deleteWhere',segment)
  self.assertIn('Inventories.batchInsert',segment)
  self.assertIn('CharacterMoney.update',segment)
  self.assertNotIn('TreasureRepository',segment)
 def test_mint_and_stale_replay_are_rejected_by_entire_thingdata_identity(self):
  txt=read('persistence/src/main/kotlin/tw/tsm/persistence/repositories/InventoryRepositoryImpl.kt')
  segment=txt.split('override suspend fun commitTwoCharacterTradeAtomic(',1)[1].split('override suspend fun loadMoney(',1)[0]
  self.assertIn('it.copy(quantity = 1)',segment)
  self.assertIn('sides.map { it.beforeBag }',segment)
  self.assertIn('sides.map { it.afterBag }',segment)
  self.assertIn('sides.sumOf { it.beforeGold.toLong() }',segment)
  self.assertIn('sides.sumOf { it.afterGold.toLong() }',segment)
  self.assertIn('first.characterId == second.characterId',segment)
 def test_h2_suite_includes_two_sided_rollback_duplicate_and_db_conflict(self):
  test=read('tests/src/test/kotlin/tw/tsm/tests/DvtTradeTwoPlayerAtomicRollbackTest.kt')
  for method in ('bothBagsAndBothGoldBalancesCommitInOneTransaction',
      'firstPlayerNeverCommitsWhenSecondPlayerSqlConstraintFails',
      'invalidAndStalePlayerSnapshotsLeaveAllRowsUntouched',
      'forgedDuplicationAndUnbalancedGoldCannotReachSql',
      'missingOrDifferentActualDatabaseRowNeverMintsInventory'):
   self.assertIn(method,test)
  self.assertIn('SchemaUtils.create(Inventories,CharacterMoney)',test)
 def test_original_live_trade_and_treasure_are_still_unwired(self):
  trade=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt')
  handlers=read('app/src/main/kotlin/tw/tsm/app/handlers/TradeHandler.kt')
  treasure=read('app/src/main/kotlin/tw/tsm/app/handlers/TreasureHandler.kt')
  di=read('app/src/main/kotlin/tw/tsm/app/ServerModule.kt')
  self.assertNotIn('commitTwoCharacterTradeAtomic(',trade)
  self.assertNotIn('commitTwoCharacterTradeAtomic(',handlers)
  self.assertNotIn('commitTwoCharacterTradeAtomic(',treasure)
  self.assertNotIn('TreasureBankCacheCoordinator(',di)
  processor=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt')
  self.assertIn('if (def.kind == 98)',processor)
 def test_domain_interface_defers_to_refuse_unwired_fake_repository(self):
  i=read('domain/src/main/kotlin/tw/tsm/domain/interfaces/repositories/InventoryRepository.kt')
  self.assertIn('suspend fun commitTwoCharacterTradeAtomic(',i)
  self.assertIn('error("Atomic trade integration is not enabled")',i)
if __name__=='__main__':unittest.main()
