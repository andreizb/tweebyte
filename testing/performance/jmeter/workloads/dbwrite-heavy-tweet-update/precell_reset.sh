#!/usr/bin/env bash
# Per-cell reset for dbwrite-heavy-tweet-update.
#
# Restores tweet content + tweet_hashtag links to the old-form state so each
# JMeter cell starts identically (every PUT performs real old to new relation
# rebuild work, regardless of CSV row recycling).
#
# Required env: TWEET_COUNT, POOL_SIZE.
#
# Hard-fails on reset error: a half-reset leaves the next cell measuring the
# wrong workload state.

set -euo pipefail

: "${TWEET_COUNT:?TWEET_COUNT not set}"
: "${POOL_SIZE:?POOL_SIZE not set}"

# Heredoc piped directly to host psql stdin (the tweet_service_db published port — reaches
# Docker-published or native-local infra alike, no docker exec). Avoids the bash
# command-substitution parsing trap where apostrophes inside an inner `$(cat <<EOF)` body
# would be treated as shell-level quote boundaries.
PGPASSWORD=postgres psql -h 127.0.0.1 -p 54322 -U postgres -d tweet_service_db -v ON_ERROR_STOP=1 -q <<EOF
-- A. Restore old-form content for all benchmark tweets, including the trailing
--    @benchmark_user_1 mention the PUT flipped to @benchmark_user_2.
UPDATE tweets
SET content = 'benchmark old tweet ' || split_part(content, ' ', 4) ||
              ' #old_'    || ((cast(split_part(content, ' ', 4) AS int)) % ${POOL_SIZE}) ||
              ' #common_' || ((cast(split_part(content, ' ', 4) AS int)) % 64) ||
              ' @benchmark_user_1',
    version = 0
WHERE content LIKE 'benchmark %tweet %';

-- B. Clear ALL tweet_hashtag links (small table, fast).
TRUNCATE TABLE tweet_hashtag;

-- C. Rebuild old-form tweet_hashtag links from current tweets + preseeded
--    hashtag dictionary. Joins by text. Robust to deterministic UUIDs.
WITH parsed AS (
    SELECT t.id AS tweet_id,
           cast(split_part(t.content, ' ', 4) AS int) AS n
    FROM tweets t
    WHERE t.content LIKE 'benchmark old tweet %'
),
target_texts AS (
    SELECT tweet_id, txt FROM parsed,
    LATERAL (VALUES
        ('old_'    || (parsed.n % ${POOL_SIZE})),
        ('common_' || (parsed.n % 64))
    ) AS t(txt)
)
INSERT INTO tweet_hashtag (tweet_id, hashtag_id)
SELECT tt.tweet_id, h.id
FROM target_texts tt
JOIN hashtags h ON h.text = tt.txt;

-- D. Restore the old-state mention: every benchmark tweet mentions
--    @benchmark_user_1. The PUT flips it to @benchmark_user_2, so clearing and
--    re-seeding the user_1 rows returns each tweet to exactly one pending
--    old->new mention reconcile for the next cell. Tweet number is parsed from
--    content (token 4); user_1 + mention ids are derived inline from the same
--    md5 seeds prepare.py uses, so the restored rows are byte-identical.
TRUNCATE TABLE mentions;
INSERT INTO mentions (id, user_id, text, tweet_id)
SELECT
    (
        substr(mention_md5, 1, 8) || '-' || substr(mention_md5, 9, 4) || '-4' ||
        substr(mention_md5, 14, 3) || '-a' || substr(mention_md5, 18, 3) || '-' ||
        substr(mention_md5, 21, 12)
    )::uuid,
    (
        substr(user_md5, 1, 8) || '-' || substr(user_md5, 9, 4) || '-4' ||
        substr(user_md5, 14, 3) || '-a' || substr(user_md5, 18, 3) || '-' ||
        substr(user_md5, 21, 12)
    )::uuid,
    'benchmark_user_1',
    t.id
FROM tweets t
CROSS JOIN LATERAL (
    SELECT cast(split_part(t.content, ' ', 4) AS int) AS n
) AS parsed
CROSS JOIN LATERAL (
    SELECT md5('jmeter-benchmark-user:1') AS user_md5,
           md5('jmeter-benchmark-update-mention:' || parsed.n::text) AS mention_md5
) AS ids
WHERE t.content LIKE 'benchmark old tweet %';

-- E. Sanity assertions: tweet_hashtag links must equal tweets × 2 and mentions
--    must equal tweets × 1. Hard-fails if seeded rows are missing.
DO \$\$
DECLARE
    old_tweets BIGINT;
    actual_links BIGINT;
    actual_mentions BIGINT;
BEGIN
    SELECT count(*) INTO old_tweets FROM tweets WHERE content LIKE 'benchmark old tweet %';
    SELECT count(*) INTO actual_links FROM tweet_hashtag;
    IF actual_links <> 2 * old_tweets THEN
        RAISE EXCEPTION 'precell_reset: tweet_hashtag link count mismatch — expected %, got %', 2 * old_tweets, actual_links;
    END IF;
    SELECT count(*) INTO actual_mentions FROM mentions;
    IF actual_mentions <> old_tweets THEN
        RAISE EXCEPTION 'precell_reset: mention count mismatch — expected %, got %', old_tweets, actual_mentions;
    END IF;
END
\$\$;
EOF
