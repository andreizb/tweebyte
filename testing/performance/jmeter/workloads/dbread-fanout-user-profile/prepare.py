#!/usr/bin/env python3
"""Prepare user-profile JMeter payload.

The workload exercises the user-profile read (GET /users/{id}), which fans out
from user-service to tweet-service (getUserTweets) and interaction-service
(getFollowingCount / getFollowersCount) — not the trivial single-row summary
read. Generates `payload/user-ids.csv` from user_service_db.

With --seed-users 1, creates the benchmark cohort (deterministic UUIDs from
md5('jmeter-benchmark-user:'||n)) AND seeds the downstream data the profile
fan-out needs: one tweet per user and a follow ring (every user gets exactly
one following and one follower). With --seed-users 0, reads existing user IDs
and assumes the downstream data is already present.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Workload dirs live at testing/performance/jmeter/workloads/<name>/.
# Add the JMeter lib dir to sys.path so we can import the shared payload helpers.
_HERE = Path(__file__).resolve().parent
_JMETER_DIR = _HERE.parents[1]
sys.path.insert(0, str(_JMETER_DIR / "lib"))

from payload_lib import (  # noqa: E402
    ensure_benchmark_follows,
    ensure_benchmark_interactions,
    ensure_benchmark_tweets,
    fetch_user_ids,
    normalize_bool,
    positive_int,
    repo_path,
    seed_benchmark_companion_summary_cache,
    write_csv,
)


def prepare(args: argparse.Namespace) -> None:
    if args.seed_companion_cache_only:
        seed_benchmark_companion_summary_cache()
        return

    if args.count is None:
        raise SystemExit("--count is required unless --seed-companion-cache-only is used")

    out_dir = repo_path(args.output_dir) if args.output_dir else (_HERE / "payload")
    ids = fetch_user_ids(args.count, args.seed_users)
    if args.seed_users:
        # The profile endpoint is only meaningful when the cohort has downstream
        # data: one tweet per user (tweet-service fan-out), a follow ring
        # (interaction-service following/followers counts), and one of every
        # interaction on that tweet (like/reply/reply-like/retweet) so the
        # enriched counts the profile reads are non-empty and cache-warm.
        ensure_benchmark_tweets(args.count, tweets_per_user=1)
        ensure_benchmark_follows(args.count)
        ensure_benchmark_interactions(args.count, tweets_per_user=1)
    output = out_dir / "user-ids.csv"
    write_csv(output, ["id"], [[value] for value in ids])

    print("Prepared JMeter payload:")
    print("  workload=user-profile (profile read GET /users/{id})")
    print(f"  count={args.count}")
    print(f"  user_ids_file={output}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare user-profile JMeter payload.")
    parser.add_argument("--count", type=positive_int)
    parser.add_argument("--output-dir", help="Output directory for generated CSV files (default: workload dir / payload).")
    parser.add_argument("--seed-users", type=normalize_bool, default=False)
    parser.add_argument(
        "--seed-companion-cache-only",
        action="store_true",
        help="seed users::<benchmark-companion-id> in Redis with TTL=-1, then exit",
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()
    prepare(args)


if __name__ == "__main__":
    main()
