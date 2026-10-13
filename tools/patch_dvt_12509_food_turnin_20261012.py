#!/usr/bin/env python3
"""Add only source-verified final food-delivery NPC for DVT mission12509 step8."""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

MISSION,STEP=12509,8
FOOD={26106:1,26013:5,26016:5}
END=(49944,330,360,1,5)
APPROVED_SHAS={
 "c06c8f2179b78033faac137cd7803d4c054f5f151a8f54df1f013f321972739e",
 "a810858dc35924ef19e537a6e73f8a684f9433420cd80a7b4aa1b6fea89df80c",
}
def sha(data):return hashlib.sha256(data).hexdigest()
def verify_receiver(eve):
 s=EveArchive(Path(eve)).parse_scene(49944)
 if s is None:raise ValueError("Missing Christmas scene")
 linked=[n for n in s["npcs"] if n["npc_id"]==15421 and
         (n["id"],n["x"],n["y"])==(5,330,360) and 10 in n["events"]]
 if len(linked)!=1:raise ValueError("No unique source-proven chef NPC15421")
 conds=s["events"][10]["conditions"]
 branch=[c for c in conds if c["no"]==10 and c["class"]==2 and
         c["param"]==MISSION and c["pstyle"]==1 and c["ops"]==5
         and c["val"]==STEP and c["to_result"]==10 and c["and_num"]==5]
 if len(branch)!=1:raise ValueError("DVT chef turn-in quest shape changed")
 for idx,(item,qty) in enumerate(FOOD.items(),start=11):
  if not any(c["no"]==idx and c["class"]==1 and c["param"]==item and
             c["pstyle"]==1 and c["ops"]==2 and c["val"]==qty-1 and
             c["to_result"]==10 for c in conds):
   raise ValueError(f"Missing original recipe ingredient {item}")
 def result(cls,param,ps,v):
  return any(r["type"]==0 and r["class"]==cls and r["param"]==param and
             r["pstyle"]==ps and r["val"]==v for r in branch[0]["results"])
 if not all(result(1,item,1,-qty) for item,qty in FOOD.items()):
  raise ValueError("Missing exact original consumption of the food bundle")
 if not (result(2,MISSION,1,1) and result(1,46484,1,1)
         and result(2,12511,1,1) and result(2,12549,1,1)):
  raise ValueError("Chef next-stage rewards no longer match original Eve")
 if any(r["type"]==0 and r["class"] in (11,13,15) for r in branch[0]["results"]):
  raise ValueError("Unexpected unsupported result cannot be bypassed")
 return END
def patch(path,eve,apply=False):
 p=Path(path)
 before=p.read_bytes()
 steps=[s for s in read_steps(p) if (s["mission"],s["step"])==(MISSION,STEP)]
 if len(steps)!=1:raise ValueError("MarkStep row missing or duplicated")
 original=steps[0]
 required=[(c[0],c[1],c[2]) for c in original["conditions"] if c[0]]
 if required!=[(3,26106,1),(3,26013,5),(3,26016,5)]:
  raise ValueError(f"Wrong ingredient requirements {required}")
 # These are real, previously vetted merchant waypoints. Do not rewrite them.
 expected=((13242,510,400,1,1),(13241,350,400,1,1),(12002,470,600,1,4))
 if tuple(c[3] for c in original["conditions"][:3])!=expected:
  raise ValueError("Source-vetted merchant waypoint changed")
 new_end=verify_receiver(eve)
 ref=scan(before)[(MISSION,STEP,"END")]
 if ref[:5]==new_end:
  print("ALREADY_FIXED",p,sha(before),new_end)
  return sha(before)
 if ref[:5]!=(0,0,0,0,0) or sha(before) not in APPROVED_SHAS:
  raise ValueError("Unknown current END or edition; refusing binary mutation")
 at=ref[5]-7
 changed=bytearray(before)
 changed[at:at+8]=struct.pack("<HHHBB",*new_end)
 diff={i for i,(x,y) in enumerate(zip(before,changed)) if x!=y}
 if not diff or not diff.issubset(set(range(at,at+8))) or len(changed)!=len(before):
  raise ValueError("Diff outside exact END destination bytes")
 if scan(changed)[(MISSION,STEP,"END")][:5]!=new_end:
  raise ValueError("Post-patch validation failed")
 print("PATCH",p,"OLD",sha(before),"NEW",sha(changed),"DIFF",len(diff),"OFFSET",at,"TARGET",new_end)
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(changed)
 backup=p.with_name(p.name+".pre-12509-food-turnin-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(before)
 if sha(backup.read_bytes())!=sha(before):raise IOError("Backup mismatch")
 tmp=p.with_name(p.name+".foodturnin-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(changed)
  if tmp.read_bytes()!=changed:raise IOError("Staged content mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if p.read_bytes()!=changed:raise IOError("Final bytes mismatch")
 print("BACKUP",backup)
 return sha(changed)
if __name__=="__main__":
 a=argparse.ArgumentParser()
 a.add_argument("file",type=Path)
 a.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 a.add_argument("--apply",action="store_true")
 x=a.parse_args()
 patch(x.file,x.eve,x.apply)
