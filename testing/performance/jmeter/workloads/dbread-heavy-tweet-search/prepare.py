#!/usr/bin/env python3
"""Prepare dbread-heavy-tweet-search JMeter payload.

Seeds a large cohort of tweets whose content embeds a known set of common
real English words, then writes those words to payload/search-terms.csv.
Each driven search (`GET /tweets/search/{searchTerm}`) runs the pg_trgm
`<%` trigram-similarity scan + GIN-indexed sort, returning many rows per
query and exercising the DB-execution-bound path.

Seeding strategy
----------------
* 1 000 users × 1 000 tweets/user = 1 M tweet rows shared across the
  database-heavy tweet workloads.
* Each tweet content is "benchmark tweet <N> about <word> #benchmark
  @benchmark_companion", where <word> cycles through the SEARCH_TERMS list.
  This guarantees every search term matches ~(1M / len(SEARCH_TERMS)) rows
  — enough rows to force a real ranking sort on every query.
* The same deterministic UUID formula as the other workloads is used so
  hashtag/mention cross-links remain consistent with the shared cohort.

With --seed-tweets 1, TRUNCATEs tweets CASCADE then re-seeds.
With --seed-tweets 0, assumes tweets already exist and only writes the CSV.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_JMETER_DIR = _HERE.parents[1]
sys.path.insert(0, str(_JMETER_DIR / "lib"))

from payload_lib import (  # noqa: E402
    BENCHMARK_COMPANION_NAME,
    _deterministic_uuid,
    _uuid_expr,
    ensure_benchmark_companion,
    ensure_benchmark_users,
    normalize_bool,
    positive_int,
    psql_exec,
    repo_path,
    write_csv,
)

# Bench-hygiene settle() + DB constants live in testing/performance/lib/.
import importlib.util as _ilu
import os as _os

_BENCH_LIB = _HERE.parents[3] / "lib"
sys.path.insert(0, str(_BENCH_LIB))
from bench_hygiene import TWEET_DB, settle  # noqa: E402

# ---------------------------------------------------------------------------
# The search terms written to the CSV. Common English words that will
# appear verbatim in tweet content so the pg_trgm `<%` operator always
# finds many matching rows, exercising real ranking work.
# ---------------------------------------------------------------------------
SEARCH_TERMS: list[str] = [
    "running",
    "morning",
    "project",
    "weekend",
    "coffee",
    "learning",
    "travel",
    "working",
    "reading",
    "excited",
    "weather",
    "friends",
    "amazing",
    "music",
    "cooking",
    "fitness",
    "nature",
    "happy",
    "creative",
    "update",
]


def seed_search_tweets(user_count: int, tweets_per_user: int) -> None:
    """TRUNCATE tweets CASCADE then re-seed with search-term-embedded content."""
    ensure_benchmark_users(max(1, user_count))
    ensure_benchmark_companion()

    total = user_count * tweets_per_user
    companion_id = _deterministic_uuid("jmeter-benchmark-companion")
    hashtag_id = _deterministic_uuid("jmeter-benchmark-hashtag:benchmark")
    terms = SEARCH_TERMS
    n_terms = len(terms)

    # Build an SQL VALUES list for the terms array so the formula
    # `terms[(n-1) % n_terms]` is fully inline in the INSERT.
    # We use a CASE expression to cycle through the term list.
    case_clauses = "\n            ".join(
        f"WHEN ((n - 1) % {n_terms}) = {i} THEN '{term}'"
        for i, term in enumerate(terms)
    )

    psql_exec("tweet-service-db", "tweet_service_db", "TRUNCATE TABLE tweets CASCADE;")

    sql = f"""
WITH benchmark_users AS (
    SELECT
        (
            substr(user_md5, 1, 8) || '-' ||
            substr(user_md5, 9, 4) || '-4' ||
            substr(user_md5, 14, 3) || '-a' ||
            substr(user_md5, 18, 3) || '-' ||
            substr(user_md5, 21, 12)
        )::uuid AS id,
        n AS seq
    FROM generate_series(1, {user_count}) AS g(n)
    CROSS JOIN LATERAL (
        SELECT md5('jmeter-benchmark-user:' || n::text) AS user_md5
    ) AS ids
),
tweet_numbers AS (
    SELECT
        n,
        ((n - 1) % {user_count}) + 1 AS user_seq,
        CASE
            {case_clauses}
            ELSE 'general'
        END AS search_term
    FROM generate_series(1, {total}) AS g(n)
)
INSERT INTO tweets (id, user_id, version, content, created_at)
SELECT
    (
        substr(tweet_md5, 1, 8) || '-' ||
        substr(tweet_md5, 9, 4) || '-4' ||
        substr(tweet_md5, 14, 3) || '-a' ||
        substr(tweet_md5, 18, 3) || '-' ||
        substr(tweet_md5, 21, 12)
    )::uuid AS id,
    benchmark_users.id AS user_id,
    0 AS version,
    'benchmark tweet ' || tweet_numbers.n || ' about ' || tweet_numbers.search_term
        || ' #benchmark @{BENCHMARK_COMPANION_NAME}' AS content,
    timezone('utc', now()) - make_interval(secs => ({total} - tweet_numbers.n))
FROM tweet_numbers
JOIN benchmark_users ON benchmark_users.seq = tweet_numbers.user_seq
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-tweet:' || tweet_numbers.n::text) AS tweet_md5
) AS ids
ON CONFLICT (id) DO NOTHING;

INSERT INTO hashtags (id, text)
VALUES ('{hashtag_id}'::uuid, 'benchmark')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tweet_hashtag (tweet_id, hashtag_id)
SELECT {_uuid_expr('tweet_md5')} AS tweet_id, '{hashtag_id}'::uuid AS hashtag_id
FROM generate_series(1, {total}) AS g(n)
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-tweet:' || n::text) AS tweet_md5
) AS ids
ON CONFLICT (tweet_id, hashtag_id) DO NOTHING;

INSERT INTO mentions (id, user_id, text, tweet_id)
SELECT
    {_uuid_expr('mention_md5')} AS id,
    '{companion_id}'::uuid AS user_id,
    '{BENCHMARK_COMPANION_NAME}' AS text,
    {_uuid_expr('tweet_md5')} AS tweet_id
FROM generate_series(1, {total}) AS g(n)
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-tweet:' || n::text) AS tweet_md5,
           md5('jmeter-benchmark-mention:' || n::text) AS mention_md5
) AS ids
ON CONFLICT (id) DO NOTHING;
"""
    psql_exec("tweet-service-db", "tweet_service_db", sql)
    settle(TWEET_DB, ["tweets", "hashtags", "tweet_hashtag", "mentions"])


def prepare(args: argparse.Namespace) -> None:
    out_dir = repo_path(args.output_dir) if args.output_dir else (_HERE / "payload")

    if args.seed_tweets:
        seed_search_tweets(args.count, args.tweets_per_user)

    output = out_dir / "search-terms.csv"
    write_csv(output, ["searchTerm"], [[term] for term in SEARCH_TERMS])

    print("Prepared JMeter payload:")
    print("  workload=dbread-heavy-tweet-search")
    print(f"  count={args.count}")
    print(f"  tweets_per_user={args.tweets_per_user}")
    print(f"  search_terms={len(SEARCH_TERMS)}")
    print(f"  search_terms_file={output}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Prepare dbread-heavy-tweet-search JMeter payload."
    )
    parser.add_argument("--count", type=positive_int, required=True)
    parser.add_argument(
        "--output-dir",
        help="Output directory for generated CSV files (default: workload dir / payload).",
    )
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
