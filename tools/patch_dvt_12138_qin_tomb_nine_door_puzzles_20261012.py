#!/usr/bin/env python3
"""Original DVT Qin tomb quest12138 nine physical puzzle Doors across paired 18 steps."""
import argparse
import hashlib
import os
import re
import struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

# Pair (odd,even) uses same Door and original Eve. (scene, x, y, DoorID, EveID, original item grant)
PAIRS={
 1:(14620,1260,1400,8,10,29363),
 3:(14623,420,820,2,3,29364),
 5:(14622,1700,2260,8,9,29365),
 7:(14630,1160,380,2,3,29366),
 9:(14632,2080,980,8,10,29367),
 11:(14629,420,580,2,3,29368),
 13:(14627,1080,360,2,3,29369),
 15:(14621,960,1000,6,8,29370),
 17:(14625,420,380,2,3,29371),
}
ORIGINAL_SHAS={
 "918133ebee509d28b964de93c0820e1eccc7c75557263e7ba20b735a8a1dc5f8",
 "24f4bb1a825c4db902e3f5fd9f713a174ac25feaa0d64f4666fa8bbbe4669288",
}
def sha(data):return hashlib.sha256(data).hexdigest()
def has_effect(c,t,cl,p,ps,v):
 return any(z["type"]==t and z["class"]==cl and z["param"]==p
            and z["pstyle"]==ps and z["val"]==v for z in c["results"])
def target_rows():
 return {step:(sid,x,y,2,door)
         for odd,(sid,x,y,door,eve,item) in PAIRS.items() for step in (odd,odd+1)}
def verify_original_eve(path):
 arc=EveArchive(Path(path))
 for odd,(sid,x,y,did,eid,item) in PAIRS.items():
  sc=arc.parse_scene(sid)
  candidates=[z for z in sc["doors"] if
     (z["id"],z["x"],z["y"])==(did,x,y) and eid in z["events"]]
  if len(candidates)!=1:
   raise ValueError(f"Original Qin puzzle Door {sid}/{did} missing")
  if any(eid in n["events"] for n in sc["npcs"]):
   raise ValueError(f"Door Eve unexpectedly bound to an NPC at step{odd}")
  e=sc["events"][eid]
  if e["when_happen"]!=[False,True,False,False]:
   raise ValueError(f"Not original Door Eve at step{odd}")
  # Odd stage is a reveal/puzzle setup at exactly the same Door; it must
  # NOT advance quest or give item, as that would bypass puzzle.
  odd_gates=[c for c in e["conditions"] if
     c["class"]==2 and c["param"]==12138 and c["pstyle"]==1
     and c["ops"]==5 and c["val"]==odd and c["and_num"]==1]
  if len(odd_gates)!=1 or odd_gates[0]["to_result"]!=odd_gates[0]["no"]:
   raise ValueError(f"Original odd puzzle stage gate missing at {odd}")
  odd_res=odd_gates[0]["results"]
  if not any(z["type"]==0 and z["class"]==3 and z["pstyle"]==4 and z["val"]==2000
             for z in odd_res):
   raise ValueError(f"Odd stage {odd} should expose original hidden object")
  if any(z["type"]==0 and z["class"] in (1,2,11,13,15) for z in odd_res):
   raise ValueError(f"Odd stage {odd} unexpectedly grants item or advances quest")
  # Even stage requires original class17? No. Actual original door branch
  # has AND=2: quest step exact + conditionClass7/param2.
  even_gates=[c for c in e["conditions"] if
     c["class"]==2 and c["param"]==12138 and c["pstyle"]==1 and
     c["ops"]==5 and c["val"]==odd+1 and c["and_num"]==2 and
     c["to_result"]==c["no"]]
  even_gates=[c for c in even_gates if
    has_effect(c,0,1,item,1,1) and has_effect(c,0,2,12138,1,1)]
  if len(even_gates)!=1:
   raise ValueError(f"Correct nine-token puzzle reward gate absent at step {odd+1}")
  terminal=even_gates[0]["no"]
  side=[c for c in e["conditions"] if c["class"]==7 and
        c["param"]==2 and c["pstyle"]==2 and c["ops"]==0 and
        c["val"]==0 and c["to_result"]==terminal]
  if len(side)!=1:raise ValueError(f"Missing original side conditionClass7 for stage {odd+1}")
  if any(z["type"]==0 and z["class"] in (11,13,15) for z in even_gates[0]["results"]):
   raise ValueError("Unknown effects on nine-token reward branch")
  print("EVE_PASS",odd,odd+1,"MAP",sid,"DOOR",did,"EVE",eid,"ITEM",item)
 return target_rows()
