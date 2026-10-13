#!/usr/bin/env python3
"""Correct two misclassified NPC waypoints to real Eve doors; restore two Qixi quest waypoints."""
import argparse
import hashlib
import os
import struct
from pathlib import Path
from datetime import datetime
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

# One navigation END per mission and stage. (mission,step)->(scene,x,y,eventKind,eventId)
TARGETS={
 (12936,16):(12403,100,400,2,7),  # Door7→Eve10, the only original +1 stage16.
 (12509,16):(49945,280,540,2,2),  # Door2→Eve9, Santa + follower completion.
 (12938,4):(12031,420,480,1,13), # Reuse stage3 original aunt NPC15133 waypoint.
 (12938,5):(12031,740,680,2,6),  # Door6→Eve19 stage5 +1 and reveals NPC14.
}
PREVIOUS={
 (12936,16):(12403,220,420,1,8),
 (12509,16):(49945,270,420,1,1),
 (12938,4):(0,0,0,0,0),
 (12938,5):(0,0,0,0,0),
}
SOURCES={
 "382540766e75528bacbb40792188dfa7c210a930bc9bcb82250ccfbea11eb280",
 "b76cc4dc6a0721bda6ff650687582ae0cd93a26d71a9b6bd4f2e00d3643f425e",
}
def sha(x):return hashlib.sha256(x).hexdigest()
def effect(c,t,cl,pa,ps,v):
 return any(z["type"]==t and z["class"]==cl and z["param"]==pa
            and z["pstyle"]==ps and z["val"]==v for z in c["results"])
def exact_step_gate(ev,quest,stage,extra_and):
 opts=[c for c in ev["conditions"] if c["class"]==2 and c["param"]==quest
       and c["pstyle"]==1 and c["ops"]==5 and c["val"]==stage and c["and_num"]==extra_and]
 if len(opts)!=1:
  raise ValueError(f"No unique exact original quest gate {quest}/{stage} AND={extra_and}")
 return opts[0]
def source_proof(eve):
 arc=EveArchive(Path(eve))
 for (qid,st),target in TARGETS.items():
  sid,x,y,kind,placement=target
  sc=arc.parse_scene(sid)
  if kind==2:
   found=[door for door in sc["doors"] if
          (door["id"],door["x"],door["y"])==(placement,x,y)]
   if len(found)!=1:raise ValueError(f"Original event door missing: {qid}/{st}")
   eve_numbers=found[0]["events"]
   # Important: when_happen[1] is *Door*, not automatic scene entry.
   events=[sc["events"][eid] for eid in eve_numbers if
           sc["events"][eid]["when_happen"]==[False,True,False,False]]
   if len(events)!=1:raise ValueError(f"Door must map exactly one original door Eve: {qid}/{st}")
   ev=events[0]
   if any(eveid in n["events"] for eveid in eve_numbers for n in sc["npcs"]):
    raise ValueError("Door Eve incorrectly linked to NPC")
   if (qid,st)==(12936,16):
    c=exact_step_gate(ev,qid,st,1)
    if not effect(c,0,2,qid,1,1) or not effect(c,0,3,8,4,500):
     raise ValueError("Original scripted doorway must advance mission and reveal NPC")
    if sc["scene_infos"].get(7) is not None:
     raise ValueError("Door7 should be non-teleport scripted interaction")
   elif (qid,st)==(12509,16):
    c=exact_step_gate(ev,qid,st,2)
    if not effect(c,0,2,qid,3,0) or not effect(c,0,8,38136,2,0):
     raise ValueError("Santa original finish must end quest and remove thief follower")
    follower=[g for g in ev["conditions"] if g["class"]==9 and
        g["param"]==0 and g["pstyle"]==1 and g["ops"]==5 and g["val"]==38136]
    if len(follower)!=1 or follower[0]["to_result"]!=c["no"]:
     raise ValueError("Original Santa finish not protected by follower condition")
    # scene_infos keys identify warp Eve numbers, not Door IDs. Eve9 is the
    # script-only completion event and is not a warp scene-info entry.
    if sc["scene_infos"].get(9) is not None:
     raise ValueError("Santa Eve9 should finish quest, not warp out")
   elif (qid,st)==(12938,5):
    c=exact_step_gate(ev,qid,st,1)
    if not effect(c,0,2,qid,1,1) or not effect(c,0,3,14,4,500):
     raise ValueError("Qixi door6 lacks original quest progress and apparition")
    if sc["scene_infos"].get(6) is not None:
     raise ValueError("Qixi door6 must be script trigger, not a teleport")
  elif kind==1:
   npc=[n for n in sc["npcs"] if n["id"]==placement and
      (n["npc_id"],n["x"],n["y"],n["close"])==(15133,470,480,True)
      and 17 in n["events"]]
   if len(npc)!=1:raise ValueError("Qixi aunt NPC source missing")
   ev=sc["events"][17]
   if ev["when_happen"]!=[True,False,False,False]:
    raise ValueError("Qixi aunt must be NPC interaction")
   c=exact_step_gate(ev,qid,st,1)
   if not effect(c,0,2,qid,1,1):raise ValueError("Qixi aunt stage4 must advance mission")
  else:raise ValueError("Unexpected target kind")
 # Original 12936/17 npc8 is the step AFTER the door trigger, not its cause.
 sc=arc.parse_scene(12403)
 if not any(n["id"]==8 and n["npc_id"]==12059 and 11 in n["events"] for n in sc["npcs"]):
  raise ValueError("Follow-on NPC after Door7 not original")
 print("SOURCE_DOOR_PROOF_PASS",len(TARGETS))
 return TARGETS

