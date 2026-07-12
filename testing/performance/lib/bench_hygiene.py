#!/usr/bin/env python3
"""Shared benchmark DB/cache hygiene — the single source for the cross-cutting
seed-lifecycle steps every workload should perform identically.

Both drivers seed in Python (JMeter via testing/performance/jmeter/lib/payload_lib.py,
k6 via testing/performance/k6/workloads/<name>/prepare.py) and both reach Postgres /
Redis the same way: host ``psql`` / ``redis-cli`` against the published ports. Those ports
are identical whether infra is Docker-published or native-local (deployment/local/local-infra.sh
binds the same 54321/54322/54323 + 63790), so this module is topology-agnostic — no
``docker exec``. This centralises the steps that were previously scattered across the trees:

  settle(db, tables)   POST-seed: VACUUM ANALYZE the freshly (re)loaded tables. A
                       TRUNCATE + bulk INSERT leaves the visibility map empty, so every
                       "Index Only Scan" silently degrades to index-scan + heap-fetch
                       until autovacuum eventually visits, making early cells measure
                       a degraded DB state. VACUUM
                       rebuilds the visibility map (ANALYZE alone does NOT) and refreshes
                       planner statistics. Benchmark hygiene, identical on both stacks,
                       never tuning.
  reset(db, tables)    PRE-seed: TRUNCATE the listed tables on their database.
  flush_redis()        FLUSHALL the shared Redis (seed-time / teardown cache reset;
                       distinct from the run.sh between-stack cold-cache flush in
                       lib/cleanup.sh, which stays where it is).

The seeders also share the two host-client primitives this module owns, so the port
map lives in exactly one place:

  psql(db, sql)        Run SQL against a service database via host psql on its
                       published port. ``\\copy`` resolves on the HOST filesystem, so a
                       keys file loads directly with no container-staging step.
                       ``capture=True`` returns parseable (-At) stdout for read-backs.
  redis_pipe(path)     Pipe a RESP command stream (bulk SET ...) into Redis via host
                       redis-cli --pipe.

The uniform per-workload lifecycle these compose into — the contract every seeder is
being converged onto ("module now, retrofit as I go", operator 2026-06-12):

    seed   ->  reset(...) the tables, bulk-INSERT, then settle(...) them
    warm   ->  (workload-specific cache warm; unchanged)
    clean  ->  reset(...) / DELETE the tables + flush_redis()

DB topology is fixed (three Postgres instances, one per service); callers pass the
matching (service, database) constant so VACUUM/TRUNCATE hit the right instance. There
is deliberately no blind "truncate everything" — each seeder names its own tables.
"""
from __future__ import annotations

import socket
import subprocess
import sys

# (label, database name) for each service's Postgres instance. The label is informational.
USER_DB = ("user-service-db", "user_service_db")
TWEET_DB = ("tweet-service-db", "tweet_service_db")
INTERACTION_DB = ("interaction-service-db", "interaction_service_db")

# Host-published datastore ports — Docker compose publishes these and local-infra.sh binds
# the same, so host clients reach either topology identically (no docker exec).
_DB_PORTS = {
    "user_service_db": 54321,
    "tweet_service_db": 54322,
    "interaction_service_db": 54323,
}
REDIS_SERVICE = "redis"
REDIS_PORT = 63790


def _run(cmd: list[str], *, input_text: str | None = None) -> subprocess.CompletedProcess[str]:
    kwargs: dict = {"check": True, "text": True, "capture_output": True}
    if input_text is not None:
        kwargs["input"] = input_text
    return subprocess.run(cmd, **kwargs)


def _require_port(port: int, label: str) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(1.0)
        if sock.connect_ex(("127.0.0.1", port)) != 0:
            print(
                f"ERROR: {label} is not reachable on 127.0.0.1:{port}. Start infra first "
                "(./run.sh runtime up infra benchmark, or deployment/local/local-infra.sh up).",
                file=sys.stderr,
            )
            sys.exit(1)


