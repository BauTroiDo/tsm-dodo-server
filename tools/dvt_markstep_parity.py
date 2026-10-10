"""Read-only parity audit of DVT MarkStep binaries and Unity TextAssets.

Wire order follows native DVT115 Data/MarkData.lua: MarkData.ReadStep.
Localized description/endGuide are excluded from structural comparisons.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path


def parse(data):
    position = 0

    def take(size):
        nonlocal position
        if size < 0 or position + size > len(data):
            raise ValueError(f"Truncated MarkStep at byte {position}: need {size}")
        value = data[position:position + size]
        position += size
        return value

    def number(fmt):
        values = struct.unpack("<" + fmt, take(struct.calcsize("<" + fmt)))
        return values[0] if len(values) == 1 else list(values)

    def text():
        size = number("H")
        if size % 2:
            raise ValueError(f"Odd UTF-16 byte length at {position - 2}")
        return take(size).decode("utf-16le")

    count = number("I")
    # Each record needs 77 bytes even with both strings empty.
    if count > (len(data) - 4) // 77:
        raise ValueError("Record count exceeds available bytes")
    rows = {}
    for _ in range(count):
        start = position
        key = (number("H"), number("B"))
        description = text()
        conditions = [number("BHBHHHBB") for _ in range(5)]
        guide = text()
        end = number("HHHBBBB")
        if key in rows:
            raise ValueError(f"Duplicate mission/step {key}")
        rows[key] = dict(description=description, guide=guide,
                         conditions=conditions, end=end,
                         offset=start, length=position - start)
    if position != len(data):
        raise ValueError(f"Trailing bytes: parsed {position} of {len(data)}")
    return rows


def structural(row):
    return {field: row[field] for field in ("conditions", "end")}


def repair_navigation(data):
    """Correct two proven DoorData.id references; never change Eve event IDs.

    Active PC and server Eve SHA256 03c84cb5...c40b910 have Door3 at
    25021 (440,580), events=[7], and 12431 (1020,720), events=[6].
    The original guide positions and every other field remain unchanged.
    """
    rows = parse(data)
    fixed = bytearray(data)
    anchors = {(11566, 2): [25021, 430, 560, 2, 7, 1, 0],
               (11934, 4): [12431, 990, 720, 2, 5, 1, 0]}
    for key, expected in anchors.items():
        row = rows.get(key)
        corrected = expected[:]
        corrected[4] = 3
        if row is None or row["end"] not in (expected, corrected):
            raise ValueError(f"Unexpected navigation anchor for {key}")
        # endSceneId(2), X(2), Y(2), kind(1), id(1), teleport(1), team(1).
        fixed[row["offset"] + row["length"] - 3] = 3
    result = bytes(fixed)
    parse(result)
    return result


def compare(client_data, server_data):
    client, server = parse(client_data), parse(server_data)
    common = client.keys() & server.keys()
    changed = [key for key in sorted(common)
               if structural(client[key]) != structural(server[key])]
    def keys(values):
        return [list(key) for key in sorted(values)]
    return dict(
        client_sha256=hashlib.sha256(client_data).hexdigest(),
        server_sha256=hashlib.sha256(server_data).hexdigest(),
        client_records=len(client), server_records=len(server),
        common_records=len(common),
        client_only=keys(client.keys() - server.keys()),
        server_only=keys(server.keys() - client.keys()),
        structural_differences=[dict(mission=key[0], step=key[1],
                                    client=structural(client[key]),
                                    server=structural(server[key])) for key in changed],
        watched_targets=[dict(mission=key[0], step=key[1],
                              client=structural(client[key]) if key in client else None,
                              server=structural(server[key]) if key in server else None)
                         for key in ((11566, 2), (11934, 4))],
    )


def extract(path):
    import UnityPy  # Optional; direct .dat comparison uses only stdlib.
    matches = []
    for obj in UnityPy.load(str(path)).objects:
        if obj.type.name == "TextAsset":
            asset = obj.read()
            if asset.m_Name == "IOS_MarkStep_C":
                value = asset.m_Script
                matches.append(value.encode("utf-8", "surrogateescape")
                               if isinstance(value, str) else bytes(value))
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one IOS_MarkStep_C TextAsset, got {len(matches)}")
    parse(matches[0])  # Verify original bytes and EOF before comparison.
    return matches[0]


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    source = cli.add_mutually_exclusive_group(required=True)
    source.add_argument("--client-dat", type=Path)
    source.add_argument("--client-assets", type=Path)
    cli.add_argument("--server-dat", type=Path, required=True)
    cli.add_argument("--report", type=Path, required=True)
    cli.add_argument("--fixed-client-dat", type=Path,
                     help="Write a separate candidate with the two verified Door ID repairs")
    cli.add_argument("--fixed-server-dat", type=Path)
    args = cli.parse_args()
    inputs = {path.resolve() for path in (args.client_dat, args.client_assets, args.server_dat) if path}
    outputs = [path.resolve() for path in (args.report, args.fixed_client_dat, args.fixed_server_dat) if path]
    if len(set(outputs)) != len(outputs) or inputs.intersection(outputs):
        cli.error("Outputs must be distinct and must not overwrite any input")
    client = extract(args.client_assets) if args.client_assets else args.client_dat.read_bytes()
    server = args.server_dat.read_bytes()
    report = compare(client, server)
    candidates = [(args.fixed_client_dat, repair_navigation(client))] if args.fixed_client_dat else []
    if args.fixed_server_dat:
        candidates.append((args.fixed_server_dat, repair_navigation(server)))
    for path, data in candidates:
        path.write_bytes(data)
    report["client_source"] = str(args.client_assets or args.client_dat)
    report["server_source"] = str(args.server_dat)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Client {report['client_records']}, server {report['server_records']}, "
          f"common {report['common_records']}, structural differences "
          f"{len(report['structural_differences'])}; report: {args.report}")


if __name__ == "__main__":
    main()

