"""16 source-verifiable distinct NPC capture routes; static choices, NOT dynamic availability."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_123xx_four_capture_battle_routes_20261012 import (
 ARENAS,source_proof,patch,sha,effect)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da"
class FourBattleCaptureRoutes(unittest.TestCase):
 def test_all_four_arena_scenes_and_four_distinct_original_npcs(self):
  targets=source_proof(EVE)
  self.assertEqual(len(targets),16)
  archive=EveArchive(EVE)
  for q,(sid,npcid,coords) in ARENAS.items():
   with self.subTest(quest=q):
    sc=archive.parse_scene(sid)
    self.assertEqual(len({targets[(q,stage)][4] for stage in range(2,6)}),4)
    for stage in range(2,6):
     n=next(z for z in sc["npcs"] if z["id"]==stage)
     self.assertEqual((n["npc_id"],n["x"],n["y"],n["close"],n["events"]),
                      (npcid,*coords[stage-2],False,[stage]))
     self.assertEqual(targets[(q,stage)],(sid,*coords[stage-2],1,stage))
 def test_each_original_monster_eve_has_battle_victory_and_correct_end_branch(self):
  archive=EveArchive(EVE)
  for q,(sid,npcid,coords) in ARENAS.items():
   sc=archive.parse_scene(sid)
   for step in range(2,6):
    with self.subTest(quest=q,step=step):
     ev=sc["events"][step]
     self.assertEqual(ev["when_happen"],[True,False,False,False])
     self.assertTrue(any(c["class"]==8 and c["param"]==step-1 and
       c["pstyle"]==1 and c["ops"]==0 and effect(c,0,2,q,1,1)
       for c in ev["conditions"]))
     self.assertTrue(any(c["class"]==2 and c["param"]==q and
       c["pstyle"]==1 and c["ops"]==5 and c["val"]==5 and
       effect(c,0,2,q,3,0) and effect(c,0,2,q+1,1,1)
       for c in ev["conditions"]))
 def test_source_mission_steps_one_and_battle_2_to_5_are_distinct(self):
  rows={(r["mission"],r["step"]):r for r in read_steps(DATA)}
  for q,(sid,npcid,coords) in ARENAS.items():
   with self.subTest(q=q):
    self.assertEqual(rows[(q,1)]["end"][0],sid)
    self.assertEqual(rows[(q,1)]["end"][3:],(2,1))
    for step in range(2,6):
     self.assertEqual(rows[(q,step)]["end"],(sid,*coords[step-2],1,step))
     self.assertFalse(any(c[0] for c in rows[(q,step)]["conditions"]))
 def test_checksum_and_guarded_repeated_no_mutation(self):
  before=DATA.read_bytes()
  self.assertEqual(sha(before),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),before)
 def test_global_source_integrity_census(self):
  a=audit(DATA,EVE)
  self.assertEqual(a["markstep_rows"],4505)
  self.assertEqual(a["targets"]["located"],5934)
  self.assertFalse(a["missing_scenes"])
  self.assertFalse(a["missing_npcs"])
  self.assertFalse(a["missing_doors"])
  self.assertEqual(sum(r["end"]==(0,0,0,0,0) for r in read_steps(DATA)),63)
  b=scan(EVE,DATA)
  self.assertEqual(len(b["missing_end_targets"]),63)
  self.assertEqual(len(b["unlocated_condition_targets"]),85)
if __name__=="__main__":unittest.main()
