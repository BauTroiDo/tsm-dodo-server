"""Guard scoped DVT boss defeat exit pacing; never change other battle packet timing."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/DvtBattleDefeatRecoveryPolicy.kt"
BATTLE = ROOT / "gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/BattleManager.kt"
CLIENT = ROOT / "lua/Logic/FightManager.lua"
KOTLIN = ROOT / "tests/src/test/kotlin/tw/tsm/tests/DvtHoangPhuTungDefeatExitPacingTest.kt"


class DvtBattleExitPacingAudit(unittest.TestCase):
    def test_client_native_battle_end_is_independent_of_every_fighter_exit_packet(self):
        client = CLIENT.read_text("utf-8")
        fight_over = client.split("function FightManager.FightOver(roleId, guardIndex)", 1)[1].split(
            "function FightManager.RevExitFight(colm, row)", 1
        )[0]
        self.assertIn("FightField.ExitFight();", fight_over)
        self.assertIn("Role.players[roleId]:SetWar(EWar.None)", fight_over)

    def test_exact_boss_defeat_only_is_faster(self):
        policy = POLICY.read_text("utf-8")
        for x in (
            "data class ExitPacing(val startupMs: Long, val interFighterMs: Long)",
            "!playerWin && mapId == 12861",
            "warType == tw.tsm.domain.constants.EWar.EVENT",
            "14671 in opponentNpcIds",
            "ExitPacing(100L, 15L)",
            "ExitPacing(500L, 100L)",
        ):
            self.assertIn(x, policy)
        kt = KOTLIN.read_text("utf-8")
        self.assertIn("bossDefeatRemovesUnnecessarySerializedWaitButKeepsOrderedExitPackets", kt)
        self.assertIn("everyOtherBattleRetainsOriginal500And100MillisecondGaps", kt)

    def test_packets_keep_local_player_last_and_event_is_still_closed_after_cleanup(self):
        src = BATTLE.read_text("utf-8")
        start = src.index("// S:011-001 drives per-fighter exit animation.")
        end = src.index("// Release battle ownership before a continuation", start)
        part = src[start:end]
        for name in (
            "DvtBattleDefeatRecoveryPolicy.exitPacing(",
            "delay(exitPacing.startupMs)",
            "val exitOrder = allExitFighters.sortedBy",
            "fighter.isHumanPlayer && fighter.roleId == localPlayerId",
            "responseSender.sendBattleExitFight(session, fighter.col, fighter.row)",
            "delay(exitPacing.interFighterMs)",
            "Battle defeat exit pacing:",
        ):
            self.assertIn(name, part)
        self.assertLess(part.index("val exitOrder ="), part.index("responseSender.sendBattleExitFight("))
        self.assertLess(part.index("responseSender.sendBattleExitFight("), part.index("delay(exitPacing.interFighterMs)"))
        self.assertIn("cleanupBattle(battle)", src[end:end + 360])
        self.assertNotIn("sendEventEnd", part)


if __name__ == "__main__":
    unittest.main()
