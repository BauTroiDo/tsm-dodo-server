#!/usr/bin/env python3
"""Guide two original DVT 10988 Sun Shangxiang follower-gated battle objectives.

When followNpc=13038 is unmet, DVT Lua selects condition target BEFORE END,
causing blank navigation on 10988 stages 3 and 4. The source step6 already
uses the same battle-area landmark. Patch only condition1 targets.
"""
import argparse,hashlib,os,struct
from pathlib import Path
from datetime import datetime
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan as scan_packed

KEYS=((10988,3),(10988,4))
TARGET=(18501,1870,1800,0,0)
ORIGINAL={
 "e48a4b7f50f6dfc4d3b902279bdda5efea0c783c3eb46f7c300a80db1bec13ad",
 "93afa327618e1632b80bb0ddcd1fb1da73e3a6f0584da2046048d0be1ac35acc"}
NEW={"a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da","511563050ea38ca86e8b85a9ec954fb1b8165ec12403b34521f3acf6b33e40f7"}
def sha(src):return hashlib.sha256(src).hexdigest()
def verify(eve,steps):
 rows={(r["mission"],r["step"]):r for r in steps}
 for k in KEYS:
  r=rows[k]
  if r["end"]!=TARGET or r["conditions"][0][:3]!=(1,13038,1):
   raise ValueError(f"Original follower battle objective not matched {k}")
  if r["conditions"][0][3] not in ((0,0,0,0,0),TARGET):
   raise ValueError(f"Unexpected condition target {k}")
  if any(x[0] for x in r["conditions"][1:]):
   raise ValueError(f"Unexpected additional conditions {k}")
 if rows[(10988,6)]["conditions"][0] != (1,13038,1,TARGET):
  raise ValueError("Source step6 follower-reacquisition landmark changed")
 sc=EveArchive(eve).parse_scene(18501)
 owner=[n for n in sc["npcs"] if
        (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(4,10240,1870,1800,False)
        and 7 in n["events"] and 8 in n["events"]]
 if len(owner)!=1:raise ValueError("Original Tai Ho pirate NPC4 not unique")
 e=sc["events"][7]
 if e["when_happen"] != [True,False,False,False]:
  raise ValueError("Eve7 not original pirate ClickNpc")
 for st in (3,4):
  matches=[c for c in e["conditions"] if
    (c["class"],c["param"],c["pstyle"],c["ops"],c["val"])==(2,10988,1,5,st)]
  if len(matches)<1:raise ValueError(f"Pirate battle event stage {st} absent")
 print("SOURCE_PASS TaiHo18501 pirate NPC4 Eve7 stage3/4, step6 fallback follower landmark")
 return True
def patch(path,eve,apply=False):
 path=Path(path)
 raw=path.read_bytes()
 steps=read_steps(path)
 verify(Path(eve),steps)
 refs=scan_packed(raw)
 changed=bytearray(raw); allowed=set()
 for mid,st in KEYS:
  ref=refs[mid,st,"CONDITION_1"]
  offset=ref[-1]-7
  if tuple(ref[:5]) not in ((0,0,0,0,0),TARGET):
   raise ValueError("Unrecognized original packed condition target")
  changed[offset:offset+8]=struct.pack("<HHHBB",*TARGET)
  allowed.update(range(offset,offset+8))
  print("FIELD",mid,st,"CONDITION_1","OFFSET",offset,"TARGET",TARGET)
 oldsha,newsha=sha(raw),sha(changed)
 if raw==changed:
  if oldsha not in NEW:raise ValueError("Unknown already-patched full hash")
  print("ALREADY_PATCHED",oldsha)
  return oldsha
 if oldsha not in ORIGINAL:
  raise ValueError("Unrecognized source data file SHA")
 diff={i for i,(a,b) in enumerate(zip(raw,changed)) if a!=b}
 if len(changed)!=len(raw) or not diff or not diff.issubset(allowed):
  raise ValueError("Change outside 2 expected condition fields")
 scanned=scan_packed(changed)
 if any(tuple(scanned[mid,st,"CONDITION_1"][:5])!=TARGET for mid,st in KEYS):
  raise ValueError("Roundtrip packed condition mismatch")
 print("PATCH",path,"OLD",oldsha,"NEW",newsha,"DIFF_BYTES",len(diff))
 if not apply:
  print("DRY_RUN_ONLY")
  return newsha
 b=path.with_name(path.name+".pre-10988-follow-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if b.exists():raise FileExistsError(b)
 b.write_bytes(raw)
 if sha(b.read_bytes())!=oldsha:raise IOError("Backup integrity failure")
 t=path.with_name(path.name+".10988-follow.tmp")
 if t.exists():raise FileExistsError(t)
 try:
  t.write_bytes(changed)
  if sha(t.read_bytes())!=newsha:raise IOError("Temp integrity failure")
  os.replace(t,path)
 finally:
  if t.exists():t.unlink()
 if sha(path.read_bytes())!=newsha:raise IOError("Postwrite file integrity failure")
 print("BACKUP",b)
 return newsha
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("file",type=Path)
 ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 ap.add_argument("--apply",action="store_true")
 a=ap.parse_args()
 patch(a.file,a.eve,a.apply)
