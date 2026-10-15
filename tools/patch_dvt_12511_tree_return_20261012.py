#!/usr/bin/env python3
"""Restore original Christmas-tree navigation for four DVT quest12511 day-return stages.
Read-only unless --apply; never change time, rewards, missions or Eve.
"""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan as scan_packed

TARGETS = {(12511,st):(49944,850,700,1,1) for st in (3,7,12,18)}
OLD = {"ab39587a5dacbd65ca60490bf58c5c3da680e826b72b195fc69f80c388f4ec07",
       "8b7cbe97e8ec96a982adb2ed2011d7d816bd9b3648a56ae1e742ab8d55ad8b68"}
def sha(b):return hashlib.sha256(b).hexdigest()
def result(c,kind,param,style,val):
    return any((r["type"],r["class"],r["param"],r["pstyle"],r["val"]) ==
               (0,kind,param,style,val) for r in c["results"])
def verify_source(eve):
    sc=EveArchive(eve).parse_scene(49944)
    if not sc:raise ValueError("Source Eve scene49944 not found")
    npcs=[n for n in sc["npcs"] if
          (n["id"],n["npc_id"],n["x"],n["y"],n["close"])==(1,38001,850,700,False)
          and 1 in n["events"]]
    if len(npcs)!=1 or any(1 in d["events"] for d in sc["doors"]):
        raise ValueError("Source NPC placement 1 / Eve1 ambiguous")
    e=sc["events"][1]
    if e["when_happen"] != [True,False,False,False]:
        raise ValueError("Source tree event is not ClickNpc")
    if not any(c["class"]==14 and c["param"]==0 and c["pstyle"]==0
               for c in e["conditions"]):
        raise ValueError("Source calendar gate absent")
    for step in (3,7,12,18):
        eligible=[c for c in e["conditions"]
          if (c["class"],c["param"],c["pstyle"],c["ops"],c["val"]) ==
             (2,12511,1,5,step)
             and result(c,2,12547,1,1)
             and result(c,2,12511,3,0)
             and result(c,2,12512,1,1)]
        if len(eligible)!=1:
            raise ValueError(f"Original tree reward branch missing/ambiguous: {step}")
    print("SOURCE_PASS 49944 NPC38001 placement1 Eve1 four stage gated reward branches")
    return TARGETS

def patch(path,eve,apply=False):
    path=Path(path); target=verify_source(Path(eve))
    before=path.read_bytes()
    steps={(x["mission"],x["step"]):x for x in read_steps(path)}
    if [s for m,s in steps if m==12511]!=list(range(1,19)):
        raise ValueError("Quest12511 stage rows changed")
    for key,dest in target.items():
        item=steps[key]
        if item["end"] not in ((0,0,0,1,1),dest) or any(x[0] for x in item["conditions"]):
            raise ValueError(f"Source quest12511 END/objectives changed {key}")
        if "cách ngày" not in item["description"].lower() or "Noel" not in item["description"]:
            raise ValueError(f"Quest12511 return on next day clue absent {key}")
        if steps[(key[0],key[1]-1)]["end"]!=(49945,270,420,1,1):
            raise ValueError("Original previous stage Santa home route changed")
        if key[1]<18 and steps[(key[0],key[1]+1)]["end"]!=(49945,270,420,1,1):
            raise ValueError("Original next stage Santa home route changed")
    refs=scan_packed(before); after=bytearray(before);allowed=set()
    for key,dest in target.items():
        ref=refs[key[0],key[1],"END"]
        offset=ref[-1]-7
        if tuple(ref[:5]) not in ((0,0,0,1,1),dest):
            raise ValueError(f"Packed field mismatch {key}")
        after[offset:offset+8]=struct.pack("<HHHBB",*dest)
        allowed.update(range(offset,offset+8))
        print("FIELD",key,"OFFSET",offset,"DEST",dest)
    oldsha=sha(before); newsha=sha(after)
    if before==after:
        # Validating source/events and exact patched fields is still necessary; known post-hashes
        if oldsha not in NEW:
            raise ValueError("Unknown full binary hash for already patched fields")
        print("ALREADY_PATCHED",oldsha);return oldsha
    if oldsha not in OLD:raise ValueError("Unknown predecessor SHA")
    differences={i for i,(a,b) in enumerate(zip(before,after)) if a!=b}
    if not differences or not differences.issubset(allowed) or len(after)!=len(before):
        raise ValueError("Unexpected changed bytes")
    check=scan_packed(after)
    if any(tuple(check[k[0],k[1],"END"][:5])!=dest for k,dest in target.items()):
        raise ValueError("Postpatch packed decode disagrees")
    print("PATCH",path,"SHA",oldsha,"->",newsha,"DIFFERENT_BYTES",len(differences))
    if not apply: print("DRY_RUN_ONLY"); return newsha
    backup=path.with_name(path.name+".pre-tree-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
    if backup.exists():raise FileExistsError(backup)
    backup.write_bytes(before)
    if sha(backup.read_bytes())!=oldsha:raise IOError("Backup hash mismatch")
    temp=path.with_name(path.name+".tree-stage.tmp")
    if temp.exists():raise FileExistsError(temp)
    try:
        temp.write_bytes(after)
        if sha(temp.read_bytes())!=newsha:raise IOError("Temp hash mismatch")
        os.replace(temp,path)
    finally:
        if temp.exists():temp.unlink()
    if sha(path.read_bytes())!=newsha:raise IOError("Installed SHA mismatch")
    print("BACKUP",backup)
    return newsha

# Populated once after two edition-specific dry-runs; safeguards rerun inputs.
NEW={"e48a4b7f50f6dfc4d3b902279bdda5efea0c783c3eb46f7c300a80db1bec13ad", "93afa327618e1632b80bb0ddcd1fb1da73e3a6f0584da2046048d0be1ac35acc",
     # Subsequent follower-condition-only patch preserves the four tree END fields.
     "a2ddb57f24debb314d2d5bc59b7721d99561717e6bad3252c35c1280150c80da",
     "511563050ea38ca86e8b85a9ec954fb1b8165ec12403b34521f3acf6b33e40f7",
     "11bba0ad361ff96266be77db1a3b5d6fcac37f82cd0345fb0c44777e8dbc1df2",
     "47c39a0b43e0e68117c1a964ade04af50adb879533d865a3d6df7d86cf2e6eed"}
if __name__=="__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("file",type=Path)
    ap.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
    ap.add_argument("--apply",action="store_true")
    a=ap.parse_args();patch(a.file,a.eve,a.apply)