def psql(db: tuple[str, str], sql: str, *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    """Run ``sql`` against the service database named by ``db`` (one of the
    (service, database) constants) via host psql on its published port — reaching
    Docker-published or native-local infra identically, never docker exec.

    ``\\copy`` meta-commands resolve on the HOST filesystem (psql is the client), so a
    seeder can ``\\copy`` straight from its keys file with no container-staging step.
    No ``-1`` single-transaction wrapper: VACUUM cannot run inside a transaction block,
    so each statement piped here must autocommit on its own.

    Streams psql output to the console unless ``capture=True``, which captures stdout
    and adds ``-At`` (unaligned, tuples-only) so a read-back parses as ``col|col`` rows.
    """
    _service, database = db
    port = _DB_PORTS[database]
    _require_port(port, f"{database} Postgres")
    flags = "-q -v ON_ERROR_STOP=1 -U postgres"
    if capture:
        flags += " -At"
    return subprocess.run(
        ["sh", "-lc", f"PGPASSWORD=postgres psql -h 127.0.0.1 -p {port} {flags} -d {database}"],
        check=True,
        text=True,
        input=sql,
        capture_output=capture,
    )


def redis_pipe(resp_path) -> None:
    """Pipe a RESP-encoded command stream (bulk SET ...) into the shared Redis via host
    redis-cli --pipe on the published port — the bulk-warm counterpart to flush_redis."""
    _require_port(REDIS_PORT, "Redis")
    with open(resp_path, "rb") as handle:
        subprocess.run(
            ["redis-cli", "-h", "127.0.0.1", "-p", str(REDIS_PORT), "--pipe"],
            check=True,
            stdin=handle,
        )


def redis_cli(*args: str, input_text: str | None = None) -> str:
    """Run a one-off redis-cli command against the shared Redis on its published port and
    return the trimmed stdout (e.g. ``OK``, a TTL, a value). For single SET/GET/TTL seeds;
    bulk loads use redis_pipe. ``-x`` reads the final argument's value from input_text."""
    _require_port(REDIS_PORT, "Redis")
    return _run(
        ["redis-cli", "-h", "127.0.0.1", "-p", str(REDIS_PORT), *args],
        input_text=input_text,
    ).stdout.strip()


def settle(db: tuple[str, str], tables: list[str]) -> None:
    """POST-seed: VACUUM ANALYZE the freshly loaded tables (rebuild the visibility map
    + refresh planner stats) so the early benchmark cells don't measure a degraded DB.
    Symmetric hygiene, not tuning. ``db`` is one of the (service, database) constants.
    One VACUUM per table keeps the statement valid on every Postgres version."""
    if not tables:
        return
    _service, database = db
    stmts = "".join(f"VACUUM (ANALYZE) {table};\n" for table in tables)
    psql(db, stmts)
    print(f"[hygiene] settled {database}: VACUUM ANALYZE {', '.join(tables)}", file=sys.stderr)


def reset(db: tuple[str, str], tables: list[str], *, cascade: bool = False) -> None:
    """PRE-seed: TRUNCATE the listed tables on their database. ``cascade=True`` only
    when an FK child would otherwise dangle (e.g. tweets <- mentions / tweet_hashtag)."""
    if not tables:
        return
    _service, database = db
    table_list = ", ".join(tables)
    suffix = " CASCADE" if cascade else ""
    psql(db, f"TRUNCATE TABLE {table_list}{suffix};\n")
    print(f"[hygiene] reset {database}: TRUNCATE {table_list}{suffix}", file=sys.stderr)


def flush_redis() -> None:
    """FLUSHALL the shared Redis (seed-time / teardown cache reset). Distinct from the
    run.sh between-stack cold-cache flush in lib/cleanup.sh."""
    _require_port(REDIS_PORT, "Redis")
    _run(["redis-cli", "-h", "127.0.0.1", "-p", str(REDIS_PORT), "FLUSHALL"])
    print("[hygiene] flushed Redis (FLUSHALL)", file=sys.stderr)
