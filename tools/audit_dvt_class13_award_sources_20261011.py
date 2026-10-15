#!/usr/bin/env python3
"""Read-only DVT Eve class13 ↔ ActivityAward structural coverage audit.

DO NOT treat any activity award row as a decoded Eve class13 grant.
This tool deliberately does not generate inventory or quest mutations.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct

from eve_tool import EveArchive

# Observation only. Source layout = 4-byte row count followed by fixed-size
# rows of 2+2+1+4+1+2+2+4 bytes. These are unnamed until authoritative client
# Lua/native semantics for the fields and class13 pStyle are recovered.
RECORD = struct.Struct("<HHBIBHHI")


def audit(eve_path: Path, award_path: Path) -> dict:
    eve_hash = hashlib.sha256(eve_path.read_bytes()).hexdigest()
    blob = award_path.read_bytes()
    if len(blob) < 4:
        raise ValueError("ActivityAward file shorter than row count")
    count = struct.unpack_from("<I", blob)[0]
    if count <= 0 or len(blob) != 4 + count * RECORD.size:
        raise ValueError("ActivityAward row count or record width mismatch")
    records = [RECORD.unpack_from(blob, 4 + i * RECORD.size)
               for i in range(count)]

    classes = []
    archive = EveArchive(eve_path)
    for scene_id in sorted(archive.scenes):
        scene = archive.parse_scene(scene_id)
        for eve_no, event in scene["events"].items():
            for condition in event["conditions"]:
                for result in condition["results"]:
                    if result["class"] == 13 and result["type"] in (0, 7):
                        classes.append({
                            "scene": scene_id, "eve": eve_no,
                            "condition": condition["no"],
                            "style": result["pstyle"],
                            "value": result["val"],
                            "parameter": result["param"],
                        })
    values = {r["value"] for r in classes}
    leading = {rec[0] for rec in records}
    second = {rec[1] for rec in records}
    by_scene = Counter(r["scene"] for r in classes)
    styles = Counter(r["style"] for r in classes)
    return {
        "eve_sha256": eve_hash,
        "award_sha256": hashlib.sha256(blob).hexdigest(),
        "award_rows": count,
        "award_record_bytes": RECORD.size,
        "eve_class13_count": len(classes),
        "eve_class13_distinct_values": len(values),
        "eve_class13_styles": dict(sorted(styles.items())),
        "eve_class13_scenes": len(by_scene),
        "eve_class13_scene12003": by_scene[12003],
        "award_leading_field_distinct": len(leading),
        "award_second_field_distinct": len(second),
        "class13_values_unmatched_as_award_leading_field": sorted(values - leading),
        "class13_values_unmatched_as_award_second_field": sorted(values - second),
        "award_first_row": list(records[0]),
        "award_last_row": list(records[-1]),
        "safe_to_grant_from_this_mapping": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--eve", type=Path, default=Path("gamedata/Eve.emg"))
    parser.add_argument("--award", type=Path,
                        default=Path("File.dat/Data-ios/IOS_ActivityAward_C.dat"))
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    report = audit(args.eve, args.award)
    result = json.dumps(report, ensure_ascii=False, indent=2)
    if args.out:
        args.out.write_text(result + "\n", encoding="utf-8")
    print(result)


if __name__ == "__main__":
    main()
