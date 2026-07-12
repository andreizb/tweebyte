#!/usr/bin/env bash
# ci-local.sh - local quality pipeline.
#
# The cloud half lives in .github/workflows/ci.yml. This is the local half: it
# runs the same build/test/lint gate (Java services + Angular frontend), then can
# optionally run the FE (functional-equivalence Cucumber) suite and a Sonar scan
# to a LOCAL SonarQube. cosign keyless signing and Docker Hub push are cloud-only
# (OIDC + Sigstore Fulcio/Rekor) and are intentionally absent here.
#
# Runtime/deployment stays owned by ./run.sh and by the FE/performance harnesses
# that already call ./run.sh with the right profile/topology.
#
# Stages
#   build + unit test + lint   ALWAYS (8 Java modules + frontend lint/test/build)
#   functional-equivalence     FE=1       (Maven suite owns its ./run.sh lifecycle)
#   Sonar -> local SonarQube   SONAR=1    (SONAR_HOST_URL + SONAR_TOKEN from env/Vault)
#
# Env toggles (1 = on, default off unless noted):
#   FE       run the functional-equivalence suite
#   SONAR    run a Sonar scan to the local SonarQube
#
# Env values:
#   STACK          async | reactive            (default: reactive — the reference stack)
#   SONAR_HOST_URL local SonarQube URL          (default: http://localhost:9000)
#   SONAR_TOKEN    Sonar auth token (from env/Vault; NEVER committed)
#
# Usage:
#   ./deployment/ci-local.sh                          # build+test+lint only
#   FE=1 STACK=async ./deployment/ci-local.sh          # build+test+lint + FE
#   SONAR=1 SONAR_TOKEN=*** ./deployment/ci-local.sh  # lint + local Sonar scan
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# --- toggles / defaults -----------------------------------------------------
FE="${FE:-0}"
SONAR="${SONAR:-0}"

STACK="${STACK:-reactive}"
SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"

# The 8 Java modules (no aggregator pom — each builds with `mvn -f <module>/pom.xml`).
MODULES=(
  backend/async/gateway-service
  backend/async/user-service
  backend/async/tweet-service
  backend/async/interaction-service
  backend/reactive/gateway-service
  backend/reactive/user-service
  backend/reactive/tweet-service
  backend/reactive/interaction-service
)

log()  { printf '\n=== %s ===\n' "$*"; }
fail() { printf 'ci-local: %s\n' "$*" >&2; exit 1; }

case "$STACK" in
  async|reactive) ;;
  *) fail "STACK must be 'async' or 'reactive' (got '${STACK}')" ;;
esac

# ---------------------------------------------------------------------------
# Stage 1: build + unit test + lint gate (ALWAYS).
# Mirrors the cloud build-test-lint and frontend jobs: Java verify (unit tests +
# jacoco) plus explicit *:check goals for spring-javaformat/spotless, checkstyle,
# PMD, SpotBugs; frontend npm ci + ESLint + vitest coverage + production build.
# ---------------------------------------------------------------------------
build_test_lint() {
  log "build + unit test + lint over ${#MODULES[@]} Java modules + frontend"
  for module in "${MODULES[@]}"; do
    log "module: ${module}"
    ( cd "${REPO_ROOT}/${module}" && ./mvnw -B -ntp \
      verify \
      spotless:check \
      checkstyle:check \
      pmd:check \
      spotbugs:check )
  done

  # The functional-equivalence harness carries the same bound gates; lint it here
  # (ITs skipped — the full Cucumber run is the separate FE=1 stage below).
  log "module: testing/functional-equivalence (lint only)"
  ( cd "${REPO_ROOT}/testing/functional-equivalence" && ./mvnw -B -ntp \
    verify \
    -DskipITs \
    spotless:check \
    checkstyle:check \
    pmd:check \
    spotbugs:check )

  log "frontend: lint + unit test + production build"
  ( cd "${REPO_ROOT}/frontend" && \
    npm ci && \
    npm run lint && \
    npm test && \
    npm run build )
}

# ---------------------------------------------------------------------------
# Stage 2 (optional): functional-equivalence Cucumber suite.
# The FE harness is the lifecycle owner: it brings the selected stack up through
# ./run.sh runtime up <stack> functional-equivalence and tears it down after.
# ---------------------------------------------------------------------------
run_fe() {
  log "functional-equivalence suite (${STACK})"
  ( cd "${REPO_ROOT}/testing/functional-equivalence" && ./mvnw -B -ntp \
    verify "-P${STACK}" )
}

# ---------------------------------------------------------------------------
# Stage 3 (optional): Sonar scan to LOCAL SonarQube.
# SONAR_TOKEN comes from env/Vault and is NEVER committed. Each module carries
# its own sonar-maven-plugin; we point it at the local host and scan all 8.
# ---------------------------------------------------------------------------
run_sonar() {
  [[ -n "${SONAR_TOKEN:-}" ]] || fail "SONAR=1 requires SONAR_TOKEN (env/Vault); never commit it"
  log "Sonar scan -> ${SONAR_HOST_URL}"
  for module in "${MODULES[@]}"; do
    local project_key="tweebyte-${module//\//-}"
    log "sonar: ${module} (projectKey=${project_key})"
    ( cd "${REPO_ROOT}/${module}" && ./mvnw -B -ntp \
      verify \
      org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
      "-Dsonar.host.url=${SONAR_HOST_URL}" \
      "-Dsonar.token=${SONAR_TOKEN}" \
      "-Dsonar.projectKey=${project_key}" )
  done
}

main() {
  cd "$REPO_ROOT"

  build_test_lint

  [[ "$FE"     == "1" ]] && run_fe
  [[ "$SONAR"  == "1" ]] && run_sonar

  log "ci-local complete (stack=${STACK} fe=${FE} sonar=${SONAR})"
}

main "$@"
