#!/usr/bin/env python3
"""Verify that a named Maven suite executed and concluded successfully."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--expected-suite", required=True)
    return parser.parse_args()


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def suites(root: ET.Element) -> list[ET.Element]:
    if local_name(root.tag) == "testsuite":
        return [root]
    return [element for element in root.iter() if local_name(element.tag) == "testsuite"]


def main() -> int:
    args = parse_args()
    try:
        root = ET.parse(args.input).getroot()
        matching = [suite for suite in suites(root) if suite.get("name") == args.expected_suite]
        if len(matching) != 1:
            raise ValueError(
                f"expected exactly one suite named {args.expected_suite!r}, found {len(matching)}"
            )
        suite = matching[0]
        testcases = [item for item in suite.iter() if local_name(item.tag) == "testcase"]
        failures = [item for item in suite.iter() if local_name(item.tag) == "failure"]
        errors = [item for item in suite.iter() if local_name(item.tag) == "error"]
        if not testcases:
            raise ValueError("suite contains no testcase elements")
        if failures or errors:
            raise ValueError(f"failures={len(failures)}; errors={len(errors)}")
        declared_tests = int(suite.get("tests", "0"))
        declared_failures = int(suite.get("failures", "0"))
        declared_errors = int(suite.get("errors", "0"))
        if declared_tests != len(testcases):
            raise ValueError(
                f"declared tests={declared_tests} but testcase elements={len(testcases)}"
            )
        if declared_failures != 0 or declared_errors != 0:
            raise ValueError(
                f"declared failures={declared_failures}; declared errors={declared_errors}"
            )
    except (OSError, ValueError, ET.ParseError) as error:
        print(f"junit_report_verification=FAIL: {error}", file=sys.stderr)
        return 1

    print("junit_report_verification=PASS")
    print(f"suite={args.expected_suite}")
    print(f"executed_tests={len(testcases)}")
    print("failures=0")
    print("errors=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
