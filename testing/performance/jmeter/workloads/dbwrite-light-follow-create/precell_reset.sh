#!/usr/bin/env bash
# Per-cell reset for dbwrite-light-follow-create. Sourced by run_bench.sh::run_one_cell()
# before each JMeter cell starts. Expects $REPO_ROOT to be set by the caller.
#
# Operation:
#   TRUNCATE TABLE follows — the workload writes into `follows` with a
#   UNIQUE (follower_id, followed_id) constraint. Without resetting between
#   cells, the second cell onward immediately hits unique-constraint
#   violations and reports failed_pct >= 22%. Each cell must measure
#   "create-follow throughput from a cold table."
#
#   The table's schema (the named uq_follows_follower_followed unique and the
#   two (follower_id|followed_id, status) indexes) is owned by Flyway and applied
#   by each service at boot (V1__baseline.sql) and is identical for both stacks —
#   a single named unique, so neither
#   side carries a duplicate unique or a stray index. Those indexes ARE the production write
#   path, so we keep them: follow-create measures honest insert cost (PK +
#   unique + 2 secondary indexes) symmetrically for both paradigms. TRUNCATE
#   preserves them; no DROP/scrub is needed.
#
# Idempotent and silently best-effort (warn on failure, don't abort).

# Host psql on the interaction_service_db published port (54323) — reaches Docker-published
# or native-local infra alike, no docker exec.
PGPASSWORD=postgres psql -h 127.0.0.1 -p 54323 -U postgres -d interaction_service_db \
  -c "TRUNCATE TABLE follows;" \
  >/dev/null 2>&1 || echo "  WARN: follows reset failed (continuing)"
