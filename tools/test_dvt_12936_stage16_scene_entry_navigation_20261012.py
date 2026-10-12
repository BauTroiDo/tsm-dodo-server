"""Quest12936/16 route into authentic 12403 map-entry Eve10 then 17 original NPC."""
import hashlib
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps,audit
from eve_tool import EveArchive
from patch_dvt_12936_stage16_scene_entry_20261012 import TARGET,sha,original_source_proof
from patch_dvt_quest_door_trigger_corrections_20261012 import TARGETS, patch as corrected_patch
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da"
class Dvt12936SceneEntryContinuation(unittest.TestCase):
 def test_original_door_graph_has_door7_stage16_result_and_npc17(self):
  self.assertEqual(original_source_proof(EVE),TARGET)
  a=EveArchive(EVE)
  sc=a.parse_scene(12403)
  self.assertEqual(sc["events"][10]["when_happen"],[False,True,False,False])
  self.assertIn(10,next(d for d in sc["doors"] if d["id"]==7)["events"])
  self.assertFalse(any(10 in n["events"] for n in sc["npcs"]))
  self.assertTrue(any(n["id"]==8 and n["npc_id"]==12059 and n["close"]
                      and 11 in n["events"] for n in sc["npcs"]))
  condition=next(c for c in sc["events"][10]["conditions"] if
     c["class"]==2 and c["param"]==12936 and c["pstyle"]==1 and
     c["ops"]==5 and c["val"]==16)
  self.assertTrue(any(x["type"]==0 and x["class"]==2 and x["param"]==12936
                      and x["pstyle"]==1 and x["val"]==1 for x in condition["results"]))
 def test_quest16_matches_original_stage17_arrival_without_invented_objective(self):
  stages={s["step"]:s for s in read_steps(DATA) if s["mission"]==12936}
  self.assertEqual(stages[16]["end"],TARGETS[(12936,16)])
  self.assertEqual(stages[17]["end"],TARGET)
  self.assertEqual(stages[15]["end"],(12401,1150,220,1,9))
  self.assertFalse(any(z[0] for z in stages[16]["conditions"]))
  report=audit(DATA,EVE,only_mission=12936)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
 def test_source_guarded_patch_is_idempotent(self):
  raw=DATA.read_bytes()
  self.assertEqual(sha(raw),SHA)
  self.assertEqual(corrected_patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),raw)
if __name__=="__main__":unittest.main()
