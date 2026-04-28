#!/usr/bin/env bash
# realism-backend.sh — host-side mlx_lm.server lifecycle for the §5.13 realism subset.
#
# Why this script exists. The realism subset of the AppSci-Q2 study (RESULTS.md
# §5.13) needs a *concurrent-batching* OpenAI-compatible LLM endpoint serving
# the same Qwen3.5-4B-MLX-4bit weights the mock was calibrated against (§5.2).
# LM Studio's MLX runtime hard-rejects --parallel > 1 for vision-architecture
# models (the empirical probe is in §5.13.1). The cleanest pivot is Apple's
# mlx-lm package — `mlx_lm.server` accepts --decode-concurrency / --prompt-
# concurrency and serves the *same on-disk MLX weights* with continuous
# batching, so calibration source and realism path differ only in the runtime
# (not in the model architecture or quantization).
#
# This script does NOT go in infrastructure/run.sh. That script orchestrates
# Docker stacks; mlx_lm.server is a host-side process because Docker Desktop
# on macOS cannot pass through Apple Silicon Metal to containers, and
# vision-arch MLX models need Metal.
#
# Usage:
#   ./infrastructure/realism-backend.sh up [--port 8081] [--parallel 8] [--model <path>]
#   ./infrastructure/realism-backend.sh down
#   ./infrastructure/realism-backend.sh status
#
# Note on heap caps for the SUT side. Once this script is up, set the
# Docker stack env before `infrastructure/run.sh up`:
#
#   export AI_BACKEND=live
#   export LIVE_LLM_BASE_URL=http://host.docker.internal:8081
#   export LIVE_LLM_MODEL=/Users/andrei/.lmstudio/models/mlx-community/Qwen3.5-4B-MLX-4bit
#   export LIVE_LLM_MAX_TOKENS=200
#   export TWEET_JAVA_TOOL_OPTIONS='-Xms1g -Xmx1500m -XX:+AlwaysPreTouch'
#   export INTERACTION_JAVA_TOOL_OPTIONS='-Xms1g -Xmx1500m -XX:+AlwaysPreTouch'
#   ./infrastructure/run.sh up async benchmark --build
#
# The heap-cap exports are essential — `infrastructure/run.sh`'s default
# benchmark profile is `-Xms3g -Xmx3g` which collides with the 4 GB compose
# `mem_limit` once thread stacks + Spring AI + Hibernate metaspace are
# accounted for. The realism cells run at rps=1/2, so 1.5 GB max heap is
# generous; the cap protects against OOM-kill mid-batch.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

VENV_DIR="${REALISM_BACKEND_VENV:-${REPO_ROOT}/infrastructure/.venv-realism}"
PID_FILE="${REALISM_BACKEND_PID_FILE:-/tmp/realism-backend.pid}"
LOG_FILE="${REALISM_BACKEND_LOG_FILE:-/tmp/realism-backend.log}"

DEFAULT_MODEL="/Users/andrei/.lmstudio/models/mlx-community/Qwen3.5-4B-MLX-4bit"
DEFAULT_PORT=8081
DEFAULT_PARALLEL=8
# enable_thinking=false disables Qwen3.5's reasoning-mode token emission.
# Without this the streaming response emits delta.reasoning chunks that
# Spring AI's OpenAI client filters out, leaving the SUT-visible response
# empty. See §5.13.2 for the empirical evidence and pivot decision.
DEFAULT_CHAT_TEMPLATE_ARGS='{"enable_thinking": false}'

usage() {
  cat <<EOF
Usage:
  ./infrastructure/realism-backend.sh up   [options]
  ./infrastructure/realism-backend.sh down
  ./infrastructure/realism-backend.sh status

Options for 'up':
  --model <path>      MLX model dir (default: $DEFAULT_MODEL)
  --port <int>        Listen port (default: $DEFAULT_PORT)
  --parallel <int>    --decode-concurrency / --prompt-concurrency
                      (default: $DEFAULT_PARALLEL)
  --chat-template-args <json>
                      Forwarded to mlx_lm.server. Default disables Qwen
                      reasoning mode: $DEFAULT_CHAT_TEMPLATE_ARGS

Environment overrides:
  REALISM_BACKEND_VENV       venv path (default: ./infrastructure/.venv-realism)
  REALISM_BACKEND_PID_FILE   pid file path
  REALISM_BACKEND_LOG_FILE   log file path
EOF
}

ensure_venv() {
  if [ ! -d "$VENV_DIR" ]; then
    echo "Creating venv at $VENV_DIR ..." >&2
    python3 -m venv "$VENV_DIR"
  fi
  if ! "$VENV_DIR/bin/python" -c 'import mlx_lm' 2>/dev/null; then
    echo "Installing mlx-lm into venv ..." >&2
    "$VENV_DIR/bin/pip" install --quiet --upgrade mlx-lm
  fi
}

cmd_up() {
  local model="$DEFAULT_MODEL"
  local port="$DEFAULT_PORT"
  local parallel="$DEFAULT_PARALLEL"
  local chat_template_args="$DEFAULT_CHAT_TEMPLATE_ARGS"

  while [ $# -gt 0 ]; do
    case "$1" in
      --model) model="$2"; shift 2 ;;
      --port) port="$2"; shift 2 ;;
      --parallel) parallel="$2"; shift 2 ;;
      --chat-template-args) chat_template_args="$2"; shift 2 ;;
      *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
    esac
  done

  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "realism-backend already running (pid $(cat "$PID_FILE"))" >&2
    exit 0
  fi

  ensure_venv

  echo "Starting mlx_lm.server: model=$model port=$port parallel=$parallel" >&2
  echo "  chat-template-args=$chat_template_args" >&2
  # Bind 0.0.0.0 so Docker containers can reach via host.docker.internal.
  "$VENV_DIR/bin/python" -m mlx_lm.server \
    --model "$model" \
    --host 0.0.0.0 \
    --port "$port" \
    --decode-concurrency "$parallel" \
    --prompt-concurrency "$parallel" \
    --chat-template-args "$chat_template_args" \
    --log-level INFO \
    > "$LOG_FILE" 2>&1 &
  local pid=$!
  echo "$pid" > "$PID_FILE"
  echo "Started pid=$pid; waiting for readiness ..." >&2
  for _ in $(seq 1 30); do
    if curl -sS --max-time 2 -X POST "http://127.0.0.1:$port/v1/chat/completions" \
         -H "Content-Type: application/json" \
         -d "{\"model\":\"$model\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":5,\"stream\":false}" \
         2>/dev/null | grep -q '"choices"'; then
      echo "realism-backend ready on port $port (pid $pid)" >&2
      exit 0
    fi
    sleep 2
  done
  echo "realism-backend did not become ready in 60s; check $LOG_FILE" >&2
  exit 1
}

cmd_down() {
  if [ ! -f "$PID_FILE" ]; then
    echo "no pid file at $PID_FILE; nothing to stop" >&2
    exit 0
  fi
  local pid
  pid="$(cat "$PID_FILE")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    echo "Stopped pid=$pid" >&2
  fi
  rm -f "$PID_FILE"
}

cmd_status() {
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "realism-backend running (pid $(cat "$PID_FILE"))"
    exit 0
  fi
  echo "realism-backend not running"
  exit 1
}

if [ $# -lt 1 ]; then
  usage
  exit 1
fi

action="$1"
shift
case "$action" in
  up) cmd_up "$@" ;;
  down) cmd_down ;;
  status) cmd_status ;;
  *) usage; exit 1 ;;
esac
