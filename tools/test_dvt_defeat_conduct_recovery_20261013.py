"""Prevent DVT loss in scene12861/Fight28 trapping client conduct after battle."""
from pathlib import Path
import sys
import unittest

R = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(R / "tools"))
from eve_tool import EveArchive

POLICY = R / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/DvtBattleDefeatRecoveryPolicy.kt"
EVE = R / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/EveFlowController.kt"
BATTLE = R / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/BattleManager.kt"
KTEST = R / "tests/src/test/kotlin/tw/tsm/tests/DvtBattleDefeatConductRecoveryTest.kt"


class BattleDefeatRecoveryAudit(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.event = EveArchive().parse_scene(12861)["events"][6]

    def test_real_dvt_scene12861_fight28_loss_is_one_talk_five_cosmetics(self):
        c = next(x for x in self.event["conditions"] if x["no"] == 16)
        self.assertEqual((c["class"], c["param"], c["pstyle"]), (8, 28, 2))
        self.assertEqual([(x["type"], x["class"], x["param"], x["pstyle"], x["val"])
                          for x in c["results"]], [
            (1, 3, 9, 0, 0),
            (0, 3, 9, 3, 2000), (0, 3, 10, 3, 2000),
            (0, 3, 11, 3, 2000), (0, 3, 12, 3, 2000),
            (0, 3, 13, 3, 2000)])
        self.assertEqual(c["results"][0]["mean_no"], 41353)

    def test_strict_policy_no_wins_other_eves_or_rewards(self):
        text = POLICY.read_text("utf-8")
        for marker in (
            "playerWin || mapId != 12861 || eveNo != 6 || fightDataKey != 28",
            "results.size != 6",
            "first.resultType != 1 || first.resultClass != 3",
            "first.parameter != 9 || first.resultMeanNo != 41353",
            "hides.map { it.parameter } == listOf(9, 10, 11, 12, 13)",
            "it.resultType == 0", "it.resultClass == 3",
            "it.parameterStyle == 3", "it.resultValue == 2000",
        ):
            self.assertIn(marker, text)

    def test_original_defeat_dialog_precedes_hides_and_native_45_end(self):
        text = EVE.read_text("utf-8")
        start = text.index("// Original DVT scene12861/Eve6/Fight28: TALK 41353 first")
        end = text.index("            stateMachine.replaceResultsAndResume(charId, postBattleResults", start + 1)
        branch = text[start:end]
        for marker in (
            "DvtBattleDefeatRecoveryPolicy.isCosmeticDefeat(",
            "val talk = stateMachine.advance(charId)",
            "talk.resultType != 1",
            "talk.resultClass != 3",
            "talk.resultMeanNo != 41353",
            "pendingDefeatTalks[charId] = runToken",
            "responseSender.sendCommonEvent(session, talk)",
            "watchDefeatTalk(session, charId, runToken)",
        ):
            self.assertIn(marker, branch)
        self.assertNotIn("resultExecutor.executeResultClass(session, hide)", branch)
        self.assertNotIn("notifyEventCompleted(", branch)

        handler = (R / "lua/Logic/Event/EventHandler.lua").read_text("utf-8")
        dialog = handler.split("EventHandler[1] = function", 1)[1].split("EventHandler[2] =", 1)[0]
        self.assertIn("eventManager.SetConduct(false)", dialog)
        self.assertIn("ShowTalkMessage(", dialog)
        self.assertIn("talkDatas[eventInfo.resultMeanNo]", dialog)
        self.assertIn("eventManager.SetConduct(true)", dialog)

        # The five hide packets must only be sent after the player's C020-006,
        # and the terminal step must use cancelAndEnd rather than quest completion.
        continuation = text.split("private suspend fun advanceDvtDefeatTalk(", 1)[1].split("    /**", 1)[0]
        self.assertIn("stateMachine.advance(charId)", continuation)
        self.assertIn("next.resultType != 0 || next.resultClass != 3", continuation)
        self.assertIn("next.parameterStyle != 3 || next.parameter !in 9..13", continuation)
        self.assertIn("resultExecutor.executeResultClass(session, next)", continuation)
        self.assertIn("responseSender.sendCommonEvent(session, next)", continuation)
        self.assertIn("cancelAndEnd(session, charId, DvtBattleDefeatRecoveryPolicy.DEATH_STOP_NAV_END_KIND)", continuation)
        self.assertNotIn("notifyEventCompleted(", continuation)
        self.assertNotIn("completeCurrentEvent(", continuation)

    def test_abandoned_talk_has_token_fenced_fail_safe(self):
        text = EVE.read_text("utf-8")
        policy = POLICY.read_text("utf-8")
        self.assertIn("TALK_WATCHDOG_MS = 120_000L", policy)
        self.assertIn("pendingDefeatTalks.remove(charId, runToken)", text)
        self.assertIn("stateMachine.clearIfCurrentRun(charId, runToken)", text)
        self.assertIn("delay(DvtBattleDefeatRecoveryPolicy.TALK_WATCHDOG_MS)", text)
        self.assertIn("pendingDefeatTalks.remove(charId)", text)

    def test_native_death_end_stops_navigation_and_quest_loss_arms_mine_grace(self):
        lua = (R / "lua/Logic/Event/EventManager.lua").read_text("utf-8")
        end = lua.split("function EventManager.ReceiveEndEvent(kind)", 1)[1].split(
            "function EventManager.ClearEventState()", 1)[0]
        self.assertIn("if kind == 45 then", end)
        self.assertIn("MarkManager.StopNavigation();", end)
        self.assertIn("MarkManager.ContinueNavigation();", end)
        self.assertIn("Role.deadMsgData.show = true;", end)

        policy = POLICY.read_text("utf-8")
        self.assertIn("DEATH_STOP_NAV_END_KIND = 45", policy)
        self.assertIn("needsPostDefeatMineGrace(", policy)
        mine = (R / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MineEncounterSystem.kt").read_text("utf-8")
        self.assertIn("fun onBattleEnd(characterId: Long, playerWin: Boolean = true)", mine)
        self.assertIn("DvtBattleDefeatRecoveryPolicy.needsPostDefeatMineGrace(", mine)
        self.assertIn("states.compute(characterId)", mine)
        self.assertIn("distancePx = 0", mine)
        self.assertIn("cooldownUntil = maxOf(previous?.cooldownUntil ?: 0L, graceUntil)", mine)
        server = (R / "app/src/main/kotlin/tw/tsm/app/ServerModule.kt").read_text("utf-8")
        self.assertIn("get<MineEncounterSystem>().onBattleEnd(session.roleId.toLong(), playerWin)", server)
        self.assertNotIn("get<MineEncounterSystem>().onBattleEnd(playerId, playerWin)", server)
        self.assertIn("get<EveFlowController>().resumeAfterBattle(session, playerId, playerWin)", server)
        flow = EVE.read_text("utf-8")
        self.assertIn("private fun cancelAndEnd(session: GameSession, charId: Long, endKind: Int = 0)", flow)
        self.assertIn("responseSender.sendEventEnd(session, endKind)", flow)

    def test_no_unconditional_post_exit_hp_overwrite_and_native_exit_order(self):
        s = BATTLE.read_text("utf-8")
        a = s.index("// S:011-001 drives per-fighter exit animation.")
        b = s.index("if (battle.warStyle == EWarStyle.WEEK_DUNGEON", a)
        chunk = s[a:b]
        self.assertIn("delay(500)", chunk)
        self.assertIn("delay(100)", chunk)
        self.assertIn("val exitOrder = allExitFighters.sortedBy", chunk)
        self.assertIn("responseSender.sendBattleExitFight(session, fighter.col, fighter.row)", chunk)
        self.assertIn("cleanupBattle(battle)", chunk)
        self.assertNotIn("exitPacing(", chunk)
        self.assertNotIn("post-exit HP resync", chunk)
        self.assertNotIn("playerStateManager.setHp(", chunk)
        self.assertNotIn("responseSender.sendSetAttribute(", chunk)

    def test_kotlin_original_eve_regressions_are_registered(self):
        kt = KTEST.read_text("utf-8")
        for name in (
            "originalDvtLossHasExactlyTalkAndFiveNpcHidesWithoutQuestOrRewards",
            "actualEveResolverSelectsDefeatBranchFromFight28Result2",
            "winOtherMapWrongFightAndUnexpectedRewardCannotAutoClose",
            "lossAndEscapeAreNotConfusedWithVictoryRewards",
            "defeatStopsNavigationAndProvidesGraceOnlyForOriginalQuest",
        ):
            self.assertIn(name, kt)


if __name__ == "__main__":
    unittest.main()
