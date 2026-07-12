#!/usr/bin/env python3
"""Shared payload-prep helpers for JMeter workloads.

Each workload directory under ../workloads/<name>/ has its own prepare.py that
imports from this module. Keeps the per-workload prepare.py thin (just the
handler + argparse) and the cross-workload SQL/docker plumbing in one place.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import subprocess
import sys
from pathlib import Path


# lib/ is at testing/performance/jmeter/lib/. Repo root is four parents up
# (lib → jmeter → performance → testing → repo).
LIB_DIR = Path(__file__).resolve().parent
JMETER_DIR = LIB_DIR.parent
REPO_ROOT = JMETER_DIR.parents[2]

# Cross-workload DB/cache hygiene + host-client primitives live one level up at
# testing/performance/lib/ so the JMeter and k6 seeders share identical, topology-agnostic
# psql/redis-cli (host clients on the published ports — Docker-published or native-local).
sys.path.insert(0, str(REPO_ROOT / "testing" / "performance" / "lib"))
from bench_hygiene import INTERACTION_DB, TWEET_DB, USER_DB, psql, redis_cli, settle  # noqa: E402

# A single shared counterparty seeded alongside the cohort. Every benchmark tweet's
# like, reply, reply-like, retweet, and mention point at this one user so the enriched
# read resolves the same user summary over and over — keeping that cache entry hot
# instead of thrashing a different user per tweet.
BENCHMARK_COMPANION_SEED = "jmeter-benchmark-companion"
BENCHMARK_COMPANION_NAME = "benchmark_companion"


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return parsed


def repo_path(value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else (REPO_ROOT / path)


def normalize_bool(value: str | int | bool) -> bool:
    if isinstance(value, bool):
        return value

    raw = str(value).strip().lower()
    if raw in {"1", "true", "yes"}:
        return True
    if raw in {"0", "false", "no"}:
        return False
    raise argparse.ArgumentTypeError(f"expected boolean 0/1/true/false, got '{value}'")


def psql_exec(service: str, database: str, sql: str, *, quiet: bool = False) -> subprocess.CompletedProcess[str]:
    # Host psql on the published port via the shared bench_hygiene primitive (no docker
    # exec) — identical against Docker-published or native-local infra. quiet=True captures
    # -At stdout for query_rows to parse; otherwise psql output streams to the console.
    return psql((service, database), sql, capture=quiet)


def query_rows(service: str, database: str, sql: str) -> list[str]:
    result = psql_exec(service, database, sql, quiet=True)
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def write_csv(path: Path, header: list[str], rows: list[list[str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        writer.writerows(rows)


# ---------------------------------------------------------------------------
# Deterministic UUID helper — shared by every benchmark seeder.
# ---------------------------------------------------------------------------


def _uuid_expr(md5_col: str) -> str:
    # Turn a 32-char md5 hex column into a v4-shaped UUID, the same formula every
    # benchmark seeder uses so a given entity (user n, tweet n, ...) always maps to
    # one stable id. Keeps the cross-table seeders (likes/replies/retweets pointing
    # at tweet n and user m) consistent without a cross-DB lookup.
    return (
        f"(substr({md5_col}, 1, 8) || '-' || substr({md5_col}, 9, 4) || '-4' || "
        f"substr({md5_col}, 14, 3) || '-a' || substr({md5_col}, 18, 3) || '-' || "
        f"substr({md5_col}, 21, 12))::uuid"
    )


def _deterministic_uuid(seed: str) -> str:
    # Python-side twin of _uuid_expr(md5('<seed>')): same character slicing, so an id
    # computed here matches one a SQL seeder derives from the same seed. Used for the
    # fixed-value companion, whose id must be a literal in interaction_service_db
    # (a different DB than the user_service_db row it points at, with no cross-DB FK).
    digest = hashlib.md5(seed.encode("utf-8")).hexdigest()
    return f"{digest[0:8]}-{digest[8:12]}-4{digest[13:16]}-a{digest[17:20]}-{digest[20:32]}"


# ---------------------------------------------------------------------------
# user-service-db helpers — shared by workloads that need user IDs.
# ---------------------------------------------------------------------------


def ensure_benchmark_users(count: int) -> None:
    sql = f"""
