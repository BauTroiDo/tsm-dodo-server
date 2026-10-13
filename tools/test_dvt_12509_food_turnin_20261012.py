"""Regression for source-backed Christmas food collection -> chef turn-in route."""
import hashlib
import unittest
from pathlib import Path

from audit_dvt_quest_navigation import audit,read_steps
from patch_dvt_12509_food_turnin_20261012 import FOOD,END,patch,verify_receiver
from patch_dvt_12509_christmas_vendors_20261011 import SOURCES

ROOT=Path(__file__).resolve().parents[1]
MARKSTEP=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
EXPECTED_SHA="11bba0ad361ff96266be77db1a3b5d6fcac37f82cd0345fb0c44777e8dbc1df2"

class OriginalChristmasFoodTurninRegression(unittest.TestCase):
    def test_three_merchants_then_real_chef_and_exact_eve_exchange(self):
        self.assertEqual(verify_receiver(EVE),END)
        row=next(x for x in read_steps(MARKSTEP) if (x["mission"],x["step"])==(12509,8))
        self.assertEqual(row["end"],END)
        self.assertEqual(len(FOOD),3)
        for i,(item,qty,scene,npc,placement,x,y,surface) in SOURCES.items():
            self.assertEqual((item,qty),list(FOOD.items())[i-1])
            self.assertEqual(row["conditions"][i-1],(3,item,qty,(scene,x,y,1,placement)))
        self.assertEqual(hashlib.sha256(MARKSTEP.read_bytes()).hexdigest(),EXPECTED_SHA)
        report=audit(MARKSTEP,EVE,only_mission=12509)
        self.assertEqual(report["missing_scenes"],[])
        self.assertEqual(report["missing_npcs"],[])
        self.assertEqual(report["missing_doors"],[])

    def test_idempotent_patch_preserves_all_other_records(self):
        before=MARKSTEP.read_bytes()
        self.assertEqual(patch(MARKSTEP,EVE,False),EXPECTED_SHA)
        self.assertEqual(before,MARKSTEP.read_bytes())

    def test_original_lua_switches_to_end_only_when_all_three_collected(self):
        lua=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf-8")
        self.assertIn("local sceneId = step.endSceneId;",lua)
        self.assertIn("Item.GetItemCount(condition.id) >= condition.count",lua)
        self.assertIn("if not doneCondition then",lua)

if __name__=="__main__":
    unittest.main()
