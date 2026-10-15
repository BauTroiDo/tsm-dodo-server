#!/usr/bin/env python3
"""Repair 10 provably wrong DVT MarkStep door IDs; never renumber NPCs/quests.

Source parity:
- MarkData.ReadStep: end/condition eventKind=2 uses DoorManager.doors[eventId].
- Eve.emg: the DVT MarkStep IDs for missions 11908/12438 are
  Eve event numbers, not door record IDs; coordinates & unique Eve
  event mapping identify the actual doors.
- mission 12682 step1: marker points to door2 location in scene27802,
  and door2 event7 checks mission12682; step2 already targets door2.

Default is inspect-only; --apply creates a byte-for-byte backup and then
atomically replaces the input. No Eve/DB/player-state writes.
"""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path

# (mission, step, location): (scene, x, y, eventKind, incorrectID, doorID)
KNOWN = {
    (11908, 1, "END"): (23872, 450, 880, 2, 8, 3),
    (11908, 2, "END"): (23872, 1030, 800, 2, 9, 4),
    (11908, 3, "END"): (23872, 570, 480, 2, 10, 5),
    (11908, 4, "END"): (23873, 2100, 1140, 2, 6, 2),
    (11908, 5, "END"): (23873, 630, 540, 2, 7, 3),
    (12438, 2, "END"): (11431, 830, 400, 2, 5, 3),
    (12438, 3, "END"): (11431, 830, 400, 2, 5, 3),
    (12438, 4, "END"): (11520, 230, 260, 2, 4, 2),
    (12438, 5, "CONDITION_1"): (11520, 230, 260, 2, 4, 2),
    (12682, 1, "END"): (27802, 1290, 593, 2, 5, 2),
}


def scan(source: bytes):
    pos = 0
    refs = {}
    def number(size):
        nonlocal pos
        if pos + size > len(source):
            raise ValueError(f"Truncated MarkStep at {pos}")
        value = int.from_bytes(source[pos:pos+size], "little")
        pos += size
        return value
    def text():
        nonlocal pos
        size = number(2)
        if size % 2 or pos + size > len(source):
            raise ValueError(f"Invalid UTF16 length at {pos}: {size}")
        pos += size
    def target(mid, step, loc):
        nonlocal pos
        at = pos
        scene, x, y, kind, eid = struct.unpack_from("<HHHBB", source, at)
        pos += 8
        refs[mid,step,loc] = (scene,x,y,kind,eid,at+7)

    count = number(4)
    if count != 4505:
        raise ValueError(f"Unexpected DVT MarkStep version with {count} rows")
    for _ in range(count):
        mid, step = number(2), number(1)
        text()
        for j in range(5):
            pos += 4  # kind(1), id(2), required count(1)
            target(mid,step,f"CONDITION_{j+1}")
        text()
        target(mid,step,"END")
        pos += 2  # teleport, checkTeam
        if pos > len(source):
            raise ValueError("Truncated MarkStep record")
    if pos != len(source):
        raise ValueError(f"Unconsumed MarkStep data {len(source)-pos} bytes")
    return refs


def repair(path: Path, apply: bool):
    before = path.read_bytes()
    refs = scan(before)
    changed = bytearray(before)
    edits = []
    already = []
    for key, (scene,x,y,kind,old,new) in KNOWN.items():
        if key not in refs:
            raise ValueError(f"Missing required MarkStep target: {key}")
        actual = refs[key]
        if actual[:4] != (scene,x,y,kind):
            raise ValueError(f"Unexpected target location {key}: {actual[:5]}")
        current = actual[4]
        if current == new:
            already.append(key)
            continue
        if current != old:
            raise ValueError(f"Unrecognized event ID {key}: {current}")
        changed[actual[5]] = new
        edits.append((key,old,new,actual[5]))

    expected = {k: v[-1] for k,v in KNOWN.items()}
    after_refs = scan(bytes(changed))
    if any(after_refs[k][4] != expected[k] for k in expected):
        raise ValueError("Repair verification failed")
    differing = [i for i,(a,b) in enumerate(zip(before,changed)) if a != b]
    if set(differing) != {i for _,_,_,i in edits}:
        raise ValueError("Repair touched unexpected bytes")
    print("INPUT",path,"SHA256",hashlib.sha256(before).hexdigest())
    print("ALREADY_CORRECT",len(already))
    for key,old,new,offset in edits:
        print("DOOR_FIX",key,old,"->",new,"offset",offset)
    if apply and edits:
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup = path.with_name(path.name+f".pre-dvt-10-doors-{stamp}.bak")
        if backup.exists():
            raise FileExistsError(backup)
        backup.write_bytes(before)
        if backup.read_bytes() != before:
            raise IOError("Backup verification failed")
        tmp = path.with_name(path.name+".repair-tmp")
        if tmp.exists():
            raise FileExistsError(tmp)
        try:
            tmp.write_bytes(changed)
            if tmp.read_bytes() != changed:
                raise IOError("Staging verification failed")
            os.replace(tmp,path)
        finally:
            if tmp.exists():
                tmp.unlink()
        if path.read_bytes() != changed:
            raise IOError("Post-repair mismatch; restore from the printed backup!")
        print("BACKUP",backup)
    print("MODE","APPLIED" if apply else "READ_ONLY","EDIT_COUNT",len(edits))
    print("FINAL_SHA256",hashlib.sha256(changed).hexdigest())
    return len(edits)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("file",type=Path)
    p.add_argument("--apply",action="store_true")
    args = p.parse_args()
    repair(args.file,args.apply)
