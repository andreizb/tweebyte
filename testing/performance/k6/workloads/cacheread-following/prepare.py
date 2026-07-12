#!/usr/bin/env python3
"""Prepare following-cache k6 payload.

Three modes (positional, default ``seed``). The workload is a self-contained
lifecycle: ``seed`` populates exactly the world it needs, ``warm`` loads the
cache, and ``clean`` truncates every table it touched + flushes the cache.
Workloads run serially, so the next one re-seeds whatever world it needs.

  seed   Builds a consistent, self-contained cohort:
           1. user_service_db.users — the key_count benchmark users the follows
              graph references, seeded additively (ON CONFLICT DO NOTHING) with
              deterministic ``bench_user_<seq>`` handles. These are REAL rows, so
              a warm blob carries a real username and a production-like run never
              hits getFollowing()'s missing-user 404->500 footgun.
           2. interaction_service_db.follows — the fan-out matrix (key_count keys
              x follows_per_key) plus the matching keys.txt that k6 samples.
              Default keys path: ./payload/n<key_count>_k<follows_per_key>/keys.txt
              Interaction-service Flyway creates the follows schema on application
              boot. seed writes DATA only (TRUNCATE + INSERT), never DDL, so the
              table must already exist.

  warm   Pre-warms Redis with the following_cache::<uuid> entries the running
         interaction-service would otherwise build on a cache MISS, but with NO
         expiry (TTL=-1). This turns the workload into a pure cache-READ benchmark
         (Option B): every request is a hit, so the runtime getFollowing() never
         falls into its miss branch and never fans out to user-service. The miss
         branch is the only writer (60s TTL), so a pre-warmed key is served
         verbatim and its TTL is never overwritten.

         The warmed blob is faithful to the seeded graph (followed_ids replicate the
         modular fan-out) and its user_name is the REAL username read back from
         user_service_db (the cohort `seed` inserted) — no reconstructed/hardcoded
         handle. Each entry carries only the fields GET /following returns —
         followed_id, user_name, created_at.

  clean  Teardown: truncates every table this seeder populated (users + follows)
         and FLUSHALLs redis. following-cache/run.sh runs it after both stacks.
         The warm entries are written with no expiry on purpose; clean (and the
         per-stack FLUSHALL in run.sh) is what evicts them, so they never outlive
         the run. If you ever seed/warm by hand, run `clean` yourself when done.

Cohort ids are deterministic uuid5(NAMESPACE_URL, "tweebyte-benchmark-user:<n>")
written to keys.txt — the single source of truth shared by seed and warm.
"""
from __future__ import annotations

import argparse
import json
import sys
import tempfile
import uuid
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_K6_DIR = _HERE.parents[1]
sys.path.insert(0, str(_K6_DIR / "lib"))

from payload_lib import positive_int, repo_path  # noqa: E402

sys.path.insert(0, str(_K6_DIR.parent / "lib"))
from bench_hygiene import INTERACTION_DB, USER_DB, flush_redis, psql, redis_pipe, settle  # noqa: E402

# Fixed, valid ISO-8601 LocalDateTime for the warmed blobs. The exact instant is
# irrelevant (the hit path streams the bytes verbatim; k6 discards the body), it
# only needs to be a well-formed created_at so the payload size is realistic.
_WARM_CREATED_AT = "2026-05-31T00:00:00"


def deterministic_user_ids(key_count: int) -> list[str]:
    return [str(uuid.uuid5(uuid.NAMESPACE_URL, f"tweebyte-benchmark-user:{i}")) for i in range(1, key_count + 1)]


def write_keys(keys_out: Path, user_ids: list[str]) -> None:
    keys_out.parent.mkdir(parents=True, exist_ok=True)
    keys_out.write_text("\n".join(user_ids) + "\n")


