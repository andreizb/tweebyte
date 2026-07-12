#!/usr/bin/env python3
"""Prepare file-download k6 payload.

Three modes (positional, default ``seed``). The workload is a self-contained
lifecycle: ``seed`` persists the one blob it needs, ``warm`` loads the
in-process cache, and ``clean`` deletes the blob it inserted. Workloads run
serially, so the next one re-seeds whatever world it needs.

  seed   Inserts ONE deterministic 256 KiB text/plain blob into
         user_service_db.media_assets — the asset GET /media/{id} streams.
         DATA only (INSERT ... ON CONFLICT (id) DO NOTHING); the media_assets
         schema is provisioned by user-service Flyway when the application boots,
         so native-local seeding runs after boot. Idempotent: both the id (deterministic
         uuid5) and the CRC32 checksum are fixed, so a re-seed is a no-op. The
         bytes are real lorem-ipsum text — the throttle is byte-count-driven and
         content-agnostic, so meaningful text reads cleaner than random junk and
         changes no benchmark number. The blob is exactly 256 KiB = 4 × the
         service's 64 KiB stream chunk.

  warm   GETs /media/{id} once against the running user-service (9091) so the
         in-process MediaCache loads the blob via the DB cache-miss path BEFORE
         k6 starts. Re-run PER STACK: the cache is in-JVM and dies with the
         container (unlike following-cache's shared Redis, which is warmed once).
         The k6 script also has its own warmup phase, but an explicit pre-warm
         guarantees the very first benchmarked request — even at conc=1000 — is a
         cache HIT, not a thundering herd of concurrent cache-miss DB reads.

  clean  Teardown: DELETE FROM media_assets (NOT TRUNCATE — users.profile_picture_id
         carries an ON DELETE SET NULL FK to media_assets, and Postgres refuses to
         TRUNCATE a FK target regardless of whether any row references it). PRESERVES
         the all-zeros default-avatar sentinel row, which is permanent by design
         (StaleMediaCleanupService hard-excludes it). Best-effort flushes the
         in-process cache via DELETE /media/cache if a stack happens to be up.

The blob id is deterministic uuid5(NAMESPACE_URL, "tweebyte-benchmark-media:file-download")
— the single source of truth shared by seed, warm, and the k6 GET path. seed also
writes it to ./payload/media_id.txt so run.sh can read it back without recomputing.
"""
from __future__ import annotations

import argparse
import base64
import subprocess
import sys
import uuid
import zlib
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_K6_DIR = _HERE.parents[1]
sys.path.insert(0, str(_K6_DIR.parent / "lib"))

from bench_hygiene import USER_DB, psql  # noqa: E402

# Deterministic blob id — the asset GET /media/{id} streams. uuid5 so seed, warm,
# and the k6 request path all agree without any out-of-band handshake.
MEDIA_ID = str(uuid.uuid5(uuid.NAMESPACE_URL, "tweebyte-benchmark-media:file-download"))

# All-zeros default-avatar sentinel (seeded by the Flyway baseline). Permanent by
# design — clean must never delete it.
_SENTINEL_ID = "00000000-0000-0000-0000-000000000000"

# 256 KiB = exactly 4 × the service's 64 KiB stream chunk.
_PAYLOAD_BYTES = 256 * 1024
_CONTENT_TYPE = "text/plain"
# Fixed, valid ISO-8601 LocalDateTime. The exact instant is irrelevant (the hit
# path streams the bytes verbatim; k6 discards the body) — it only needs to be a
# well-formed created_at the NOT NULL column accepts.
_CREATED_AT = "2026-05-31 00:00:00"

_LOREM = (
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod "
    "tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, "
    "quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo "
    "consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse "
    "cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non "
    "proident, sunt in culpa qui officia deserunt mollit anim id est laborum. "
)


