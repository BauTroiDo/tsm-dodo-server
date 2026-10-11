#!/usr/bin/env python3
"""Patch ONLY five source-verified missing item-NPC navigation targets.

DVT original Lua Data/MarkData.lua ReadStep uses an 8-byte target:
u16 scene, u16 X, u16 Y, u8 eventKind, u8 placementId.
The target is the single Eve NPC that gives the step's required item.
Works on BOTH 892087-byte original repo/server MarkStep and 928425-byte
client staging edition by locating rows dynamically. Never copy entire
server binary over the client binary.
"""
import argparse,hashlib,os,struct,sys
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan as locate_targets
from eve_tool import EveArchive

# mission, stage -> (item_id, scene, x, y, npc_id, npc_placement_index)
TARGETS = {
 (12158,1): (29372,14673,1390,180,40113,2),
 (12158,2): (29373,14671,1390,200,40114,2),
 (12158,3): (29374,14674,1390,200,40115,1),
 (12158,4): (29375,14680,1390,200,40116,2),
 (12158,5): (29376,14678,1390,180,40117,1),
}
ACCEPTED_SOURCE_SHA = {
 "cd5d4dfe175ae12fb71a4ad6c52f17d447335944d6efdfb269652534dbf3815f",
 "d239860783645d1e966346f5685a39442207b08ebdd09794b453219c91d89218",
}

def sha(raw: bytes):
 return hashlib.sha256(raw).hexdigest()

def patch(path: Path,eve_path: Path,apply:bool=False):
 before=path.read_bytes()
 refs=locate_targets(before)
 records={(s["mission"],s["step"]):s for s in read_steps(path)}
 eve=EveArchive(eve_path)
 after=bytearray(before)
 edits=[]
 seen=set()
 for key, (item_id, scene_id, x, y, npc_id, placement) in TARGETS.items():
  if key not in records:raise ValueError(f"MarkStep row missing: {key}")
  row=records[key]
  kind,item,qty,current=row["conditions"][0]
  if (kind,item,qty)!=(3,item_id,1):raise ValueError(f"Different quest requirement {key}: {(kind,item,qty)}")
  positions=[(idx,q[0],q[1],q[2],q[3]) for idx,q in enumerate(row["conditions"]) if q[0]!=0]
  if len(positions)!=1:raise ValueError(f"Multiple objective requirements {key}: {positions}")
  if row["end"]!=(0,0,0,0,0):raise ValueError(f"Unexpected END target {key}")
  e_scene=eve.parse_scene(scene_id)
  if e_scene is None:raise ValueError(f"No Eve scene {scene_id}")
  matches=[n for n in e_scene["npcs"] if n["id"]==placement and n["npc_id"]==npc_id and (n["x"],n["y"])==(x,y)]
  if len(matches)!=1:raise ValueError(f"Eve source NPC missing {key}: {matches}")
  if not any(res["type"] in (0,7) and res["class"]==1 and res["param"]==item_id and res["pstyle"]==1 and res["val"]==1
             for eid in matches[0]["events"] for cond in e_scene["events"][eid]["conditions"] for res in cond["results"]):
   raise ValueError(f"NPC never grants item {item_id} {key}")
  raw_tuple=(scene_id,x,y,1,placement)
  target_key=(key[0],key[1],"CONDITION_1")
  old=refs[target_key]
  offset=old[-1]-7
  if offset in seen:raise ValueError("Duplicate field offset")
  seen.add(offset)
  if old[:5]==raw_tuple:
   print('ALREADY_FIXED',key,raw_tuple)
   continue
  if old[:5]!=(0,0,0,0,0):
   raise ValueError(f"Unexpected nonblank original waypoint {key}: {old[:5]}")
  after[offset:offset+8]=struct.pack("<HHHBB",*raw_tuple)
  edits.append((key,item_id,raw_tuple,offset))
 original=sha(before)
 if edits and original not in ACCEPTED_SOURCE_SHA:raise ValueError(f"Unknown MarkStep edition hash {original}")
 changed={i for i,(a,b) in enumerate(zip(before,after)) if a!=b}
 allowed={n for _,_,_,pos in edits for n in range(pos,pos+8)}
 if not changed.issubset(allowed) or len(after)!=len(before):raise ValueError("Touched bytes outside 5 authorized fields")
 for key,item,target,offset in edits:
  if tuple(struct.unpack_from("<HHHBB",after,offset))!=target:raise ValueError(f"Bad output waypoint {key}")
 if len(edits) not in (0,5):raise ValueError(f"Partial edition mismatch; expected 0 or 5 fixes, got {len(edits)}")
 print("SOURCE",path,"SHA",original,"NEW_SHA",sha(after),"BYTE_CHANGES",len(changed),"RECORDS",len(records))
 for key,item,target,offset in edits:print("PATCH",key,"ITEM",item,"NPC_TARGET",target,"OFFSET",offset)
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(after)
 if not edits:
  print("NO_CHANGE")
  return original
 backup=path.with_name(path.name+".pre-dvt-12158-five-waypoints-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(before)
 if backup.read_bytes()!=before:raise IOError("Backup bytes mismatch")
 tempfile=path.with_name(path.name+".dvt12158-tmp")
 if tempfile.exists():raise FileExistsError(tempfile)
 try:
  tempfile.write_bytes(after)
  if tempfile.read_bytes()!=after:raise IOError("Staging failed")
  os.replace(tempfile,path)
 finally:
  if tempfile.exists():tempfile.unlink()
 if path.read_bytes()!=after:raise IOError("Post-patch bytes mismatch")
 print("APPLIED_BACKUP",backup)
 return sha(after)

if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 opts=ap.parse_args()
 patch(opts.file,opts.eve,opts.apply)
