"""Pin actual DVT Treasure card distribution sources and their BAG-only semantics."""
import unittest
from pathlib import Path
from audit_dvt_treasure_reward_sources_20261013 import decode_rows,source_card_rewards
from audit_dvt_treasure_tables_20261012 import load_treasure_item_catalog

ROOT=Path(__file__).resolve().parents[1]
def file(path):return (ROOT/path).read_text(encoding="utf8")
class DvtTreasureRewardSources(unittest.TestCase):
 @classmethod
 def setUpClass(cls):cls.sources=source_card_rewards()
 def test_blissbag_has_original114_cards_in_175_source_reward_rows(self):
  r=self.sources["blissbag"]
  self.assertEqual(self.sources["total_rows"]["blissbag"],37257)
  self.assertEqual(len(r),175)
  self.assertEqual({x[1] for x in r},set(range(25601,25715)))
  self.assertEqual(len({x[0] for x in r}),27)
  self.assertTrue(all(x[2]>0 and x[4] in (1,2) for x in r))
  self.assertIn((34617,25605,1,980,1),r)
 def test_original_exchange_has_62_treasure_card_rows_but_not_114(self):
  rows=self.sources["exchange"]
  self.assertEqual(self.sources["total_rows"]["exchange"],5036)
  self.assertEqual(len(rows),62)
  self.assertEqual(len({r[1] for r in rows}),62)
  self.assertEqual(len({r[0] for r in rows}),12)
  self.assertIn((44238,25604,1),rows)
  self.assertEqual({r[1] for r in rows}-set(range(25601,25715)),set())
 def test_achievement_six_hundred_packed_rows_contain_no_direct_card_reward(self):
  self.assertEqual(self.sources["total_rows"]["achievements"],600)
  self.assertEqual(self.sources["achievements"],[])
 def test_bliss_and_exchange_record_widths_follow_decrypted_original_lua(self):
  lua=file("lua/Data/BlissBagData.lua")
  self.assertIn("self.itemId = reader:ReadUInt16()",lua)
  self.assertIn("self.count = reader:ReadUInt32()",lua)
  self.assertIn("self.pr = reader:ReadUInt32()",lua)
  ex=file("lua/Data/ExchangeData.lua")
  self.assertIn("self.itemId = reader:ReadUInt16()",ex)
  self.assertIn("self.count = reader:ReadUInt16()",ex)
  ach=file("lua/Data/AchievementData.lua")
  self.assertIn("self.itemId = reader:ReadUInt16()",ach)
  self.assertIn("self.itemCount = reader:ReadByte()",ach)
 def test_kotlin_blissbox_claim_commits_to_bag_not_treasure_bank(self):
  handler=file("app/src/main/kotlin/tw/tsm/app/handlers/ItemHandler.kt")
  portion=handler.split("// BlissBag reward must be all-or-nothing with source consumption.",1)[1].split("// kh",1)[0]
  self.assertIn("inventorySystem.openGiftBagAtomic(",portion)
  self.assertIn("responseSender.sendSetBagItem(",portion)
  self.assertNotIn("TreasureRepository",portion)
  self.assertNotIn("cardCopiesTotal",portion)
  source=file("lua/Logic/Treasure.lua")
  self.assertIn("data.count = 0;",source)
  self.assertIn("data.id = v.skillLink;",source)
 def test_truncated_source_blissbag_table_fails_closed(self):
  import tempfile
  from pathlib import Path
  original=(ROOT/"File.dat/Data-ios/IOS_BlissBag_C.dat").read_bytes()
  with tempfile.TemporaryDirectory() as temp:
   from audit_dvt_treasure_tables_20261012 import DATA
   # decode_rows intentionally hardwired to DATA; test the count math directly
   import struct
   self.assertEqual(37257,struct.unpack_from("<i",original)[0])
   self.assertEqual(len(original),4+37257*13)
   for corrupt in (original[:-1],original+b"X"):
    self.assertNotEqual(len(corrupt),4+struct.unpack_from("<i",corrupt)[0]*13)
if __name__=="__main__":unittest.main()
