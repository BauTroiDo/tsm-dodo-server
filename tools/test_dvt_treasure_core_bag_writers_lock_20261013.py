"""Guard key DVT BAG mutators against autosave and staged Treasure bank races.

The lock covers individual suspend mutation methods, not every synchronous
cache writer or every multi-operation shop transaction. Do not activate S123.
"""
import re
import unittest
from pathlib import Path

R=Path(__file__).resolve().parents[1]
def src(p): return (R/p).read_text(encoding='utf8')
NAMES=(
    'addItemsToBag','addItemsToBagExact','addItemBundleToBagExact',
    'removeBagItemsExact','removeFromBag','moveBagItem','setBagItem',
    'setBagItemsAtomic','openGiftBagAtomic',
)
class TreasureBagMutatorsFenced(unittest.TestCase):
 def test_nine_core_suspend_writers_share_character_save_mutex(self):
  txt=src('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  for name in NAMES:
   with self.subTest(method=name):
    self.assertEqual(1,len(re.findall(r'    suspend fun '+name+r'\(',txt)))
    self.assertEqual(1,len(re.findall(r'    private suspend fun '+name+r'Unfenced\(',txt)))
    match=re.search(r'    suspend fun '+name+r'\(.*?\n\s*CharacterPersistenceGuard.withLock\(characterId\) \{ '+name+r'Unfenced\(',txt,re.S)
    self.assertIsNotNone(match)
 def test_mutator_internal_call_paths_avoid_recursive_nonreentrant_mutex(self):
  txt=src('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  body=txt.split('private suspend fun addItemsToBagExactUnfenced(',1)[1].split('private suspend fun addItemBundleToBagExactUnfenced(',1)[0]
  self.assertIn('val results = addItemsToBagUnfenced(',body)
  body=txt.split('private suspend fun openGiftBagAtomicUnfenced(',1)[1].split('suspend fun commitBattlePassClaim(',1)[0]
  self.assertIn('setBagItemsAtomicUnfenced(characterId, changes)',body)
  self.assertNotIn('setBagItemsAtomic(characterId, changes)',body)
 def test_stage_coordinator_uses_identical_guard_and_is_still_unwired(self):
  inv=src('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  coordinator=src('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TreasureBankCacheCoordinator.kt')
  handler=src('app/src/main/kotlin/tw/tsm/app/handlers/TreasureHandler.kt')
  processor=src('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt')
  app=src('app/src/main/kotlin/tw/tsm/app/ServerModule.kt')
  self.assertIn('CharacterPersistenceGuard.withLock(characterId)',coordinator)
  self.assertIn('CharacterPersistenceGuard.withLock(characterId)',inv)
  self.assertIn('if (def.kind == 98)',processor)
  self.assertNotIn('stageBank(',handler)
  self.assertNotIn('TreasureBankCacheCoordinator(',app)
 def test_direct_sync_mutators_remain_unfenced_and_are_not_claimed_secure(self):
  inv=src('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  self.assertIn('fun setBagItemSync(',inv)
  self.assertIn('fun addItemDirectSync(',inv)
  self.assertIn('suspend fun depositToBank(',inv)
  self.assertIn('suspend fun withdrawFromStorage(',inv)
  # These are still blockers for turning on banking.
 def test_kotlin_parallel_writers_are_exercised(self):
  test=src('tests/src/test/kotlin/tw/tsm/tests/DvtTreasureBankCacheCoordinatorTest.kt')
  for name in ('bagWriterCannotInterleaveWithStagedSqlCommit',
               'removeAndDirectSetCannotPassBankSettlementLock',
               'atomicBagChangeWaitsUntilTreasureBankAndCacheArePublished'):
   self.assertIn(name,test)
if __name__=='__main__': unittest.main()
