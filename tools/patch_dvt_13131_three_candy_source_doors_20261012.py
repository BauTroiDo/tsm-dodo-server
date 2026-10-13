#!/usr/bin/env python3
"""DVT quest13131 three source-verified nonadaptive trick-or-treat Door guides.

The three Doors are optional original choices, never a forced route; if player
uses an alternative Door first, they should choose a different unused door.
"""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan

QUEST=13131
SCENE=54922
CHOICES={2:(7,540,140,31),3:(3,740,220,32),4:(4,980,260,33)}
SOURCE_SHA={
 "5d89bbc211ae6c1cbc9cf55e846cbb13634adc8b25cd081897ba7d11a28896f0",
 "4e85f8647afd5a76345dfef771153b72b7197a36fb2d156100246d4636d88851",
}
def sha(b):return hashlib.sha256(b).hexdigest()
def effect(cond,typ,cl,param,ps,v):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==
  (typ,cl,param,ps,v) for z in cond["results"])
def source_proof(eve):
 sc=EveArchive(Path(eve)).parse_scene(SCENE)
 girl=[z for z in sc["npcs"] if (z["id"],z["npc_id"],z["x"],z["y"],z["close"])==
   (27,15062,390,480,False) and 19 in z["events"]]
 if len(girl)!=1:raise ValueError("Original source Su Xiao Mei interaction missing")
 # Original stage1 MarkStep already points to girl; do not alter.
 valid={}
 for st,(doorid,x,y,eid) in CHOICES.items():
  door=[z for z in sc["doors"] if
      (z["id"],z["x"],z["y"])==(doorid,x,y) and eid in z["events"]]
  if len(door)!=1 or any(eid in n["events"] for n in sc["npcs"]):
   raise ValueError(f"No authentic source candy Door owner {st}")
  evt=sc["events"][eid]
  if evt["when_happen"]!=[False,True,False,False]:
   raise ValueError("Source candy event must be Door entry trigger")
  cs=evt["conditions"]
  if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["and_num"])
     ==(2,QUEST,1,2,1,5) for c in cs):
   raise ValueError("Original source candy in-progress gate absent")
  # Source Door can cause quest+1 after an eligible interaction; stage4 can
  # also clear quest / start 13132 / 12499 and award item29433. No bypass.
  mid=[c for c in cs if (c["class"],c["param"],c["pstyle"],c["ops"],c["val"])
      ==(2,QUEST,1,2,1) and effect(c,0,2,QUEST,1,1)]
  if len(mid)!=1:
   raise ValueError("Candy door normal source +1 outcome absent")
  end=[c for c in cs if (c["class"],c["param"],c["pstyle"],c["ops"],c["val"])
      ==(2,QUEST,1,5,4) and
       effect(c,0,2,QUEST,3,0) and
       effect(c,0,2,13132,1,1) and
       effect(c,0,2,12499,1,1) and
       effect(c,0,1,29433,1,1)]
  if len(end)!=1:
   raise ValueError("Source stage4 candy final and reward lost")
  # Eligibility depends on 4 *source original* conditions class7, NOT merely arrival.
  gates=[c for c in cs if c["class"]==7 and c["param"]==0 and c["pstyle"]==3
     and c["ops"]==6 and c["val"] in (38030,38029,38028,32003)]
  if len(gates)!=4:
   raise ValueError("Original four Halloween prerequisites changed")
  valid[st]=(SCENE,x,y,2,doorid)
  print("EVE_PASS",st,"DOOR",doorid,"EVE",eid,"X",x,"Y",y)
 if len(set(x[-1] for x in valid.values()))!=3:
  raise ValueError("No distinct door guide choices")
 return valid
def patch(file,eve,apply=False):
 p=Path(file);valid=source_proof(eve)
 old=p.read_bytes()
 rows={z["step"]:z for z in read_steps(p) if z["mission"]==QUEST}
 if sorted(rows)!=[1,2,3,4]:
  raise ValueError("Original quest13131 4-step MarkStep schema changed")
 if rows[1]["end"]!=(54922,390,480,1,27):
  raise ValueError("Original source girl stage1 navigation changed")
 for st,dest in valid.items():
  row=rows[st]
  if row["end"] not in ((0,0,0,0,0),dest) or any(c[0] for c in row["conditions"]):
   raise ValueError(f"Original MarkStep new condition/unexpected END at {st}")
  if row["description"]!="帶著蘇小妹去要糖果吧！" and "kẹo" not in row["description"].lower():
   raise ValueError(f"Unexpected original trick-or-treat stage text {st}")
 refs=scan(old)
 out=bytearray(old);allowed=set()
 for st,dest in sorted(valid.items()):
  rec=refs[(QUEST,st,"END")]
  if tuple(rec[:5]) not in ((0,0,0,0,0),dest):
   raise ValueError("Packed END source mismatch")
  start=rec[-1]-7
  out[start:start+8]=struct.pack("<HHHBB",*dest)
  allowed.update(range(start,start+8))
  print("FIELD",QUEST,st,"OFFSET",start,"TARGET",dest)
 if out==old:
  print("ALREADY_FIXED",p,sha(old));return sha(old)
 if sha(old) not in SOURCE_SHA or len(out)!=len(old):
  raise ValueError("Unknown source or staging binary SHA/version")
 diff={i for i,(a,b) in enumerate(zip(old,out)) if a!=b}
 if not diff or not diff.issubset(allowed) or any(
  not diff.intersection(range(refs[(QUEST,st,"END")][-1]-7,
     refs[(QUEST,st,"END")][-1]+1)) for st in valid):
  raise ValueError("Bytes altered outside 3 source END fields")
 after=scan(out)
 if any(tuple(after[(QUEST,st,"END")][:5])!=dest for st,dest in valid.items()):
  raise ValueError("Packed field post patch readback not correct")
 print("PATCH",p,"OLD",sha(old),"NEW",sha(out),"DIFF_BYTES",len(diff))
 if not apply:
  print("DRY_RUN_ONLY");return sha(out)
 backup=p.with_name(p.name+".pre-13131-source-candy-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(old)
 if sha(backup.read_bytes())!=sha(old):raise IOError("Backup SHA mismatch")
 temp=p.with_name(p.name+".13131-candy-tmp")
 if temp.exists():raise FileExistsError(temp)
 try:
  temp.write_bytes(out)
  if sha(temp.read_bytes())!=sha(out):raise IOError("Temp SHA mismatch")
  os.replace(temp,p)
 finally:
  if temp.exists():temp.unlink()
 if sha(p.read_bytes())!=sha(out):raise IOError("Postwrite SHA mismatch")
 print("BACKUP",backup)
 return sha(out)
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 o=ap.parse_args()
 patch(o.file,o.eve,o.apply)
