#!/usr/bin/env python3
"""Source-verified DVT mission10852 stage1 route to first reachable Moon Rabbit.

The client gets to cave scene10984 through real NPC15153 in scene11021.
Among four stage1 rabbit battle triggers in arrival scene10984, pick the
closest one to the exact Eve teleport arrival (3780,2820).
Do not alter other rabbit NPCs or create any quest reward.
"""
import argparse,hashlib,math,os,struct
from datetime import datetime
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps
from repair_dvt_mission_door_refs_20261011 import scan

MISSION=10852
STEP=1
RABBIT_NPC_ID=38022
CAVE=10984
ENTRY_SCENE=11021
ENTRY_NPC_ID=15153
ENTRY_PLACEMENT=7
ENTRY_WARP_SCENE_INFO=5
ARRIVAL=(3780,2820)
RABBIT=(3710,2300)
RABBIT_PLACEMENT=3
APPROVED_ORIGINALS={
 "8f37189491c21ab6f59973ae9a86dbdb279b4276485ef420a85b0bcd0b7bc115",
 "da1eefd8914cfd13fe251c8397add11102dfee305fb4ed92a854b13faabd51fc",
}

def sha(raw):return hashlib.sha256(raw).hexdigest()

def verified_target(archive:EveArchive):
    town=archive.parse_scene(ENTRY_SCENE)
    entry=town["scene_infos"].get(ENTRY_WARP_SCENE_INFO)
    if not entry or (entry["target_scene"],entry["target_x"],entry["target_y"])!=(CAVE,*ARRIVAL):
        raise ValueError("Original DVT city NPC entrance does not lead to the verified cave")
    owners=[n for n in town["npcs"] if n["id"]==ENTRY_PLACEMENT and n["npc_id"]==ENTRY_NPC_ID]
    if len(owners)!=1:raise ValueError("Original city entry NPC missing")
    # The quest-conditioned branch opens an original NPC dialogue choice.
    # Only the subsequent choice branch (same NPC event) actually warps.
    # Do not incorrectly require both operations in one condition row.
    has_quest_stage=False
    has_choice_warp=False
    for no in owners[0]["events"]:
        conds=town["events"][no]["conditions"]
        has_quest_stage |= any(
            c["class"]==2 and c["param"]==MISSION and
            c["pstyle"]==1 and c["val"]==1 and
            any(r["type"]==6 for r in c["results"])
            for c in conds
        )
        has_choice_warp |= any(
            c["class"]==10 and c["param"]==7 and
            c["pstyle"]==30 and
            any(r["type"]==2 and r["class"]==7 and
                r["param"]==ENTRY_WARP_SCENE_INFO for r in c["results"])
            for c in conds
        )
    if not (has_quest_stage and has_choice_warp):
        raise ValueError("Original NPC does not offer the stage1 dialogue and cave choice warp")
    cave=archive.parse_scene(CAVE)
    rabbits=[n for n in cave["npcs"] if n["npc_id"]==RABBIT_NPC_ID and not n["close"]
             and any(c["class"]==2 and c["param"]==MISSION and c["pstyle"]==1
                     and c["val"]==0 and any(r["type"]==3 for r in c["results"])
                     for no in n["events"] for c in cave["events"][no]["conditions"])]
    if len(rabbits)!=4:raise ValueError(f"Unexpected rabbit battle choices: {len(rabbits)}")
    ranked=sorted(rabbits,key=lambda n:(math.dist(ARRIVAL,(n["x"],n["y"])),n["id"]))
    winner=ranked[0]
    if (winner["id"],winner["x"],winner["y"])!=(RABBIT_PLACEMENT,*RABBIT):
        raise ValueError("Closest original rabbit changed, stop before overriding a player route")
    return (CAVE,*RABBIT,1,RABBIT_PLACEMENT)

def patch(path:Path,eve:Path,apply=False):
    old=path.read_bytes()
    steps=[s for s in read_steps(path) if (s["mission"],s["step"])==(MISSION,STEP)]
    if len(steps)!=1:raise ValueError(f"Expected one MarkStep, found {len(steps)}")
    st=steps[0]
    if st["end"]!=(0,0,0,0,0) or st["conditions"][0][:3]!=(1,RABBIT_NPC_ID,1):
        raise ValueError("Original rabbit capture stage differs")
    target=verified_target(EveArchive(eve))
    ref=scan(old)[(MISSION,STEP,"CONDITION_1")]
    if ref[:5]==target:
        print("ALREADY_FIXED",path,sha(old),target)
        return sha(old)
    if ref[:5]!=(0,0,0,0,0):raise ValueError(f"Unexpected existing target: {ref}")
    if sha(old) not in APPROVED_ORIGINALS:raise ValueError(f"Unknown edition hash: {sha(old)}")
    at=ref[-1]-7
    new=bytearray(old)
    new[at:at+8]=struct.pack("<HHHBB",*target)
    changed={i for i,(a,b) in enumerate(zip(old,new)) if a!=b}
    if not changed or not changed.issubset(set(range(at,at+8))) or len(new)!=len(old):
        raise ValueError(f"Unexpected byte changes {changed}")
    print("SOURCE",path,"ORIGINAL_SHA",sha(old),"NEW_SHA",sha(new),"CHANGED_BYTES",len(changed),
          "TARGET",target,"OFFSET",at)
    if not apply:
        print("DRY_RUN_ONLY")
        return sha(new)
    backup=path.with_name(path.name+".pre-dvt-10852-cave-rabbit-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
    if backup.exists():raise FileExistsError(backup)
    backup.write_bytes(old)
    if backup.read_bytes()!=old:raise IOError("Backup mismatch")
    staged=path.with_name(path.name+".dvt10852.tmp")
    if staged.exists():raise FileExistsError(staged)
    try:
        staged.write_bytes(new)
        if staged.read_bytes()!=new:raise IOError("Atomic temp mismatch")
        os.replace(staged,path)
    finally:
        if staged.exists():staged.unlink()
    if path.read_bytes()!=new:raise IOError("Verification failed")
    print("APPLIED",path,"BACKUP",backup)
    return sha(new)

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("file",type=Path)
    p.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
    p.add_argument("--apply",action="store_true")
    a=p.parse_args()
    patch(a.file,a.eve,a.apply)
