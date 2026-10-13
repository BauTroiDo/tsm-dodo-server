"""Proof and regression: only the original correct A Dou stage1 gets follower target."""
import tempfile,unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_12444_real_adou_navigation_20261012 import (
 KEY,DEST,patch,verify_eve,verify_steps,has_follow_attachment,sha)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="11bba0ad361ff96266be77db1a3b5d6fcac37f82cd0345fb0c44777e8dbc1df2"
class UniqueOriginalAdouGuidance(unittest.TestCase):
 def test_original_unique_source_follower_event(self):
  self.assertEqual(verify_eve(EVE),DEST)
  arc=EveArchive(EVE)
  owners=[]
  for sid in arc.scenes:
   scene=arc.parse_scene(sid)
   for eid,ev in scene["events"].items():
    for c in ev["conditions"]:
     if has_follow_attachment(c):
      own=[("npc",n["id"],n["npc_id"],n["x"],n["y"])
           for n in scene["npcs"] if eid in n["events"]]
      owners.append((sid,eid,c["no"],c["class"],c["param"],c["pstyle"],c["ops"],c["val"],own))
  self.assertEqual(owners,[(54947,1,7,2,12444,1,5,1,
                             [("npc",1,38265,1050,540)])])
 def test_exact_condition1_and_three_decoy_stages_preserved(self):
  steps=read_steps(DATA)
  self.assertTrue(verify_steps(steps))
  rows={(s["mission"],s["step"]):s for s in steps}
  self.assertEqual(rows[KEY]["conditions"][0],(1,38265,1,DEST))
  for stage in (1,2,3,4):
   self.assertEqual(rows[(12444,stage)]["end"],(54922,1310,960,1,1))
  for stage in (2,3,4):
   self.assertEqual(rows[(12444,stage)]["conditions"][0],
                    (1,38265,1,(0,0,0,0,0)))
 def test_guarded_idempotence_and_reject_unknown_sha(self):
  original=DATA.read_bytes()
  self.assertEqual(sha(original),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),original)
  with tempfile.TemporaryDirectory() as td:
   corrupted=Path(td)/"unknown.dat"
   content=bytearray(original);content[400]^=1;corrupted.write_bytes(content)
   with self.assertRaises(ValueError):patch(corrupted,EVE,False)
   self.assertEqual(corrupted.read_bytes(),content)
 def test_audit_no_invalid_references(self):
  rep=audit(DATA,EVE)
  self.assertEqual(rep["markstep_rows"],4505)
  self.assertEqual(rep["targets"]["located"],5935)
  self.assertFalse(rep["missing_scenes"])
  self.assertFalse(rep["missing_npcs"])
  self.assertFalse(rep["missing_doors"])
  blocked=scan(EVE,DATA)
  self.assertEqual(len(blocked["missing_end_targets"]),63)
  self.assertEqual(len(blocked["pending_condition_guided_end_targets"]),8)
  self.assertEqual(len(blocked["missing_end_without_pending_condition_guidance"]),55)
  self.assertEqual(len(blocked["unlocated_condition_targets"]),84)
if __name__=="__main__":
 unittest.main()
