"""DVT 12763 seven herb Doors and 13051 two precise ghost NPC waypoints."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import audit,read_steps
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_12763_13051_herb_ghost_source_routes_20261012 import (
 HERB,GHOST,source_proof,patch,sha,has)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
EXPECTED_SHA="e48a4b7f50f6dfc4d3b902279bdda5efea0c783c3eb46f7c300a80db1bec13ad"
class HerbAndGhostSourceRoutes(unittest.TestCase):
 def test_all_seven_authentic_door_eves_and_nonlinear_herb_deltas(self):
  self.assertEqual(len(HERB),7)
  verified=source_proof(EVE)
  archive=EveArchive(EVE)
  for step,(sid,did,x,y,eid,delta) in HERB.items():
   with self.subTest(step=step):
    self.assertEqual(verified[(12763,step)],(sid,x,y,2,did))
    sc=archive.parse_scene(sid)
    doors=[z for z in sc["doors"] if z["id"]==did]
    self.assertEqual(len(doors),1)
    self.assertEqual((doors[0]["x"],doors[0]["y"]),(x,y))
    self.assertIn(eid,doors[0]["events"])
    evt=sc["events"][eid]
    self.assertEqual(evt["when_happen"],[False,True,False,False])
    pairs=[c for c in evt["conditions"] if c["class"]==2 and c["param"]==12763
          and c["pstyle"]==1 and c["ops"]==5 and c["val"]==step and c["and_num"]==2]
    self.assertEqual(len(pairs),2)
    awarded=next(c for c in pairs if has(c,0,1,29513,1,-1))
    self.assertEqual(awarded["no"],4)
    self.assertTrue(has(awarded,0,2,12763,1,delta))
 def test_ghost_source_matches_hidden_npcs_and_exact_completion_without_forcing(self):
  archive=EveArchive(EVE)
  self.assertEqual(len(GHOST),2)
  for step,(sid,pid,npcid,x,y,eid,no) in GHOST.items():
   with self.subTest(step=step):
    sc=archive.parse_scene(sid)
    npc=next(z for z in sc["npcs"] if z["id"]==pid)
    self.assertEqual((npc["npc_id"],npc["x"],npc["y"],npc["close"]),(npcid,x,y,True))
    self.assertIn(eid,npc["events"])
    ev=sc["events"][eid]
    self.assertEqual(ev["when_happen"],[True,False,False,False])
    cond=next(c for c in ev["conditions"] if c["no"]==no)
    self.assertEqual((cond["class"],cond["param"],cond["pstyle"],cond["ops"],cond["val"]),
                     (2,13051,1,5,step))
    self.assertTrue(has(cond,0,2,13051,1,1) if step==1 else has(cond,0,2,13051,3,0))
 def test_markstep_exact_nine_routes_original_herb_npc_neighbors_and_partial_ghost(self):
  steps={(x["mission"],x["step"]):x for x in read_steps(DATA)}
  self.assertEqual([st for (q,st) in steps if q==12763],[1,2,3,4,5,6,7,8,10])
  for step,(sid,did,x,y,eid,delta) in HERB.items():
   with self.subTest(step=step):
    self.assertEqual(steps[(12763,step)]["end"],(sid,x,y,2,did))
    self.assertFalse(any(c[0] for c in steps[(12763,step)]["conditions"]))
  for step,(sid,pid,npcid,x,y,eid,no) in GHOST.items():
   with self.subTest(ghost_step=step):
    self.assertEqual(steps[(13051,step)]["end"],(sid,x,y,1,pid))
  for step in (1,10):
   self.assertEqual(steps[(12763,step)]["end"],(12002,170,340,1,19))
 def test_source_guard_idempotent_byte_checksum_and_no_unintended_mutation(self):
  before=DATA.read_bytes()
  self.assertEqual(sha(before),EXPECTED_SHA)
  self.assertEqual(patch(DATA,EVE,False),EXPECTED_SHA)
  self.assertEqual(DATA.read_bytes(),before)
 def test_corpus_integrity_counts_and_valid_eve_targets(self):
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5932)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
  self.assertEqual(sum(x["end"]==(0,0,0,0,0) for x in read_steps(DATA)),63)
  blockers=scan(EVE,DATA)
  self.assertEqual(len(blockers["missing_end_targets"]),63)
  self.assertEqual(len(blockers["unlocated_condition_targets"]),87)
if __name__=="__main__":unittest.main()
