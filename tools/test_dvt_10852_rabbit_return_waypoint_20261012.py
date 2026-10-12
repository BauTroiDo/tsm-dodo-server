"""Prove Jade Rabbit capture -> genuine Ngô Cương return, without bypassing class13."""
import hashlib
import unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps, audit
from eve_tool import EveArchive
from patch_dvt_10852_rabbit_return_waypoint_20261012 import (
    RETURN, HUNT, MISSION, RABBIT, patch, source_verified
)
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
TARGET_SHA="94bbeeb0fde711f0435d490928ffba94d86afaaa880f6868e4dc6320dff1c424"

class DvtRabbitReturnWaypointTest(unittest.TestCase):
    def test_capture_then_return_has_source_backed_target(self):
        self.assertEqual(source_verified(EVE),RETURN)
        row=next(x for x in read_steps(DATA) if (x["mission"],x["step"])==(MISSION,1))
        self.assertEqual(row["conditions"][0],(1,RABBIT,1,HUNT))
        self.assertEqual(row["end"],RETURN)
        self.assertEqual(hashlib.sha256(DATA.read_bytes()).hexdigest(),TARGET_SHA)
        report=audit(DATA,EVE,only_mission=MISSION)
        self.assertEqual(report["missing_scenes"],[])
        self.assertEqual(report["missing_npcs"],[])
        self.assertEqual(report["missing_doors"],[])

    def test_completed_capture_depends_on_follow_npc_and_keeps_unverified_reward_fence(self):
        client_lua=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf8")
        self.assertIn("Role.GetFollowNpc(Role.playerId, condition.id) ~= nil",client_lua)
        self.assertIn("local sceneId = step.endSceneId;",client_lua)
        src=(ROOT/"gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveResultExecutor.kt").read_text(encoding="utf8")
        self.assertIn("r.resultClass in setOf(11, 13, 15)",src)
        scene=EveArchive(EVE).parse_scene(11021)
        branch=next(c for c in scene["events"][11]["conditions"] if c["no"]==5)
        self.assertTrue(any(r["type"]==0 and r["class"]==13 and
                            r["param"]==0 and r["pstyle"]==1 and r["val"]==40
                            for r in branch["results"]))

    def test_repeat_patch_is_read_only(self):
        old=DATA.read_bytes()
        self.assertEqual(patch(DATA,EVE,False),TARGET_SHA)
        self.assertEqual(DATA.read_bytes(),old)

if __name__=="__main__":
    unittest.main()
