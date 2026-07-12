#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"
INFRA_RUNNER="${REPO_ROOT}/deployment/docker-compose/compose.sh"
LOCAL_RUNNER="${REPO_ROOT}/deployment/local/local-app.sh"

usage() {
  cat <<'EOF'
Usage:
  ./run.sh runtime up <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
  ./run.sh runtime down <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
  ./run.sh runtime destroy <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
  ./run.sh runtime ps <infra|async|reactive> [extra docker compose args...]
  ./run.sh runtime logs <infra|async|reactive> [extra docker compose args...]

  ./run.sh local up   <async|reactive> benchmark <service...|all>
  ./run.sh local down <async|reactive> benchmark <service...|all>
  ./run.sh local ps   [async|reactive] [benchmark] [service...|all]
  ./run.sh local logs <async|reactive> benchmark <service> [tail args...]

Profiles:
  prod      — normal runtime (no instrumentation).
  benchmark — performance-test profile (toxiproxy, GC log, JVM heap caps;
              k6 hits services directly bypassing the gateway).
  functional-equivalence
            — functional-equivalence profile (JaCoCo agent layered into each
              service JVM for the Cucumber suite under testing/functional-equivalence/).
              Cleanly isolated from prod/benchmark — overlay only loaded when
              this profile is selected.

Compatibility aliases:
  ./run.sh up|down|destroy|ps|logs ... still work unchanged

Benchmark topology supports two app modes. `runtime` keeps app services in
Docker. `local` keeps infrastructure in Docker but runs app services as local
benchmark-profile JVMs on the same ports.

Examples:
  ./run.sh runtime up infra benchmark
  ./run.sh runtime up reactive benchmark
EOF
}

delegate_infra() {
  exec bash "$INFRA_RUNNER" "$@"
}

delegate_local() {
  exec bash "$LOCAL_RUNNER" "$@"
}

main() {
  if (($# < 1)); then
    usage
    exit 1
  fi

  case "$1" in
    runtime)
      shift
      if (($# < 1)); then
        usage
        exit 1
      fi
      delegate_infra "$@"
      ;;
    local)
      shift
      if (($# < 1)); then
        usage
        exit 1
      fi
      delegate_local "$@"
      ;;
    up|down|destroy|ps|logs)
      delegate_infra "$@"
      ;;
    --help|-h|help)
      usage
      ;;
    *)
      echo "Unsupported command: $1" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
