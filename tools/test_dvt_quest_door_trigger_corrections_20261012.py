"""Block NPC-vs-Door Eve confusion in quest12509/16,12936/16,12938/4-5."""
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps,audit
from eve_tool import EveArchive
from patch_dvt_quest_door_trigger_corrections_20261012 import TARGETS,source_proof,patch,sha,effect
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
HASH="e3ba70127cb69cfa9b51e47864015eca288fa91b56d6d5616b98a203cd793f5c"
class StageSpecificOriginalDoorEveRoutes(unittest.TestCase):
 def test_all_four_targets_are_original_source_gated_interactions(self):
  self.assertEqual(source_proof(EVE),TARGETS)
  rows={(x["mission"],x["step"]):x for x in read_steps(DATA)}
  for key,t in TARGETS.items():
   with self.subTest(key=key):
    self.assertEqual(rows[key]["end"],t)
  self.assertEqual(rows[(12938,3)]["end"],rows[(12938,4)]["end"])
  self.assertEqual(rows[(12938,6)]["end"],(12031,690,660,1,14))
  self.assertEqual(rows[(12936,17)]["end"],(12403,220,420,1,8))
  self.assertNotEqual(rows[(12936,16)]["end"],rows[(12936,17)]["end"])
  self.assertEqual(rows[(12509,1)]["end"],(49945,270,420,1,1))
  self.assertEqual(rows[(12509,2)]["end"],(49945,270,420,1,1))
  self.assertNotEqual(rows[(12509,16)]["end"],rows[(12509,1)]["end"])
  self.assertEqual(rows[(12509,16)]["conditions"][0],
      (1,38136,1,(49946,590,310,1,12)))
 def test_door_trigger_events_are_door_bound_not_map_entry_or_npc(self):
  arc=EveArchive(EVE)
  for (qid,stage),(sid,x,y,kind,door_id) in TARGETS.items():
   if kind!=2:continue
   sc=arc.parse_scene(sid)
   door=next(z for z in sc["doors"] if z["id"]==door_id)
   self.assertEqual((door["x"],door["y"]),(x,y))
   self.assertEqual(len(door["events"]),1)
   eveid=door["events"][0]
   self.assertEqual(sc["events"][eveid]["when_happen"],[False,True,False,False])
   self.assertFalse(any(eveid in npc["events"] for npc in sc["npcs"]))
   req=next(c for c in sc["events"][eveid]["conditions"] if
             c["class"]==2 and c["param"]==qid and
             c["pstyle"]==1 and c["ops"]==5 and c["val"]==stage)
   self.assertTrue(effect(req,0,2,qid,1,1) if qid!=12509 else effect(req,0,2,qid,3,0))
 def test_server_routes_real_door_event_and_lua_tracks_door_id(self):
  router=(ROOT/"gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveTriggerRouter.kt").read_text(encoding="utf8")
  self.assertIn("val door = sceneData.doors[triggerID]",router)
  self.assertIn("for (eveNo in door.events)",router)
  lua=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf8")
  self.assertIn("function MarkManager.CheckNavigationDoor(door)",lua)
  self.assertIn("DoorManager.doors[navigationEventId] ~= door",lua)
 def test_santa_ending_is_follower_gated_and_does_not_fake_rewards(self):
  sc=EveArchive(EVE).parse_scene(49945)
  self.assertIn(9,next(d for d in sc["doors"] if d["id"]==2)["events"])
  cs=sc["events"][9]["conditions"]
  q=next(x for x in cs if x["class"]==2 and x["param"]==12509 and x["val"]==16)
  req=next(x for x in cs if x["class"]==9 and x["val"]==38136)
  self.assertEqual(req["to_result"],q["no"])
  self.assertTrue(effect(q,0,2,12509,3,0))
  self.assertTrue(effect(q,0,8,38136,2,0))
  self.assertFalse(any(z["type"]==0 and z["class"] in (11,13,15) for z in q["results"]))
 def test_exact_sha_zero_broken_targets_idempotent_census(self):
  before=DATA.read_bytes()
  self.assertEqual(sha(before),HASH)
  self.assertEqual(patch(DATA,EVE,False),HASH)
  self.assertEqual(DATA.read_bytes(),before)
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5924)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
if __name__=="__main__":unittest.main()
