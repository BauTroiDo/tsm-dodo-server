"""Five real item-NPC auto-route targets recovered for Qin Tomb mission12158.

DVT original Eve returns exact item grant and stage increment in each
source NPC event. A generated route must never point to an arbitrary
scene/place or bypass multi-location hunts.
"""
import hashlib
import unittest
from pathlib import Path

from audit_dvt_quest_navigation import read_steps, audit
from eve_tool import EveArchive
from patch_dvt_12158_npc_navigation_20261011 import TARGETS, patch

ROOT = Path(__file__).resolve().parents[1]
MARKSTEP = ROOT / "File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE = ROOT / "gamedata/Eve.emg"


class RecoveredQinTombWaypointsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.steps = {(s["mission"], s["step"]): s for s in read_steps(MARKSTEP)}
        cls.eve = EveArchive(EVE)

    def test_byte_hash_and_exact_original_five_npc_routes(self):
        self.assertEqual(
            hashlib.sha256(MARKSTEP.read_bytes()).hexdigest(),
            "c06c8f2179b78033faac137cd7803d4c054f5f151a8f54df1f013f321972739e"
        )
        self.assertEqual(len(TARGETS), 5)
        for key, (item_id, scene_id, x, y, npc_id, placement) in TARGETS.items():
            step = self.steps[key]
            self.assertEqual(
                step["conditions"][0], (3, item_id, 1, (scene_id, x, y, 1, placement)))
            self.assertEqual(step["end"], (0, 0, 0, 0, 0),
                "Do not invent the next-stage turn-in location")
            npcs = [
                npc for npc in self.eve.parse_scene(scene_id)["npcs"]
                if npc["id"] == placement and npc["npc_id"] == npc_id
                and (npc["x"], npc["y"]) == (x,y)
            ]
            self.assertEqual(len(npcs),1)
            self.assertTrue(any(
                r["type"] in (0,7) and r["class"] == 1 and
                r["param"] == item_id and r["pstyle"] == 1 and r["val"] == 1
                for ev_no in npcs[0]["events"]
                for cond in self.eve.parse_scene(scene_id)["events"][ev_no]["conditions"]
                for r in cond["results"]
            ))

    def test_no_unknown_map_door_or_npc_after_patching(self):
        report = audit(MARKSTEP, EVE, only_mission=12158)
        self.assertEqual(report["missing_scenes"],[])
        self.assertEqual(report["missing_doors"],[])
        self.assertEqual(report["missing_npcs"],[])
        self.assertGreaterEqual(report["targets"]["located"], 5)

    def test_patch_is_idempotent_and_never_modifies_a_patched_file(self):
        sha_before=hashlib.sha256(MARKSTEP.read_bytes()).hexdigest()
        self.assertEqual(patch(MARKSTEP,EVE,False),sha_before)
        self.assertEqual(hashlib.sha256(MARKSTEP.read_bytes()).hexdigest(),sha_before)


if __name__=="__main__":
    unittest.main()
