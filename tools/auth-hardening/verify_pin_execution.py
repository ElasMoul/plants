#!/usr/bin/env python3
"""Verify that successful JUnit cases execute the defect pins assigned to a suite."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


PIN_PATTERN = re.compile(r"PP-AUTH-[0-9]{3}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--pins", type=Path, required=True)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--assignment", required=True)
    return parser.parse_args()


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def markdown_pin_rows(path: Path) -> dict[str, str]:
    rows: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        cells = [cell.strip().strip("`") for cell in line.strip().strip("|").split("|")]
        if len(cells) < 2 or not PIN_PATTERN.fullmatch(cells[0]):
            continue
        if cells[0] in rows:
            raise ValueError(f"duplicate registered defect pin: {cells[0]}")
        rows[cells[0]] = cells[1]
    if not rows:
        raise ValueError("defect pin document contains no registered pins")
    return rows


def successful_testcase_identities(report: Path) -> list[str]:
    root = ET.parse(report).getroot()
    suites = [item for item in root.iter() if local_name(item.tag) == "testsuite"]
    if local_name(root.tag) == "testsuite":
        suites = [root]
    if not suites:
        raise ValueError("JUnit report contains no testsuite elements")
    failures = [item for item in root.iter() if local_name(item.tag) == "failure"]
    errors = [item for item in root.iter() if local_name(item.tag) == "error"]
    skipped = [item for item in root.iter() if local_name(item.tag) == "skipped"]
    if failures or errors or skipped:
        raise ValueError(
            "JUnit conclusion is not successful: "
            f"failures={len(failures)}; errors={len(errors)}; skipped={len(skipped)}"
        )
    for suite in suites:
        conclusion = {
            field: int(suite.get(field, "-1")) for field in ("tests", "failures", "errors", "skipped")
        }
        if conclusion["tests"] <= 0:
            raise ValueError(f"suite has no declared executed tests: {suite.get('name')!r}")
        if any(conclusion[field] != 0 for field in ("failures", "errors", "skipped")):
            raise ValueError(
                f"suite has unsuccessful declared conclusion: {suite.get('name')!r} "
                f"{conclusion!r}"
            )
    testcases = [item for item in root.iter() if local_name(item.tag) == "testcase"]
    if not testcases:
        raise ValueError("JUnit report contains no testcase elements")
    return [f"{case.get('classname', '')} {case.get('name', '')}" for case in testcases]


def main() -> int:
    args = parse_args()
    try:
        fixture = json.loads(args.fixture.read_text(encoding="utf-8"))
        allowed_assignments = fixture["allowed_assignments"]
        if args.assignment not in allowed_assignments:
            raise ValueError(f"assignment is not registered: {args.assignment!r}")
        documented = markdown_pin_rows(args.pins)
        fixture_pins = {item["pin"]: item["assignment"] for item in fixture["pins"]}
        if documented != fixture_pins:
            raise ValueError(
                f"documented pins differ from fixture: documented={documented!r}; "
                f"fixture={fixture_pins!r}"
            )
        assigned_pins = sorted(
            pin for pin, assignment in documented.items() if assignment == args.assignment
        )
        if not assigned_pins:
            raise ValueError(f"assignment has no registered defect pins: {args.assignment!r}")
        identities = successful_testcase_identities(args.report)
        executed = [
            pin
            for pin in assigned_pins
            if any(pin in identity and args.assignment in identity for identity in identities)
        ]
        missing = sorted(set(assigned_pins) - set(executed))
        if missing:
            raise ValueError(f"assigned pins have no successful tagged testcase: {missing!r}")
    except (OSError, ValueError, KeyError, TypeError, ET.ParseError, json.JSONDecodeError) as error:
        print(f"pin_execution_verification=FAIL: {error}", file=sys.stderr)
        return 1

    print("pin_execution_verification=PASS")
    print(f"assignment={args.assignment}")
    print(f"executed_pin_count={len(executed)}")
    for pin in executed:
        print(f"executed_pin={pin}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
