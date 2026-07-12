#!/usr/bin/env python3
"""Prepare getTweet k6 payload.

Counterpart of cacheread-following/prepare.py for the cross-service-fan-out single-tweet
READ. ``GET /tweets/{tweetId}`` resolves the tweet from tweet_service_db, then
enrichSingleTweetDto fans out per request:
  - interaction-service: likes count, replies count, retweets count, replies list
    (each reply author is then resolved via a user-service getUserSummary);
  - tweet_service_db (local r2dbc): the tweet's hashtag + mention rows.
So a faithful fixture must seed, for every benchmarked tweet, REAL rows on each of
those legs — otherwise the fan-out returns empties (a degenerate read) or, worse, a
missing reply-author 404s and fails the whole GET (a reply author absent from
user_service_db raises UserNotFoundException, which propagates out of the Mono.zip).

Two modes (positional, default ``seed``). Self-contained lifecycle like every other
workload: ``seed`` populates exactly the world it needs across the three service DBs,
``clean`` truncates every table it touched + flushes the interaction-service count
caches in Redis. Workloads run serially, so the next one re-seeds whatever it needs.
There is no ``warm`` mode: this read is not a pre-warmed Redis blob (that is
cacheread-following). getTweet's only caches are interaction-service's 60s count-caches
and user-service's 60s user-summary cache, which k6's own warmup phase fills the
production way (cold miss -> DB read + fan-out -> cache at the default 60s TTL).

  seed   Builds a consistent, self-contained cohort spanning the three service DBs:
           1. user_service_db.users — the USER_POOL benchmark users that the tweet
              authors, reply authors, like actors, retweeters and mention targets
              reference, seeded additively (ON CONFLICT DO NOTHING) with deterministic
              gettweet_user_<seq> handles. REAL rows so the per-reply getUserSummary
              fan-out resolves (a missing reply author would 404 -> 500 the GET).
           2. tweet_service_db.tweets — TWEET_COUNT tweets (the keys k6 samples),
              each authored by a pool user; plus, per tweet, one tweet_hashtag link to
              a shared hashtags pool entry and one mentions row, so the local hashtag +
              mention reads return real rows.
           3. interaction_service_db.{replies,likes,retweets} — per tweet:
              REPLIES_PER_TWEET replies (each by a pool user, so the replies-list
              author fan-out resolves), LIKES_PER_TWEET tweet-likes (distinct actors,
              the uq_likes_user_likeable unique forces it) and RETWEETS_PER_TWEET
              retweets, so all three enrichment counts are non-zero and the replies
              list is non-empty.
         The seeded tweet UUIDs are written to keys.txt — the single source of truth
         k6 samples. The hot subset is just the first HOT_COUNT lines (the warmed set
         the run.sh warmup phase keeps hot at the 60s count-cache TTL).

         All schemas (tweets/hashtags/mentions/tweet_hashtag, replies/likes/retweets,
         users) are Flyway-owned (each service's V1__baseline.sql), created by the apps
         at boot. seed writes DATA only (TRUNCATE + INSERT), never DDL, so under
         native-local it must run AFTER the apps have migrated their DBs.

  clean  Teardown: truncates every table this seeder populated across the three DBs
         (users; tweets + hashtags + mentions + tweet_hashtag; replies + likes +
         retweets) and FLUSHALLs redis (evicting the interaction-service count-caches
         and user-service summary-cache this workload warmed). getTweet/run.sh runs it
         after both stacks under the Docker topologies; native-local tears its
         ephemeral infra down wholesale instead. If you seed by hand, run `clean`
         yourself when done.

Cohort ids are deterministic so seed is reproducible and re-runnable: tweet ids are
uuid5(NAMESPACE_URL, "tweebyte-gettweet-tweet:<n>") written to keys.txt; pool user ids
are uuid5(NAMESPACE_URL, "tweebyte-gettweet-user:<n>"); the per-tweet relation row ids
are md5-derived v4-shaped uuids. Both stacks therefore measure the identical world.
"""
from __future__ import annotations

