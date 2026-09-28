# Planotell dev delivery (D113)

How a task reaches a **dev** candidate of plantpal, and how Factory observes it.
Planotell is the product name. `plantpal` is the repository and YouTrack project
`PLA` is the tracker. Governing documents: D112, D113,
`factory/docs/YOUTRACK_DELIVERY.md`, and contracts `v0.36.0`
`docs/task-delivery.md` (§Producers, row `app-deploy`; §App-deploy).

**Production is out of scope.** Any push to `main` deploys production
(`.github/workflows/deploy.yml`). Nothing in this flow pushes, merges or opens
a PR to `main`. The owner's own dev→main release is the only route to
production, and D113 does not authorize it.

## 1. Task branch → `dev`

1. Branch from `origin/dev`, not from local `dev`, which can hold unpushed
   coordination commits: `git switch -c feature/PLA-<n>-<slug> origin/dev`.
   Use `feature/PP-<n>-…` for non-YouTrack work.
2. Push the branch and open a PR **into `dev`**. Never target `main`.
3. The PR must pass the checks the `dev` ruleset requires. The last one is
   D112's merge gate:

   | Check | Workflow | Kind |
   |---|---|---|
   | `Backend CI` | `ci.yml` (spotless, checkstyle, unit + Testcontainers ITs, JaCoCo gate) | CI |
   | `Frontend CI` | `ci.yml` (lint, Jest, production build) | CI |
   | `sonar-gate` | `ci.yml`, self-hosted `ci-runner`, same-repo PRs into `dev` only | quality (D112) |
   | `Detect secrets` | `secret-scan.yml` (gitleaks, every push) | security. **Runs, but is not in the ruleset yet; see §5** |

4. If `sonar-gate` fails, the PR author fixes it on the source branch and
   pushes, and the check re-runs. There is no bypass (D112 clause 3).
5. Merge through GitHub only. The ruleset refuses a merge whose required checks
   are not green.

## 2. Deploy the merged revision to dev

```bash
python -m venv tools/dev-delivery/.venv
tools/dev-delivery/.venv/Scripts/pip install -r tools/dev-delivery/requirements.txt   # contracts v0.36.0 binding
tools/dev-delivery/.venv/Scripts/python tools/dev-delivery/dev_delivery.py deploy \
    [--revision <sha>] [--delivery-id <uuid>] [--operation-key <key>] \
    [--criterion AC-1=GET:/api/v1/plants:401] ...
```

`deploy` does the following, in order, and refuses at any failed step:

1. `git fetch origin dev`, then resolves the target. The default is the head of
   `origin/dev`. The target must be on **`origin/dev`'s first-parent line**, meaning a
   revision `dev` itself pointed at, such as a PR merge result. A task-branch
   revision is refused, even after it has been merged in.
2. **Pre-deploy gate:** `Backend CI`, `Frontend CI` and `Detect secrets` must
   show `success` on the merged SHA's push run. `sonar-gate` must show
   `success` on the head of the PR whose merge commit is that SHA. A pending,
   skipped or missing check counts as not passed.
3. Builds from a clean `git archive` of that SHA into `.dev-delivery/build/<sha>/`,
   so the working tree and uncommitted files never ship. The backend image is
   stamped with `APP_REVISION=<sha>` (`backend/Dockerfile`).
4. Brings up compose project `plantpal-devdelivery`
   (`deploy/dev-delivery/docker-compose.yml`, taken from the deployed revision
   itself) with `docker compose up --wait`.
5. Observes the app and runs smoke checks through the **published URL**, then
   writes the receipt.

`--operation-key` makes a deploy idempotent. Sending the same key with the same
revision returns the stored result without redeploying. Sending the same key with
a different revision is refused with `operation_key_conflict` (exit 3).

### Isolation (no production data or secrets)

- Own compose project, so its network and volumes (`postgres_data`,
  `redis_data`, `photos_data`) are separate from the long-lived local stack.
- Postgres and Redis publish **no** host ports. Kafka is not used
  (`APP_IDENTIFICATION_TRANSPORT=in-process`, as in production).
