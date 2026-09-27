#!/usr/bin/env python3
"""Planotell dev-only delivery for plantpal (D113): deploy, receipt, lookup, rollback.

Deploys ONE isolated dev candidate of plantpal from a revision that is already
merged into ``origin/dev``, observes what the running app reports about itself,
runs smoke checks, and stores an app-owned deployment receipt. ``lookup`` maps a
receipt to the tagged contracts v0.31.0 ``delivery.producer-result`` shape
(producer ``app-deploy``, nativeRef ``plantpal:deployments/<id>``).

Hard lines (see docs/dev-delivery.md):
  * dev only. This tool never touches ``main``, deploy/vps/ or production secrets.
  * only revisions reachable from ``origin/dev`` whose required checks passed.
    There is no bypass flag (D112 clause 3).
  * unknown stays unknown. A missing observation is ``unknown``/``null``, never
    ``passed``/0/a configured value.

Usage (from the repo root):
  python tools/dev-delivery/dev_delivery.py deploy [--revision SHA]
        [--delivery-id UUID] [--operation-key KEY]
        [--criterion ID=METHOD:PATH:STATUS[:CONTAINS]]...
  python tools/dev-delivery/dev_delivery.py lookup <deploymentId>
  python tools/dev-delivery/dev_delivery.py receipt <deploymentId>
  python tools/dev-delivery/dev_delivery.py list
  python tools/dev-delivery/dev_delivery.py observe
  python tools/dev-delivery/dev_delivery.py reconcile <deploymentId>
  python tools/dev-delivery/dev_delivery.py rollback [--to <deploymentId>]
  python tools/dev-delivery/dev_delivery.py down
"""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import io
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import tarfile
import urllib.error
import urllib.request
from pathlib import Path

REPOSITORY = "plantpal"
GITHUB_REPO = "ElasMoul/plants"
BRANCH = "dev"
APP_IDENTITY = "plantpal"
COMPOSE_PROJECT = "plantpal-devdelivery"
COMPOSE_FILE = Path("deploy/dev-delivery/docker-compose.yml")
DEFAULT_PORT = 8184
PLANOTELL_URL = "http://planotell.platform.localhost"
RECEIPT_SCHEMA = "plantpal.dev-deployment-receipt/1"  # plantpal-native until contracts tags one

# Checks that must have passed on GitHub before a revision may be deployed.
# The three run on the push of the merged SHA to dev; sonar-gate runs on the PR
# that produced it (D112: pull_request into dev only).
PUSH_CHECKS = ("Backend CI", "Frontend CI", "Detect secrets")
PR_CHECK = "sonar-gate"

SHA_RE = re.compile(r"^[0-9a-f]{40}$")

# Keys a dev env file may carry. Anything else, and any production-only key, is
# refused so a production .env can't be pointed at this stack by accident.
ALLOWED_ENV_KEYS = {
    "JWT_SECRET", "VAPID_PUBLIC_KEY", "VAPID_PRIVATE_KEY",
    "GITHUB_TOKEN", "GITHUB_BASE_URL", "GITHUB_IDENTIFICATION_MODEL",
    "GITHUB_ANNOTATION_MODEL", "GITHUB_GPT41_MODEL", "GITHUB_O4_MINI_MODEL",
    "GITHUB_GPT41_MINI_MODEL", "DEEPSEEK_MODEL", "PLANTNET_API_KEY",
    "OLLAMA_BASE_URL", "OLLAMA_MODEL", "ANTHROPIC_API_KEY", "ANTHROPIC_BASE_URL",
    "ANTHROPIC_MODEL_DEFAULT", "ANTHROPIC_MODEL_CHEAP", "ANTHROPIC_MODEL_MAX",
    "CORS_ALLOWED_ORIGINS",
}
PRODUCTION_ONLY_KEYS = {
    "DATABASE_URL", "REDIS_URL", "CLOUDINARY_URL", "SENTRY_DSN", "STORAGE_TYPE",
    "DEEPSEEK_HOSTED_API_KEY", "DEEPSEEK_HOSTED_BASE_URL", "APP_ADMIN_BOOTSTRAP_EMAIL",
    "VPS_HOST", "VPS_SSH_KEY",
}
UNSET = "unset-in-dev-delivery"


