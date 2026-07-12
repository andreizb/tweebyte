#!/usr/bin/env python3
"""Prepare the CPU-bound image-preview k6 payload.

The workload derives a
"sensitive-media preview" of a resident ORIGINAL: POST /media/{srcId}/preview runs
the unchanged CPU pipeline (3× Gaussian blur + Sobel + resize + JPEG) over the
seeded original's bytes and content-addresses the result. So the seed installs the
ONE original the run re-previews, and k6 hits /media/{srcId}/preview with a JSON
password body.

Three modes (positional, default ``seed``), mirroring blockio-file-download's lifecycle:

  seed   Inserts the canonical 256×256 Lena test image (payload/lena-256x256.jpg)
         into user_service_db.media_assets as an ORIGINAL — source_media_id NULL,
         access_hash NULL — at a deterministic uuid5 id. DATA only (INSERT ...
         ON CONFLICT (id) DO NOTHING); user-service Flyway creates the schema when
         the application boots, so native-local seeding runs after boot. Idempotent:
         both the id (uuid5) and the
         CRC32 checksum are fixed, so a re-seed is a no-op. Lena is THE standard
         edge-detection/blur test image — real content at the 256×256 dimensions the
         pipeline's blur/sobel cost is measured at, not synthetic junk. Writes the
         id to ./payload/source_media_id.txt so run.sh reads it back without
         recomputing.

  warm   POSTs /media/{srcId}/preview once (JSON password body) against the running
         user-service (9091) BEFORE k6 starts. This single call caches the source
         (resolveAsset DB-miss → cache) AND inserts+caches the derived preview
         (store content-addressed DB-miss → bcrypt → save). Without it the first
         k6 wave — up to conc=1000 — would herd: N concurrent source DB-reads, N
         bcrypt encodes, and N racing inserts on the uq_media_assets_checksum UNIQUE.
         After warm, every benchmarked request runs the pipeline (the measured CPU
         work) then a pure cache HIT on the store — no bcrypt, no DB. Re-run PER
         STACK: the cache is in-JVM and dies with the container.

  clean  Teardown: DELETE FROM media_assets — both the seeded original AND the one
         derived preview row the run inserts. DELETE (not TRUNCATE) because
         users.profile_picture_id carries an ON DELETE SET NULL FK to media_assets,
         and Postgres refuses to TRUNCATE a FK target. PRESERVES the all-zeros
         default-avatar sentinel row, which is permanent by design
         (StaleMediaCleanupService hard-excludes it). Best-effort flushes the
         in-process cache via DELETE /media/cache if a stack happens to be up.

The source id is uuid5(NAMESPACE_URL, "tweebyte-benchmark-media:image-upload-source")
— the single source of truth shared by seed, warm, and the k6 /preview path. The
preview password is a fixed benchmark constant (BENCHMARK_PASSWORD), shared with
warm and script.js; it is irrelevant to throughput because bcrypt runs only on the
one genuine insert.
"""
from __future__ import annotations

import argparse
import base64
import json
import subprocess
import sys
import uuid
import zlib
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_K6_DIR = _HERE.parents[1]
sys.path.insert(0, str(_K6_DIR.parent / "lib"))

from bench_hygiene import USER_DB, psql  # noqa: E402

# Deterministic source-original id — the asset POST /media/{srcId}/preview derives
# from. uuid5 so seed, warm, and the k6 request path all agree without a handshake.
SOURCE_MEDIA_ID = str(uuid.uuid5(uuid.NAMESPACE_URL, "tweebyte-benchmark-media:image-upload-source"))

# Fixed benchmark password for the preview gate. Shared with script.js. Irrelevant to
# throughput: bcrypt runs only on the single content-addressed insert (the store
# factory is invoked once per distinct pipeline output, i.e. once per run).
BENCHMARK_PASSWORD = "preview-gate-benchmark"

# All-zeros default-avatar sentinel (seeded by the Flyway baseline). Permanent by
# design — clean must never delete it.
_SENTINEL_ID = "00000000-0000-0000-0000-000000000000"

# Canonical 256×256 Lena test image — real content at the dimensions the blur/sobel
# cost is measured at. Committed alongside this script.
_SOURCE_IMAGE = _HERE / "payload" / "lena-256x256.jpg"
_CONTENT_TYPE = "image/jpeg"
# Fixed, valid ISO-8601 LocalDateTime. The exact instant is irrelevant (previews
# derive from the bytes; the column only needs a well-formed NOT NULL created_at).
_CREATED_AT = "2026-05-31 00:00:00"


def _write_source_id(payload_dir: Path) -> Path:
    payload_dir.mkdir(parents=True, exist_ok=True)
    source_id_path = payload_dir / "source_media_id.txt"
    source_id_path.write_text(SOURCE_MEDIA_ID + "\n")
    return source_id_path


