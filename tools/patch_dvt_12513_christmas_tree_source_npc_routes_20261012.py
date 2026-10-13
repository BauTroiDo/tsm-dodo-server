#!/usr/bin/env python3
"""Source-guarded DVT quest12513: 12 Christmas tree search NPC waypoints."""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

# stage: (scene, NPC placement, source Eve number, real NPC xy, original progress-effect values, clue)
SOURCES={
 2:(12106,9,2,470,300,(9,10,11),"Khổng tước"),
 3:(12140,2,3,190,380,(8,9,10),"Quảng trường"),
 4:(12171,3,4,650,360,(7,8,9),"Gần cổng"),
 11:(12004,3,6,350,360,(10,11,12),"1 con lợn"),
 12:(12137,2,3,510,460,(9,10,11),"nhà trống"),
 13:(12015,4,6,470,320,(8,9,10),"chắc chắn"),
 21:(13151,10,5,150,320,(10,11,12),"giàu có"),
 22:(13172,5,5,350,280,(9,10,11),"rèm"),
 23:(13283,9,3,110,460,(8,9,10),"không có chủ quán"),
 31:(18304,10,10,310,400,(9,),"Lễ đường"),
 32:(18171,3,5,650,360,(8,),"gần rừng"),
 33:(18134,3,5,610,300,(7,),"Đường lớn"),
}
INPUT_SHAS={
 "94bbeeb0fde711f0435d490928ffba94d86afaaa880f6868e4dc6320dff1c424",
 "985eac0351fc923ab904cf34791e96cdb983e02d1a521cfcdd6cf04230950825",
}
def sha(b):return hashlib.sha256(b).hexdigest()
def has(c,ty,cl,pa,ps,v):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==(ty,cl,pa,ps,v) for z in c["results"])
def target_rows():
 return {step:(sid,x,y,1,placement) for step,(sid,placement,eveid,x,y,values,clue) in SOURCES.items()}
def proof_source(eve_path):
 arc=EveArchive(Path(eve_path))
 for step,(sid,pid,eid,x,y,values,clue) in SOURCES.items():
  scene=arc.parse_scene(sid)
  npc=[n for n in scene["npcs"] if (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(pid,38289,x,y,True) and eid in n["events"]]
  if len(npc)!=1:raise ValueError(f"Original Christmas tree NPC missing at stage{step}")
  if any(eid in d["events"] for d in scene["doors"]):raise ValueError("Source NPC Eve wrongly classified as door")
  ev=scene["events"][eid]
  if ev["when_happen"]!=[True,False,False,False]:raise ValueError("Original tree NPC event trigger type changed")
  eligible=[c for c in ev["conditions"] if
    (c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["and_num"])==(2,12513,1,5,step,2)]
  # DVT choice has an inventory-present and inventory-absent branch, both must stay gated.
  if len(eligible)!=2:raise ValueError(f"Tree search stage{step} must have both inventory branches")
  grant=[c for c in eligible if
    has(c,0,1,29465,1,-1) and
    all(has(c,0,2,12513,1,v) for v in values)]
  if len(grant)!=1:raise ValueError(f"Missing original progress branch for stage{step}")
  branch=grant[0]
  def cond_for(c):
   return [a for a in ev["conditions"] if a["to_result"]==c["no"] and
           a["class"]==1 and a["param"]==29465 and a["pstyle"]==1]
  cond=cond_for(branch)
  if len(cond)!=1 or cond[0]["ops"]!=2 or cond[0]["val"]!=0:
   raise ValueError(f"Missing original item29465 absent-check at stage{step}")
  other=[c for c in eligible if c is not branch][0]
  cond_other=cond_for(other)
  if len(cond_other)!=1 or (cond_other[0]["ops"],cond_other[0]["val"])!=(1,1):
   raise ValueError(f"Missing original item29465 present-check at stage{step}")
  if any(z["type"]==0 and z["class"] in (11,13,15) for z in branch["results"]):
   raise ValueError(f"Unknown reward effect in source stage{step}")
  print("EVE_PASS",step,"MAP",sid,"NPC",pid,"EVE",eid,"PROGRESS",values)
 return target_rows()

def patch(file,eve,apply=False):
 path=Path(file)
 target=proof_source(eve)
 raw=path.read_bytes()
 quest={r["step"]:r for r in read_steps(path) if r["mission"]==12513}
 if len(quest)!=14 or sorted(quest)!=[1,2,3,4,11,12,13,21,22,23,31,32,33,40]:
  raise ValueError("Original 12513 quest step layout changed")
 for stage in (1,40):
  if quest[stage]["end"]!=(49945,270,420,1,1):
   raise ValueError("Original Santa begin/end waypoint must not change")
 for stage,t in target.items():
  row=quest[stage]
  if row["end"] not in ((0,0,0,0,0),t):
   raise ValueError(f"Stage{stage} original END isn't blank")
  if any(c[0] for c in row["conditions"]):
   raise ValueError(f"Unexpected MarkStep item objective stage{stage}")
  if SOURCES[stage][6].casefold() not in row["description"].casefold():
   raise ValueError(f"Clue mismatch in original DVT description stage{stage}")
 packed=scan(raw)
 edited=bytearray(raw)
 allowed=set()
 for stage,t in sorted(target.items()):
  rec=packed[(12513,stage,"END")]
  if tuple(rec[:5]) not in ((0,0,0,0,0),t):
   raise ValueError("Independent MarkStep packed index does not match")
  at=rec[-1]-7
  edited[at:at+8]=struct.pack("<HHHBB",*t)
  allowed.update(range(at,at+8))
  print("FIELD",stage,"OFFSET",at,"END",t)
 if edited==raw:
  print("ALREADY_FIXED",path,sha(raw))
  return sha(raw)
 if sha(raw) not in INPUT_SHAS or len(edited)!=len(raw):
  raise ValueError("Unrecognized source MarkStep SHA/size; refuse write")
 differences={i for i,(a,b) in enumerate(zip(raw,edited)) if a!=b}
 if not differences or not differences.issubset(allowed):
  raise ValueError("Binary changes outside 12 authorized 8-byte END fields")
 if any(tuple(scan(edited)[(12513,s,"END")][:5])!=t for s,t in target.items()):
  raise ValueError("Post-patch packed MarkStep verification failed")
 print("PATCH",path,"OLD_SHA",sha(raw),"NEW_SHA",sha(edited),"CHANGED_BYTES",len(differences))
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(edited)
 backup=path.with_name(path.name+".pre-12513-tree-npc-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Backup SHA incorrect")
 temporary=path.with_name(path.name+".tree-route-tmp")
 if temporary.exists():raise FileExistsError(temporary)
 try:
  temporary.write_bytes(edited)
  if sha(temporary.read_bytes())!=sha(edited):raise IOError("Temp SHA incorrect")
  os.replace(temporary,path)
 finally:
  if temporary.exists():temporary.unlink()
 if sha(path.read_bytes())!=sha(edited):raise IOError("Final SHA incorrect")
 print("BACKUP",backup)
 return sha(edited)

if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 args=ap.parse_args()
 patch(args.file,args.eve,args.apply)
