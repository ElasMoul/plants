#!/usr/bin/env python3
"""Verify GitHub Actions conclusions and all downloaded auth JUnit reports."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, TextIO


REQUIRED_JOBS = ("Backend CI", "Frontend CI", "Auth Characterization CI")
REQUIRED_SUITES = (
    ("protected_endpoint_auth_tests", "backend/target/surefire-reports/TEST-ProtectedEndpointAuthTest.xml", "ProtectedEndpointAuthTest"),
    ("auth_guard_specs", "frontend/test-results/auth-guard.xml", "auth.guard.spec.ts"),
    ("jwt_interceptor_specs", "frontend/test-results/jwt-interceptor.xml", "jwt.interceptor.spec.ts"),
    ("session_handoff_specs", "frontend/test-results/session-handoff.xml", "session-handoff.spec.ts"),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-json", required=True)
    parser.add_argument("--reports-dir", type=Path, required=True)
    return parser.parse_args()


def read_json(source: str, stdin: TextIO) -> dict[str, Any]:
    raw = stdin.read() if source == "-" else Path(source).read_text(encoding="utf-8")
    value = json.loads(raw)
    if not isinstance(value, dict) or not value:
        raise ValueError("run JSON must be a non-empty object")
    return value


def verify_conclusions(run: dict[str, Any]) -> None:
    if run.get("conclusion") != "success":
        raise ValueError(f"workflow conclusion is not successful: {run.get('conclusion')!r}")
    jobs = run.get("jobs")
    if not isinstance(jobs, list) or not jobs:
        raise ValueError("run JSON contains no jobs")
    by_name = {job.get("name"): job for job in jobs if isinstance(job, dict)}
    for name in REQUIRED_JOBS:
        matches = [job for job in jobs if isinstance(job, dict) and job.get("name") == name]
        if len(matches) != 1:
            raise ValueError(f"expected exactly one required job named {name!r}")
        if by_name[name].get("conclusion") != "success":
            raise ValueError(f"required job {name!r} is not successful")


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def suite_elements(root: ET.Element) -> list[ET.Element]:
    if local_name(root.tag) == "testsuite":
        return [root]
    return [element for element in root.iter() if local_name(element.tag) == "testsuite"]


def declared_count(suite: ET.Element, attribute: str) -> int:
    raw = suite.get(attribute)
    if raw is None:
        raise ValueError(f"suite has no {attribute!r} conclusion attribute")
    value = int(raw)
    if value < 0:
        raise ValueError(f"suite has negative {attribute}={value}")
    return value


def verify_report(path: Path, expected_suite: str) -> int:
    root = ET.parse(path).getroot()
    matching = [suite for suite in suite_elements(root) if suite.get("name") == expected_suite]
    if len(matching) != 1:
        raise ValueError(f"{path}: expected exactly one suite named {expected_suite!r}")
    suite = matching[0]
    testcases = [element for element in suite.iter() if local_name(element.tag) == "testcase"]
    failures = [element for element in suite.iter() if local_name(element.tag) == "failure"]
    errors = [element for element in suite.iter() if local_name(element.tag) == "error"]
    skipped = [element for element in suite.iter() if local_name(element.tag) == "skipped"]
    if not testcases:
        raise ValueError(f"{path}: suite contains no testcase elements")
    declared = {name: declared_count(suite, name) for name in ("tests", "failures", "errors", "skipped")}
    if declared["tests"] != len(testcases):
        raise ValueError(f"{path}: declared tests do not match testcase elements")
    if failures or errors or skipped or any(declared[name] for name in ("failures", "errors", "skipped")):
        raise ValueError(f"{path}: suite conclusion is not successful")
    return len(testcases)


def main() -> int:
    args = parse_args()
    try:
        run = read_json(args.run_json, sys.stdin)
        verify_conclusions(run)
        executed = {
            label: verify_report(args.reports_dir / relative_path, suite)
            for label, relative_path, suite in REQUIRED_SUITES
        }
    except (OSError, ValueError, TypeError, json.JSONDecodeError, ET.ParseError) as error:
        print(f"ci_run_verification=FAIL: {error}", file=sys.stderr)
        return 1

    print("ci_run_verification=PASS")
    print("workflow_conclusion=success")
    print(f"required_job_conclusions={len(REQUIRED_JOBS)}")
    for name in REQUIRED_JOBS:
        print(f"required_job={name}; conclusion=success")
    for label, count in executed.items():
        print(f"executed_{label}={count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
