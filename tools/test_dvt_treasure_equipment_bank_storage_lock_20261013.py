"""Guard BAG, equipment, bank, storage and reward writers under one mutex.

Original DVT UI permissions and C123 transfer remain intact; no new grant.
"""
import re,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p):return (ROOT/p).read_text(encoding='utf8')
METHODS=(
'addRewardsToBagExact','commitBattlePassClaim',
'equipItem','unequipItem',
'depositToBank','withdrawFromBank',
'depositToStorage','withdrawFromStorage',
)
class TreasureBagStorageFence(unittest.TestCase):
 def test_eight_multi_container_and_reward_mutators_use_character_guard(self):
  src=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  for name in METHODS:
   with self.subTest(name=name):
    self.assertEqual(src.count('    suspend fun '+name+'('),1)
    self.assertEqual(src.count('    private suspend fun '+name+'Unfenced('),1)
    m=re.search(r'    suspend fun '+name+r'\(.*?\n\s*CharacterPersistenceGuard.withLock\(characterId\) \{ '+name+r'Unfenced\(',src,re.S)
    self.assertIsNotNone(m)
 def test_nested_exact_reward_add_does_not_reacquire_nonreentrant_lock(self):
  src=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  block=src.split('private suspend fun addRewardsToBagExactUnfenced(',1)[1].split('fun hasNpcSecondReincarnationToken(',1)[0]
  self.assertIn('addItemsToBagUnfenced(characterId, itemId, qty)',block)
  self.assertNotIn('addItemsToBag(characterId, itemId, qty)',block)
 def test_bank_and_storage_transfers_keep_original_business_logic(self):
  src=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  for name in ('depositToBank','withdrawFromBank','depositToStorage','withdrawFromStorage'):
   block=src.split('private suspend fun '+name+'Unfenced(',1)[1]
   self.assertIn('inventoryRepo.saveItem(',block[:4300])
  self.assertIn('canDepositToStorage(bagItem)',src)
  self.assertIn('EquipSlot.isBasicEquip(fitType)',src)
 def test_coordinator_and_live_guards_still_fail_closed(self):
  app=read('app/src/main/kotlin/tw/tsm/app/ServerModule.kt')
  handler=read('app/src/main/kotlin/tw/tsm/app/handlers/TreasureHandler.kt')
  use=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt')
  self.assertNotIn('TreasureBankCacheCoordinator(',app)
  self.assertNotIn('stageBank(',handler)
  self.assertIn('if (def.kind == 98)',use)
 def test_banking_race_with_bank_and_autosave_is_exercised_in_real_kotlin(self):
  tests=read('tests/src/test/kotlin/tw/tsm/tests/DvtTreasureBankCacheCoordinatorTest.kt')
  for name in ('bankDepositCannotRaceTreasureBankSettlement',
               'bankWithdrawalAndLogoutWaitUntilCardSettlementFinishes'):
   self.assertIn(name,tests)
 def test_incomplete_sync_and_money_paths_are_not_claimed_safe(self):
  src=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt')
  self.assertIn('fun setBagItemSync(',src)
  self.assertIn('fun addItemDirectSync(',src)
  money=read('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MoneySystem.kt')
  self.assertIn('moneyMap',money)
if __name__=='__main__':unittest.main()