class DeliveryError(Exception):
    """A refusal or failure with a message meant for the operator."""

    def __init__(self, message: str, exit_code: int = 2):
        super().__init__(message)
        self.exit_code = exit_code


# ── paths / state ─────────────────────────────────────────────────────────────

def repo_root() -> Path:
    out = run(["git", "rev-parse", "--show-toplevel"], check=True).stdout.strip()
    return Path(out)


def state_dir(root: Path) -> Path:
    d = root / ".dev-delivery"
    for sub in ("receipts", "build", "logs"):
        (d / sub).mkdir(parents=True, exist_ok=True)
    return d


def now_utc() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")


def run(cmd, *, check=False, timeout=600, cwd=None, env=None, log=None):
    """Run a command, never blocking forever; output optionally appended to a log."""
    proc = subprocess.run(
        cmd, cwd=cwd, env=env, capture_output=True, text=True,
        encoding="utf-8", errors="replace", timeout=timeout,
    )
    if log is not None:
        with open(log, "a", encoding="utf-8") as fh:
            fh.write(f"$ {' '.join(map(str, cmd))}\n{proc.stdout}{proc.stderr}\n[exit {proc.returncode}]\n")
    if check and proc.returncode != 0:
        raise DeliveryError(f"command failed ({proc.returncode}): {' '.join(map(str, cmd))}\n{proc.stderr.strip()}")
    return proc


# ── receipts ──────────────────────────────────────────────────────────────────

def receipt_path(state: Path, deployment_id: str) -> Path:
    if not re.fullmatch(r"[A-Za-z0-9._:-]{1,128}", deployment_id):
        raise DeliveryError(f"not a deployment id: {deployment_id!r}")
    return state / "receipts" / f"{deployment_id}.json"


def save_receipt(state: Path, receipt: dict) -> None:
    path = receipt_path(state, receipt["deploymentId"])
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    os.replace(tmp, path)


def load_receipt(state: Path, deployment_id: str) -> dict:
    path = receipt_path(state, deployment_id)
    if not path.exists():
        raise DeliveryError(f"deployment_not_found: {deployment_id}", exit_code=4)
    return json.loads(path.read_text(encoding="utf-8"))


def all_receipts(state: Path) -> list[dict]:
    receipts = [json.loads(p.read_text(encoding="utf-8")) for p in (state / "receipts").glob("*.json")]
    return sorted(receipts, key=lambda r: r["startedAt"])


def last_passed(state: Path, exclude: str | None = None) -> dict | None:
    passed = [r for r in all_receipts(state) if r["result"] == "passed" and r["deploymentId"] != exclude]
    return passed[-1] if passed else None


def rollback_identity(previous: dict | None) -> dict | None:
    if previous is None:
        return None
    return {
        "deploymentId": previous["deploymentId"],
        "revision": previous["revision"],
        "imageDigests": previous["imageDigests"],
    }


def to_producer_result(receipt: dict) -> dict:
    """Map a native receipt to contracts v0.31.0 delivery.producer-result, validated by the binding."""
    observed = receipt.get("observed") or {}
    environment = None
    if (observed.get("appIdentity") and observed.get("deploymentId")
            and SHA_RE.match(observed.get("revision") or "")):
        environment = {
            "name": "dev",
            "appIdentity": observed["appIdentity"],
            "deploymentId": observed["deploymentId"],
            "deployedRevision": observed["revision"],
            "url": receipt["testUrl"],
        }
    result = {
        "producer": "app-deploy",
        "operationId": receipt["deploymentId"],
        "correlation": receipt["correlation"],
        "repository": REPOSITORY,
        "branch": receipt["branch"],
        "revision": receipt["revision"],
        "outcome": receipt["result"],
        "exitCode": receipt["exitCode"],
        "observedAt": receipt.get("observedAt") or receipt["startedAt"],
        "nativeRef": f"plantpal:deployments/{receipt['deploymentId']}",
        "artifactRef": (receipt.get("imageDigests") or {}).get("backend"),
        "environment": environment,
        "checks": [
            {"name": c["name"], "criterionId": c.get("criterionId"),
             "outcome": c["outcome"], "exitCode": c.get("exitCode")}
            for c in receipt["checks"]
        ],
    }
    from platform_contracts.delivery.delivery_producer_result import DeliveryProducerResult
    return DeliveryProducerResult.model_validate(result).model_dump(mode="json")