def seed(keys_out: Path, key_count: int, follows_per_key: int) -> None:
    # \copy reads keys_out straight off the host filesystem (psql is the client), so
    # there is no stage-into-container step; psql() guards the published port itself.
    sql = f"""
\\timing on
SET client_min_messages TO WARNING;
SET statement_timeout TO 0;
SET lock_timeout TO 0;
SET synchronous_commit TO off;

-- The follows table, unique constraint, and read-path indexes are owned by
-- interaction-service Flyway, so seed must run after the app has migrated the DB.
-- The seeder only resets + repopulates DATA; it never issues DDL. TRUNCATE clears
-- the rows while preserving the indexes, so they stay warm.
TRUNCATE TABLE follows;

CREATE TEMP TABLE benchmark_user_ids (
    seq BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE
);

\\copy benchmark_user_ids(user_id) FROM '{keys_out}' WITH (FORMAT text)

INSERT INTO follows (id, follower_id, followed_id, status, created_at)
SELECT
    (
        substr(follow_md5, 1, 8) || '-' ||
        substr(follow_md5, 9, 4) || '-4' ||
        substr(follow_md5, 14, 3) || '-a' ||
        substr(follow_md5, 18, 3) || '-' ||
        substr(follow_md5, 21, 12)
    )::uuid AS id,
    source.user_id AS follower_id,
    target.user_id AS followed_id,
    'ACCEPTED' AS status,
    timezone('utc', now()) AS created_at
FROM benchmark_user_ids AS source
CROSS JOIN generate_series(1, {follows_per_key}) AS follow_offset(offset_value)
JOIN benchmark_user_ids AS target
  ON target.seq = ((source.seq - 1 + follow_offset.offset_value) % {key_count}) + 1
CROSS JOIN LATERAL (
    SELECT md5('follow:' || source.seq::text || ':' || follow_offset.offset_value::text) AS follow_md5
) AS ids;

SELECT count(*) AS follow_rows FROM follows;
SELECT count(DISTINCT follower_id) AS distinct_followers FROM follows;
SELECT count(DISTINCT followed_id) AS distinct_followed FROM follows;
"""

    psql(INTERACTION_DB, sql)
    settle(INTERACTION_DB, ["follows"])


def seed_users(keys_out: Path, key_count: int) -> None:
    """Additively seeds the benchmark cohort into user_service_db.users.

    The follows graph and the warm blobs both reference these key_count users by
    the deterministic uuid5 ids in keys.txt, so a real row must exist for each.
    Seeded additively (ON CONFLICT DO NOTHING, which covers every UNIQUE column —
    id/user_name/email/biography) so a re-seed is idempotent. The bench_user_<seq>
    / @bench.tweebyte handles are disjoint from other workloads' benchmark cohorts
    (e.g. user-profile's benchmark_user_<n> / @example.test), so they never collide.
    """
    # \copy reads keys_out straight off the host filesystem; psql() guards the port.
    sql = f"""
\\timing on
SET client_min_messages TO WARNING;
SET synchronous_commit TO off;

CREATE TEMP TABLE benchmark_user_ids (
    seq BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE
);

\\copy benchmark_user_ids(user_id) FROM '{keys_out}' WITH (FORMAT text)

INSERT INTO users (id, user_name, email, biography, password, is_private, birth_date, created_at)
SELECT
    user_id,
    'bench_user_' || seq,
    'bench_user_' || seq || '@bench.tweebyte',
    'following-cache bench user ' || seq,
    'benchmark-password',
    false,
    DATE '1990-01-01' + ((seq % 1000)::int),
    timezone('utc', now())
FROM benchmark_user_ids
ON CONFLICT DO NOTHING;

SELECT count(*) AS cohort_users
FROM users u
JOIN benchmark_user_ids c ON c.user_id = u.id;
"""

    psql(USER_DB, sql)
    settle(USER_DB, ["users"])


def _fetch_usernames(keys_out: Path, key_count: int) -> dict[str, str]:
    """Reads back the real user_name for each cohort id from user_service_db.

    Returns {user_id_str: user_name}. The warm blob uses the followed user's
    username, so this sources it from the actual seeded row rather than
    reconstructing the handle (Option 2: real DB data, not hardcoded).
    """
    # \copy reads keys_out off the host; psql(capture=True) adds -At so stdout parses
    # as `id|user_name` tuples — one read-back of the seeded cohort's real handles.
    sql = f"""
SET client_min_messages TO WARNING;
CREATE TEMP TABLE benchmark_user_ids (seq BIGSERIAL PRIMARY KEY, user_id UUID NOT NULL);
\\copy benchmark_user_ids(user_id) FROM '{keys_out}' WITH (FORMAT text)
SELECT u.id, u.user_name FROM users u JOIN benchmark_user_ids c ON c.user_id = u.id;
"""

    result = psql(USER_DB, sql, capture=True)

    mapping: dict[str, str] = {}
    for line in result.stdout.splitlines():
        line = line.strip()
        if "|" not in line:
            continue
        uid, _, uname = line.partition("|")
        mapping[uid] = uname
    return mapping


def _build_resp(user_ids: list[str], key_count: int, follows_per_key: int, handle, username_map: dict[str, str]) -> int:
    """Writes RESP `SET key value` commands (no expiry) for every following_cache
    key to ``handle``. Returns the number of keys written. The per-entry user_name
    is the followed user's REAL username (from ``username_map``, sourced from
    user_service_db), matching what getFollowing() would store on a live miss."""
    for source_seq in range(1, key_count + 1):
        follower_id = user_ids[source_seq - 1]
        entries = []
        for offset in range(1, follows_per_key + 1):
            followed_seq = ((source_seq - 1 + offset) % key_count) + 1
            followed_id = user_ids[followed_seq - 1]
            entries.append({
                "followed_id": followed_id,
                "user_name": username_map[followed_id],
                "created_at": _WARM_CREATED_AT,
            })
        key = f"following_cache::{follower_id}".encode()
        value = json.dumps(entries, separators=(",", ":")).encode()
        handle.write(b"*3\r\n$3\r\nSET\r\n$%d\r\n%s\r\n$%d\r\n%s\r\n" % (len(key), key, len(value), value))
    return key_count


