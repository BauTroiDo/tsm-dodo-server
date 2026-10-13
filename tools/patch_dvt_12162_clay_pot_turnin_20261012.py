#!/usr/bin/env python3
"""DVT 12162/1: restore source-proven TURN-IN waypoint, not clay-pot acquisition."""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path

from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

MISSION, STEP, ITEM, QUANTITY = 12162, 1, 32043, 30
TARGET = (12808, 310, 360, 1, 10)
SOURCE_SHAS = {
    "6e01ccb0fc06944911bfe5ee159bc6e261fbd794af081cfe5fb868d489388958",
    "25f279da092c06e8159fb22790b21c5b80106258a633074de242bdfc1c753cd0",
}

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def verify_eve(archive):
    scene = archive.parse_scene(TARGET[0])
    if scene is None:
        raise ValueError("Missing original turn-in scene 12808")
    matches = [npc for npc in scene["npcs"]
               if npc["npc_id"] == 15050 and
               (npc["id"],npc["x"],npc["y"]) == (TARGET[4],TARGET[1],TARGET[2])
               and 13 in npc["events"]]
    if len(matches) != 1:
        raise ValueError("DVT NPC15050 placement10 at original coordinate missing")
    conditions = scene["events"][13]["conditions"]
    mission = [c for c in conditions if c["class"] == 2 and c["param"] == MISSION
               and c["pstyle"] == 1 and c["val"] == 1 and c["no"] == 6]
    has_required_pots = any(c["no"] == 7 and c["class"] == 1 and c["param"] == ITEM
                            and c["pstyle"] == 1 and c["ops"] == 2 and c["val"] == 29
                            and c["to_result"] == 6 for c in conditions)
    if len(mission) != 1 or not has_required_pots:
        raise ValueError("Original 30-pot mission turn-in conditions changed")
    results = mission[0]["results"]
    def effect(cls, param, pstyle, value):
        return any(r["type"] == 0 and r["class"] == cls and r["param"] == param
                   and r["pstyle"] == pstyle and r["val"] == value for r in results)
    if not all((effect(1, ITEM, 1, -30), effect(1, 26416, 1, 5),
                effect(2, MISSION, 3, 0), effect(2, 12163, 1, 1))):
        raise ValueError("Original pot collection/next mission transition changed")
    return TARGET

def patch(path, eve, apply=False):
    path, eve = Path(path), Path(eve)
    raw = path.read_bytes()
    candidates = [s for s in read_steps(path)
                  if (s["mission"],s["step"]) == (MISSION,STEP)]
    if len(candidates) != 1:
        raise ValueError("Missing or duplicate 12162/1")
    stage = candidates[0]
    if not stage["conditions"] or stage["conditions"][0][:3] != (3,ITEM,QUANTITY):
        raise ValueError("Expected collect exactly thirty clay pots")
    if stage["conditions"][0][3] != (0,0,0,0,0):
        raise ValueError("Collection source must remain unlocated until independently verified")
    target = verify_eve(EveArchive(eve))
    ref = scan(raw)[(MISSION,STEP,"END")]
    if ref[:5] == target:
        print("ALREADY_FIXED",path,sha(raw),target)
        return sha(raw)
    if ref[:5] != (0,0,0,0,0):
        raise ValueError(f"Unknown original turn-in waypoint {ref[:5]}")
    if sha(raw) not in SOURCE_SHAS:
        raise ValueError(f"Unexpected source edition SHA {sha(raw)}")
    start = ref[5] - 7
    edited = bytearray(raw)
    edited[start:start+8] = struct.pack("<HHHBB",*target)
    diff = {i for i,(a,b) in enumerate(zip(raw,edited)) if a!=b}
    if not diff or not diff.issubset(set(range(start,start+8))) or len(edited)!=len(raw):
        raise ValueError("Unapproved binary change")
    if scan(bytes(edited))[(MISSION,STEP,"END")][:5] != target:
        raise ValueError("Post-edit MarkStep parse mismatch")
    print("PATCH",path,"OLD_SHA",sha(raw),"NEW_SHA",sha(edited),
          "CHANGED_BYTES",len(diff),"BYTE_WINDOW",start,start+7,"TARGET",target)
    if not apply:
        print("DRY_RUN_ONLY")
        return sha(edited)
    backup = path.with_name(path.name + ".pre-dvt-12162-turnin-" +
                            datetime.now().strftime("%Y%m%d_%H%M%S") + ".bak")
    if backup.exists():
        raise FileExistsError(backup)
    backup.write_bytes(raw)
    if backup.read_bytes()!=raw:
        raise IOError("Backup verification failure")
    staged = path.with_name(path.name+".dvt12162-tmp")
    if staged.exists():
        raise FileExistsError(staged)
    try:
        staged.write_bytes(edited)
        if staged.read_bytes()!=edited:
            raise IOError("Temporary file mismatch")
        os.replace(staged,path)
    finally:
        if staged.exists(): staged.unlink()
    if path.read_bytes()!=edited:
        raise IOError("Post-write verification failure")
    print("BACKUP",backup)
    return sha(edited)

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("file",type=Path)
    parser.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
    parser.add_argument("--apply",action="store_true")
    args=parser.parse_args()
    patch(args.file,args.eve,args.apply)
