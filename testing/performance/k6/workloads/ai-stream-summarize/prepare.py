#!/usr/bin/env python3
"""Prepare ai-streaming k6 fixtures.

Seeds the single deterministic user the AI workload references. W2 (tool-use)
issues a mid-stream cross-service call that fetches this user; without the row
the call 404s and the measurement is meaningless. The k6 script and run_bench.sh
both default USER_ID/AI_USER_ID to 00000000-0000-0000-0000-000000000001, so that
is the default seeded id here too.

Seeding belongs in the harness — same as cacheread-following and cpu-image-preview — not
baked into the application under test, so the two stacks stay byte-for-byte equal
at the app layer and the benchmark user is created identically regardless of which
stack is up.

  seed   Inserts the deterministic AI user into user_service_db.users, additively
         (ON CONFLICT DO NOTHING), so re-runs are no-ops. Stack-agnostic: the DB
         lives in infrastructure.yml and is shared by async and reactive, so one
         seed covers both.

  clean  Deletes the seeded user by id. Optional teardown; the row is harmless and
         idempotent, so leaving it in place between runs is fine.
"""
from __future__ import annotations

import argparse
import sys
import uuid
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_K6_DIR = _HERE.parents[1]
sys.path.insert(0, str(_K6_DIR.parent / "lib"))

from bench_hygiene import USER_DB, psql  # noqa: E402

DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001"


def _canonical_uuid(value: str) -> str:
    # Parse + re-emit so an operator-supplied id can never inject SQL: psql here
    # runs a plain (non-parameterized) script over stdin, so the id is inlined.
    return str(uuid.UUID(value))


def _psql(sql: str) -> None:
    # Host-client psql on the published user_service_db port — reaches Docker-published
    # and native-local infra identically (never docker exec, which native-local has no
    # container for). Shared with the other workload seeders via bench_hygiene.
    psql(USER_DB, sql)


def seed(user_id: str) -> None:
    uid = _canonical_uuid(user_id)
    sql = f"""
SET client_min_messages TO WARNING;
INSERT INTO users (id, user_name, email, biography, password, is_private, birth_date, created_at)
VALUES (
    '{uid}'::uuid,
    'benchmark_user',
    'benchmark@tweebyte.local',
    'Deterministic user for AI W2 tool-use benchmarks.',
    '$2a$10$dummyhashnotusedinbenchmarks................',
    false,
    DATE '1990-01-01',
    timezone('utc', now())
)
ON CONFLICT DO NOTHING;
"""
    _psql(sql)
    print(f"Seeded ai-streaming benchmark user: {uid}")


def clean(user_id: str) -> None:
    uid = _canonical_uuid(user_id)
    _psql(f"DELETE FROM users WHERE id = '{uid}'::uuid;")
    print(f"Removed ai-streaming benchmark user: {uid}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare ai-streaming k6 fixtures.")
    parser.add_argument("command", nargs="?", default="seed", choices=["seed", "clean"])
    parser.add_argument(
        "--user-id",
        default=DEFAULT_USER_ID,
        help=f"Benchmark user UUID to seed/clean (default: {DEFAULT_USER_ID}).",
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()
    if args.command == "clean":
        clean(args.user_id)
    else:
        seed(args.user_id)


if __name__ == "__main__":
    main()