def warm(keys_out: Path, key_count: int, follows_per_key: int) -> None:
    if keys_out.exists():
        user_ids = [line for line in keys_out.read_text().splitlines() if line]
        if len(user_ids) < key_count:
            print(f"keys file has {len(user_ids)} ids (< {key_count}); regenerating deterministically.")
            user_ids = deterministic_user_ids(key_count)
            write_keys(keys_out, user_ids)
    else:
        user_ids = deterministic_user_ids(key_count)
        write_keys(keys_out, user_ids)

    username_map = _fetch_usernames(keys_out, key_count)
    missing = [uid for uid in user_ids[:key_count] if uid not in username_map]
    if missing:
        print(
            f"ERROR: {len(missing)} of {key_count} cohort users are absent from user_service_db "
            f"(e.g. {missing[0]}). Run `prepare.py seed` first so warm can read real usernames.",
            file=sys.stderr,
        )
        sys.exit(1)

    with tempfile.NamedTemporaryFile(suffix=".resp", delete=False) as tmp:
        tmp_path = Path(tmp.name)
        written = _build_resp(user_ids, key_count, follows_per_key, tmp, username_map)

    try:
        redis_pipe(tmp_path)
    finally:
        tmp_path.unlink(missing_ok=True)

    print("Pre-warmed following-cache into Redis (TTL=-1 / no expiry):")
    print(f"  keys_written={written}")
    print(f"  follows_per_key={follows_per_key}")
    print("  user_name sourced from user_service_db (real cohort rows)")


def clean() -> None:
    """Teardown: truncate every table this seeder populated, then flush the cache.

    following-cache owns its fixture end-to-end (the user cohort it seeded in
    user_service_db, the follows graph in interaction_service_db, and the warmed
    following_cache:: keys in redis), so teardown wholesale-truncates users +
    follows and FLUSHALLs redis. Workloads run serially, so the next one re-seeds
    whatever world it needs — no surgical per-row bookkeeping. The follows TRUNCATE
    is guarded with to_regclass because the table may be absent on a clean-only
    invocation (the app, which owns the schema, may never have booted); users is
    part of the user-service schema so it always exists.
    """
    # follows may be absent on a clean-only invocation (the app, which owns the schema,
    # may never have booted), so guard its TRUNCATE with to_regclass; users always exists.
    psql(
        INTERACTION_DB,
        "DO $$ BEGIN IF to_regclass('public.follows') IS NOT NULL "
        "THEN EXECUTE 'TRUNCATE TABLE follows'; END IF; END $$;\n",
    )
    psql(USER_DB, "TRUNCATE TABLE users;\n")
    flush_redis()

    print("following-cache teardown complete: truncated users + follows, flushed redis.")


def prepare(args: argparse.Namespace) -> None:
    keys_out = (
        repo_path(args.keys_out)
        if args.keys_out
        else (_HERE / "payload" / f"n{args.key_count}_k{args.follows_per_key}" / "keys.txt")
    )

    if args.mode == "warm":
        warm(keys_out, args.key_count, args.follows_per_key)
        return

    if args.mode == "clean":
        clean()
        return

    user_ids = deterministic_user_ids(args.key_count)
    write_keys(keys_out, user_ids)
    seed_users(keys_out, args.key_count)
    seed(keys_out, args.key_count, args.follows_per_key)

    print("Prepared following-cache payload:")
    print(f"  key_count={args.key_count}")
    print(f"  follows_per_key={args.follows_per_key}")
    print(f"  keys_file={keys_out}")
    print(f"  expected_rows={args.key_count * args.follows_per_key}")
    print("  seeded user cohort into user_service_db (bench_user_<seq>)")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare following-cache k6 payload.")
    parser.add_argument("mode", nargs="?", default="seed", choices=["seed", "warm", "clean"],
                        help="seed: cohort users + follows table + keys.txt (default). "
                             "warm: pre-warm Redis (TTL=-1). clean: truncate users+follows, flush redis.")
    parser.add_argument("--key-count", type=positive_int, required=True)
    parser.add_argument("--follows-per-key", type=positive_int, required=True)
    parser.add_argument("--keys-out", help="Output path for the generated benchmark keys file (default: workload dir / payload / n<K>_k<F>/keys.txt).")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    prepare(args)


if __name__ == "__main__":
    main()
