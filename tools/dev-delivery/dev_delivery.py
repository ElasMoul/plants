#!/usr/bin/env python3
"""Planotell dev-only delivery for plantpal (D113): deploy, receipt, lookup, rollback.

Deploys ONE isolated dev candidate of plantpal from a revision that is already
merged into ``origin/dev``, observes what the running app reports about itself,
runs smoke checks, and stores an app-owned deployment receipt.

The on-disk record is native (``plantpal.dev-deployment-receipt/1``). Both lookup
transports emit the tagged contracts **v0.36.0** shapes (producer ``app-deploy``,
nativeRef ``plantpal:deployments/<id>``):

  * ``lookup <id>``  -> ``delivery.producer-result``, built through the receipt by
    the §App-deploy mapping.
  * ``receipt <id>`` -> ``delivery.deployment-receipt``, the full tagged receipt
    (including rollback identity), validated against the JSON Schema in
    ``tools/dev-delivery/schemas/`` and against the cross-field rules the schema
    cannot express.

The same two documents are served over HTTP by ``serve`` (contracts **v0.37.0**
``app-deploy-lookup.openapi.yaml``): ``GET /delivery/v1/app-deploys/<id>`` and
``.../receipt``, bearer-authenticated, loopback-only.

A miss on either CLI command is exit 4 with ``deployment_not_found: <id>`` on stderr and
nothing on stdout: Factory reads that as ``unavailable``, never as ``failed``.

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
  python tools/dev-delivery/dev_delivery.py serve [--port 8185]
  python tools/dev-delivery/dev_delivery.py review start (--pr N | --branch B) [--revision SHA]
        [--idempotency-key K] [--delivery-id UUID] [--port 8186]
  python tools/dev-delivery/dev_delivery.py review status [--idempotency-key K]
  python tools/dev-delivery/dev_delivery.py review stop [--idempotency-key K]

``review`` runs an UNMERGED PR head in its own compose project, volumes, config and port
(contracts v0.38.0 §Review environments). One at a time; it never merges or pushes anything.
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
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

REPOSITORY = "plantpal"
GITHUB_REPO = "ElasMoul/plants"
BRANCH = "dev"
APP_IDENTITY = "plantpal"
COMPOSE_PROJECT = "plantpal-devdelivery"
COMPOSE_FILE = Path("deploy/dev-delivery/docker-compose.yml")
DEFAULT_PORT = 8184
REVIEW_COMPOSE_PROJECT = "plantpal-review"   # its own project: network, volumes, containers
REVIEW_PORT = 8186                           # next free 81xx after 8185 (PLATFORM_STATE); registration requested from platform-vault
IMAGE_PREFIX = {"dev": "plantpal-devdelivery", "review": "plantpal-review"}
REVIEW_KEY_RE = re.compile(r"^[^\s/]{1,200}$")      # launcher review port: idempotencyKey
BRANCH_RE = re.compile(r"^[A-Za-z0-9._/-]{1,200}$")
NEVER_REVIEWED = ("main", BRANCH)            # main deploys production; dev has its own deploy path
PLANOTELL_URL = "http://planotell.platform.localhost"
RECEIPT_SCHEMA = "plantpal.dev-deployment-receipt/1"  # the on-disk native record
CONTRACTS_TAG = "v0.38.0"                             # the tagged shapes both lookup transports emit
TAGGED_DIGEST_KIND = "local-image-id"                 # plantpal's digests are local engine image ids
# Vendored verbatim from the tag above; see schemas/README.md. Used only to validate.
SCHEMA_DIR = Path(__file__).resolve().parent / "schemas"
PRIMARY_COMPONENT = "backend"  # the component that serves /actuator/info, so it carries artifactRef

# Checks that must have passed on GitHub before a revision may be deployed.
# The three run on the push of the merged SHA to dev; sonar-gate runs on the PR
# that produced it (D112: pull_request into dev only).
PUSH_CHECKS = ("Backend CI", "Frontend CI", "Detect secrets")
PR_CHECK = "sonar-gate"

SHA_RE = re.compile(r"^[0-9a-f]{40}$")

# The tagged running-app identity fields, in the order app/deployment-identity declares them.
IDENTITY_FIELDS = ("appIdentity", "revision", "deploymentId", "environment")

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
    """A refusal or failure with a message meant for the operator.

    ``bare`` suppresses the ``dev-delivery:`` prefix. The lookup transports have a
    machine-read stderr contract (``deployment_not_found: <id>``, §App-deploy), so
    their miss message is printed verbatim.
    """

    def __init__(self, message: str, exit_code: int = 2, bare: bool = False):
        super().__init__(message)
        self.exit_code = exit_code
        self.bare = bare


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
        raise DeliveryError(f"deployment_not_found: {deployment_id}", exit_code=4, bare=True)
    return json.loads(path.read_text(encoding="utf-8"))


def all_receipts(state: Path) -> list[dict]:
    receipts = [json.loads(p.read_text(encoding="utf-8")) for p in (state / "receipts").glob("*.json")]
    return sorted(receipts, key=lambda r: r["startedAt"])


def dev_receipts(state: Path) -> list[dict]:
    """Dev deployments only: a review environment is never a rollback target or a dev operation key."""
    return [r for r in all_receipts(state) if r["environment"] == "dev"]


def last_passed(state: Path, exclude: str | None = None) -> dict | None:
    passed = [r for r in dev_receipts(state) if r["result"] == "passed" and r["deploymentId"] != exclude]
    return passed[-1] if passed else None


def rollback_identity(previous: dict | None) -> dict | None:
    if previous is None:
        return None
    return {
        "deploymentId": previous["deploymentId"],
        "revision": previous["revision"],
        "imageDigests": previous["imageDigests"],
    }


def to_tagged_receipt(receipt: dict) -> dict:
    """Native ``plantpal.dev-deployment-receipt/1`` -> contracts v0.36.0 ``delivery.deployment-receipt``.

    The tagged shape is closed, so the native-only fields stay in the on-disk file and
    are dropped here (contracts ``docs/task-delivery.md`` §Upgrade / repin v0.36.0):
    ``schema``, ``revisionRole``, ``composeProject``, ``port`` and ``log``, plus
    ``preDeployChecks`` (already the head of ``checks``). The native per-check
    ``detail``/``prHead`` are dropped for the same reason: ``check`` is closed too.
    """
    tagged = {
        "deploymentId": receipt["deploymentId"],
        "kind": receipt["kind"],
        "repository": receipt["repository"],
        "branch": receipt["branch"],
        "mergedRevision": receipt["revision"],           # the revision that was DEPLOYED
        "imageDigests": receipt["imageDigests"],
        "digestKind": TAGGED_DIGEST_KIND,
        "result": receipt["result"],
        "exitCode": receipt["exitCode"],
        "startedAt": receipt["startedAt"],
        "finishedAt": receipt["finishedAt"],
        "observedAt": receipt["observedAt"],
        "environment": {"name": receipt["environment"], "url": receipt["testUrl"]},
        "observed": receipt["observed"],                 # the revision that is SERVED
        "checks": [
            {"name": c["name"], "criterionId": c.get("criterionId"),
             "outcome": c["outcome"], "exitCode": c.get("exitCode")}
            for c in receipt["checks"]
        ],
        "correlation": receipt["correlation"],
        "rollback": receipt["rollback"],
        "rollbackOf": receipt.get("rollbackOf"),          # explicit null for kind: deploy
        "restores": receipt.get("restores"),
        "nativeRef": f"{REPOSITORY}:deployments/{receipt['deploymentId']}",
    }
    if receipt["environment"] == "review":
        # contracts v0.38.0 §Review environments: mergedRevision holds the PR head, and the
        # role says so. A dev receipt must NOT carry any of these, so they are review-only.
        tagged["revisionRole"] = "task"
        tagged["review"] = receipt["review"]
        tagged["environment"]["apiDocsUrl"] = receipt.get("apiDocsUrl")
    return tagged


def producer_result_from_tagged(tagged: dict) -> dict:
    """The §App-deploy receipt -> producer-result mapping, as contracts states it.

    ``environment`` is built **only** from what the running app reported (``observed``),
    never from the receipt's own ``deploymentId``/``mergedRevision``: a producer that
    filled it from its intentions would report a deployment verified that nobody
    observed. Rollback identity is deliberately not mapped — Factory reads ``rollback``
    from the receipt it re-fetches through ``nativeRef``.
    """
    observed = tagged["observed"]
    environment = None
    if observed is not None and observed.get("revision") is not None and observed.get("deploymentId") is not None:
        environment = {
            "name": tagged["environment"]["name"],
            "appIdentity": observed["appIdentity"],
            "deploymentId": observed["deploymentId"],
            "deployedRevision": observed["revision"],
            "url": tagged["environment"]["url"],
        }
    return {
        "producer": "app-deploy",
        "operationId": tagged["deploymentId"],
        "correlation": tagged["correlation"],
        "repository": tagged["repository"],
        "branch": tagged["branch"],
        "revision": tagged["mergedRevision"],
        "outcome": tagged["result"],
        "exitCode": tagged["exitCode"],
        "observedAt": tagged["observedAt"] or tagged["startedAt"],
        "nativeRef": tagged["nativeRef"],
        "artifactRef": tagged["imageDigests"].get(PRIMARY_COMPONENT),
        "environment": environment,
        "checks": tagged["checks"],
    }


def to_producer_result(receipt: dict) -> dict:
    """Map a native receipt to delivery.producer-result through the tagged receipt."""
    from platform_contracts.delivery.delivery_producer_result import DeliveryProducerResult
    return DeliveryProducerResult.model_validate(producer_result_from_tagged(to_tagged_receipt(receipt))).model_dump(mode="json")


# ── tagged validation: schema + the rules the schema cannot express ───────────

def check_deployment_semantics(tagged: dict) -> list[str]:
    """The app-deploy cross-field rules, ported from contracts ``tests/validate_delivery.py``.

    Equality between two fields of one document (the revision the app reports serving
    vs the one that was deployed) has no draft 2020-12 keyword, and it is exactly the
    claim a receipt exists to prove — so it must be checked here. Validating against
    the schema alone would happily accept a ``passed`` receipt whose URL serves
    something else.
    """
    out = []
    if tagged["nativeRef"] != f"{tagged['repository']}:deployments/{tagged['deploymentId']}":
        out.append("nativeRef must be <repository>:deployments/<deploymentId>")
    rollback = tagged.get("rollback")
    if rollback is not None and rollback["deploymentId"] == tagged["deploymentId"]:
        out.append("rollback identity must name an EARLIER deployment")
    if tagged["kind"] == "rollback" and tagged["deploymentId"] in (tagged.get("restores"), tagged.get("rollbackOf")):
        out.append("a rollback is a new deployment, never the one it restores or replaces")
    if tagged["environment"]["name"] == "review":
        if tagged.get("revisionRole") != "task":
            out.append("a review receipt must have revisionRole task")
        if tagged.get("review") is None:
            out.append("a review receipt must carry the review block")
        if tagged["kind"] != "deploy" or tagged.get("rollback") is not None:
            out.append("a review receipt is kind deploy with rollback null")
    elif "review" in tagged or "revisionRole" in tagged or "apiDocsUrl" in tagged["environment"]:
        out.append("a dev receipt must not carry review, revisionRole or apiDocsUrl")
    if tagged["result"] == "passed":
        observed = tagged.get("observed") or {}
        if observed.get("revision") != tagged["mergedRevision"]:
            out.append("passed requires the running app to report mergedRevision")
        if observed.get("deploymentId") != tagged["deploymentId"]:
            out.append("passed requires the running app to report this deploymentId")
        if tagged["environment"]["name"] != observed.get("environment"):
            out.append("passed requires the running app to report the receipt's environment")
    return out


def schema_dir() -> Path:
    """Where the JSON Schemas live: an explicit override, else the copies pinned beside this tool."""
    override = os.environ.get("PLATFORM_CONTRACTS_SCHEMAS")
    return Path(override) if override else SCHEMA_DIR


def schema_registry(where: Path):
    """A registry resolving the vendored schemas by ``$id`` and by the relative names their ``$ref``s use."""
    from referencing import Registry, Resource
    registry = Registry()
    for path in (where / "delivery").glob("*.json"):
        resource = Resource.from_contents(json.loads(path.read_text(encoding="utf-8")))
        registry = registry.with_resource(resource.contents["$id"], resource)
        registry = registry.with_resource(path.name, resource)
        registry = registry.with_resource("https://platform/contracts/delivery/" + path.name, resource)
    identity = Resource.from_contents(json.loads((where / "app" / "deployment-identity.json").read_text(encoding="utf-8")))
    registry = registry.with_resource(identity.contents["$id"], identity)
    registry = registry.with_resource("https://platform/contracts/app/deployment-identity.json", identity)
    return registry


def schema_problems(tagged: dict) -> list[str]:
    """Validate against the JSON Schema itself — not only the generated binding.

    The binding is generated from the schema and does not implement its ``if``/``then``
    conditionals, so it accepts documents the schema rejects (contracts
    ``docs/task-delivery.md`` §Binding caveat).
    """
    try:
        from jsonschema import Draft202012Validator, FormatChecker
    except ImportError as e:  # a partial venv would otherwise silently skip this check
        raise DeliveryError(f"jsonschema is required to validate the tagged receipt ({e}); "
                            "install tools/dev-delivery/requirements.txt") from e
    where = schema_dir()
    if not (where / "delivery" / "delivery.deployment-receipt.json").exists():
        raise DeliveryError(f"no delivery.deployment-receipt schema under {where} — "
                            "see tools/dev-delivery/schemas/README.md")
    schema = json.loads((where / "delivery" / "delivery.deployment-receipt.json").read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, registry=schema_registry(where), format_checker=FormatChecker())
    return [f"schema: {list(e.absolute_path)}: {e.message}" for e in validator.iter_errors(tagged)]


def normalize_tagged_receipt(tagged: dict) -> dict:
    """The receipt as the binding round-trips it, so both transports emit identical scalars.

    Without this the raw mapping and the binding's own dump disagree on incidental
    formatting (``AnyUrl`` grows a trailing slash), and a consumer comparing
    ``environment.url`` across the two transports would see two different strings.
    """
    from platform_contracts.delivery.delivery_deployment_receipt import DeliveryDeploymentReceipt
    return DeliveryDeploymentReceipt.model_validate(tagged).model_dump(mode="json")


def validate_tagged_receipt(tagged: dict) -> list[str]:
    """Binding + JSON Schema + cross-field rules. Empty list means the receipt is well formed.

    All three, because each catches something the others do not: the schema catches a
    wrong enum or a leaked native field, the cross-field rules catch a ``passed``
    receipt that proves nothing, and the binding catches what plantpal will actually emit.
    """
    from pydantic import ValidationError
    from platform_contracts.delivery.delivery_deployment_receipt import DeliveryDeploymentReceipt
    problems = []
    try:
        DeliveryDeploymentReceipt.model_validate(tagged)
    except ValidationError as e:
        problems += [f"binding: {list(err['loc'])}: {err['msg']}" for err in e.errors()[:5]]
    problems += schema_problems(tagged)
    problems += check_deployment_semantics(tagged)
    return problems


# ── pre-deploy gate: merged + required checks ─────────────────────────────────

def resolve_merged_revision(root: Path, revision: str | None) -> str:
    run(["git", "fetch", "--quiet", "origin", BRANCH], check=True, cwd=root, timeout=120)
    target = revision or run(["git", "rev-parse", f"origin/{BRANCH}"], check=True, cwd=root).stdout.strip()
    target = run(["git", "rev-parse", "--verify", f"{target}^{{commit}}"], check=True, cwd=root).stdout.strip()
    if not SHA_RE.match(target):
        raise DeliveryError(f"could not resolve a full revision: {target!r}")
    if not on_first_parent_line(root, target):
        raise DeliveryError(f"refused: {target} is not a revision of origin/{BRANCH} itself "
                            "(task-branch revisions are never deployed, even once merged)")
    return target


def on_first_parent_line(root: Path, sha: str) -> bool:
    """True only for commits `dev` itself pointed at (merge results), not commits merged in from a task branch."""
    line = run(["git", "rev-list", "--first-parent", f"origin/{BRANCH}"], check=True, cwd=root).stdout.split()
    return sha in line


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


def ensure_dev_config(state: Path, name: str = "dev") -> tuple[Path, str]:
    """Create (once) the stack's own dev-only secrets; never copied from backend/.env or deploy/vps/.

    ``name`` picks the environment's own files (``dev.env``/``db-password`` or
    ``review.env``/``review-db-password``), so a review never shares a secret with the dev stack.
    """
    env_file = state / f"{name}.env"
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
    db_file = state / ("db-password" if name == "dev" else f"{name}-db-password")
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


def compose_env(tree: Path, sha: str, deployment_id: str, env_file: Path, db_password: str, port: int,
                environment: str = "dev") -> dict:
    env = dict(os.environ)
    env.update({
        "APP_REVISION": sha,
        "APP_DEPLOYMENT_ID": deployment_id,
        "CONTRACTS_M2": str(contracts_m2(tree)),
        "DEV_DELIVERY_ENV_FILE": str(env_file.resolve()),
        "DEV_DB_PASSWORD": db_password,
        "DEV_DELIVERY_PORT": str(port),
        "APP_ENVIRONMENT": environment,
        "IMAGE_PREFIX": IMAGE_PREFIX[environment],
    })
    return env


def compose(tree: Path, env: dict, args: list[str], log: Path, timeout: int, project: str = COMPOSE_PROJECT):
    cmd = ["docker", "compose", "-p", project, "-f", str(tree / COMPOSE_FILE), *args]
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


def observe_identity_block(body: str) -> dict | None:
    """The ``/actuator/info`` ``deployment`` block as served, narrowed to the tagged fields.

    This block IS ``app/deployment-identity``: a field the app did not report stays
    absent here and reads as ``null``, never as a default. ``None`` means the body
    carried no identity block at all — which is not the same as an app that reported
    nothing, and neither ever counts as verified.
    """
    try:
        deployment = json.loads(body).get("deployment")
    except (ValueError, AttributeError):
        return None
    if not isinstance(deployment, dict):
        return None
    return {k: deployment.get(k) for k in IDENTITY_FIELDS}


def observe_identity(base_url: str) -> dict | None:
    status, body = http("GET", f"{base_url}/actuator/info")
    return observe_identity_block(body) if status == 200 else None


def compare(observed_value, expected) -> str:
    if observed_value is None:
        return "unknown"
    return "passed" if observed_value == expected else "failed"


def identity_checks(observed: dict | None, sha: str, deployment_id: str, environment: str = "dev") -> list[dict]:
    if observed is None:
        return [{"name": n, "criterionId": None, "outcome": "unknown", "exitCode": None}
                for n in ("identity:app", "identity:revision", "identity:deployment", "identity:environment")]
    return [
        {"name": "identity:app", "criterionId": None, "outcome": compare(observed["appIdentity"], APP_IDENTITY), "exitCode": None},
        {"name": "identity:revision", "criterionId": None, "outcome": compare(observed["revision"], sha), "exitCode": None},
        {"name": "identity:deployment", "criterionId": None, "outcome": compare(observed["deploymentId"], deployment_id), "exitCode": None},
        {"name": "identity:environment", "criterionId": None, "outcome": compare(observed["environment"], environment), "exitCode": None},
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


def run_checks(base_url: str, sha: str, deployment_id: str, criteria: list[dict],
               environment: str = "dev") -> tuple[dict | None, list[dict]]:
    observed = observe_identity(base_url)
    checks = identity_checks(observed, sha, deployment_id, environment)
    checks.append(smoke(base_url, "smoke:frontend-index", "GET", "/", 200, "<app-root"))
    checks.append(smoke(base_url, "smoke:backend-health", "GET", "/actuator/health", 200, '"UP"'))
    checks.append(smoke(base_url, "smoke:api-auth-guard", "GET", "/api/v1/plants", 401))
    if environment == "review":
        checks.append(smoke(base_url, "smoke:swagger-ui", "GET", "/swagger-ui.html", 200, "swagger-ui"))
        checks.append(smoke(base_url, "smoke:api-docs", "GET", "/v3/api-docs", 200, '"openapi"'))
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
    for r in dev_receipts(state):
        if r["correlation"].get("operationKey") == key:
            return r
    return None


def new_deployment_id(sha: str, prefix: str = "pla-dev") -> str:
    return f"{prefix}-{dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S')}-{sha[:12]}"


def settle(root: Path, state: Path, receipt: dict, tree: Path, env: dict, build: bool,
           criteria: list[dict]) -> dict:
    """Bring the stack up for `receipt` and record everything observed."""
    log = state / "logs" / f"{receipt['deploymentId']}.log"
    sha = receipt["revision"]
    environment = receipt["environment"]
    project = receipt["composeProject"]
    exit_code = None
    try:
        if build:
            built = compose(tree, env, ["build"], log, timeout=1800, project=project)
            if built.returncode != 0:
                exit_code = built.returncode
        if exit_code is None:
            up = compose(tree, env, ["up", "-d", "--no-build", "--wait", "--wait-timeout", "600"], log,
                         timeout=720, project=project)
            exit_code = up.returncode
    except subprocess.TimeoutExpired:
        exit_code = None  # timed out: result is unknown, not failed-with-a-made-up-code
    receipt["exitCode"] = exit_code
    receipt["imageDigests"] = {
        "backend": image_digest(f"{IMAGE_PREFIX[environment]}-backend:{sha}"),
        "frontend": image_digest(f"{IMAGE_PREFIX[environment]}-frontend:{sha}"),
    }
    base_url = f"http://127.0.0.1:{receipt['port']}"
    observed, checks = run_checks(base_url, sha, receipt["deploymentId"], criteria, environment)
    receipt["observed"] = observed
    receipt["observedAt"] = now_utc()
    if environment == "review":
        # The review URL is the loopback one; no hostname is configured, so none is reported.
        docs = [c for c in checks if c["name"] in ("smoke:swagger-ui", "smoke:api-docs")]
        receipt["apiDocsUrl"] = f"{base_url}/swagger-ui.html" if all(c["outcome"] == "passed" for c in docs) else None
        receipt["testUrl"] = base_url
    else:
        checks.append(planotell_check(sha, receipt["deploymentId"]))
        receipt["testUrl"] = PLANOTELL_URL if checks[-1]["outcome"] == "passed" else base_url
    receipt["checks"] = receipt["preDeployChecks"] + checks
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
        current = dev_receipts(state)
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
    latest = dev_receipts(state)[-1]
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
        observed, checks = run_checks(base_url, receipt["revision"], receipt["deploymentId"], [], receipt["environment"])
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


def producer_result_document(state: Path, deployment_id: str) -> dict:
    """The ``delivery.producer-result`` both transports emit for one deployment."""
    return to_producer_result(load_receipt(state, deployment_id))


def tagged_receipt_document(state: Path, deployment_id: str) -> dict:
    """The ``delivery.deployment-receipt`` both transports emit for one deployment.

    Refuses rather than emitting a receipt that does not satisfy the tag it claims: a
    consumer that binds to the shape has no way to tell a near-miss from a real one.
    """
    tagged = to_tagged_receipt(load_receipt(state, deployment_id))
    problems = validate_tagged_receipt(tagged)
    if problems:
        raise DeliveryError(f"refused: {deployment_id} does not satisfy "
                            f"delivery.deployment-receipt ({CONTRACTS_TAG}): {'; '.join(problems)}")
    return normalize_tagged_receipt(tagged)


def cmd_lookup(args) -> int:
    state = state_dir(repo_root())
    print(json.dumps(producer_result_document(state, args.deployment_id), indent=2))
    return 0


def cmd_receipt(args) -> int:
    """Print the tagged delivery.deployment-receipt (the lookup transport for the full record)."""
    state = state_dir(repo_root())
    print(json.dumps(tagged_receipt_document(state, args.deployment_id), indent=2))
    return 0


# ── HTTP lookup transport (contracts v0.37.0, app-deploy-lookup.openapi.yaml) ──
# Additive to the CLI (D031): the same two documents, wrapped as {"data": ...}, with
# the published miss/failure table. Host, port and credential are plantpal's own
# (docs/dev-delivery.md §3 "HTTP lookup route"); loopback-only per D040.

LOOKUP_PORT = 8185
LOOKUP_TOKEN_ENV = "DEV_DELIVERY_LOOKUP_TOKEN"
LOOKUP_ROUTE_RE = re.compile(r"^/delivery/v1/app-deploys/([^/]*)(/receipt)?$")
DEPLOYMENT_ID_RE = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


def lookup_token(state: Path) -> str:
    """The deployCaller bearer credential: env var if set, else ``.dev-delivery/lookup-token``.

    Generated on first use and kept in the (gitignored) state dir, so the caller reads
    it from the deploying host; it grants nothing but these two read-only GETs.
    """
    token = os.environ.get(LOOKUP_TOKEN_ENV, "").strip()
    if token:
        return token
    path = state / "lookup-token"
    if not path.exists():
        path.write_text(secrets.token_urlsafe(32) + "\n", encoding="utf-8")
    return path.read_text(encoding="utf-8").strip()


def delivery_error(status: int, code: str, message: str, retryable: bool) -> tuple[int, dict]:
    return status, {"error": {"code": code, "message": message, "retryable": retryable}}


def lookup_response(state: Path, token: str, path: str, authorization: str | None) -> tuple[int, dict]:
    """Answer one GET. Pure: (status, body) — the handler only writes it out.

    Order matters: an unauthenticated call is a 403 before anything else, so a caller
    without the credential can never learn whether an id exists (never a 404).
    """
    scheme, _, presented = (authorization or "").partition(" ")
    presented = presented.strip() if scheme == "Bearer" else ""
    if not presented or not secrets.compare_digest(presented, token):
        return delivery_error(403, "caller_not_authorized", "missing or unenrolled deployCaller credential", False)
    match = LOOKUP_ROUTE_RE.match(path.split("?", 1)[0])
    if not match:
        return delivery_error(422, "invalid_request", f"no such route: {path}", False)
    deployment_id, is_receipt = match.group(1), bool(match.group(2))
    if not DEPLOYMENT_ID_RE.match(deployment_id):
        return delivery_error(422, "invalid_request", f"not a deployment id: {deployment_id!r}", False)
    if not (state / "receipts").is_dir():
        return delivery_error(503, "producer_unavailable", "receipt store is not readable", True)
    try:
        document = (tagged_receipt_document if is_receipt else producer_result_document)(state, deployment_id)
    except DeliveryError as e:
        if e.exit_code == 4:
            return delivery_error(404, "deployment_not_found", f"deployment_not_found: {deployment_id}", False)
        return delivery_error(503, "producer_unavailable", str(e), True)
    except (OSError, ValueError, KeyError) as e:
        return delivery_error(503, "producer_unavailable", f"receipt store unreadable: {e}", True)
    return 200, {"data": document}


def lookup_server(state: Path, token: str, host: str, port: int):
    class Handler(BaseHTTPRequestHandler):
        server_version = "plantpal-dev-delivery-lookup"

        def do_GET(self):
            status, body = lookup_response(state, token, self.path, self.headers.get("Authorization"))
            self._send(status, body)

        def _refuse(self):
            self._send(*delivery_error(422, "invalid_request", "read-only: GET only", False))

        do_POST = do_PUT = do_PATCH = do_DELETE = _refuse

        def _send(self, status, body):
            raw = json.dumps(body, indent=2).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def log_message(self, fmt, *args):  # no bearer tokens or bodies in logs
            try:
                sys.stderr.write(f"lookup {self.command} {self.path.split('?', 1)[0]} -> {args[1] if len(args) > 1 else '?'}\n")
            except OSError:  # broken/closed stderr must never fail the response
                pass

    return ThreadingHTTPServer((host, port), Handler)


def cmd_serve(args) -> int:
    if args.host not in ("127.0.0.1", "::1", "localhost"):
        raise DeliveryError("refused: the lookup route is loopback-only (D040)")
    state = state_dir(repo_root())
    server = lookup_server(state, lookup_token(state), args.host, args.port)
    print(f"dev-delivery lookup: http://{args.host}:{args.port}/delivery/v1/app-deploys/<id>[/receipt] "
          f"(contracts v0.37.0; bearer from ${LOOKUP_TOKEN_ENV} or .dev-delivery/lookup-token)",
          file=sys.stderr, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
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


# ── review environment (contracts v0.38.0 §Review environments) ──────────────
# Runs an UNMERGED PR head in its own compose project, volumes, config and port. It never
# touches the dev stack, never merges or pushes anything, and never reaches production.
# One review at a time (owner ruling): starting another tears the previous one down.

REVIEW_CHECKS = PUSH_CHECKS + (PR_CHECK,)   # all four must be green on the PR head itself


def review_state_path(state: Path) -> Path:
    return state / "review-current.json"


def load_review_state(state: Path) -> dict | None:
    path = review_state_path(state)
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def save_review_state(state: Path, current: dict) -> None:
    current["updatedAt"] = now_utc()
    path = review_state_path(state)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(current, indent=2) + "\n", encoding="utf-8")
    os.replace(tmp, path)


def resolve_review_target(root: Path, pr: int | None, branch: str | None, expected: str | None) -> dict:
    """The PR (or branch) head to review: {sha, branch, pullRequest}. Refuses anything but an open same-repo PR into dev."""
    if (pr is None) == (branch is None):
        raise DeliveryError("give exactly one of --pr or --branch")
    pull_request = None
    if pr is not None:
        proc = run(["gh", "api", f"repos/{GITHUB_REPO}/pulls/{int(pr)}", "--jq",
                    "{sha: .head.sha, ref: .head.ref, base: .base.ref, state: .state, "
                    "repo: .head.repo.full_name, url: .html_url}"], timeout=60)
        if proc.returncode != 0:
            raise DeliveryError(f"could not read PR #{pr}: {proc.stderr.strip()}")
        info = json.loads(proc.stdout)
        if info["state"] != "open":
            raise DeliveryError(f"refused: PR #{pr} is {info['state']}; only an unmerged open PR is reviewed here")
        if info["base"] != BRANCH:
            raise DeliveryError(f"refused: PR #{pr} targets {info['base']}, not {BRANCH}")
        if info["repo"] != GITHUB_REPO:
            raise DeliveryError(f"refused: PR #{pr} comes from a fork ({info['repo']}); its code is not built here")
        branch, sha = info["ref"], info["sha"]
        pull_request = {"number": int(pr), "url": info["url"]}
    else:
        if not BRANCH_RE.match(branch) or ".." in branch:
            raise DeliveryError(f"not a branch name: {branch!r}")
        proc = run(["gh", "api", f"repos/{GITHUB_REPO}/branches/{branch}", "--jq", ".commit.sha"], timeout=60)
        if proc.returncode != 0:
            raise DeliveryError(f"could not read branch {branch}: {proc.stderr.strip()}")
        sha = proc.stdout.strip()
    if branch in NEVER_REVIEWED:
        raise DeliveryError(f"refused: {branch} is not reviewed here ({'production' if branch == 'main' else 'use deploy'})")
    if not SHA_RE.match(sha):
        raise DeliveryError(f"could not resolve a full revision: {sha!r}")
    if expected and expected != sha:
        raise DeliveryError(f"refused: expected revision {expected} but the head is now {sha}")
    return {"sha": sha, "branch": branch, "pullRequest": pull_request}


def review_gate(sha: str) -> list[dict]:
    """The PR head's own required checks. A pending, skipped or missing check is not a pass."""
    return [{"name": (f"quality:{n}" if n == PR_CHECK else f"ci:{n}"), "criterionId": None,
             "outcome": github_check(sha, n), "exitCode": None} for n in REVIEW_CHECKS]


