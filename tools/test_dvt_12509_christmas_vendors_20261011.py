"""DVT mission12509/8 multi-vendor item waypoint source parity.

All three objectives have independent NPC Surface shops. Keep the
Christmas quest END points at genuine chef; verify all three merchants too.
"""
import hashlib
import unittest
from pathlib import Path

from audit_dvt_quest_navigation import read_steps, audit
from eve_tool import EveArchive
from patch_dvt_12509_christmas_vendors_20261011 import SOURCES, patch

ROOT=Path(__file__).resolve().parents[1]
STEP_PATH=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE_PATH=ROOT/"gamedata/Eve.emg"


class DvtChristmasMultiVendorNavigationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.eve=EveArchive(EVE_PATH)
        cls.row=next(st for st in read_steps(STEP_PATH)
                     if st["mission"]==12509 and st["step"]==8)

    def test_three_independent_shop_sources_match_exact_required_items(self):
        self.assertEqual(
            hashlib.sha256(STEP_PATH.read_bytes()).hexdigest(),
            "11bba0ad361ff96266be77db1a3b5d6fcac37f82cd0345fb0c44777e8dbc1df2")
        self.assertEqual(len(SOURCES),3)
        self.assertEqual(self.row["end"],(49944,330,360,1,5))
        for i,(item,qty,scene,npc_id,placement,x,y,surface_no) in SOURCES.items():
            self.assertEqual(self.row["conditions"][i-1],
                             (3,item,qty,(scene,x,y,1,placement)))
            sc=self.eve.parse_scene(scene)
            vendor=[n for n in sc["npcs"] if
                (n["id"],n["npc_id"],n["x"],n["y"])==
                (placement,npc_id,x,y)]
            self.assertEqual(len(vendor),1)
            shop=sc["surfaces"][surface_no]
            self.assertEqual(shop["option_mode"],1)
            self.assertTrue(any(s["data_kind"]==2 and
                                s["data_id"]==item and s["style"] in (2,3)
                                for s in shop["sentences"]))
            self.assertTrue(any(r["type"]==6 and r["mean_no"]==surface_no
                for no in vendor[0]["events"]
                for cond in sc["events"][no]["conditions"]
                for r in cond["results"]))

    def test_multivendor_patch_is_idempotent_and_has_zero_broken_targets(self):
        before=STEP_PATH.read_bytes()
        expected=hashlib.sha256(before).hexdigest()
        self.assertEqual(patch(STEP_PATH,EVE_PATH,False),expected)
        self.assertEqual(STEP_PATH.read_bytes(),before)
        result=audit(STEP_PATH,EVE_PATH,only_mission=12509)
        self.assertEqual(result["missing_scenes"],[])
        self.assertEqual(result["missing_npcs"],[])
        self.assertEqual(result["missing_doors"],[])

    def test_earthware_pots_do_not_get_fabricated_merchant_waypoints(self):
        # 12162 asks for 30 of 32043. The active Eve merchant Surface
        # and ground Good tables contain NO direct acquisition source.
        # Do not introduce a fictitious merchant or claim that a mission
        # turn-in NPC is the source of the items.
        pot=32043
        surface_hits=0
        ground_hits=0
        for sid in self.eve.scenes:
            sc=self.eve.parse_scene(sid)
            surface_hits+=sum(
                s["data_id"]==pot and s["data_kind"]==2
                for ui in sc["surfaces"].values()
                for s in ui["sentences"])
            ground_hits+=sum(g["item_id"]==pot for g in sc["goods"])
        self.assertEqual(surface_hits,0)
        self.assertEqual(ground_hits,0)
        p=next(st for st in read_steps(STEP_PATH)
               if st["mission"]==12162 and st["step"]==1)
        self.assertEqual(p["conditions"][0],
                         (3,32043,30,(0,0,0,0,0)))

if __name__=="__main__":
    unittest.main()
