#!/usr/bin/env python3
"""Restore two missing DVT NPC navigation targets only where original Eve proves them."""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

DEST={(10122,2):(11002,570,260,1,5),(10290,1):(14566,450,360,1,1)}
ACCEPT={
 "23714ce568f5bb3bca45645f07573d6810177a8c5c8d430889669a6789a24ed6",
 "f86a7448ff60e690590b384e248d4410a70ea6eac390f41246dcacc48aa1d21a",
}
def sha(x):return hashlib.sha256(x).hexdigest()
def cond(c,q,step):
 return c["class"]==2 and c["param"]==q and c["pstyle"]==1 and c["ops"]==5 and c["val"]==step
def effect(r,cls,param,pstyle,val):
 return r["type"]==0 and r["class"]==cls and r["param"]==param and r["pstyle"]==pstyle and r["val"]==val
def original_eve_verified(eve):
 a=EveArchive(Path(eve))
 outdoor=a.parse_scene(11002)
 hit=[n for n in outdoor["npcs"] if
      (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(5,17018,570,260,False)
      and 14 in n["events"]]
 if len(hit)!=1:raise ValueError("No unique visible 10122 spy NPC")
 evt=outdoor["events"][14]["conditions"]
 if not any(cond(c,10122,2) and c["to_result"]==3 and
           any(z["type"]==2 and z["class"]==7 and z["param"]==6 for z in c["results"])
           for c in evt):
  raise ValueError("Original 10122 stage2 does not ask spy to enter palace door6")
 if outdoor["scene_infos"][6]["target_scene"]!=11301:
  raise ValueError("Original palace ingress door no longer leads to 11301")
 inside=a.parse_scene(11301)
 if not any(n["id"]==8 and n["npc_id"]==14216 and 4 in n["events"]
            for n in inside["npcs"]):
  raise ValueError("Original palace NPC14216 absent")
 if not any(cond(c,10122,2) for c in inside["events"][4]["conditions"]):
  raise ValueError("10122 palace follow-through missing")
 cave=a.parse_scene(14566)
 mate=[n for n in cave["npcs"] if
       (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(1,35013,450,360,False)
       and 2 in n["events"]]
 if len(mate)!=1:raise ValueError("Original cave target not a visible NPC")
 candidates=cave["events"][2]["conditions"]
 opens=[c for c in candidates if cond(c,10290,1) and c["no"]==8]
 dialogs=[c for c in candidates if c["no"]==9 and c["class"]==8 and c["pstyle"]==1]
 if len(opens)!=1 or len(dialogs)!=1:
  raise ValueError("Original cave NPC branch missing")
 if not all(any(effect(z,*required) for z in dialogs[0]["results"])
            for required in ((1,29095,1,1),(2,10290,1,1))):
  raise ValueError("Cave does not grant actual Dong Tam item then advance quest")
 return DEST

def patch(path,eve,apply=False):
 p=Path(path)
 raw=p.read_bytes()
 orig=original_eve_verified(eve)
 steps={ (s["mission"],s["step"]):s for s in read_steps(p)
         if (s["mission"],s["step"]) in orig}
 if set(steps)!=set(orig) or any(any(c[0] for c in steps[key]["conditions"])
                                for key in orig for c in steps[key]["conditions"]):
  raise ValueError("Expected original dialogue-first mission records with no acquisition condition")
 refs=scan(raw)
 staged=bytearray(raw)
 mods=[]
 for key,t in orig.items():
  ref=refs[(*key,"END")]
  if tuple(ref[:5])==t:continue
  if tuple(ref[:5])!=(0,0,0,0,0):raise ValueError(f"Unexpected source END {key}")
  at=ref[-1]-7
  staged[at:at+8]=struct.pack("<HHHBB",*t)
  mods.append((key,at,t))
 if not mods:
  print("ALREADY_FIXED",p,sha(raw))
  return sha(raw)
 if len(mods)!=len(orig) or sha(raw) not in ACCEPT:raise ValueError("Unknown edition or partial patch")
 changed={i for i,(x,y) in enumerate(zip(raw,staged)) if x!=y}
 allowed={i for _,at,_ in mods for i in range(at,at+8)}
 if not changed or not changed.issubset(allowed) or len(raw)!=len(staged):
  raise ValueError("Diff escapes authorized END fields")
 reread=scan(staged)
 if any(tuple(reread[(*key,"END")][:5])!=target for key,target in orig.items()):
  raise ValueError("Patched destination parser mismatch")
 print("PATCH",p,"SHA_OLD",sha(raw),"SHA_NEW",sha(staged),"BYTE_DIFF",len(changed),"COUNT",len(mods))
 for key,at,target in mods:print(" ROUTE",key,"OFFSET",at,"END",target)
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(staged)
 backup=p.with_name(p.name+".pre-10122-10290-eve-npc-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Backup corruption")
 tmp=p.with_name(p.name+".quest-two-routes-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(staged)
  if sha(tmp.read_bytes())!=sha(staged):raise IOError("Staged write mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(staged):raise IOError("Final write mismatch")
 print("BACKUP",backup)
 return sha(staged)
if __name__=="__main__":
 a=argparse.ArgumentParser()
 a.add_argument("file",type=Path)
 a.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 a.add_argument("--apply",action="store_true")
 x=a.parse_args()
 patch(x.file,x.eve,x.apply)