def fetch_revision(root: Path, sha: str) -> None:
    run(["git", "fetch", "--quiet", "origin", sha], check=True, cwd=root, timeout=300)
    run(["git", "cat-file", "-e", f"{sha}^{{commit}}"], check=True, cwd=root)


def overlay_delivery_files(root: Path, tree: Path) -> None:
    """Run the compose/nginx files of THIS tool's checkout against the PR tree.

    The review must not depend on whether the PR was branched before these files learned
    about review environments and Swagger; only the delivery wiring is replaced, never app code.
    """
    source = Path(__file__).resolve().parents[2] / "deploy" / "dev-delivery"
    target = tree / "deploy" / "dev-delivery"
    target.mkdir(parents=True, exist_ok=True)
    for f in source.iterdir():
        if f.is_file():
            shutil.copyfile(f, target / f.name)


def review_teardown(state: Path, current: dict | None) -> None:
    """Stop the review project and remove its volumes (a review's data is disposable)."""
    proc = run(["docker", "compose", "-p", REVIEW_COMPOSE_PROJECT, "down", "-v", "--remove-orphans"], timeout=300)
    if proc.returncode != 0:
        raise DeliveryError(f"could not stop {REVIEW_COMPOSE_PROJECT}: {proc.stderr.strip()}", 5)
    if current is not None and current["status"] != "stopped":
        current["status"] = "stopped"
        save_review_state(state, current)