INSERT INTO users (id, user_name, email, biography, password, is_private, birth_date, created_at)
SELECT
    (
        substr(user_md5, 1, 8) || '-' ||
        substr(user_md5, 9, 4) || '-4' ||
        substr(user_md5, 14, 3) || '-a' ||
        substr(user_md5, 18, 3) || '-' ||
        substr(user_md5, 21, 12)
    )::uuid AS id,
    'benchmark_user_' || n AS user_name,
    'benchmark_user_' || n || '@example.test' AS email,
    'benchmark biography ' || n AS biography,
    'benchmark-password' AS password,
    false AS is_private,
    DATE '1990-01-01' + ((n % 1000)::int) AS birth_date,
    timezone('utc', now()) AS created_at
FROM generate_series(1, {count}) AS g(n)
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-user:' || n::text) AS user_md5
) AS ids
ON CONFLICT (id) DO NOTHING;
"""
    psql_exec("user-service-db", "user_service_db", sql)
    settle(USER_DB, ["users"])


def ensure_benchmark_companion() -> None:
    # One shared counterparty for every benchmark tweet's like, reply, reply-like,
    # retweet, and mention. Seeded into user_service_db with a deterministic UUID so
    # the cross-DB interaction/mention seeders can point at it with an inline literal.
    # Its email deliberately does NOT match 'benchmark_user_%@example.test', keeping it
    # out of the cohort CSV — it is a counterparty, never a workload subject.
    companion_md5 = f"md5('{BENCHMARK_COMPANION_SEED}')"
    sql = f"""
INSERT INTO users (id, user_name, email, biography, password, is_private, birth_date, created_at)
SELECT
    {_uuid_expr('companion_md5')} AS id,
    '{BENCHMARK_COMPANION_NAME}' AS user_name,
    '{BENCHMARK_COMPANION_NAME}@example.test' AS email,
    'benchmark companion' AS biography,
    'benchmark-password' AS password,
    false AS is_private,
    DATE '1990-01-01' AS birth_date,
    timezone('utc', now()) AS created_at
FROM (SELECT {companion_md5} AS companion_md5) AS ids
ON CONFLICT (id) DO NOTHING;
"""
    psql_exec("user-service-db", "user_service_db", sql)


def seed_benchmark_companion_summary_cache() -> None:
    """Seed the shared top-reply author's interaction-service summary permanently."""
    companion_id = _deterministic_uuid(BENCHMARK_COMPANION_SEED)
    key = f"users::{companion_id}"
    value = json.dumps(
        {
            "id": companion_id,
            "user_name": BENCHMARK_COMPANION_NAME,
            "is_private": False,
        },
        separators=(",", ":"),
    )
    ok = redis_cli("-x", "SET", key, input_text=value)
    if ok != "OK":
        raise RuntimeError(f"failed to seed Redis key {key}: {ok}")

    ttl = redis_cli("TTL", key)
    if ttl != "-1":
        raise RuntimeError(f"expected permanent Redis key {key}, got TTL={ttl}")

    print("Seeded permanent interaction-service user summary:")
    print(f"  key={key}")
    print("  ttl=-1")


def benchmark_user_ids(count: int) -> list[str]:
    return query_rows(
        "user-service-db",
        "user_service_db",
        f"""
SELECT id
FROM users
WHERE email LIKE 'benchmark_user_%@example.test'
ORDER BY split_part(split_part(email, '@', 1), '_', 3)::int
LIMIT {count};
""",
    )


def existing_user_ids(count: int) -> list[str]:
    return query_rows(
        "user-service-db",
        "user_service_db",
        f"""
SELECT id
FROM users
ORDER BY created_at NULLS LAST, id
LIMIT {count};
""",
    )


def fetch_user_ids(count: int, seed_users: bool) -> list[str]:
    if seed_users:
        ensure_benchmark_users(count)
        ids = benchmark_user_ids(count)
    else:
        ids = existing_user_ids(count)

    if len(ids) < count:
        if not seed_users:
            print(
                f"ERROR: requested {count} user ids but only found {len(ids)} in user_service_db. "
                "Rerun with --seed-users 1 if you want the tool to create benchmark users.",
                file=sys.stderr,
            )
            sys.exit(1)
        print(f"ERROR: failed to prepare {count} benchmark users.", file=sys.stderr)
        sys.exit(1)

    return ids


# ---------------------------------------------------------------------------
# tweet-service-db helpers — shared by workloads that need tweet IDs.
# ---------------------------------------------------------------------------


