#!/usr/bin/env python3
"""Restore DVT12998 and 12936 unlocated event NPCs; exact known Eve gates, no spawning."""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

# (mission,stage): (scene, x, y, kind=NPC, placement, original NPC template, EveNo).
TARGETS={
 (12998,1):(12007,590,480,1,4,38414,5),
 (12998,2):(12018,850,300,1,5,38412,6),
 (12936,4):(12421,490,520,1,10,11123,8),
 (12936,9):(12701,270,420,1,9,11074,14),
 (12936,21):(12701,850,620,1,10,14644,16),
}
PARENTS={
 (12936,4): (12936,5,0,(3,29521,1,(12421,490,520,1,10))),
 (12936,9): (12936,10,0,(1,11074,1,(12701,270,420,1,9))),
}
OLD_HASHES={
 "4acb01edebaf1f2fee8240bd4bb61d949c6146515da792a1869409cd66f20939",
 "de917171a4ae9acdab65566a93d146e70f5fca691724cbee9a6d03f848b3d2e2",
}
def sha(x):return hashlib.sha256(x).hexdigest()
def original_eve_checks(path):
 arc=EveArchive(Path(path))
 for (quest,stage),(sid,x,y,kind,placement,npc,eid) in TARGETS.items():
  scene=arc.parse_scene(sid)
  candidates=[n for n in scene["npcs"] if (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==
   (placement,npc,x,y,True) and eid in n["events"]]
  if len(candidates)!=1:
   raise ValueError(f"Source NPC missing or no longer quest-conditional: {quest}/{stage}")
  event=scene["events"][eid]
  if event["when_happen"]!=[True,False,False,False]:
   raise ValueError(f"NPC quest event is not interaction {quest}/{stage}")
  matching=[c for c in event["conditions"] if c["class"]==2
    and c["param"]==quest and c["pstyle"]==1 and c["ops"]==5
    and c["val"]==stage and c["to_result"]==c["no"]
    and any(z["type"]==0 and z["class"]==2 and z["param"]==quest
            and z["pstyle"]==1 and z["val"]==1 for z in c["results"])]
  if len(matching)!=1:
   raise ValueError(f"No exact step-advancing mission gate: {quest}/{stage}")
  if matching[0]["and_num"]!=1:
   raise ValueError(f"Unexpected extra AND requirement on quest {quest}/{stage}")
  # A second NPC in the 12998/2 teahouse has an auxiliary Eve, but the chosen
  # first quest event actually advances the step; do not target the auxiliary.
  if (quest,stage)==(12998,2):
   friend=[n for n in scene["npcs"] if n["id"]==6 and n["npc_id"]==38413
           and 7 in n["events"]]
   if len(friend)!=1:
    raise ValueError("Missing second teahouse guest in original source")
  print("EVE_PASS",quest,stage,"SCENE",sid,"NPC",npc,"EVE",eid,"CLOSED_FLAG",True)

def patch(path,eve,apply=False):
 p=Path(path);original_eve_checks(eve)
 raw=p.read_bytes()
 stages={(s["mission"],s["step"]):s for s in read_steps(p)}
 for key,data in TARGETS.items():
  st=stages[key]
  if st["end"] not in ((0,0,0,0,0),tuple(data[:5])):
   raise ValueError(f"Unknown or prepatched END: {key}")
  if any(c[0] for c in st["conditions"]):
   raise ValueError(f"Unexpected objective at stage {key}")
 for key,(q,s,slot,condition) in PARENTS.items():
  if stages[(q,s)]["conditions"][slot]!=condition:
   raise ValueError(f"Existing next-stage exact NPC evidence changed at {key}")
 if stages[(12998,3)]["end"]!=(12003,470,520,1,48):
  raise ValueError("Original 12998/3 return NPC missing")
 if stages[(12936,22)]["end"]!=(12421,710,600,1,11):
  raise ValueError("Original 12936/22 hand-in missing")
 # This patch only fills five blank ENDs, never modifies any packed NPC/scene def.
 locations=scan(raw)
 new=bytearray(raw)
 changed_positions=set()
 for key,data in TARGETS.items():
  ref=locations[(key[0],key[1],"END")]
  if tuple(ref[:5]) not in ((0,0,0,0,0),tuple(data[:5])):
   raise ValueError(f"Packed scan mismatch {key}")
  at=ref[-1]-7
  packed=struct.pack("<HHHBB",*data[:5])
  if bytes(new[at:at+8]) not in (bytes(8),packed):
   raise ValueError(f"Field modified unexpectedly at {at}")
  if bytes(new[at:at+8])==bytes(8):
   new[at:at+8]=packed
   changed_positions.update(range(at,at+8))
  print("SOURCE_FIELD",key,"OFFSET",at,"TARGET",data[:5])
 if new==raw:
  print("ALREADY_FIXED",p,sha(raw))
  return sha(raw)
 if sha(raw) not in OLD_HASHES or len(new)!=len(raw):
  raise ValueError("Unknown MarkStep source SHA/length; do not write")
 diffs={i for i,(a,b) in enumerate(zip(raw,new)) if a!=b}
 if not diffs or not diffs.issubset(changed_positions):
  raise ValueError("Changed bytes escaped exact original target records")
 for key,data in TARGETS.items():
  if tuple(scan(new)[(key[0],key[1],"END")][:5])!=tuple(data[:5]):
   raise ValueError(f"Target decode failed {key}")
 print("PATCH",p,"OLD_SHA",sha(raw),"NEW_SHA",sha(new),
       "ACTUAL_DIFF_BYTES",len(diffs),"RECORDS",len(TARGETS))
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(new)
 backup=p.with_name(p.name+".pre-dvt12998-12936-npc-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Source backup failed")
 tmp=p.with_name(p.name+".dvt12998-12936-npc-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(new)
  if sha(tmp.read_bytes())!=sha(new):raise IOError("Atomic temp SHA mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(new):raise IOError("Post-write SHA mismatch")
 print("BACKUP",backup)
 return sha(new)

if __name__=="__main__":
 parser=argparse.ArgumentParser()
 parser.add_argument("file",type=Path)
 parser.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 parser.add_argument("--apply",action="store_true")
 a=parser.parse_args()
 patch(a.file,a.eve,a.apply)
