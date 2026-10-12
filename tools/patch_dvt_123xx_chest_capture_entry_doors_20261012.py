#!/usr/bin/env python3
"""Restore original eight stage1 Door Eve waypoints for treasure hunt and monster-capture arenas."""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

# questId: (scene,Door coordinate x,y,kind2,doorId,EveNo,mode)
TARGETS={
 12330:(62003,1680,1580,2,1,1,"chest"),
 12346:(62004,2060,920,2,1,1,"chest"),
 12350:(62005,2900,1000,2,1,1,"chest"),
 12354:(62006,1240,1580,2,1,1,"chest"),
 12358:(62007,780,680,2,1,1,"capture"),
 12362:(62008,780,680,2,1,1,"capture"),
 12366:(62009,780,680,2,1,1,"capture"),
 12370:(62010,780,680,2,1,1,"capture"),
}
OLD_SHAS={
 "91eee7bb0f02b1357c4c166a5b32467a71842a292e74d1d66057d0e706b7595e",
 "c7e1b563fbba6a366556130657172febf196657ca3dfbaa32c0c45ff0b6a2969",
}
def sha(b):return hashlib.sha256(b).hexdigest()
def source_proof(eve):
 archive=EveArchive(Path(eve))
 for q,(sid,x,y,kind,door_id,eid,mode) in TARGETS.items():
  sc=archive.parse_scene(sid)
  actual=[door for door in sc["doors"] if
     (door["id"],door["x"],door["y"])==(door_id,x,y) and door["events"]==[eid]]
  if len(actual)!=1:raise ValueError(f"Real unique stage1 Door missing {q}")
  if any(eid in n["events"] for n in sc["npcs"]):
   raise ValueError(f"Ambiguous Door/NPC event binding {q}")
  ev=sc["events"][eid]
  if ev["when_happen"]!=[False,True,False,False]:
   raise ValueError(f"Eve{eid} in quest {q} not door-owned")
  gates=[c for c in ev["conditions"] if
         (c["class"],c["param"],c["pstyle"],c["ops"],c["val"],
          c["and_num"],c["to_result"])==(2,q,1,5,1,1,1)]
  if len(gates)!=1 or len(ev["conditions"])!=1:
   raise ValueError(f"Quest{q} stage1 original Eve has unexpected gates")
  effects=[z for z in gates[0]["results"] if z["type"]==0]
  if not any((z["class"],z["param"],z["pstyle"],z["val"])==(2,q,1,1) for z in effects):
   raise ValueError(f"Stage1 did not advance quest {q}")
  if any(z["class"] in (1,11,13,15) for z in effects):
   raise ValueError(f"Stage1 unexpectedly grants unsourced item/reward {q}")
  # Chest arena populates puzzle objects, capture arena reveals monsters.
  # All 8 stage1 events have original class3 map-placement operations.
  if not any(z["class"]==3 and z["val"]==2000 for z in effects):
   raise ValueError(f"No original arena setup effects at quest {q}")
  if mode=="chest":
   if not any(n["npc_id"]==20001 and n["events"] for n in sc["npcs"]):
    raise ValueError(f"Original chest scene has no interactive traps/objects {q}")
  else:
   if not any(n["npc_id"] in (22087,22088,22089,22090) and n["events"] for n in sc["npcs"]):
    raise ValueError(f"Original catch arena has no capture NPCs {q}")
  if sid!=62010 and sc["scene_infos"]:
   raise ValueError("Original isolated puzzle arena should not invent a warp")
  print("EVE_PASS",q,"SCENE",sid,"DOOR",door_id,"MODE",mode)
 return {q:t[:5] for q,t in TARGETS.items()}

def patch(file,eve,apply=False):
 p=Path(file)
 targets=source_proof(eve)
 raw=p.read_bytes()
 rows={(z["mission"],z["step"]):z for z in read_steps(p)}
 for q,t in targets.items():
  if len([z for z in rows if z[0]==q])!=5:
   raise ValueError(f"Original 5-step quest shape changed {q}")
  row=rows[(q,1)]
  if row["end"] not in ((0,0,0,0,0),t) or any(c[0] for c in row["conditions"]):
   raise ValueError(f"Original blank stage1 quest has other obligations {q}")
  # Capture/chest stages 2..5 are genuine multi-NPC interaction/battle
  # puzzles with no unique destination. Do not fabricate one.
  for st in (2,3,4,5):
   if rows[(q,st)]["end"]!=(0,0,0,0,0):
    raise ValueError(f"Existing arena sub-stage modified unexpectedly {q}/{st}")
 refs=scan(raw)
 edited=bytearray(raw)
 allowed=set()
 for q,t in targets.items():
  rec=refs[(q,1,"END")]
  if tuple(rec[:5]) not in ((0,0,0,0,0),t):
   raise ValueError(f"Original packed MarkStep data disagrees: {q}")
  at=rec[-1]-7
  edited[at:at+8]=struct.pack("<HHHBB",*t)
  allowed.update(range(at,at+8))
  print("FIELD",q,"AT",at,"TARGET",t)
 if edited==raw:
  print("ALREADY_FIXED",p,sha(raw))
  return sha(raw)
 if sha(raw) not in OLD_SHAS or len(edited)!=len(raw):
  raise ValueError("Unexpected source/client MarkStep predecessor SHA")
 changes={i for i,(a,b) in enumerate(zip(raw,edited)) if a!=b}
 if len(changes)!=64 or changes!=allowed:
  raise ValueError("Expected 64 changes ONLY within 8 original END fields")
 if any(tuple(scan(edited)[(q,1,"END")][:5])!=t for q,t in targets.items()):
  raise ValueError("Packed END roundtrip failed")
 print("PATCH",p,"OLD_SHA",sha(raw),"NEW_SHA",sha(edited),
       "CHANGED_BYTES",len(changes),"FIELDS",len(targets))
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(edited)
 backup=p.with_name(p.name+".pre-123xx-arena-entry-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):
  raise IOError("Source backup SHA incorrect")
 temporary=p.with_name(p.name+".arena-entry-tmp")
 if temporary.exists():raise FileExistsError(temporary)
 try:
  temporary.write_bytes(edited)
  if sha(temporary.read_bytes())!=sha(edited):raise IOError("Temp SHA invalid")
  os.replace(temporary,p)
 finally:
  if temporary.exists():temporary.unlink()
 if sha(p.read_bytes())!=sha(edited):raise IOError("Post-deploy file SHA mismatch")
 print("BACKUP",backup)
 return sha(edited)

if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 args=ap.parse_args()
 patch(args.file,args.eve,args.apply)