def ensure_benchmark_tweets(user_count: int, tweets_per_user: int = 1) -> None:
    # Seed the tweet rows the JMeter tweet-update / tweets-get workloads
    # reference. We need (a) the user-service-db users to exist with
    # deterministic UUIDs (ensure_benchmark_users runs against
    # user_service_db) AND (b) tweets in tweet-service-db that point at
    # those same UUIDs.
    #
    # `ensure_benchmark_users` derives user UUIDs from
    # md5('jmeter-benchmark-user:' || n). We replicate that formula
    # here inline so the INSERT runs entirely inside tweet_service_db
    # without cross-DB queries.
    ensure_benchmark_users(max(1, user_count))
    # The companion is the mention target embedded in every tweet's content
    # (@benchmark_companion). It must exist in user_service_db so the read path's
    # user-summary lookup resolves it (and stays cache-hot across the cohort).
    ensure_benchmark_companion()
    total = user_count * tweets_per_user
    companion_id = _deterministic_uuid(BENCHMARK_COMPANION_SEED)
    # One shared hashtag row reused across every tweet, faithful to the create path's
    # find-or-create-by-text dedup: a real cohort all tagging #benchmark would collapse
    # to a single hashtags row, with one tweet_hashtag link per tweet.
    hashtag_id = _deterministic_uuid("jmeter-benchmark-hashtag:benchmark")
    # TRUNCATE before INSERT to avoid silent state-leakage. The previous
    # design relied on `ON CONFLICT (id) DO NOTHING`, which is correct for
    # idempotency but breaks when two runs share the same `total` and so
    # generate identical tweet UUIDs (md5('jmeter-benchmark-tweet:' || N)).
    # CASCADE is required because `tweets` is FK-referenced by `mentions`
    # and `tweet_hashtag` — those tables would dangle without their parents
    # after a re-seed.
    truncate_sql = "TRUNCATE TABLE tweets CASCADE;"
    psql_exec("tweet-service-db", "tweet_service_db", truncate_sql)
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
    'benchmark tweet ' || tweet_numbers.n || ' #benchmark @{BENCHMARK_COMPANION_NAME}' AS content,
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


def benchmark_tweet_ids(count: int) -> list[str]:
    return query_rows(
        "tweet-service-db",
        "tweet_service_db",
        f"""
SELECT id
FROM tweets
WHERE content LIKE 'benchmark tweet %'
ORDER BY split_part(content, ' ', 3)::int
LIMIT {count};
""",
    )


def existing_tweet_ids(count: int) -> list[str]:
    return query_rows(
        "tweet-service-db",
        "tweet_service_db",
        f"""
SELECT id
FROM tweets
ORDER BY created_at NULLS LAST, id
LIMIT {count};
""",
    )


def benchmark_tweet_user_ids(count: int) -> list[str]:
    return query_rows(
        "tweet-service-db",
        "tweet_service_db",
        f"""
SELECT DISTINCT user_id
FROM tweets
WHERE content LIKE 'benchmark tweet %'
ORDER BY user_id
LIMIT {count};
""",
    )


def existing_tweet_user_ids(count: int) -> list[str]:
    return query_rows(
        "tweet-service-db",
        "tweet_service_db",
        f"""
SELECT DISTINCT user_id
FROM tweets
ORDER BY user_id
LIMIT {count};
""",
    )


def fetch_tweet_ids(count: int, seed_tweets: bool) -> list[str]:
    if seed_tweets:
        ensure_benchmark_tweets(count)
        ids = benchmark_tweet_ids(count)
    else:
        ids = existing_tweet_ids(count)

    if len(ids) < count:
        if not seed_tweets:
            print(
                f"ERROR: requested {count} tweet ids but only found {len(ids)} in tweet_service_db. "
                "Rerun with --seed-tweets 1 if you want the tool to create benchmark tweets.",
                file=sys.stderr,
            )
            sys.exit(1)
        print(f"ERROR: failed to prepare {count} benchmark tweets.", file=sys.stderr)
        sys.exit(1)

    return ids


def fetch_tweet_user_ids(count: int, seed_tweets: bool, tweets_per_user: int = 1) -> list[str]:
    if seed_tweets:
        ensure_benchmark_tweets(count, tweets_per_user)
        ids = benchmark_tweet_user_ids(count)
    else:
        ids = existing_tweet_user_ids(count)

    if len(ids) < count:
        if not seed_tweets:
            print(
                f"ERROR: requested {count} tweet owner ids but only found {len(ids)} in tweet_service_db. "
                "Rerun with --seed-tweets 1 if you want the tool to create benchmark tweets.",
                file=sys.stderr,
            )
            sys.exit(1)
        print(f"ERROR: failed to prepare {count} benchmark tweet owners.", file=sys.stderr)
        sys.exit(1)

    return ids


