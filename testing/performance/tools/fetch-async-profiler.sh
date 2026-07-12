#!/usr/bin/env bash
# Fetch async-profiler for the diagnostics overlay (deployment/docker-compose/diagnostics.yml).
#
# The overlay bind-mounts ./testing/performance/tools/async-profiler into each measured
# container at /opt/async-profiler:ro so a debugging session can run
# `/opt/async-profiler/bin/asprof` against the running JVM. The binary must match the
# CONTAINER OS/arch (Linux, same arch as the Docker VM = the host arch), NOT macOS — so
# this always downloads a linux-* build. Idempotent: re-running is a no-op once present.
#
# Usage: ./testing/performance/tools/fetch-async-profiler.sh [version]
set -euo pipefail

VERSION="${1:-3.0}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${HERE}/async-profiler"

case "$(uname -m)" in
  arm64|aarch64) ARCH="arm64" ;;
  x86_64|amd64)  ARCH="x64" ;;
  *) echo "ERROR: unsupported host arch $(uname -m) (need arm64 or x86_64)" >&2; exit 1 ;;
esac

if [[ -x "${DEST}/bin/asprof" ]]; then
  echo "async-profiler already present at ${DEST} ($(${DEST}/bin/asprof --version 2>/dev/null || echo unknown)) — nothing to do."
  exit 0
fi

ASSET="async-profiler-${VERSION}-linux-${ARCH}.tar.gz"
URL="https://github.com/async-profiler/async-profiler/releases/download/v${VERSION}/${ASSET}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading ${URL}"
curl -fsSL "$URL" -o "${TMP}/ap.tar.gz"
mkdir -p "$DEST"
# --strip-components=1 drops the async-profiler-<ver>-linux-<arch>/ top dir so the mount
# resolves /opt/async-profiler/bin/asprof and /opt/async-profiler/lib/libasyncprofiler.so.
tar -xzf "${TMP}/ap.tar.gz" -C "$DEST" --strip-components=1
chmod +x "${DEST}/bin/asprof" 2>/dev/null || true

echo "Installed async-profiler ${VERSION} (linux-${ARCH}) to ${DEST}"
echo "It is mounted read-only into the diagnostics containers at /opt/async-profiler."
