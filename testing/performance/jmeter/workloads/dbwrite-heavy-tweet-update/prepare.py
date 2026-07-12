#!/usr/bin/env python3
"""Prepare tweet-update JMeter payload.

Workload exercises tweet content + hashtag relation maintenance. Each PUT
loads the tweet (with @EntityGraph on async / explicit lookups on reactive),
rewrites the content, and rebuilds the tweet_hashtag join-table rows.

Schema notes:
- `hashtags` has no `created_at` column. Insert only (id, text).
- `hashtags.text` has no UNIQUE constraint (verified in
  deployment/docker-compose/schema/tweet_service_db.sql, shared by both stacks).
  To stay idempotent we use deterministic UUIDs derived from
  md5('benchmark-hashtag:' || text) and `ON CONFLICT (id) DO NOTHING`.

Old-state vs new-state content shape (bounded, deterministic, no timestamps):
  old: 'benchmark old tweet <i> #old_<i%pool> #common_<i%64> @benchmark_user_1'
  new: 'benchmark new tweet <i> #new_<i%pool> #common_<i%64> @benchmark_user_2'

The trailing @mention flips between two pre-seeded cohort users (user_1 → user_2)
so every PUT drives a real mention reconcile (delete the user_1 row, resolve +
insert a user_2 row) alongside the #old_->#new_ hashtag rebuild — the
mention-table twin of the hashtag flip.

Hashtag dictionary kept small: 3 prefixes × pool buckets gives 3*pool entries.
Default pool=1024 → 3072 hashtag rows. All cached easily in shared_buffers.

Seeding produces:
  1. user rows (deterministic UUIDs, ensure_benchmark_users)
  2. hashtag rows (deterministic UUIDs by text)
  3. tweet rows with old-form content (deterministic UUIDs, version=0)
  4. tweet_hashtag links to old-form hashtags
  5. old-state mention rows (each tweet mentions @benchmark_user_1)
  6. JMeter CSV with user_id, tweet_id, content (new-form)

precell_reset.sh consumes the same SQL functions to restore (3), (4) and (5).
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_JMETER_DIR = _HERE.parents[1]
sys.path.insert(0, str(_JMETER_DIR / "lib"))

from payload_lib import (  # noqa: E402
    ensure_benchmark_users,
    normalize_bool,
    positive_int,
    psql_exec,
    write_csv,
)


def _old_content(i: int, pool: int) -> str:
    """Match precell_reset.sh's reset SQL byte-for-byte."""
    return f"benchmark old tweet {i} #old_{i % pool} #common_{i % 64} @benchmark_user_1"


def _new_content(i: int, pool: int) -> str:
    """Match the JMeter PUT body. The trailing @benchmark_user_2 mention flips the
    old-state @benchmark_user_1 mention, so each PUT drives a real mention reconcile
    (delete user_1, resolve + insert user_2) alongside the hashtag rebuild — the
    mention crosses into user-service via resolution, deliberately, to exercise that
    path symmetrically on both stacks."""
    return f"benchmark new tweet {i} #new_{i % pool} #common_{i % 64} @benchmark_user_2"


def _seed_hashtags(pool: int) -> None:
    # Step 1: clear any prior benchmark-namespace hashtag rows. The schema
    # has no UNIQUE constraint on `hashtags.text`, so a dirty DB could carry
    # multiple rows with the same text from earlier runs. Reactive
    # `linkTweetToHashtagsByText` joins by text and would insert one
    # tweet_hashtag link per duplicate row — silently inflating the link
    # rate above what async would produce. Clean slate before seeding.
    # tweet_hashtag rows FK-cascade off hashtags so we have to wipe links
    # first (TRUNCATE tweets CASCADE in step 3 will catch any stragglers).
    psql_exec("tweet-service-db", "tweet_service_db",
              "DELETE FROM tweet_hashtag "
              "WHERE hashtag_id IN ("
              "    SELECT id FROM hashtags "
              "    WHERE text LIKE 'old_%' OR text LIKE 'new_%' OR text LIKE 'common_%'"
              ");")
    psql_exec("tweet-service-db", "tweet_service_db",
              "DELETE FROM hashtags "
              "WHERE text LIKE 'old_%' OR text LIKE 'new_%' OR text LIKE 'common_%';")
    # Step 2: re-seed with deterministic UUIDs.
    sql = f"""
WITH tag_texts AS (
    SELECT prefix || '_' || n AS text
    FROM (VALUES ('old'), ('new')) AS p(prefix)
    CROSS JOIN generate_series(0, {pool - 1}) AS g(n)
    UNION ALL
    SELECT 'common_' || n FROM generate_series(0, 63) AS g(n)
)
INSERT INTO hashtags (id, text)
SELECT
    (
        substr(tag_md5, 1, 8) || '-' ||
        substr(tag_md5, 9, 4) || '-4' ||
        substr(tag_md5, 14, 3) || '-a' ||
        substr(tag_md5, 18, 3) || '-' ||
        substr(tag_md5, 21, 12)
    )::uuid,
    tt.text
FROM tag_texts tt
CROSS JOIN LATERAL (
    SELECT md5('benchmark-hashtag:' || tt.text) AS tag_md5
) AS ids
ON CONFLICT (id) DO NOTHING;
"""
    psql_exec("tweet-service-db", "tweet_service_db", sql)


