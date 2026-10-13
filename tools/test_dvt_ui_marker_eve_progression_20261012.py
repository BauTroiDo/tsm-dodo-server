"""Original DVT beginner UI icon unlocks originate from legitimate Eve results.

All 84 UiOpen gate rows remain untouched. This test proves four exact source
mission flags are granted only from their original NPC/door interactions,
not from generated login/GM unlocks or hardcoded level-one bypass.
"""
import struct
import unittest
from collections import Counter
from pathlib import Path
from eve_tool import EveArchive

ROOT=Path(__file__).resolve().parents[1]
EVE=ROOT/"gamedata/Eve.emg"
MARK=ROOT/"File.dat/Data-ios/IOS_Mark_C.dat"
UI=ROOT/"File.dat/Data-ios/IOS_UiOpen_C.dat"
LUA=ROOT/"lua/Logic/Function.lua"
GATES={11804:936,12283:1375,12285:1376,12396:1434}
# scene, eve, source owner type/id, source condition class/param/ps/ops/val.
SOURCE={
 11804:[
   (10851,1,"DOOR",1,(10,2,30,0,0)),
   (10853,11,"DOOR",2,(10,3,30,0,0))],
 12283:[
   (10851,4,"NPC",1,(2,12282,1,5,2)),
   (10853,3,"NPC",1,(2,12282,1,5,12))],
 12285:[
   (10851,11,"NPC",9,(10,4,30,0,0)),
   (10851,11,"NPC",9,(10,4,31,0,0)),
   (10851,11,"NPC",9,(10,4,32,0,0)),
   (10851,11,"NPC",9,(10,4,33,0,0)),
   (10853,8,"NPC",3,(10,1,30,0,0)),
   (10853,8,"NPC",3,(10,1,31,0,0)),
   (10853,8,"NPC",3,(10,1,32,0,0)),
   (10853,8,"NPC",3,(10,1,33,0,0))],
 12396:[
   (10851,4,"NPC",1,(2,12280,1,5,4)),
   (10853,2,"NPC",1,(2,12280,1,5,14))]
}
def parse_mark():
 b=MARK.read_bytes()
 n=struct.unpack_from("<I",b)[0]
 p=4
 out={}
 for _ in range(n):
  l=struct.unpack_from("<H",b,p)[0];p+=2
  name=b[p:p+l].decode("utf-16le");p+=l
  kind=b[p];p+=1
  mid,bit=struct.unpack_from("<HH",b,p);p+=4
  p+=1
  l=struct.unpack_from("<H",b,p)[0];p+=2
  p+=l
  out[mid]=(bit,kind,name)
 assert p==len(b)
 return out

def parse_ui():
 b=UI.read_bytes();p=4;n=struct.unpack_from("<I",b)[0];out=[]
 for _ in range(n):
  rid,kind,group=struct.unpack_from("<BBB",b,p);p+=3
  p+=4
  for _ in range(2):
   l=struct.unpack_from("<H",b,p)[0];p+=2+l
  shlv=b[p];p+=1;shflag=struct.unpack_from("<H",b,p)[0];p+=2
  relv=b[p];p+=1;reqflag=struct.unpack_from("<H",b,p)[0];p+=2
  p+=5
  out.append((rid,kind,group,shlv,shflag,relv,reqflag))
 assert n==84 and p==len(b)
 return out

class DvtOriginalBeginnerUnlocks(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.arc=EveArchive(EVE)
  cls.marks=parse_mark()
  cls.ui=parse_ui()
 def test_original_mark_ids_are_real_and_ui_uses_them(self):
  for mission,bit in GATES.items():
   self.assertEqual(self.marks[mission][0],bit)
   self.assertGreater(bit,0)
  gated_ids={mission:[] for mission in GATES}
  for row in self.ui:
   for mission in GATES:
    if row[4]==mission or row[6]==mission:
     gated_ids[mission].append(row[0])
  for mission,ids in gated_ids.items():
   self.assertTrue(ids,f"Missing original UI gated-by-mission {mission}")
  self.assertIn(32,gated_ids[11804])
  self.assertIn(11,gated_ids[12396])
  self.assertIn(12,gated_ids[12283])
  self.assertIn(16,gated_ids[12285])
 def test_unique_source_eve_grant_branches_with_exact_gates(self):
  seen=Counter()
  for sid in self.arc.scenes:
   sc=self.arc.parse_scene(sid)
   for eid,ev in sc["events"].items():
    for c in ev["conditions"]:
     for r in c["results"]:
      if (r["type"],r["class"],r["pstyle"],r["val"])!=(0,2,1,1):
       continue
      if r["param"] not in GATES:continue
      owners=[("NPC",x["id"]) for x in sc["npcs"] if eid in x["events"]]
      owners += [("DOOR",x["id"]) for x in sc["doors"] if eid in x["events"]]
      shape=(c["class"],c["param"],c["pstyle"],c["ops"],c["val"])
      matches=[(sid,eid,kind,oid,shape) for kind,oid in owners]
      self.assertEqual(len(matches),1,f"Grant Eve owner ambiguous: scene{sid} Eve{eid}")
      self.assertIn(matches[0],SOURCE[r["param"]],f"Unexpected source marker grant {matches[0]}")
      seen[(r["param"],*matches[0])]+=1
  for mission,expected in SOURCE.items():
   self.assertEqual({k[1:] for k in seen if k[0]==mission},set(expected))
   self.assertTrue(all(value==1 for k,value in seen.items() if k[0]==mission))
  self.assertEqual(sum(seen.values()),14)
 def test_gates_are_not_from_character_level_only(self):
  gates={r[0]:r for r in self.ui}
  for marker in (11,12,16,32):
   r=gates[marker]
   self.assertNotEqual((r[4],r[6]),(0,0))
  for marker,mission in [(11,12396),(12,12283),(16,12285),(32,11804)]:
   r=gates[marker]
   self.assertTrue(mission in (r[4],r[6]))
  lua=LUA.read_text(encoding="utf-8")
  self.assertIn("MarkManager.GetMissionFlag(functionData.showFlag)",lua)
  self.assertIn("MarkManager.GetMissionFlag(functionData.requireFlag)",lua)
 def test_door_marker_grant_is_before_warp_in_original_eve(self):
  for scene,eve,_,_,_ in SOURCE[11804]:
   s=self.arc.parse_scene(scene)
   effects=[]
   for c in s["events"][eve]["conditions"]:
    if any(r["class"]==2 and r["param"]==11804 and r["pstyle"]==1 for r in c["results"]):
     effects=c["results"]
   self.assertGreaterEqual(len(effects),2)
   self.assertEqual((effects[0]["type"],effects[0]["class"],effects[0]["param"],effects[0]["pstyle"],effects[0]["val"]),(0,2,11804,1,1))
   self.assertEqual(effects[1]["type"],2)
   self.assertIn(effects[1]["param"],s["scene_infos"])
   self.assertEqual(s["scene_infos"][effects[1]["param"]]["target_scene"],12003)
if __name__=="__main__":unittest.main()
