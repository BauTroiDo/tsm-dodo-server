"""Original DVT Moon Rabbit quest10852 first-reachable NPC waypoint regression."""
import hashlib
import math
import unittest
from pathlib import Path

from audit_dvt_quest_navigation import read_steps, audit
from eve_tool import EveArchive
from patch_dvt_10852_rabbit_cave_waypoint_20261012 import (
    ARRIVAL, CAVE, ENTRY_SCENE, ENTRY_NPC_ID, MISSION, RABBIT_NPC_ID,
    RABBIT_PLACEMENT, RABBIT, verified_target, patch
)

ROOT=Path(__file__).resolve().parents[1]
MARKSTEP=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
TARGET_SHA="87e30327e5c42ccec30f788022fc28a5f181505c03bb8102fdb3beb14a489ec0"


class DvtMoonRabbitQuestNavigationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.archive=EveArchive(EVE)
        cls.row=next(s for s in read_steps(MARKSTEP) if
                     (s["mission"],s["step"])==(MISSION,1))

    def test_quest_stage1_guides_to_genuine_rabbit_nearest_actual_dvt_cave_entry(self):
        self.assertEqual(hashlib.sha256(MARKSTEP.read_bytes()).hexdigest(),TARGET_SHA)
        self.assertEqual(self.row["conditions"][0],
            (1,RABBIT_NPC_ID,1,(CAVE,*RABBIT,1,RABBIT_PLACEMENT)))
        self.assertEqual(self.row["end"],(11021,190,180,1,7))
        self.assertIn("Ngô Cương",self.row["guide"])
        self.assertEqual(verified_target(self.archive),
                         (CAVE,*RABBIT,1,RABBIT_PLACEMENT))
        city=self.archive.parse_scene(ENTRY_SCENE)
        self.assertEqual(
            (city["scene_infos"][5]["target_scene"],
             city["scene_infos"][5]["target_x"],
             city["scene_infos"][5]["target_y"]),
            (CAVE,*ARRIVAL))
        self.assertTrue(any(n["npc_id"]==ENTRY_NPC_ID and n["id"]==7 for n in city["npcs"]))
        cave=self.archive.parse_scene(CAVE)
        rabbits=[n for n in cave["npcs"] if n["npc_id"]==RABBIT_NPC_ID and not n["close"]]
        self.assertEqual(len(rabbits),4)
        ordered=sorted(rabbits,key=lambda n:math.dist(ARRIVAL,(n["x"],n["y"])))
        self.assertEqual((ordered[0]["id"],ordered[0]["x"],ordered[0]["y"]),
                         (RABBIT_PLACEMENT,*RABBIT))
        self.assertLess(math.dist(ARRIVAL,RABBIT),600)
        self.assertGreater(math.dist(ARRIVAL,(ordered[1]["x"],ordered[1]["y"])),2000)

    def test_all_original_rabbit_npcs_remain_untouched_and_stage_bound(self):
        scene_ids=set()
        bound=[]
        for sid in self.archive.scenes:
            scene=self.archive.parse_scene(sid)
            for npc in scene["npcs"]:
                if npc["npc_id"]!=RABBIT_NPC_ID:continue
                if any(c["class"]==2 and c["param"]==MISSION and
                       any(r["type"]==3 for r in c["results"])
                       for no in npc["events"]
                       for c in scene["events"][no]["conditions"]):
                    bound.append((sid,npc["id"],npc["x"],npc["y"]))
                    scene_ids.add(sid)
        self.assertEqual(len(bound),18)
        self.assertEqual(scene_ids,{10982,10983,10984,10985})
        self.assertIn((CAVE,RABBIT_PLACEMENT,*RABBIT),bound)

    def test_patch_is_idempotent_and_avoids_fabricated_targets(self):
        old=MARKSTEP.read_bytes()
        self.assertEqual(patch(MARKSTEP,EVE,False),TARGET_SHA)
        self.assertEqual(MARKSTEP.read_bytes(),old)
        report=audit(MARKSTEP,EVE,only_mission=MISSION)
        self.assertEqual(report["missing_scenes"],[])
        self.assertEqual(report["missing_npcs"],[])
        self.assertEqual(report["missing_doors"],[])
        self.assertGreaterEqual(report["targets"]["located"],1)


if __name__=="__main__":
    unittest.main()
