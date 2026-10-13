#!/usr/bin/env python3
"""Source proof: route DVT12936 stage16 to scene12403, whose entry Eve10 increments to stage17."""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

TARGET=(12403,220,420,1,8)
OLD_SHA={
 "98b8ac110c68acc8e2921b21f965e57e763878cdda4e69f015651a39599f2aa1",
 "6de2b89ace1c97c8ff674352da767bf02a57be78ccc1768cc58f4227d204834d",
}
def sha(x):return hashlib.sha256(x).hexdigest()
def original_source_proof(eve):
 arc=EveArchive(Path(eve))
 before=arc.parse_scene(12401)
 via=arc.parse_scene(12402)
 dest=arc.parse_scene(12403)
 if (before["scene_infos"][2]["target_scene"],via["scene_infos"][2]["target_scene"],
     dest["scene_infos"][1]["target_scene"])!=(12402,12403,12402):
  raise ValueError("Original three-scene entrance chain changed")
 npc=[n for n in dest["npcs"] if
      (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(8,12059,270,420,True)
      and 11 in n["events"]]
 if len(npc)!=1:raise ValueError("Original stage17 hidden NPC/placement missing")
 ev=dest["events"][10]
 if ev["when_happen"]!=[False,True,False,False] or any(
    10 in n["events"] for n in dest["npcs"]):
  raise ValueError("Stage16 must auto-trigger on map entry, not on fake NPC click")
 gate=[c for c in ev["conditions"] if c["class"]==2 and c["param"]==12936
       and c["pstyle"]==1 and c["ops"]==5 and c["val"]==16]
 if len(gate)!=1 or gate[0]["and_num"]!=1:
  raise ValueError("Original event has no simple stage16 source gate")
 effects=gate[0]["results"]
 def has(t,c,p,s,v):
  return any(x["type"]==t and x["class"]==c and x["param"]==p
    and x["pstyle"]==s and x["val"]==v for x in effects)
 if not all((has(0,2,12936,1,1),has(0,3,8,4,500),has(0,3,7,4,500))):
  raise ValueError("Original entry Eve must advance stage and show destination NPC")
 if not any(c["class"]==2 and c["param"]==12936 and c["pstyle"]==1
            and c["ops"]==5 and c["val"]==17 for c in dest["events"][11]["conditions"]):
  raise ValueError("Original stage17 destination interaction absent")
 return TARGET

def patch(path,eve,apply=False):
 p=Path(path)
 target=original_source_proof(eve)
 raw=p.read_bytes()
 rows={s["step"]:s for s in read_steps(p) if s["mission"]==12936}
 if rows[15]["end"]!=(12401,1150,220,1,9) or rows[17]["end"]!=target:
  raise ValueError("Original approach/NPC destination changed")
 if any(c[0] for c in rows[16]["conditions"]):
  raise ValueError("Stage16 is no longer original scene-entry objective")
 ref=scan(raw)[(12936,16,"END")]
 if tuple(ref[:5])==target:
  print("ALREADY_FIXED",p,sha(raw),target)
  return sha(raw)
 if tuple(ref[:5])!=(0,0,0,0,0) or sha(raw) not in OLD_SHA:
  raise ValueError("Unexpected input MarkStep SHA or stage16 END")
 at=ref[-1]-7
 new=bytearray(raw)
 new[at:at+8]=struct.pack("<HHHBB",*target)
 changed={i for i,(a,b) in enumerate(zip(raw,new)) if a!=b}
 if not changed or len(changed)>8 or not changed.issubset(set(range(at,at+8))):
  raise ValueError("Edited outside exact eight-byte field")
 if tuple(scan(new)[(12936,16,"END")][:5])!=target:
  raise ValueError("Edited data roundtrip failed")
 print("PATCH",p,"OLD_SHA",sha(raw),"NEW_SHA",sha(new),"AT",at,
       "DIFF_BYTES",len(changed),"END",target)
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(new)
 backup=p.with_name(p.name+".pre-12936-stage16-entry-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Backup checksum failure")
 tmp=p.with_name(p.name+".12936-stage16-entry-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(new)
  if sha(tmp.read_bytes())!=sha(new):raise IOError("Temp checksum failure")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(new):raise IOError("Post-atomic checksum failure")
 print("BACKUP",backup)
 return sha(new)
if __name__=="__main__":
 cli=argparse.ArgumentParser()
 cli.add_argument("file",type=Path)
 cli.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 cli.add_argument("--apply",action="store_true")
 a=cli.parse_args()
 patch(a.file,a.eve,a.apply)
