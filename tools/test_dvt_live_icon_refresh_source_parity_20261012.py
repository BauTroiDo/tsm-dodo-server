"""DVT in-session UI unlock refresh parity: level S008 and marker S024.

This is source-regression coverage, NOT a client bypass: all 84 UiOpen rows
retain their original level/mission gates.
"""
import hashlib,re,unittest
from pathlib import Path
from restore_dvt_uiopen_original_gates_20261012 import parse,ORIGINAL_SHA
ROOT=Path(__file__).resolve().parents[1]
LUA=ROOT/"lua"
TABLE=ROOT/"File.dat/Data-ios/IOS_UiOpen_C.dat"

def function_body(path,start,next_marker):
 src=path.read_text(encoding="utf-8")
 assert src.count(start)==1,(path,start)
 part=src.split(start,1)[1]
 assert next_marker in part,(path,next_marker)
 return part.split(next_marker,1)[0]

class DvtLiveUiUnlockSourceParity(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  raw=TABLE.read_bytes()
  assert hashlib.sha256(raw).hexdigest()==ORIGINAL_SHA
  cls.ui={x["id"]:x for x in parse(raw)}

 def test_source_level_thresholds_and_marker_gates_not_wiped(self):
  self.assertEqual(len(self.ui),84)
  for rid in (18,20,22,28,66):
   x=self.ui[rid]
   self.assertGreater(x["requireLv"],0)
  self.assertEqual(self.ui[18]["showLv"],15)
  self.assertEqual(self.ui[20]["showLv"],10)
  self.assertEqual(self.ui[22]["showLv"],15)
  self.assertEqual(self.ui[66]["requireLv"],60)
  self.assertEqual(self.ui[32]["requireFlag"],11804)
  self.assertEqual(self.ui[11]["showFlag"],12396)
  self.assertEqual(self.ui[12]["showFlag"],12283)
  self.assertEqual(self.ui[16]["showFlag"],12285)

 def test_server_lv_packet_decodes_to_player_and_calls_refresh_on_changed_level(self):
  decoder=function_body(LUA/"Common/protocal.lua",
         "protocolTable[8][1] = function(data)",
         "protocolTable[8][2] = function(data)")
  for phrase in ("local attrKind = data:ReadByte()",
                 "local sign = data:ReadByte()",
                 "local attrValue = data:ReadInt32()",
                 "local attrArg = data:ReadInt32()",
                 "Role.SetAttribute(EHuman.Player, Role.playerId, attrKind, attrValue, attrArg)"):
   self.assertIn(phrase,decoder)
  router=function_body(LUA/"Logic/Role.lua",
          "function Role.SetAttribute(kind, index, attrKind, attrValue, attrArg)",
          "function Role.SetMoveTarget(role)")
  self.assertIn("this.player:SetAttribute(attrKind, attrValue, attrArg)",router)
  level=function_body(LUA/"Controller/RoleController.lua",
          "elseif kind == EAttribute.Lv then", "elseif kind == EAttribute.Exp then")
  self.assertIn("self.attributes[EAttribute.Lv] = value",level)
  self.assertRegex(level,r"self.kind == EHuman.Player and oriLv ~= nil and oriLv ~= value and value ~= 0")
  self.assertIn("Function.UpdateVisible()",level)
  self.assertIn("Dungeon.InitAvailableDungeonDatas()",level)
  self.assertLess(level.index("self.attributes[EAttribute.Lv] = value"),level.index("Function.UpdateVisible()"))
  normal=(ROOT/"gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/BattleManager.kt").read_text(encoding="utf-8")
  self.assertGreaterEqual(normal.count("responseSender.sendSetAttribute(session, EAttribute.LV,"),2)

 def test_marker_incremental_flag_event_refreshes_immediately(self):
  handler=function_body(LUA/"Common/protocal.lua",
          "protocolTable[24][5] = function(data)", "protocolTable[24][7] = function(data)")
  self.assertIn("local count = data:ReadUInt32()",handler)
  self.assertIn("MarkManager.SetMissionFlag(data:ReadUInt16(), data:ReadBoolean(), i == count)",handler)
  mutator=function_body(LUA/"Logic/MarkManager.lua",
          "function MarkManager.SetMissionFlag(bitIndex, bitValue, endData)",
          "function MarkManager.ShowPairMessage(")
  self.assertIn("this.flags = SetFlag(this.flags, bitIndex, bitValue)",mutator)
  self.assertIn("Function.UpdateVisible()",mutator)
  self.assertLess(mutator.index("this.flags = SetFlag"),mutator.index("Function.UpdateVisible()"))
  self.assertIn('NotifyManager.Notify("CheckCondition", ECondition.MissionFlag)',mutator)

 def test_refresh_evaluates_original_visibility_and_usage_separately(self):
  fn=LUA/"Logic/Function.lua"
  show=function_body(fn,"function Function.CheckShow(functionId)",
                     "function Function.CheckUse(functionId, showMessage)")
  self.assertIn("Role.CheckPlayerLv(functionData.showLv)",show)
  self.assertIn("MarkManager.GetMissionFlag(functionData.showFlag)",show)
  use=function_body(fn,"function Function.CheckUse(functionId, showMessage)",
                    "function Function.UpdateVisible(...)")
  self.assertIn("Role.CheckPlayerLv(functionData.requireLv)",use)
  self.assertIn("MarkManager.GetMissionFlag(functionData.requireFlag)",use)
  refresh=function_body(fn,"function Function.UpdateVisible(...)",
                        "function Function.UpdateRedDot()")
  self.assertIn("local active, color = this.CheckShow(k)",refresh)
  self.assertIn("v.gameObject:SetActive(active)",refresh)
  menu=(LUA/"UI/UIMainFunction.lua").read_text(encoding="utf-8")
  self.assertIn("this.SetShowAll(not this.showAll)",menu)
  self.assertIn("Function.UpdateVisible()",menu)

 def test_login_initial_flag_packet_precedes_player_appearance(self):
  login=(ROOT/"gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/LoginService.kt").read_text(encoding="utf-8")
  self.assertIn("questSystem.reconcileStoredMarkerMissionFlags(record.id)",login)
  self.assertLess(login.index("questSystem.reconcileStoredMarkerMissionFlags(record.id)"),
                  login.index("responseSender.sendInitMissionFlags(session, flags)"))
  self.assertLess(login.index("responseSender.sendInitMissionFlags(session, flags)"),
                  login.index("responseSender.sendPlayerAppear("))

if __name__=="__main__":unittest.main()
