"""Naihe approach/Li Si stage7 source guides and conditional-navigation audit."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_11336_12158_source_waypoints_20261012 import (
 TARGETS, verify_source, patch, sha, effect)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="ab39587a5dacbd65ca60490bf58c5c3da680e826b72b195fc69f80c388f4ec07"
class OriginalNaiheQinGuide(unittest.TestCase):
 def test_original_two_npc_eve_owners_and_source_gates(self):
  self.assertEqual(verify_source(EVE),TARGETS)
  a=EveArchive(EVE)
  for sid,eid,npcid,x,y in ((60801,3,27074,2950,360),(14661,3,40107,270,420)):
   with self.subTest(scene=sid):
    sc=a.parse_scene(sid)
    npc=next(n for n in sc["npcs"] if n["id"]==1)
    self.assertEqual((npc["npc_id"],npc["x"],npc["y"],npc["close"]),(npcid,x,y,False))
    self.assertIn(eid,npc["events"])
    self.assertEqual(sc["events"][eid]["when_happen"],[True,False,False,False])
 def test_naihe_intro_does_not_fabricate_progress_on_arrival(self):
  cs=EveArchive(EVE).parse_scene(60801)["events"][3]["conditions"]
  self.assertTrue(any(c["class"]==2 and c["param"]==11336 and
   c["pstyle"]==2 and c["ops"]==0 and c["val"]==0 and
   effect(c,0,2,11336,1,1) for c in cs))
  self.assertTrue(any(c["class"]==2 and c["param"]==11336 and
   c["pstyle"]==1 and c["ops"]==2 and c["val"]==0 for c in cs))
 def test_qin_stage_seven_battle_outcome_is_original_completion(self):
  cs=EveArchive(EVE).parse_scene(14661)["events"][3]["conditions"]
  self.assertTrue(any(c["class"]==2 and c["param"]==12158 and
   c["pstyle"]==1 and c["ops"]==5 and c["val"]==7 for c in cs))
  self.assertTrue(any(c["class"]==8 and c["param"]==9 and c["pstyle"]==1 and
   effect(c,0,2,12158,3,0) and effect(c,0,2,12159,1,1) for c in cs))
 def test_two_end_fields_untouched_neighbors_and_checksum(self):
  rows={(r["mission"],r["step"]):r for r in read_steps(DATA)}
  for key,dest in TARGETS.items():
   self.assertEqual(rows[key]["end"],dest)
   self.assertFalse(any(c[0] for c in rows[key]["conditions"]))
  self.assertEqual(rows[(11336,2)]["end"],(60806,1040,380,1,1))
  self.assertEqual(rows[(12158,6)]["end"],(14649,510,270,1,1))
  original=DATA.read_bytes()
  self.assertEqual(sha(original),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),original)
 def test_condition_target_guides_have_separate_limited_audit_classification(self):
  rep=scan(EVE,DATA)
  self.assertEqual(len(rep["missing_end_targets"]),67)
  self.assertEqual(len(rep["pending_condition_guided_end_targets"]),8)
  self.assertEqual(len(rep["missing_end_without_pending_condition_guidance"]),59)
  special={(r["mission"],r["step"]) for r in rep["pending_condition_guided_end_targets"]}
  self.assertEqual(special,{(12158,s) for s in range(1,6)}|{(13047,s) for s in (1,3,5)})
  for row in rep["pending_condition_guided_end_targets"]:
   self.assertEqual(row["scope"],"only_while_a_source_condition_is_unmet")
   self.assertTrue(all(t[0] and (t[1] or t[2]) for t in row["targets"]))
   self.assertTrue(all(k in (1,3) for k in row["condition_kinds"]))
 def test_markstep_global_integrity(self):
  rep=audit(DATA,EVE)
  self.assertEqual(rep["markstep_rows"],4505)
  self.assertEqual(rep["targets"]["located"],5928)
  self.assertFalse(rep["missing_scenes"])
  self.assertFalse(rep["missing_npcs"])
  self.assertFalse(rep["missing_doors"])
  self.assertEqual(sum(r["end"]==(0,0,0,0,0) for r in read_steps(DATA)),63)
if __name__=="__main__":unittest.main()
