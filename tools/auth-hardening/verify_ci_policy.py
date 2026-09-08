#!/usr/bin/env python3
"""Structurally verify the blocking GitHub Actions auth-characterization policy."""

from __future__ import annotations

import argparse
import fnmatch
import json
import re
import sys
from pathlib import Path
from typing import Any

import yaml


class StringKeyLoader(yaml.BaseLoader):
    """Keep GitHub's `on` key as text instead of YAML 1.1 boolean true."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workflow-dir", type=Path, required=True)
    parser.add_argument("--fixture", type=Path, required=True)
    return parser.parse_args()


def mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict) or not value:
        raise ValueError(f"{label} must be a non-empty mapping")
    return value


def sequence(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list) or not value:
        raise ValueError(f"{label} must be a non-empty list")
    return value


def is_false_or_absent(value: Any) -> bool:
    return value is None or str(value).lower() == "false"


def steps_by_id(job: dict[str, Any]) -> dict[str, dict[str, Any]]:
    steps = sequence(job.get("steps"), "required job steps")
    identified = [step for step in steps if isinstance(step, dict) and step.get("id")]
    result = {str(step["id"]): step for step in identified}
    if len(result) != len(identified):
        raise ValueError("required job contains duplicate step ids")
    return result


def assert_blocking(step: dict[str, Any], label: str) -> None:
    if not is_false_or_absent(step.get("continue-on-error")):
        raise ValueError(f"{label} is non-blocking via continue-on-error")


def verify_events(workflow: dict[str, Any], fixture: dict[str, Any]) -> None:
    events = mapping(workflow.get("on"), "workflow on")
    for event_name in sequence(fixture.get("required_events"), "required_events"):
        if event_name not in events:
            raise ValueError(f"required event is absent: {event_name}")
        config = events[event_name]
        if config in (None, ""):
            continue
        event = mapping(config, f"event {event_name}")
        forbidden = set(fixture.get("forbidden_event_filters", [])) & set(event)
        if forbidden:
            raise ValueError(f"event {event_name} is filtered by {sorted(forbidden)!r}")


def verify_runner_steps(job: dict[str, Any], fixture: dict[str, Any]) -> int:
    indexed = steps_by_id(job)
    required = sequence(fixture.get("required_runner_steps"), "required_runner_steps")
    for policy in required:
        step_id = policy["id"]
        step = indexed.get(step_id)
        if step is None:
            raise ValueError(f"required runner step is absent: {step_id}")
        assert_blocking(step, step_id)
        if step.get("working-directory") != policy["working_directory"]:
            raise ValueError(f"{step_id} has an unapproved working-directory")
        if str(step.get("run", "")).strip() != policy["run"]:
            raise ValueError(f"{step_id} has an unapproved runner command")
        if step.get("if") not in policy["allowed_if"]:
            raise ValueError(f"{step_id} has an unapproved if policy")
    return len(required)


def report_paths(step: dict[str, Any]) -> list[str]:
    raw = mapping(step.get("with"), "report artifact inputs").get("path")
    if not isinstance(raw, str):
        raise ValueError("report artifact path must be a YAML scalar")
    return [line.strip() for line in raw.splitlines() if line.strip()]


def verify_report_artifact(job: dict[str, Any], fixture: dict[str, Any]) -> int:
    policy = mapping(fixture.get("report_artifact"), "report_artifact")
    step = steps_by_id(job).get(policy["step_id"])
    if step is None:
        raise ValueError("required report artifact step is absent")
    assert_blocking(step, policy["step_id"])
    inputs = mapping(step.get("with"), "report artifact inputs")
    expected_paths = policy["required_paths"]
    if step.get("uses") != policy["uses"] or step.get("if") != policy["if"]:
        raise ValueError("report artifact action or always-run policy is unapproved")
    if inputs.get("name") != policy["name"]:
        raise ValueError("report artifact name is unapproved")
    if inputs.get("if-no-files-found") != policy["if_no_files_found"]:
        raise ValueError("report artifact does not fail for missing reports")
    actual_paths = report_paths(step)
    if actual_paths != expected_paths or not actual_paths:
        raise ValueError("report artifact paths are absent, reordered, or unapproved")
    return len(actual_paths)


def verify_existing_suites(jobs: dict[str, Any], fixture: dict[str, Any]) -> None:
    for policy in fixture.get("required_existing_suite_steps", []):
        job = mapping(jobs.get(policy["job_id"]), f"job {policy['job_id']}")
        matches = [
            step
            for step in sequence(job.get("steps"), f"job {policy['job_id']} steps")
            if isinstance(step, dict)
            and step.get("working-directory") == policy["working_directory"]
            and str(step.get("run", "")).strip() == policy["run"]
        ]
        if len(matches) != 1:
            raise ValueError(f"existing full suite step changed in {policy['job_id']}")
        assert_blocking(matches[0], f"existing suite in {policy['job_id']}")


def matches_any(path: str, globs: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in globs)


def verify_ci_inputs(job: dict[str, Any], fixture: dict[str, Any], workflow_file: str) -> None:
    inputs = {workflow_file}
    for step in sequence(job.get("steps"), "required job steps"):
        if not isinstance(step, dict):
            raise ValueError("required job contains a malformed step")
        working_directory = step.get("working-directory")
        if working_directory:
            inputs.add(f"{working_directory.rstrip('/')}/**")
        step_inputs = step.get("with")
        if isinstance(step_inputs, dict):
            for key in ("cache-dependency-path", "path"):
                value = step_inputs.get(key)
                if isinstance(value, str):
                    inputs.update(line.strip() for line in value.splitlines() if line.strip())
        run = step.get("run")
        if isinstance(run, str):
            inputs.update(re.findall(r"(?:backend|frontend)/[A-Za-z0-9_./*?-]+", run))
    allowed = sequence(fixture.get("allowed_ci_input_globs"), "allowed_ci_input_globs")
    disallowed = sorted(path for path in inputs if not matches_any(path, allowed))
    if disallowed:
        raise ValueError(f"CI input paths are outside the app allow-list: {disallowed!r}")


def main() -> int:
    args = parse_args()
    try:
        fixture = json.loads(args.fixture.read_text(encoding="utf-8"))
        if fixture.get("subject") != "github_actions_workflow":
            raise ValueError("fixture must use subject=github_actions_workflow")
        workflow_path = args.workflow_dir / Path(fixture["workflow_file"]).name
        workflow = mapping(yaml.load(workflow_path.read_text(encoding="utf-8"), Loader=StringKeyLoader), "workflow")
        if workflow.get("name") != fixture["workflow_name"]:
            raise ValueError("workflow name is absent or unapproved")
        verify_events(workflow, fixture)
        jobs = mapping(workflow.get("jobs"), "workflow jobs")
        job_policy = mapping(fixture.get("required_job"), "required_job")
        job = mapping(jobs.get(job_policy["id"]), "required job")
        if job.get("name") != job_policy["name"]:
            raise ValueError("required job name is absent or unapproved")
        if job.get("runs-on") not in job_policy["allowed_runs_on"]:
            raise ValueError("required job runner is absent or unapproved")
        if job.get("if") is not None or not is_false_or_absent(job.get("continue-on-error")):
            raise ValueError("required job is filtered or non-blocking")
        runner_count = verify_runner_steps(job, fixture)
        report_count = verify_report_artifact(job, fixture)
        verify_existing_suites(jobs, fixture)
        verify_ci_inputs(job, fixture, fixture["workflow_file"])
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError, yaml.YAMLError) as error:
        print(f"ci_policy_verification=FAIL: {error}", file=sys.stderr)
        return 1

    print("ci_policy_verification=PASS")
    print(f"required_runner_steps={runner_count}")
    print(f"required_report_paths={report_count}")
    print(f"required_existing_suite_steps={len(fixture['required_existing_suite_steps'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
