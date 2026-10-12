"""Keep ordinary DVT Treasure item stacks separate from the C/S123 banked count.

The Eve scan is scoped to resultClass1 item parameters only. A missing match
does NOT prove no card acquisition from shops/events/other protocols.
"""
import unittest
from pathlib import Path
from audit_dvt_treasure_tables_20261012 import load_treasure_item_catalog
from audit_dvt_treasure_card_acquisition_20261013 import scan
ROOT=Path(__file__).resolve().parents[1]
def get(path):return (ROOT/path).read_text(encoding="utf8")
class DvtTreasureAcquisitionIsolation(unittest.TestCase):
 def test_exact_114_item_identity_links_from_original_25800_item_table(self):
  index=load_treasure_item_catalog()
  self.assertEqual(len(index),114)
  self.assertEqual(set(index),set(range(1,115)))
  self.assertEqual({itemid for itemid,_ in index.values()},set(range(25601,25715)))
 def test_original_lua_keeps_item_bag_and_treasure_bank_distinct(self):
  lua=get("lua/Logic/Item.lua")
  func=lua.split("function Item.GetItemCount(itemId)",1)[1].split("function Item.GetItemIndex(",1)[0]
  self.assertIn("this.GetBag(EThings.Bag)",func)
  self.assertIn("count = count + v.quant",func)
  tres=get("lua/Logic/Treasure.lua")
  f=tres.split("function Treasure.GetCardCount(data, lv)",1)[1].split("function Treasure.GetAllBonus()",1)[0]
  self.assertIn("local count = data.count",f)
  self.assertIn("count = count - data.lvData[i].count",f)
  protocol=get("lua/Common/protocal.lua")
  f=protocol.split("protocolTable[123][1] = function(data)",1)[1].split("protocolTable[123][2] = function(data)",1)[0]
  self.assertIn("Treasure.treasureItems[id].count = quant",f)
  self.assertNotIn("Item.GetItemCount",f)
 def test_cache_auto_save_can_overwrite_independently_committed_sql(self):
  inv=get("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/InventorySystem.kt")
  money=get("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MoneySystem.kt")
  repo=get("persistence/src/main/kotlin/tw/tsm/persistence/repositories/TreasureRepositoryImpl.kt")
  self.assertIn("bags[characterId]?.let { inventoryRepo.saveAllItems(characterId, EThings.BAG, it) }",inv)
  self.assertIn("inventoryRepo.saveMoney(characterId, arr[0].toInt(), arr[1].toInt())",money)
  self.assertIn("CharacterMoney.update(",repo)
  self.assertIn("Inventories.update(",repo)
  # This is an explicit blocker, not a test endorsing stale cache behavior.
  handler=get("app/src/main/kotlin/tw/tsm/app/handlers/TreasureHandler.kt")
  self.assertNotIn("purchaseLevel(",handler)
  wiring=get("app/src/main/kotlin/tw/tsm/app/ServerModule.kt")
  self.assertNotIn("TreasureRepositoryImpl(",wiring)
 def test_original_eve_item_results_have_no_direct_treasure_card_param(self):
  mentions,counts=scan()
  self.assertGreater(sum(counts.values()),8000)
  self.assertEqual(mentions,[])
  # This only audits original Eve resultClass=1 item param references;
  # other acquisition sources may exist and require separate study.
 def test_kotlin_source_index_has_no_bag_to_bank_auto_grant(self):
  source=get("data/src/main/kotlin/tw/tsm/data/loaders/TreasureCardSourceIndex.kt")
  self.assertIn("SOURCE_TREASURE_ITEM_KIND = 98",source)
  self.assertIn("fun unbankedBagCopies(",source)
  self.assertIn("fun bankedSpendableCopies(",source)
  self.assertIn("state.cardCopiesTotal - invested",source)
  self.assertNotIn("insert {",source)
  self.assertNotIn("upsert",source)
  self.assertNotIn("subtractGold",source)
if __name__=="__main__":unittest.main()
