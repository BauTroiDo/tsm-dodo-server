#!/usr/bin/env python3
"""30 genuine DVT area-guidance ENDs; NOT stage completion or free items.

Santa: 14 daily *help* stages navigate to original NPC38127 in scene49945.
Grandpa: 16 item-search stages navigate to original scene49955 NPC38317
as a room landmark. Two real activity Doors remain separate alternatives.
Four daily tree-reward stages are explicitly NOT changed.
"""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan

SANTA_STAGES=(1,2,4,5,6,8,9,10,11,13,14,15,16,17)
REWARD_STAGES=(3,7,12,18)
GRANDPA_STAGES=tuple(range(1,17))
DESTS={(12511,k):(49945,270,420,1,1) for k in SANTA_STAGES}
DESTS.update({(12634,k):(49955,410,370,1,1) for k in GRANDPA_STAGES})
INPUT_SHA={
 "91684c0a287e24b31a5e251322e19a69e96f6961e28d4c90bbadcc40055162f0",
 "3b918753986b8688b24bf3931d2ace6d7051fbf99f2bb5c99f24f83afc224f0b",
}
def sha(x):return hashlib.sha256(x).hexdigest()
def has(c,ty,cl,p,ps,value):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==
   (ty,cl,p,ps,value) for z in c["results"])
