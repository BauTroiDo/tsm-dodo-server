"""Quest12509/10 source-proof 10 rods source→turn-in; /14 original quiz NPC."""
import hashlib
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps,audit
from eve_tool import EveArchive
from patch_dvt_12509_rod_collection_and_riddle_20261012 import (
 QUEST,ROD,ROD_SOURCE,ROD_TURNIN,QUIZ,packed_condition,offsets,patch,source_verified,effect
)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="3128277213fd51bbf531a5b5b09f1558ab0265b638ddef680bc1211d00ce3788"

class DvtNoelRodAndRiddleNavigation(unittest.TestCase):
 def test_rod_collection_npc_original_eve_grants_and_turnin_consumes_ten(self):
  self.assertEqual(source_verified(EVE),(ROD_SOURCE,ROD_TURNIN,QUIZ))
  scene=EveArchive(EVE).parse_scene(49946)
  acq=scene["events"][19]["conditions"]
  grant=next(c for c in acq if c["no"]==1)
  self.assertTrue(any(effect(x,0,1,ROD,1,1) for x in grant["results"]))
  hand=scene["events"][20]["conditions"]
  success=next(c for c in hand if c["no"]==10)
  self.assertTrue(any(effect(x,0,1,ROD,1,-10) for x in success["results"]))
  self.assertTrue(any(effect(x,0,2,QUEST,1,1) for x in success["results"]))
 def test_quest_step10_routes_to_source_then_switches_to_original_handin_at_ten(self):
  steps={s["step"]:s for s in read_steps(DATA) if s["mission"]==QUEST}
  self.assertEqual(steps[10]["conditions"][0],(3,ROD,10,ROD_SOURCE))
  self.assertFalse(any(c[0] for c in steps[10]["conditions"][1:]))
  self.assertEqual(steps[10]["end"],ROD_TURNIN)
  self.assertEqual(steps[9]["end"],(49946,1770,1530,1,9))
  source=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf8")
  self.assertIn("doneCondition = Item.GetItemCount(condition.id) >= condition.count;",source)
  self.assertIn("local sceneId = step.endSceneId;",source)
 def test_riddle_destination_is_original_visible_npc_with_correct_answer_quest_advance(self):
  steps={s["step"]:s for s in read_steps(DATA) if s["mission"]==QUEST}
  self.assertEqual(steps[14]["end"],QUIZ)
  e=EveArchive(EVE).parse_scene(49946)["events"][21]
  self.assertEqual(e["when_happen"],[True,False,False,False])
  keys={c["param"] for c in e["conditions"] if c["class"]==10 and
    any(effect(x,0,2,QUEST,1,1) for x in c["results"])}
  self.assertEqual(keys,{7,8,9,10,11,12})
  self.assertEqual(steps[15]["end"],(49946,590,310,1,12))
 def test_packed_offsets_and_source_validity(self):
  data=DATA.read_bytes()
  self.assertEqual(hashlib.sha256(data).hexdigest(),SHA)
  pts=offsets(data)
  self.assertEqual(data[pts[10][0]:pts[10][0]+12],packed_condition())
  self.assertEqual(audit(DATA,EVE,only_mission=QUEST)["missing_npcs"],[])
  self.assertEqual(audit(DATA,EVE,only_mission=QUEST)["missing_scenes"],[])
 def test_idempotent_and_does_not_rewrite_any_unrelated_quest(self):
  data=DATA.read_bytes()
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),data)

if __name__=="__main__":
 unittest.main()
