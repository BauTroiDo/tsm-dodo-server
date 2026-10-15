#!/usr/bin/env python3
"""Recover original DVT Chu-family oil trail Door Eve routes; no event/state modifications."""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

# q,stage : map, real Door coordinates, kind2, doorId, Eve event No
PROOFS={
 (12636,1):(49957,560,340,2,4,5),
 (12636,2):(49957,380,320,2,3,6),
 (12636,3):(49954,940,440,2,10,11),
 (12636,4):(49954,1180,740,2,11,12),
 (12636,5):(49954,1200,940,2,7,6),
 (12636,6):(49960,1120,780,2,3,6),
 (12636,7):(49960,920,580,2,4,7),
 (12636,8):(49960,480,440,2,5,8),
 (12636,9):(49960,320,540,2,2,2),
 (12636,10):(49961,720,520,2,2,2),
 (12636,11):(49961,580,360,2,3,3),
 (12650,1):(49957,560,340,2,4,8),
 (12650,2):(49958,400,320,2,3,7),
}
OLD_SHA={
 "363cb99326302577ca26b432716c4a9862fa16c8d164a5d592e11faf4959308a",
 "a7ac8cd9b1fc4b8479b5886adb1d2d6daa80e716a7b7fb4cced4ab5df1f7b11d",
}
def sha(b):return hashlib.sha256(b).hexdigest()
def check_eve(eve):
 archive=EveArchive(Path(eve))
 for (q,step),(sid,x,y,kind,doorid,eventid) in PROOFS.items():
  sc=archive.parse_scene(sid)
  doors=[z for z in sc["doors"] if (z["id"],z["x"],z["y"])==(doorid,x,y)
         and eventid in z["events"]]
  if len(doors)!=1:raise ValueError(f"Original door not found {q}/{step}, door {doorid} scene {sid}")
  if any(eventid in npc["events"] for npc in sc["npcs"]):
   raise ValueError(f"Selected Door Eve also bound to NPC {q}/{step}")
  ev=sc["events"][eventid]
  if ev["when_happen"]!=[False,True,False,False]:
   raise ValueError(f"Original Door trigger misclassified {q}/{step}")
  match=[c for c in ev["conditions"] if c["class"]==2 and c["param"]==q
          and c["pstyle"]==1 and c["ops"]==5 and c["val"]==step
          and c["and_num"]==1 and c["to_result"]==c["no"]]
  if len(match)!=1:raise ValueError(f"No isolated Eve quest step gate {q}/{step}")
  results=match[0]["results"]
  if not any(z["type"]==0 and z["class"]==2 and z["param"]==q
             and z["pstyle"]==1 and z["val"]==1 for z in results):
   raise ValueError(f"No genuine step +1 on door {q}/{step}")
  if any(z["type"]==0 and z["class"] in (11,13,15) for z in results):
   raise ValueError(f"Unsupported result class reached at {q}/{step}")
  print("SOURCE_PASS",q,step,"SCENE",sid,"DOOR",doorid,"EVE",eventid)
 # Two independent quests really share Door4 in original scene49957.
 shared=archive.parse_scene(49957)["doors"]
 door=next(z for z in shared if z["id"]==4)
 if not (5 in door["events"] and 8 in door["events"]):
  raise ValueError("Different quests' overlapping oil-trail door events changed")
 return PROOFS

def patch(file,eve,apply=False):
 p=Path(file)
 check_eve(eve)
 data=p.read_bytes()
 rows={(r["mission"],r["step"]):r for r in read_steps(p)}
 if rows[(12636,12)]["end"]!=(49955,410,370,1,1):
  raise ValueError("Original final return to Chu grandfather must remain unchanged")
 if rows[(12650,3)]["end"]!=(49955,410,370,1,1):
  raise ValueError("Original smaller quest return to Chu grandfather changed")
 for key,entry in PROOFS.items():
  row=rows[key]
  if row["end"] not in ((0,0,0,0,0),entry[:5]):
   raise ValueError(f"Unexpected original step END {key}")
  if any(c[0] for c in row["conditions"]):
   raise ValueError(f"Original oil-trace step has unexpected active conditions {key}")
 positions=scan(data)
 edited=bytearray(data)
 allowed=set()
 for key,t in PROOFS.items():
  ref=positions[(key[0],key[1],"END")]
  if tuple(ref[:5]) not in ((0,0,0,0,0),t[:5]):
   raise ValueError(f"Independent packed record mismatch {key}")
  at=ref[-1]-7
  edited[at:at+8]=struct.pack("<HHHBB",*t[:5])
  allowed.update(range(at,at+8))
  print("FIELD",key,"OFFSET",at,"END",t[:5])
 if edited==data:
  print("ALREADY_FIXED",p,sha(data))
  return sha(data)
 if sha(data) not in OLD_SHA or len(edited)!=len(data):
  raise ValueError("Unknown server/client original MarkStep edition")
 changed={i for i,(a,b) in enumerate(zip(data,edited)) if a!=b}
 if not changed or not changed.issubset(allowed):
  raise ValueError("Byte modifications outside 13 authorized original END fields")
 after=scan(edited)
 if any(tuple(after[(q,s,"END")][:5])!=tuple(t[:5]) for (q,s),t in PROOFS.items()):
  raise ValueError("Roundtrip decode mismatch")
 print("PATCH",p,"BEFORE",sha(data),"AFTER",sha(edited),"CHANGED_BYTES",len(changed),"FIELDS",len(PROOFS))
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(edited)
 backup=p.with_name(p.name+".pre-oil-trail-door-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(data)
 if sha(backup.read_bytes())!=sha(data):raise IOError("Original backup digest mismatch")
 temp=p.with_name(p.name+".oil-trail-door-tmp")
 if temp.exists():raise FileExistsError(temp)
 try:
  temp.write_bytes(edited)
  if sha(temp.read_bytes())!=sha(edited):raise IOError("Temp digest mismatch")
  os.replace(temp,p)
 finally:
  if temp.exists():temp.unlink()
 if sha(p.read_bytes())!=sha(edited):raise IOError("Atomic readback digest mismatch")
 print("BACKUP",backup)
 return sha(edited)

if __name__=="__main__":
 parser=argparse.ArgumentParser()
 parser.add_argument("file",type=Path)
 parser.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 parser.add_argument("--apply",action="store_true")
 args=parser.parse_args()
 patch(args.file,args.eve,args.apply)
