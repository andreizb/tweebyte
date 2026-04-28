#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import subprocess
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[2]
PAYLOAD_ROOT = SCRIPT_DIR / "payload"
COMPOSE_FILE = REPO_ROOT / "infrastructure" / "compose" / "infrastructure.yml"


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


def require_running(service: str) -> None:
    result = run(compose_cmd("ps", "-q", service), quiet=True)
    if not result.stdout.strip():
        print(f"ERROR: {service} is not running. Start infra first with ./run.sh runtime up infra prod", file=sys.stderr)
        sys.exit(1)


def psql_exec(service: str, database: str, sql: str, *, quiet: bool = False) -> subprocess.CompletedProcess[str]:
    require_running(service)
    return run(
        compose_cmd(
            "exec",
            "-T",
            service,
            "sh",
            "-lc",
            f"PGPASSWORD=postgres psql -v ON_ERROR_STOP=1 -U postgres -d {database} -At",
        ),
        input_text=sql,
        quiet=quiet,
    )


def query_rows(service: str, database: str, sql: str) -> list[str]:
    result = psql_exec(service, database, sql, quiet=True)
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def payload_dir(command: str, output_dir: str | None) -> Path:
    return repo_path(output_dir) if output_dir else (PAYLOAD_ROOT / command)


def write_csv(path: Path, header: list[str], rows: list[list[str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        writer.writerows(rows)


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
    psql_exec("user_service_db", "user_service_db", sql)


def benchmark_user_ids(count: int) -> list[str]:
    return query_rows(
        "user_service_db",
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
        "user_service_db",
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


def ensure_benchmark_tweets(count: int) -> None:
    ensure_benchmark_users(max(1, count))
    sql = f"""
WITH benchmark_users AS (
    SELECT
        id,
        row_number() OVER (ORDER BY split_part(split_part(email, '@', 1), '_', 3)::int) AS seq
    FROM users
    WHERE email LIKE 'benchmark_user_%@example.test'
    ORDER BY split_part(split_part(email, '@', 1), '_', 3)::int
    LIMIT {count}
),
tweet_numbers AS (
    SELECT n, ((n - 1) % {count}) + 1 AS user_seq
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
    'benchmark tweet ' || tweet_numbers.n AS content,
    timezone('utc', now()) - make_interval(secs => ({count} - tweet_numbers.n))
FROM tweet_numbers
JOIN benchmark_users ON benchmark_users.seq = tweet_numbers.user_seq
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-tweet:' || tweet_numbers.n::text) AS tweet_md5
) AS ids
ON CONFLICT (id) DO NOTHING;
"""
    psql_exec("tweet_service_db", "tweet_service_db", sql)


def benchmark_tweet_ids(count: int) -> list[str]:
    return query_rows(
        "tweet_service_db",
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
        "tweet_service_db",
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
        "tweet_service_db",
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
        "tweet_service_db",
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


def fetch_tweet_user_ids(count: int, seed_users: bool, seed_tweets: bool) -> list[str]:
    if seed_tweets:
        if not seed_users:
            ensure_benchmark_users(count)
        ensure_benchmark_tweets(count)
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


def prepare_user_summary(args: argparse.Namespace) -> None:
    out_dir = payload_dir("user-summary", args.output_dir)
    ids = fetch_user_ids(args.count, args.seed_users)
    output = out_dir / "user-ids.csv"
    write_csv(output, ["id"], [[value] for value in ids])

    print("Prepared JMeter payload:")
    print("  workload=user-summary")
    print(f"  count={args.count}")
    print(f"  user_ids_file={output}")


def prepare_follow_create(args: argparse.Namespace) -> None:
    out_dir = payload_dir("follow-create", args.output_dir)
    ids = fetch_user_ids(args.count, args.seed_users)
    rotated = ids[1:] + ids[:1] if len(ids) > 1 else list(ids)

    follower_output = out_dir / "follower-user-ids.csv"
    followed_output = out_dir / "followed-user-ids.csv"
    write_csv(follower_output, ["id"], [[value] for value in ids])
    write_csv(followed_output, ["uuid"], [[value] for value in rotated])

    print("Prepared JMeter payload:")
    print("  workload=follow-create")
    print(f"  count={args.count}")
    print(f"  follower_user_ids_file={follower_output}")
    print(f"  followed_user_ids_file={followed_output}")


def prepare_tweet_update(args: argparse.Namespace) -> None:
    out_dir = payload_dir("tweet-update", args.output_dir)
    ids = fetch_tweet_ids(args.count, args.seed_tweets)
    output = out_dir / "tweet-updates.csv"

    rows = []
    width = max(args.content_length, 24)
    for index, tweet_id in enumerate(ids, start=1):
        content = (f"benchmark tweet update {index} " + ("x" * width))[:width]
        rows.append([tweet_id, content])

    write_csv(output, ["tweet_id", "content"], rows)

    print("Prepared JMeter payload:")
    print("  workload=tweet-update")
    print(f"  count={args.count}")
    print(f"  tweet_updates_file={output}")


def prepare_tweets_get(args: argparse.Namespace) -> None:
    out_dir = payload_dir("tweets-get", args.output_dir)
    ids = fetch_tweet_user_ids(args.count, args.seed_users, args.seed_tweets)
    output = out_dir / "user-ids.csv"
    write_csv(output, ["id"], [[value] for value in ids])

    print("Prepared JMeter payload:")
    print("  workload=tweets-get")
    print(f"  count={args.count}")
    print(f"  user_ids_file={output}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare JMeter benchmark payloads.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    user_summary = subparsers.add_parser("user-summary", help="Prepare user ids for user-summary JMeter workloads.")
    user_summary.add_argument("--count", type=positive_int, required=True)
    user_summary.add_argument("--output-dir", help="Output directory for generated CSV files.")
    user_summary.add_argument("--seed-users", type=normalize_bool, default=False)
    user_summary.set_defaults(handler=prepare_user_summary)

    follow_create = subparsers.add_parser("follow-create", help="Prepare paired user-id payloads for follow-create.")
    follow_create.add_argument("--count", type=positive_int, required=True)
    follow_create.add_argument("--output-dir", help="Output directory for generated CSV files.")
    follow_create.add_argument("--seed-users", type=normalize_bool, default=False)
    follow_create.set_defaults(handler=prepare_follow_create)

    tweet_update = subparsers.add_parser("tweet-update", help="Prepare tweet updates for tweet-update.")
    tweet_update.add_argument("--count", type=positive_int, required=True)
    tweet_update.add_argument("--output-dir", help="Output directory for generated CSV files.")
    tweet_update.add_argument("--seed-users", type=normalize_bool, default=False)
    tweet_update.add_argument("--seed-tweets", type=normalize_bool, default=False)
    tweet_update.add_argument("--content-length", type=positive_int, default=120)
    tweet_update.set_defaults(handler=prepare_tweet_update)

    tweets_get = subparsers.add_parser("tweets-get", help="Prepare user ids for tweets-get.")
    tweets_get.add_argument("--count", type=positive_int, required=True)
    tweets_get.add_argument("--output-dir", help="Output directory for generated CSV files.")
    tweets_get.add_argument("--seed-users", type=normalize_bool, default=False)
    tweets_get.add_argument("--seed-tweets", type=normalize_bool, default=False)
    tweets_get.set_defaults(handler=prepare_tweets_get)

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    args.handler(args)


if __name__ == "__main__":
    main()
