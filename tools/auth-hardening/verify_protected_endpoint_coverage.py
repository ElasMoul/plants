#!/usr/bin/env python3
"""Verify two-case denial evidence for every protected backend inventory row."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path


EXPECTED_CASES = {"missing_credentials", "malformed_bearer"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    return parser.parse_args()


def plain(value: str) -> str:
    value = value.strip()
    if value.startswith("`") and value.endswith("`"):
        return value[1:-1]
    return value


def protected_rows(inventory: Path) -> dict[str, tuple[str, str]]:
    rows: dict[str, tuple[str, str]] = {}
    in_backend_table = False
    for line in inventory.read_text(encoding="utf-8").splitlines():
        if line == "## Backend endpoint records":
            in_backend_table = True
            continue
        if in_backend_table and line.startswith("## "):
            break
        if not in_backend_table or not line.startswith("| BE-"):
            continue
        columns = [plain(column) for column in line.strip().strip("|").split("|")]
        if len(columns) != 7:
            raise ValueError(f"malformed backend inventory row: {line}")
        endpoint_id, method, path, _controller, _rule, access, _source = columns
        if access == "protected":
            if endpoint_id in rows:
                raise ValueError(f"duplicate protected inventory id: {endpoint_id}")
            rows[endpoint_id] = (method, path)
    if not rows:
        raise ValueError("inventory contains no protected backend rows")
    return rows


def verify_cases(
    rows: dict[str, tuple[str, str]], cases: list[object]
) -> tuple[int, int]:
    seen: Counter[tuple[str, str]] = Counter()
    for index, raw_case in enumerate(cases):
        if not isinstance(raw_case, dict):
            raise ValueError(f"case {index} is not an object")
        required = {
            "endpoint_id",
            "method",
            "path",
            "credential_case",
            "expected_status",
            "actual_status",
            "passed",
        }
        if set(raw_case) != required:
            raise ValueError(f"case {index} fields do not match the evidence schema")
        endpoint_id = raw_case["endpoint_id"]
        credential_case = raw_case["credential_case"]
        if endpoint_id not in rows:
            raise ValueError(f"case {index} references unknown protected row {endpoint_id!r}")
        expected_method, expected_path = rows[endpoint_id]
        if (raw_case["method"], raw_case["path"]) != (expected_method, expected_path):
            raise ValueError(f"case {index} method/path differs from inventory row {endpoint_id}")
        if credential_case not in EXPECTED_CASES:
            raise ValueError(f"case {index} has unknown credential case {credential_case!r}")
        if raw_case["expected_status"] != 401 or raw_case["actual_status"] != 401:
            raise ValueError(f"case {index} did not observe the required 401 denial")
        if raw_case["passed"] is not True:
            raise ValueError(f"case {index} is not marked passed")
        seen[(endpoint_id, credential_case)] += 1

    expected_pairs = {(row, case) for row in rows for case in EXPECTED_CASES}
    actual_pairs = set(seen)
    missing = sorted(expected_pairs - actual_pairs)
    extra = sorted(actual_pairs - expected_pairs)
    duplicates = sorted(pair for pair, count in seen.items() if count != 1)
    if missing or extra or duplicates:
        raise ValueError(
            "incomplete two-case coverage: "
            f"missing_count={len(missing)} sample={missing[:4]!r}; "
            f"extra_count={len(extra)} sample={extra[:4]!r}; "
            f"duplicate_count={len(duplicates)} sample={duplicates[:4]!r}"
        )
    return len(rows), len(cases)


def main() -> int:
    args = parse_args()
    try:
        rows = protected_rows(args.inventory)
        evidence = json.loads(args.input.read_text(encoding="utf-8"))
        if evidence.get("schema_version") != 1:
            raise ValueError("unsupported or missing schema_version")
        if evidence.get("suite") != "ProtectedEndpointAuthTest":
            raise ValueError("evidence suite must be ProtectedEndpointAuthTest")
        cases = evidence.get("cases")
        if not isinstance(cases, list) or not cases:
            raise ValueError("evidence contains no executed cases")
        protected_count, executed_count = verify_cases(rows, cases)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"protected_endpoint_coverage_verification=FAIL: {error}", file=sys.stderr)
        return 1

    print("protected_endpoint_coverage_verification=PASS")
    print(f"protected_rows={protected_count}")
    print(f"executed_cases={executed_count}")
    print("credential_cases_per_row=2")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
