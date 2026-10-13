"""DVT12509 stage16: captured thief navigates to source-proven Santa scene-entry completion."""
import hashlib
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps,audit
from eve_tool import EveArchive
from patch_dvt_12509_final_santa_return_20261012 import (
 MISSION,STEP,FOLLOW,SANTA,ORIGINAL_CAPTURE,verify_eve,effect
)
from patch_dvt_quest_door_trigger_corrections_20261012 import TARGETS, patch as corrected_patch
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="11bba0ad361ff96266be77db1a3b5d6fcac37f82cd0345fb0c44777e8dbc1df2"
class DvtChristmasThiefFinalReturnTest(unittest.TestCase):
 def test_real_santa_door2_completes_mission_with_follower_gate(self):
  self.assertEqual(verify_eve(EVE),SANTA)
  a=EveArchive(EVE)
  outer=a.parse_scene(49945)
  e=outer["events"][9]
  self.assertIn(9,next(d for d in outer["doors"] if d["id"]==2)["events"])
  self.assertFalse(any(9 in n["events"] for n in outer["npcs"]))
  self.assertEqual(e["when_happen"],[False,True,False,False])
  self.assertTrue(any(c["class"]==9 and c["val"]==FOLLOW for c in e["conditions"]))
  final=next(c for c in e["conditions"] if c["class"]==2 and c["param"]==MISSION and c["val"]==STEP)
  self.assertTrue(any(effect(z,2,MISSION,3,0) for z in final["results"]))
  self.assertFalse(any(z["type"]==0 and z["class"] in (11,13,15) for z in final["results"]))
 def test_original_capture_and_alternative_cave_routes_remain_available(self):
  a=EveArchive(EVE)
  for sid in (49947,49948):
   e=a.parse_scene(sid)["events"][3]
   self.assertEqual(e["when_happen"],[False,True,False,False])
   gate=next(c for c in e["conditions"] if c["class"]==2 and c["param"]==MISSION and c["val"]==STEP)
   self.assertTrue(any(effect(z,2,MISSION,3,0) for z in gate["results"]))
  stages={s["step"]:s for s in read_steps(DATA) if s["mission"]==MISSION}
  self.assertEqual(stages[15]["end"],ORIGINAL_CAPTURE)
  self.assertEqual(stages[16]["conditions"][0],(1,FOLLOW,1,ORIGINAL_CAPTURE))
  self.assertNotEqual(stages[16]["end"],stages[1]["end"])
  self.assertNotEqual(stages[16]["end"],stages[2]["end"])
  self.assertEqual(stages[16]["end"],TARGETS[(MISSION,STEP)])
  self.assertEqual(audit(DATA,EVE,only_mission=MISSION)["missing_npcs"],[])
 def test_lua_automatically_switches_to_original_return_when_thief_follows(self):
  lua=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf8")
  self.assertIn("Role.GetFollowNpc(Role.playerId, condition.id) ~= nil",lua)
  self.assertIn("local sceneId = step.endSceneId;",lua)
 def test_patch_is_idempotent_and_exact_data_checksum(self):
  before=DATA.read_bytes()
  self.assertEqual(hashlib.sha256(before).hexdigest(),SHA)
  self.assertEqual(corrected_patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),before)
if __name__=="__main__":unittest.main()
