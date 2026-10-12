"""Regression: Treasure kind98 / ability245 must not vanish via ItemUseProcessor."""
import unittest
from pathlib import Path
from audit_dvt_treasure_card_direct_use_20261013 import treasure_use_records
ROOT=Path(__file__).resolve().parents[1]
def get(file):return (ROOT/file).read_text(encoding="utf8")
class TreasureDirectUseSafety(unittest.TestCase):
 def test_all114_source_cards_have_direct_use245_and_exact_id_map(self):
  rows=treasure_use_records()
  self.assertEqual(len(rows),114)
  self.assertEqual({x[0] for x in rows},set(range(25601,25715)))
  self.assertEqual({x[1] for x in rows},set(range(1,115)))
  self.assertEqual({x[2] for x in rows},{245})
  self.assertEqual({x[3] for x in rows},{1})
 def test_original_lua_declares_treasure_kind98_and_separate_s123_snapshot(self):
  lua=get("lua/Data/ItemData.lua")
  self.assertIn("Treasure = 98",lua)
  treasure=get("lua/Logic/Treasure.lua")
  self.assertIn("v.kind == EItemKind.Treasure",treasure)
  self.assertIn("data.id = v.skillLink",treasure)
  recv=get("lua/Common/protocal.lua")
  self.assertIn("Treasure.treasureItems[id].count = quant",recv)
 def test_server_rejects_ability245_before_generic_consume_or_pet_use(self):
  src=get("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt")
  proc=src.split("fun processUseItem(",1)[1].split("return when (specialAbility)",1)[0]
  self.assertIn("if (def.kind == 98)",proc)
  self.assertIn("return ItemUseResult(success = false, consumed = false)",proc)
  self.assertLess(proc.index("if (def.kind == 98)"),proc.index("if (followIndex > 0)"))
  handler=get("app/src/main/kotlin/tw/tsm/app/handlers/ItemHandler.kt")
  self.assertIn("if (!result.consumed)",handler)
  # The generic no-op path otherwise says success=true and consumed=true!
  fallback=src.split("private fun processGenericAttrItem(",1)[1].split("private fun applyAttribute(",1)[0]
  self.assertIn("return ItemUseResult(success = true, attrChanges = changes)",fallback)
 def test_normal_gift_bag_atomic_redeem_stays_unchanged(self):
  src=get("app/src/main/kotlin/tw/tsm/app/handlers/ItemHandler.kt")
  self.assertIn("inventorySystem.openGiftBagAtomic(",src)
  self.assertIn("responseSender.sendSetBagItem(",src)
if __name__=="__main__":unittest.main()
