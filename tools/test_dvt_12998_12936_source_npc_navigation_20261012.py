"""Validate five recovered DVT festival/relationship NPC destinations against true Eve."""
import hashlib
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import audit,read_steps
from eve_tool import EveArchive
from patch_dvt_12998_12936_source_npc_routes_20261012 import (
 TARGETS,PARENTS,sha,original_eve_checks,patch
)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
NEW_SHA="ab39587a5dacbd65ca60490bf58c5c3da680e826b72b195fc69f80c388f4ec07"
class OriginalHiddenFestivalNpcNavigation(unittest.TestCase):
 def test_five_stages_have_exact_original_source_npc_arrivals(self):
  rows={(s["mission"],s["step"]):s for s in read_steps(DATA)}
  self.assertEqual(len(TARGETS),5)
  for key,info in TARGETS.items():
   with self.subTest(mission=key[0],step=key[1]):
    self.assertEqual(rows[key]["end"],tuple(info[:5]))
    self.assertFalse(any(cond[0] for cond in rows[key]["conditions"]))
  for key,(quest,step,index,expected) in PARENTS.items():
   self.assertEqual(rows[(quest,step)]["conditions"][index],expected)
  self.assertEqual(rows[(12998,3)]["end"],(12003,470,520,1,48))
  self.assertEqual(rows[(12936,22)]["end"],(12421,710,600,1,11))
 def test_real_stage_gated_eve_advances_original_quest_no_fabricated_spawns(self):
  original_eve_checks(EVE)
  arc=EveArchive(EVE)
  for (qid,step),(sid,x,y,kind,placement,npc,eid) in TARGETS.items():
   with self.subTest(mission=qid,step=step):
    scene=arc.parse_scene(sid)
    entry=next(n for n in scene["npcs"] if n["id"]==placement)
    self.assertEqual((entry["npc_id"],entry["x"],entry["y"],entry["close"]),
                     (npc,x,y,True))
    self.assertIn(eid,entry["events"])
    self.assertEqual(scene["events"][eid]["when_happen"],[True,False,False,False])
    stages=[c for c in scene["events"][eid]["conditions"] if
      c["class"]==2 and c["param"]==qid and c["pstyle"]==1
      and c["ops"]==5 and c["val"]==step]
    self.assertEqual(len(stages),1)
    self.assertTrue(any(z["type"]==0 and z["class"]==2 and
      z["param"]==qid and z["pstyle"]==1 and z["val"]==1
      for z in stages[0]["results"]))
 def test_all_still_reference_real_npc_and_scene_and_preserve_others(self):
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["missing_scenes"],[])
  self.assertEqual(report["missing_npcs"],[])
  self.assertEqual(report["missing_doors"],[])
  self.assertEqual(report["targets"]["located"],5928)
 def test_patch_is_idempotent_and_exact_checksum(self):
  before=DATA.read_bytes()
  self.assertEqual(sha(before),NEW_SHA)
  self.assertEqual(patch(DATA,EVE,False),NEW_SHA)
  self.assertEqual(DATA.read_bytes(),before)
 def test_stage16_entry_script_remains_original_after_route_repair(self):
  rows={(s["mission"],s["step"]):s for s in read_steps(DATA)}
  self.assertEqual(rows[(12936,16)]["end"],(12403,100,400,2,7))
  ev=EveArchive(EVE).parse_scene(12403)["events"][10]
  self.assertEqual(ev["when_happen"],[False,True,False,False])
  self.assertTrue(any(c["class"]==2 and c["param"]==12936 and c["val"]==16 and
    any(z["type"]==0 and z["class"]==2 and z["param"]==12936 and z["val"]==1
        for z in c["results"]) for c in ev["conditions"]))
if __name__=="__main__":unittest.main()
