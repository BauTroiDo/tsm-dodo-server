#!/usr/bin/env python3
"""16 source-authentic fixed chest choices for four DVT 3-chest quest arenas.

Each target is a REAL original NPC20001 Eve quest interaction, not dynamic
next-unopened-chest detection. Six alternatives remain for each room.
"""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan

ARENAS={
 12330:(62003,((2,3090,2800,5),(3,290,2300,6),(4,290,760,7),(5,3090,1500,8))),
 12346:(62004,((1,310,2300,6),(2,1410,1460,7),(3,2850,1660,8),(4,3630,2800,9))),
 12350:(62005,((1,2810,180,7),(2,290,200,8),(3,1950,1460,9),(4,1130,2740,10))),
 12354:(62006,((1,330,160,12),(2,1270,2980,13),(3,2250,2920,14),(4,2830,220,2))),
}
OLD_SHAS={
 "7306310a6d8599f847c1c25d8db57356a9a15bf1d5e8c9e5a4dcd4b03e312f4c",
 "9812fb7c733af74629f5d6eb1f385e5519139844ff32a5b3e2f72e1dba4f9ded",
}
def sha(b):return hashlib.sha256(b).hexdigest()
def effect(cond,typ,cl,param,style,val):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==
   (typ,cl,param,style,val) for z in cond["results"])

def source_proof(eve):
 a=EveArchive(Path(eve))
 out={}
 for q,(sid,proposed) in ARENAS.items():
  scene=a.parse_scene(sid)
  door=[z for z in scene["doors"] if z["id"]==1 and 1 in z["events"]]
  if len(door)!=1 or scene["events"][1]["when_happen"]!=[False,True,False,False]:
   raise ValueError(f"Original stage1 arena entrance Door1 missing {q}")
  boxes=[z for z in scene["npcs"] if z["npc_id"]==20001 and z["events"] and not z["close"]]
  if len(boxes)!=6:raise ValueError(f"Expected six independent source NPC20001 chests {q}")
  if len(proposed)!=4 or len(set(e[0] for e in proposed))!=4:
   raise ValueError(f"Repeated chosen chest NPC {q}")
  for stage,(nid,x,y,eid) in enumerate(proposed,2):
   npc=[z for z in boxes if z["id"]==nid and (z["x"],z["y"],z["events"])==(x,y,[eid])]
   if len(npc)!=1:raise ValueError(f"Source real chest placement/owner changed {q}/{stage}")
   ev=scene["events"][eid]
   if ev["when_happen"]!=[True,False,False,False]:
    raise ValueError(f"Expected real ClickNpc chest Eve {q}/{stage}")
   conds=[c for c in ev["conditions"] if c["class"]==2 and c["param"]==q and c["pstyle"]==1]
   if len(conds)!=4 or sorted((c["ops"],c["val"]) for c in conds)!=[(2,1),(5,3),(5,4),(5,5)]:
    raise ValueError(f"Expected four authentic chest open/complete quest gates {q}/{stage}")
   open_cond=next(c for c in conds if c["ops"]==2 and c["val"]==1)
   if not effect(open_cond,0,2,q,1,1) or not effect(open_cond,0,3,nid,3,2000):
    raise ValueError(f"Original chest+1 and single-chest visibility effects changed {q}/{stage}")
   finish=next(c for c in conds if c["ops"]==5 and c["val"]==5)
   if not (effect(finish,0,2,q,3,0) and effect(finish,0,2,q+1,1,1)
           and effect(finish,0,2,12321,1,1)):
    raise ValueError(f"Original chest final quest complete+next missions missing {q}/{stage}")
   out[(q,stage)]=(sid,x,y,1,nid)
  print("EVE_SOURCE_PASS",q,"SCENE",sid,"SIX_EQUIVALENT_CHESTS","FOUR_DISTINCT_SUGGESTED")
 if len(out)!=16:raise ValueError("Expected 16 source chest destinations")
 return out

def patch(path,eve,apply=False):
 p=Path(path)
 targets=source_proof(eve)
 raw=p.read_bytes()
 rows={(r["mission"],r["step"]):r for r in read_steps(p)}
 for q,(sid,_) in ARENAS.items():
  if sorted(st for mid,st in rows if mid==q)!=[1,2,3,4,5]:
   raise ValueError(f"Original 5-stage chest quest layout changed {q}")
  start=rows[(q,1)]["end"]
  if start[0]!=sid or start[3:]!=(2,1):
   raise ValueError(f"Prior original Door1 entrance route missing {q}")
 for key,dest in targets.items():
  row=rows[key]
  if row["end"] not in ((0,0,0,0,0),dest) or any(c[0] for c in row["conditions"]):
   raise ValueError(f"Preexisting nonzero chest MarkStep or objectives {key}")
  if "rương" not in row["description"]:
   raise ValueError(f"Stage has no chest quest clue {key}")
 refs=scan(raw)
 edited=bytearray(raw)
 allowed=set()
 for key,dest in sorted(targets.items()):
  record=refs[(*key,"END")]
  if tuple(record[:5]) not in ((0,0,0,0,0),dest):
   raise ValueError(f"Packed END record isn't original value {key}")
  at=record[-1]-7
  edited[at:at+8]=struct.pack("<HHHBB",*dest)
  allowed.update(range(at,at+8))
  print("FIELD",key,"OFFSET",at,"END",dest)
 if edited==raw:
  print("ALREADY_FIXED",p,sha(raw));return sha(raw)
 if sha(raw) not in OLD_SHAS or len(edited)!=len(raw):
  raise ValueError("Unexpected source/client predecessor SHA/length")
 diff={i for i,(x,y) in enumerate(zip(raw,edited)) if x!=y}
 if not diff or not diff.issubset(allowed) or any(
  not diff.intersection(range(refs[(*key,"END")][-1]-7,refs[(*key,"END")][-1]+1))
  for key in targets):
  raise ValueError("Edited unrelated MarkStep bytes or skipped source END")
 verify=scan(edited)
 if any(tuple(verify[(*key,"END")][:5])!=d for key,d in targets.items()):
  raise ValueError("Packed independent readback mismatch")
 print("PATCH",p,"OLD",sha(raw),"NEW",sha(edited),"DIFF_BYTES",len(diff),"FIELDS",len(targets))
 if not apply:
  print("DRY_RUN_ONLY");return sha(edited)
 backup=p.with_name(p.name+".pre-123xx-four-chests-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Backup full SHA invalid")
 temp=p.with_name(p.name+".123xx-chests-tmp")
 if temp.exists():raise FileExistsError(temp)
 try:
  temp.write_bytes(edited)
  if sha(temp.read_bytes())!=sha(edited):raise IOError("Temporary data SHA wrong")
  os.replace(temp,p)
 finally:
  if temp.exists():temp.unlink()
 if sha(p.read_bytes())!=sha(edited):raise IOError("Post-replace SHA mismatch")
 print("BACKUP",backup)
 return sha(edited)
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 args=ap.parse_args()
 patch(args.file,args.eve,args.apply)
