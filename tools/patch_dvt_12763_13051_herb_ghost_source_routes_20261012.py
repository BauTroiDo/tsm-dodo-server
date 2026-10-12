#!/usr/bin/env python3
"""Restore DVT source Door herb-delivery routes and two cursed-village ghost NPC routes.

No item grant, non-linear stage, map NPC visibility or Eve effects altered.
"""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

# mission12763: stage -> (scene,doorId,x,y,eveNo,questResultDelta)
HERB={
 2:(12179,2,540,440,3,8),
 3:(12102,2,520,440,3,7),
 4:(12003,10,640,240,45,6),
 5:(12004,2,520,480,7,5),
 6:(12015,2,420,440,7,4),
 7:(12001,16,1700,360,100,3),
 8:(12005,4,900,560,11,2),
}
# mission13051: stage -> (scene,placement,npcId,x,y,eveNo,progressConditionNo)
GHOST={
 1:(12137,7,38428,570,340,7,7),
 2:(49991,5,38427,690,480,10,1),
}
OLD_SHAS={
 "3128277213fd51bbf531a5b5b09f1558ab0265b638ddef680bc1211d00ce3788",
 "322be78108db3fd93463a21953164164176fa4cdeed958200462b4c9077c38fe",
}
def sha(b):return hashlib.sha256(b).hexdigest()
def has(c,ty,cl,p,ps,v):
 return any((z["type"],z["class"],z["param"],z["pstyle"],z["val"])==(ty,cl,p,ps,v)
    for z in c["results"])
def source_proof(path):
 arc=EveArchive(Path(path))
 targets={}
 for st,(sid,did,x,y,eid,delta) in HERB.items():
  sc=arc.parse_scene(sid)
  doors=[z for z in sc["doors"] if (z["id"],z["x"],z["y"])==(did,x,y) and eid in z["events"]]
  if len(doors)!=1:raise ValueError(f"No source Door owner quest12763 step{st}")
  if any(eid in z["events"] for z in sc["npcs"]):
   raise ValueError(f"Source Door Eve shared with NPC quest12763 step{st}")
  ev=sc["events"][eid]
  if ev["when_happen"]!=[False,True,False,False]:
   raise ValueError(f"Invalid Door Eve kind quest12763 step{st}")
  # A real two-way branch: no herbal item shows a message, carrying
  # original item29513 consumes exactly one and advances by NON-LINEAR delta.
  gates=[c for c in ev["conditions"] if
   (c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["and_num"])
    ==(2,12763,1,5,st,2)]
  if len(gates)!=2:raise ValueError(f"Lost original two herb branches step{st}")
  rewarding=[c for c in gates if has(c,0,1,29513,1,-1) and
             has(c,0,2,12763,1,delta)]
  if len(rewarding)!=1 or rewarding[0]["no"]!=4:
   raise ValueError(f"Herb branch stage{st} has unexpected original non-linear delta")
  if any(z["type"]==0 and z["class"] in (11,13,15) for z in rewarding[0]["results"]):
   raise ValueError("Unknown reward effect found, no automatic routing change")
  if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["to_result"])
             ==(1,29513,1,2,0,4) for c in ev["conditions"]):
   raise ValueError(f"Original source herbal inventory >0 requirement absent step{st}")
  if not any((c["class"],c["param"],c["pstyle"],c["ops"],c["val"],c["to_result"])
             ==(1,29513,1,5,0,2) for c in ev["conditions"]):
   raise ValueError(f"Original source no-item alternate branch absent step{st}")
  targets[(12763,st)]=(sid,x,y,2,did)
  print("HERB_PASS",st,"SCENE",sid,"DOOR",did,"EVE",eid,"NONLINEAR_PLUS",delta)
 for st,(sid,nid,npcid,x,y,eid,no) in GHOST.items():
  sc=arc.parse_scene(sid)
  npc=[z for z in sc["npcs"] if (z["id"],z["npc_id"],z["x"],z["y"])
    ==(nid,npcid,x,y) and eid in z["events"]]
  if len(npc)!=1 or not npc[0]["close"]:
   raise ValueError(f"Missing source hidden quest13051 NPC stage{st}")
  if any(eid in z["events"] for z in sc["doors"]):
   raise ValueError(f"Ghost Eve should be ClickNpc only at stage{st}")
  ev=sc["events"][eid]
  if ev["when_happen"]!=[True,False,False,False]:
   raise ValueError(f"Original ghost NPC event trigger changed {st}")
  branches=[c for c in ev["conditions"] if
     (c["no"],c["class"],c["param"],c["pstyle"],c["ops"],c["val"])
      ==(no,2,13051,1,5,st)]
  if len(branches)!=1 or not has(branches[0],0,2,13051,1,1 if st==1 else 0):
   # Stage2 is a completion resultClass2/pStyle3 value0, not a +1.
   if st==2 and len(branches)==1 and has(branches[0],0,2,13051,3,0):
    pass
   else:raise ValueError(f"Original ghost quest progress branch missing {st}")
  if st==1 and not any(c["class"]==1 and c["param"]==29639 and
        c["pstyle"]==1 and c["ops"]==2 and c["val"]==0 and
        c["to_result"]==no for c in ev["conditions"]):
   raise ValueError("Original quest13051 female NPC item29639 gate missing")
  targets[(13051,st)]=(sid,x,y,1,nid)
  print("GHOST_PASS",st,"SCENE",sid,"NPC",npcid,"PLACEMENT",nid,"EVE",eid)
 return targets
