#!/usr/bin/env python3
"""Prepare tweets-get JMeter payload.

Generates `payload/user-ids.csv` with the `id` column populated by the
distinct user_ids from tweet_service_db.tweets. With --seed-tweets 1,
also TRUNCATEs and re-seeds benchmark tweets at `--tweets-per-user` per
user before reading.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_JMETER_DIR = _HERE.parents[1]
sys.path.insert(0, str(_JMETER_DIR / "lib"))

from payload_lib import (  # noqa: E402
    ensure_benchmark_interactions,
    fetch_tweet_user_ids,
    normalize_bool,
    positive_int,
    repo_path,
    write_csv,
)


def prepare(args: argparse.Namespace) -> None:
    out_dir = repo_path(args.output_dir) if args.output_dir else (_HERE / "payload")
    ids = fetch_tweet_user_ids(args.count, args.seed_tweets, args.tweets_per_user)
    if args.seed_tweets:
        # Every seeded tweet gets one of each interaction (like/reply/reply-like/
        # retweet) so the enriched tweets-get read returns non-empty, cache-warm
        # counts. Runs after fetch_tweet_user_ids has seeded the tweets themselves.
        ensure_benchmark_interactions(args.count, args.tweets_per_user)
    output = out_dir / "user-ids.csv"
    write_csv(output, ["id"], [[value] for value in ids])

    print("Prepared JMeter payload:")
    print("  workload=tweets-get")
    print(f"  count={args.count}")
    print(f"  tweets_per_user={args.tweets_per_user}")
    print(f"  user_ids_file={output}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare tweets-get JMeter payload.")
    parser.add_argument("--count", type=positive_int, required=True)
    parser.add_argument("--output-dir", help="Output directory for generated CSV files (default: workload dir / payload).")
    parser.add_argument("--seed-tweets", type=normalize_bool, default=False)
    parser.add_argument(
        "--tweets-per-user",
        type=positive_int,
        default=1000,
        help="Tweets seeded per user (default: 1000).",
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()
    prepare(args)


if __name__ == "__main__":
    main()
