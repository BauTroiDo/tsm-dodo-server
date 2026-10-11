#!/usr/bin/env python3
"""Restore DVT10126 stage4 FIRST destination, the original Dong Quan spy."""
import argparse,hashlib,os,struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

MISSION,STEP,FOLLOW_NPC=10126,4,10099
TARGET=(11002,570,260,1,5)
ORIGINAL_FOLLOW=(11811,1902,1615,1,8)
EXPECTED={
 "6272d35367a8fda2c389380ce4f3aec0c9d1d5c65ddffc542e3cb240da1ac2c3",
 "4d51a729a20694855adfcca266b41d56c5e403d0062be702b8d26f9fb63bde44",
}
def sha(data):return hashlib.sha256(data).hexdigest()

def original_spy_target(eve):
 a=EveArchive(Path(eve))
 sc=a.parse_scene(11002)
 if sc is None:raise ValueError("Missing palace approach scene 11002")
 npc=[n for n in sc["npcs"] if n["npc_id"]==17018 and
      (n["id"],n["x"],n["y"],n["close"])==(5,570,260,False) and 14 in n["events"]]
 if len(npc)!=1:raise ValueError("Missing precise quest spy event14 NPC17018")
 event=sc["events"][14]["conditions"]
 def condition(n,style,ops,step):
  return any(c["no"]==n and c["class"]==2 and c["param"]==MISSION
             and c["pstyle"]==style and c["ops"]==ops and c["val"]==step
             for c in event)
 if not (condition(1,1,5,4) and condition(2,1,1,4)):
  raise ValueError("Spy event must distinguish quest10126 step4")
 if 6 not in sc["scene_infos"] or sc["scene_infos"][6]["target_scene"]!=11301:
  raise ValueError("Original NPC spy must stand by scene11002 door6 into palace")
 target=a.parse_scene(11301)
 if target is None:raise ValueError("Missing original palace interior")
 receivers=[n for n in target["npcs"] if n["npc_id"]==14216 and n["id"]==8 and
            (n["x"],n["y"])==(470,380) and 12 in n["events"]]
 if len(receivers)!=1:raise ValueError("Original palace turn-in NPC14216 changed")
 src=target["events"][12]["conditions"]
 transition=[c for c in src if c["no"]==1 and c["class"]==2 and
             c["param"]==MISSION and c["pstyle"]==1 and c["ops"]==5
             and c["val"]==4 and c["and_num"]==2]
 if len(transition)!=1:raise ValueError("Step4→5 gate changed")
 if not any(r["type"]==0 and r["class"]==2 and r["param"]==MISSION and
            r["pstyle"]==1 and r["val"]==1 for r in transition[0]["results"]):
  raise ValueError("No original transition to next mission step")
 return TARGET

def patch(path,eve,apply=False):
 path=Path(path)
 raw=path.read_bytes()
 match=[s for s in read_steps(path) if (s["mission"],s["step"])==(MISSION,STEP)]
 if len(match)!=1:raise ValueError("Quest10126 step4 missing")
 st=match[0]
 if st["conditions"][0]!=(1,FOLLOW_NPC,1,ORIGINAL_FOLLOW) or any(c[0]!=0 for c in st["conditions"][1:]):
  raise ValueError("The original compulsory follow-NPC objective changed")
 target=original_spy_target(eve)
 ref=scan(raw)[(MISSION,STEP,"END")]
 if ref[:5]==target:
  print("ALREADY_FIXED",path,sha(raw),target)
  return sha(raw)
 if ref[:5]!=(0,0,0,0,0) or sha(raw) not in EXPECTED:
  raise ValueError("Unapproved MarkStep edition/END")
 pos=ref[-1]-7
 edited=bytearray(raw)
 edited[pos:pos+8]=struct.pack("<HHHBB",*target)
 changed=[i for i,(x,y) in enumerate(zip(raw,edited)) if x!=y]
 if not changed or not set(changed).issubset(set(range(pos,pos+8))) or len(edited)!=len(raw):
  raise ValueError("Changed bytes escape approved END")
 if scan(edited)[(MISSION,STEP,"END")][:5]!=target:
  raise ValueError("Edited record parser mismatch")
 print("PATCH",path,"OLD",sha(raw),"NEW",sha(edited),"OFFSET",pos,"DIFF",len(changed),"TARGET",target)
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(edited)
 backup=path.with_name(path.name+".pre-10126-spy-route-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if backup.read_bytes()!=raw:raise IOError("Backup corrupted")
 tmp=path.with_name(path.name+".spy-route-tmp")
 if tmp.exists():raise FileExistsError(tmp)
 try:
  tmp.write_bytes(edited)
  if tmp.read_bytes()!=edited:raise IOError("Temp mismatch")
  os.replace(tmp,path)
 finally:
  if tmp.exists():tmp.unlink()
 if path.read_bytes()!=edited:raise IOError("Final checksum mismatch")
 print("BACKUP",backup)
 return sha(edited)

if __name__=="__main__":
 p=argparse.ArgumentParser()
 p.add_argument("file",type=Path)
 p.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 p.add_argument("--apply",action="store_true")
 x=p.parse_args()
 patch(x.file,x.eve,x.apply)