def patch(file,eve,apply=False):
 p=Path(file)
 targets=source_proof(eve)
 raw=p.read_bytes()
 rows={(row["mission"],row["step"]):row for row in read_steps(p)}
 herb=[row for row in rows if row[0]==12763]
 ghost=[row for row in rows if row[0]==13051]
 if sorted(st for q,st in herb)!=[1,2,3,4,5,6,7,8,10] or ghost!=[(13051,1),(13051,2)]:
  raise ValueError("Source quest step schemas changed")
 for st in (1,10):
  if rows[(12763,st)]["end"]!=(12002,170,340,1,19):
   raise ValueError("Original 12763 Bao BDY NPC route changed")
 for key,target in targets.items():
  row=rows[key]
  prev=((0,0,0,0,0) if key[0]==12763 else (target[0],0,0,0,0))
  if row["end"] not in (prev,target):
   raise ValueError(f"Pre-existing MarkStep target changed {key}: {row['end']}")
  if any(c[0] for c in row["conditions"]):
   raise ValueError(f"Original stage {key} has new active condition; can't patch")
 positions=scan(raw)
 edited=bytearray(raw)
 allowed=set()
 for key,target in sorted(targets.items()):
  ref=positions[(key[0],key[1],"END")]
  prev=((0,0,0,0,0) if key[0]==12763 else (target[0],0,0,0,0))
  if tuple(ref[:5]) not in (prev,target):
   raise ValueError(f"Original packed END did not match {key}")
  at=ref[-1]-7
  edited[at:at+8]=struct.pack("<HHHBB",*target)
  allowed.update(range(at,at+8))
  print("FIELD",key,"OFFSET",at,"TARGET",target)
 if edited==raw:
  print("ALREADY_FIXED",p,sha(raw))
  return sha(raw)
 if sha(raw) not in OLD_SHAS or len(edited)!=len(raw):
  raise ValueError("Unknown original Git/client predecessor SHA/length")
 diff={i for i,(b,a) in enumerate(zip(raw,edited)) if b!=a}
 if not diff or not diff.issubset(allowed) or any(not diff.intersection(range(positions[(q,st,"END")][-1]-7,positions[(q,st,"END")][-1]+1)) for q,st in targets):
  raise ValueError("Unexpected byte modifications outside exactly 9 END fields")
 after=scan(edited)
 if any(tuple(after[(q,st,"END")][:5])!=t for (q,st),t in targets.items()):
  raise ValueError("END independent packed decoding failed")
 print("PATCH",p,"OLD",sha(raw),"NEW",sha(edited),"DIFF_BYTES",len(diff))
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(edited)
 backup=p.with_name(p.name+".pre-12763-herb-13051-ghost-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Backup SHA mismatch")
 tmp=p.with_name(p.name+".12763-13051-temp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(edited)
  if sha(tmp.read_bytes())!=sha(edited):raise IOError("Staged SHA mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(edited):raise IOError("Atomic write readback mismatch")
 print("BACKUP",backup)
 return sha(edited)
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 args=ap.parse_args()
 patch(args.file,args.eve,args.apply)