# ── pre-deploy gate: merged + required checks ─────────────────────────────────

def resolve_merged_revision(root: Path, revision: str | None) -> str:
    run(["git", "fetch", "--quiet", "origin", BRANCH], check=True, cwd=root, timeout=120)
    target = revision or run(["git", "rev-parse", f"origin/{BRANCH}"], check=True, cwd=root).stdout.strip()
    target = run(["git", "rev-parse", "--verify", f"{target}^{{commit}}"], check=True, cwd=root).stdout.strip()
    if not SHA_RE.match(target):
        raise DeliveryError(f"could not resolve a full revision: {target!r}")
    merged = run(["git", "merge-base", "--is-ancestor", target, f"origin/{BRANCH}"], cwd=root)
    if merged.returncode != 0:
        raise DeliveryError(f"refused: {target} is not merged into origin/{BRANCH} (task-branch revisions are never deployed)")
    return target


def check_outcome(conclusions: list[str | None], status_all_completed: bool) -> str:
    """GitHub check-run conclusions -> passed|failed|pending|unknown (unknown stays unknown)."""
    # A job skipped by its `if:` (e.g. sonar-gate's push-event twin of the PR run) is not a
    # verdict; judge the runs that actually ran. Only-skipped means nothing ran: unknown.
    if not status_all_completed:
        return "pending"
    conclusions = [c for c in conclusions if c != "skipped"]
    if not conclusions:
        return "unknown"
    if any(c in ("failure", "timed_out", "cancelled", "action_required") for c in conclusions):
        return "failed"
    if all(c == "success" for c in conclusions):
        return "passed"
    return "unknown"  # skipped/neutral/stale: not evidence that the check passed


def github_check(sha: str, name: str) -> str:
    proc = run(["gh", "api", f"repos/{GITHUB_REPO}/commits/{sha}/check-runs?per_page=100",
                "--jq", f'[.check_runs[] | select(.name == "{name}") | {{status, conclusion}}]'], timeout=60)
    if proc.returncode != 0:
        return "unknown"
    runs_ = json.loads(proc.stdout or "[]")
    return check_outcome([r["conclusion"] for r in runs_], all(r["status"] == "completed" for r in runs_))


def pull_request_head(sha: str) -> str | None:
    proc = run(["gh", "api", f"repos/{GITHUB_REPO}/commits/{sha}/pulls",
                "--jq", f'[.[] | select(.base.ref == "{BRANCH}" and .merged_at != null and .merge_commit_sha == "{sha}") | .head.sha]'],
               timeout=60)
    if proc.returncode != 0:
        return None
    heads = json.loads(proc.stdout or "[]")
    return heads[0] if len(heads) == 1 else None


def required_checks(sha: str) -> list[dict]:
    checks = [{"name": f"ci:{n}", "criterionId": None, "outcome": github_check(sha, n), "exitCode": None}
              for n in PUSH_CHECKS]
    head = pull_request_head(sha)
    sonar = github_check(head, PR_CHECK) if head else "unknown"
    checks.append({"name": f"quality:{PR_CHECK}", "criterionId": None, "outcome": sonar,
                   "exitCode": None, "prHead": head})
    return checks


# ── isolated dev config ───────────────────────────────────────────────────────

P256_P = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF
P256_N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
P256_B = 0x5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B
P256_G = (0x6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296,
          0x4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5)


