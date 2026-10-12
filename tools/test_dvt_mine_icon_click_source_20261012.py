"""Pin original DVT Mining icon zero-default wire contract and strict server gates."""
import re,unittest
from pathlib import Path
from restore_dvt_uiopen_original_gates_20261012 import parse,ORIGINAL_SHA,sha
ROOT=Path(__file__).resolve().parents[1]
class MineIconSourceContract(unittest.TestCase):
 def test_mine_ui_is_quest_gated_not_globally_unlocked(self):
  table=ROOT/"File.dat/Data-ios/IOS_UiOpen_C.dat"
  self.assertEqual(sha(table.read_bytes()),ORIGINAL_SHA)
  ids={x["id"]:x for x in parse(table.read_bytes())}
  row=ids[77]
  self.assertEqual((row["kind"],row["group"],row["showFlag"]),(71,3,11804))
 def test_source_icon_sends_zero_only_to_info_query(self):
  f=(ROOT/"lua/Logic/Function.lua").read_text(encoding='utf8')
  segment=f.split("elseif functionData.kind == EFunction.Mine then")[-1].split("elseif functionData.kind == EFunction.BattlePass then")[0]
  self.assertIn("sendBuffer:WriteByte(0)",segment)
  self.assertIn("Network.Send(170, 1, sendBuffer)",segment)
  drop=(ROOT/"lua/UI/UIMine.lua").read_text(encoding='utf8').split("function UIMine.OnDropChange_MineChange(step)",1)[1].split("function UIMine.Mining(data)",1)[0]
  self.assertIn("step = step + 1",drop)
  self.assertIn("Network.Send(170, 1, sendBuffer)",drop)
  self.assertIn("for i = 1, 1 do",(ROOT/"lua/UI/UIMine.lua").read_text(encoding='utf8'))
 def test_kotlin_only_aliases_zero_for_info_query_not_entrance(self):
  src=(ROOT/"app/src/main/kotlin/tw/tsm/app/handlers/MiningHandler.kt").read_text(encoding='utf8')
  info=src.split("private suspend fun queryInfo",1)[1].split("private suspend fun enterMine",1)[0]
  enter=src.split("private suspend fun enterMine",1)[1].split("private suspend fun refill",1)[0]
  self.assertIn("if (requestedMineType == 0) 1 else requestedMineType",info)
  self.assertIn("if (mineType != 1)",info)
  self.assertIn("sendInfo(session, system.getView(session.roleId.toLong()))",info)
  self.assertIn("mineType != 1",enter)
  self.assertNotIn("requestedMineType == 0",enter)
  self.assertIn("session.battleId != 0L",enter)
  self.assertIn("partySystem.isInParty(session.playerId)",enter)
 def test_original_server_response_opens_ui_on_success_only(self):
  protocol=(ROOT/"lua/Common/protocal.lua").read_text(encoding='utf8')
  receive=protocol.split("protocolTable[170][1] = function(data)",1)[1].split("protocolTable[170][2] = function(data)",1)[0]
  self.assertIn("if res == 1 then",receive)
  self.assertIn("UI.Open(UIMine)",receive)
  self.assertIn("if UI.IsVisible(UIMine)",receive)
  self.assertIn("UIMine.SetData(type, lastMining, setTime, machinesData, dateLimit)",receive)
if __name__=="__main__":unittest.main()