- One published port: `127.0.0.1:8184` → nginx, plain http. Allocation is
  requested in `plantpal-20260927-platform-vault-planotell-dev-port-and-coordination-path`.
- Config is `.dev-delivery/dev.env`, generated on first run: a fresh
  `JWT_SECRET`, a fresh VAPID pair and a fresh database password. It is never
  copied from `backend/.env` or `deploy/vps/`. A key outside the dev allowlist,
  or any production-only key (`DATABASE_URL`, `REDIS_URL`, `CLOUDINARY_URL`,
  `SENTRY_DSN`, …), is refused. AI keys default to `unset-in-dev-delivery`, so
  AI features are not testable on the candidate unless the owner adds
  **dev-scoped** keys.

## 3. Observed identity, smoke checks, receipt

The running backend reports its own identity at `GET /actuator/info` (public,
`DeploymentIdentityInfoContributor`):

```json
{"deployment": {"appIdentity": "plantpal", "revision": "<40-hex|null>",
                "deploymentId": "<id|null>", "environment": "dev|null"}}
```

`null` means not reported. It is never filled with a default. The dev nginx
proxies `/actuator/{health,info}`, so the check is made through the URL under
test.

This block **is** the tagged `app/deployment-identity` shape (contracts
v0.36.0) — it is served as-is, and the same document is embedded in the receipt
as `observed`. `runtime` and Factory read it to prove which revision a URL
serves; a `null` field never matches an expected value. It is a different shape
from `app/identity.json` (the tenant attribution sent with AI calls) and from
`app.health`, which carries no revision.

Checks recorded on every deployment:

| Check | Passes when | Otherwise |
|---|---|---|
| `ci:*`, `quality:sonar-gate` | GitHub reports `success` | refused before deploy |
| `identity:app` / `identity:revision` / `identity:deployment` / `identity:environment` | the running app reports `plantpal`, the merged SHA, this deployment id, `dev` | `failed` on mismatch, `unknown` if not reported |
| `smoke:frontend-index` | `GET /` → 200 with `<app-root` | `failed` / `unavailable` |
| `smoke:backend-health` | `GET /actuator/health` → 200 `UP` | same |
| `smoke:api-auth-guard` | `GET /api/v1/plants` → 401 (proxied to the backend, security on) | same |
| `criterion:<id>` | each `--criterion` from the approved plan | same |
| `route:planotell` | `http://planotell.platform.localhost/actuator/info` reports **this** deployment and revision | `unavailable` / `failed`; recorded, not gating |

The overall `result` is `passed` only if the stack came up (exit 0) and every
gating check passed. Any `unknown` makes the result `unknown`, never `passed`.
A timed-out deploy has `exitCode: null`.

`testUrl` is `http://planotell.platform.localhost` **only when `route:planotell`
passed**. Otherwise it is the loopback URL. A configured hostname is never
reported as verified.

### Receipt and lookup

The **native** receipt is the on-disk record, at
`.dev-delivery/receipts/<deploymentId>.json` with schema
`plantpal.dev-deployment-receipt/1`. It contains `deploymentId`, `revision`
(merged), `revisionRole: merged`, `imageDigests` (`backend`, `frontend`; local
docker image ids, no registry), `result`, `exitCode`, `observed`, `checks`,
`correlation`, `rollback`, and the log path.

Both transports emit the **tagged** contracts shapes, pinned at **v0.36.0**
(`tools/dev-delivery/requirements.txt`). The native file is the record; the
tagged document is what consumers bind to. Native-only fields (`schema`,
`revisionRole`, `composeProject`, `port`, `preDeployChecks`, `log`, and the
per-check `detail`/`prHead`) stay in the file and are dropped from the tagged
document, whose shape is closed. Mapping: `contracts` `docs/task-delivery.md`
§App-deploy and §Upgrade / repin v0.36.0.