def _p256_add(a, b):
    if a is None:
        return b
    if b is None:
        return a
    if a[0] == b[0] and (a[1] + b[1]) % P256_P == 0:
        return None
    if a == b:
        lam = (3 * a[0] * a[0] - 3) * pow(2 * a[1], -1, P256_P) % P256_P
    else:
        lam = (b[1] - a[1]) * pow(b[0] - a[0], -1, P256_P) % P256_P
    x = (lam * lam - a[0] - b[0]) % P256_P
    return x, (lam * (a[0] - x) - a[1]) % P256_P


def _p256_mul(k, point):
    acc = None
    while k:
        if k & 1:
            acc = _p256_add(acc, point)
        point = _p256_add(point, point)
        k >>= 1
    return acc


def on_p256(point) -> bool:
    x, y = point
    return (y * y - (x * x * x - 3 * x + P256_B)) % P256_P == 0


def vapid_keypair() -> tuple[str, str]:
    """Fresh VAPID pair (base64url, unpadded): 65-byte uncompressed public point, 32-byte scalar."""
    d = secrets.randbelow(P256_N - 1) + 1
    x, y = _p256_mul(d, P256_G)
    pub = b"\x04" + x.to_bytes(32, "big") + y.to_bytes(32, "big")
    b64 = lambda raw: base64.urlsafe_b64encode(raw).rstrip(b"=").decode()
    return b64(pub), b64(d.to_bytes(32, "big"))


def parse_env(text: str) -> dict[str, str]:
    env = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip()
    return env


def validate_dev_env(env: dict[str, str]) -> None:
    prod = sorted(set(env) & PRODUCTION_ONLY_KEYS)
    if prod:
        raise DeliveryError(f"refused: dev env carries production-only keys {prod}")
    unknown = sorted(set(env) - ALLOWED_ENV_KEYS)
    if unknown:
        raise DeliveryError(f"refused: dev env carries keys outside the dev allowlist {unknown}")


def ensure_dev_config(state: Path) -> tuple[Path, str]:
    """Create (once) the stack's own dev-only secrets; never copied from backend/.env or deploy/vps/."""
    env_file = state / "dev.env"
    if not env_file.exists():
        pub, priv = vapid_keypair()
        env_file.write_text(
            "# plantpal dev-delivery env — generated, dev-only, never production values.\n"
            "# Optional: replace the AI keys with DEV-scoped keys to make AI features testable.\n"
            f"JWT_SECRET={base64.b64encode(secrets.token_bytes(64)).decode()}\n"
            f"VAPID_PUBLIC_KEY={pub}\nVAPID_PRIVATE_KEY={priv}\n"
            f"GITHUB_TOKEN={UNSET}\nPLANTNET_API_KEY={UNSET}\nANTHROPIC_API_KEY=\n",
            encoding="utf-8")
    validate_dev_env(parse_env(env_file.read_text(encoding="utf-8")))
    db_file = state / "db-password"
    if not db_file.exists():
        db_file.write_text(secrets.token_hex(24), encoding="utf-8")
    return env_file, db_file.read_text(encoding="utf-8").strip()


def contracts_m2(tree: Path) -> Path:
    pom = (tree / "backend" / "pom.xml").read_text(encoding="utf-8")
    match = re.search(r"<artifactId>contracts</artifactId>\s*<version>([^<]+)</version>", pom)
    if not match:
        raise DeliveryError("could not read the contracts pin from backend/pom.xml")
    path = Path.home() / ".m2" / "repository" / "io" / "platform" / "contracts" / match.group(1)
    if not path.exists():
        raise DeliveryError(f"contracts {match.group(1)} not installed in {path} — see DEPLOYMENT.md 'Consuming contracts'")
    return path


# ── build / run ───────────────────────────────────────────────────────────────

