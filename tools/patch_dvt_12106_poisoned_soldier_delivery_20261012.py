#!/usr/bin/env python3
"""DVT12106: independently prove each of 20 medicine delivery NPC targets."""
import argparse
import hashlib
import math
import os
import struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

MISSION,ITEM,QTY=12106,29342,5
SOURCE=(30108,550,470,1,1)
STAGES=tuple(list(range(6,16))+list(range(18,28)))
PAIR_SCENES=((30892,(6,8,10,12,14)),(30891,(18,20,22,24,26)))
# Both duplicate soldiers per stage group have the same source-proven
# event semantics. Pick a deterministic *existing* placement; no new Eve.
PREFERRED={
 6:8,8:10,10:11,12:14,14:15,
 18:9,20:11,22:13,24:16,26:17,
}
SOURCE_HASHES={
 "8abb49e2636ff925af23a259e4b1924d9f7fe557f38c383f6912c84d14af3110",
 "60be6337107c15c7f7997542ff0f800b8f7c7fdab7342afa99983b7bacbdbce0",
}
def sha(x):return hashlib.sha256(x).hexdigest()

def effect(r, cls,param,style,val):
 return r["type"]==0 and r["class"]==cls and r["param"]==param and r["pstyle"]==style and r["val"]==val

def verify_targets(eve):
 archive=EveArchive(Path(eve))
 targets={}
 all_options={}
 for sid,even_steps in PAIR_SCENES:
  sc=archive.parse_scene(sid)
  if sc is None:raise ValueError(f"Missing DVT soldiers scene {sid}")
  for stage in even_steps:
   options=[]
   for npc in sc["npcs"]:
    if npc["npc_id"]!=17491 or npc["close"]:continue
    if not npc["events"]:continue
    for no in npc["events"]:
     event=sc["events"][no]
     stage_results=[c for c in event["conditions"]
       if c["class"]==2 and c["param"]==MISSION and c["pstyle"]==1
       and c["ops"]==5 and c["val"]==stage and
       any(effect(x,2,MISSION,1,1) for x in c["results"])]
     next_results=[c for c in event["conditions"]
       if c["class"]==2 and c["param"]==MISSION and c["pstyle"]==1
       and c["ops"]==5 and c["val"]==stage+1 and
       any(effect(x,1,ITEM,1,-1) for x in c["results"]) and
       any(effect(x,2,MISSION,1,1) for x in c["results"])]
     if len(stage_results)==1 and len(next_results)==1:
      options.append(npc)
   options.sort(key=lambda n:n["id"])
   if len(options)!=2 or any(n["id"]==options[0]["id"] for n in options[1:]):
    raise ValueError(f"Expected exactly two independently valid soldiers at {sid}/{stage}; got {[(n['id'],n['npc_id']) for n in options]}")
   picked=[n for n in options if n["id"]==PREFERRED[stage]]
   if len(picked)!=1:raise ValueError(f"Preferred soldier missing from confirmed pair {sid}/{stage}")
   npc=picked[0]
   target=(sid,npc["x"],npc["y"],1,npc["id"])
   targets[stage]=target
   targets[stage+1]=target
   all_options[stage]=[(n["id"],n["x"],n["y"]) for n in options]
 if set(targets)!=set(STAGES):raise ValueError("Wrong quest12106 stage coverage")
 return targets,all_options

def verify_steps(data,targets):
 rows=[s for s in read_steps(data) if s["mission"]==MISSION and s["step"] in STAGES]
 if len(rows)!=len(STAGES) or {s["step"] for s in rows}!=set(STAGES):
  raise ValueError("Mission12106 stage records missing or duplicated")
 for s in rows:
  if s["conditions"][0]!=(3,ITEM,QTY,SOURCE) or any(c[0] for c in s["conditions"][1:]):
   raise ValueError(f"Unexpected DVT medicine objective {s['step']}")
  if s["end"] not in ((0,0,0,0,0),targets[s["step"]]):
   raise ValueError(f"Unknown END at 12106/{s['step']}: {s['end']}")
 return rows

def patch(path,eve,apply=False):
 path=Path(path)
 raw=path.read_bytes()
 targets,options=verify_targets(eve)
 rows=verify_steps(path,targets)
 refs=scan(raw)
 edited=bytearray(raw)
 changes=[]
 old_count=0
 for stage in STAGES:
  ref=refs[(MISSION,stage,"END")]
  target=targets[stage]
  if ref[:5]==target:continue
  if ref[:5]!=(0,0,0,0,0):raise ValueError("Inconsistent stage END")
  at=ref[-1]-7
  edited[at:at+8]=struct.pack("<HHHBB",*target)
  changes.append((stage,at,target))
 if not changes:
  print("ALREADY_FIXED",path,sha(raw),"STAGES",len(STAGES))
  return sha(raw)
 if len(changes)!=len(STAGES) or sha(raw) not in SOURCE_HASHES:
  raise ValueError("Unexpected partially modified edition or SHA")
 allowed={x for _,at,_ in changes for x in range(at,at+8)}
 diff={i for i,(x,y) in enumerate(zip(raw,edited)) if x!=y}
 if not diff or not diff.issubset(allowed) or len(raw)!=len(edited):
  raise ValueError("Diff outside verified quest12106 END fields")
 reparsed=scan(edited)
 if any(reparsed[(MISSION,step,"END")][:5]!=targets[step] for step in STAGES):
  raise ValueError("Post-patch END decoding mismatch")
 print("PATCH",path,"OLD_SHA",sha(raw),"NEW_SHA",sha(edited),"TARGETS",len(changes),
       "DIFF_BYTES",len(diff))
 for stage,at,target in changes:
  print(" TARGET",stage,"OFFSET",at,"NPC",target,"VALID_ALTERNATIVES",options[stage if stage%2==0 else stage-1])
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(edited)
 backup=path.with_name(path.name+".pre-12106-soldier-returns-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Backup corrupted")
 tmp=path.with_name(path.name+".12106return-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(edited)
  if sha(tmp.read_bytes())!=sha(edited):raise IOError("Temp SHA mismatch")
  os.replace(tmp,path)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(path.read_bytes())!=sha(edited):raise IOError("Final SHA mismatch")
 print("BACKUP",backup)
 return sha(edited)

if __name__=="__main__":
 cli=argparse.ArgumentParser()
 cli.add_argument("file",type=Path)
 cli.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 cli.add_argument("--apply",action="store_true")
 args=cli.parse_args()
 patch(args.file,args.eve,args.apply)
