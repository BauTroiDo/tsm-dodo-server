"""Christmas socks quest12509 steps5/6 must route to original NPC3, without grants."""
import hashlib
import tempfile
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps, audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_12509_socks_20261012 import TARGETS, sha, patch, verify_source, has_result

ROOT = Path(__file__).resolve().parents[1]
MARK = ROOT / "File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE = ROOT / "gamedata/Eve.emg"
SHA = "a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da"

class ChristmasSocksOriginalEvent(unittest.TestCase):
    def test_original_npc_and_stage_dependent_quests(self):
        self.assertEqual(verify_source(EVE), TARGETS)
        scene = EveArchive(EVE).parse_scene(49944)
        npc = next(n for n in scene["npcs"] if n["id"] == 3)
        self.assertEqual((npc["npc_id"],npc["x"],npc["y"],npc["close"]),
                         (38286,250,720,False))
        self.assertIn(8, npc["events"])
        self.assertEqual(scene["events"][8]["when_happen"], [True,False,False,False])
        for stage,item,add in ((5,29451,2),(6,29466,3)):
            with self.subTest(stage=stage):
                matched = [c for c in scene["events"][8]["conditions"]
                    if (c["class"],c["param"],c["pstyle"],c["ops"],c["val"]) ==
                       (2,12509,1,5,stage) and
                    has_result(c,1,item,1,-2) and has_result(c,2,12509,1,add)]
                self.assertEqual(len(matched),1)

    def test_exact_two_end_waypoints_and_unchanged_neighbors(self):
        rows = {(r["mission"],r["step"]):r for r in read_steps(MARK)}
        for key,target in TARGETS.items():
            self.assertEqual(rows[key]["end"],target)
            self.assertFalse(any(c[0] for c in rows[key]["conditions"]))
        for s in (4,11):
            self.assertEqual(rows[(12509,s)]["end"],(49944,370,730,1,3))
        self.assertEqual(rows[(12509,7)]["end"],(49944,410,390,1,5))
        self.assertEqual(sha(MARK.read_bytes()),SHA)

    def test_patcher_idempotent_and_fail_closed(self):
        before = MARK.read_bytes()
        self.assertEqual(patch(MARK,EVE,False),SHA)
        self.assertEqual(MARK.read_bytes(),before)
        with tempfile.TemporaryDirectory() as td:
            bad = Path(td)/"MarkStep.dat"
            corrupted = bytearray(before)
            corrupted[350] ^= 1
            bad.write_bytes(corrupted)
            with self.assertRaises(ValueError):
                patch(bad,EVE,False)
            self.assertEqual(bad.read_bytes(),corrupted)

    def test_full_audit_counts_and_zero_invalid_destinations(self):
        rep = audit(MARK,EVE)
        self.assertEqual(rep["markstep_rows"],4505)
        self.assertEqual(rep["targets"]["located"],5934)
        self.assertFalse(rep["missing_scenes"])
        self.assertFalse(rep["missing_npcs"])
        self.assertFalse(rep["missing_doors"])
        self.assertEqual(sum(r["end"]==(0,0,0,0,0) for r in read_steps(MARK)),63)
        blockers = scan(EVE,MARK)
        self.assertEqual(len(blockers["missing_end_targets"]),63)
        self.assertEqual(len(blockers["pending_condition_guided_end_targets"]),8)
        self.assertEqual(len(blockers["missing_end_without_pending_condition_guidance"]),55)
        self.assertEqual(len(blockers["unlocated_condition_targets"]),85)

if __name__=="__main__":
    unittest.main()