def _seed_tweets_with_old_content(count: int, pool: int, user_count: int) -> None:
    # Mirror ensure_benchmark_tweets's TRUNCATE+INSERT pattern so we get
    # deterministic tweet UUIDs that match across cells. CASCADE because
    # mentions + tweet_hashtag FK off tweets.
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
    SELECT n, ((n - 1) % {user_count}) + 1 AS user_seq
    FROM generate_series(1, {count}) AS g(n)
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
    'benchmark old tweet ' || tweet_numbers.n ||
        ' #old_' || (tweet_numbers.n % {pool}) ||
        ' #common_' || (tweet_numbers.n % 64) ||
        ' @benchmark_user_1' AS content,
    timezone('utc', now()) - make_interval(secs => ({count} - tweet_numbers.n))
FROM tweet_numbers
JOIN benchmark_users ON benchmark_users.seq = tweet_numbers.user_seq
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-tweet:' || tweet_numbers.n::text) AS tweet_md5
) AS ids;
"""
    psql_exec("tweet-service-db", "tweet_service_db", sql)


def _link_tweets_to_old_hashtags(count: int, pool: int) -> None:
    # For each tweet n, link it to hashtags `old_<n%pool>` and `common_<n%64>`.
    sql = f"""
WITH tweet_numbers AS (
    SELECT n,
        (
            substr(tweet_md5, 1, 8) || '-' ||
            substr(tweet_md5, 9, 4) || '-4' ||
            substr(tweet_md5, 14, 3) || '-a' ||
            substr(tweet_md5, 18, 3) || '-' ||
            substr(tweet_md5, 21, 12)
        )::uuid AS tweet_id
    FROM generate_series(1, {count}) AS g(n)
    CROSS JOIN LATERAL (
        SELECT md5('jmeter-benchmark-tweet:' || n::text) AS tweet_md5
    ) AS ids
),
target_texts AS (
    SELECT tweet_id, text FROM tweet_numbers,
    LATERAL (VALUES
        ('old_' || (tweet_numbers.n % {pool})),
        ('common_' || (tweet_numbers.n % 64))
    ) AS t(text)
)
INSERT INTO tweet_hashtag (tweet_id, hashtag_id)
SELECT tt.tweet_id, h.id
FROM target_texts tt
JOIN hashtags h ON h.text = tt.text
ON CONFLICT DO NOTHING;
"""
    psql_exec("tweet-service-db", "tweet_service_db", sql)


def _seed_old_mentions(count: int) -> None:
    # Old-state mention: every benchmark tweet mentions @benchmark_user_1. The PUT
    # body flips it to @benchmark_user_2, so each update drives a real mention
    # reconcile (delete the user_1 row, resolve + insert a user_2 row) — the
    # mention-table twin of the #old_->#new_ hashtag rebuild. user_1's id and each
    # mention id are derived inline from the same md5 seeds the cohort/tweet seeders
    # use; mentions.user_id is cross-DB (no FK), so the literal id is enough. The
    # tweet_id is the row's own id (the tweet was just seeded above).
    psql_exec("tweet-service-db", "tweet_service_db", "DELETE FROM mentions;")
    sql = f"""
