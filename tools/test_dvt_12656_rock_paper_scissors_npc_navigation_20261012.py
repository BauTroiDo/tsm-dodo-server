"""Original DVT quest12656 minigame NPC navigation for all 12 rounds/stages."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_12656_rock_paper_scissors_npc_navigation_20261012 import (
 QUEST,DEST,proof,patch,sha,effect)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
EXPECTED_SHA="91684c0a287e24b31a5e251322e19a69e96f6961e28d4c90bbadcc40055162f0"
class PigMinigameOriginalNpcRoutes(unittest.TestCase):
 def test_authentic_49960_npc38093_owns_both_source_mission_eves(self):
  self.assertEqual(proof(EVE),{i:DEST for i in range(1,13)})
  scene=EveArchive(EVE).parse_scene(49960)
  npc=next(n for n in scene["npcs"] if n["id"]==1)
  self.assertEqual((npc["npc_id"],npc["x"],npc["y"],npc["close"],npc["events"]),
                   (38093,610,360,True,[4,5]))
  self.assertFalse(any(4 in d["events"] or 5 in d["events"] for d in scene["doors"]))
  self.assertEqual(scene["events"][4]["when_happen"],[True,False,False,False])
  self.assertEqual(scene["events"][5]["when_happen"],[True,False,False,False])
 def test_stage1_activation_and_stage12_item_reward_keep_original_gates(self):
  scene=EveArchive(EVE).parse_scene(49960)
  start,finish=scene["events"][5],scene["events"][4]
  self.assertTrue(any(c["class"]==2 and c["param"]==12656 and
    c["pstyle"]==2 and c["ops"]==0 and c["val"]==0 and
    effect(c,0,2,12656,1,1) for c in start["conditions"]))
  reward=next(c for c in finish["conditions"] if
    c["class"]==2 and c["param"]==12656 and c["pstyle"]==1 and
    c["ops"]==5 and c["val"]==12 and effect(c,0,1,29471,1,10))
  self.assertTrue(effect(reward,0,2,12656,3,0))
  self.assertTrue(any(c["class"]==7 and c["param"]==2 and c["pstyle"]==2
                      and c["to_result"]==reward["no"] for c in finish["conditions"]))
 def test_midrounds_do_not_fabricate_ten_stage_specific_eve_results(self):
  finish=EveArchive(EVE).parse_scene(49960)["events"][4]
  self.assertFalse(any(c["class"]==2 and c["param"]==12656 and
    c["pstyle"]==1 and c["ops"]==5 and 2<=c["val"]<=11
    for c in finish["conditions"]))
  mids=[c for c in finish["conditions"] if c["class"]==2 and
    c["param"]==12656 and c["pstyle"]==1 and c["ops"]==2 and c["val"]==1]
  self.assertEqual({c["and_num"] for c in mids},{3,4})
  for c in mids:
   self.assertTrue(any(k["class"]==2 and k["param"]==12656 and
       k["pstyle"]==1 and k["ops"]==1 and k["val"]==12 and
       k["to_result"]==c["no"] for k in finish["conditions"]))
 def test_all_twelve_navigation_targets_are_only_clicknpc_not_auto_quest_completion(self):
  rows=[x for x in read_steps(DATA) if x["mission"]==QUEST]
  self.assertEqual(len(rows),12)
  self.assertEqual([x["step"] for x in rows],list(range(1,13)))
  for row in rows:
   with self.subTest(step=row["step"]):
    self.assertEqual(row["end"],DEST)
    self.assertFalse(any(c[0] for c in row["conditions"]))
 def test_sha_idempotence_and_static_full_corpus_audit(self):
  before=DATA.read_bytes()
  self.assertEqual(sha(before),EXPECTED_SHA)
  self.assertEqual(patch(DATA,EVE,False),EXPECTED_SHA)
  self.assertEqual(DATA.read_bytes(),before)
  a=audit(DATA,EVE)
  self.assertEqual(a["markstep_rows"],4505)
  self.assertEqual(a["targets"]["located"],5845)
  self.assertFalse(a["missing_scenes"])
  self.assertFalse(a["missing_npcs"])
  self.assertFalse(a["missing_doors"])
  self.assertEqual(sum(x["end"]==(0,0,0,0,0) for x in read_steps(DATA)),146)
  blockers=scan(EVE,DATA)
  self.assertEqual(len(blockers["missing_end_targets"]),150)
  self.assertEqual(len(blockers["unlocated_condition_targets"]),87)
if __name__=="__main__":unittest.main()
