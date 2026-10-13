#!/usr/bin/env python3
"""Original DVT ghost-house 12444 stage1 single authentic A Dou follow-NPC waypoint.

Never route stages2–4 to fake clones: only stage1 has authentic source
class8 param38265 pStyle1 val0 attach effect in original Eve54947/NPC1.
Only MarkStep 12444/1 CONDITION_1 8-byte waypoint is eligible.
"""
import argparse
import hashlib
import os
import struct
from pathlib import Path
from datetime import datetime
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan as packed_scan

KEY=(12444,1)
DEST=(54947,1050,540,1,1)
BEFORE_SHA={
 "a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da",
 "511563050ea38ca86e8b85a9ec954fb1b8165ec12403b34521f3acf6b33e40f7",
}
AFTER_SHA={
 "11bba0ad361ff96266be77db1a3b5d6fcac37f82cd0345fb0c44777e8dbc1df2",
 "47c39a0b43e0e68117c1a964ade04af50adb879533d865a3d6df7d86cf2e6eed",
}

def sha(raw):
 return hashlib.sha256(raw).hexdigest()

def has_follow_attachment(c):
 return any((r["type"],r["class"],r["param"],r["pstyle"],r["val"])==
            (0,8,38265,1,0) for r in c["results"])

def verify_eve(eve):
 archive=EveArchive(Path(eve))
 s=archive.parse_scene(54947)
 if s is None:raise ValueError("Source haunted house 54947 missing")
 owners=[n for n in s["npcs"] if
  (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(1,38265,1050,540,False)
  and n["events"]==[1]]
 if len(owners)!=1:raise ValueError("Original genuine A Dou placement/Eve1 changed")
 if any(1 in d["events"] for d in s["doors"]):
  raise ValueError("Ambiguous Eve1 click ownership")
 e=s["events"][1]
 if e["when_happen"] != [True,False,False,False]:
  raise ValueError("Genuine A Dou not original ClickNpc")
 attached=[c for c in e["conditions"] if
  (c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==(2,12444,1,5,1)
  and has_follow_attachment(c)]
 if len(attached)!=1 or attached[0]["no"]!=7:
  raise ValueError("Source A Dou follower attachment not at stage1/C7")
 # All three other stage-specific clones in the original ending room lack attachment.
 for npc_id,stage in ((2,2),(3,3),(5,4)):
  npc=next((n for n in s["npcs"] if n["id"]==npc_id),None)
  if npc is None or npc["npc_id"]!=38265 or len(npc["events"])!=1:
   raise ValueError(f"Expected source clone missing: {npc_id}")
  ev=s["events"][npc["events"][0]]
  if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==
             (2,12444,1,5,stage) for c in ev["conditions"]):
   raise ValueError(f"Wrong stage-gated NPC clone mapping: {stage}")
  if any(has_follow_attachment(c) for c in ev["conditions"]):
   raise ValueError(f"Unexpected clone follow effect: stage {stage}")
 # The four adjoining rooms also contain NPC38265 puzzle candidates which
 # only change stage or dialog; they do not attach the follower.
 for sid in (54924,54925,54926,54927):
  room=archive.parse_scene(sid)
  if room is None:raise ValueError(f"Missing ghost room {sid}")
  for npc in room["npcs"]:
   if npc["npc_id"]!=38265:continue
   for eid in npc["events"]:
    if any(has_follow_attachment(c) for c in room["events"][eid]["conditions"]):
     raise ValueError(f"Unexpected follower attachment in ghost decoy room {sid}")
 # Verified quest turn-in is different NPC38266 at 54922/NPC1, not a follow target.
 returner=archive.parse_scene(54922)
 home=[n for n in returner["npcs"] if
       (n["id"],n["npc_id"],n["x"],n["y"])==(1,38266,1310,960)
       and 1 in n["events"]]
 if len(home)!=1:raise ValueError("Original mission giver/return NPC changed")
 print("SOURCE_PASS 54947/NPC1/type38265 Eve1/C7 unique stage1 follower, "
       "stages2/3/4 original decoys do not attach, return NPC38266 distinct")
 return DEST

def verify_steps(rows):
 steps=[r for r in rows if r["mission"]==12444]
 if [r["step"] for r in steps]!=[1,2,3,4]:
  raise ValueError("Original ghost puzzle four steps changed")
 for r in steps:
  if r["end"]!=(54922,1310,960,1,1):
   raise ValueError(f"Ghost house return scene marker changed at step{r['step']}")
  if len(r["conditions"])==0 or r["conditions"][0][:3]!=(1,38265,1):
   raise ValueError(f"Original stage{r['step']} follower objective missing")
  if any(c[0] for c in r["conditions"][1:]):
   raise ValueError("Unexpected extra source conditions")
  if r["step"]==1:
   if r["conditions"][0][3] not in ((0,0,0,0,0),DEST):
    raise ValueError("Stage1 source target is already unexpected")
  elif r["conditions"][0][3]!=(0,0,0,0,0):
   raise ValueError("Do not rewrite clone-dependent stages2-4")
 return True

def patch(path,eve,apply=False):
 path=Path(path)
 dest=verify_eve(eve)
 original=path.read_bytes()
 verify_steps(read_steps(path))
 fields=packed_scan(original)
 ref=fields[KEY[0],KEY[1],"CONDITION_1"]
 offset=ref[-1]-7
 if tuple(ref[:5]) not in ((0,0,0,0,0),dest):
  raise ValueError("Unexpected packed condition1 waypoint")
 updated=bytearray(original)
 updated[offset:offset+8]=struct.pack("<HHHBB",*dest)
 oldsha=sha(original)
 newsha=sha(updated)
 if updated==original:
  if oldsha not in AFTER_SHA:raise ValueError("Already filled condition, unknown global SHA")
  print("ALREADY_PATCHED",path,oldsha);return oldsha
 if oldsha not in BEFORE_SHA:raise ValueError("Unknown original complete file SHA")
 diffs={i for i,(a,b) in enumerate(zip(original,updated)) if a!=b}
 if not diffs or len(updated)!=len(original) or not diffs.issubset(set(range(offset,offset+8))):
  raise ValueError("Binary write outside single target field")
 checked=packed_scan(updated)
 if tuple(checked[KEY[0],KEY[1],"CONDITION_1"][:5])!=dest:
  raise ValueError("Independent packed roundtrip mismatch")
 print("FIELD",KEY,"CONDITION_1","OFFSET",offset,"DEST",dest)
 print("PATCH",path,"OLD_SHA",oldsha,"NEW_SHA",newsha,"DIFFERENT_BYTES",len(diffs))
 if not apply:print("DRY_RUN_ONLY");return newsha
 backup=path.with_name(path.name+".pre-12444-true-adou-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(original)
 if sha(backup.read_bytes())!=oldsha:raise IOError("Backup SHA mismatch")
 temporary=path.with_name(path.name+".12444-adou-tmp")
 if temporary.exists():raise FileExistsError(temporary)
 try:
  temporary.write_bytes(updated)
  if sha(temporary.read_bytes())!=newsha:raise IOError("Temp SHA mismatch")
  os.replace(temporary,path)
 finally:
  if temporary.exists():temporary.unlink()
 if sha(path.read_bytes())!=newsha:raise IOError("Postwrite SHA mismatch")
 print("BACKUP",backup)
 return newsha
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 args=ap.parse_args()
 patch(args.file,args.eve,args.apply)
