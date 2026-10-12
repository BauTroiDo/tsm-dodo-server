"""Source-proven DVT quest10122 Dong Quan spy and quest10290 cave NPC autonav."""
import hashlib
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps,audit
from eve_tool import EveArchive
from patch_dvt_10122_10290_source_npc_routes_20261012 import DEST,original_eve_verified,patch
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="18291b043c1384d3cbc10305230d2e9798877c8dd5f6c89802bd677832273234"
class OriginalSourceNpcReturnDestinations(unittest.TestCase):
 def test_visible_quest_npcs_and_exact_original_eve_branches(self):
  self.assertEqual(original_eve_verified(EVE),DEST)
  e=EveArchive(EVE)
  spy=e.parse_scene(11002)
  self.assertEqual([(n["id"],n["npc_id"],n["close"]) for n in spy["npcs"]
    if 14 in n["events"]],[(5,17018,False)])
  self.assertEqual(spy["scene_infos"][6]["target_scene"],11301)
  cave=e.parse_scene(14566)
  self.assertEqual([(n["id"],n["npc_id"],n["close"]) for n in cave["npcs"]
    if 2 in n["events"]],[(1,35013,False)])
 def test_both_quest_steps_have_real_targets_and_no_acquisition_override(self):
  actual={(x["mission"],x["step"]):x for x in read_steps(DATA) if (x["mission"],x["step"]) in DEST}
  self.assertEqual(set(actual),set(DEST))
  for key,endpoint in DEST.items():
   self.assertEqual(actual[key]["end"],endpoint)
   self.assertFalse(any(c[0] for c in actual[key]["conditions"]))
   result=audit(DATA,EVE,only_mission=key[0])
   self.assertEqual(result["missing_scenes"],[])
   self.assertEqual(result["missing_npcs"],[])
   self.assertEqual(result["missing_doors"],[])
  self.assertEqual(hashlib.sha256(DATA.read_bytes()).hexdigest(),SHA)
 def test_repeat_patch_read_only_and_unchanged_lua(self):
  before=DATA.read_bytes()
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),before)
  lua=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf8")
  self.assertIn("local sceneId = step.endSceneId;",lua)
if __name__=="__main__":unittest.main()
