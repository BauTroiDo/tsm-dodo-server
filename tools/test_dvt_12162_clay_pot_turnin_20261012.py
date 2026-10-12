"""DVT 12162/1 has a real item hand-in NPC; acquisition source remains unknown."""
import hashlib
import unittest
from pathlib import Path

from audit_dvt_quest_navigation import read_steps, audit
from eve_tool import EveArchive
from patch_dvt_12162_clay_pot_turnin_20261012 import (
    TARGET, ITEM, MISSION, STEP, QUANTITY, patch, verify_eve
)

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE = ROOT/"gamedata/Eve.emg"
TARGET_HASH = "918133ebee509d28b964de93c0820e1eccc7c75557263e7ba20b735a8a1dc5f8"

class DvtPotTurnInTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stage = next(s for s in read_steps(FILE)
                         if (s["mission"],s["step"]) == (MISSION,STEP))
        cls.eve = EveArchive(EVE)

    def test_turn_in_npc_and_exact_original_30_pot_consumption(self):
        self.assertEqual(verify_eve(self.eve), (12808,310,360,1,10))
        self.assertEqual(self.stage["conditions"][0][:3],(3,ITEM,QUANTITY))
        self.assertEqual(self.stage["conditions"][0][3],(0,0,0,0,0),
                         "Acquisition source unknown; never falsely point at collection NPC")
        self.assertEqual(self.stage["end"],TARGET)
        self.assertEqual(hashlib.sha256(FILE.read_bytes()).hexdigest(),TARGET_HASH)

    def test_reapply_is_read_only_and_no_other_quest_target_is_missing(self):
        before=FILE.read_bytes()
        self.assertEqual(patch(FILE,EVE,False),TARGET_HASH)
        self.assertEqual(FILE.read_bytes(),before)
        result=audit(FILE,EVE,only_mission=MISSION)
        self.assertEqual(result["missing_scenes"],[])
        self.assertEqual(result["missing_npcs"],[])
        self.assertEqual(result["missing_doors"],[])

if __name__=="__main__":
    unittest.main()