import argparse
import sys
import uuid
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_K6_DIR = _HERE.parents[1]
sys.path.insert(0, str(_K6_DIR / "lib"))

from payload_lib import positive_int, repo_path  # noqa: E402

sys.path.insert(0, str(_K6_DIR.parent / "lib"))
from bench_hygiene import (  # noqa: E402
    INTERACTION_DB,
    TWEET_DB,
    USER_DB,
    flush_redis,
    psql,
    settle,
)

# Size of the shared user pool that every tweet/reply/like/retweet/mention references.
# Small relative to TWEET_COUNT (authors and reply authors are drawn modularly from it),
# so the cohort is realistic without one user per row. Reply authors MUST exist here or
# the per-reply getUserSummary fan-out 404s and fails the GET; see module docstring.
USER_POOL = 10000

# Size of the shared hashtag pool. Each tweet links to one pool hashtag (modular), so the
# hashtag read returns a real row without minting a distinct hashtag per tweet.
HASHTAG_POOL = 1000


def deterministic_tweet_ids(tweet_count: int) -> list[str]:
    return [str(uuid.uuid5(uuid.NAMESPACE_URL, f"tweebyte-gettweet-tweet:{i}")) for i in range(1, tweet_count + 1)]


def deterministic_user_ids(user_pool: int) -> list[str]:
    return [str(uuid.uuid5(uuid.NAMESPACE_URL, f"tweebyte-gettweet-user:{i}")) for i in range(1, user_pool + 1)]


def write_keys(keys_out: Path, tweet_ids: list[str]) -> None:
    keys_out.parent.mkdir(parents=True, exist_ok=True)
    keys_out.write_text("\n".join(tweet_ids) + "\n")


def _users_out(keys_out: Path) -> Path:
    # Sidecar pool file next to keys.txt. keys.txt is the tweet ids k6 samples; the user
    # pool is a separate cohort that several tables \copy from, so it lives in its own file.
    return keys_out.parent / "users.txt"


def seed_users(users_out: Path, user_pool: int) -> None:
    """Additively seeds the shared USER_POOL into user_service_db.users.

    Tweet authors, reply authors, like actors, retweeters and mention targets all draw
    from this pool, so a real row must exist for each — most importantly the reply
    authors, whose getUserSummary the enrichment dereferences (a 404 there 500s the GET).
    Seeded additively (ON CONFLICT DO NOTHING covers every UNIQUE column: id / user_name /
    email). The gettweet_user_<seq> / @gettweet.bench handles are disjoint from the other
    workloads' cohorts (cacheread-following's bench_user_<n> / @bench.tweebyte, dbread-fanout-user-profile's
    benchmark_user_<n> / @example.test), so they never collide on a shared user DB.
    """
    sql = f"""
\\timing on
SET client_min_messages TO WARNING;
SET synchronous_commit TO off;

CREATE TEMP TABLE gettweet_user_ids (
    seq BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE
);

\\copy gettweet_user_ids(user_id) FROM '{users_out}' WITH (FORMAT text)

INSERT INTO users (id, user_name, email, biography, password, is_private, birth_date, created_at)
SELECT
    user_id,
    'gettweet_user_' || seq,
    'gettweet_user_' || seq || '@gettweet.bench',
    'getTweet bench user ' || seq,
    'benchmark-password',
    false,
    DATE '1990-01-01' + ((seq % 1000)::int),
    timezone('utc', now())
FROM gettweet_user_ids
ON CONFLICT DO NOTHING;

SELECT count(*) AS cohort_users
FROM users u
JOIN gettweet_user_ids c ON c.user_id = u.id;
"""
    psql(USER_DB, sql)
    settle(USER_DB, ["users"])