def review_status_value(current: dict, receipt: dict | None) -> str:
    if current["status"] == "stopped":
        return "stopped"
    if receipt is None or receipt["result"] == "pending":
        return "starting"
    return "ready" if receipt["result"] == "passed" else "failed"


def review_environment_document(state: Path, current: dict) -> dict:
    """The launcher ``ReviewEnvironment`` (review-environment.openapi.yaml): what ``status`` and ``stop`` print."""
    receipt_path_ = receipt_path(state, current["deploymentId"])
    native = json.loads(receipt_path_.read_text(encoding="utf-8")) if receipt_path_.exists() else None
    return {
        "idempotencyKey": current["idempotencyKey"], "repository": REPOSITORY, "branch": current["branch"],
        "pullRequest": current["pullRequest"], "expectedRevision": current["revision"],
        "status": review_status_value(current, native),
        "receipt": tagged_receipt_document(state, current["deploymentId"]) if native else None,
        "error": None, "startedAt": current["startedAt"], "updatedAt": current["updatedAt"],
    }


def review_current_for(state: Path, key: str | None) -> dict:
    current = load_review_state(state)
    if current is None or (key is not None and current["idempotencyKey"] != key):
        raise DeliveryError(f"review_environment_not_found: {key or '(none)'}", exit_code=4, bare=True)
    return current


