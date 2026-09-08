#!/usr/bin/env python3
"""Fail unless the complete branch/worktree changed-file channel is allow-listed."""

from __future__ import annotations

import argparse
import fnmatch
import json
import subprocess
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--fixture", type=Path, required=True)
    return parser.parse_args()


def git_paths(root: Path, *arguments: str) -> set[str]:
    result = subprocess.run(
        ["git", *arguments], cwd=root, check=True, text=True, capture_output=True
    )
    return {line.replace("\\", "/") for line in result.stdout.splitlines() if line.strip()}


def changed_paths(root: Path, base_ref: str, ignored_untracked: set[str]) -> list[str]:
    paths = git_paths(root, "diff", "--name-only", "--diff-filter=ACMRTUXB", f"{base_ref}...HEAD")
    paths |= git_paths(root, "diff", "--name-only", "--diff-filter=ACMRTUXB")
    paths |= git_paths(root, "diff", "--cached", "--name-only", "--diff-filter=ACMRTUXB")
    untracked = git_paths(root, "ls-files", "--others", "--exclude-standard")
    paths |= untracked - ignored_untracked
    return sorted(paths)


def matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def main() -> int:
    args = parse_args()
    try:
        root = args.input.resolve()
        fixture = json.loads(args.fixture.read_text(encoding="utf-8"))
        if not (root / ".git").exists():
            raise ValueError(f"input is not a git worktree root: {root}")
        if fixture.get("subject") != "git_changed_files":
            raise ValueError("fixture must route assertions through subject=git_changed_files")
        paths = changed_paths(
            root,
            fixture["base_ref"],
            set(fixture.get("ignored_preexisting_untracked", [])),
        )
        if not paths:
            raise ValueError("changed-file subject is empty")
        disallowed = [path for path in paths if not matches(path, fixture["allowed_globs"])]
        forbidden = [path for path in paths if matches(path, fixture["forbidden_globs"])]
        if disallowed or forbidden:
            raise ValueError(f"disallowed={disallowed!r}; forbidden={forbidden!r}")
    except (OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"allowed_change_paths_verification=FAIL: {error}", file=sys.stderr)
        return 1

    print("allowed_change_paths_verification=PASS")
    print(f"changed_file_records={len(paths)}")
    print("production_behavior_file_records=0")
    for path in paths:
        print(f"changed_file={path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
