import unittest
from collections import Counter
from pathlib import Path
from eve_tool import EveArchive

ROOT = Path(__file__).resolve().parents[1]


class DeployedDvtQuestItemPstyle3ParityTest(unittest.TestCase):
    def test_authoritative_eve_has_55_quest_item_take_operations(self):
        eve = EveArchive(ROOT / "gamedata" / "Eve.emg")
        data = []
        for sid in sorted(eve.scenes):
            scene = eve.parse_scene(sid)
            for event in scene["events"].values():
                for c in event["conditions"]:
                    for r in c["results"]:
                        if r["type"] in (0, 7) and r["class"] == 1 and r["pstyle"] == 3:
                            data.append((sid, r["param"], r["val"]))
        self.assertEqual(55, len(data))
        self.assertEqual(44, sum(v == 0 for (_, _, v) in data))
        self.assertEqual(11, sum(v > 0 for (_, _, v) in data))
        self.assertTrue(all(item > 0 and cap >= 0 for _, item, cap in data))
        self.assertEqual(24, Counter(sid for sid, _, _ in data)[54944])
        self.assertEqual(8, Counter(sid for sid, _, _ in data)[49984])

    def test_git_source_keeps_the_verified_live_server_quest_item_reclamation(self):
        src = (ROOT / "gamelogic" / "src" / "main" / "kotlin" / "tw" /
               "tsm" / "gamelogic" / "systems" / "EveResultExecutor.kt").read_text(
                   encoding="utf-8")
        # A live DEV hotfix existed outside the PR checkout in Oct 2026.
        # Removing any of these restores the original 55-event stall and
        # silently overwrites a tested player-item flow on redeployment.
        for required in (
            "private fun isClearAllQuestItem",
            "private fun isBoundedQuestItem",
            "isClearAllQuestItem(r) && !isBoundedQuestItem(r)",
            "val projectedBag =",
            "inventorySystem.getBag(session.roleId.toLong()).values",
            "isClearAllQuestItem(itemResult) -> -current.toInt()",
            "isBoundedQuestItem(itemResult) ->",
            "inventorySystem.removeBagItemsExact(charId, consumptions)",
            "responseSender.sendReduceBagItem(session, slot, quantity)",
            "DvtOrgResourceResultPolicy.decode(",
        ):
            if required == "isClearAllQuestItem(r) && !isBoundedQuestItem(r)":
                self.assertIn("!isClearAllQuestItem(r) && !isBoundedQuestItem(r)", src)
            else:
                self.assertIn(required, src, required)


if __name__ == "__main__":
    unittest.main()