INSERT INTO mentions (id, user_id, text, tweet_id)
SELECT
    (
        substr(mention_md5, 1, 8) || '-' ||
        substr(mention_md5, 9, 4) || '-4' ||
        substr(mention_md5, 14, 3) || '-a' ||
        substr(mention_md5, 18, 3) || '-' ||
        substr(mention_md5, 21, 12)
    )::uuid AS id,
    (
        substr(user_md5, 1, 8) || '-' ||
        substr(user_md5, 9, 4) || '-4' ||
        substr(user_md5, 14, 3) || '-a' ||
        substr(user_md5, 18, 3) || '-' ||
        substr(user_md5, 21, 12)
    )::uuid AS user_id,
    'benchmark_user_1' AS text,
    (
        substr(tweet_md5, 1, 8) || '-' ||
        substr(tweet_md5, 9, 4) || '-4' ||
        substr(tweet_md5, 14, 3) || '-a' ||
        substr(tweet_md5, 18, 3) || '-' ||
        substr(tweet_md5, 21, 12)
    )::uuid AS tweet_id
FROM generate_series(1, {count}) AS g(n)
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-user:1') AS user_md5,
           md5('jmeter-benchmark-tweet:' || n::text) AS tweet_md5,
           md5('jmeter-benchmark-update-mention:' || n::text) AS mention_md5
) AS ids
ON CONFLICT (id) DO NOTHING;
"""
    psql_exec("tweet-service-db", "tweet_service_db", sql)


def prepare(args: argparse.Namespace) -> None:
    pool = args.pool_size
    count = args.count
    out_dir = Path(args.output_dir) if args.output_dir else (_HERE / "payload")
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.seed_tweets:
        print(f"[prepare] seeding {count} users + benchmark fixtures...")
        ensure_benchmark_users(max(1, count))
        print(f"[prepare] seeding hashtag dictionary (pool={pool})...")
        _seed_hashtags(pool)
        print(f"[prepare] seeding {count} tweets with old-form content...")
        _seed_tweets_with_old_content(count, pool, count)
        print(f"[prepare] linking tweets to old-form hashtags...")
        _link_tweets_to_old_hashtags(count, pool)
        print(f"[prepare] seeding old-state mentions (@benchmark_user_1)...")
        _seed_old_mentions(count)

    # CSV body uses the SAME tweet UUIDs the seed produced, paired with NEW
    # content (different prefix + new_<i%pool> hashtag) and the tweet's author
    # id (PUT targets the owner-scoped /tweets/{user_id}/{tweet_id} path).
    # _seed_tweets_with_old_content is called with user_count = count, so
    # user_seq = ((n-1) % count) + 1 = n — i.e. tweet n is owned by user n.
    rows = []
    import hashlib

    def _det_uuid(seed: str) -> str:
        digest = hashlib.md5(seed.encode()).hexdigest()
        return (
            f"{digest[0:8]}-{digest[8:12]}-4{digest[13:16]}-"
            f"a{digest[17:20]}-{digest[20:32]}"
        )

    for i in range(1, count + 1):
        tweet_uuid = _det_uuid(f"jmeter-benchmark-tweet:{i}")
        user_uuid = _det_uuid(f"jmeter-benchmark-user:{i}")
        rows.append([user_uuid, tweet_uuid, _new_content(i, pool)])

    output = out_dir / "tweet-updates.csv"
    write_csv(output, ["user_id", "tweet_id", "content"], rows)

    print("Prepared JMeter payload:")
    print(f"  workload=tweet-update")
    print(f"  count={count}")
    print(f"  pool_size={pool}")
    print(f"  csv={output}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare tweet-update JMeter payload.")
    parser.add_argument("--count", type=positive_int, required=True,
                        help="Number of tweets to seed and CSV rows to emit.")
    parser.add_argument("--pool-size", type=positive_int, default=1024,
                        help="Hashtag pool bucket count (old_0..old_<pool-1> + new_0..new_<pool-1>). "
                             "Determines how many distinct hashtags a tweet can reference. Default 1024.")
    parser.add_argument("--output-dir", help="Override CSV output directory.")
    parser.add_argument("--seed-tweets", type=normalize_bool, default=False,
                        help="If 1, TRUNCATE tweets + reseed all benchmark fixtures (users, hashtags, "
                             "tweets, tweet_hashtag links) before generating the CSV.")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    prepare(args)


if __name__ == "__main__":
    main()