| Command | Output |
|---|---|
| `dev_delivery.py lookup <id>` | `delivery.producer-result` (v0.36.0 binding): `producer: app-deploy`, `operationId` = deployment id, `nativeRef: plantpal:deployments/<id>`, `artifactRef` = backend image digest, `environment` built **only from observed values** (`null` if the app did not report a full revision). **No rollback field** — Factory reads `rollback` from the receipt it re-fetches through `nativeRef` |
| `dev_delivery.py receipt <id>` | `delivery.deployment-receipt` (v0.36.0): the full tagged receipt, including `rollback` identity, `kind`, per-component `imageDigests` and `digestKind: local-image-id`. Validated on emit — see below — and refused rather than emitted if it does not satisfy the tag |
| `dev_delivery.py list` | every deployment: id, kind, revision, result |
| `dev_delivery.py observe` | what the loopback URL and the Planotell URL report right now |
| `dev_delivery.py reconcile <id>` | settles a receipt left `pending` by a crash, by re-observing only (never redeploys); the result is `unknown` or `failed`, never `passed` |

**Lookup transport.** Both commands are the CLI transport named in
`contracts` §App-deploy: run from the repository root in the plantpal checkout
**on the host that deployed**, with the tool's own venv. On success: exit `0`
and exactly one JSON document on stdout. On a miss: exit `4`, stderr exactly
`deployment_not_found: <id>`, nothing on stdout — Factory reads that as
`unavailable`, never `failed`, and never as grounds to redeploy under the same
operation key. Any other non-zero exit, or stdout that does not parse, is
`unavailable` too. There is no HTTP route.

**Ruling — the CLI is the only sanctioned transport (2026-09-28).** Factory
cannot use it: its outbound surface is read-only HTTP GETs to named platform
service ports (`spec-factory.md` §4), it may not write to or import from a sibling
repo (§3), and it has no execution host — even deployed it runs in a container on
the internal dashboard network, not on the deploying host where the checkout and
`.dev-delivery/receipts/` live. Demand
`factory-20260928-plantpal-reachable-receipt-transport` therefore asks plantpal for
a lookup Factory can call, or for this ruling if plantpal holds there is only one
sanctioned transport.

plantpal does not hand-roll the alternative. An HTTP route Factory binds to is a
cross-repo interface, and `contracts` v0.36.0 §App-deploy states the order
explicitly — "plantpal must offer a new transport and **contracts must publish
it**" — which is also this repo's standing rule that all cross-repo interfaces
come from `contracts`. So: the CLI above remains the **only sanctioned transport**
until `contracts` publishes a route, and plantpal has raised
`plantpal-20260928-contracts-app-deploy-lookup-route` asking it to, proposing
`GET /delivery/v1/app-deploy/deployments/{id}/receipt` (and `/producer-result`) on
this stack's existing published port, so no process runs inside the checkout to
answer a call.

Consequences while that is open, stated so the gap is visible rather than silent:

- Factory's consuming leg **stalls**. `plantpal-20260928-factory-consume-app-deploy-receipt`
  stays **blocked** on its fetch criteria, and a receipt's rollback identity is
  read by nobody.
- Factory must **not** bind to the native `plantpal.dev-deployment-receipt/1`
  document as a substitute, and must not treat a lookup it cannot perform as
  grounds to redeploy under the same operation key.
- Nothing in this section changes for the CLI's existing consumers: both commands
  keep emitting exactly what they emit today, and `contracts`' §App-deploy miss
  contract (exit `4` → `unavailable`, never `failed`) is unchanged.

**What `receipt` validates before it emits.** Three checks, because each catches
what the others miss:

1. the **generated binding** (`DeliveryDeploymentReceipt`) — what plantpal
   actually emits;
2. the **JSON Schema itself** — vendored verbatim at v0.36.0 in
   `tools/dev-delivery/schemas/` (see its README). The binding is generated from
   the schema and does not implement its `if`/`then` conditionals, so it accepts
   documents the schema rejects;
3. the **cross-field rules the schema cannot express**, ported from `contracts`
   `tests/validate_delivery.py` `check_deployment_semantics`: `nativeRef` must
   name this deployment, a rollback identity must name an earlier deployment, a
   rollback is never the deployment it restores, and a `passed` receipt must have
   `observed.revision == mergedRevision`, `observed.deploymentId ==
   deploymentId` and the observed environment. Draft 2020-12 has no keyword for
   "these two fields must be equal", and that equality is exactly what a receipt
   exists to prove — a `passed` receipt whose URL serves something else is
   schema-valid.

