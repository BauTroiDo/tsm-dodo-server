"""Verify that DVT 32043 has genuine BlissBag sources, not invented monster drops."""
import json
import struct
import unittest
from collections import defaultdict
from pathlib import Path

from eve_tool import EveArchive

ROOT = Path(__file__).resolve().parents[1]

class ClayPotBlissBagSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        data = (ROOT / "File.dat/Data-ios/IOS_BlissBag_C.dat").read_bytes()
        n = struct.unpack_from("<I", data)[0]
        assert len(data) == 4 + n * 13
        cls.groups = defaultdict(list)
        for j in range(n):
            bag, item, qty, weight, kind = struct.unpack_from("<HHIIB", data, 4 + 13 * j)
            if bag in (46285, 32919, 32920):
                cls.groups[bag].append((item, qty, weight, kind))

    def test_exact_original_clay_pot_chance_and_no_small_pot_confusion(self):
        bag = self.groups[46285]
        self.assertEqual(len(bag), 6)
        by_kind = {k: [r for r in bag if r[3] == k] for k in (1, 2, 3)}
        self.assertEqual(sum(r[2] for r in by_kind[3]), 10_000)
        self.assertEqual([r for r in by_kind[3] if r[0] == 32043], [(32043,1,2500,3)])
        self.assertFalse(any(r[0] == 32015 for r in bag))
        self.assertEqual(self.groups[32919], [(32043,1,10000,1)])
        self.assertEqual(self.groups[32920], [(32043,1,10000,1)])

    def test_original_quest10120_grants_real_source_bag_from_npc14216(self):
        s = EveArchive(ROOT/"gamedata/Eve.emg").parse_scene(11301)
        self.assertTrue(any(n["npc_id"] == 14216 and n["id"] == 8
                            and (n["x"],n["y"]) == (470,380)
                            and 3 in n["events"] for n in s["npcs"]))
        branches = s["events"][3]["conditions"]
        self.assertTrue(any(c["class"] == 2 and c["param"] == 10120
                            and any(r["type"] == 0 and r["class"] == 1 and
                                    r["param"] == 46285 and r["pstyle"] == 1
                                    and r["val"] == 1 for r in c["results"])
                            for c in branches))

    def test_no_unverified_npc_drop_for_item32043_is_sneaked_in(self):
        drops = json.loads((ROOT/"gamedata/npc_drops.json").read_text(encoding="utf-8"))
        self.assertFalse(any(e["itemId"] == 32043 for entries in drops.values() for e in entries),
                         "Do not invent a drop rate without an authoritative DVT NPC source")

if __name__ == "__main__":
    unittest.main()
