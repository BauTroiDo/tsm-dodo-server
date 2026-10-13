"""Regression guard for the DEV-stuck Kong Phuong / Kong Dung delivery mission."""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from eve_tool import EveArchive

EVALUATOR = ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveConditionEvaluator.kt"
KOTLIN_TEST = ROOT / "tests/src/test/kotlin/tw/tsm/tests/DvtKongRongGiftTurnInRegressionTest.kt"


class DvtKongRongDeliveryRegression(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.archive = EveArchive(ROOT / "gamedata" / "Eve.emg")
        cls.npc = cls.archive.parse_scene(11012)
        cls.giver = cls.archive.parse_scene(11101)

    def test_original_giver_grants_one_item_then_receiver_consumes_one_and_advances(self):
        giver_event = self.giver["events"][2]
        self.assertTrue(any(
            r["class"] == 1 and r["param"] == 30002 and r["val"] == 1
            for c in giver_event["conditions"] for r in c["results"]
        ))
        npc = next(n for n in self.npc["npcs"] if n["id"] == 1)
        self.assertEqual(npc["npc_id"], 11002)
        self.assertIn(8, npc["events"])
        event = self.npc["events"][8]
        cond16 = next(c for c in event["conditions"] if c["no"] == 16)
        cond17 = next(c for c in event["conditions"] if c["no"] == 17)
        self.assertEqual((cond16["class"], cond16["param"], cond16["val"], cond16["and_num"]),
                         (2, 10074, 1, 2))
        self.assertEqual((cond17["class"], cond17["param"], cond17["pstyle"],
                          cond17["ops"], cond17["val"]), (1, 30002, 1, 4, 1))
        self.assertTrue(any(r["class"] == 1 and r["param"] == 30002 and r["val"] == -1
                            for r in cond16["results"]))
        self.assertTrue(any(r["class"] == 2 and r["param"] == 10074 and
                            r["pstyle"] == 1 and r["val"] == 1 for r in cond16["results"]))

    def test_inventory_only_ops4_is_inclusive_while_dungeon_comparator_stays_strict(self):
        code = EVALUATOR.read_text("utf-8")
        block = code.split("            1 -> {", 1)[1].split("            2 -> {", 1)[0]
        self.assertIn("4 -> qty >= cond.conditionValue", block)
        self.assertIn("2 -> qty > cond.conditionValue", block)
        self.assertIn("6 -> qty != cond.conditionValue", block)
        self.assertIn("else -> compareStep(qty, cond.conditionOps, cond.conditionValue)", block)
        self.assertIn("4 -> actual > expected", code)

    def test_kotlin_real_dvt_eve_and_item_missing_contrast_covered(self):
        code = KOTLIN_TEST.read_text("utf-8")
        for name in (
            "giverAwardsExactlyOneGiftAndReceiverChecksInclusiveOne",
            "exactOneGiftMatchesOriginalStepAndSelectsNineResultDeliveryNotFallbackTalk",
            "missingGiftCannotAdvanceMissionOrConsumeItems",
            "unrelatedInventoryThresholdAndStrictGreaterOperatorsStayDistinct",
            "unrelatedMissionAndDungeonNumericComparisonsAreNotRelaxed",
        ):
            self.assertIn(name, code)


    def test_original_return_npc_closes_base_mission_then_starts_visible_alias(self):
        giver = self.giver["events"][2]
        step2 = next(c for c in giver["conditions"]
                     if c["class"] == 2 and c["param"] == 10074
                     and c["pstyle"] == 1 and c["val"] == 2)
        self.assertEqual(step2["no"], 14)
        results = step2["results"]
        self.assertTrue(any(r["class"] == 2 and r["param"] == 10075
                            and r["pstyle"] == 1 and r["val"] == 1
                            for r in results))
        self.assertTrue(any(r["class"] == 2 and r["param"] == 10074
                            and r["pstyle"] == 3 for r in results))
        self.assertTrue(any(r["class"] == 1 and r["param"] == 22701
                            and r["val"] == 1 for r in results))
        kt = KOTLIN_TEST.read_text("utf-8")
        for name in (
            "realEveResolverPicksGiftDeliveryInsteadOfGenericTalk",
            "afterDeliveryOriginalGiverCloses10074AndStartsVisible10075",
            "returningBeforeDeliveryDoesNotPrematurelyCompleteVisibleAlias",
        ):
            self.assertIn(name, kt)


if __name__ == "__main__":
    unittest.main()
