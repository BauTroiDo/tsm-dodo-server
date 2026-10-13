#!/usr/bin/env python3
"""Source-proven DVT Wu Gang mission13119 14 original NPC destination routes.

Both scenes54937/54938 have the genuine same NPC38465 at same coordinates.
Use scene54937 as a fixed *valid choice*; 54938 remains equally valid, not
auto-selected by location. No quest stage, choices, item or reward modified.
"""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan

QUEST=13119
TARGET=(54937,1610,820,1,1)
ALT_TARGET=(54938,1610,820,1,1)
OLD_SHA={
 "16b5a3430afcf0cc8cbf0115b3d4ca8d7035269537fef0a55e9b2db6a3845043",
 "4ef26a52beb9d8a0a864b6688bff0264e3b71022dcc343e0db9e11cf91c66c15",
}
def sha(b):return hashlib.sha256(b).hexdigest()
def has(c,ty,cl,param,ps,v):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==
  (ty,cl,param,ps,v) for z in c["results"])
def source_proof(eve):
 a=EveArchive(Path(eve))
 for scene_id in (54937,54938):
  sc=a.parse_scene(scene_id)
  npcs=[n for n in sc["npcs"] if
   (n["id"],n["npc_id"],n["x"],n["y"],n["close"],n["events"])==
    (1,38465,1610,820,False,[1])]
  if len(npcs)!=1:raise ValueError(f"No matching original chopper NPC in {scene_id}")
  if any(1 in z["events"] for z in sc["doors"]):raise ValueError("Chopper Eve owned by Door")
  event=sc["events"][1]
  if event["when_happen"]!=[True,False,False,False]:
   raise ValueError("Chopper not original ClickNpc event")
  cs=event["conditions"]
  if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"])
     ==(2,QUEST,2,0,0) for c in cs):
   raise ValueError("Original unstarted chopper quest gate missing")
  if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"])
     ==(2,QUEST,1,2,0) and any(z["type"]==6 for z in c["results"]) for c in cs):
   raise ValueError("Original chopper in-progress choice gate missing")
  for ops,delta in ((30,1),(31,2),(32,3)):
   valid=[c for c in cs if
      (c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==(10,1,ops,0,0)
      and has(c,0,2,QUEST,1,delta)]
   if len(valid)!=1:raise ValueError(f"Original chopper callback{ops} +{delta} missing")
  for lower,upper in ((0,5),(4,10),(9,15)):
   if not any(c["class"]==2 and c["param"]==QUEST and c["pstyle"]==1
      and c["ops"]==2 and c["val"]==lower and c["and_num"]==2 for c in cs):
    raise ValueError(f"Original range lower bound {lower} absent")
   if not any(c["class"]==2 and c["param"]==QUEST and c["pstyle"]==1
      and c["ops"]==1 and c["val"]==upper for c in cs):
    raise ValueError(f"Original range upper bound {upper} absent")
  finals=[c for c in cs if c["class"]==2 and c["param"]==QUEST and c["pstyle"]==1
    and c["ops"]==2 and c["val"]==14 and has(c,0,2,QUEST,3,0)
    and has(c,0,2,13121,1,1) and has(c,0,1,29701,1,50)]
  if len(finals)!=1:raise ValueError("Original chopper stage>14 complete/reward missing")
  if any(z["type"]==0 and z["class"] in (11,13,15) for z in finals[0]["results"]):
   raise ValueError("Unknown effect on original chopper finish")
  print("SOURCE_PASS",scene_id,"NPC38465","SOURCE_CALLBACKS",30,31,32,"FINAL_REWARD","29701x50")
 return {st:TARGET for st in range(1,15)}
def patch(file,eve,apply=False):
 p=Path(file)
 target=source_proof(eve)
 original=p.read_bytes()
 rows=[r for r in read_steps(p) if r["mission"]==QUEST]
 if [r["step"] for r in rows]!=list(range(1,15)):
  raise ValueError("Original 14-step quest13119 MarkStep schema changed")
 for row in rows:
  if row["description"] not in ("幫助吳剛伐桂吧！","Giúp Ngô Cương chặt cây quế!") or any(c[0] for c in row["conditions"]):
   raise ValueError("Original Chinese chopper stage objective changed")
  if row["end"] not in ((0,0,0,0,0),TARGET):
   raise ValueError("Quest13119 unexpected pre-existing END")
 offsets=scan(original)
 out=bytearray(original);allowed=set()
 for st,dest in target.items():
  ref=offsets[(QUEST,st,"END")]
  if tuple(ref[:5]) not in ((0,0,0,0,0),TARGET):
   raise ValueError("Independent source packed END doesn't match")
  start=ref[-1]-7
  out[start:start+8]=struct.pack("<HHHBB",*dest)
  allowed.update(range(start,start+8))
  print("FIELD",st,"OFFSET",start,"END",dest)
 if out==original:
  print("ALREADY_FIXED",p,sha(original));return sha(original)
 if sha(original) not in OLD_SHA or len(out)!=len(original):
  raise ValueError("Unknown source edition SHA or length")
 diff={i for i,(a,b) in enumerate(zip(original,out)) if a!=b}
 if not diff or not diff.issubset(allowed) or any(not diff.intersection(
  range(offsets[(QUEST,st,"END")][-1]-7,offsets[(QUEST,st,"END")][-1]+1)) for st in target):
  raise ValueError("Unexpected changed bytes outside 14 END slots")
 check=scan(out)
 if any(tuple(check[(QUEST,st,"END")][:5])!=TARGET for st in target):
  raise ValueError("Packed field post-edit readback mismatch")
 print("PATCH",p,"OLD",sha(original),"NEW",sha(out),"DIFF_BYTES",len(diff))
 if not apply:print("DRY_RUN_ONLY");return sha(out)
 backup=p.with_name(p.name+".pre-13119-source-wugang-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(original)
 if sha(backup.read_bytes())!=sha(original):raise IOError("Backup checksum failed")
 tmp=p.with_name(p.name+".wugang13119-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(out)
  if sha(tmp.read_bytes())!=sha(out):raise IOError("Temp checksum mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(out):raise IOError("Post-edit file SHA wrong")
 print("BACKUP",backup)
 return sha(out)
if __name__=="__main__":
 a=argparse.ArgumentParser()
 a.add_argument("file",type=Path)
 a.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 a.add_argument("--apply",action="store_true")
 o=a.parse_args()
 patch(o.file,o.eve,o.apply)