def archive_tree(root: Path, state: Path, sha: str) -> Path:
    tree = state / "build" / sha
    if not (tree / COMPOSE_FILE).exists():
        if tree.exists():
            shutil.rmtree(tree)
        tar_bytes = subprocess.run(["git", "archive", "--format=tar", sha], cwd=root,
                                   capture_output=True, check=True, timeout=300).stdout
        with tarfile.open(fileobj=io.BytesIO(tar_bytes)) as tar:
            tar.extractall(tree, filter="data")
    if not (tree / COMPOSE_FILE).exists():
        raise DeliveryError(f"refused: {sha} predates dev delivery (no {COMPOSE_FILE.as_posix()})")
    return tree


def compose_env(tree: Path, sha: str, deployment_id: str, env_file: Path, db_password: str, port: int) -> dict:
    env = dict(os.environ)
    env.update({
        "APP_REVISION": sha,
        "APP_DEPLOYMENT_ID": deployment_id,
        "CONTRACTS_M2": str(contracts_m2(tree)),
        "DEV_DELIVERY_ENV_FILE": str(env_file.resolve()),
        "DEV_DB_PASSWORD": db_password,
        "DEV_DELIVERY_PORT": str(port),
    })
    return env


def compose(tree: Path, env: dict, args: list[str], log: Path, timeout: int):
    cmd = ["docker", "compose", "-p", COMPOSE_PROJECT, "-f", str(tree / COMPOSE_FILE), *args]
    return run(cmd, env=env, log=log, timeout=timeout)


def image_digest(image: str) -> str | None:
    proc = run(["docker", "image", "inspect", image, "--format", "{{.Id}}"], timeout=60)
    digest = proc.stdout.strip()
    return digest if proc.returncode == 0 and digest.startswith("sha256:") else None


# ── observation + smoke ───────────────────────────────────────────────────────

