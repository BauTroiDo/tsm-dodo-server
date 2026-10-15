#!/usr/bin/env python3
"""Patch ONLY 7 verified DVT MarkStep navigation targets (not translations).

The server targets were checked against existing NPC/door IDs in Eve.emg.
Default mode only reports. --apply makes an immutable timestamped backup first.

Usage:
 python tools/repair_dvt_client_markstep_20261011.py CLIENT_MARKSTEP SERVER_MARKSTEP
 python tools/repair_dvt_client_markstep_20261011.py CLIENT_MARKSTEP SERVER_MARKSTEP --apply
"""
import argparse
import hashlib
import struct
from datetime import datetime
from pathlib import Path

# (mission, step) -> verified (scene, x, y, kind, eventId).
# 11566/11934 were previously fixed on the Kotlin server to door3.
# 11900 target NPC/doors and scenes exist in BOTH client and server Eve.
VERIFIED = {
    (11566, 2): (25021, 430, 560, 2, 3),
    (11934, 4): (12431, 990, 720, 2, 3),
    (11900, 1): (12861, 1220, 1720, 2, 5),
    (11900, 2): (12581, 2880, 1020, 2, 3),
    (11900, 3): (12584, 560, 360, 2, 4),
    (11900, 4): (12589, 1490, 1020, 1, 17),
    (11900, 12): (12242, 130, 500, 1, 7),
}


def locate_targets(data):
    pos = 0
    result = {}
    def integer(size):
        nonlocal pos
        if pos + size > len(data):
            raise ValueError("Truncated MarkStep input")
        value = int.from_bytes(data[pos:pos + size], "little")
        pos += size
        return value
    def skip_text():
        nonlocal pos
        length = integer(2)
        if length % 2 != 0 or pos + length > len(data):
            raise ValueError("Invalid MarkStep text")
        pos += length
    count = integer(4)
    if not 0 <= count < 100000:
        raise ValueError("Invalid MarkStep record count")
    for _ in range(count):
        mission, step = integer(2), integer(1)
        skip_text()
        pos += 5 * 12  # Lua MarkData.ReadStep condition records
        skip_text()
        start = pos
        pos += 8  # endSceneId/x/y/eventKind/eventId
        pos += 2  # teleport/checkTeam
        if pos > len(data):
            raise ValueError("Truncated MarkStep target")
        key = mission, step
        if key in result:
            raise ValueError(f"Duplicate mission/step {key}")
        result[key] = (start, data[start:start + 8])
    if pos != len(data):
        raise ValueError(f"Trailing bytes after MarkStep: {len(data)-pos}")
    return result


def repair(client_file, server_file, apply=False):
    original = client_file.read_bytes()
    reference = server_file.read_bytes()
    client = locate_targets(original)
    server = locate_targets(reference)
    if set(client) != set(server):
        raise ValueError("Different MarkStep keys; refuse cross-version repair")
    mismatched = {key for key in client if client[key][1] != server[key][1]}
    if not mismatched.issubset(VERIFIED):
        unknown = sorted(mismatched - VERIFIED.keys())
        raise ValueError(f"Unexpected target differences, refusing repair: {unknown[:20]}")
    for key in VERIFIED:
        if key not in client or key not in server:
            raise ValueError(f"Verified target missing: {key}")
        packed = struct.pack("<HHHBB", *VERIFIED[key])
        if server[key][1] != packed:
            raise ValueError(f"Unexpected server target at {key}")
    changed = bytearray(original)
    for key in sorted(mismatched):
        offset, previous = client[key]
        expected = server[key][1]
        if key in {(11566, 2), (11934, 4)}:
            old_id = 7 if key[0] == 11566 else 5
            if previous[:7] != expected[:7] or previous[7] != old_id:
                raise ValueError(f"Wrong legacy door shape at {key}")
        elif previous != bytes(8):
            raise ValueError(f"Unexpected nonblank legacy target at {key}")
        changed[offset:offset + 8] = expected
        print(f"REPAIR {key}: {previous.hex()} -> {expected.hex()}")

    if any(locate_targets(changed)[key][1] != server[key][1] for key in client):
        raise ValueError("Target mismatch remains after repair")
    if apply and mismatched:
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup = client_file.with_name(f"{client_file.name}.before-verified-nav-{stamp}.bak")
        backup.write_bytes(original)
        client_file.write_bytes(changed)
        if client_file.read_bytes() != changed:
            raise IOError("Client file verification failed")
        print("BACKUP", backup)
    print("MODE", "APPLY" if apply else "AUDIT", "mismatch_count", len(mismatched))
    print("SHA256", hashlib.sha256(changed).hexdigest())
    return len(mismatched)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("client", type=Path)
    p.add_argument("server", type=Path)
    p.add_argument("--apply", action="store_true")
    args = p.parse_args()
    repair(args.client, args.server, args.apply)