def patch(path,eve,apply=False):
 p=Path(path)
 expected=verify_original_eve(eve)
 src=p.read_bytes()
 rows={x["step"]:x for x in read_steps(p) if x["mission"]==12138}
 if len(expected)!=18 or len(rows)!=20:
  raise ValueError("Unexpected original 20-stage Qin tomb storyline")
 for step in (19,20):
  if rows[step]["end"]!=(14620,450,350,1,1):
   raise ValueError("Original post-puzzle Fusu ghost NPC route changed")
 for step,t in expected.items():
  row=rows[step]
  if row["end"] not in ((0,0,0,0,0),t):
   raise ValueError(f"Existing Qin stage destination differs at {step}")
  if any(c[0] for c in row["conditions"]):
   raise ValueError(f"Qin stage{step} has an unexpected active item/follower objective")
  pair=step if step%2 else step-1
  if row["description"]!=rows[pair]["description"]:
   raise ValueError(f"Odd/even stage{pair} descriptions differ")
  match=re.search(r"([0-9]+),([0-9]+)[ ]*$",row["description"])
  if not match:raise ValueError(f"No original explicit coordinates in text at {step}")
  textx,texty=map(int,match.groups())
  if abs(textx-t[1])>100 or abs(texty-t[2])>100:
   raise ValueError(f"Original location text and authentic door too far: {step}")
 positions=scan(src)
 result=bytearray(src)
 allowed=set()
 for stage,target in sorted(expected.items()):
  ref=positions[(12138,stage,"END")]
  if tuple(ref[:5]) not in ((0,0,0,0,0),target):
   raise ValueError(f"Packed original END differs at {stage}")
  at=ref[-1]-7
  result[at:at+8]=struct.pack("<HHHBB",*target)
  allowed.update(range(at,at+8))
  print("FIELD",stage,"AT",at,"END",target)
 if result==src:
  print("ALREADY_FIXED",p,sha(src))
  return sha(src)
 if sha(src) not in ORIGINAL_SHAS or len(result)!=len(src):
  raise ValueError("Unknown original MarkStep source checksum, refusing write")
 diffs={i for i,(old,new) in enumerate(zip(src,result)) if old!=new}
 if not diffs or diffs!=allowed:
  raise ValueError(f"Qin puzzle patch exceeded or omitted 18 exact END fields: {len(diffs)}")
 reread=scan(result)
 if any(tuple(reread[(12138,st,"END")][:5])!=t for st,t in expected.items()):
  raise ValueError("Postpatch packed MarkStep decode failed")
 print("PATCH",p,"OLD_SHA",sha(src),"NEW_SHA",sha(result),
       "CHANGED_BYTES",len(diffs),"FIELD_COUNT",len(expected))
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(result)
 backup=p.with_name(p.name+".pre-12138-qin-tomb-doors-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(src)
 if sha(backup.read_bytes())!=sha(src):raise IOError("Backup SHA mismatch")
 tmp=p.with_name(p.name+".12138-qin-tomb-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(result)
  if sha(tmp.read_bytes())!=sha(result):raise IOError("Staged SHA mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(result):raise IOError("Post-swap SHA mismatch")
 print("BACKUP",backup)
 return sha(result)
if __name__=="__main__":
 p=argparse.ArgumentParser()
 p.add_argument("file",type=Path)
 p.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 p.add_argument("--apply",action="store_true")
 args=p.parse_args()
 patch(args.file,args.eve,args.apply)
