"""DEV-only menu icon gates: deterministic 84-row baseline, exclusions, no world-map unlock."""
import hashlib,struct,tempfile,unittest
from pathlib import Path
from patch_dev_ui_icon_menu_unlock_20261012 import (
    parse,patch_bytes,execute,sha,INPUT_HASH,OUTPUT_HASH,PROTECTED_IDS)
ROOT=Path(__file__).resolve().parents[1]
ORIGINAL=ROOT/"File.dat/Data-ios/IOS_UiOpen_C.dat"
CLIENT_SHAS="af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e"
SOURCE=ROOT/"lua/Logic/Function.lua"

def predecessor():
    raw=ORIGINAL.read_bytes()
    assert sha(raw)==CLIENT_SHAS,sha(raw)
    patched=bytearray(raw)
    positions={r["id"]:r for r in parse(raw)}
    for mid,level in ((62,10),(65,10),(63,15),(23,20),(66,60)):
        row=positions[mid]
        p0,p1,p2,p3=row["positions"]
        patched[p0]=level
        patched[p2]=level
        if mid==23:
            struct.pack_into("<H",patched,p1,0)
            struct.pack_into("<H",patched,p3,0)
    assert sha(patched)==INPUT_HASH,sha(patched)
    return bytes(patched)

class DevIconMenuUnlockProof(unittest.TestCase):
    def test_existing_staging_edits_reproduced_without_client_files(self):
        before=predecessor()
        self.assertEqual(len(parse(before)),84)
        self.assertEqual(sha(before),INPUT_HASH)
        self.assertEqual(len(before),2978)

    def test_50_icon_gates_unlock_without_touching_teleport(self):
        before=predecessor()
        after,touched,diff,oldrows,newrows=patch_bytes(before)
        self.assertEqual(sha(after),OUTPUT_HASH)
        self.assertEqual(len(touched),50)
        self.assertEqual(diff,146)
        before_by={r["id"]:r for r in oldrows}
        after_by={r["id"]:r for r in newrows}
        for mid,old in before_by.items():
            new=after_by[mid]
            for field in ("kind","group","name","icon","animation","msg","switch"):
                self.assertEqual(new[field],old[field],(mid,field))
            if mid==32 or old["group"] in (0,5,6):
                self.assertEqual(new,old,mid)
            elif old["group"] in (1,2,3) and old["kind"] and old["icon"] not in ("","0"):
                self.assertLessEqual(new["showLv"],1)
                self.assertEqual((new["showFlag"],new["requireLv"],new["requireFlag"]),(0,0,0))
        self.assertEqual(after_by[32]["showFlag"],11804)
        self.assertEqual(after_by[32]["requireFlag"],11804)
        self.assertNotIn(32,touched)

    def test_runtime_activity_and_housing_checks_not_disabled(self):
        source=SOURCE.read_text(encoding="utf-8")
        self.assertIn("bit.band(functionData.serverSwitch, Network.serverSwitch)",source)
        self.assertIn("MarkManager.GetMissionFlag(functionData.showFlag)",source)
        self.assertIn("MarkManager.GetMissionFlag(functionData.requireFlag)",source)
        self.assertIn("HouseManager.IsInHouse()",source)
        self.assertIn("UILimitedTimeSale.CheckTime()",source)
        self.assertIn("this.SetShowAll(not this.showAll)",(ROOT/"lua/UI/UIMainFunction.lua").read_text(encoding="utf-8"))

    def test_idempotent_and_fail_closed_unknown_data(self):
        original=predecessor()
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"UiOpen_C.dat"
            p.write_bytes(original)
            self.assertEqual(execute(p,True),OUTPUT_HASH)
            backups=list(Path(tmp).glob("*.bak"))
            self.assertEqual(len(backups),1)
            self.assertEqual(backups[0].read_bytes(),original)
            self.assertEqual(execute(p,False),OUTPUT_HASH)
            self.assertEqual(sha(p.read_bytes()),OUTPUT_HASH)
            p.write_bytes(bytes([original[0]^1])+original[1:])
            with self.assertRaises(ValueError):
                execute(p,False)

if __name__=="__main__":
    unittest.main()
