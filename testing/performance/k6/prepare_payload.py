#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[2]
PAYLOAD_ROOT = SCRIPT_DIR / "payload"
COMPOSE_FILE = REPO_ROOT / "infrastructure" / "compose" / "infrastructure.yml"


def repo_path(value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else (REPO_ROOT / path)


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return parsed


def run(cmd: list[str], *, input_text: str | None = None, quiet: bool = False) -> subprocess.CompletedProcess[str]:
    kwargs = {
        "check": True,
        "text": True,
        "capture_output": quiet,
    }
    if input_text is not None:
        kwargs["input"] = input_text
    return subprocess.run(cmd, **kwargs)


def compose_cmd(*args: str) -> list[str]:
    return ["docker", "compose", "--project-directory", str(REPO_ROOT), "-f", str(COMPOSE_FILE), *args]


def require_running_db() -> None:
    result = run(compose_cmd("ps", "-q", "interaction_service_db"), quiet=True)
    if not result.stdout.strip():
        print("ERROR: interaction_service_db is not running. Start infra first with ./run.sh runtime up infra benchmark", file=sys.stderr)
        sys.exit(1)


def deterministic_user_ids(key_count: int) -> list[str]:
    return [str(uuid.uuid5(uuid.NAMESPACE_URL, f"tweebyte-benchmark-user:{i}")) for i in range(1, key_count + 1)]


def write_following_cache_keys(keys_out: Path, user_ids: list[str]) -> None:
    keys_out.parent.mkdir(parents=True, exist_ok=True)
    keys_out.write_text("\n".join(user_ids) + "\n")


def seed_following_cache(keys_out: Path, key_count: int, follows_per_key: int) -> None:
    require_running_db()

    tmp_keys_path = "/tmp/following_cache_keys.txt"
    with keys_out.open("rb") as handle:
        subprocess.run(
            compose_cmd("exec", "-T", "interaction_service_db", "sh", "-lc", f"cat > '{tmp_keys_path}'"),
            check=True,
            stdin=handle,
        )

    sql = f"""
\\timing on
SET client_min_messages TO WARNING;
SET statement_timeout TO 0;
SET lock_timeout TO 0;
SET synchronous_commit TO off;

DROP TABLE IF EXISTS follows;

CREATE TABLE follows (
    id UUID PRIMARY KEY,
    follower_id UUID NOT NULL,
    followed_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (follower_id, followed_id)
);

CREATE TEMP TABLE benchmark_user_ids (
    seq BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE
);

\\copy benchmark_user_ids(user_id) FROM '{tmp_keys_path}' WITH (FORMAT text)

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

CREATE INDEX follows_follower_status_idx ON follows (follower_id, status);
CREATE INDEX follows_followed_status_idx ON follows (followed_id, status);

ANALYZE follows;

SELECT count(*) AS follow_rows FROM follows;
SELECT count(DISTINCT follower_id) AS distinct_followers FROM follows;
SELECT count(DISTINCT followed_id) AS distinct_followed FROM follows;
"""

    try:
        run(
            compose_cmd(
                "exec",
                "-T",
                "interaction_service_db",
                "sh",
                "-lc",
                "PGPASSWORD=postgres psql -v ON_ERROR_STOP=1 -U postgres -d interaction_service_db",
            ),
            input_text=sql,
        )
    finally:
        run(compose_cmd("exec", "-T", "interaction_service_db", "sh", "-lc", f"rm -f '{tmp_keys_path}'"), quiet=True)


def prepare_following_cache(args: argparse.Namespace) -> None:
    keys_out = repo_path(args.keys_out) if args.keys_out else PAYLOAD_ROOT / "following-cache" / f"n{args.key_count}_k{args.follows_per_key}" / "keys.txt"
    user_ids = deterministic_user_ids(args.key_count)

    write_following_cache_keys(keys_out, user_ids)
    seed_following_cache(keys_out, args.key_count, args.follows_per_key)

    print("Prepared following-cache payload:")
    print(f"  key_count={args.key_count}")
    print(f"  follows_per_key={args.follows_per_key}")
    print(f"  keys_file={keys_out}")
    print(f"  expected_rows={args.key_count * args.follows_per_key}")


def generate_ppm(ppm_path: Path, width: int, height: int) -> None:
    ppm_path.parent.mkdir(parents=True, exist_ok=True)
    with ppm_path.open("wb") as handle:
        handle.write(f"P6\n{width} {height}\n255\n".encode("ascii"))
        for y in range(height):
            row = bytearray()
            for x in range(width):
                row.extend(
                    (
                        (x * 7 + y * 3) % 256,
                        (x * 11 + y * 5) % 256,
                        (x * 13 + y * 17) % 256,
                    )
                )
            handle.write(row)


def prepare_image_upload(args: argparse.Namespace) -> None:
    fmt = args.image_format.lower()
    if fmt not in {"jpg", "jpeg", "png"}:
        print(f"ERROR: unsupported image format '{args.image_format}'. Use jpg, jpeg, or png.", file=sys.stderr)
        sys.exit(1)

    output_path = repo_path(args.output) if args.output else PAYLOAD_ROOT / "image-upload" / f"{args.width}x{args.height}.{fmt}"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    sips = shutil_which("sips")
    if not sips:
        print("ERROR: sips not found. Image payload generation requires macOS sips.", file=sys.stderr)
        sys.exit(1)

    with tempfile.TemporaryDirectory(prefix="tweebyte-image-payload-") as tmp_dir:
        ppm_path = Path(tmp_dir) / f"source_{args.width}x{args.height}.ppm"
        generate_ppm(ppm_path, args.width, args.height)

        sips_format = "jpeg" if fmt in {"jpg", "jpeg"} else fmt
        run([sips, "-s", "format", sips_format, str(ppm_path), "--out", str(output_path)], quiet=True)

    print("Prepared image-upload payload:")
    print(f"  width={args.width}")
    print(f"  height={args.height}")
    print(f"  format={fmt}")
    print(f"  payload_file={output_path}")


def shutil_which(binary: str) -> str | None:
    for directory in os.getenv("PATH", "").split(os.pathsep):
        candidate = Path(directory) / binary
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    return None


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare modern benchmark payloads.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    following_cache = subparsers.add_parser("following-cache", help="Seed the follow-cache benchmark dataset and write the matching keys file.")
    following_cache.add_argument("--key-count", type=positive_int, required=True)
    following_cache.add_argument("--follows-per-key", type=positive_int, required=True)
    following_cache.add_argument("--keys-out", help="Output path for the generated benchmark keys file.")
    following_cache.set_defaults(handler=prepare_following_cache)

    image_upload = subparsers.add_parser("image-upload", help="Generate an image payload for the image-upload benchmark.")
    image_upload.add_argument("--width", type=positive_int, required=True)
    image_upload.add_argument("--height", type=positive_int, required=True)
    image_upload.add_argument("--image-format", default="jpg")
    image_upload.add_argument("--output", help="Output path for the generated image payload.")
    image_upload.set_defaults(handler=prepare_image_upload)

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    args.handler(args)


if __name__ == "__main__":
    main()
