"""Verify exact DVT-original UI show/use level and quest gating (no DEV bypass)."""
import struct
import tempfile
import unittest
from pathlib import Path
from restore_dvt_uiopen_original_gates_20261012 import (
    parse,compare_gates,restore,sha,ORIGINAL_SHA,DEV_SHA)

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_UiOpen_C.dat"
GATES=("showLv","showFlag","requireLv","requireFlag")
PREVIOUS_STAGING_SHA="e5d123bb74520866f2002643ac90606432ea5dbd7240c8064c8e4e23f366eb48"

def reconstruct_accidental_dev_data(original):
    """Generate exact bad release in test memory only, never deploy it."""
    current=bytearray(original)
    by={r["id"]:r for r in parse(original)}
    for key,level in ((62,10),(65,10),(63,15),(23,20),(66,60)):
        p0,p1,p2,p3=by[key]["positions"]
        current[p0]=level
        current[p2]=level
        if key==23:
            struct.pack_into("<H",current,p1,0)
            struct.pack_into("<H",current,p3,0)
    if sha(current)!=PREVIOUS_STAGING_SHA:
        raise ValueError("Prior UI table stage unexpectedly changed")
    patched=bytearray(current)
    for r in parse(current):
        if r["id"]==32 or r["group"] not in (1,2,3) or r["kind"]==0 or r["icon"] in ("","0"):
            continue
        p0,p1,p2,p3=r["positions"]
        if r["showLv"]>1:patched[p0]=1
        if r["showFlag"]:struct.pack_into("<H",patched,p1,0)
        if r["requireLv"]:patched[p2]=0
        if r["requireFlag"]:struct.pack_into("<H",patched,p3,0)
    if sha(patched)!=DEV_SHA:
        raise ValueError("Accidental DEV UI bypass version not uniquely reproduced")
    return bytes(patched)

class OriginalDvtUiUnlockRequirements(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.canonical=DATA.read_bytes()
        cls.rows={x["id"]:x for x in parse(cls.canonical)}

    def test_source_84_records_byte_accurate(self):
        self.assertEqual(sha(self.canonical),ORIGINAL_SHA)
        self.assertEqual(len(self.canonical),2978)
        self.assertEqual(len(self.rows),84)
        self.assertEqual(self.canonical[:4],struct.pack("<I",84))

    def test_level_and_quest_milestones(self):
        sample={
            18:(15,0,15,0),   # Dungeon
            20:(10,0,10,0),   # Organization
            22:(15,0,15,0),   # Compound
            28:(15,0,15,0),   # Activity
            23:(0,11804,0,11804), # Furnace; keep original quest
            62:(0,11804,10,0), # Server lucky bag
            65:(0,11804,10,0), # Army lucky bag
            63:(0,11804,15,0), # Unbounded activity
            66:(0,11804,60,0), # House
            10:(0,11804,0,11804), # Special skills tree
            11:(0,12396,0,0), # Bag visibility flag
            12:(0,12283,0,0), # Status visibility flag
            16:(0,12285,0,0), # Team visibility flag
            32:(0,11804,0,11804), # Teleport/world map
            73:(0,11804,0,11804), # Elf
            75:(0,11804,0,11804), # Godskin
        }
        for key,fields in sample.items():
            with self.subTest(id=key):
                self.assertEqual(tuple(self.rows[key][k] for k in GATES),fields)

    def test_lua_enforces_both_visibility_and_actual_use(self):
        function=(ROOT/"lua/Logic/Function.lua").read_text(encoding="utf-8")
        data=(ROOT/"lua/Data/FunctionData.lua").read_text(encoding="utf-8")
        for phrase in (
            "function Function.CheckShow(functionId)",
            "function Function.CheckUse(functionId, showMessage)",
            "Role.CheckPlayerLv(functionData.showLv)",
            "MarkManager.GetMissionFlag(functionData.showFlag)",
            "Role.CheckPlayerLv(functionData.requireLv)",
            "MarkManager.GetMissionFlag(functionData.requireFlag)",
            "bit.band(functionData.serverSwitch, Network.serverSwitch)",
            "HouseManager.IsInHouse()",
            "if functionData.kind == EFunction.Festival then"):
            self.assertIn(phrase,function)
        for phrase in ("self.showLv = reader:ReadByte()",
                       "self.showFlag = reader:ReadUInt16()",
                       "self.requireLv = reader:ReadByte()",
                       "self.requireFlag = reader:ReadUInt16()"):
            self.assertIn(phrase,data)

    def test_mission_flag_packets_and_ui_refresh_are_kept(self):
        manager=(ROOT/"lua/Logic/MarkManager.lua").read_text(encoding="utf-8")
        protocol=(ROOT/"lua/Common/protocal.lua").read_text(encoding="utf-8")
        self.assertIn("function MarkManager.GetMissionFlag(missionId)",manager)
        self.assertIn("markDatas[missionId].bitId",manager)
        self.assertIn("function MarkManager.SetMissionFlag(bitIndex, bitValue, endData)",manager)
        flag_update=manager.split("function MarkManager.SetMissionFlag(",1)[1].split("function MarkManager.ShowPairMessage(",1)[0]
        self.assertIn("Function.UpdateVisible();",flag_update)
        self.assertIn("protocolTable[24][5] = function(data)",protocol)
        self.assertIn("MarkManager.SetMissionFlag(data:ReadUInt16(), data:ReadBoolean()",protocol)
        self.assertIn("protocolTable[24][7] = function(data)",protocol)
        self.assertIn("MarkManager.InitMissionFlag(data)",protocol)

    def test_exact_bypass_reverts_only_four_gate_columns(self):
        accidental=reconstruct_accidental_dev_data(self.canonical)
        changed,ids=compare_gates(accidental,self.canonical)
        self.assertEqual(len(changed),149)
        self.assertEqual(len(ids),50)
        self.assertIn(23,ids)
        self.assertIn(66,ids)
        self.assertNotIn(32,ids)
        for original,after in zip(parse(self.canonical),parse(accidental)):
            for field in ("id","kind","group","name","icon","animation","msg","switch"):
                self.assertEqual(original[field],after[field])

    def test_atomic_restore_backup_idempotence_unknown_rejected(self):
        old=reconstruct_accidental_dev_data(self.canonical)
        with tempfile.TemporaryDirectory() as td:
            target=Path(td)/"UiOpen_C.dat"
            target.write_bytes(old)
            self.assertEqual(restore(target,DATA,True),ORIGINAL_SHA)
            self.assertEqual(target.read_bytes(),self.canonical)
            backups=list(Path(td).glob("*.bak"))
            self.assertEqual(len(backups),1)
            self.assertEqual(backups[0].read_bytes(),old)
            self.assertEqual(restore(target,DATA,False),ORIGINAL_SHA)
            corrupted=bytearray(old)
            corrupted[300]^=1
            target.write_bytes(corrupted)
            with self.assertRaises(ValueError):
                restore(target,DATA,True)
            self.assertEqual(target.read_bytes(),corrupted)

if __name__=="__main__":
    unittest.main()
