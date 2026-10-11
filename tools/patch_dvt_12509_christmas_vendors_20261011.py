#!/usr/bin/env python3
"""Repair only THREE original DVT Christmas mission12509/step8 shop item waypoints.

Reference: client Lua Data/MarkData.lua and Data/Eve/Eve_SurfaceData.lua.
Source NPC must actually open an Eve merchant Surface that sells exactly
the item required, and target placement must match that NPC's Eve index.
"""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan

# (condition index 1..5): (item, required, scene, npc type, npc placement, x, y, surface_id)
SOURCES={
 1:(26106,1,13242,16015,1,510,400,2),
 2:(26013,5,13241,16016,1,350,400,2),
 3:(26016,5,12002,16017,4,470,600,2),
}
SOURCE_SHA={
 "642a8e85c5ceabc9f4a59d97cefa96ed5c8839ff843322d234ef9768a24c2689",
 "e62963dee3af68824741979372e18e0a2d4884ef2a439787d1beb56218cbb7a3",
}

def sha(b):return hashlib.sha256(b).hexdigest()

def patch(path:Path,eve_path:Path,apply=False):
 original=path.read_bytes()
 old_sha=sha(original)
 refs=scan(original)
 steps={(row["mission"],row["step"]):row for row in read_steps(path)}
 if (12509,8) not in steps:raise ValueError("Mission12509 stage8 does not exist")
 st=steps[(12509,8)]
 if st["end"]!=(0,0,0,0,0):raise ValueError("The original Christmas stage8 return waypoint changed")
 assert len([c for c in st["conditions"] if c[0]!=0])==3
 eve=EveArchive(eve_path)
 new=bytearray(original)
 updated=[]
 for i,(item,qty,scene_id,npc_type,placement,x,y,surface_no) in SOURCES.items():
  k,got_item,got_qty,old_target=st["conditions"][i-1]
  if (k,got_item,got_qty)!=(3,item,qty):
   raise ValueError(f"Unexpected objective {i}: {(k,got_item,got_qty)}")
  sc=eve.parse_scene(scene_id)
  if not sc:raise ValueError(f"Missing scene {scene_id}")
  targets=[n for n in sc["npcs"] if n["id"]==placement and n["npc_id"]==npc_type and (n["x"],n["y"])==(x,y) and not n["close"]]
  if len(targets)!=1:raise ValueError(f"Shop NPC not found for item {item}")
  surface=sc["surfaces"].get(surface_no)
  if surface is None or surface["option_mode"]!=1:
   raise ValueError(f"NPC item {item} not associated with a merchant Surface")
  if not any(v["data_id"]==item and v["data_kind"]==2 and v["style"] in (2,3) for v in surface["sentences"]):
   raise ValueError(f"Item {item} absent from actual seller inventory")
  if not any(res["type"]==6 and res["mean_no"]==surface_no
             for event_no in targets[0]["events"]
             for c in sc["events"][event_no]["conditions"]
             for res in c["results"]):
   raise ValueError(f"The NPC does not open shop Surface for {item}")
  target=(scene_id,x,y,1,placement)
  kref=(12509,8,f"CONDITION_{i}")
  old=refs[kref]
  if old[:5]==target:continue
  if old[:5]!=(0,0,0,0,0):raise ValueError(f"Unknown/altered old target {kref}: {old[:5]}")
  offset=old[-1]-7
  new[offset:offset+8]=struct.pack("<HHHBB",*target)
  updated.append((i,item,target,offset))
 if len(updated) not in (0,3):raise ValueError(f"Unexpected partial patch {updated}")
 if updated and old_sha not in SOURCE_SHA:raise ValueError(f"Unexpected MarkStep edition SHA {old_sha}")
 changed={i for i,(a,b) in enumerate(zip(original,new)) if a!=b}
 permitted={j for _,_,_,at in updated for j in range(at,at+8)}
 if not changed.issubset(permitted) or len(new)!=len(original):raise ValueError("Changed data outside 3 targets")
 print("SOURCE",path,"ORIGINAL_SHA",old_sha,"AFTER_SHA",sha(new),"CHANGED_BYTES",len(changed))
 for row in updated:print("NEW_MERCHANT_WAYPOINT",*row)
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(new)
 if not updated:
  print("ALREADY_APPLIED")
  return old_sha
 backup=path.with_name(path.name+".pre-dvt-12509-multivendor-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(original)
 if backup.read_bytes()!=original:raise IOError("Unable to verify original backup")
 temp=path.with_name(path.name+".dvt-12509-tmp")
 if temp.exists():raise FileExistsError(temp)
 try:
  temp.write_bytes(new)
  if temp.read_bytes()!=new:raise IOError("Staging mismatch")
  os.replace(temp,path)
 finally:
  if temp.exists():temp.unlink()
 if path.read_bytes()!=new:raise IOError("Post-change mismatch")
 print("BACKUP",backup)
 print("APPLIED")
 return sha(new)

if __name__=="__main__":
 a=argparse.ArgumentParser()
 a.add_argument("file",type=Path)
 a.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 a.add_argument("--apply",action="store_true")
 z=a.parse_args()
 patch(z.file,z.eve,z.apply)
