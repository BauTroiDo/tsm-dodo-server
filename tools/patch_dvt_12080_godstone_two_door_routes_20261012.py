#!/usr/bin/env python3
"""Original DVT divine-stone quest12080 stage4/7 navigation to battle-gated Door Eves."""
import argparse, hashlib, os, struct
from pathlib import Path
from datetime import datetime
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

TARGETS={
 4: (32839,640,700,2,1,1),
 7: (32837,820,540,2,1,1),
}
SOURCE_HASHES={
 "18291b043c1384d3cbc10305230d2e9798877c8dd5f6c89802bd677832273234",
 "75f6e387c0af1d0c8ff15aad9ac2fadfd256433fc23e13fb47af6b6967cbc208",
}
def sha(b): return hashlib.sha256(b).hexdigest()
def source_eve_proof(eve_path):
 archive=EveArchive(Path(eve_path))
 for stage,(scene_id,x,y,kind,door_id,eve_id) in TARGETS.items():
  sc=archive.parse_scene(scene_id)
  doors=[d for d in sc["doors"] if (d["id"],d["x"],d["y"])==(door_id,x,y) and eve_id in d["events"]]
  if len(doors)!=1: raise ValueError(f"Missing original Godstone door scene{scene_id} door{door_id}")
  if any(eve_id in n["events"] for n in sc["npcs"]): raise ValueError("Door source Eve also NPC-bound")
  event=sc["events"][eve_id]
  if event["when_happen"]!=[False,True,False,False]:raise ValueError("Wrong original Door trigger type")
  gates=[c for c in event["conditions"] if
      c["class"]==2 and c["param"]==12080 and c["pstyle"]==1 and
      c["ops"]==5 and c["val"]==stage]
  if len(gates)!=6:raise ValueError(f"Unexpected original battle branch count stage{stage}: {len(gates)}")
  if not any(c["and_num"]==3 and any(z["type"]==0 and z["class"]==2 and
              z["param"]==12080 and z["pstyle"] in (1,2) and z["val"]==1
              for z in c["results"]) for c in gates):
   raise ValueError(f"Battle-dependent progress branch missing stage{stage}")
  if not any(c["and_num"] in (2,3) for c in gates):
   raise ValueError("Missing original alternative conditions")
  if not any(x["class"]==8 for x in event["conditions"]):
   raise ValueError("Missing original battle result condition in Eve")
  if sc["scene_infos"].get(eve_id,{}).get("target_scene") is None:
   raise ValueError("Original source Door warp should remain present")
  print("EVE_SOURCE_PASS",stage,scene_id,"DOOR",door_id,"EVE",eve_id,"STAGE_GATES",len(gates))
 return {stage:route[:5] for stage,route in TARGETS.items()}
def patch(file,eve,apply=False):
 p=Path(file)
 target=source_eve_proof(eve)
 before=p.read_bytes()
 rows={x["step"]:x for x in read_steps(p) if x["mission"]==12080}
 if len(rows)!=14:raise ValueError("Original Godstone steps changed")
 expected_neighbors={3:(32546,1810,340,2,1),5:(32841,710,220,1,1),
                     6:(32841,710,220,1,1),8:(32841,710,220,1,1)}
 for st,t in expected_neighbors.items():
  if rows[st]["end"]!=t:raise ValueError(f"Original neighbor step{st} changed")
 for st,t in target.items():
  if rows[st]["end"] not in ((0,0,0,0,0),t) or any(c[0] for c in rows[st]["conditions"]):
   raise ValueError(f"Unknown original MarkStep stage{st} target/conditions")
 locations=scan(before)
 edited=bytearray(before)
 allowed=set()
 for st,t in sorted(target.items()):
  rec=locations[(12080,st,"END")]
  at=rec[-1]-7
  if tuple(rec[:5]) not in ((0,0,0,0,0),t):raise ValueError(f"Packed MarkStep record mismatch stage{st}")
  edited[at:at+8]=struct.pack("<HHHBB",*t)
  allowed.update(range(at,at+8))
  print("FIELD",st,"OFFSET",at,"TO",t)
 if edited==before:
  print("ALREADY_FIXED",p,sha(before))
  return sha(before)
 if sha(before) not in SOURCE_HASHES or len(edited)!=len(before):
  raise ValueError("Unexpected source/client edition SHA")
 differences={i for i,(a,b) in enumerate(zip(before,edited)) if a!=b}
 if len(differences)!=16 or differences!=allowed:
  raise ValueError("Authorized 16-byte END difference check failed")
 after=scan(edited)
 if any(tuple(after[(12080,st,"END")][:5])!=t for st,t in target.items()):
  raise ValueError("Roundtrip packed END mismatch")
 print("PATCH",p,"SHA_OLD",sha(before),"SHA_NEW",sha(edited),"CHANGED_BYTES",len(differences))
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(edited)
 backup=p.with_name(p.name+".pre-12080-godstone-doors-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists(): raise FileExistsError(backup)
 backup.write_bytes(before)
 if sha(backup.read_bytes())!=sha(before):raise IOError("Backup checksum mismatch")
 tmp=p.with_name(p.name+".12080-godstone-door-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(edited)
  if sha(tmp.read_bytes())!=sha(edited):raise IOError("Temp checksum mismatch")
  os.replace(tmp,p)
 finally:
  if tmp.exists():tmp.unlink()
 if sha(p.read_bytes())!=sha(edited):raise IOError("Post replacement checksum mismatch")
 print("BACKUP",backup)
 return sha(edited)
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 args=ap.parse_args()
 patch(args.file,args.eve,args.apply)
