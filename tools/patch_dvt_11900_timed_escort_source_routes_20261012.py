#!/usr/bin/env python3
"""Original DVT timed rescue quest11900 source NPC and Door navigation stages5..11.

These coordinates restore wayfinding only. Source escort timer/follower/battle Eve
are NOT modified; quest progression is still contingent on their original gates.
"""
import argparse,hashlib,os,struct
from pathlib import Path
from datetime import datetime
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

# stage: (scene,x,y, navigationKind,placement,EveNo,required original result branchNo)
TARGETS={
 5:(12589,1490,1020,1,17,9,20),
 6:(12589,1340,1000,2,3,10,10),
 7:(12588,440,540,2,3,5,13),
 8:(12582,1280,1220,2,4,8,10),
 9:(12861,1220,1720,2,5,6,10),
 10:(12861,1220,1720,2,5,6,13),
 11:(12011,1140,880,2,15,24,3),
}
INPUT_SHA={
 "b2bb1ae382f5c0e50efe49e8ec8b98ce3a44bbba97e8bed955d94a1fd9749753",
 "4d6a4ea028df1649b77602fe0e9faebca0542032f5186d3b0e9e97e176d0dcc4",
}
def sha(x):return hashlib.sha256(x).hexdigest()
def effect(c,ty,cls,param,ps,v):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==(ty,cls,param,ps,v) for z in c["results"])
def proof_eve(path):
 arc=EveArchive(Path(path))
 for stage,(sid,x,y,kind,pid,eid,no) in TARGETS.items():
  sc=arc.parse_scene(sid)
  owners=(sc["npcs"] if kind==1 else sc["doors"])
  original=[o for o in owners if (o["id"],o["x"],o["y"])==(pid,x,y) and eid in o["events"]]
  if len(original)!=1:raise ValueError(f"Quest11900 stage{stage} missing real original trigger")
  ev=sc["events"][eid]
  when=([True,False,False,False] if kind==1 else [False,True,False,False])
  if ev["when_happen"]!=when:raise ValueError(f"Quest11900 wrong trigger kind at stage{stage}")
  opposite=(sc["doors"] if kind==1 else sc["npcs"])
  if any(eid in o["events"] for o in opposite):raise ValueError(f"Mixed owner of Eve at stage{stage}")
  if kind==1:
   if original[0]["npc_id"]!=10269 or original[0]["close"]:
    raise ValueError("Wounded Zhang Bao must be genuine NPC10269 placement17")
  quest=[c for c in ev["conditions"] if
     c["class"]==2 and c["param"]==11900 and
     c["pstyle"]==1 and c["ops"]==5 and c["val"]==stage]
  if not quest:raise ValueError(f"Missing source stage gate at stage{stage}")
  if stage in (6,7,8,9):
   # Original quest-stage gate only starts a battle. Quest +1 belongs to
   # the conditionClass8 victorious battle outcome, not mere Door click.
   branch=[c for c in ev["conditions"] if c["class"]==8 and c["pstyle"]==1
     and c["no"]==no and effect(c,0,2,11900,1,1)]
  else:
   branch=[c for c in quest if c["no"]==no and effect(c,0,2,11900,1,1)]
  if len(branch)!=1:raise ValueError(f"Original quest +1 source branch missing at stage{stage}")
  if any(z["type"]==0 and z["class"] in (11,13,15) for z in branch[0]["results"]):
   raise ValueError("Unknown source effect present on selected original source branch")
  if stage in (5,11):
   followers=[c for c in ev["conditions"] if c["to_result"]==no and c["class"]==9
         and c["param"]==0 and c["pstyle"]==1 and c["val"]==10269]
   if not followers:raise ValueError(f"Follow NPC guard missing at stage{stage}")
  if stage in (6,7,8,9):
   result=[c for c in ev["conditions"] if c["class"]==8 and c["pstyle"]==1
           and effect(c,0,2,11900,1,1)]
   if not result:raise ValueError(f"Battle-win guarded stage transition missing: {stage}")
   # Door trigger branch is fenced by the original follow-NPC and quest-state logic.
   gated=[c for c in quest if c["and_num"]==3]
   if not gated:raise ValueError(f"Escort Door source stage{stage} not AND3 guarded")
   if not any(c["class"]==9 and c["val"]==10269 for c in ev["conditions"]):
    raise ValueError(f"Escort NPC10269 gate missing in Door{pid}")
  print("EVE_SOURCE_PASS",stage,"SCENE",sid,"KIND",kind,"PLACEMENT",pid,"EVE",eid,"PROGRESS_NO",no)
 return {st:spec[:5] for st,spec in TARGETS.items()}
def patch(file,eve,apply=False):
 path=Path(file)
 expected=proof_eve(eve)
 before=path.read_bytes()
 rows={r["step"]:r for r in read_steps(path) if r["mission"]==11900}
 if len(rows)!=12 or rows[4]["end"]!=(12589,1490,1020,1,17) or rows[12]["end"]!=(12242,130,500,1,7):
  raise ValueError("Original escort mission stage count/adjacent routes changed")
 for st,end in expected.items():
  row=rows[st]
  if row["end"] not in ((0,0,0,0,0),end):
   raise ValueError(f"Unexpected original end stage{st}")
  if row["conditions"][0]!=(1,10269,1,(12589,1440,1000,1,17)):
   raise ValueError(f"Original fallback NPC-follow navigation changed stage{st}")
  if any(c[0] for c in row["conditions"][1:]):
   raise ValueError(f"Unexpected additional quest objective stage{st}")
  if st<=10 and "thời gian" not in row["description"]:
   raise ValueError(f"Timed escort textual objective missing stage{st}")
  if st==11 and "cổng thành Cự Lộc" not in row["description"]:
   raise ValueError("Julu city gate textual objective missing")
 pos=scan(before)
 after=bytearray(before)
 allowed=set()
 for st,end in expected.items():
  rec=pos[(11900,st,"END")]
  if tuple(rec[:5]) not in ((0,0,0,0,0),end):
   raise ValueError("Independent packed MarkStep record disagrees")
  at=rec[-1]-7
  after[at:at+8]=struct.pack("<HHHBB",*end)
  allowed.update(range(at,at+8))
  print("FIELD",st,"OFFSET",at,"END",end)
 if after==before:
  print("ALREADY_FIXED",path,sha(before))
  return sha(before)
 if sha(before) not in INPUT_SHA or len(after)!=len(before):
  raise ValueError("Unrecognized MarkStep source SHA/size")
 diff={i for i,(x,y) in enumerate(zip(before,after)) if x!=y}
 if not diff or not diff.issubset(allowed):
  raise ValueError("Unexpected binary diff outside seven END fields")
 packed_after=scan(after)
 if any(tuple(packed_after[(11900,st,"END")][:5])!=v for st,v in expected.items()):
  raise ValueError("Packed end field roundtrip failed")
 print("PATCH",path,"BEFORE",sha(before),"AFTER",sha(after),"BYTE_DIFF",len(diff))
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(after)
 backup=path.with_name(path.name+".pre-11900-escort-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(before)
 if sha(backup.read_bytes())!=sha(before):raise IOError("Backup SHA mismatch")
 tmp=path.with_name(path.name+".escort-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(after)
  if sha(tmp.read_bytes())!=sha(after):raise IOError("Temp SHA mismatch")
  os.replace(tmp,path)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(path.read_bytes())!=sha(after):raise IOError("Live SHA mismatch")
 print("BACKUP",backup)
 return sha(after)
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 opts=ap.parse_args()
 patch(opts.file,opts.eve,opts.apply)
