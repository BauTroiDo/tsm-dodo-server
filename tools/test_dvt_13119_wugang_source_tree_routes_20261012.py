"""Original DVT Wu Gang chopper mission13119: 14 valid source NPC routes."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_13119_wugang_source_tree_routes_20261012 import (
 QUEST,TARGET,ALT_TARGET,source_proof,patch,sha,has)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
EXPECTED="e3ba70127cb69cfa9b51e47864015eca288fa91b56d6d5616b98a203cd793f5c"
class WuGangSourceTreeGuide(unittest.TestCase):
 def test_both_authentic_chopper_npc_sources_and_equivalent_callbacks(self):
  self.assertEqual(source_proof(EVE),{s:TARGET for s in range(1,15)})
  archive=EveArchive(EVE)
  for sid in (54937,54938):
   with self.subTest(scene=sid):
    sc=archive.parse_scene(sid)
    chopper=next(n for n in sc["npcs"] if n["id"]==1)
    self.assertEqual((chopper["npc_id"],chopper["x"],chopper["y"],chopper["close"],chopper["events"]),
                     (38465,1610,820,False,[1]))
    self.assertEqual(sc["events"][1]["when_happen"],[True,False,False,False])
    conds=sc["events"][1]["conditions"]
    for op,delta in ((30,1),(31,2),(32,3)):
     self.assertTrue(any(c["class"]==10 and c["param"]==1 and c["pstyle"]==op
       and has(c,0,2,13119,1,delta) for c in conds))
 def test_source_in_progress_choice_and_threshold_completion_not_auto_arrival(self):
  archive=EveArchive(EVE)
  for sid in (54937,54938):
   with self.subTest(scene=sid):
    cs=archive.parse_scene(sid)["events"][1]["conditions"]
    self.assertTrue(any(c["class"]==2 and c["param"]==13119 and
      c["pstyle"]==1 and c["ops"]==2 and c["val"]==0 and
      any(z["type"]==6 for z in c["results"]) for c in cs))
    self.assertTrue(any(c["class"]==2 and c["param"]==13119 and
      c["pstyle"]==1 and c["ops"]==2 and c["val"]==14 and
      has(c,0,2,13119,3,0) and has(c,0,2,13121,1,1) and
      has(c,0,1,29701,1,50) for c in cs))
 def test_fourteen_waypoints_not_forced_per_step_eve(self):
  rows=[r for r in read_steps(DATA) if r["mission"]==13119]
  self.assertEqual([r["step"] for r in rows],list(range(1,15)))
  for row in rows:
   with self.subTest(stage=row["step"]):
    self.assertEqual(row["end"],TARGET)
    self.assertEqual(row["description"],"幫助吳剛伐桂吧！")
    self.assertFalse(any(c[0] for c in row["conditions"]))
  self.assertNotEqual(TARGET[0],ALT_TARGET[0])
  self.assertEqual(TARGET[1:],ALT_TARGET[1:])
 def test_guarded_checksum_and_second_run_idempotence(self):
  raw=DATA.read_bytes()
  self.assertEqual(sha(raw),EXPECTED)
  self.assertEqual(patch(DATA,EVE,False),EXPECTED)
  self.assertEqual(DATA.read_bytes(),raw)
 def test_corpus_integrity_reduction_and_no_unlocated_new_target(self):
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5924)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
  self.assertEqual(sum(x["end"]==(0,0,0,0,0) for x in read_steps(DATA)),67)
  blockers=scan(EVE,DATA)
  self.assertEqual(len(blockers["missing_end_targets"]),71)
  self.assertEqual(len(blockers["unlocated_condition_targets"]),87)
if __name__=="__main__":unittest.main()
