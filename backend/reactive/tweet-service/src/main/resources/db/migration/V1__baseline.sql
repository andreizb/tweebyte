-- tweet_service_db — Flyway V1 baseline (single source of truth for the schema).
--
-- Applied by each service at boot via Flyway, before Hibernate ddl-auto=validate on
-- the async stack; reactive (R2DBC) relies on it directly. This file is byte-identical
-- across the async and reactive copies so both stacks share one matching checksum.
--
-- Types mirror what the application persists (uuid, varchar(255), plain TIMESTAMP =
-- timestamp without time zone microsecond precision, bigint) so Hibernate validate
-- passes. Foreign keys are intra-DB only (mentions/tweet_hashtag → tweets/hashtags);
-- user_id references the separate user_service_db and so cannot be a DB-level FK.
-- tweets.version backs JPA optimistic locking and is nullable for the same reason
-- Hibernate leaves it nullable (rows predating versioning).

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS tweets (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    version     BIGINT,
    content     VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    -- Zero or more attached media, each a logical reference to user_service_db's
    -- media_assets.id. Cross-DB, so no FK (same rationale as user_id above); the
    -- frontend resolves each id via file-download. Nullable/empty when the tweet
    -- carries no media.
    media_ids   UUID[]
);

CREATE TABLE IF NOT EXISTS hashtags (
    id    UUID         PRIMARY KEY,
    text  VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS mentions (
    id        UUID         PRIMARY KEY,
    user_id   UUID         NOT NULL,
    text      VARCHAR(255) NOT NULL,
    tweet_id  UUID         NOT NULL,
    CONSTRAINT fk_mentions_tweet FOREIGN KEY (tweet_id) REFERENCES tweets (id)
);

CREATE TABLE IF NOT EXISTS tweet_hashtag (
    tweet_id    UUID NOT NULL,
    hashtag_id  UUID NOT NULL,
    PRIMARY KEY (tweet_id, hashtag_id),
    CONSTRAINT fk_tweet_hashtag_tweet   FOREIGN KEY (tweet_id)   REFERENCES tweets (id),
    CONSTRAINT fk_tweet_hashtag_hashtag FOREIGN KEY (hashtag_id) REFERENCES hashtags (id)
);

CREATE INDEX IF NOT EXISTS idx_user_id          ON tweets (user_id);
-- Composite index for the per-user feed page: GET /tweets/user/{id} runs
-- `WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?` (Spring Data
-- PAGE_SORT, identical async ↔ reactive). It serves the predicate AND the sort in index
-- order, removing the per-page server-side sort. idx_user_id above is kept (a left-prefix
-- of this index) to avoid any unrelated planner regression.
CREATE INDEX IF NOT EXISTS idx_tweets_user_feed ON tweets (user_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_tweets_content_trgm ON tweets USING gin (content gin_trgm_ops);
CREATE INDEX IF NOT EXISTS hashtags_text_idx    ON hashtags (text);
CREATE INDEX IF NOT EXISTS mentions_tweet_idx   ON mentions (tweet_id);
CREATE INDEX IF NOT EXISTS tweet_hashtag_hashtag_idx ON tweet_hashtag (hashtag_id);
