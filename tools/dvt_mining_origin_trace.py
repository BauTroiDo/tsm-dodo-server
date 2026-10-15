#!/usr/bin/env python3
"""Read-only audit of observed DVT ORIGINAL mining rewards.

This does NOT assign production rates. Input must come from a real authorized
original-DVT session, with packet trace provenance. Screenshots of a rounded
percent bar are not adequate evidence for reserve deltas.
"""
import argparse
import csv
import json
from collections import defaultdict
from datetime import datetime
from pathlib import Path

WORKERS = ("foreman", "senior", "leader", "regular")
COLUMNS = (
    "source", "capture_ref", "worker", "start_utc", "end_utc",
    "full_before", "full_after", "ts_before", "ts_after",
    "ore_before", "ore_after", "other_ts_events",
)
MAX_ORE = 600_000_000


def read_trials(path):
    rows = []
    with Path(path).open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None or set(COLUMNS) - set(reader.fieldnames):
            raise ValueError("Missing columns: " + ", ".join(sorted(set(COLUMNS) - set(reader.fieldnames or []))))
        for line, record in enumerate(reader, start=2):
            if not any((value or "").strip() for value in record.values()):
                continue
            try:
                source = record["source"].strip()
                ref = record["capture_ref"].strip()
                worker = record["worker"].strip()
                if source != "DVT_ORIGINAL" or not ref:
                    raise ValueError("requires DVT_ORIGINAL with original packet capture reference")
                if worker not in WORKERS:
                    raise ValueError("unknown worker; use " + "/".join(WORKERS))
                start = datetime.fromisoformat(record["start_utc"].strip().replace("Z", "+00:00"))
                end = datetime.fromisoformat(record["end_utc"].strip().replace("Z", "+00:00"))
                if start.tzinfo is None or end.tzinfo is None or end <= start:
                    raise ValueError("timestamps must be timezone-aware and increasing")
                values = {key: int(record[key]) for key in
                          ("full_before", "full_after", "ts_before", "ts_after", "ore_before", "ore_after")}
                if (min(values.values()) < 0 or
                    values["full_before"] > 65535 or values["full_after"] > 65535 or
                    values["ore_before"] > MAX_ORE or values["ore_after"] > MAX_ORE or
                    values["ts_before"] > 999_999_999 or values["ts_after"] > 999_999_999):
                    raise ValueError("invalid DVT wire state range")
                if record["other_ts_events"].strip().lower() not in ("yes", "no"):
                    raise ValueError("other_ts_events must be yes/no")
                rows.append({
                    "worker": worker,
                    "capture_ref": ref,
                    "start_utc": start.isoformat(),
                    "end_utc": end.isoformat(),
                    "fuel_spent": values["full_before"] - values["full_after"],
                    "ts_delta": values["ts_after"] - values["ts_before"],
                    "ore_delta": values["ore_before"] - values["ore_after"],
                    "contaminated": record["other_ts_events"].strip().lower() == "yes",
                })
            except (KeyError, ValueError, TypeError) as exc:
                raise ValueError(f"CSV line {line}: {exc}") from exc
    return rows


def summarize(trials):
    """Report observations, not a source-proven yield formula."""
    groups = defaultdict(list)
    rejected = []
    for row in trials:
        if row["contaminated"] or row["fuel_spent"] <= 0 or row["ts_delta"] < 0 or row["ore_delta"] < 0:
            rejected.append(row)
        else:
            groups[row["worker"]].append(row)
    report = {
        "status": "OBSERVATIONS_ONLY_NOT_A_VERIFIED_DVT_RATE",
        "captured_trials": len(trials),
        "accepted_trials": sum(len(v) for v in groups.values()),
        "rejected_trials": len(rejected),
        "needs_original_packet_provenance_review": True,
        "worker_observations": {},
    }
    for worker in WORKERS:
        observations = groups[worker]
        ts_deltas = [r["ts_delta"] for r in observations]
        ore_deltas = [r["ore_delta"] for r in observations]
        report["worker_observations"][worker] = {
            "count": len(observations),
            "ts_gain_observed": sorted(set(ts_deltas)),
            "ore_debit_observed": sorted(set(ore_deltas)),
            "ts_delta_range": [min(ts_deltas), max(ts_deltas)] if ts_deltas else None,
            "fuel_spent": [r["fuel_spent"] for r in observations],
            "sufficient_repeated_samples": len(observations) >= 3,
            "constant_observed_ts_gain": len(set(ts_deltas)) == 1 if len(observations) >= 3 else None,
        }
    report["ready_to_code_exact_original_formula"] = False
    report["next_evidence"] = (
        "Check provenance and raw S035-050(kind=8), S170-001/002(UInt64) "
        "alongside miner switch/day limit. Repeated observations alone do not prove rate/RNG/reset."
    )
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", help="Raw observations from original DVT, NEVER an emulated DEV")
    args = parser.parse_args()
    report = summarize(read_trials(args.csv))
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

[executed on device: DESKTOP-Q96IFHO (32ea4e17-03d0-439c-bd47-fee62bd241c0)]