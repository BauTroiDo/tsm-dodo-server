#!/usr/bin/env python3
"""Read-only quest blocker / target gap triage against active DVT Eve & MarkStep.

Never alters live database, mission flags, player items or binary input.
"""
import argparse
import collections
import hashlib
import json
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import read_steps

UNSUPPORTED_EFFECTS = {11: "city_resource", 13: "activity_reward"}
UNSUPPORTED_CONDITIONS = {13: "city_identity", 17: "child_lifecycle"}


def classify_dvt_class14(condition: dict) -> str:
    """Classify exact original time shape, not all 279 as unsupported.

    Original DVT Lua Data/Eve/Eve_NpcEventData.lua reads CTimeValue as
    ReadDouble(); EveConditionEvaluator handles only param0/style0 and
    OLE calendar operations 1..4 with a finite positive cutoff.
    The remaining param2/style1..4/ops0 are friend-delivery NPC22063
    (12001/Eve64), NOT regular event clock comparisons.
    """
    import math
    if (
        condition["param"] == 0 and condition["pstyle"] == 0
        and condition["ops"] in (1, 2, 3, 4)
        and condition["val"] == 0 and condition["sub_item"] == 0
        and math.isfinite(condition["time_value"])
        and condition["time_value"] > 0.0
    ):
        return "supported_ole_time"
    return "unverified_special_time"



def scan(eve_file: Path, markstep_file: Path) -> dict:
    digest = hashlib.sha256(eve_file.read_bytes()).hexdigest()
    eve = EveArchive(eve_file)
    steps = read_steps(markstep_file)
    mission_ids = {s["mission"] for s in steps}
    count = collections.Counter()
    markers = {"result": [], "condition": []}
    by_mission = collections.defaultdict(lambda: {
        "blocked_effects": set(), "blocked_conditions": set(),
        "scenes": set(), "events": set()})
    coverage_by_scene = collections.defaultdict(set)
    for sid in sorted(eve.scenes):
        scene = eve.parse_scene(sid)
        if not scene: continue
        count["indexed_scenes"] += 1
        for event_no, ev in scene["events"].items():
            involved = set()
            for cond in ev["conditions"]:
                if cond["class"] == 2 and cond["param"] in mission_ids:
                    involved.add(cond["param"])
                for effect in cond["results"]:
                    if effect["class"] == 2 and effect["param"] in mission_ids:
                        involved.add(effect["param"])
            for cond in ev["conditions"]:
                cno = cond["no"]
                linked = sorted(involved)
                if cond["class"] == 14:
                    classification = classify_dvt_class14(cond)
                    count["cond_14"] += 1  # complete source census, not blocked count
                    count["cond_14_" + classification] += 1
                if (cond["class"] in UNSUPPORTED_CONDITIONS or
                        (cond["class"] == 14 and
                         classification == "unverified_special_time")):
                    name = ("unverified_special_time" if cond["class"] == 14
                            else UNSUPPORTED_CONDITIONS[cond["class"]])
                    if cond["class"] != 14:
                        count["cond_"+str(cond["class"])] += 1
                    row = {
                        "scene": sid, "event": event_no, "condition": cno,
                        "class": cond["class"], "param": cond["param"],
                        "style": cond["pstyle"], "ops": cond["ops"],
                        "value": cond["val"],
                        "time_value": cond["time_value"],
                        "sub_item": cond["sub_item"],
                        "linked_markstep_missions": linked}
                    markers["condition"].append(row)
                    for mid in linked:
                        by_mission[mid]["blocked_conditions"].add(name)
                        by_mission[mid]["scenes"].add(sid)
                        by_mission[mid]["events"].add(f"{sid}/{event_no}")
                    coverage_by_scene[sid].add(name)
                for ix, result in enumerate(cond["results"]):
                    if result["type"] not in (0, 7): continue
                    if result["class"] in UNSUPPORTED_EFFECTS:
                        name = UNSUPPORTED_EFFECTS[result["class"]]
                        count["result_"+str(result["class"])] += 1
                        row = {
                            "scene": sid, "event": event_no, "condition": cno,
                            "result_index": ix, "type": result["type"],
                            "class": result["class"], "param": result["param"],
                            "style": result["pstyle"], "value": result["val"],
                            "linked_markstep_missions": linked}
                        markers["result"].append(row)
                        coverage_by_scene[sid].add(name)
                        for mid in linked:
                            by_mission[mid]["blocked_effects"].add(name)
                            by_mission[mid]["scenes"].add(sid)
                            by_mission[mid]["events"].add(f"{sid}/{event_no}")
    blank_end = []
    unlocated_objectives = []
    for step in steps:
        end = step["end"]
        if end[0] == 0 or (end[1] == 0 and end[2] == 0):
            meaningful = [(i+1,kind,item_id,qty,tgt)
                          for i,(kind,item_id,qty,tgt) in enumerate(step["conditions"])
                          if kind != 0]
            blank_end.append({
                "mission": step["mission"], "step": step["step"],
                "conditions": len(meaningful),
                "located_conditions": sum(1 for (_,_,_,_,x) in meaningful
                                          if x[0] != 0 and (x[1] or x[2])),
                "guide_nonempty": bool(step["guide"].strip()),
                "description_nonempty": bool(step["description"].strip())})
        for i,(kind,item_id,qty,tgt) in enumerate(step["conditions"]):
            if kind and (tgt[0] == 0 or (tgt[1] == 0 and tgt[2] == 0)):
                unlocated_objectives.append({
                    "mission": step["mission"], "step": step["step"],
                    "condition_index": i+1, "kind": kind, "id": item_id,
                    "required": qty})
    result = {
        "eve_file": str(eve_file),
        "eve_sha256": digest,
        "markstep_file": str(markstep_file),
        "total_marksteps": len(steps),
        "markstep_missions": len(mission_ids),
        "counters": dict(count),
        "scene_coverage": {str(k): sorted(v) for k,v in
                          sorted(coverage_by_scene.items())},
        "potentially_blocked_missions": {
            str(k): {
                "blocked_effects": sorted(v["blocked_effects"]),
                "blocked_conditions": sorted(v["blocked_conditions"]),
                "scenes": sorted(v["scenes"]),
                "events": sorted(v["events"])}
            for k,v in sorted(by_mission.items())
        },
        "missing_end_targets": blank_end,
        "unlocated_condition_targets": unlocated_objectives,
        "blockers": markers,
    }
    return result


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--eve", type=Path, default=Path("gamedata/Eve.emg"))
    p.add_argument("--markstep", type=Path,
                   default=Path("File.dat/Data-ios/IOS_MarkStep_C.dat"))
    p.add_argument("--out", type=Path)
    args = p.parse_args()
    r = scan(args.eve, args.markstep)
    if args.out:
        args.out.write_text(json.dumps(r, ensure_ascii=False, indent=2),
                            encoding="utf-8")
    linked = list(r["potentially_blocked_missions"])
    print(json.dumps({
        "sha": r["eve_sha256"], "mission_step_rows": r["total_marksteps"],
        "mission_ids": r["markstep_missions"],
        "count": r["counters"],
        "scenes_with_blockers": len(r["scene_coverage"]),
        "potentially_affected_missions": len(linked),
        "mission_id_sample": linked[:55],
        "blank_end_targets": len(r["missing_end_targets"]),
        "blank_end_no_objectives": sum(x["conditions"] == 0
            for x in r["missing_end_targets"]),
        "unlocated_objectives": len(r["unlocated_condition_targets"]),
        "unlocated_objective_sample": r["unlocated_condition_targets"][:25]
    },ensure_ascii=False,indent=2))


if __name__ == "__main__":
    main()