def http(method: str, url: str, timeout: float = 10.0) -> tuple[int | None, str]:
    """(status, body); status None when the URL could not be reached at all."""
    req = urllib.request.Request(url, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read(200_000).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read(200_000).decode("utf-8", "replace")
    except (urllib.error.URLError, OSError, ValueError):
        return None, ""


def observe_identity(base_url: str) -> dict | None:
    status, body = http("GET", f"{base_url}/actuator/info")
    if status != 200:
        return None
    try:
        deployment = json.loads(body).get("deployment")
    except (ValueError, AttributeError):
        return None
    if not isinstance(deployment, dict):
        return None
    return {k: deployment.get(k) for k in ("appIdentity", "revision", "deploymentId", "environment")}


def compare(observed_value, expected) -> str:
    if observed_value is None:
        return "unknown"
    return "passed" if observed_value == expected else "failed"


def identity_checks(observed: dict | None, sha: str, deployment_id: str) -> list[dict]:
    if observed is None:
        return [{"name": n, "criterionId": None, "outcome": "unknown", "exitCode": None}
                for n in ("identity:app", "identity:revision", "identity:deployment", "identity:environment")]
    return [
        {"name": "identity:app", "criterionId": None, "outcome": compare(observed["appIdentity"], APP_IDENTITY), "exitCode": None},
        {"name": "identity:revision", "criterionId": None, "outcome": compare(observed["revision"], sha), "exitCode": None},
        {"name": "identity:deployment", "criterionId": None, "outcome": compare(observed["deploymentId"], deployment_id), "exitCode": None},
        {"name": "identity:environment", "criterionId": None, "outcome": compare(observed["environment"], "dev"), "exitCode": None},
    ]


def smoke(base_url: str, name: str, method: str, path: str, status: int,
          contains: str | None = None, criterion_id: str | None = None) -> dict:
    got, body = http(method, f"{base_url}{path}")
    if got is None:
        outcome = "unavailable"
    elif got == status and (contains is None or contains in body):
        outcome = "passed"
    else:
        outcome = "failed"
    return {"name": name, "criterionId": criterion_id, "outcome": outcome, "exitCode": None,
            "detail": f"{method} {path} -> {got} (expected {status}{', contains ' + repr(contains) if contains else ''})"}


def parse_criterion(spec: str) -> dict:
    """ID=METHOD:PATH:STATUS[:CONTAINS] — one criterion smoke check from the approved plan."""
    match = re.fullmatch(r"([a-zA-Z0-9][a-zA-Z0-9_.-]{0,79})=(GET|HEAD|POST|PUT|DELETE):(/[^:]*):(\d{3})(?::(.+))?", spec)
    if not match:
        raise DeliveryError(f"bad --criterion {spec!r}; expected ID=METHOD:PATH:STATUS[:CONTAINS]")
    cid, method, path, status, contains = match.groups()
    return {"criterionId": cid, "method": method, "path": path, "status": int(status), "contains": contains}


def run_checks(base_url: str, sha: str, deployment_id: str, criteria: list[dict]) -> tuple[dict | None, list[dict]]:
    observed = observe_identity(base_url)
    checks = identity_checks(observed, sha, deployment_id)
    checks.append(smoke(base_url, "smoke:frontend-index", "GET", "/", 200, "<app-root"))
    checks.append(smoke(base_url, "smoke:backend-health", "GET", "/actuator/health", 200, '"UP"'))
    checks.append(smoke(base_url, "smoke:api-auth-guard", "GET", "/api/v1/plants", 401))
    for c in criteria:
        checks.append(smoke(base_url, f"criterion:{c['criterionId']}", c["method"], c["path"],
                            c["status"], c["contains"], c["criterionId"]))
    return observed, checks


def planotell_check(sha: str, deployment_id: str) -> dict:
    """Observe the requested URL itself — configuration never counts as verification."""
    observed = observe_identity(PLANOTELL_URL)
    if observed is None:
        outcome, detail = "unavailable", f"{PLANOTELL_URL}/actuator/info not reachable or not plantpal"
    elif observed["revision"] == sha and observed["deploymentId"] == deployment_id:
        outcome, detail = "passed", f"{PLANOTELL_URL} serves {deployment_id} at {sha}"
    else:
        outcome, detail = "failed", f"{PLANOTELL_URL} serves {observed}"
    return {"name": "route:planotell", "criterionId": None, "outcome": outcome, "exitCode": None, "detail": detail}


def overall(deploy_exit: int | None, checks: list[dict]) -> str:
    """passed only if the stack came up and every gating check passed; unknown never becomes passed."""
    gating = [c for c in checks if c["name"] != "route:planotell"]
    if deploy_exit is None:
        return "unknown"
    if deploy_exit != 0 or any(c["outcome"] == "failed" for c in gating):
        return "failed"
    if all(c["outcome"] == "passed" for c in gating):
        return "passed"
    return "unknown"


# ── commands ──────────────────────────────────────────────────────────────────

def find_by_operation_key(state: Path, key: str) -> dict | None:
    for r in all_receipts(state):
        if r["correlation"].get("operationKey") == key:
            return r
    return None


def new_deployment_id(sha: str) -> str:
    return f"pla-dev-{dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S')}-{sha[:12]}"


def settle(root: Path, state: Path, receipt: dict, tree: Path, env: dict, build: bool,
           criteria: list[dict]) -> dict:
    """Bring the stack up for `receipt` and record everything observed."""
    log = state / "logs" / f"{receipt['deploymentId']}.log"
    sha = receipt["revision"]
    exit_code = None
    try:
        if build:
            built = compose(tree, env, ["build"], log, timeout=1800)
            if built.returncode != 0:
                exit_code = built.returncode
        if exit_code is None:
            up = compose(tree, env, ["up", "-d", "--no-build", "--wait", "--wait-timeout", "600"], log, timeout=720)
            exit_code = up.returncode
    except subprocess.TimeoutExpired:
        exit_code = None  # timed out: result is unknown, not failed-with-a-made-up-code
    receipt["exitCode"] = exit_code
    receipt["imageDigests"] = {
        "backend": image_digest(f"plantpal-devdelivery-backend:{sha}"),
        "frontend": image_digest(f"plantpal-devdelivery-frontend:{sha}"),
    }
    base_url = f"http://127.0.0.1:{receipt['port']}"
    observed, checks = run_checks(base_url, sha, receipt["deploymentId"], criteria)
    checks.append(planotell_check(sha, receipt["deploymentId"]))
    receipt["observed"] = observed
    receipt["checks"] = receipt["preDeployChecks"] + checks
    receipt["observedAt"] = now_utc()
    receipt["testUrl"] = PLANOTELL_URL if checks[-1]["outcome"] == "passed" else base_url
    receipt["result"] = overall(exit_code, receipt["checks"])
    receipt["finishedAt"] = now_utc()
    receipt["log"] = str(log.relative_to(root))
    save_receipt(state, receipt)
    return receipt


def base_receipt(sha: str, deployment_id: str, port: int, correlation: dict, kind: str,
                 pre_checks: list[dict], rollback: dict | None) -> dict:
    return {
        "schema": RECEIPT_SCHEMA, "kind": kind, "deploymentId": deployment_id,
        "repository": REPOSITORY, "branch": BRANCH, "revision": sha, "revisionRole": "merged",
        "environment": "dev", "composeProject": COMPOSE_PROJECT, "port": port,
        "correlation": correlation, "result": "pending", "exitCode": None,
        "startedAt": now_utc(), "finishedAt": None, "observedAt": None,
        "imageDigests": {"backend": None, "frontend": None},
        "digestKind": "local docker image id (sha256 of image config; no registry push)",
        "preDeployChecks": pre_checks, "checks": pre_checks, "observed": None,
        "testUrl": f"http://127.0.0.1:{port}", "rollback": rollback, "log": None,
    }


def cmd_deploy(args) -> int:
    root = repo_root()
    state = state_dir(root)
    criteria = [parse_criterion(c) for c in args.criterion]
    if args.operation_key:
        existing = find_by_operation_key(state, args.operation_key)
        if existing:
            requested = resolve_merged_revision(root, args.revision)
            if existing["revision"] != requested:
                raise DeliveryError(f"operation_key_conflict: {args.operation_key} already names {existing['deploymentId']} at {existing['revision']}", 3)
            print(json.dumps(to_producer_result(existing), indent=2))
            return 0
    sha = resolve_merged_revision(root, args.revision)
    pre_checks = required_checks(sha)
    blocking = [c for c in pre_checks if c["outcome"] != "passed"]
    if blocking:
        names = ", ".join(f"{c['name']}={c['outcome']}" for c in blocking)
        raise DeliveryError(f"refused: required checks for {sha} have not all passed ({names})")
    env_file, db_password = ensure_dev_config(state)
    tree = archive_tree(root, state, sha)
    deployment_id = new_deployment_id(sha)
    correlation = {"deliveryId": args.delivery_id, "operationKey": args.operation_key}
    receipt = base_receipt(sha, deployment_id, args.port, correlation, "deploy", pre_checks,
                           rollback_identity(last_passed(state)))
    save_receipt(state, receipt)  # reserve first: a crash leaves a findable pending record
    env = compose_env(tree, sha, deployment_id, env_file, db_password, args.port)
    receipt = settle(root, state, receipt, tree, env, build=True, criteria=criteria)
    print(json.dumps(to_producer_result(receipt), indent=2))
    return 0 if receipt["result"] == "passed" else 1


def cmd_rollback(args) -> int:
    root = repo_root()
    state = state_dir(root)
    if args.to:
        target = load_receipt(state, args.to)
    else:
        current = all_receipts(state)
        if not current or not current[-1].get("rollback"):
            raise DeliveryError("no rollback identity recorded on the latest deployment")
        target = load_receipt(state, current[-1]["rollback"]["deploymentId"])
    sha = target["revision"]
    for component, digest in target["imageDigests"].items():
        if digest is None or image_digest(f"plantpal-devdelivery-{component}:{sha}") != digest:
            raise DeliveryError(f"refused: {component} image of {target['deploymentId']} is gone or changed; redeploy {sha} instead")
    env_file, db_password = ensure_dev_config(state)
    tree = archive_tree(root, state, sha)
    deployment_id = new_deployment_id(sha)
    latest = all_receipts(state)[-1]
    receipt = base_receipt(sha, deployment_id, args.port, {"deliveryId": None, "operationKey": None},
                           "rollback", target["preDeployChecks"], rollback_identity(latest if latest["result"] == "passed" else None))
    receipt["rollbackOf"] = latest["deploymentId"]
    receipt["restores"] = target["deploymentId"]
    save_receipt(state, receipt)
    env = compose_env(tree, sha, deployment_id, env_file, db_password, args.port)
    receipt = settle(root, state, receipt, tree, env, build=False, criteria=[])
    print(json.dumps(to_producer_result(receipt), indent=2))
    return 0 if receipt["result"] == "passed" else 1


def cmd_reconcile(args) -> int:
    """Settle a receipt left `pending` by a crash: re-observe; never re-deploy."""
    root = repo_root()
    state = state_dir(root)
    receipt = load_receipt(state, args.deployment_id)
    if receipt["result"] == "pending":
        base_url = f"http://127.0.0.1:{receipt['port']}"
        observed, checks = run_checks(base_url, receipt["revision"], receipt["deploymentId"], [])
        receipt["observed"] = observed
        receipt["checks"] = receipt["preDeployChecks"] + checks
        receipt["observedAt"] = now_utc()
        # The deploy's own exit was never recorded, so the best a re-observation can say is
        # that the running app is (or is not) this deployment; that is still not `passed`.
        receipt["result"] = "failed" if any(c["outcome"] == "failed" for c in checks) else "unknown"
        receipt["finishedAt"] = now_utc()
        save_receipt(state, receipt)
    print(json.dumps(to_producer_result(receipt), indent=2))
    return 0


def cmd_lookup(args) -> int:
    state = state_dir(repo_root())
    print(json.dumps(to_producer_result(load_receipt(state, args.deployment_id)), indent=2))
    return 0


def cmd_receipt(args) -> int:
    state = state_dir(repo_root())
    print(json.dumps(load_receipt(state, args.deployment_id), indent=2))
    return 0


def cmd_list(_args) -> int:
    for r in all_receipts(state_dir(repo_root())):
        print(f"{r['deploymentId']}\t{r['kind']}\t{r['revision']}\t{r['result']}\t{r['startedAt']}")
    return 0


def cmd_observe(args) -> int:
    print(json.dumps({
        "loopback": observe_identity(f"http://127.0.0.1:{args.port}"),
        "planotell": observe_identity(PLANOTELL_URL),
        "observedAt": now_utc(),
    }, indent=2))
    return 0


def cmd_down(_args) -> int:
    proc = run(["docker", "compose", "-p", COMPOSE_PROJECT, "down"], timeout=300)
    print(proc.stdout + proc.stderr)
    return proc.returncode


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("deploy")
    p.add_argument("--revision")
    p.add_argument("--delivery-id")
    p.add_argument("--operation-key")
    p.add_argument("--criterion", action="append", default=[])
    p.add_argument("--port", type=int, default=DEFAULT_PORT)
    p.set_defaults(func=cmd_deploy)
    p = sub.add_parser("rollback")
    p.add_argument("--to")
    p.add_argument("--port", type=int, default=DEFAULT_PORT)
    p.set_defaults(func=cmd_rollback)
    for name, func in (("lookup", cmd_lookup), ("receipt", cmd_receipt), ("reconcile", cmd_reconcile)):
        p = sub.add_parser(name)
        p.add_argument("deployment_id")
        p.set_defaults(func=func)
    sub.add_parser("list").set_defaults(func=cmd_list)
    p = sub.add_parser("observe")
    p.add_argument("--port", type=int, default=DEFAULT_PORT)
    p.set_defaults(func=cmd_observe)
    sub.add_parser("down").set_defaults(func=cmd_down)
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except DeliveryError as e:
        print(f"dev-delivery: {e}", file=sys.stderr)
        return e.exit_code


if __name__ == "__main__":
    sys.exit(main())
