#!/usr/bin/env python3
"""Shared payload-prep helpers for k6 workloads.

Each workload directory under ../workloads/<name>/ has its own prepare.py
that imports from this module. Mirrors the JMeter side at
testing/performance/jmeter/lib/payload_lib.py.
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


# lib/ is at testing/performance/k6/lib/. Repo root is four parents up
# (lib → k6 → performance → testing → repo).
LIB_DIR = Path(__file__).resolve().parent
K6_DIR = LIB_DIR.parent
REPO_ROOT = K6_DIR.parents[2]
COMPOSE_FILE = REPO_ROOT / "deployment" / "docker-compose" / "infrastructure.yml"


def repo_path(value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else (REPO_ROOT / path)


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return parsed


def run(cmd: list[str], *, input_text: str | None = None, quiet: bool = False) -> subprocess.CompletedProcess[str]:
    kwargs = {
        "check": True,
        "text": True,
        "capture_output": quiet,
    }
    if input_text is not None:
        kwargs["input"] = input_text
    return subprocess.run(cmd, **kwargs)


def compose_cmd(*args: str) -> list[str]:
    project_name = os.environ.get("COMPOSE_PROJECT_NAME", "tweebyte")
    return [
        "docker", "compose",
        "--project-name", project_name,
        "--project-directory", str(REPO_ROOT),
        "-f", str(COMPOSE_FILE),
        *args,
    ]


def require_running(service: str) -> None:
    result = run(compose_cmd("ps", "-q", service), quiet=True)
    if not result.stdout.strip():
        print(f"ERROR: {service} is not running. Start infra first with ./run.sh runtime up infra benchmark", file=sys.stderr)
        sys.exit(1)


def shutil_which(binary: str) -> str | None:
    for directory in os.getenv("PATH", "").split(os.pathsep):
        candidate = Path(directory) / binary
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    return None