def _split_pair_rows(rows: list[str]) -> list[list[str]]:
    # psql -At emits multi-column rows as `col1|col2`. Split each into a
    # [tweet_id, user_id] pair.
    return [line.split("|", 1) for line in rows if "|" in line]


def benchmark_tweet_id_owner_pairs(count: int) -> list[list[str]]:
    return _split_pair_rows(query_rows(
        "tweet-service-db",
        "tweet_service_db",
        f"""
SELECT id, user_id
FROM tweets
WHERE content LIKE 'benchmark tweet %'
ORDER BY split_part(content, ' ', 3)::int
LIMIT {count};
""",
    ))


def existing_tweet_id_owner_pairs(count: int) -> list[list[str]]:
    return _split_pair_rows(query_rows(
        "tweet-service-db",
        "tweet_service_db",
        f"""
SELECT id, user_id
FROM tweets
ORDER BY created_at NULLS LAST, id
LIMIT {count};
""",
    ))


def fetch_tweet_id_owner_pairs(count: int, seed_tweets: bool) -> list[list[str]]:
    # Owner-scoped tweet PUT/DELETE needs both the tweet id and its author id so
    # the JMeter path /tweets/{user_id}/{tweet_id} targets a tweet the user owns
    # (otherwise every update/delete 404s). Pairs come back in the same
    # deterministic order as fetch_tweet_ids.
    if seed_tweets:
        ensure_benchmark_tweets(count)
        pairs = benchmark_tweet_id_owner_pairs(count)
    else:
        pairs = existing_tweet_id_owner_pairs(count)

    if len(pairs) < count:
        if not seed_tweets:
            print(
                f"ERROR: requested {count} tweet id/owner pairs but only found {len(pairs)} in tweet_service_db. "
                "Rerun with --seed-tweets 1 if you want the tool to create benchmark tweets.",
                file=sys.stderr,
            )
            sys.exit(1)
        print(f"ERROR: failed to prepare {count} benchmark tweets.", file=sys.stderr)
        sys.exit(1)

    return pairs


# ---------------------------------------------------------------------------
# interaction-service-db helpers — follow edges + per-tweet interactions
# (like / reply / reply-like / retweet) for the enriched read fan-out.
# ---------------------------------------------------------------------------


def ensure_benchmark_follows(user_count: int) -> None:
    # Seed a follow ring across the benchmark cohort so the user-profile read
    # (GET /users/{id}) fans out to non-empty data: getFollowingCount and
    # getFollowersCount both filter on status = 'ACCEPTED'. User n follows
    # user n+1, the last wrapping back to the first, so every cohort member
    # has exactly one following and exactly one follower.
    #
    # User UUIDs are derived inline from md5('jmeter-benchmark-user:' || n),
    # the same formula ensure_benchmark_users uses, so the INSERT runs entirely
    # inside interaction_service_db with no cross-DB query. follow.id is
    # deterministic from md5('jmeter-benchmark-follow:' || n).
    ensure_benchmark_users(max(1, user_count))
    if user_count < 2:
        return
    # TRUNCATE before INSERT for the same state-leakage reason as
    # ensure_benchmark_tweets: a prior run at a different cohort size leaves
    # stale edges that would skew the ACCEPTED counts.
    psql_exec("interaction-service-db", "interaction_service_db", "TRUNCATE TABLE follows;")
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
edges AS (
    SELECT n AS seq, (n % {user_count}) + 1 AS followed_seq
    FROM generate_series(1, {user_count}) AS g(n)
)
INSERT INTO follows (id, follower_id, followed_id, status, created_at)
SELECT
    (
        substr(follow_md5, 1, 8) || '-' ||
        substr(follow_md5, 9, 4) || '-4' ||
        substr(follow_md5, 14, 3) || '-a' ||
        substr(follow_md5, 18, 3) || '-' ||
        substr(follow_md5, 21, 12)
    )::uuid AS id,
    follower.id AS follower_id,
    followed.id AS followed_id,
    'ACCEPTED' AS status,
    timezone('utc', now()) AS created_at