def source_proof(eve_path):
 arc=EveArchive(Path(eve_path))
 santa=arc.parse_scene(49945)
 owners=[n for n in santa["npcs"] if
    (n["id"],n["npc_id"],n["x"],n["y"],n["close"],n["events"])==
    (1,38127,290,420,False,[1,5,6,7,8])]
 if len(owners)!=1:raise ValueError("Original Santa NPC ownership changed")
 if any(e["when_happen"]!=[True,False,False,False] for eid,e in santa["events"].items() if eid in owners[0]["events"]):
  raise ValueError("Expected original Santa direct ClickNpc event")
 if not any(c["class"]==2 and c["param"]==12511 and
        c["pstyle"]==2 and c["ops"]==0 for c in santa["events"][1]["conditions"]):
  raise ValueError("Original Santa daily gate missing")
 if not any(has(c,0,2,12511,1,1) for eid in (5,6,7,8)
     for c in santa["events"][eid]["conditions"]):
  raise ValueError("Santa original multi-day quest progression source absent")
 grandfather=arc.parse_scene(49955)
 grand=[n for n in grandfather["npcs"] if
   (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(1,38317,390,320,False)
   and 11 in n["events"]]
 if len(grand)!=1:raise ValueError("Original grandpa house landmark NPC missing")
 last=grandfather["events"][11]
 if last["when_happen"]!=[True,False,False,False]:
  raise ValueError("Grandpa completion event is not ClickNpc")
 if not any(c["class"]==2 and c["param"]==12634 and
     c["pstyle"]==1 and c["ops"]==5 and c["val"]==17 and
     has(c,0,2,12634,3,0) for c in last["conditions"]):
  raise ValueError("Source grandpa final 17-step turn-in changed")
 # Two genuine, *alternative* activity Doors in the SAME room; neither is
 # asserted to be an always-available static target of the 16 stages.
 for did,evt_no in ((2,14),(3,15)):
  matched=[door for door in grandfather["doors"] if door["id"]==did and evt_no in door["events"]]
  if len(matched)!=1:raise ValueError("Missing one of both authentic activity doors")
  eve=grandfather["events"][evt_no]
  if eve["when_happen"]!=[False,True,False,False]:
   raise ValueError("Activity Door event is not original Door trigger")
  good=[c for c in eve["conditions"] if
    c["class"]==2 and c["param"]==12634 and
    c["pstyle"]==1 and c["ops"]==2 and c["val"]==0 and
    has(c,0,1,29472,1,-1) and has(c,0,2,12634,1,1)]
  if len(good)!=1:raise ValueError("Original grandfather dough item+progress alternatives missing")
 if len(DESTS)!=30:raise ValueError("Unexpected aggregate patch size")
 print("SOURCE_PROOF_PASS Santa14 help-stage NPC38127, Grandpa16 house-landmark NPC38317")
 print("PRESERVE Santa four next-day tree reward stages, both 12634 item29472 alternate source Doors")
 return DESTS
def patch(file,eve,apply=False):
 p=Path(file)
 targets=source_proof(eve)
 original=p.read_bytes()
 records=read_steps(p)
 rows={(row["mission"],row["step"]):row for row in records}
 santa=[row for row in records if row["mission"]==12511]
 grand=[row for row in records if row["mission"]==12634]
 if [r["step"] for r in santa]!=list(range(1,19)) or [r["step"] for r in grand]!=list(range(1,18)):
  raise ValueError("Original 18-day and 17-stage source mission layout changed")
 if grand[-1]["end"]!=(49955,410,370,1,1):
  raise ValueError("Grandpa stage17 source existing end changed")
 for st in REWARD_STAGES:
  row=rows[(12511,st)]
  # The later source-verified tree-navigation patch fills scene and XY,
  # but does not alter these reward stages' original event kind/id or Eve results.
  if row["end"] not in ((0,0,0,1,1),(49944,850,700,1,1)) or "cách ngày" not in row["description"]:
   raise ValueError("Daily tree reward stage must retain original event kind and source")
 for key,dest in targets.items():
  row=rows[key]
  if row["end"] not in ((0,0,0,0,0),dest) or any(c[0] for c in row["conditions"]):
   raise ValueError(f"Preexisting quest MarkStep obligations changed {key}")
  description=row["description"]
  if key[0]==12511 and ("ông già Noel" not in description or "cách ngày" in description):
   raise ValueError(f"Santa daily helper stage text doesn't match {key}")
  if key[0]==12634 and "nhà Chu gia gia" not in description:
   raise ValueError(f"Original grandfather house stage location clue missing {key}")
 offsets=scan(original)
 new=bytearray(original);allowed=set()
 for q,st in sorted(targets):
  val=targets[(q,st)]
  ref=offsets[(q,st,"END")]
  if tuple(ref[:5]) not in ((0,0,0,0,0),val):
   raise ValueError(f"Packed source END unexpectedly nonzero {(q,st)}")
  offset=ref[-1]-7
  new[offset:offset+8]=struct.pack("<HHHBB",*val)
  allowed.update(range(offset,offset+8))
  print("FIELD",q,st,offset,val)
 if new==original:
  print("ALREADY_FIXED",p,sha(original));return sha(original)
 if len(original)!=len(new) or sha(original) not in INPUT_SHA:
  raise ValueError("Unrecognized original full SHA or binary length")
 delta={i for i,(a,b) in enumerate(zip(original,new)) if a!=b}
 if not delta or not delta.issubset(allowed) or any(
   not delta.intersection(range(offsets[(q,st,"END")][-1]-7,offsets[(q,st,"END")][-1]+1))
   for q,st in targets):
  raise ValueError("Data changed outside 30 authorized END fields")
 verify=scan(new)
 if any(tuple(verify[(q,st,"END")][:5])!=dest for (q,st),dest in targets.items()):
  raise ValueError("Packed MarkStep END roundtrip mismatch")
 print("BATCH_PATCH",p,"SHA_OLD",sha(original),"SHA_NEW",sha(new),"DIFF_BYTES",len(delta),"FIELDS",len(targets))
 if not apply:
  print("DRY_RUN_ONLY");return sha(new)
 backup=p.with_name(p.name+".pre-12511-12634-batch-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(original)
 if sha(backup.read_bytes())!=sha(original):raise IOError("Backup SHA mismatch")
 tmp=p.with_name(p.name+".12511-12634-batch-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(new)
  if sha(tmp.read_bytes())!=sha(new):raise IOError("Staged SHA mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(new):raise IOError("Post-write SHA mismatch")
 print("BACKUP",backup)
 return sha(new)
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 args=ap.parse_args()
 patch(args.file,args.eve,args.apply)
