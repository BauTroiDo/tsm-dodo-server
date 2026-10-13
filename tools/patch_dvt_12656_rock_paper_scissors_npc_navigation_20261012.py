#!/usr/bin/env python3
"""DVT 12656 golden-pig 12 stage original NPC38093 routes, preserve game/reward mechanics."""
from pathlib import Path
from datetime import datetime
import argparse,hashlib,os,struct
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan
QUEST=12656
DEST=(49960,610,360,1,1)
SOURCE_SHA={
 "55f09d04eca91d488ab85113a33550e207db0943e5784093ae1b42dffa2ab867",
 "24d936d4f95368888425d2b81b4d4fb33f7d2c2794379743b4c924c20484161c",
}
def sha(data):return hashlib.sha256(data).hexdigest()
def effect(c,ty,cl,p,ps,v):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==
            (ty,cl,p,ps,v) for z in c["results"])
def proof(eve):
 scene=EveArchive(Path(eve)).parse_scene(49960)
 actual=[n for n in scene["npcs"] if
   (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(1,38093,610,360,True)
   and n["events"]==[4,5]]
 if len(actual)!=1:raise ValueError("Original 49960 pig NPC38093 position or Eve owners changed")
 if any(4 in d["events"] or 5 in d["events"] for d in scene["doors"]):
  raise ValueError("Original NPC Eve unexpectedly Door-owned")
 finish,start=scene["events"][4],scene["events"][5]
 if finish["when_happen"]!=[True,False,False,False] or start["when_happen"]!=[True,False,False,False]:
  raise ValueError("Source pig interaction not original ClickNpc Eve")
 # Stage1 initiates the minigame, guarded by quest absent/available condition.
 begins=[c for c in start["conditions"] if
  (c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["and_num"])
   ==(2,QUEST,2,0,0,3) and effect(c,0,2,QUEST,1,1)]
 if len(begins)!=1:raise ValueError("Stage1 minigame initiation source missing")
 # End-game is a separate stage12 branch requiring the ORIGINAL class7 check;
 # source reward 10 item29471 and mission completion are never patched.
 ends=[c for c in finish["conditions"] if
   (c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["and_num"])
    ==(2,QUEST,1,5,12,2) and effect(c,0,1,29471,1,10)
    and effect(c,0,2,QUEST,3,0)]
 if len(ends)!=1:raise ValueError("Stage12 original reward/quest completion branch missing")
 last=ends[0]
 if not any((x["class"],x["param"],x["pstyle"],x["ops"],x["val"],x["to_result"])
            ==(7,2,2,0,0,last["no"]) for x in finish["conditions"]):
  raise ValueError("Stage12 source conditionClass7 required, cannot bypass")
 # Stages2..11 have NO unique stage-specific Eve target. Source NPC handles
 # intermediate steps via range gates >1 and <12, scripted result callback.
 ranges=[c for c in finish["conditions"] if
    (c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["and_num"])
     ==(2,QUEST,1,2,1,3)]
 if len(ranges)!=1:raise ValueError("Original pig mid-round range gate missing")
 range_c=ranges[0]
 if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["to_result"])
     ==(2,QUEST,1,1,12,range_c["no"]) for c in finish["conditions"]):
  raise ValueError("Upper bound for pig mid-round stage missing")
 # The item/class7-qualified *other* range branch opens the original menu
 # (resultType6/class3), rather than the first general NPC dialogue branch.
 menus=[c for c in finish["conditions"] if
   (c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["and_num"])
    ==(2,QUEST,1,2,1,4) and
   any(z["type"]==6 and z["class"]==3 for z in c["results"])]
 if len(menus)!=1 or not any(
   (c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["to_result"])==
     (2,QUEST,1,1,12,menus[0]["no"]) for c in finish["conditions"]):
  raise ValueError("Original pig minigame choice/range branch missing")
 if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==
     (10,2,30,0,0) and effect(c,0,2,QUEST,1,1)
     for c in finish["conditions"]):
  raise ValueError("Original pig callback-driven round progression absent")
 print("EVE_PROOF_PASS QUEST12656 NPC38093 scene49960 placement1 source Eves4/5")
 print("ROUND_TYPES stage1 initialization, stage2-11 midround range, stage12 class7-gated completion")
 return {n:DEST for n in range(1,13)}
def patch(path,eve,apply=False):
 p=Path(path)
 targets=proof(eve)
 before=p.read_bytes()
 rows={r["step"]:r for r in read_steps(p) if r["mission"]==QUEST}
 if sorted(rows)!=list(range(1,13)):
  raise ValueError("Original quest12656 12 MarkStep rows not found")
 for st,entry in rows.items():
  if entry["end"] not in ((0,0,0,0,0),DEST):raise ValueError(f"Stage{st} has unknown END")
  if any(c[0] for c in entry["conditions"]):raise ValueError(f"Stage{st} acquired active objective")
  if st==1 and "Kim Trư" not in entry["description"]:raise ValueError("Stage1 missing original pig clue")
  if st==12 and "Kim Trư" not in entry["description"]:raise ValueError("Stage12 missing original pig clue")
  if st in range(2,12) and "oẳn tù tì" not in entry["description"]:
   raise ValueError(f"Stage{st} is no longer pig rock-paper-scissors")
 refs=scan(before)
 out=bytearray(before)
 allowed=set()
 for st,expected in targets.items():
  rec=refs[(QUEST,st,"END")]
  if tuple(rec[:5]) not in ((0,0,0,0,0),DEST):
   raise ValueError(f"Original packed END changed stage{st}")
  at=rec[-1]-7
  out[at:at+8]=struct.pack("<HHHBB",*expected)
  allowed.update(range(at,at+8))
  print("FIELD",st,"OFFSET",at,"END",expected)
 if out==before:
  print("ALREADY_FIXED",p,sha(before));return sha(before)
 if sha(before) not in SOURCE_SHA or len(out)!=len(before):
  raise ValueError("Unexpected source/client MarkStep version SHA")
 changed={i for i,(a,b) in enumerate(zip(before,out)) if a!=b}
 if not changed or not changed.issubset(allowed):
  raise ValueError("Bytes changed outside exactly 12 quest12656 END fields")
 if any(not changed.intersection(range(refs[(QUEST,st,"END")][-1]-7,
     refs[(QUEST,st,"END")][-1]+1)) for st in targets):
  raise ValueError("Expected every original empty END to change")
 readback=scan(out)
 if any(tuple(readback[(QUEST,st,"END")][:5])!=DEST for st in targets):
  raise ValueError("Independent packed END round-trip failure")
 print("PATCH",p,"OLD_SHA",sha(before),"NEW_SHA",sha(out),"DIFF_BYTES",len(changed))
 if not apply:
  print("DRY_RUN_ONLY");return sha(out)
 backup=p.with_name(p.name+".pre-12656-pig-minigame-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(before)
 if sha(backup.read_bytes())!=sha(before):raise IOError("Backup SHA mismatch")
 tmp=p.with_name(p.name+".pig12656-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(out)
  if sha(tmp.read_bytes())!=sha(out):raise IOError("Temp SHA mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(out):raise IOError("Post-write SHA mismatch")
 print("BACKUP",backup)
 return sha(out)
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 a=ap.parse_args()
 patch(a.file,a.eve,a.apply)
