-- user_service_db — Flyway V1 baseline (single source of truth for the schema).
--
-- Applied by each service at boot via Flyway, before Hibernate ddl-auto=validate
-- on the async stack; reactive (R2DBC, no schema generation) relies on it directly.
-- This file is byte-identical across the async and reactive copies so both stacks
-- migrate the shared physical DB with one matching checksum.
--
-- Column types mirror what the application persists exactly so Hibernate validate
-- passes: uuid ids, varchar(255) strings, plain TIMESTAMP (timestamp without time
-- zone, microsecond precision), date, boolean. NOT NULL / UNIQUE / index choices
-- encode the real invariants (validate ignores them, but they keep a fresh volume
-- faithful to production and let the H2-backed async test schema enforce the same
-- rules).

CREATE TABLE IF NOT EXISTS users (
    id                 UUID         PRIMARY KEY,
    user_name          VARCHAR(255) NOT NULL,
    email              VARCHAR(255) NOT NULL,
    biography          VARCHAR(255) NOT NULL,
    password           VARCHAR(255) NOT NULL,
    is_private         BOOLEAN      NOT NULL,
    birth_date         DATE         NOT NULL,
    created_at         TIMESTAMP    NOT NULL,
    -- Avatar: points at the media_assets row backing the user's picture. Fresh
    -- registrations default to the all-zeros default-avatar sentinel (seeded at the
    -- end of this file) and "removing" a picture swaps back to it, so this is not a
    -- user-facing NULL — it only becomes NULL via the ON DELETE SET NULL action below
    -- if its asset is ever deleted. get-profile returns just this id (no join, no
    -- asset bytes), so the client fetches the image separately through file-download
    -- GET /media/{id} (§3.3). The FK is added below, once media_assets exists.
    profile_picture_id UUID,
    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT uq_users_user_name UNIQUE (user_name)
);

-- media_assets — content-addressed store behind the two media workloads
-- (image-upload §3.2 and file-download §3.3), both hosted in user-service.
-- Two kinds of row share the table:
--   originals — uploaded as-is via POST /media; source_media_id IS NULL.
--   previews  — derived via POST /media/{id}/preview, which runs the one-way
--               blur+sobel+resize+JPEG pipeline over an original's bytes and
--               stores the degraded result with source_media_id = that original.
--               A preview is a public stand-in while the original stays gated:
--               /preview requires a password, so access_hash holds its bcrypt
--               digest and POST /media/{id}/reveal streams the original bytes only
--               when a supplied password matches. access_hash IS NULL only for
--               originals and pure uploads.
-- id = UUID.nameUUIDFromBytes(bytes) and checksum = CRC32 of the stored bytes
-- (unsigned 32-bit, fits BIGINT, UNIQUE), so identical bytes dedup to one row.
-- A seeder persists each benchmark asset once and warms it into an in-process
-- cache, so the hot paths read from memory; the first content-addressed write of
-- a run inserts a row and identical follow-ups dedup to a cache hit. This table is
-- the cache-miss source of truth.
CREATE TABLE IF NOT EXISTS media_assets (
    id              UUID         PRIMARY KEY,
    content_type    VARCHAR(255) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    checksum        BIGINT       NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    data            BYTEA        NOT NULL,
    -- Self-reference: a preview points at the original it was derived from; NULL
    -- for originals and pure uploads. ON DELETE SET NULL so deleting an original
    -- orphans its previews (degraded, no recoverable source) rather than blocking
    -- the delete. Inline self-FK is legal because the target table is this one.
    source_media_id UUID,
    -- bcrypt digest (60 chars) of the preview's reveal password. NULL only for
    -- originals and pure uploads; every preview is gated.
    access_hash     VARCHAR(60),
    CONSTRAINT uq_media_assets_checksum UNIQUE (checksum),
    CONSTRAINT fk_media_assets_source
        FOREIGN KEY (source_media_id) REFERENCES media_assets (id) ON DELETE SET NULL
);

-- users.profile_picture_id → media_assets(id). Declared here (not inline above)
-- because the referenced table is created after users. ON DELETE SET NULL: deleting
-- an asset clears any avatar referencing it instead of blocking the delete. Because
-- this FK exists, the media cleanup must DELETE FROM media_assets (which honors the
-- SET NULL action) rather than TRUNCATE — Postgres refuses to TRUNCATE a table that
-- is the target of a foreign key, regardless of whether any row actually references it.
ALTER TABLE users
    ADD CONSTRAINT fk_users_profile_picture
    FOREIGN KEY (profile_picture_id) REFERENCES media_assets (id) ON DELETE SET NULL;

-- Default-avatar sentinel. A fixed all-zeros id holding a generic gray-silhouette
-- avatar JPEG. Every fresh registration defaults profile_picture_id to this row, and
-- "remove my picture" is the client swapping profile_picture_id back to it — so the
-- column is never user-facing NULL and there are no nullable-avatar branches. The id
-- is the nil UUID on purpose: image-upload mints ids via UUID.nameUUIDFromBytes, which
-- can never produce all-zeros, so the sentinel can't collide with an uploaded asset.
-- checksum is the real CRC32 of the bytes below (consistent with the content-addressed
-- store, unique). data is the JPEG inlined as base64 and decoded to bytea at init.
-- StaleMediaCleanupService hard-excludes this id, so the GC can never delete it
-- regardless of references or age. ON CONFLICT keeps the init idempotent.
INSERT INTO media_assets (id, content_type, size_bytes, checksum, created_at, data)
VALUES (
    '00000000-0000-0000-0000-000000000000',
    'image/jpeg',
    2373,
    1779100626,
    TIMESTAMP '1970-01-01 00:00:00',
    decode('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', 'base64')
)
ON CONFLICT (id) DO NOTHING;
