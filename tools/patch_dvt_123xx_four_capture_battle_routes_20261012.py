#!/usr/bin/env python3
"""DVT four monster-capture arenas × four distinct source valid NPC choices.

These 16 fixed waypoints are ONE SOURCE-VALID SUGGESTED ROUTE.
They are NOT a claim NPC choice order is mandatory or adaptive to manual kills.
"""
from pathlib import Path
from datetime import datetime
import argparse,hashlib,os,struct
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan
ARENAS={
 12358:(62007,22087,((210,260),(1310,420),(810,1120),(30,1000))),
 12362:(62008,22088,((210,260),(1290,440),(50,980),(810,1120))),
 12366:(62009,22089,((90,360),(1270,480),(70,1020),(850,1120))),
 12370:(62010,22090,((210,300),(1270,460),(1330,760),(850,1120))),
}
OLD_SHAS={
 "8ee5a121997a2e1a1ab38338215da956e42ac86d51d1ca7b93bad2b607c65f0a",
 "a0814cec7dd5d8d272170fa995ec1741b4cdfb3048e7645f54f9ef3b917432c2",
}
def sha(b):return hashlib.sha256(b).hexdigest()
def effect(c,typ,cl,param,style,val):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==
       (typ,cl,param,style,val) for z in c["results"])
def source_proof(eve):
 a=EveArchive(Path(eve))
 target={}
 for q,(sid,npcid,coords) in ARENAS.items():
  scene=a.parse_scene(sid)
  if len(coords)!=4 or len(set(coords))!=4:raise ValueError(f"Repeat source NPC position {q}")
  # Original first-stage Door1 Eve1 was restored in prior guarded patch.
  entry=[d for d in scene["doors"] if d["id"]==1 and 1 in d["events"]]
  if len(entry)!=1 or scene["events"][1]["when_happen"]!=[False,True,False,False]:
   raise ValueError(f"Prior scene Arena Entry Door1 disappeared {q}")
  all_mobs=[n for n in scene["npcs"] if n["npc_id"]==npcid and n["id"] in range(2,7)]
  if len(all_mobs)!=5:raise ValueError(f"Source capture arena doesn't have five equivalent mobs {q}")
  for step in range(2,6):
   placement=step
   xy=coords[step-2]
   npc=[n for n in all_mobs if n["id"]==placement]
   if len(npc)!=1 or (npc[0]["x"],npc[0]["y"],npc[0]["close"],npc[0]["events"])!=(*xy,False,[placement]):
    raise ValueError(f"Original active source NPC placement changed {q}/{step}")
   ev=scene["events"][placement]
   if ev["when_happen"]!=[True,False,False,False]:
    raise ValueError(f"Monster capture interaction not genuine ClickNpc {q}/{step}")
   stages=[c for c in ev["conditions"] if
    (c["class"],c["param"],c["pstyle"],c["ops"],c["val"]) in
    ((2,q,1,2,1),(2,q,1,5,3),(2,q,1,5,4),(2,q,1,5,5))]
   if len(stages)!=4:raise ValueError(f"Four original minigame stage gates absent {q}/{step}")
   battle=[c for c in ev["conditions"] if
    (c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==
    (8,placement-1,1,0,0) and effect(c,0,2,q,1,1)]
   if len(battle)!=1:raise ValueError(f"Original victory progression missing {q}/{step}")
   completed=[c for c in ev["conditions"] if
     (c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==
      (2,q,1,5,5) and effect(c,0,2,q,3,0) and
      effect(c,0,2,q+1,1,1)]
   if len(completed)!=1:
    raise ValueError(f"Original final catch completion & followup missing {q}/{step}")
   target[(q,step)]=(sid,*xy,1,placement)
  print("SOURCE_PASS",q,"SCENE",sid,"SOURCE_NPCS",npcid,"FOUR DISTINCT EVE-VALID CHOICES")
 if len(target)!=16:raise ValueError("Expected 4x4 source valid paths")
 return target

def patch(file,eve,apply=False):
 p=Path(file);target=source_proof(eve)
 old=p.read_bytes()
 rows={(x["mission"],x["step"]):x for x in read_steps(p)}
 for q,(sid,npcid,coords) in ARENAS.items():
  if sorted(st for (mid,st) in rows if mid==q)!=[1,2,3,4,5]:
   raise ValueError(f"Original capture quest row count/layout changed {q}")
  entry=rows[(q,1)]["end"]
  if entry[0]!=sid or entry[3:]!=(2,1):
   raise ValueError(f"Original source Door1 stage1 endpoint changed {q}")
 for (q,step),destination in target.items():
  row=rows[(q,step)]
  if row["end"] not in ((0,0,0,0,0),destination):
   raise ValueError(f"Unknown MarkStep END source stage{q}/{step}")
  if any(c[0] for c in row["conditions"]):
   raise ValueError(f"New MarkStep side quest objective can't be overwritten {q}/{step}")
 refs=scan(old)
 new=bytearray(old);allowed=set()
 for (q,step),targetrow in sorted(target.items()):
  ref=refs[(q,step,"END")]
  if tuple(ref[:5]) not in ((0,0,0,0,0),targetrow):
   raise ValueError(f"Packed END record mismatch {q}/{step}")
  at=ref[-1]-7
  new[at:at+8]=struct.pack("<HHHBB",*targetrow)
  allowed.update(range(at,at+8))
  print("FIELD",q,step,"OFFSET",at,"END",targetrow)
 if new==old:
  print("ALREADY_FIXED",p,sha(old));return sha(old)
 if len(new)!=len(old) or sha(old) not in OLD_SHAS:
  raise ValueError("Original source edition SHA/length unknown")
 diff={i for i,(x,y) in enumerate(zip(old,new)) if x!=y}
 if not diff or not diff.issubset(allowed) or any(
  not diff.intersection(range(refs[(q,st,"END")][-1]-7,refs[(q,st,"END")][-1]+1))
  for (q,st) in target):
  raise ValueError("Unexpected modifications outside 16 source END fields")
 verify=scan(new)
 if any(tuple(verify[(q,st,"END")][:5])!=val for (q,st),val in target.items()):
  raise ValueError("Post patch packed END roundtrip mismatch")
 print("PATCH",p,"SHA_OLD",sha(old),"SHA_NEW",sha(new),"CHANGED",len(diff),"FIELDS",len(target))
 if not apply:print("DRY_RUN_ONLY");return sha(new)
 backup=p.with_name(p.name+".pre-123xx-four-captures-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(old)
 if sha(backup.read_bytes())!=sha(old):raise IOError("Backup checksum mismatch")
 temp=p.with_name(p.name+".four-captures-tmp")
 if temp.exists():raise FileExistsError(temp)
 try:
  temp.write_bytes(new)
  if sha(temp.read_bytes())!=sha(new):raise IOError("Temp checksum mismatch")
  os.replace(temp,p)
 finally:
  if temp.exists():temp.unlink()
 if sha(p.read_bytes())!=sha(new):raise IOError("Post-swap checksum mismatch")
 print("BACKUP",backup)
 return sha(new)
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 args=ap.parse_args()
 patch(args.file,args.eve,args.apply)