def seed_tweets(keys_out: Path, users_out: Path, tweet_count: int, user_pool: int) -> None:
    """Seeds the tweets cohort and its local relations in tweet_service_db.

    Per tweet (TRUNCATE + INSERT, data only — the schema is Flyway-owned):
      - tweets:        one row, author = pool user (modular), realistic content/created_at.
      - hashtags:      a shared HASHTAG_POOL of distinct hashtag rows (seeded once).
      - tweet_hashtag: one link per tweet to a pool hashtag (modular). The hashtag read
                       JOINs hashtags via tweet_hashtag, so the LINK row is what makes the
                       hashtags[] non-empty — a bare hashtags row is not enough.
      - mentions:      one row per tweet (FK -> tweets.id), mentioning a pool user. The
                       mention's user_id is NOT dereferenced on this path (the mapper emits
                       it raw), but it is a real pool id for consistency.
    Order matters for the FKs: hashtags + tweets before tweet_hashtag, tweets before
    mentions. tweet_hashtag/mentions are children of tweets, so the reset cascades.
    """
    sql = f"""
\\timing on
SET client_min_messages TO WARNING;
SET statement_timeout TO 0;
SET lock_timeout TO 0;
SET synchronous_commit TO off;

-- Data-only reset of this workload's tweet-side tables. tweet_hashtag + mentions are FK
-- children of tweets; CASCADE clears them together. Schema (PKs/FKs/indexes) is owned by
-- tweet-service's Flyway V1__baseline.sql and is preserved by TRUNCATE.
TRUNCATE TABLE tweets, hashtags, mentions, tweet_hashtag CASCADE;

CREATE TEMP TABLE gettweet_tweet_ids (
    seq BIGSERIAL PRIMARY KEY,
    tweet_id UUID NOT NULL UNIQUE
);
\\copy gettweet_tweet_ids(tweet_id) FROM '{keys_out}' WITH (FORMAT text)

CREATE TEMP TABLE gettweet_user_ids (
    seq BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE
);
\\copy gettweet_user_ids(user_id) FROM '{users_out}' WITH (FORMAT text)

-- Shared hashtag pool (HASHTAG_POOL distinct rows). Deterministic md5-derived v4 ids.
INSERT INTO hashtags (id, text)
SELECT
    (
        substr(h_md5, 1, 8) || '-' || substr(h_md5, 9, 4) || '-4' || substr(h_md5, 14, 3)
        || '-a' || substr(h_md5, 18, 3) || '-' || substr(h_md5, 21, 12)
    )::uuid,
    'gettweet_tag_' || g.n
FROM generate_series(1, {HASHTAG_POOL}) AS g(n)
CROSS JOIN LATERAL (SELECT md5('gettweet-hashtag:' || g.n::text) AS h_md5) AS m;

-- Tweets: author = pool user (modular over USER_POOL).
INSERT INTO tweets (id, user_id, version, content, created_at)
SELECT
    t.tweet_id,
    author.user_id,
    0,
    'getTweet benchmark tweet #' || t.seq,
    timezone('utc', now()) - (t.seq % 100000) * interval '1 second'
FROM gettweet_tweet_ids AS t
JOIN gettweet_user_ids AS author
  ON author.seq = ((t.seq - 1) % {user_pool}) + 1;

-- One hashtag link per tweet (to a pool hashtag, modular). This LINK row is what makes
-- the hashtag read non-empty (it JOINs hashtags through tweet_hashtag).
INSERT INTO tweet_hashtag (tweet_id, hashtag_id)
SELECT
    t.tweet_id,
    h.id
FROM gettweet_tweet_ids AS t
JOIN (
    SELECT row_number() OVER (ORDER BY text) AS seq, id FROM hashtags
) AS h ON h.seq = ((t.seq - 1) % {HASHTAG_POOL}) + 1;

-- One mention per tweet (FK -> tweets.id), mentioning a pool user (modular).
INSERT INTO mentions (id, user_id, text, tweet_id)
SELECT
    (
        substr(m_md5, 1, 8) || '-' || substr(m_md5, 9, 4) || '-4' || substr(m_md5, 14, 3)
        || '-a' || substr(m_md5, 18, 3) || '-' || substr(m_md5, 21, 12)
    )::uuid,
    mentioned.user_id,
    '@gettweet_user_' || mentioned.seq,
    t.tweet_id
FROM gettweet_tweet_ids AS t
JOIN gettweet_user_ids AS mentioned
  ON mentioned.seq = ((t.seq) % {user_pool}) + 1
CROSS JOIN LATERAL (SELECT md5('gettweet-mention:' || t.seq::text) AS m_md5) AS ids;

SELECT count(*) AS tweet_rows FROM tweets;
SELECT count(*) AS hashtag_rows FROM hashtags;
SELECT count(*) AS tweet_hashtag_rows FROM tweet_hashtag;
SELECT count(*) AS mention_rows FROM mentions;
"""
    psql(TWEET_DB, sql)
    settle(TWEET_DB, ["tweets", "hashtags", "mentions", "tweet_hashtag"])


