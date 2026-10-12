"""All 18 source-proven Qin tomb Door guide targets; real 9-token puzzles untouched."""
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import audit,read_steps
from eve_tool import EveArchive
from patch_dvt_12138_qin_tomb_nine_door_puzzles_20261012 import PAIRS,target_rows,verify_original_eve,patch,sha,has_effect
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="5d89bbc211ae6c1cbc9cf55e846cbb13634adc8b25cd081897ba7d11a28896f0"
class QinTombNineTokenPuzzleDoorGuides(unittest.TestCase):
 def test_original_9_door_18_stage_source_puzzle_safeguards(self):
  self.assertEqual(len(PAIRS),9)
  self.assertEqual(verify_original_eve(EVE),target_rows())
  archive=EveArchive(EVE)
  for odd,(sceneid,x,y,doorid,eveid,itemid) in PAIRS.items():
   with self.subTest(stage_pair=(odd,odd+1)):
    sc=archive.parse_scene(sceneid)
    door=next(d for d in sc["doors"] if d["id"]==doorid)
    self.assertEqual((door["x"],door["y"]),(x,y))
    self.assertIn(eveid,door["events"])
    self.assertFalse(any(eveid in npc["events"] for npc in sc["npcs"]))
    ev=sc["events"][eveid]
    self.assertEqual(ev["when_happen"],[False,True,False,False])
    oddg=next(c for c in ev["conditions"] if
         c["class"]==2 and c["param"]==12138 and c["pstyle"]==1
         and c["ops"]==5 and c["val"]==odd)
    self.assertEqual(oddg["and_num"],1)
    self.assertFalse(any(z["type"]==0 and z["class"] in (1,2) for z in oddg["results"]))
    eveng=next(c for c in ev["conditions"] if
         c["class"]==2 and c["param"]==12138 and c["pstyle"]==1
         and c["ops"]==5 and c["val"]==odd+1 and c["and_num"]==2
         and has_effect(c,0,1,itemid,1,1) and has_effect(c,0,2,12138,1,1))
    side=[c for c in ev["conditions"] if
         c["class"]==7 and c["param"]==2 and c["pstyle"]==2 and
         c["to_result"]==eveng["no"]]
    self.assertEqual(len(side),1)
 def test_18_original_blank_stages_become_exact_door_targets_with_same_stage_pairs(self):
  rows={r["step"]:r for r in read_steps(DATA) if r["mission"]==12138}
  self.assertEqual(len(rows),20)
  for step,target in target_rows().items():
   with self.subTest(step=step):
    self.assertEqual(rows[step]["end"],target)
    self.assertEqual(target[3],2)
    self.assertFalse(any(c[0] for c in rows[step]["conditions"]))
  for odd in PAIRS:
   self.assertEqual(rows[odd]["end"],rows[odd+1]["end"])
   self.assertEqual(rows[odd]["description"],rows[odd+1]["description"])
  self.assertEqual(rows[19]["end"],(14620,450,350,1,1))
  self.assertEqual(rows[20]["end"],(14620,450,350,1,1))
 def test_old_kotlin_client_door_dispatch_preserved(self):
  kt=(ROOT/"gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveTriggerRouter.kt").read_text(encoding="utf8")
  lua=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf8")
  self.assertIn("val door = sceneData.doors[triggerID]",kt)
  self.assertIn("for (eveNo in door.events)",kt)
  self.assertIn("function MarkManager.CheckNavigationDoor(door)",lua)
 def test_sha_patcher_is_idempotent_and_does_not_modify_other_records(self):
  src=DATA.read_bytes()
  self.assertEqual(sha(src),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),src)
 def test_all_legitimate_original_scene_door_targets_and_census(self):
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5921)
  self.assertEqual(sum(1 for row in read_steps(DATA) if row["end"]==(0,0,0,0,0)),70)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
if __name__=="__main__":unittest.main()
