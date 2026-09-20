#!/usr/bin/env python3
"""Verify that a named frontend Jest suite executed and concluded successfully."""

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


def suite_elements(root: ET.Element) -> list[ET.Element]:
    if local_name(root.tag) == "testsuite":
        return [root]
    return [element for element in root.iter() if local_name(element.tag) == "testsuite"]


def children_named(element: ET.Element, name: str) -> list[ET.Element]:
    return [child for child in element.iter() if local_name(child.tag) == name]


def declared_count(element: ET.Element, name: str) -> int:
    raw = element.get(name)
    if raw is None:
        raise ValueError(f"suite has no {name!r} conclusion attribute")
    value = int(raw)
    if value < 0:
        raise ValueError(f"suite has negative {name}={value}")
    return value


def main() -> int:
    args = parse_args()
    try:
        root = ET.parse(args.input).getroot()
        matching = [
            suite for suite in suite_elements(root) if suite.get("name") == args.expected_suite
        ]
        if len(matching) != 1:
            raise ValueError(
                f"expected exactly one suite named {args.expected_suite!r}, found {len(matching)}"
            )
        suite = matching[0]
        testcases = children_named(suite, "testcase")
        failures = children_named(suite, "failure")
        errors = children_named(suite, "error")
        skipped = children_named(suite, "skipped")
        if not testcases:
            raise ValueError("suite contains no testcase elements")
        declared_tests = declared_count(suite, "tests")
        declared_failures = declared_count(suite, "failures")
        declared_errors = declared_count(suite, "errors")
        declared_skipped = declared_count(suite, "skipped")
        if declared_tests != len(testcases):
            raise ValueError(
                f"declared tests={declared_tests} but testcase elements={len(testcases)}"
            )
        if failures or errors or skipped:
            raise ValueError(
                f"failures={len(failures)}; errors={len(errors)}; skipped={len(skipped)}"
            )
        if declared_failures or declared_errors or declared_skipped:
            raise ValueError(
                "declared conclusion is not successful: "
                f"failures={declared_failures}; errors={declared_errors}; "
                f"skipped={declared_skipped}"
            )
    except (OSError, ValueError, ET.ParseError) as error:
        print(f"frontend_junit_report_verification=FAIL: {error}", file=sys.stderr)
        return 1

    print("frontend_junit_report_verification=PASS")
    print(f"suite={args.expected_suite}")
    print(f"executed_specs={len(testcases)}")
    print("failures=0")
    print("errors=0")
    print("skipped=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