def seed(payload_dir: Path) -> None:
    if not _SOURCE_IMAGE.exists():
        print(f"ERROR: source image not found: {_SOURCE_IMAGE}", file=sys.stderr)
        sys.exit(1)

    data = _SOURCE_IMAGE.read_bytes()
    checksum = zlib.crc32(data) & 0xFFFFFFFF
    b64 = base64.b64encode(data).decode("ascii")

    sql = f"""
SET client_min_messages TO WARNING;
SET synchronous_commit TO off;

-- media_assets and its schema (PK, the uq_media_assets_checksum UNIQUE, the
-- source_media_id self-FK, the profile_picture_id FK) are owned by the user-service
-- Flyway migration, applied at app boot (no docker schema-init SQL exists in any
-- topology) — so under native-local this seed runs AFTER the app boots. The seeder
-- writes DATA only (single INSERT), never DDL. The row
-- is an ORIGINAL: source_media_id and access_hash stay NULL (only previews are
-- gated). ON CONFLICT (id) DO NOTHING keeps a re-seed idempotent: id and checksum
-- are both deterministic, so the row is byte-identical every time.
--
-- The source is owned at a deterministic id, but media_assets enforces a global
-- UNIQUE(checksum). A pre-existing row carrying this checksum under a DIFFERENT id (a
-- stale artifact from an older seed shape or an un-torn-down run) would make the insert
-- below fail the checksum constraint, and ON CONFLICT (id) cannot absorb a checksum
-- collision. Reconcile first: drop any previews derived from such a stale source (their
-- source_media_id would otherwise be nulled by the ON DELETE SET NULL FK, leaving an
-- orphan), then drop the stale source itself. The canonical id is preserved.
DELETE FROM media_assets
WHERE source_media_id IN (
    SELECT id FROM media_assets WHERE checksum = {checksum} AND id <> '{SOURCE_MEDIA_ID}'
);
DELETE FROM media_assets WHERE checksum = {checksum} AND id <> '{SOURCE_MEDIA_ID}';

INSERT INTO media_assets (id, content_type, size_bytes, checksum, created_at, data)
VALUES (
    '{SOURCE_MEDIA_ID}',
    '{_CONTENT_TYPE}',
    {len(data)},
    {checksum},
    TIMESTAMP '{_CREATED_AT}',
    decode('{b64}', 'base64')
)
ON CONFLICT (id) DO NOTHING;

SELECT id, content_type, size_bytes, checksum, source_media_id FROM media_assets WHERE id = '{SOURCE_MEDIA_ID}';
"""

    psql(USER_DB, sql)

    source_id_path = _write_source_id(payload_dir)
    print("Prepared image-upload source original:")
    print(f"  source_media_id={SOURCE_MEDIA_ID}")
    print(f"  size_bytes={len(data)}")
    print(f"  checksum={checksum}")
    print(f"  content_type={_CONTENT_TYPE}")
    print(f"  source_id_file={source_id_path}")


def warm(base_url: str) -> None:
    """Derives the preview once from the running stack: caches the source and
    inserts+caches the content-addressed preview, so the first k6 wave is a pure
    cache HIT on the store rather than a herd of concurrent bcrypt+insert races. The
    cache is in-JVM, so this runs per stack (the prior stack's cache died with its
    container). POST /media/{srcId}/preview returns 200 with the preview id."""
    url = f"{base_url}/media/{SOURCE_MEDIA_ID}/preview"
    body = json.dumps({"password": BENCHMARK_PASSWORD})
    result = subprocess.run(
        ["curl", "-sS", "-o", "/dev/null", "-w", "%{http_code}",
         "-X", "POST", "-H", "Content-Type: application/json", "-d", body, url],
        check=True,
        text=True,
        capture_output=True,
    )
    code = result.stdout.strip()
    if code != "200":
        print(
            f"ERROR: warm POST {url} returned HTTP {code} (expected 200). "
            "Run `prepare.py seed` first and confirm the stack is up.",
            file=sys.stderr,
        )
        sys.exit(1)
    print(f"Warmed image-upload preview via POST {url} (HTTP {code}).")


def clean(base_url: str) -> None:
    """Teardown: delete every media row this workload could have created (the seeded
    original AND the one derived preview), then best-effort flush the in-process
    cache.

    DELETE (not TRUNCATE) because users.profile_picture_id is a FK target. The
    sentinel row is preserved — it is permanent by design. The cache flush is
    best-effort: at the run.sh teardown point both stacks are already down, so the
    DELETE /media/cache simply no-ops; it only does real work if clean is invoked by
    hand while a stack is up.
    """
    psql(USER_DB, f"DELETE FROM media_assets WHERE id <> '{_SENTINEL_ID}';\n")

    subprocess.run(
        ["curl", "-sS", "-X", "DELETE", "-o", "/dev/null", f"{base_url}/media/cache"],
        check=False,
        capture_output=True,
    )

    print("image-upload teardown complete: deleted media_assets (sentinel preserved), flushed cache.")


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
    parser = argparse.ArgumentParser(description="Prepare image-upload k6 payload.")
    parser.add_argument("mode", nargs="?", default="seed", choices=["seed", "warm", "clean"],
                        help="seed: insert the Lena source original into media_assets (default). "
                             "warm: POST /media/{srcId}/preview once to cache source + preview. "
                             "clean: delete media_assets rows (sentinel preserved), flush cache.")
    parser.add_argument("--base-url", default="http://localhost:9091",
                        help="user-service base URL for warm/clean (default: http://localhost:9091).")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    prepare(args)


if __name__ == "__main__":
    main()
