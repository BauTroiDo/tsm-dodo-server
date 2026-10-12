"""Source-guard DVT Treasure icon request/response and conservative Kotlin handshake."""
from pathlib import Path
import unittest,hashlib
from restore_dvt_uiopen_original_gates_20261012 import parse,ORIGINAL_SHA
ROOT=Path(__file__).resolve().parents[1]
def get(path):
 return (ROOT/path).read_text(encoding='utf8')
class TreasureIconSourceParity(unittest.TestCase):
 def test_original_ui_lock_and_exact_123_rpc(self):
  p=ROOT/"File.dat/Data-ios/IOS_UiOpen_C.dat"
  self.assertEqual(hashlib.sha256(p.read_bytes()).hexdigest(),ORIGINAL_SHA)
  row=next(r for r in parse(p.read_bytes()) if r['id']==82)
  self.assertEqual((row['kind'],row['group'],row['showFlag'],row['requireFlag']),(74,3,11804,11804))
  f=get('lua/Logic/Function.lua')
  branch=f.split('elseif functionData.kind == EFunction.Treasure then',1)[1].split('elseif functionData.kind == EFunction.Relationship then',1)[0]
  self.assertIn('UI.Open(UITreasure)',branch)
  self.assertIn('Network.Send(123, 1)',branch)
  self.assertIn('Treasure.sendOpenRequest = true',branch)
 def test_original_receiver_opens_after_snapshot(self):
  lua=get('lua/Common/protocal.lua')
  body=lua.split('protocolTable[123][1] = function(data)',1)[1].split('protocolTable[123][2] = function(data)',1)[0]
  for item in ['Treasure.Initialize()', 'local count = data:ReadInt32()',
               'if Treasure.sendOpenRequest then', 'UI.Open(UITreasure)',
               'Treasure.sendOpenRequest = false']:
   self.assertIn(item,body)
  self.assertIn('for i = 1, count do',body)
 def test_source_upgrades_have_eight_byte_requests_and_error_formats(self):
  ui=get('lua/UI/UITreasure.lua')
  self.assertIn('Network.Send(123, 2, sendBuffer)',ui)
  self.assertIn('Network.Send(123, 3, sendBuffer)',ui)
  for fragment in ('sendBuffer:WriteUInt16(Treasure.treasureItems[currentSelectCard].id)',
                   'sendBuffer:WriteInt32(1)',
                   'sendBuffer:WriteUInt16(Item.GetItemIndex('):
   self.assertIn(fragment,ui)
  protocol=get('lua/Common/protocal.lua')
  self.assertIn('protocolTable[123][2] = function(data)',protocol)
  self.assertIn('protocolTable[123][3] = function(data)',protocol)
  self.assertIn('elseif result == 255 then',protocol.split('protocolTable[123][2]',1)[1].split('protocolTable[123][3]',1)[0])
 def test_server_registers_123_with_immutable_mission_gate_and_no_resource_mutations(self):
  handler=get('app/src/main/kotlin/tw/tsm/app/handlers/TreasureHandler.kt')
  mod=get('app/src/main/kotlin/tw/tsm/app/ServerModule.kt')
  self.assertIn('private const val TREASURE_OPCODE = 123',handler)
  self.assertIn('register(get<TreasureHandler>())',mod)
  self.assertIn('single { TreasureHandler(get<QuestSystem>()) }',mod)
  self.assertIn('override val mainKind: Int = TREASURE_OPCODE',handler)
  self.assertIn('TREASURE_UNLOCK_BIT = 936',handler)
  self.assertIn('it.bitIndex == TREASURE_UNLOCK_BIT && it.bitValue != 0',handler)
  self.assertIn('.writeIntLE(0) // No persistent Treasure holdings exist yet',handler)
  self.assertEqual(handler.count('.writeByte(255)'),2)
  self.assertNotIn('inventorySystem.',handler)
  self.assertNotIn('moneySystem.',handler)
  self.assertNotIn('upsert',handler)
if __name__=='__main__':unittest.main()
