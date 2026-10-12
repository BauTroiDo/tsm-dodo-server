"""Original DVT Christmas-tree quest12513 12 stage-specific real NPC38289 waypoints."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import audit,read_steps
from patch_dvt_12513_christmas_tree_source_npc_routes_20261012 import SOURCES,target_rows,proof_source,patch,sha,has
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="16b5a3430afcf0cc8cbf0115b3d4ca8d7035269537fef0a55e9b2db6a3845043"
class ChristmasTreeDvtNpcGuides(unittest.TestCase):
 def test_original_twelve_source_stage_npc_gates(self):
  self.assertEqual(len(SOURCES),12)
  self.assertEqual(proof_source(EVE),target_rows())
  arc=EveArchive(EVE)
  for step,(sid,pid,eid,x,y,values,clue) in SOURCES.items():
   with self.subTest(step=step):
    scene=arc.parse_scene(sid)
    n=next(n for n in scene["npcs"] if n["id"]==pid)
    self.assertEqual((n["npc_id"],n["x"],n["y"],n["close"]),(38289,x,y,True))
    self.assertIn(eid,n["events"])
    self.assertEqual(scene["events"][eid]["when_happen"],[True,False,False,False])
    ev=scene["events"][eid]
    candidates=[c for c in ev["conditions"] if c["class"]==2 and
      c["param"]==12513 and c["pstyle"]==1 and c["ops"]==5 and c["val"]==step and c["and_num"]==2]
    self.assertEqual(len(candidates),2)
    true=[c for c in candidates if has(c,0,1,29465,1,-1)]
    self.assertEqual(len(true),1)
    self.assertTrue(all(has(true[0],0,2,12513,1,v) for v in values))
 def test_every_stage_route_and_santa_original_start_and_return(self):
  stages={r["step"]:r for r in read_steps(DATA) if r["mission"]==12513}
  self.assertEqual(len(stages),14)
  for step,target in target_rows().items():
   with self.subTest(step=step):
    self.assertEqual(stages[step]["end"],target)
    self.assertFalse(any(c[0] for c in stages[step]["conditions"]))
  self.assertEqual(stages[1]["end"],(49945,270,420,1,1))
  self.assertEqual(stages[40]["end"],(49945,270,420,1,1))
 def test_original_tree_item29465_checks_preserved_for_all_twelve_locations(self):
  arc=EveArchive(EVE)
  for step,(sid,pid,eid,x,y,values,clue) in SOURCES.items():
   ev=arc.parse_scene(sid)["events"][eid]
   main=next(c for c in ev["conditions"] if c["class"]==2 and c["param"]==12513
             and c["pstyle"]==1 and c["val"]==step and has(c,0,1,29465,1,-1))
   side=[c for c in ev["conditions"] if c["to_result"]==main["no"] and
         c["class"]==1 and c["param"]==29465 and c["pstyle"]==1]
   self.assertEqual(len(side),1)
   self.assertEqual((side[0]["ops"],side[0]["val"]),(2,0))
 def test_guarded_patch_sha_and_idempotency(self):
  raw=DATA.read_bytes()
  self.assertEqual(sha(raw),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),raw)
 def test_source_valid_no_missing_scene_npc_or_door_after_change(self):
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5907)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
  self.assertEqual(sum(1 for r in read_steps(DATA) if r["end"]==(0,0,0,0,0)),84)
if __name__=="__main__":unittest.main()
