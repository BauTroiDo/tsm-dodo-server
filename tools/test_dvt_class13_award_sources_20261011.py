import unittest
from pathlib import Path

from audit_dvt_class13_award_sources_20261011 import audit

ROOT = Path(__file__).resolve().parents[1]


class DvtClass13AwardProvenanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = audit(
            ROOT / "gamedata" / "Eve.emg",
            ROOT / "File.dat" / "Data-ios" / "IOS_ActivityAward_C.dat",
        )

    def test_source_binaries_are_the_active_dvt_versions(self):
        r = self.report
        self.assertEqual(r["eve_sha256"],
            "03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910")
        self.assertEqual(r["award_sha256"],
            "c5a015cbfd2b9e62ba2977dcdba44cd9d6f608d76156fff791068af1c67f27ac")
        self.assertEqual(r["award_rows"], 159)
        self.assertEqual(r["award_record_bytes"], 18)
        self.assertEqual(r["award_first_row"],
            [1, 101, 1, 2, 4, 68, 1, 21197])

    def test_class13_Eve_distribution_is_stable(self):
        r = self.report
        self.assertEqual(r["eve_class13_count"], 89)
        self.assertEqual(r["eve_class13_distinct_values"], 82)
        self.assertEqual(r["eve_class13_styles"], {1: 82, 2: 7})
        self.assertEqual(r["eve_class13_scenes"], 25)
        self.assertEqual(r["eve_class13_scene12003"], 55)

    def test_activity_award_is_not_a_complete_direct_reward_id_lookup(self):
        r = self.report
        self.assertEqual(r["award_leading_field_distinct"], 25)
        self.assertEqual(len(r["class13_values_unmatched_as_award_leading_field"]), 61)
        self.assertEqual(r["award_second_field_distinct"], 25)
        self.assertEqual(len(r["class13_values_unmatched_as_award_second_field"]), 81)
        self.assertFalse(r["safe_to_grant_from_this_mapping"],
            "Do not convert an unproven row association into free items")


if __name__ == "__main__":
    unittest.main()