Override the schema directory for a local check against a `contracts` checkout:
`PLATFORM_CONTRACTS_SCHEMAS=<dir> dev_delivery.py receipt <id>`.

### Running the tool's tests

```bash
python -m venv tools/dev-delivery/.venv
tools/dev-delivery/.venv/Scripts/pip install -r tools/dev-delivery/requirements.txt
tools/dev-delivery/.venv/Scripts/python -m unittest discover -s tools/dev-delivery -p "test_*.py"
```

They are pure logic — no docker, git or network — and cover the tagged mapping,
the schema + cross-field validation, the `/actuator/info` identity round-trip
(including `null` staying unreported rather than defaulted) and both transports'
exit codes. CI runs them in the **Dev Delivery Tool CI** job on every push and
PR; `contracts` is a public repository, so the pinned install needs no token.

## 4. Rollback identity

Each receipt's `rollback` names the last **passed** deployment before it:
`deploymentId`, `revision` and `imageDigests`. It is `null` for the first
deployment. `dev_delivery.py rollback [--to <id>]` redeploys that deployment's
retained images **without rebuilding**. It first checks that each image's
digest still matches the receipt. The rollback is itself a new deployment, with
`kind: rollback`, `rollbackOf` and `restores`, and it goes through the same
observation and checks. Data is not rolled back. Liquibase migrations are
forward-only, so rolling back across a migration can fail its health check and
be recorded as `failed`.

`dev_delivery.py down` stops the stack and keeps its volumes.

## 5. Prerequisites that remain (not done by plantpal)

| # | Prerequisite | Owner | Status |
|---|---|---|---|
| 1 | Add `Detect secrets` to the `dev` ruleset's required checks | owner (repo settings) | open. The check runs on every push and the deploy gate enforces it, but a PR merge does not require it |
| 2 | Tagged receipt / identity / lookup shapes (incl. rollback identity) | `contracts` | **done** — shipped in contracts **v0.36.0**; plantpal's consuming leg repinned to it (`delivery.deployment-receipt` from `receipt <id>`, `app/deployment-identity` is the `/actuator/info` block). Consumers: **bind to v0.36.0** |
| 3 | Record port 8184 and rule how plantpal publishes coordination commits (a push to `main` deploys) | `platform-vault` (owner) | port 8184 **recorded** in the D040 register (vault commit `6e06b0b`, 2026-09-27) and plantpal's default stands; the coordination-publication ruling came back **blocked at the owner** (no existing rule covers it), so the practice in §1 stands until ruled |
| 4 | Managed hosting of the candidate, and `planotell.platform.localhost` → `127.0.0.1:8184` (launcher name proxy on port 80; today launcher maps `planotell` to `:8444`, the long-lived local stack) | `runtime`, which raises its own `launcher`/`gateway` demands | Factory demand `factory-20260927-dev-delivery-routing` (after this one) |
| 5 | Factory re-fetching the receipt (execution host, repo lock, or a reachable transport) | `contracts` → then plantpal; `factory` consumes | **re-ruled 2026-09-28.** Factory can never call the CLI (no execution host, read-only HTTP surface — see §3's ruling), so `contracts` is asked to publish an app-deploy lookup route (`plantpal-20260928-contracts-app-deploy-lookup-route`); plantpal implements it on that publication. Until then Factory's fetch leg stalls visibly and it must not bind to the native document |

### Committing a receipt trips the secret scanner

`secret-scan.yml` (gitleaks `generic-api-key`) matches the receipts'
`correlation.operationKey` field: the field name contains "Key" and the
caller-chosen run label (`plantpal-d113-verify-20260927`) clears the rule's
entropy floor. The value is an idempotency label, not a credential, and nothing
else in a receipt is secret — but committing one needs a `.gitleaksignore`
fingerprint per flagged line (`<commit>:<path>:<rule>:<line>`), as commit
`4fcc50c` does.

Fingerprints are bound to the commit that introduced them, so a squash-merge
that rewrites it needs fresh entries. Prefer reading a receipt with
`dev_delivery.py lookup <id>` over committing it; `.dev-delivery/` is gitignored
for that reason.
