"""Twenty source-confirmed DVT12106 sick soldier medicine delivery destinations."""
import hashlib
import unittest
from pathlib import Path

from audit_dvt_quest_navigation import read_steps,audit
from patch_dvt_12106_poisoned_soldier_delivery_20261012 import (
 MISSION, ITEM, QTY, SOURCE, STAGES, PREFERRED, verify_targets, verify_steps, patch
)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="16b5a3430afcf0cc8cbf0115b3d4ca8d7035269537fef0a55e9b2db6a3845043"

class SourceProven12106PoisonedSoldierNavigation(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.targets,cls.options=verify_targets(EVE)

 def test_twenty_independent_medicine_delivery_waypoints_match_real_eve_soldiers(self):
  self.assertEqual(set(self.targets),set(STAGES))
  rows=verify_steps(DATA,self.targets)
  self.assertEqual(len(rows),20)
  self.assertEqual({s["step"] for s in rows},set(STAGES))
  self.assertEqual({s["conditions"][0] for s in rows},{(3,ITEM,QTY,SOURCE)})
  self.assertTrue(all(s["end"]==self.targets[s["step"]] for s in rows))
  self.assertEqual(hashlib.sha256(DATA.read_bytes()).hexdigest(),SHA)
  self.assertEqual(audit(DATA,EVE,only_mission=MISSION)["missing_npcs"],[])

 def test_source_eve_contains_exactly_two_valid_original_soldiers_per_pair(self):
  self.assertEqual(len(self.options),10)
  self.assertEqual(set(self.options),set(PREFERRED))
  for s,options in self.options.items():
   self.assertEqual(len(options),2)
   self.assertIn(PREFERRED[s],{id for id,x,y in options})
   self.assertEqual(self.targets[s],self.targets[s+1])
   self.assertEqual(self.targets[s][4],PREFERRED[s])
  self.assertEqual({self.targets[s][0] for s in STAGES},{30891,30892})

 def test_data_only_patch_does_not_reapply_or_mutate_any_quest_data(self):
  before=DATA.read_bytes()
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),before)

 def test_original_client_lua_switches_to_delivery_only_after_five_medicines(self):
  lua=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf-8")
  self.assertIn("Item.GetItemCount(condition.id) >= condition.count",lua)
  self.assertIn("local sceneId = step.endSceneId;",lua)
  self.assertIn("if not doneCondition then",lua)

if __name__=="__main__":
 unittest.main()