def cmd_review_start(args) -> int:
    root = repo_root()
    state = state_dir(root)
    if args.idempotency_key and not REVIEW_KEY_RE.match(args.idempotency_key):
        raise DeliveryError(f"not an idempotency key: {args.idempotency_key!r}")
    if args.port in (DEFAULT_PORT, LOOKUP_PORT):
        raise DeliveryError(f"refused: port {args.port} belongs to the dev stack / lookup route")
    target = resolve_review_target(root, args.pr, args.branch, args.revision)
    sha = target["sha"]
    key = args.idempotency_key or f"review-{sha[:12]}"
    current = load_review_state(state)
    if current and current["idempotencyKey"] == key and (
            current["revision"] != sha or current["branch"] != target["branch"]
            or current["pullRequest"] != target["pullRequest"]):
        raise DeliveryError(f"idempotency_key_conflict: {key} already names {current['deploymentId']} "
                            f"at {current['revision']}", 3)
    if current and current["status"] != "stopped" and current["revision"] == sha:
        observed = observe_identity(f"http://127.0.0.1:{current['port']}")
        if observed and observed["revision"] == sha and observed["deploymentId"] == current["deploymentId"]:
            print(json.dumps(tagged_receipt_document(state, current["deploymentId"]), indent=2))
            return 0 if load_receipt(state, current["deploymentId"])["result"] == "passed" else 1
    pre_checks = review_gate(sha)
    blocking = [c for c in pre_checks if c["outcome"] != "passed"]
    if blocking:
        names = ", ".join(f"{c['name']}={c['outcome']}" for c in blocking)
        raise DeliveryError(f"refused: required checks for {sha} have not all passed ({names})")
    env_file, db_password = ensure_dev_config(state, "review")
    fetch_revision(root, sha)
    tree = archive_tree(root, state, sha)
    overlay_delivery_files(root, tree)
    review_teardown(state, current)  # one at a time: the previous review (any revision) goes first
    deployment_id = new_deployment_id(sha, "pla-rev")
    receipt = base_receipt(sha, deployment_id, args.port, {"deliveryId": args.delivery_id, "operationKey": key},
                           "deploy", pre_checks, None)
    receipt.update({"branch": target["branch"], "environment": "review", "composeProject": REVIEW_COMPOSE_PROJECT,
                    "revisionRole": "task", "apiDocsUrl": None,
                    "review": {"pullRequest": target["pullRequest"]["number"] if target["pullRequest"] else None,
                               "pullRequestUrl": target["pullRequest"]["url"] if target["pullRequest"] else None}})
    save_receipt(state, receipt)
    started = now_utc()
    current = {"idempotencyKey": key, "deploymentId": deployment_id, "revision": sha, "branch": target["branch"],
               "pullRequest": target["pullRequest"], "port": args.port, "status": "active", "startedAt": started}
    save_review_state(state, current)
    env = compose_env(tree, sha, deployment_id, env_file, db_password, args.port, "review")
    receipt = settle(root, state, receipt, tree, env, build=True, criteria=[])
    print(json.dumps(tagged_receipt_document(state, deployment_id), indent=2))
    return 0 if receipt["result"] == "passed" else 1


