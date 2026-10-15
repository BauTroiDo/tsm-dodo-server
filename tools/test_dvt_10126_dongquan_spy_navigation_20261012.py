"""Prove first navigable encounter for DVT10126 stage4 Dong Quan infiltration."""
import hashlib
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps, audit
from eve_tool import EveArchive
from patch_dvt_10126_palace_spy_route_20261012 import (
    MISSION,STEP,FOLLOW_NPC,TARGET,ORIGINAL_FOLLOW,original_spy_target,patch,
)

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="11bba0ad361ff96266be77db1a3b5d6fcac37f82cd0345fb0c44777e8dbc1df2"

class DvtDongQuanSpyAutoNavigationTest(unittest.TestCase):
    def test_original_spy_first_and_palace_dialogue_advances_step4(self):
        self.assertEqual(original_spy_target(EVE),TARGET)
        stage=next(r for r in read_steps(DATA) if
                   (r["mission"],r["step"])==(MISSION,STEP))
        self.assertEqual(stage["conditions"][0],(1,FOLLOW_NPC,1,ORIGINAL_FOLLOW))
        self.assertEqual(stage["end"],TARGET)
        self.assertEqual(hashlib.sha256(DATA.read_bytes()).hexdigest(),SHA)
        a=EveArchive(EVE)
        outer=a.parse_scene(11002)
        self.assertEqual(outer["scene_infos"][6]["target_scene"],11301)
        self.assertEqual([(n["id"],n["x"],n["y"]) for n in outer["npcs"]
                          if n["npc_id"]==17018 and 14 in n["events"]],[(5,570,260)])
        inside=a.parse_scene(11301)
        self.assertTrue(any(n["npc_id"]==14216 and n["id"]==8 and 12 in n["events"] for n in inside["npcs"]))
        self.assertEqual(audit(DATA,EVE,only_mission=MISSION)["missing_npcs"],[])

    def test_existing_lua_selects_end_after_recruiting_companion(self):
        lua=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf8")
        self.assertIn("Role.GetFollowNpc(Role.playerId, condition.id) ~= nil",lua)
        self.assertIn("local sceneId = step.endSceneId;",lua)
        self.assertIn("if not doneCondition then",lua)

    def test_repeat_patch_has_no_side_effects(self):
        before=DATA.read_bytes()
        self.assertEqual(patch(DATA,EVE,False),SHA)
        self.assertEqual(DATA.read_bytes(),before)

if __name__=="__main__":
    unittest.main()
