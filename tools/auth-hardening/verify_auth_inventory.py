#!/usr/bin/env python3
"""Verify PlantPal's documented frontend routes, backend endpoints, and auth defect pins."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


FRONTEND_HEADERS = ["ID", "App", "Method", "Path", "Declaration", "Guard", "Access", "Source"]
BACKEND_HEADERS = ["ID", "Method", "Path", "Controller", "Security rule", "Access", "Source"]
PIN_HEADERS = [
    "Pin",
    "Assignment",
    "Surface",
    "Current behavior",
    "Security consequence",
    "Characterization",
    "Source",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--pins", type=Path, required=True)
    parser.add_argument("--fixture-dir", type=Path, required=True)
    return parser.parse_args()


def clean_cell(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value.startswith("`") and value.endswith("`"):
        return value[1:-1]
    return value


def parse_table(document: Path, heading: str, headers: list[str]) -> list[dict[str, str]]:
    lines = document.read_text(encoding="utf-8").splitlines()
    try:
        heading_index = lines.index(heading)
    except ValueError as error:
        raise ValueError(f"{document}: missing heading {heading!r}") from error

    table_lines: list[str] = []
    for line in lines[heading_index + 1 :]:
        if line.startswith("## "):
            break
        if line.strip().startswith("|"):
            table_lines.append(line.strip())

    if len(table_lines) < 3:
        raise ValueError(f"{document}: {heading} has no data rows")
    actual_headers = [clean_cell(cell) for cell in table_lines[0].strip("|").split("|")]
    if actual_headers != headers:
        raise ValueError(f"{document}: {heading} headers {actual_headers!r} != {headers!r}")

    rows: list[dict[str, str]] = []
    for line in table_lines[2:]:
        cells = [clean_cell(cell) for cell in line.strip("|").split("|")]
        if len(cells) != len(headers):
            raise ValueError(f"{document}: malformed row {line!r}")
        rows.append(dict(zip(headers, cells, strict=True)))
    return rows


def require_unique(rows: list[dict[str, str]], fields: tuple[str, ...], label: str) -> None:
    seen: set[tuple[str, ...]] = set()
    for row in rows:
        key = tuple(row[field] for field in fields)
        if key in seen:
            raise ValueError(f"duplicate {label}: {key!r}")
        seen.add(key)


def repo_root(inventory: Path) -> Path:
    root = inventory.resolve().parents[2]
    if not (root / ".git").exists():
        raise ValueError(f"inventory does not resolve inside a git worktree: {inventory}")
    return root


def require_sources(root: Path, rows: list[dict[str, str]]) -> None:
    for row in rows:
        for raw_source in row["Source"].split(";"):
            source = root / raw_source.strip().strip("`")
            if not source.is_file():
                raise ValueError(f"{row.get('ID', row.get('Pin'))}: missing source {source}")


def join_mapping(base: str, suffix: str) -> str:
    if not base:
        return suffix or "/"
    if not suffix:
        return base
    return f"{base.rstrip('/')}/{suffix.lstrip('/')}"


def backend_access(method: str, path: str) -> tuple[str, str]:
    if method == "POST" and path in {"/api/v1/auth/register", "/api/v1/auth/login"}:
        return "POST permitAll", "public"
    if path in {"/api/v1/photos/{filename}", "/photos/{filename}"}:
        return "path permitAll", "public"
    return "anyRequest authenticated", "protected"


def discover_backend_endpoints(root: Path) -> list[dict[str, str]]:
    source_root = root / "backend/src/main/java"
    mapping_pattern = re.compile(
        r"@(Get|Post|Put|Patch|Delete)Mapping(?:\s*\((.*?)\))?", re.DOTALL
    )
    discovered: list[dict[str, str]] = []
    for source in sorted(source_root.rglob("*Controller.java")):
        text = source.read_text(encoding="utf-8")
        if "@RestController" not in text:
            continue
        controller_match = re.search(r"public\s+class\s+(\w+Controller)\b", text)
        if not controller_match:
            raise ValueError(f"cannot find controller class in {source}")
        controller = controller_match.group(1)
        class_mapping = re.search(r'@RequestMapping\(\s*"([^"]*)"\s*\)', text)
        base = class_mapping.group(1) if class_mapping else ""
        for match in mapping_pattern.finditer(text):
            method = match.group(1).upper()
            arguments = match.group(2) or ""
            suffixes = re.findall(r'"([^"]*)"', arguments) or [""]
            for suffix in suffixes:
                path = join_mapping(base, suffix)
                security_rule, access = backend_access(method, path)
                discovered.append(
                    {
                        "Method": method,
                        "Path": path,
                        "Controller": controller,
                        "Security rule": security_rule,
                        "Access": access,
                        "Source": source.relative_to(root).as_posix(),
                    }
                )
    return discovered


def compare_backend_inventory(root: Path, rows: list[dict[str, str]]) -> None:
    fields = ("Method", "Path", "Controller", "Security rule", "Access", "Source")
    documented = {tuple(row[field] for field in fields) for row in rows}
    discovered = {tuple(row[field] for field in fields) for row in discover_backend_endpoints(root)}
    missing = sorted(discovered - documented)
    extra = sorted(documented - discovered)
    if missing or extra:
        raise ValueError(f"backend inventory drift; missing={missing!r}; extra={extra!r}")


def compare_frontend_fixture(rows: list[dict[str, str]], fixture: dict[str, object]) -> None:
    fields = ("ID", "App", "Method", "Path", "Declaration", "Guard", "Access")
    actual = [{field: row[field] for field in fields} for row in rows]
    expected = fixture["frontend_records"]
    if actual != expected:
        raise ValueError("frontend route rows differ from auth-inventory.json")


def verify_source_samples(
    root: Path,
    frontend_rows: list[dict[str, str]],
    backend_rows: list[dict[str, str]],
    fixture: dict[str, object],
) -> tuple[int, int]:
    by_id = {row["ID"]: row for row in frontend_rows + backend_rows}
    counts = {"route": 0, "controller": 0}
    for sample in fixture["declaration_samples"]:
        row = by_id.get(sample["record_id"])
        if row is None:
            raise ValueError(f"sample record is absent: {sample['record_id']}")
        for field, value in sample["expected"].items():
            if row.get(field) != value:
                raise ValueError(
                    f"{sample['record_id']} field {field!r}: {row.get(field)!r} != {value!r}"
                )
        for source_check in sample["source_checks"]:
            source = root / source_check["path"]
            text = source.read_text(encoding="utf-8")
            for literal in source_check["contains"]:
                if literal not in text:
                    raise ValueError(f"{sample['record_id']}: {literal!r} absent from {source}")
        counts[sample["kind"]] += 1
    return counts["route"], counts["controller"]


def verify_pins(root: Path, rows: list[dict[str, str]], fixture_dir: Path) -> None:
    schema = json.loads((fixture_dir / "defect-pin-schema.json").read_text(encoding="utf-8"))
    expected = {(pin["pin"], pin["assignment"]) for pin in schema["pins"]}
    actual = {(row["Pin"], row["Assignment"]) for row in rows}
    if actual != expected:
        raise ValueError(f"defect pins differ from fixture; expected={expected!r}; actual={actual!r}")
    allowed_assignments = set(schema["allowed_assignments"])
    for row in rows:
        if not re.fullmatch(schema["pin_pattern"], row["Pin"]):
            raise ValueError(f"invalid pin id: {row['Pin']}")
        if row["Assignment"] not in allowed_assignments:
            raise ValueError(f"invalid pin assignment: {row['Assignment']}")
        if any(not row[field] for field in PIN_HEADERS):
            raise ValueError(f"pin has an empty required field: {row['Pin']}")
    require_sources(root, rows)
    for check in schema["source_checks"]:
        source = root / check["path"]
        text = source.read_text(encoding="utf-8")
        if any(literal not in text for literal in check["contains"]):
            raise ValueError(f"defect pin source evidence drifted: {check['pin']}")


def main() -> int:
    args = parse_args()
    try:
        root = repo_root(args.inventory)
        fixture = json.loads(
            (args.fixture_dir / "auth-inventory.json").read_text(encoding="utf-8")
        )
        frontend_rows = parse_table(
            args.inventory, "## Frontend route records", FRONTEND_HEADERS
        )
        backend_rows = parse_table(
            args.inventory, "## Backend endpoint records", BACKEND_HEADERS
        )
        pin_rows = parse_table(args.pins, "## Defect pin records", PIN_HEADERS)
        require_unique(frontend_rows, ("ID",), "frontend id")
        require_unique(frontend_rows, ("App", "Method", "Path"), "frontend route")
        require_unique(backend_rows, ("ID",), "backend id")
        require_unique(backend_rows, ("Method", "Path"), "backend endpoint")
        require_unique(pin_rows, ("Pin",), "defect pin")
        require_sources(root, frontend_rows)
        require_sources(root, backend_rows)
        compare_frontend_fixture(frontend_rows, fixture)
        compare_backend_inventory(root, backend_rows)
        verify_pins(root, pin_rows, args.fixture_dir)
        sampled_routes, sampled_controllers = verify_source_samples(
            root, frontend_rows, backend_rows, fixture
        )
        if min(len(frontend_rows), len(backend_rows), len(pin_rows)) <= 0:
            raise ValueError("all record categories must be positive")
        if sampled_routes < 3 or sampled_controllers < 3:
            raise ValueError("at least three route and controller samples are required")
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"auth_inventory_verification=FAIL: {error}", file=sys.stderr)
        return 1

    print("auth_inventory_verification=PASS")
    print(f"frontend_route_records={len(frontend_rows)}")
    print(f"backend_endpoint_records={len(backend_rows)}")
    print(f"defect_pin_records={len(pin_rows)}")
    print(f"sampled_route_declarations={sampled_routes}")
    print(f"sampled_controller_declarations={sampled_controllers}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
