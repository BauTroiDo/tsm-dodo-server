#!/usr/bin/env python3
"""Read-only DVT MarkStep targets vs Eve NPC/door entries; no game/DB mutation.

Usage:
 python tools/audit_dvt_quest_navigation.py --markstep File.dat/Data-ios/IOS_MarkStep_C.dat --eve gamedata/Eve.emg
"""
import argparse
import json
from collections import Counter
from pathlib import Path
from eve_tool import EveArchive


def read_steps(path):
    data = Path(path).read_bytes()
    offset = 0
    def number(size):
        nonlocal offset
        if offset + size > len(data):
            raise ValueError(f"Truncated MarkStep at {offset}")
        value = int.from_bytes(data[offset:offset + size], "little")
        offset += size
        return value
    def txt():
        nonlocal offset
        size = number(2)
        if size % 2 or offset + size > len(data):
            raise ValueError(f"Invalid UTF-16 byte length at {offset}")
        result = data[offset:offset + size].decode("utf-16le")
        offset += size
        return result

    steps = []
    count = number(4)
    for _ in range(count):
        mission = number(2)
        step = number(1)
        description = txt()
        conditions = []
        for condition in range(5):
            kind, item_id, qty = number(1), number(2), number(1)
            target = (number(2), number(2), number(2), number(1), number(1))
            conditions.append((kind, item_id, qty, target))
        guide = txt()
        end_target = (number(2), number(2), number(2), number(1), number(1))
        teleport, team = number(1), number(1)
        steps.append({
            "mission":mission, "step":step, "description": description, "guide":guide,
            "conditions":conditions, "end":end_target,
        })
    if offset != len(data):
        raise ValueError(f"Remaining bytes: {len(data)-offset}")
    return steps


def audit(markstep, eve, only_mission=None):
    steps = read_steps(markstep)
    archive = EveArchive(eve)
    caches = {}
    missing_scenes = []
    missing_npcs = []
    missing_doors = []
    target_counts = Counter()
    def validate(mission, step, target, location):
        scene_id, x, y, kind, event_id = target
        if scene_id <= 0 or (x == 0 and y == 0):
            target_counts["unlocated"] += 1
            return
        target_counts["located"] += 1
        if scene_id not in archive.scenes:
            missing_scenes.append((mission, step, location, scene_id))
            return
        if scene_id not in caches:
            caches[scene_id] = archive.parse_scene(scene_id)
        scene = caches[scene_id]
        if scene is None:
            missing_scenes.append((mission, step, location, scene_id))
            return
        if kind == 1 and event_id:
            target_counts["npc_targets"] += 1
            if event_id not in {n["id"] for n in scene["npcs"]}:
                missing_npcs.append((mission, step, location, scene_id, event_id))
        elif kind == 2 and event_id:
            target_counts["door_targets"] += 1
            if event_id not in {d["id"] for d in scene["doors"]}:
                missing_doors.append((mission, step, location, scene_id, event_id))
        elif kind not in (0, 1, 2):
            target_counts["unsupported_event_kinds"] += 1

    for st in steps:
        mid = st["mission"]
        if only_mission is not None and mid != only_mission:
            continue
        validate(mid, st["step"], st["end"], "END")
        for index, (kind, iid, qty, target) in enumerate(st["conditions"]):
            if kind:
                validate(mid, st["step"], target, f"CONDITION_{index+1}")

    report = {
        "markstep_file": str(markstep),
        "eve_file":str(eve),
        "markstep_rows": len(steps),
        "scenes_loaded":len(caches),
        "targets":dict(target_counts),
        "missing_scenes":missing_scenes,
        "missing_npcs":missing_npcs,
        "missing_doors":missing_doors,
    }
    return report


def main():
    cli=argparse.ArgumentParser()
    cli.add_argument("--markstep", type=Path, default=Path("File.dat/Data-ios/IOS_MarkStep_C.dat"))
    cli.add_argument("--eve", type=Path, default=Path("gamedata/Eve.emg"))
    cli.add_argument("--mission", type=int)
    cli.add_argument("--out", type=Path)
    opts=cli.parse_args()
    result=audit(opts.markstep,opts.eve,opts.mission)
    if opts.out:
        opts.out.write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding="utf-8")
    short={
        k: (v[:30] if isinstance(v,list) else v) for k,v in result.items()
    }
    short["total_missing_scenes"]=len(result["missing_scenes"])
    short["total_missing_npcs"]=len(result["missing_npcs"])
    short["total_missing_doors"]=len(result["missing_doors"])
    print(json.dumps(short,indent=2,ensure_ascii=False))
    # Read-only auditor deliberately returns 0 for missing refs: classify
    # intentional scripted doors before changing the client navigation data.


if __name__ == "__main__":
    main()
