"""DVT Trương Bào NPC10269 timed escort quest11900: seven authentic quest waypoints."""
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from eve_tool import EveArchive
from patch_dvt_11900_timed_escort_source_routes_20261012 import TARGETS,proof_eve,patch,sha,effect

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
EXPECTED_SHA="e48a4b7f50f6dfc4d3b902279bdda5efea0c783c3eb46f7c300a80db1bec13ad"

class OriginalTimedEscortSourceRoutes(unittest.TestCase):
 def test_real_npc_and_six_original_stage_gated_door_routes(self):
  expected={s:spec[:5] for s,spec in TARGETS.items()}
  self.assertEqual(proof_eve(EVE),expected)
  arc=EveArchive(EVE)
  for stage,(sid,x,y,kind,placement,eid,branch) in TARGETS.items():
   with self.subTest(stage=stage):
    sc=arc.parse_scene(sid)
    owner=next(o for o in (sc["npcs"] if kind==1 else sc["doors"]) if o["id"]==placement)
    self.assertEqual((owner["x"],owner["y"]),(x,y))
    self.assertIn(eid,owner["events"])
    ev=sc["events"][eid]
    self.assertEqual(ev["when_happen"],
      [True,False,False,False] if kind==1 else [False,True,False,False])
    self.assertTrue(any(c["class"]==2 and c["param"]==11900 and
       c["pstyle"]==1 and c["ops"]==5 and c["val"]==stage
       for c in ev["conditions"]))
 def test_preserve_original_follow_npc_objective_then_end_target(self):
  stages={r["step"]:r for r in read_steps(DATA) if r["mission"]==11900}
  self.assertEqual(len(stages),12)
  self.assertEqual(stages[4]["end"],(12589,1490,1020,1,17))
  for step,target in TARGETS.items():
   with self.subTest(step=step):
    self.assertEqual(stages[step]["end"],target[:5])
    self.assertEqual(stages[step]["conditions"][0],
      (1,10269,1,(12589,1440,1000,1,17)))
    self.assertFalse(any(c[0] for c in stages[step]["conditions"][1:]))
  self.assertEqual(stages[12]["end"],(12242,130,500,1,7))
 def test_battle_win_only_quest_increments_in_tomb_door_6_through_9(self):
  arc=EveArchive(EVE)
  for stage in range(6,10):
   with self.subTest(stage=stage):
    sid,x,y,kind,placement,eid,branch=TARGETS[stage]
    ev=arc.parse_scene(sid)["events"][eid]
    quest=[c for c in ev["conditions"] if c["class"]==2 and
      c["param"]==11900 and c["pstyle"]==1 and c["val"]==stage]
    self.assertTrue(any(c["and_num"]==3 for c in quest))
    # Starting a tomb fight does NOT automatically grant mission +1.
    self.assertFalse(any(effect(c,0,2,11900,1,1) for c in quest))
    win=next(c for c in ev["conditions"] if c["no"]==branch)
    self.assertEqual((win["class"],win["pstyle"]),(8,1))
    self.assertTrue(effect(win,0,2,11900,1,1))
 def test_native_client_escort_fallback_and_kotlin_check_have_follower(self):
  lua=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf8")
  self.assertIn("doneCondition = Role.GetFollowNpc(Role.playerId, condition.id) ~= nil;",lua)
  self.assertIn("sceneId = condition.sceneId;",lua)
  self.assertIn("if not doneCondition then",lua)
  # Keep Kotlin evaluator's source-supported follower location/space guards;
  # don't bypass them when merely adding a MarkStep navigation END.
  kotlin=(ROOT/"gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt").read_text(encoding="utf8")
  self.assertIn("5 -> {",kotlin)
  self.assertIn("evaluatePetSpaceCondition(cond, state)",kotlin)
  self.assertIn("6 -> {",kotlin)
  self.assertIn("state.followNpcIds",kotlin)
  self.assertIn("state.innNpcIds",kotlin)
 def test_sha_idempotence_and_all_global_audit_counts(self):
  before=DATA.read_bytes()
  self.assertEqual(sha(before),EXPECTED_SHA)
  self.assertEqual(patch(DATA,EVE,False),EXPECTED_SHA)
  self.assertEqual(DATA.read_bytes(),before)
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5932)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
  self.assertEqual(sum(1 for row in read_steps(DATA) if row["end"]==(0,0,0,0,0)),63)
  raw=scan(EVE,DATA)
  self.assertEqual(len(raw["missing_end_targets"]),63)
  self.assertEqual(len(raw["unlocated_condition_targets"]),87)
if __name__=="__main__":unittest.main()