def seed_interactions(
    keys_out: Path,
    users_out: Path,
    tweet_count: int,
    user_pool: int,
    replies_per_tweet: int,
    likes_per_tweet: int,
    retweets_per_tweet: int,
) -> None:
    """Seeds the per-tweet interaction rows in interaction_service_db.

    Per tweet (TRUNCATE + INSERT, data only):
      - replies:  REPLIES_PER_TWEET rows, tweet_id -> the tweet, user_id -> a pool user
                  (each DISTINCT per reply, so the replies-list author fan-out resolves a
                  real user-summary for every one). The endpoint returns the newest
                  min(M,10) ordered by (created_at DESC, id DESC); staggered created_at
                  gives a stable order.
      - likes:    LIKES_PER_TWEET rows (likeable_id -> tweet, likeable_type='TWEET'),
                  each by a DISTINCT pool actor — uq_likes_user_likeable (user_id,
                  likeable_id, likeable_type) forbids duplicate actors on one tweet, so
                  likes_per_tweet must be <= USER_POOL.
      - retweets: RETWEETS_PER_TWEET rows (original_tweet_id -> tweet, retweeter -> pool).
    All counts therefore non-zero and the replies list non-empty. tweet_id / likeable_id /
    original_tweet_id are cross-DB references to tweet_service_db (no FK, by design);
    user_id / retweeter_id reference the seeded pool in user_service_db.
    """
    sql = f"""
\\timing on
SET client_min_messages TO WARNING;
SET statement_timeout TO 0;
SET lock_timeout TO 0;
SET synchronous_commit TO off;

-- Data-only reset of this workload's interaction tables (schema is interaction-service
-- Flyway-owned). follows is left untouched — it is cacheread-following's table, not ours.
TRUNCATE TABLE replies, likes, retweets;

CREATE TEMP TABLE gettweet_tweet_ids (
    seq BIGSERIAL PRIMARY KEY,
    tweet_id UUID NOT NULL UNIQUE
);
\\copy gettweet_tweet_ids(tweet_id) FROM '{keys_out}' WITH (FORMAT text)

CREATE TEMP TABLE gettweet_user_ids (
    seq BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE
);
\\copy gettweet_user_ids(user_id) FROM '{users_out}' WITH (FORMAT text)

-- Replies: REPLIES_PER_TWEET per tweet, each author a distinct pool user (modular). The
-- per-reply getUserSummary resolves because every user_id is a seeded pool row.
INSERT INTO replies (id, tweet_id, user_id, content, created_at)
SELECT
    (
        substr(r_md5, 1, 8) || '-' || substr(r_md5, 9, 4) || '-4' || substr(r_md5, 14, 3)
        || '-a' || substr(r_md5, 18, 3) || '-' || substr(r_md5, 21, 12)
    )::uuid,
    t.tweet_id,
    author.user_id,
    'getTweet reply ' || off.n || ' on tweet ' || t.seq,
    timezone('utc', now()) - off.n * interval '1 second'
FROM gettweet_tweet_ids AS t
CROSS JOIN generate_series(1, {replies_per_tweet}) AS off(n)
JOIN gettweet_user_ids AS author
  ON author.seq = ((t.seq - 1 + off.n) % {user_pool}) + 1
CROSS JOIN LATERAL (SELECT md5('gettweet-reply:' || t.seq::text || ':' || off.n::text) AS r_md5) AS ids;

-- Likes on the tweet: LIKES_PER_TWEET distinct actors (modular). uq_likes_user_likeable
-- forbids a repeated actor on the same tweet, so distinct offsets give distinct actors.
INSERT INTO likes (id, user_id, likeable_id, likeable_type, created_at)
SELECT
    (
        substr(l_md5, 1, 8) || '-' || substr(l_md5, 9, 4) || '-4' || substr(l_md5, 14, 3)
        || '-a' || substr(l_md5, 18, 3) || '-' || substr(l_md5, 21, 12)
    )::uuid,
    actor.user_id,
    t.tweet_id,
    'TWEET',
    timezone('utc', now()) - off.n * interval '1 second'
FROM gettweet_tweet_ids AS t
CROSS JOIN generate_series(1, {likes_per_tweet}) AS off(n)
JOIN gettweet_user_ids AS actor
  ON actor.seq = ((t.seq - 1 + off.n) % {user_pool}) + 1
CROSS JOIN LATERAL (SELECT md5('gettweet-like:' || t.seq::text || ':' || off.n::text) AS l_md5) AS ids;

-- Retweets of the tweet: RETWEETS_PER_TWEET rows (retweeter a pool user, modular).
INSERT INTO retweets (id, original_tweet_id, retweeter_id, content, created_at)
SELECT
    (
        substr(rt_md5, 1, 8) || '-' || substr(rt_md5, 9, 4) || '-4' || substr(rt_md5, 14, 3)
        || '-a' || substr(rt_md5, 18, 3) || '-' || substr(rt_md5, 21, 12)
    )::uuid,
    t.tweet_id,
    rt.user_id,
    NULL,
    timezone('utc', now()) - off.n * interval '1 second'
FROM gettweet_tweet_ids AS t
CROSS JOIN generate_series(1, {retweets_per_tweet}) AS off(n)
JOIN gettweet_user_ids AS rt
  ON rt.seq = ((t.seq - 1 + off.n) % {user_pool}) + 1
CROSS JOIN LATERAL (SELECT md5('gettweet-retweet:' || t.seq::text || ':' || off.n::text) AS rt_md5) AS ids;

SELECT count(*) AS reply_rows FROM replies;
SELECT count(*) AS like_rows FROM likes WHERE likeable_type = 'TWEET';
SELECT count(*) AS retweet_rows FROM retweets;
"""
    psql(INTERACTION_DB, sql)
    settle(INTERACTION_DB, ["replies", "likes", "retweets"])


