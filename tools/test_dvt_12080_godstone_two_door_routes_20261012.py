"""DVT12080 authentic two Door triggers; retain original battle-gated stage transitions."""
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps,audit
from eve_tool import EveArchive
from patch_dvt_12080_godstone_two_door_routes_20261012 import TARGETS,patch,sha,source_eve_proof
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="b2bb1ae382f5c0e50efe49e8ec8b98ce3a44bbba97e8bed955d94a1fd9749753"
class GodstoneStage4Stage7Navigation(unittest.TestCase):
 def test_stage_gated_battle_event_is_bound_to_exact_original_door(self):
  self.assertEqual(source_eve_proof(EVE),{k:v[:5] for k,v in TARGETS.items()})
  arc=EveArchive(EVE)
  for stage,(sid,x,y,kind,did,eveid) in TARGETS.items():
   with self.subTest(stage=stage):
    sc=arc.parse_scene(sid)
    door=next(z for z in sc["doors"] if z["id"]==did)
    self.assertEqual((door["x"],door["y"],door["events"]),(x,y,[eveid]))
    self.assertEqual(sc["events"][eveid]["when_happen"],[False,True,False,False])
    c=[x for x in sc["events"][eveid]["conditions"] if x["class"]==2 and x["param"]==12080 and x["pstyle"]==1 and x["ops"]==5 and x["val"]==stage]
    self.assertEqual(len(c),6)
    self.assertTrue(any(z["class"]==8 for z in sc["events"][eveid]["conditions"]))
    self.assertEqual({x["and_num"] for x in c},{2,3})
 def test_both_stage_endpoints_repaired_without_premature_quest_increment(self):
  s={x["step"]:x for x in read_steps(DATA) if x["mission"]==12080}
  self.assertEqual(s[4]["end"],TARGETS[4][:5])
  self.assertEqual(s[7]["end"],TARGETS[7][:5])
  self.assertFalse(any(x[0] for x in s[4]["conditions"]))
  self.assertFalse(any(x[0] for x in s[7]["conditions"]))
  self.assertEqual(s[3]["end"],(32546,1810,340,2,1))
  for step in (5,6,8):self.assertEqual(s[step]["end"],(32841,710,220,1,1))
 def test_source_guard_patch_no_mutation_and_static_targets(self):
  original=DATA.read_bytes()
  self.assertEqual(sha(original),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),original)
  a=audit(DATA,EVE)
  self.assertEqual(a["markstep_rows"],4505)
  self.assertEqual(a["targets"]["located"],5817)
  self.assertFalse(a["missing_scenes"])
  self.assertFalse(a["missing_npcs"])
  self.assertFalse(a["missing_doors"])
 def test_kotlin_door_dispatch_tries_each_original_eve_until_a_match(self):
  kt=(ROOT/"gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveTriggerRouter.kt").read_text(encoding="utf8")
  start=kt.index("val door = sceneData.doors[triggerID]")
  end=kt.index("private suspend fun routeWhenHappen",start)
  body=kt[start:end]
  self.assertIn("for (eveNo in door.events)",body)
  self.assertIn("resolveActivateAndAdvance(",body)
  self.assertIn("if (result != null)",body)
  self.assertIn("flowController.startEvent(session, result)",body)
  self.assertIn("return",body)
if __name__=="__main__":unittest.main()
