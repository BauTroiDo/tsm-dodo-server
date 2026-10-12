"""Thirty DVT source area guides: Santa14 daily help, Grandpa16 item-search."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_12511_12634_santa_grandpa_area_guides_20261012 import (
 SANTA_STAGES,REWARD_STAGES,GRANDPA_STAGES,DESTS,source_proof,patch,sha,has)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="5d89bbc211ae6c1cbc9cf55e846cbb13634adc8b25cd081897ba7d11a28896f0"
class SantaGrandpaBulkGuidance(unittest.TestCase):
 def test_full_source_ownership_and_exact_30_only_area_guides(self):
  self.assertEqual(source_proof(EVE),DESTS)
  self.assertEqual(len(DESTS),30)
  self.assertEqual(len(SANTA_STAGES),14)
  self.assertEqual(len(GRANDPA_STAGES),16)
  self.assertFalse(set(SANTA_STAGES).intersection(REWARD_STAGES))
  arc=EveArchive(EVE)
  santa=arc.parse_scene(49945)
  grand=arc.parse_scene(49955)
  self.assertTrue(any(x["npc_id"]==38127 and x["id"]==1 for x in santa["npcs"]))
  self.assertTrue(any(x["npc_id"]==38317 and x["id"]==1 for x in grand["npcs"]))
 def test_only_literal_santa_help_stages_get_original_santa_npc(self):
  rows={r["step"]:r for r in read_steps(DATA) if r["mission"]==12511}
  self.assertEqual(sorted(rows),list(range(1,19)))
  for stage in SANTA_STAGES:
   with self.subTest(stage=stage):
    self.assertEqual(rows[stage]["end"],(49945,270,420,1,1))
    self.assertIn("ông già Noel",rows[stage]["description"])
    self.assertNotIn("cách ngày",rows[stage]["description"])
  for stage in REWARD_STAGES:
   with self.subTest(stage=stage):
    self.assertEqual(rows[stage]["end"],(0,0,0,1,1))
    self.assertIn("cách ngày",rows[stage]["description"])
 def test_grandpa_room_guide_is_not_misrepresented_as_all_stage_eve_solution(self):
  rows={r["step"]:r for r in read_steps(DATA) if r["mission"]==12634}
  self.assertEqual(sorted(rows),list(range(1,18)))
  for stage in GRANDPA_STAGES:
   with self.subTest(stage=stage):
    self.assertEqual(rows[stage]["end"],(49955,410,370,1,1))
    self.assertIn("nhà Chu gia gia",rows[stage]["description"])
    self.assertFalse(any(c[0] for c in rows[stage]["conditions"]))
  self.assertEqual(rows[17]["end"],(49955,410,370,1,1))
  room=EveArchive(EVE).parse_scene(49955)
  for door,eid in ((2,14),(3,15)):
   with self.subTest(door=door):
    entry=next(x for x in room["doors"] if x["id"]==door)
    self.assertIn(eid,entry["events"])
    e=room["events"][eid]
    self.assertEqual(e["when_happen"],[False,True,False,False])
    self.assertTrue(any(c["class"]==2 and c["param"]==12634
       and has(c,0,1,29472,1,-1) and has(c,0,2,12634,1,1)
       for c in e["conditions"]))
 def test_expected_checksum_idempotence_and_original_all_other_rows_unchanged(self):
  original=DATA.read_bytes()
  self.assertEqual(sha(original),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),original)
 def test_global_markstep_and_mission_blocker_census(self):
  a=audit(DATA,EVE)
  self.assertEqual(a["markstep_rows"],4505)
  self.assertEqual(a["targets"]["located"],5921)
  self.assertFalse(a["missing_scenes"])
  self.assertFalse(a["missing_npcs"])
  self.assertFalse(a["missing_doors"])
  self.assertEqual(sum(r["end"]==(0,0,0,0,0) for r in read_steps(DATA)),70)
  blockers=scan(EVE,DATA)
  self.assertEqual(len(blockers["missing_end_targets"]),74)
  self.assertEqual(len(blockers["unlocated_condition_targets"]),87)
if __name__=="__main__":unittest.main()