def clean() -> None:
    """Teardown: truncate every table this seeder populated across the three DBs, then
    flush Redis (the interaction-service count-caches + user-service summary-cache this
    workload warms). getTweet owns its fixture end-to-end, so teardown wholesale-truncates:
      - tweet_service_db: tweets + hashtags + mentions + tweet_hashtag (CASCADE clears the
        FK children with the parent);
      - interaction_service_db: replies + likes + retweets (follows is cacheread-following's,
        left untouched);
      - user_service_db: the gettweet user pool — wholesale-truncate users, matching
        cacheread-following's clean (workloads run serially; the next re-seeds its world).
    Each TRUNCATE is guarded with to_regclass because a clean-only invocation may run
    before any app booted to migrate that DB's schema, so the table may not yet exist.
    """
    psql(
        TWEET_DB,
        "DO $$ BEGIN IF to_regclass('public.tweets') IS NOT NULL "
        "THEN EXECUTE 'TRUNCATE TABLE tweets, hashtags, mentions, tweet_hashtag CASCADE'; END IF; END $$;\n",
    )
    psql(
        INTERACTION_DB,
        "DO $$ BEGIN IF to_regclass('public.replies') IS NOT NULL "
        "THEN EXECUTE 'TRUNCATE TABLE replies, likes, retweets'; END IF; END $$;\n",
    )
    psql(
        USER_DB,
        "DO $$ BEGIN IF to_regclass('public.users') IS NOT NULL "
        "THEN EXECUTE 'TRUNCATE TABLE users'; END IF; END $$;\n",
    )
    flush_redis()

    print(
        "getTweet teardown complete: truncated tweets+hashtags+mentions+tweet_hashtag, "
        "replies+likes+retweets, users; flushed redis."
    )


