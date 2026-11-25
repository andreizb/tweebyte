#!/usr/bin/env python3
"""Transfer only Maven POMs between jobs in the same Actions run."""

import argparse
import hashlib
from pathlib import Path, PurePosixPath
import re
import stat
from zipfile import ZIP_DEFLATED, ZipFile

MAX_FILE = 1024 * 1024
MAX_ARCHIVE = 128 * 1024 * 1024
MAX_UNION = 256 * 1024 * 1024
MAX_ENTRIES = 10_000
EXPECTED = {"maven-poms-functional", *(f"maven-poms-{i}" for i in range(8))}
COMPONENT = re.compile(r"[A-Za-z0-9_.+-]{1,180}")


def validate_name(name):
    parts = name.split("/")
    if (
        not 4 <= len(parts) <= 40
        or len(name) > 1024
        or any(p in {".", ".."} or not COMPONENT.fullmatch(p) for p in parts)
        or not parts[-1].endswith(".pom")
        or not parts[-1].startswith(parts[-3] + "-")
    ):
        raise ValueError("Invalid Maven POM coordinate in transfer archive")


def export_poms(repository, archive):
    repository = repository.resolve(strict=True)
    total = count = 0
    with ZipFile(archive, "w", compression=ZIP_DEFLATED) as target:
        for path in sorted(repository.rglob("*.pom")):
            if path.is_symlink() or not path.is_file():
                continue
            relative = path.relative_to(repository)
            if path.resolve() != path:
                raise ValueError("Refusing to follow a symlink when exporting POMs")
            name = relative.as_posix()
            validate_name(name)
            size = path.stat().st_size
            total += size
            count += 1
            if not 0 < size <= MAX_FILE or total > MAX_ARCHIVE or count > MAX_ENTRIES:
                raise ValueError("Maven POM export exceeds transfer limits")
            target.write(path, name)
    if not count:
        raise ValueError("No Maven POM metadata found after build")
    print(f"Collected {count} Maven POMs")


def restore_poms(artifacts, repository):
    inputs = {
        d.name: d / "maven-poms.zip"
        for d in artifacts.iterdir()
        if d.name.startswith("maven-poms-") and d.is_dir() and not d.is_symlink()
    }
    if set(inputs) != EXPECTED:
        raise ValueError("Incomplete same-run Maven metadata artifacts")

    total = 0
    digests = {}
    root = repository.resolve()
    for path in sorted(inputs.values()):
        if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_ARCHIVE:
            raise ValueError("Missing or oversized Maven metadata archive")
        with ZipFile(path) as archive:
            entries = archive.infolist()
            if not 0 < len(entries) <= MAX_ENTRIES:
                raise ValueError("Empty or oversized Maven metadata archive")
            if sum(entry.file_size for entry in entries) > MAX_ARCHIVE:
                raise ValueError("Uncompressed Maven metadata archive exceeds transfer limits")
            for entry in entries:
                name = entry.filename
                validate_name(name)
                if entry.orig_filename != name:
                    raise ValueError("Invalid NUL in Maven metadata archive entry")
                mode = stat.S_IFMT(entry.external_attr >> 16)
                if mode not in (0, stat.S_IFREG) or not 0 < entry.file_size <= MAX_FILE:
                    raise ValueError("Invalid Maven metadata archive entry")
                data = archive.read(entry)
                digest = hashlib.sha256(data).digest()
                if name in digests:
                    if digests[name] != digest:
                        raise ValueError(f"Conflicting Maven POM artifacts for {name}")
                    continue
                total += len(data)
                if total > MAX_UNION or len(digests) >= MAX_ENTRIES:
                    raise ValueError("Combined Maven POM metadata exceeds transfer limits")
                dest = root.joinpath(*PurePosixPath(name).parts)
                if dest.resolve() != dest:
                    raise ValueError("Refusing to follow a symlink when restoring POMs")
                dest.parent.mkdir(parents=True, exist_ok=True)
                try:
                    with dest.open("xb") as output:
                        output.write(data)
                except FileExistsError:
                    if dest.stat().st_size != len(data) or dest.read_bytes() != data:
                        raise ValueError(f"Conflicting existing Maven POM for {name}") from None
                digests[name] = digest
    print(f"Restored {len(digests)} distinct Maven POMs; Trivy retains online fallback")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("export", "restore"))
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    args = parser.parse_args()
    operation = export_poms if args.operation == "export" else restore_poms
    operation(args.source, args.target)


if __name__ == "__main__":
    main()