def cmd_review_status(args) -> int:
    state = state_dir(repo_root())
    print(json.dumps(review_environment_document(state, review_current_for(state, args.idempotency_key)), indent=2))
    return 0


def cmd_review_stop(args) -> int:
    state = state_dir(repo_root())
    current = review_current_for(state, args.idempotency_key)
    review_teardown(state, current)
    print(json.dumps(review_environment_document(state, current), indent=2))
    return 0


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
    review = sub.add_parser("review", help="run an unmerged PR head in an isolated review environment")
    review_sub = review.add_subparsers(dest="review_command", required=True)
    p = review_sub.add_parser("start")
    p.add_argument("--pr", type=int)
    p.add_argument("--branch")
    p.add_argument("--revision", help="expected PR head; refused if the head has moved")
    p.add_argument("--idempotency-key")
    p.add_argument("--delivery-id")
    p.add_argument("--port", type=int, default=REVIEW_PORT)
    p.set_defaults(func=cmd_review_start)
    for name, func in (("status", cmd_review_status), ("stop", cmd_review_stop)):
        p = review_sub.add_parser(name)
        p.add_argument("--idempotency-key")
        p.set_defaults(func=func)
    p = sub.add_parser("serve")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=LOOKUP_PORT)
    p.set_defaults(func=cmd_serve)
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except DeliveryError as e:
        print(str(e) if e.bare else f"dev-delivery: {e}", file=sys.stderr)
        return e.exit_code


if __name__ == "__main__":
    sys.exit(main())
