"""Quest12636 11-step Chu oil trace + quest12650 first two real Door Eve nav targets."""
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps,audit
from eve_tool import EveArchive
from patch_dvt_12636_12650_oil_trace_door_routes_20261012 import PROOFS,check_eve,patch,sha

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="5d89bbc211ae6c1cbc9cf55e846cbb13634adc8b25cd081897ba7d11a28896f0"

class ChuOilFootprintOriginalDoorNavigation(unittest.TestCase):
 def test_exact_source_13_quest_stage_gated_door_triggers(self):
  self.assertEqual(check_eve(EVE),PROOFS)
  self.assertEqual(len(PROOFS),13)
  self.assertEqual([i for q,i in sorted(PROOFS) if q==12636],list(range(1,12)))
  self.assertEqual([i for q,i in sorted(PROOFS) if q==12650],[1,2])
  archive=EveArchive(EVE)
  for (mission,step),(sid,x,y,kind,did,eid) in PROOFS.items():
   with self.subTest(quest=mission,stage=step):
    scene=archive.parse_scene(sid)
    door=next(d for d in scene["doors"] if d["id"]==did)
    self.assertEqual((door["x"],door["y"]),(x,y))
    self.assertIn(eid,door["events"])
    self.assertFalse(any(eid in npc["events"] for npc in scene["npcs"]))
    ev=scene["events"][eid]
    self.assertEqual(ev["when_happen"],[False,True,False,False])
    actual=[c for c in ev["conditions"] if c["class"]==2 and c["param"]==mission
          and c["pstyle"]==1 and c["ops"]==5 and c["val"]==step]
    self.assertEqual(len(actual),1)
    self.assertEqual(actual[0]["and_num"],1)
    self.assertTrue(any(z["type"]==0 and z["class"]==2 and z["param"]==mission
          and z["pstyle"]==1 and z["val"]==1 for z in actual[0]["results"]))
    self.assertFalse(any(z["type"]==0 and z["class"] in (11,13,15)
          for z in actual[0]["results"]))
 def test_exact_13_markstep_end_positions_and_original_returns(self):
  rows={(r["mission"],r["step"]):r for r in read_steps(DATA)}
  for key,target in PROOFS.items():
   with self.subTest(key=key):
    self.assertEqual(rows[key]["end"],target[:5])
    self.assertEqual(target[3],2)
    self.assertFalse(any(c[0] for c in rows[key]["conditions"]))
  self.assertEqual(rows[(12636,12)]["end"],(49955,410,370,1,1))
  self.assertEqual(rows[(12650,3)]["end"],(49955,410,370,1,1))
 def test_shared_door_must_retain_both_independent_quest_eves(self):
  sc=EveArchive(EVE).parse_scene(49957)
  door=next(d for d in sc["doors"] if d["id"]==4)
  self.assertEqual(door["events"],[12,4,5,8])
  self.assertIn(5,door["events"])
  self.assertIn(8,door["events"])
  self.assertNotEqual(PROOFS[(12636,1)][5],PROOFS[(12650,1)][5])
  for eid,quest in ((5,12636),(8,12650)):
   ev=sc["events"][eid]
   self.assertTrue(any(c["class"]==2 and c["param"]==quest and c["val"]==1
       for c in ev["conditions"]))
 def test_exact_checksum_and_idempotent_patch(self):
  before=DATA.read_bytes()
  self.assertEqual(sha(before),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),before)
 def test_zero_invalid_scene_or_door_and_census(self):
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5921)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])

if __name__=="__main__":unittest.main()
