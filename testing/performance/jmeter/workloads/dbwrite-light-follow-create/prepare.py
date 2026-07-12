#!/usr/bin/env python3
"""Prepare follow-create JMeter payload.

Generates a single CSV:
  - payload/cohort-user-ids.csv  — column `id`, the seeded user cohort in n-order

The JMeter plan (plan.jmx) derives each (follower, followed) pair per iteration from a
global monotonic counter indexed into this cohort, so the payload is inexhaustible: a
cell can never run dry and wrap into already-inserted (duplicate) follows, and follower
and followed are taken from the one array at deterministic offsets so they can never
misalign. This replaces the old two-CSV scheme, which exhausted at `count` rows (a 90 s
cell at >~11k rps consumes >1M pairs and then measured duplicate-swallow no-ops, not
inserts) and whose two independent `shareMode.all` iterators could drift under load.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_JMETER_DIR = _HERE.parents[1]
sys.path.insert(0, str(_JMETER_DIR / "lib"))

from payload_lib import (  # noqa: E402
    fetch_user_ids,
    normalize_bool,
    positive_int,
    repo_path,
    write_csv,
)


def prepare(args: argparse.Namespace) -> None:
    out_dir = repo_path(args.output_dir) if args.output_dir else (_HERE / "payload")
    ids = fetch_user_ids(args.count, args.seed_users)

    cohort_output = out_dir / "cohort-user-ids.csv"
    write_csv(cohort_output, ["id"], [[value] for value in ids])

    print("Prepared JMeter payload:")
    print("  workload=follow-create")
    print(f"  count={args.count}")
    print(f"  cohort_user_ids_file={cohort_output}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare follow-create JMeter payload.")
    parser.add_argument("--count", type=positive_int, required=True)
    parser.add_argument("--output-dir", help="Output directory for generated CSV files (default: workload dir / payload).")
    parser.add_argument("--seed-users", type=normalize_bool, default=False)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    prepare(args)


if __name__ == "__main__":
    main()