def _payload() -> bytes:
    """Real lorem-ipsum text tiled to exactly _PAYLOAD_BYTES (256 KiB)."""
    base = _LOREM.encode("ascii")
    reps = (_PAYLOAD_BYTES // len(base)) + 1
    return (base * reps)[:_PAYLOAD_BYTES]


def _write_media_id(payload_dir: Path) -> Path:
    payload_dir.mkdir(parents=True, exist_ok=True)
    media_id_path = payload_dir / "media_id.txt"
    media_id_path.write_text(MEDIA_ID + "\n")
    return media_id_path


def seed(payload_dir: Path) -> None:
    data = _payload()
    checksum = zlib.crc32(data) & 0xFFFFFFFF
    b64 = base64.b64encode(data).decode("ascii")

    sql = f"""
SET client_min_messages TO WARNING;
SET synchronous_commit TO off;

-- media_assets and its schema (PK, the uq_media_assets_checksum UNIQUE, the
-- profile_picture_id FK) are owned by the user-service Flyway migration, applied at
-- app boot (no docker schema-init SQL exists in any topology) — so under native-local
-- this seed runs AFTER the app boots. The seeder writes DATA only (single INSERT),
-- never DDL. ON CONFLICT (id) DO NOTHING keeps a re-seed idempotent: id and checksum
-- are both deterministic, so the row is byte-identical every time.
INSERT INTO media_assets (id, content_type, size_bytes, checksum, created_at, data)
VALUES (
    '{MEDIA_ID}',
    '{_CONTENT_TYPE}',
    {len(data)},
    {checksum},
    TIMESTAMP '{_CREATED_AT}',
    decode('{b64}', 'base64')
)
ON CONFLICT (id) DO NOTHING;

SELECT id, content_type, size_bytes, checksum FROM media_assets WHERE id = '{MEDIA_ID}';
"""

    psql(USER_DB, sql)

    media_id_path = _write_media_id(payload_dir)
    print("Prepared file-download payload:")
    print(f"  media_id={MEDIA_ID}")
    print(f"  size_bytes={len(data)}")
    print(f"  checksum={checksum}")
    print(f"  content_type={_CONTENT_TYPE}")
    print(f"  media_id_file={media_id_path}")


def warm(base_url: str) -> None:
    """Loads the in-process MediaCache by GETting the blob once from the running
    stack. The cache is in-JVM, so this runs per stack (the prior stack's cache
    died with its container). The download streams 206 PARTIAL_CONTENT."""
    url = f"{base_url}/media/{MEDIA_ID}"
    result = subprocess.run(
        ["curl", "-sS", "-o", "/dev/null", "-w", "%{http_code}", url],
        check=True,
        text=True,
        capture_output=True,
    )
    code = result.stdout.strip()
    if code not in {"200", "206"}:
        print(
            f"ERROR: warm GET {url} returned HTTP {code} (expected 206). "
            "Run `prepare.py seed` first and confirm the stack is up.",
            file=sys.stderr,
        )
        sys.exit(1)
    print(f"Warmed file-download MediaCache via GET {url} (HTTP {code}).")


def clean(base_url: str) -> None:
    """Teardown: delete every media row this workload could have created, then
    best-effort flush the in-process cache.

    DELETE (not TRUNCATE) because users.profile_picture_id is a FK target. The
    sentinel row is preserved — it is permanent by design. The cache flush is
    best-effort: at the run.sh teardown point both stacks are already down, so the
    DELETE /media/cache simply no-ops; it only does real work if clean is invoked
    by hand while a stack is up.
    """
    psql(USER_DB, f"DELETE FROM media_assets WHERE id <> '{_SENTINEL_ID}';\n")

    subprocess.run(
        ["curl", "-sS", "-X", "DELETE", "-o", "/dev/null", f"{base_url}/media/cache"],
        check=False,
        capture_output=True,
    )

    print("file-download teardown complete: deleted media_assets (sentinel preserved), flushed cache.")


def prepare(args: argparse.Namespace) -> None:
    payload_dir = _HERE / "payload"

    if args.mode == "warm":
        warm(args.base_url)
        return

    if args.mode == "clean":
        clean(args.base_url)
        return

    seed(payload_dir)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare file-download k6 payload.")
    parser.add_argument("mode", nargs="?", default="seed", choices=["seed", "warm", "clean"],
                        help="seed: insert the 256 KiB blob into media_assets (default). "
                             "warm: GET /media/{id} once to load the in-process cache. "
                             "clean: delete media_assets rows (sentinel preserved), flush cache.")
    parser.add_argument("--base-url", default="http://localhost:9091",
                        help="user-service base URL for warm/clean (default: http://localhost:9091).")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    prepare(args)


if __name__ == "__main__":
    main()