def prepare(args: argparse.Namespace) -> None:
    keys_out = (
        repo_path(args.keys_out)
        if args.keys_out
        else (_HERE / "payload" / f"n{args.tweet_count}_r{args.replies_per_tweet}" / "keys.txt")
    )

    if args.mode == "clean":
        clean()
        return

    if args.likes_per_tweet > USER_POOL:
        print(
            f"ERROR: --likes-per-tweet={args.likes_per_tweet} exceeds the user pool ({USER_POOL}); "
            "the likes unique constraint (one actor per tweet) needs that many distinct actors.",
            file=sys.stderr,
        )
        sys.exit(1)

    users_out = _users_out(keys_out)
    tweet_ids = deterministic_tweet_ids(args.tweet_count)
    user_ids = deterministic_user_ids(USER_POOL)
    write_keys(keys_out, tweet_ids)
    write_keys(users_out, user_ids)

    seed_users(users_out, USER_POOL)
    seed_tweets(keys_out, users_out, args.tweet_count, USER_POOL)
    seed_interactions(
        keys_out,
        users_out,
        args.tweet_count,
        USER_POOL,
        args.replies_per_tweet,
        args.likes_per_tweet,
        args.retweets_per_tweet,
    )

    print("Prepared getTweet payload:")
    print(f"  tweet_count={args.tweet_count}")
    print(f"  user_pool={USER_POOL}")
    print(f"  replies_per_tweet={args.replies_per_tweet}")
    print(f"  likes_per_tweet={args.likes_per_tweet}")
    print(f"  retweets_per_tweet={args.retweets_per_tweet}")
    print(f"  keys_file={keys_out}")
    print(f"  users_file={users_out}")
    print(f"  expected_reply_rows={args.tweet_count * args.replies_per_tweet}")
    print(f"  expected_like_rows={args.tweet_count * args.likes_per_tweet}")
    print(f"  expected_retweet_rows={args.tweet_count * args.retweets_per_tweet}")
    print("  seeded user pool into user_service_db (gettweet_user_<seq>)")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare getTweet k6 payload.")
    parser.add_argument(
        "mode",
        nargs="?",
        default="seed",
        choices=["seed", "clean"],
        help="seed: user pool + tweets (+hashtag/mention relations) + replies/likes/retweets "
        "+ keys.txt (default). clean: truncate every seeded table across the three DBs, flush redis.",
    )
    parser.add_argument("--tweet-count", type=positive_int, required=True,
                        help="Number of tweets to seed (= number of keys k6 samples).")
    parser.add_argument("--replies-per-tweet", type=positive_int, default=2,
                        help="Replies seeded per tweet (each by a distinct pool user). Default 2.")
    parser.add_argument("--likes-per-tweet", type=positive_int, default=3,
                        help="Tweet-likes seeded per tweet (distinct actors; <= user pool). Default 3.")
    parser.add_argument("--retweets-per-tweet", type=positive_int, default=2,
                        help="Retweets seeded per tweet. Default 2.")
    parser.add_argument("--keys-out",
                        help="Output path for the generated tweet keys file "
                             "(default: workload dir / payload / n<TWEET_COUNT>_r<REPLIES_PER_TWEET>/keys.txt).")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    prepare(args)


if __name__ == "__main__":
    main()
