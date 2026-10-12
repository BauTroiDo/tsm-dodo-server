"""Source-guard staged Treasure BAG cache publish against periodic save/logout.

This stage is not a C023-015 handler activation. All unguarded bag writers
remain blockers, so the runtime must not be wired until the same lock covers
all relevant mutation paths and original 245 semantics are proven.
"""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def read(p):return (ROOT/p).read_text(encoding="utf-8")

class TreasureCacheCoherence(unittest.TestCase):
 def test_coordinator_uses_same_lock_as_periodic_autosave_and_logout(self):
  src=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TreasureBankCacheCoordinator.kt')
  inv=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  self.assertIn('CharacterPersistenceGuard.withLock(characterId)',src)
  self.assertEqual(inv.count('CharacterPersistenceGuard.withLock(characterId)'),2)
  self.assertIn('suspend fun save(characterId: Long)',inv)
  self.assertIn('suspend fun saveAndUnload(characterId: Long)',inv)
  self.assertIn('withContext(NonCancellable)',src)
  self.assertIn('inventory.setBagItemSync(characterId, bagSlot, after)',src)
  self.assertIn('repository.bankCard(',src)
  self.assertLess(src.index('repository.bankCard('),src.index('inventory.setBagItemSync'))
 def test_cache_delta_validates_actual_card_before_db_debit(self):
  src=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TreasureBankCacheCoordinator.kt')
  for phrase in ('originalCardItemIds[treasureId] ?: return null','before.itemId != expectedItemId',
                 'before.quantity < quantity','expectedVersion < 0','quantity <= 0',
                 'before.copy(quantity = before.quantity - quantity)',
                 'return@withContext null'):
   self.assertIn(phrase,src)
 def test_dormant_service_is_unwired_and_original_ability245_guard_remains(self):
  app=read('app/src/main/kotlin/tw/tsm/app/ServerModule.kt')
  handler=read('app/src/main/kotlin/tw/tsm/app/handlers/TreasureHandler.kt')
  items=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt')
  self.assertNotIn('TreasureBankCacheCoordinator(',app)
  self.assertNotIn('stageBank(',handler)
  self.assertNotIn('stageBank(',items)
  self.assertIn('if (def.kind == 98)',items)
 def test_kotlin_real_autosave_logout_cancel_and_failure_regressions(self):
  test=read('tests/src/test/kotlin/tw/tsm/tests/DvtTreasureBankCacheCoordinatorTest.kt')
  for item in ('successfulCommitPublishesBagBeforePeriodicAutosaveCanSnapshot',
               'successfulCommitPublishesBagBeforeLogoutRemovesCachedBag',
               'rejectedOrFailedSqlDoesNotPublishAnyBagChange',
               'failedCommitThrowsButInMemoryBagRemainsUnchanged',
               'cancellationDuringDbCommitDoesNotInterruptCachePublication',
               'wrongCardOrUnloadedBagCannotInvokeSqlBanking'):
   self.assertIn(item,test)
 def test_uncoupled_bag_writers_still_present_so_live_transfer_must_remain_off(self):
  inv=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  self.assertIn('suspend fun removeFromBag(',inv)
  self.assertIn('suspend fun moveBagItem(',inv)
  self.assertIn('suspend fun setBagItemsAtomic(',inv)
  # These are not coordinated with the common mutex across the whole method.
  # Live activation is forbidden until every writer participates and source
  # banking semantics match native.
  repo=read('persistence/src/main/kotlin/tw/tsm/persistence/repositories/TreasureRepositoryImpl.kt')
  self.assertIn('suspendTransaction',repo)
  self.assertNotIn('TreasureBankCacheCoordinator',repo)
if __name__=='__main__':unittest.main()