def patch(path,eve,apply=False):
 p=Path(path)
 source_proof(eve)
 raw=p.read_bytes()
 rows={(x["mission"],x["step"]):x for x in read_steps(p)}
 if rows[(12936,17)]["end"]!=(12403,220,420,1,8):
  raise ValueError("Original follow-on 12936 NPC end changed")
 for s in (1,2):
  if rows[(12509,s)]["end"]!=(49945,270,420,1,1):
   raise ValueError("Original Santa stage1/2 NPC endpoint changed")
 if rows[(12509,16)]["conditions"][0]!=(1,38136,1,(49946,590,310,1,12)):
  raise ValueError("Santa follower chase objective changed")
 if rows[(12938,3)]["end"]!=TARGETS[(12938,4)]:
  raise ValueError("Qixi original stage3 aunt target changed")
 if rows[(12938,6)]["end"]!=(12031,690,660,1,14):
  raise ValueError("Qixi original stage6 apparition target changed")
 for key,target in TARGETS.items():
  if key!=(12509,16) and any(c[0] for c in rows[key]["conditions"]):
   raise ValueError(f"Unexpected original item/follower condition: {key}")
  if rows[key]["end"] not in (target,PREVIOUS[key]):
   raise ValueError(f"Unexpected pre-existing END at {key}: {rows[key]['end']}")
 offsets=scan(raw)
 new=bytearray(raw)
 allowed=set()
 for key,target in TARGETS.items():
  rec=offsets[(key[0],key[1],"END")]
  at=rec[-1]-7
  if tuple(rec[:5]) not in (target,PREVIOUS[key]):
   raise ValueError(f"Packed field unexpectedly changed: {key}")
  packed=struct.pack("<HHHBB",*target)
  allowed.update(range(at,at+8))
  new[at:at+8]=packed
  print("FIELD",key,"OFFSET",at,"FROM",tuple(rec[:5]),"TO",target)
 if new==raw:
  print("ALREADY_FIXED",p,sha(raw))
  return sha(raw)
 if sha(raw) not in SOURCES or len(raw)!=len(new):
  raise ValueError("Unknown source MarkStep edition, refusing modifications")
 changed={i for i,(x,y) in enumerate(zip(raw,new)) if x!=y}
 if not changed or not changed.issubset(allowed):
  raise ValueError("Byte difference beyond authorized 4 END fields")
 for key,target in TARGETS.items():
  if tuple(scan(new)[(key[0],key[1],"END")][:5])!=target:
   raise ValueError(f"Roundtrip invalid target: {key}")
 print("PATCH",p,"OLD_SHA",sha(raw),"NEW_SHA",sha(new),"CHANGED_BYTES",len(changed))
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(new)
 bak=p.with_name(p.name+".pre-door-trigger-correction-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if bak.exists():raise FileExistsError(bak)
 bak.write_bytes(raw)
 if sha(bak.read_bytes())!=sha(raw):raise IOError("Backup SHA mismatch")
 tmp=p.with_name(p.name+".door-trigger-correction-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(new)
  if sha(tmp.read_bytes())!=sha(new):raise IOError("Temp SHA mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(new):raise IOError("Final SHA mismatch")
 print("BACKUP",bak)
 return sha(new)

if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 args=ap.parse_args()
 patch(args.file,args.eve,args.apply)
