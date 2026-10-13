#!/usr/bin/env python3
"""Guide DVT Noel12509 step12 to its source-opened cave door2, already used by step13."""
import argparse,hashlib,os,struct
from pathlib import Path
from datetime import datetime
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

END=(49946,2370,150,2,2)
OLD_SHA={
"2edf6533584247e20f9d18e19753595b59d9c47fde6960d0630a38f8d1f0532f",
"78c2f74b0b4ad82932ad385b8f92cb68315a4ce803d405293f0472c7b4dca364",
}
def sha(b):return hashlib.sha256(b).hexdigest()
def verify_source(eve):
 a=EveArchive(Path(eve))
 mid=a.parse_scene(49946)
 if mid["scene_infos"][2]["target_scene"]!=49947 or mid["scene_infos"][3]["target_scene"]!=49948:
  raise ValueError("Original paired source cave doors changed")
 gate=mid["events"][2]
 if gate["when_happen"]!=[False,True,False,False]:
  raise ValueError("Original door unlock must be an entry trigger")
 matches=[c for c in gate["conditions"] if c["class"]==2 and c["param"]==12509
          and c["pstyle"]==1 and c["ops"]==5 and c["val"]==12 and c["to_result"]==1]
 if len(matches)!=1 or not all(any(z["type"]==2 and z["class"]==7 and z["param"]==eid
                            for z in matches[0]["results"]) for eid in (2,3)):
  raise ValueError("Both cave door unlock effects not supported by quest12509/12")
 for sid in (49947,49948):
  sc=a.parse_scene(sid)
  if sc["scene_infos"][1]["target_scene"]!=49946:
   raise ValueError("Cave return door does not lead to original approach scene")
  if not any(c["class"]==2 and c["param"]==12509 and c["pstyle"]==1
             and c["ops"]==5 and c["val"]==12
             for eid in (2,4,5) for c in sc["events"][eid]["conditions"]):
   raise ValueError("Cave has no original step12 scene-entry interaction")
 return END

def patch(path,eve,apply=False):
 p=Path(path);raw=p.read_bytes()
 target=verify_source(eve)
 rows={s["step"]:s for s in read_steps(p) if s["mission"]==12509}
 if rows[11]["end"]!=(49944,370,730,1,3):
  raise ValueError("Earlier quest stage11 NPC waypoint changed")
 if rows[13]["end"]!=target:
  raise ValueError("Must reuse exact existing original stage13 door waypoint")
 if rows[14]["end"]!=(49946,2250,220,1,11):
  raise ValueError("Original verified quiz NPC changed")
 if any(c[0] for c in rows[12]["conditions"]):
  raise ValueError("Stage12 is not the original dialogue/scene objective")
 ref=scan(raw)[(12509,12,"END")]
 if tuple(ref[:5])==target:
  print("ALREADY_FIXED",p,sha(raw),target)
  return sha(raw)
 if tuple(ref[:5])!=(0,0,0,0,0) or sha(raw) not in OLD_SHA:
  raise ValueError("Unrecognized source edition or modified stage12 END")
 at=ref[-1]-7
 new=bytearray(raw)
 new[at:at+8]=struct.pack("<HHHBB",*target)
 changed={i for i,(a,b) in enumerate(zip(raw,new)) if a!=b}
 if not changed or len(changed)>8 or not changed.issubset(set(range(at,at+8))):
  raise ValueError("Unexpected edits outside exact eight-byte END field")
 if tuple(scan(new)[(12509,12,"END")][:5])!=target:
  raise ValueError("Patched END roundtrip mismatch")
 print("PATCH",p,"OFFSET",at,"DIFF",len(changed),"OLD_SHA",sha(raw),"NEW_SHA",sha(new),"END",target)
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(new)
 backup=p.with_name(p.name+".pre-12509-cave12-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Backup SHA")
 tmp=p.with_name(p.name+".12509-cave12-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(new)
  if sha(tmp.read_bytes())!=sha(new):raise IOError("Temp SHA")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(new):raise IOError("Result SHA")
 print("BACKUP",backup)
 return sha(new)
if __name__=="__main__":
 q=argparse.ArgumentParser()
 q.add_argument("file",type=Path)
 q.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 q.add_argument("--apply",action="store_true")
 o=q.parse_args()
 patch(o.file,o.eve,o.apply)
