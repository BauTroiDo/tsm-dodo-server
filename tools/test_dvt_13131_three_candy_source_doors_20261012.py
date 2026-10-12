"""Quest13131 three source-valid Halloween candy trick-or-treat Doors."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_13131_three_candy_source_doors_20261012 import (
 QUEST,SCENE,CHOICES,source_proof,patch,sha,effect)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
EXPECTED_SHA="a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da"
class HalloweenCandySourceDoorRoutes(unittest.TestCase):
 def test_three_authentic_distinct_eve_doors_and_source_gating(self):
  targets=source_proof(EVE)
  self.assertEqual(len(targets),3)
  scene=EveArchive(EVE).parse_scene(SCENE)
  self.assertEqual(len({x[0] for x in CHOICES.values()}),3)
  for step,(did,x,y,eid) in CHOICES.items():
   with self.subTest(stage=step):
    self.assertEqual(targets[step],(SCENE,x,y,2,did))
    door=next(z for z in scene["doors"] if z["id"]==did)
    self.assertEqual((door["x"],door["y"]),(x,y))
    self.assertIn(eid,door["events"])
    ev=scene["events"][eid]
    self.assertEqual(ev["when_happen"],[False,True,False,False])
    self.assertTrue(any(c["class"]==2 and c["param"]==QUEST and
      c["pstyle"]==1 and c["ops"]==2 and c["val"]==1 and
      effect(c,0,2,QUEST,1,1) for c in ev["conditions"]))
 def test_completion_requires_source_stage_four_and_preserves_rewards(self):
  archive=EveArchive(EVE)
  sc=archive.parse_scene(SCENE)
  for eid in (31,32,33):
   with self.subTest(eve=eid):
    e=sc["events"][eid]
    cs=e["conditions"]
    self.assertTrue(any(c["class"]==2 and c["param"]==QUEST and
      c["pstyle"]==1 and c["ops"]==5 and c["val"]==4 and
      effect(c,0,2,QUEST,3,0) and
      effect(c,0,2,13132,1,1) and
      effect(c,0,2,12499,1,1) and
      effect(c,0,1,29433,1,1) for c in cs))
    self.assertEqual({c["val"] for c in cs if
      c["class"]==7 and c["param"]==0 and c["pstyle"]==3 and c["ops"]==6},
      {38030,38029,38028,32003})
 def test_only_three_end_destinations_and_original_girl_stage_one(self):
  rows={r["step"]:r for r in read_steps(DATA) if r["mission"]==QUEST}
  self.assertEqual(sorted(rows),[1,2,3,4])
  self.assertEqual(rows[1]["end"],(54922,390,480,1,27))
  for st,(did,x,y,eid) in CHOICES.items():
   self.assertEqual(rows[st]["end"],(54922,x,y,2,did))
   self.assertFalse(any(c[0] for c in rows[st]["conditions"]))
 def test_exact_sha_guard_idempotence_and_no_extra_bytes(self):
  before=DATA.read_bytes()
  self.assertEqual(sha(before),EXPECTED_SHA)
  self.assertEqual(patch(DATA,EVE,False),EXPECTED_SHA)
  self.assertEqual(DATA.read_bytes(),before)
 def test_corpus_markstep_and_blocker_census(self):
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5934)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
  self.assertEqual(sum(r["end"]==(0,0,0,0,0) for r in read_steps(DATA)),63)
  blockers=scan(EVE,DATA)
  self.assertEqual(len(blockers["missing_end_targets"]),63)
  self.assertEqual(len(blockers["unlocated_condition_targets"]),85)
if __name__=="__main__":unittest.main()
