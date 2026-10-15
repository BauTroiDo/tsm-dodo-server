#!/usr/bin/env python3
"""Strict source-backed navigation for 11336/1 Naihe NPC and 12158/7 Qin tomb Li Si NPC.

This does NOT change Eve, quest steps, reward effects, or mission inventory.
"""
import argparse, hashlib, os, struct
from datetime import datetime
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan as packed_scan

TARGETS={
 (11336,1):(60801,2950,360,1,1),
 (12158,7):(14661,270,420,1,1),
}
OLD_SHA={
 "e3ba70127cb69cfa9b51e47864015eca288fa91b56d6d5616b98a203cd793f5c",
 "fd257e2defe6a83c3bdd4205e3930892ef3fbbdd179e7194f650ec8425576fe0",
}
DESCRIPTIONS={
 (11336,1):"Thử tìm đường đến cầu Nại Hà",
 (12158,7):"Cùng Lý Tư đi sâu vào trong Lăng Tần",
}
def sha(b): return hashlib.sha256(b).hexdigest()
def effect(c,t,cl,p,ps,v):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==(t,cl,p,ps,v) for z in c["results"])
def verify_source(eve):
 archive=EveArchive(Path(eve))
 s=archive.parse_scene(60801)
 n=[x for x in s["npcs"] if
   (x["id"],x["npc_id"],x["x"],x["y"],x["close"])==(1,27074,2950,360,False)
   and 3 in x["events"]]
 if len(n)!=1 or any(3 in z["events"] for z in s["doors"]):
  raise ValueError("Original Naihe guide NPC/Eve ownership not found")
 e=s["events"][3]
 if e["when_happen"]!=[True,False,False,False]:
  raise ValueError("Naihe quest event not ClickNpc")
 if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==
            (2,11336,2,0,0) and effect(c,0,2,11336,1,1) for c in e["conditions"]):
  raise ValueError("Original Naihe introductory quest activation lost")
 if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==
            (2,11336,1,2,0) for c in e["conditions"]):
  raise ValueError("Original Naihe in-progress condition lost")
 s=archive.parse_scene(14661)
 n=[x for x in s["npcs"] if
   (x["id"],x["npc_id"],x["x"],x["y"],x["close"])==(1,40107,270,420,False)
   and 3 in x["events"]]
 if len(n)!=1 or any(3 in z["events"] for z in s["doors"]):
  raise ValueError("Original Li Si NPC/Eve ownership not found")
 e=s["events"][3]
 if e["when_happen"]!=[True,False,False,False]:
  raise ValueError("Qin tomb quest event not ClickNpc")
 if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==
            (2,12158,1,5,7) for c in e["conditions"]):
  raise ValueError("Original stage7 Li Si interaction not found")
 if not any(c["class"]==8 and c["param"]==9 and c["pstyle"]==1
     and effect(c,0,2,12158,3,0) and effect(c,0,2,12159,1,1)
     for c in e["conditions"]):
  raise ValueError("Original Li Si battle victory stage7 completion/followup missing")
 print("SOURCE_PASS", "Naihe60801/NPC27074/Eve3", "QinTomb14661/NPC40107/Eve3")
 return TARGETS
def patch(file,eve,apply=False):
 p=Path(file)
 targets=verify_source(eve)
 old=p.read_bytes()
 rows={(z["mission"],z["step"]):z for z in read_steps(p)}
 if [st for (q,st) in rows if q==11336]!=[1,2] or [st for (q,st) in rows if q==12158]!=[1,2,3,4,5,6,7]:
  raise ValueError("Original affected MarkStep mission layout changed")
 if rows[(11336,2)]["end"]!=(60806,1040,380,1,1):
  raise ValueError("Original Naihe step2 return NPC route was modified")
 if rows[(12158,6)]["end"]!=(14649,510,270,1,1):
  raise ValueError("Original Qin tomb stage6 Li Si collection-turnin target modified")
 for key,dest in targets.items():
  row=rows[key]
  if row["description"]!=DESCRIPTIONS[key] or row["end"] not in ((0,0,0,0,0),dest):
   raise ValueError(f"Unknown MarkStep clue/preexisting END {key}")
  if any(c[0]!=0 for c in row["conditions"]):
   raise ValueError(f"Unexpected new objective on source quest {key}")
 refs=packed_scan(old)
 new=bytearray(old)
 allowed=set()
 for key,dest in sorted(targets.items()):
  q,st=key
  record=refs[(q,st,"END")]
  if tuple(record[:5]) not in ((0,0,0,0,0),dest):
   raise ValueError(f"Original packed END changed {key}")
  start=record[-1]-7
  new[start:start+8]=struct.pack("<HHHBB",*dest)
  allowed.update(range(start,start+8))
  print("FIELD",key,"OFFSET",start,"DEST",dest)
 if new==old:
  print("ALREADY_FIXED",p,sha(old));return sha(old)
 if sha(old) not in OLD_SHA or len(new)!=len(old):
  raise ValueError("Unknown full binary predecessor SHA/version/size")
 diff={i for i,(a,b) in enumerate(zip(old,new)) if a!=b}
 if not diff or not diff.issubset(allowed) or any(not diff.intersection(
       range(refs[(q,st,"END")][-1]-7,refs[(q,st,"END")][-1]+1))
       for q,st in targets):
  raise ValueError("Unexpected modified bytes outside exactly two 8-byte END slots")
 decoded=packed_scan(new)
 if any(tuple(decoded[(q,st,"END")][:5])!=dest for (q,st),dest in targets.items()):
  raise ValueError("Independent packed END post-edit decode mismatch")
 print("PATCH",p,"OLD_SHA",sha(old),"NEW_SHA",sha(new),"DIFF_BYTES",len(diff))
 if not apply:
  print("DRY_RUN_ONLY");return sha(new)
 backup=p.with_name(p.name+".pre-naihe-lisi-source-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(old)
 if sha(backup.read_bytes())!=sha(old):raise IOError("Backup full SHA failed")
 temp=p.with_name(p.name+".11336-12158-temp")
 if temp.exists():raise FileExistsError(temp)
 try:
  temp.write_bytes(new)
  if sha(temp.read_bytes())!=sha(new):raise IOError("Temp full SHA mismatch")
  os.replace(temp,p)
 finally:
  if temp.exists():temp.unlink()
 if sha(p.read_bytes())!=sha(new):raise IOError("Postswap hash wrong")
 print("BACKUP",backup)
 return sha(new)
if __name__=="__main__":
 parser=argparse.ArgumentParser()
 parser.add_argument("file",type=Path)
 parser.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 parser.add_argument("--apply",action="store_true")
 args=parser.parse_args()
 patch(args.file,args.eve,args.apply)
