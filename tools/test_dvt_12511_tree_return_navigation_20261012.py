"""Original Christmas-tree return and four next-day reward stages DVT12511."""
import tempfile
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps,audit
from audit_dvt_quest_blockers_20261011 import scan
from patch_dvt_12511_tree_return_20261012 import TARGETS,verify_source,patch,sha,result
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="e48a4b7f50f6dfc4d3b902279bdda5efea0c783c3eb46f7c300a80db1bec13ad"
class ChristmasTreeStageRoutes(unittest.TestCase):
 def test_unique_click_npc_and_four_source_reward_branches(self):
  self.assertEqual(verify_source(EVE),TARGETS)
  sc=EveArchive(EVE).parse_scene(49944)
  npc=[x for x in sc["npcs"] if (x["id"],x["npc_id"])==(1,38001)]
  self.assertEqual(len(npc),1)
  self.assertEqual((npc[0]["x"],npc[0]["y"],npc[0]["close"]),(850,700,False))
  ev=sc["events"][1]
  self.assertEqual(ev["when_happen"],[True,False,False,False])
  self.assertTrue(any(c["class"]==14 and c["param"]==0 for c in ev["conditions"]))
  for st in (3,7,12,18):
   candidates=[c for c in ev["conditions"] if
    (c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==(2,12511,1,5,st)
    and result(c,2,12547,1,1) and result(c,2,12511,3,0) and result(c,2,12512,1,1)]
   self.assertEqual(len(candidates),1)
 def test_all_four_navigation_end_targets_no_condition_or_neighbor_changes(self):
  rows={(x["mission"],x["step"]):x for x in read_steps(DATA)}
  for key,dest in TARGETS.items():
   self.assertEqual(rows[key]["end"],dest)
   self.assertEqual([c for c in rows[key]["conditions"] if c[0]],[])
   self.assertEqual(rows[(12511,key[1]-1)]["end"],(49945,270,420,1,1))
   if key[1]<18:self.assertEqual(rows[(12511,key[1]+1)]["end"],(49945,270,420,1,1))
 def test_sha_and_idempotence_and_unknown_reject(self):
  original=DATA.read_bytes()
  self.assertEqual(sha(original),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),original)
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/"unknown.dat"
   broken=bytearray(original);broken[24]^=1;p.write_bytes(broken)
   with self.assertRaises(ValueError):patch(p,EVE,False)
 def test_audited_census(self):
  a=audit(DATA,EVE)
  self.assertEqual(a["markstep_rows"],4505)
  self.assertEqual(a["targets"]["located"],5932)
  self.assertEqual(a["missing_scenes"],[])
  self.assertEqual(a["missing_npcs"],[])
  self.assertEqual(a["missing_doors"],[])
  self.assertEqual(sum(x["end"]==(0,0,0,0,0) for x in read_steps(DATA)),63)
  b=scan(EVE,DATA)
  self.assertEqual(len(b["missing_end_targets"]),63)
  self.assertEqual(len(b["pending_condition_guided_end_targets"]),8)
  self.assertEqual(len(b["missing_end_without_pending_condition_guidance"]),55)
  self.assertEqual(len(b["unlocated_condition_targets"]),87)
if __name__=="__main__":unittest.main()
