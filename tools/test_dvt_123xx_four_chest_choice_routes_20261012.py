"""Source-verifiable 16 distinct NPC20001 treasure chest waypoints."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_123xx_four_chest_choice_routes_20261012 import ARENAS,source_proof,patch,sha,effect
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da"
class SourceChestChoices(unittest.TestCase):
 def test_six_original_chest_choices_and_four_distinct_selected(self):
  verified=source_proof(EVE)
  self.assertEqual(len(verified),16)
  archive=EveArchive(EVE)
  for quest,(sid,choices) in ARENAS.items():
   with self.subTest(quest=quest):
    scene=archive.parse_scene(sid)
    source=[n for n in scene["npcs"] if n["npc_id"]==20001]
    self.assertEqual(len(source),6)
    self.assertEqual(len({c[0] for c in choices}),4)
    for st,(placement,x,y,eid) in enumerate(choices,2):
     target=verified[(quest,st)]
     self.assertEqual(target,(sid,x,y,1,placement))
     npc=next(n for n in source if n["id"]==placement)
     self.assertEqual((npc["x"],npc["y"],npc["close"],npc["events"]),
                      (x,y,False,[eid]))
 def test_original_chest_activation_hides_only_selected_and_advances_on_branch(self):
  archive=EveArchive(EVE)
  for quest,(sid,choices) in ARENAS.items():
   scene=archive.parse_scene(sid)
   for st,(placement,x,y,eid) in enumerate(choices,2):
    with self.subTest(quest=quest,step=st):
     ev=scene["events"][eid]
     self.assertEqual(ev["when_happen"],[True,False,False,False])
     first=next(c for c in ev["conditions"] if c["class"]==2 and
       c["param"]==quest and c["pstyle"]==1 and c["ops"]==2 and c["val"]==1)
     self.assertTrue(effect(first,0,2,quest,1,1))
     self.assertTrue(effect(first,0,3,placement,3,2000))
     final=next(c for c in ev["conditions"] if c["class"]==2 and
       c["param"]==quest and c["pstyle"]==1 and c["ops"]==5 and c["val"]==5)
     self.assertTrue(effect(final,0,2,quest,3,0))
     self.assertTrue(effect(final,0,2,quest+1,1,1))
     self.assertTrue(effect(final,0,2,12321,1,1))
 def test_preserves_real_original_stage1_door_and_stage2_to_5_end(self):
  rows={(row["mission"],row["step"]):row for row in read_steps(DATA)}
  for quest,(sid,choices) in ARENAS.items():
   self.assertEqual(rows[(quest,1)]["end"][0],sid)
   self.assertEqual(rows[(quest,1)]["end"][3:],(2,1))
   for step,(nid,x,y,eid) in enumerate(choices,2):
    with self.subTest(quest=quest,step=step):
     self.assertEqual(rows[(quest,step)]["end"],(sid,x,y,1,nid))
     self.assertFalse(any(c[0] for c in rows[(quest,step)]["conditions"]))
 def test_checksum_guard_idempotence_no_unrelated_byte_changes(self):
  before=DATA.read_bytes()
  self.assertEqual(sha(before),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),before)
 def test_full_valid_scene_npc_door_census_and_remaining_blanks(self):
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5934)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
  self.assertEqual(sum(x["end"]==(0,0,0,0,0) for x in read_steps(DATA)),63)
  blockers=scan(EVE,DATA)
  self.assertEqual(len(blockers["missing_end_targets"]),63)
  self.assertEqual(len(blockers["unlocated_condition_targets"]),85)
if __name__=="__main__":unittest.main()
