"""Data parity guard for all DVT quest navigation and risky Eve effects."""
import unittest
from pathlib import Path
from audit_dvt_quest_blockers_20261011 import scan
from audit_dvt_quest_navigation import audit as audit_navigation

ROOT = Path(__file__).resolve().parents[1]


class ActiveDvtQuestBlockerCensusTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.audit = scan(
            ROOT / "gamedata" / "Eve.emg",
            ROOT / "File.dat" / "Data-ios" / "IOS_MarkStep_C.dat",
        )

    def test_authoritative_eve_and_mission_step_coverage(self):
        a = self.audit
        self.assertEqual(
            a["eve_sha256"],
            "03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910",
        )
        self.assertEqual(a["counters"]["indexed_scenes"], 3823)
        self.assertEqual(a["total_marksteps"], 4505)
        self.assertEqual(a["markstep_missions"], 984)

    def test_unsupported_effects_cannot_be_silently_forgotten(self):
        a = self.audit["counters"]
        self.assertEqual(a["result_11"], 35)
        self.assertEqual(a["result_13"], 89)
        self.assertEqual(a["cond_13"], 99)
        self.assertEqual(a["cond_14"], 279)
        self.assertEqual(a["cond_14_supported_ole_time"], 274)
        self.assertEqual(a["cond_14_unverified_special_time"], 5)
        self.assertEqual(a["cond_17"], 16)

    def test_only_five_special_class14_social_conditions_remain_unverified(self):
        # Client Data/Eve/Eve_NpcEventData.lua calls ReadDouble for CTimeValue,
        # not an opaque eight-byte blob. The ordinary 274 time comparisons
        # are source-verified by Kotlin EveConditionEvaluator.
        a = self.audit
        special = [r for r in a["blockers"]["condition"] if r["class"] == 14]
        self.assertEqual(5, len(special))
        self.assertEqual(
            {(r["scene"], r["event"]) for r in special}, {(12001, 64)})
        self.assertEqual(
            sorted((r["condition"], r["param"], r["style"], r["ops"])
                   for r in special),
            [(3, 2, 2, 0), (5, 2, 1, 0), (7, 2, 3, 0),
             (9, 2, 4, 0), (13, 2, 4, 0)])
        self.assertTrue(all(
            r["time_value"] == 37931.6033664236 and
            r["sub_item"] == 0 for r in special))
        self.assertNotIn("unverified_activity_state",
                         {x for scene in a["scene_coverage"].values()
                          for x in scene})

    def test_unlocated_objectives_and_two_steps_without_any_path(self):
        a = self.audit
        self.assertEqual(len(a["missing_end_targets"]), 269)
        self.assertEqual(len(a["unlocated_condition_targets"]), 88)
        count_by_kind = {}
        for row in a["unlocated_condition_targets"]:
            count_by_kind[row["kind"]] = count_by_kind.get(row["kind"], 0) + 1
        self.assertEqual(count_by_kind, {3: 81, 1: 7})
        no_route = {
            (r["mission"], r["step"])
            for r in a["missing_end_targets"]
            if r["conditions"] and not r["located_conditions"]
        }
        self.assertEqual(no_route, {
            (10852, 1), (12162, 1),
        })

    def test_linked_missions_are_potential_not_confirmed_blocked(self):
        # This is a *candidate* list: an Eve event can contain multiple
        # mutually exclusive conditions. Do not flag every mission as broken.
        # Correct classification matters: 274 already-supported class14 clock
        # checks must not make ~80 unrelated missions appear broken.
        self.assertEqual(len(self.audit["potentially_blocked_missions"]), 2)
        self.assertEqual(len(self.audit["scene_coverage"]), 47)
        self.assertEqual(
            self.audit["potentially_blocked_missions"]["10852"]["blocked_effects"],
            ["activity_reward"],
        )

    def test_all_navigable_dvt_destinations_point_to_actual_eve_places(self):
        result = audit_navigation(
            ROOT / "File.dat" / "Data-ios" / "IOS_MarkStep_C.dat",
            ROOT / "gamedata" / "Eve.emg",
        )
        self.assertEqual(result["markstep_rows"], 4505)
        self.assertEqual(result["targets"]["located"], 5724)
        self.assertEqual(result["missing_scenes"], [],
            "Quest target references a scene absent from active Eve")
        self.assertEqual(result["missing_doors"], [],
            "Quest target references a door ID not present in Eve")
        self.assertEqual(result["missing_npcs"], [],
            "Quest target references a nonexistent fixed NPC")
        self.assertEqual(len(result["nonstatic_hunt_targets"]), 1,
            "The one nonstatic NPC hunt must stay separately classified")

    def test_github_markstep_binary_includes_five_source_verified_npc_waypoints(self):
        import hashlib
        path = ROOT / "File.dat" / "Data-ios" / "IOS_MarkStep_C.dat"
        sha = hashlib.sha256(path.read_bytes()).hexdigest()
        self.assertEqual(sha,
            "8f37189491c21ab6f59973ae9a86dbdb279b4276485ef420a85b0bcd0b7bc115",
            "A subsequent deployment must not restore the ten broken door IDs")

if __name__ == "__main__":
    unittest.main()
