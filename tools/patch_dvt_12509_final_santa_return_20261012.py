#!/usr/bin/env python3
"""Source-backed DVT12509/16 follower hand-in to the original Santa entry event."""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

MISSION,STEP,FOLLOW=12509,16,38136
SANTA=(49945,270,420,1,1)
ORIGINAL_CAPTURE=(49946,590,310,1,12)
SOURCE_HASHES={
 "0cecf9af8234f7c31a16ab0937839c1b5bcfcea8e1f7be1872e7e85218e31c2a",
 "247bcb4c698e033a58e750d819c6861d668fdeb5431c0031c745266ba2648fab",
}
def sha(x):return hashlib.sha256(x).hexdigest()
def effect(x,cls,param,pstyle,val):
 return x["type"]==0 and x["class"]==cls and x["param"]==param and x["pstyle"]==pstyle and x["val"]==val
def verify_eve(eve):
 archive=EveArchive(Path(eve))
 christmas=archive.parse_scene(49945)
 if christmas is None:raise ValueError("Santa scene49945 does not exist")
 santa=[n for n in christmas["npcs"] if
        (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(1,38127,290,420,False)]
 if len(santa)!=1:raise ValueError("Expected visible original Santa NPC38127/placement1")
 if 9 not in christmas["events"] or christmas["events"][9]["when_happen"] != [False,True,False,False]:
  raise ValueError("Santa step16 completion is not the original scene-entry Eve9")
 if any(9 in n["events"] for n in christmas["npcs"]):
  raise ValueError("Original completion Eve9 must be scene-entry, not NPC click")
 c=christmas["events"][9]["conditions"]
 gate=[x for x in c if x["no"]==1 and x["class"]==2 and x["param"]==MISSION
       and x["pstyle"]==1 and x["ops"]==5 and x["val"]==STEP
       and x["and_num"]==2 and x["to_result"]==1]
 follow=[x for x in c if x["no"]==2 and x["class"]==9
         and x["pstyle"]==1 and x["ops"]==5 and x["val"]==FOLLOW
         and x["to_result"]==1]
 if len(gate)!=1 or len(follow)!=1:raise ValueError("Missing original Santa quest-and-follower gate")
 rs=gate[0]["results"]
 for e in ((2,12509,3,0),(2,12510,1,1),(8,38136,2,0)):
  if not any(effect(x,*e) for x in rs):raise ValueError(f"Missing original completion effect {e}")
 if any(x["type"]==0 and x["class"] in (11,13,15) for x in rs):
  raise ValueError("Unknown completion reward class; refuse route")
 # The alternate, source-original Khảm Bộc Từ completion remains untouched.
 for scene in (49947,49948):
  alternate=archive.parse_scene(scene)
  if alternate is None or 3 not in alternate["events"]:
   raise ValueError(f"Alternative cave completion scene{scene} absent")
  alt=alternate["events"][3]
  if alt["when_happen"]!=[False,True,False,False]:
   raise ValueError("Original alternative cave completion no longer scene-entry")
  cg=[x for x in alt["conditions"] if x["no"]==1 and x["class"]==2
      and x["param"]==MISSION and x["pstyle"]==1 and x["ops"]==5 and x["val"]==STEP]
  cf=[x for x in alt["conditions"] if x["no"]==2 and x["class"]==9 and x["val"]==FOLLOW]
  if len(cg)!=1 or len(cf)!=1 or not any(effect(z,2,MISSION,3,0) for z in cg[0]["results"]):
   raise ValueError("Alternate cave completion path changed")
 return SANTA

def patch(path,eve,apply=False):
 path=Path(path)
 raw=path.read_bytes()
 target=verify_eve(eve)
 rows=[s for s in read_steps(path) if s["mission"]==MISSION]
 stages={s["step"]:s for s in rows}
 if set((1,2,15,16))-set(stages):raise ValueError("Missing relevant Christmas stages")
 if stages[1]["end"]!=target or stages[2]["end"]!=target:
  raise ValueError("Must reuse exact existing original 12509/1,2 Santa waypoint")
 stage=stages[16]
 if stage["conditions"][0]!=(1,FOLLOW,1,ORIGINAL_CAPTURE) or any(x[0] for x in stage["conditions"][1:]):
  raise ValueError("Original follow thief NPC38136 objective changed")
 if stages[15]["end"]!=ORIGINAL_CAPTURE:
  raise ValueError("Previous original thief capture waypoint changed")
 ref=scan(raw)[(MISSION,STEP,"END")]
 if tuple(ref[:5])==target:
  print("ALREADY_FIXED",path,sha(raw),target)
  return sha(raw)
 if tuple(ref[:5])!=(0,0,0,0,0) or sha(raw) not in SOURCE_HASHES:
  raise ValueError("Unknown MarkStep edition or non-blank END")
 at=ref[-1]-7
 edited=bytearray(raw)
 edited[at:at+8]=struct.pack("<HHHBB",*target)
 diff={i for i,(a,b) in enumerate(zip(raw,edited)) if a!=b}
 if len(diff)!=8 or not diff.issubset(set(range(at,at+8))) or len(edited)!=len(raw):
  raise ValueError("Diff escaped authorized eight-byte END")
 if tuple(scan(edited)[(MISSION,STEP,"END")][:5])!=target:
  raise ValueError("Edited target parse mismatch")
 print("PATCH",path,"OLD_SHA",sha(raw),"NEW_SHA",sha(edited),"OFFSET",at,"DIFF_BYTES",len(diff),"TO",target)
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(edited)
 backup=path.with_name(path.name+".pre-12509-santa-final-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Backup SHA corrupt")
 tmp=path.with_name(path.name+".12509-santa-final-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(edited)
  if sha(tmp.read_bytes())!=sha(edited):raise IOError("Temporary write SHA mismatch")
  os.replace(tmp,path)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(path.read_bytes())!=sha(edited):raise IOError("Result SHA mismatch")
 print("BACKUP",backup)
 return sha(edited)

if __name__=="__main__":
 cli=argparse.ArgumentParser()
 cli.add_argument("file",type=Path)
 cli.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 cli.add_argument("--apply",action="store_true")
 a=cli.parse_args()
 patch(a.file,a.eve,a.apply)
