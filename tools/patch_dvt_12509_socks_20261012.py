#!/usr/bin/env python3
"""Source-verified DVT Christmas lost-socks quests 12509 steps 5 and 6.

Changes ONLY the two MarkStep END waypoints, never Eve/Lua/quest rewards.
Usage: python tools/patch_dvt_12509_socks_20261012.py FILE [--eve PATH] [--apply]
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

TARGETS = {(12509, 5): (49944, 370, 730, 1, 3),
           (12509, 6): (49944, 370, 730, 1, 3)}
PREDECESSORS = {
    "e0d5135b01ea5331c69384174a6ab0a0db05a7aa1189dfd1b3d7aa94cdbeca35",
    "3521cd4e21059ecac3f2fe9b82de87f6fb892e221997f088673c2f2d21835a37",
}
PATCHED_SHAS = {
    "ab39587a5dacbd65ca60490bf58c5c3da680e826b72b195fc69f80c388f4ec07",
    "8b7cbe97e8ec96a982adb2ed2011d7d816bd9b3648a56ae1e742ab8d55ad8b68",
}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def has_result(c, klass, param, style, value):
    return any((r["type"], r["class"], r["param"], r["pstyle"], r["val"])
               == (0, klass, param, style, value) for r in c["results"])

def verify_source(eve_file):
    scene = EveArchive(Path(eve_file)).parse_scene(49944)
    if scene is None:
        raise ValueError("Missing original Eve49944")
    npc = [n for n in scene["npcs"] if
           (n["id"], n["npc_id"], n["x"], n["y"], n["close"])
           == (3, 38286, 250, 720, False) and 8 in n["events"]]
    if len(npc) != 1 or any(8 in door["events"] for door in scene["doors"]):
        raise ValueError("Original Christmas NPC38286/Eve8 owner missing")
    event = scene["events"][8]
    if event["when_happen"] != [True, False, False, False]:
        raise ValueError("Christmas Eve8 is not a ClickNpc event")
    for stage, consumed_item, increment in ((5, 29451, 2), (6, 29466, 3)):
        matches = [c for c in event["conditions"] if
                   (c["class"], c["param"], c["pstyle"], c["ops"], c["val"])
                   == (2, 12509, 1, 5, stage) and
                   has_result(c, 2, 12509, 1, increment) and
                   has_result(c, 1, consumed_item, 1, -2)]
        if len(matches) != 1:
            raise ValueError(f"Original Christmas step{stage} item consumption and advancement not unique")
    print("SOURCE_PASS scene49944 NPC38286 placement3 ClickNpc Eve8 step5/6")
    return TARGETS

def patch(file, eve_file, apply=False):
    targets = verify_source(eve_file)
    path = Path(file)
    before = path.read_bytes()
    rows = {(s["mission"], s["step"]): s for s in read_steps(path)}
    if [s for (m, s) in rows if m == 12509] != list(range(1, 17)):
        raise ValueError("Unexpected Christmas quest layout")
    old_npc_guide = (49944, 370, 730, 1, 3)
    if rows[(12509, 4)]["end"] != old_npc_guide or rows[(12509, 11)]["end"] != old_npc_guide:
        raise ValueError("Neighboring original Christmas NPC3 navigation not preserved")
    if rows[(12509, 7)]["end"] != (49944, 410, 390, 1, 5):
        raise ValueError("Christmas step7 stage follow-up changed")
    for (mid, stage), target in targets.items():
        step = rows[(mid, stage)]
        if (step["end"] not in ((0, 0, 0, 0, 0), target) or
                any(c[0] for c in step["conditions"])):
            raise ValueError(f"Unknown Christmas quest step target/condition: {(mid,stage)}")
        if not ("tất" in step["description"].lower() or "襪" in step["description"]):
            raise ValueError(f"Christmas socks quest description changed: {stage}")
    refs = scan_packed(before)
    changed = bytearray(before)
    allowed = set()
    for key, dest in sorted(targets.items()):
        ref = refs[(key[0], key[1], "END")]
        start = ref[-1] - 7
        if tuple(ref[:5]) not in ((0,0,0,0,0), dest):
            raise ValueError(f"Unrecognized original packed END {key}")
        changed[start:start + 8] = struct.pack("<HHHBB", *dest)
        allowed.update(range(start, start + 8))
        print("END", key, "OFFSET", start, "DEST", dest)
    if changed == before:
        if sha(before) not in PATCHED_SHAS:
            raise ValueError("Unrecognized complete SHA even though target fields match")
        print("ALREADY_PATCHED", path, sha(before))
        return sha(before)
    if sha(before) not in PREDECESSORS:
        raise ValueError("Unknown complete input SHA; fail closed")
    diff = {i for i, (x,y) in enumerate(zip(before, changed)) if x != y}
    if not diff or not diff.issubset(allowed) or len(changed) != len(before):
        raise ValueError("Out-of-scope binary change")
    verified = scan_packed(changed)
    if any(tuple(verified[(k[0], k[1], "END")][:5]) != target
           for k,target in targets.items()):
        raise ValueError("Independent packed field verification failed")
    print("DIFF_BYTES", len(diff), "OLD_SHA", sha(before), "NEW_SHA", sha(changed))
    if apply:
        backup = path.with_name(path.name + ".pre-12509-socks-" +
                                datetime.now().strftime("%Y%m%d_%H%M%S") + ".bak")
        if backup.exists():
            raise FileExistsError(backup)
        backup.write_bytes(before)
        if sha(backup.read_bytes()) != sha(before):
            raise IOError("Backup sha mismatch")
        temp = path.with_name(path.name + ".12509-socks-tmp")
        if temp.exists():
            raise FileExistsError(temp)
        try:
            temp.write_bytes(changed)
            if sha(temp.read_bytes()) != sha(changed):
                raise IOError("Staging sha mismatch")
            os.replace(temp, path)
        finally:
            if temp.exists():
                temp.unlink()
        if sha(path.read_bytes()) != sha(changed):
            raise IOError("Post-replace hash mismatch")
        print("BACKUP", backup)
    else:
        print("DRY_RUN_ONLY")
    return sha(changed)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("file", type=Path)
    parser.add_argument("--eve", type=Path, default=Path("gamedata/Eve.emg"))
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    patch(args.file, args.eve, args.apply)
