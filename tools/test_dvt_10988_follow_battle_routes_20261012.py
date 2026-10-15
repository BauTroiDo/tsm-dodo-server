"""DVT Sun Shangxiang 10988 follower NPC13038 stage3/4 condition guide parity."""
import tempfile,unittest
from pathlib import Path
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from eve_tool import EveArchive
from patch_dvt_10988_follow_battle_routes_20261012 import KEYS,TARGET,sha,patch,verify
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="11bba0ad361ff96266be77db1a3b5d6fcac37f82cd0345fb0c44777e8dbc1df2"
class SourceSunShangxiangFollowerRoutes(unittest.TestCase):
 def test_source_eve_npc_and_battle_objective(self):
  rows=read_steps(DATA)
  self.assertTrue(verify(EVE,rows))
  s=EveArchive(EVE).parse_scene(18501)
  npc=next(n for n in s["npcs"] if n["id"]==4)
  self.assertEqual((npc["npc_id"],npc["x"],npc["y"],npc["close"]),
                    (10240,1870,1800,False))
  for stage in (3,4):
   self.assertTrue(any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"]) ==
          (2,10988,1,5,stage) for c in s["events"][7]["conditions"]))
 def test_two_conditions_and_existing_combat_end_unchanged(self):
  steps={(r["mission"],r["step"]):r for r in read_steps(DATA)}
  for k in KEYS:
   r=steps[k]
   self.assertEqual(r["end"],TARGET)
   self.assertEqual(r["conditions"][0],(1,13038,1,TARGET))
   self.assertFalse(any(c[0] for c in r["conditions"][1:]))
  self.assertEqual(steps[(10988,6)]["conditions"][0],(1,13038,1,TARGET))
  self.assertEqual(steps[(10988,5)]["conditions"][0][3],(0,0,0,0,0))
 def test_hash_idempotence_and_unknown_rejected(self):
  original=DATA.read_bytes()
  self.assertEqual(sha(original),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(original,DATA.read_bytes())
  with tempfile.TemporaryDirectory() as t:
   invalid=Path(t)/"corrupt.dat"
   x=bytearray(original);x[350]^=1;invalid.write_bytes(x)
   with self.assertRaises(ValueError):patch(invalid,EVE,False)
   self.assertEqual(invalid.read_bytes(),x)
 def test_census_and_no_false_target_issues(self):
  rep=audit(DATA,EVE)
  self.assertEqual(rep["markstep_rows"],4505)
  self.assertEqual(rep["targets"]["located"],5935)
  self.assertEqual(rep["missing_scenes"],[])
  self.assertEqual(rep["missing_npcs"],[])
  self.assertEqual(rep["missing_doors"],[])
  blockers=scan(EVE,DATA)
  self.assertEqual(len(blockers["missing_end_targets"]),63)
  self.assertEqual(len(blockers["pending_condition_guided_end_targets"]),8)
  self.assertEqual(len(blockers["missing_end_without_pending_condition_guidance"]),55)
  self.assertEqual(len(blockers["unlocated_condition_targets"]),84)
if __name__=="__main__":unittest.main()
