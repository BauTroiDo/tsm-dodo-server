"""Data parity guard for all DVT quest navigation and risky Eve effects."""
import unittest
from pathlib import Path
from audit_dvt_quest_blockers_20261011 import scan

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
        self.assertEqual(a["cond_17"], 16)

    def test_unlocated_objectives_and_eight_steps_without_any_path(self):
        a = self.audit
        self.assertEqual(len(a["missing_end_targets"]), 269)
        self.assertEqual(len(a["unlocated_condition_targets"]), 96)
        count_by_kind = {}
        for row in a["unlocated_condition_targets"]:
            count_by_kind[row["kind"]] = count_by_kind.get(row["kind"], 0) + 1
        self.assertEqual(count_by_kind, {3: 89, 1: 7})
        no_route = {
            (r["mission"], r["step"])
            for r in a["missing_end_targets"]
            if r["conditions"] and not r["located_conditions"]
        }
        self.assertEqual(no_route, {
            (10852, 1), (12158, 1), (12158, 2), (12158, 3),
            (12158, 4), (12158, 5), (12162, 1), (12509, 8),
        })

    def test_linked_missions_are_potential_not_confirmed_blocked(self):
        # This is a *candidate* list: an Eve event can contain multiple
        # mutually exclusive conditions. Do not flag every mission as broken.
        self.assertEqual(len(self.audit["potentially_blocked_missions"]), 82)
        self.assertEqual(len(self.audit["scene_coverage"]), 85)
        self.assertEqual(
            self.audit["potentially_blocked_missions"]["10852"]["blocked_effects"],
            ["activity_reward"],
        )


if __name__ == "__main__":
    unittest.main()
