"""Guard the six byte-exact original DVT Treasure definition tables.

Tests are deliberately read-only: no account inventory, DB schema, gold or
Treasure stat mutation is permitted by the source-catalog auditor.
"""
import json,struct,tempfile,unittest
from pathlib import Path
from audit_dvt_treasure_tables_20261012 import (
    DATA,SCHEMAS,EXPECTED_SHA256,load_table,load_all,audit,
    load_treasure_item_catalog,level_requirement,star_requirement,
)

EXPECTED={
    "TreasureActivityCriteria":(86,9,778),
    "TreasureGroupInfo":(29,18,526),
    "TreasureGroupLV":(290,13,3774),
    "TreasureLv":(1140,22,25084),
    "TreasureRoleCriteria":(18,8,148),
    "TreasureStar":(684,21,14368),
}
class OriginalDvtTreasureTableRegression(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tables=load_all()
        cls.stats=audit(cls.tables)

    def test_six_tables_match_lua_readers_byte_length_and_canonical_sha(self):
        self.assertEqual(set(self.tables),set(EXPECTED))
        for name,values in EXPECTED.items():
            with self.subTest(name=name):
                d=self.tables[name]
                self.assertEqual((d["count"],d["size"],d["length"]),values)
                self.assertEqual(d["sha256"],EXPECTED_SHA256[name])
                self.assertEqual(d["count"],len(d["rows"]))

    def test_all_114_cards_have_ten_levels_and_six_stars(self):
        stats=self.stats
        self.assertEqual(stats["treasure_count"],114)
        self.assertEqual(stats["treasure_ids"],list(range(1,115)))
        self.assertEqual(stats["star_ids"],list(range(1,115)))
        self.assertEqual(set(stats["level_rows_by_id"].values()),{10})
        self.assertEqual(set(stats["star_rows_by_id"].values()),{6})
        self.assertEqual(stats["lv_values"],{i:114 for i in range(1,11)})
        self.assertEqual(stats["star_values"],{i:114 for i in range(1,7)})
        self.assertEqual(stats["duplicate_levels"],[])
        self.assertEqual(stats["duplicate_stars"],[])
        self.assertEqual(stats["missing_levels"],{})
        self.assertEqual(stats["missing_stars"],{})

    def test_29_groups_have_referential_integrity_with_ten_levels_each(self):
        stats=self.stats
        self.assertEqual(stats["combination_ids"],list(range(1,30)))
        self.assertEqual(stats["group_missing_treasure_refs"],{})
        self.assertEqual(stats["group_lv_missing_group_refs"],[])
        self.assertEqual(sum(stats["group_nonzero_card_counts"].values()),29)
        self.assertEqual(sum(r["level"]==10 for r in self.tables["TreasureGroupLV"]["rows"]),29)

    def test_no_negative_source_materials_or_money_and_sources_have_expected_ranges(self):
        st=self.stats
        self.assertEqual(st["invalid_costs"],{"level":[],"star":[]})
        self.assertEqual(st["level_item_count_range"],[20,800])
        self.assertEqual(st["level_money_count_range"],[100000,1000000])
        self.assertEqual(st["star_item_count_range"],[1,10])
        self.assertEqual(st["star_money_count_range"],[10000,100000])
        self.assertEqual(st["level_card_count_range"],[1,10])

    def test_item_c_kind98_links_all_114_treasures_without_duplicates(self):
        catalog=load_treasure_item_catalog()
        self.assertEqual(sorted(catalog),list(range(1,115)))
        self.assertEqual(len({item_id for item_id,name in catalog.values()}),114)
        self.assertEqual(catalog[1][0],25601)
        self.assertEqual(catalog[114][0],25714)
        self.assertTrue(all(name.strip() for _,name in catalog.values()))

    def test_read_only_level_quote_uses_exact_next_row_not_flat_estimates(self):
        quote=level_requirement(1,0,self.tables)
        self.assertEqual((quote["cardCopiesRequired"],
                          quote["materialsItemId"],
                          quote["materialsCount"],
                          quote["goldCost"]), (1,29869,20,100000))
        after_1=level_requirement(1,1,self.tables)
        self.assertEqual((after_1["targetLevel"],
                          after_1["cardCopiesRequired"],
                          after_1["consumedCardCopiesFromPriorLevels"],
                          after_1["materialsCount"],
                          after_1["goldCost"]), (2,2,1,40,200000))
        last=level_requirement(114,9,self.tables)
        self.assertEqual((last["targetLevel"],last["cardCopiesRequired"],
                          last["consumedCardCopiesFromPriorLevels"]), (10,10,45))
        self.assertIsNone(level_requirement(114,10,self.tables))

    def test_star_curve_is_source_only_not_invented_star_exp_progression(self):
        one=star_requirement(1,0,self.tables)
        self.assertEqual((one["id"],one["star"],one["itemCount"],one["moneyCount"]),
                         (1,1,1,10000))
        self.assertEqual(star_requirement(1,5,self.tables)["star"],6)
        self.assertIsNone(star_requirement(1,6,self.tables))

    def test_strict_inputs_reject_unknown_card_or_invalid_level(self):
        for id,lv in [(0,0),(115,0),(1,-1),(1,11)]:
            with self.subTest(item=id,level=lv),self.assertRaises(ValueError):
                level_requirement(id,lv,self.tables)
        for id,st in [(0,0),(115,0),(1,-1),(1,7)]:
            with self.subTest(item=id,star=st),self.assertRaises(ValueError):
                star_requirement(id,st,self.tables)

    def test_truncated_or_padding_extra_records_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            source=DATA/"IOS_TreasureLv_C.dat"
            path=Path(temp)/source.name
            blob=source.read_bytes()
            for altered in (blob[:-1],blob+b"\x00",struct.pack("<i",9999)+blob[4:]):
                path.write_bytes(altered)
                with self.assertRaises(ValueError):
                    load_table("TreasureLv",temp)

    def test_lua_documents_card_usage_and_distinct_material_ids(self):
        root=Path(__file__).resolve().parents[1]
        logic=(root/"lua/Logic/Treasure.lua").read_text(encoding="utf-8")
        ui=(root/"lua/UI/UITreasure.lua").read_text(encoding="utf-8")
        self.assertIn("count = count - data.lvData[i].count",logic)
        self.assertIn("itemInNeed[2].itemId = 29869",ui)
        self.assertIn("itemInNeed[1].itemId = 29870",ui)
        self.assertIn("itemInNeed[3].itemId = 65351",ui)
        self.assertIn("sendBuffer:WriteInt32(times)",ui)
        self.assertIn("slider_Exp.maxValue = 100",ui)
        # Multi-count StarExp rules still require a separately validated
        # state transition; static definitions alone do not authorize upgrades.

if __name__=="__main__":unittest.main()
