#!/usr/bin/env python3
"""DVT mission10852 stage1: restore the verified Jade Rabbit RETURN waypoint."""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path

from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

MISSION, STEP, RABBIT = 10852, 1, 38022
HUNT = (10984, 3710, 2300, 1, 3)
RETURN = (11021, 190, 180, 1, 7)
SOURCE_SHAS = {
    "e0278071b4f12f75d058d51661e743ad0c42c83ab6a08af2bc610ba4f7e02074",
    "b7af396c575d50b18cb3408c8e476e91fa7a543c573256d3240ce52f69c8770c",
}

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def source_verified(eve):
    sc = EveArchive(Path(eve)).parse_scene(RETURN[0])
    if not sc: raise ValueError("Missing return scene")
    linked = [npc for npc in sc["npcs"] if npc["npc_id"] == 15153
              and (npc["id"],npc["x"],npc["y"]) == (RETURN[4],RETURN[1],RETURN[2])
              and 11 in npc["events"]]
    if len(linked) != 1: raise ValueError("Missing Ngô Cương NPC/Eve11 at expected source coordinates")
    conditions = sc["events"][11]["conditions"]
    def cond(no,cls,param,style,val,result):
        return any(c["no"] == no and c["class"] == cls and c["param"] == param
                   and c["pstyle"] == style and c["val"] == val
                   and c["to_result"] == result for c in conditions)
    if not (cond(5,2,MISSION,1,1,5) and cond(6,9,0,1,RABBIT,5)
            and cond(7,7,2,2,0,5)):
        raise ValueError("Ngô Cương original completed-capture condition changed")
    result = next(c for c in conditions if c["no"] == 5)["results"]
    def effect(cls,param,style,val):
        return any(r["type"] == 0 and r["class"] == cls and r["param"] == param
                   and r["pstyle"] == style and r["val"] == val for r in result)
    if not all((
        effect(13,0,1,40),  # UNRESOLVED; never skip, fake or grant rewards
        effect(8,RABBIT,2,1),
        effect(2,MISSION,3,0),
        effect(2,10853,1,1),
    )):
        raise ValueError("Original completion chain changed; unsupported class13 still required")
    return RETURN

def patch(path, eve, apply=False):
    path = Path(path)
    raw = path.read_bytes()
    row = [s for s in read_steps(path) if (s["mission"],s["step"]) == (MISSION,STEP)]
    if len(row) != 1 or row[0]["conditions"][0] != (1,RABBIT,1,HUNT):
        raise ValueError("Original live Jade Rabbit hunt waypoint changed")
    end = source_verified(eve)
    ref = scan(raw)[(MISSION,STEP,"END")]
    if ref[:5] == end:
        print("ALREADY_FIXED",path,sha(raw),end)
        return sha(raw)
    if ref[:5] != (0,0,0,0,0) or sha(raw) not in SOURCE_SHAS:
        raise ValueError("Unknown MarkStep source / END; refusing unverified binary edit")
    pos = ref[5]-7
    edited = bytearray(raw)
    edited[pos:pos+8] = struct.pack("<HHHBB",*end)
    changed = {i for i,(a,b) in enumerate(zip(raw,edited)) if a!=b}
    if not changed or not changed.issubset(set(range(pos,pos+8))) or len(edited)!=len(raw):
        raise ValueError("Binary edit escapes END field")
    if scan(edited)[(MISSION,STEP,"END")][:5] != end:
        raise ValueError("Post-edit parse mismatch")
    print("PATCH",path,"OLD_SHA",sha(raw),"NEW_SHA",sha(edited),
          "CHANGED_BYTES",len(changed),"OFFSET",pos,"END",end)
    if not apply:
        print("DRY_RUN_ONLY")
        return sha(edited)
    backup = path.with_name(path.name+".pre-10852-return-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
    if backup.exists(): raise FileExistsError(backup)
    backup.write_bytes(raw)
    if backup.read_bytes()!=raw: raise IOError("Backup readback mismatch")
    tmp = path.with_name(path.name+".return-tmp")
    if tmp.exists(): raise FileExistsError(tmp)
    try:
        tmp.write_bytes(edited)
        if tmp.read_bytes()!=edited: raise IOError("Tmp readback mismatch")
        os.replace(tmp,path)
    finally:
        if tmp.exists(): tmp.unlink()
    if path.read_bytes()!=edited: raise IOError("Post-write verify failed")
    print("BACKUP",backup)
    return sha(edited)

if __name__ == "__main__":
    a=argparse.ArgumentParser()
    a.add_argument("file",type=Path)
    a.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
    a.add_argument("--apply",action="store_true")
    q=a.parse_args()
    patch(q.file,q.eve,q.apply)
