"""DVT12509/12 source cave door2 restored while alternative door3 preserved."""
import hashlib,unittest
from pathlib import Path
from audit_dvt_quest_navigation import audit,read_steps
from eve_tool import EveArchive
from patch_dvt_12509_cave_stage12_door_20261012 import END,patch,verify_source
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="e48a4b7f50f6dfc4d3b902279bdda5efea0c783c3eb46f7c300a80db1bec13ad"
class CaveReturnSteps(unittest.TestCase):
 def test_source_scene_entry_opens_both_genuine_quest_stage12_cave_doors(self):
  self.assertEqual(verify_source(EVE),END)
  a=EveArchive(EVE)
  outer=a.parse_scene(49946)
  self.assertEqual(outer["scene_infos"][2]["target_scene"],49947)
  self.assertEqual(outer["scene_infos"][3]["target_scene"],49948)
  stage=next(c for c in outer["events"][2]["conditions"] if c["class"]==2 and c["param"]==12509 and c["val"]==12 and c["ops"]==5)
  self.assertTrue(all(any(z["type"]==2 and z["class"]==7 and z["param"]==door for z in stage["results"]) for door in (2,3)))
 def test_original_stage12_uses_existing_stage13_door_and_preserves_quiz(self):
  stages={s["step"]:s for s in read_steps(DATA) if s["mission"]==12509}
  self.assertEqual(stages[12]["end"],END)
  self.assertEqual(stages[13]["end"],END)
  self.assertEqual(stages[14]["end"],(49946,2250,220,1,11))
  self.assertEqual(stages[11]["end"],(49944,370,730,1,3))
  self.assertFalse(any(z[0] for z in stages[12]["conditions"]))
  report=audit(DATA,EVE,only_mission=12509)
  self.assertEqual((report["missing_scenes"],report["missing_npcs"],report["missing_doors"]),([],[],[]))
 def test_idempotent_and_exact_expected_sha(self):
  raw=DATA.read_bytes()
  self.assertEqual(hashlib.sha256(raw).hexdigest(),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),raw)
if __name__=="__main__":unittest.main()