FROM edges
JOIN benchmark_users follower ON follower.seq = edges.seq
JOIN benchmark_users followed ON followed.seq = edges.followed_seq
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-follow:' || edges.seq::text) AS follow_md5
) AS ids
ON CONFLICT DO NOTHING;
"""
    psql_exec("interaction-service-db", "interaction_service_db", sql)
    settle(INTERACTION_DB, ["follows"])


def ensure_benchmark_interactions(user_count: int, tweets_per_user: int = 1) -> None:
    # Give every benchmark tweet exactly one of each interaction so the enriched read
    # (tweets-get / user-profile) returns non-empty, cache-warm data:
    #   - 1 TWEET like   (companion likes the tweet)
    #   - 1 reply        (companion replies)
    #   - 1 REPLY like   (companion likes that reply — replies ARE likeable)
    #   - 1 retweet      (companion retweets; retweets are NOT likeable per the schema
    #                     CHECK likeable_type IN ('TWEET','REPLY'), so it gets no like)
    # The companion is the single shared counterparty, so the per-reply / per-top-reply
    # user-summary lookup keeps one cache entry hot instead of thrashing a user per tweet.
    #
    # All ids are derived inline from md5('jmeter-benchmark-<kind>:' || n) so the rows
    # point at tweet n and reply n (md5('jmeter-benchmark-tweet:'||n) /
    # md5('jmeter-benchmark-reply:'||n)) without a cross-DB lookup; the companion's
    # user id is a precomputed literal (it lives in user_service_db, no cross-DB FK).
    ensure_benchmark_companion()
    total = max(1, user_count) * tweets_per_user
    companion_id = _deterministic_uuid(BENCHMARK_COMPANION_SEED)
    # TRUNCATE before INSERT for the same state-leakage reason as the follow/tweet
    # seeders: a prior run at a different cohort size would leave stale rows that skew
    # the cached counts. CASCADE is unnecessary — nothing FK-references these tables.
    sql = f"""
TRUNCATE TABLE likes, replies, retweets;

INSERT INTO replies (id, tweet_id, user_id, content, created_at)
SELECT
    {_uuid_expr('reply_md5')} AS id,
    {_uuid_expr('tweet_md5')} AS tweet_id,
    '{companion_id}'::uuid AS user_id,
    'benchmark reply ' || n AS content,
    timezone('utc', now())
FROM generate_series(1, {total}) AS g(n)
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-tweet:' || n::text) AS tweet_md5,
           md5('jmeter-benchmark-reply:' || n::text) AS reply_md5
) AS ids
ON CONFLICT (id) DO NOTHING;

INSERT INTO retweets (id, original_tweet_id, retweeter_id, content, created_at)
SELECT
    {_uuid_expr('retweet_md5')} AS id,
    {_uuid_expr('tweet_md5')} AS original_tweet_id,
    '{companion_id}'::uuid AS retweeter_id,
    'benchmark retweet ' || n AS content,
    timezone('utc', now())
FROM generate_series(1, {total}) AS g(n)
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-tweet:' || n::text) AS tweet_md5,
           md5('jmeter-benchmark-retweet:' || n::text) AS retweet_md5
) AS ids
ON CONFLICT (id) DO NOTHING;

INSERT INTO likes (id, user_id, likeable_id, likeable_type, created_at)
SELECT
    {_uuid_expr('like_md5')} AS id,
    '{companion_id}'::uuid AS user_id,
    {_uuid_expr('tweet_md5')} AS likeable_id,
    'TWEET' AS likeable_type,
    timezone('utc', now())
FROM generate_series(1, {total}) AS g(n)
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-tweet:' || n::text) AS tweet_md5,
           md5('jmeter-benchmark-tweet-like:' || n::text) AS like_md5
) AS ids
ON CONFLICT (id) DO NOTHING;

INSERT INTO likes (id, user_id, likeable_id, likeable_type, created_at)
SELECT
    {_uuid_expr('like_md5')} AS id,
    '{companion_id}'::uuid AS user_id,
    {_uuid_expr('reply_md5')} AS likeable_id,
    'REPLY' AS likeable_type,
    timezone('utc', now())
FROM generate_series(1, {total}) AS g(n)
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-reply:' || n::text) AS reply_md5,
           md5('jmeter-benchmark-reply-like:' || n::text) AS like_md5
) AS ids
ON CONFLICT (id) DO NOTHING;
"""
    psql_exec("interaction-service-db", "interaction_service_db", sql)
    settle(INTERACTION_DB, ["likes", "replies", "retweets"])
