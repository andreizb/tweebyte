-- interaction_service_db — Flyway V1 baseline (single source of truth for the schema).
--
-- Applied by each service at boot via Flyway, before Hibernate ddl-auto=validate on
-- the async stack; reactive (R2DBC) relies on it directly. This file is byte-identical
-- across the async and reactive copies so both stacks share one matching checksum.
--
-- Types mirror what the application persists (uuid, varchar(255), plain TIMESTAMP =
-- timestamp without time zone microsecond precision) so Hibernate validate passes.
-- There are NO foreign keys: follower_id/followed_id/user_id reference user_service_db
-- and tweet_id/likeable_id/original_tweet_id reference tweet_service_db — both live in
-- separate per-service databases, so referential integrity is enforced in the
-- application layer, not by cross-database FKs.

CREATE TABLE IF NOT EXISTS follows (
    id           UUID         PRIMARY KEY,
    follower_id  UUID         NOT NULL,
    followed_id  UUID         NOT NULL,
    status       VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uq_follows_follower_followed UNIQUE (follower_id, followed_id)
);

CREATE TABLE IF NOT EXISTS likes (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL,
    likeable_id    UUID         NOT NULL,
    likeable_type  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT uq_likes_user_likeable UNIQUE (user_id, likeable_id, likeable_type),
    CONSTRAINT ck_likes_likeable_type CHECK (likeable_type IN ('TWEET', 'REPLY'))
);

CREATE TABLE IF NOT EXISTS replies (
    id          UUID         PRIMARY KEY,
    tweet_id    UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    content     VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL,
    -- Zero or more attached media, logical references to user_service_db's
    -- media_assets.id. Cross-DB, so no FK (same rationale as user_id); nullable/empty
    -- when the reply carries no media.
    media_ids   UUID[]
);

CREATE TABLE IF NOT EXISTS retweets (
    id                 UUID         PRIMARY KEY,
    original_tweet_id  UUID         NOT NULL,
    retweeter_id       UUID         NOT NULL,
    content            VARCHAR(255),
    created_at         TIMESTAMP    NOT NULL,
    -- Zero or more attached media on the quote/retweet's own comment, logical
    -- references to user_service_db's media_assets.id. Cross-DB, so no FK;
    -- nullable/empty when the retweet carries no media.
    media_ids          UUID[]
);

CREATE INDEX IF NOT EXISTS follows_followed_status_idx ON follows (followed_id, status);
CREATE INDEX IF NOT EXISTS follows_follower_status_idx ON follows (follower_id, status);
CREATE INDEX IF NOT EXISTS likes_likeable_idx          ON likes (likeable_id, likeable_type);
CREATE INDEX IF NOT EXISTS replies_tweet_idx           ON replies (tweet_id, created_at);
CREATE INDEX IF NOT EXISTS retweets_original_tweet_idx ON retweets (original_tweet_id);
CREATE INDEX IF NOT EXISTS retweets_retweeter_idx      ON retweets (retweeter_id);
