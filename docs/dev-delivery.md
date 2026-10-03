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

**CLI transport.** Both commands are the CLI transport named in
`contracts` §App-deploy: run from the repository root in the plantpal checkout
**on the host that deployed**, with the tool's own venv. On success: exit `0`
and exactly one JSON document on stdout. On a miss: exit `4`, stderr exactly
`deployment_not_found: <id>`, nothing on stdout — Factory reads that as
`unavailable`, never `failed`, and never as grounds to redeploy under the same
operation key. Any other non-zero exit, or stdout that does not parse, is
`unavailable` too. The CLI is **not withdrawn** by the HTTP route below (D031).

### HTTP lookup route (contracts v0.37.0)

`contracts` **v0.37.0** published `schemas/delivery-api/app-deploy-lookup.openapi.yaml`
(off `plantpal-20260928-contracts-app-deploy-lookup-route`), and
`dev_delivery.py serve` implements it. This is the transport Factory can reach:
Factory cannot run the CLI (no execution host, read-only HTTP surface —
`spec-factory.md` §3/§4).

| Route | 200 `data` |
|---|---|
| `GET /delivery/v1/app-deploys/{deploymentId}` | `delivery.producer-result` — identical to `lookup <id>` |
| `GET /delivery/v1/app-deploys/{deploymentId}/receipt` | `delivery.deployment-receipt` (v0.36.0 tagged shape), identical to `receipt <id>`, incl. `rollback` |

Success is `{"data": ...}`; every failure is `{"error": delivery.error}`:

| `error.code` | Status | `retryable` | When |
|---|---|---|---|
| `deployment_not_found` | 404 | `false` | No receipt for that id in this host's store. The **only** 404. A miss → `unavailable`, never `failed` |
| `invalid_request` | 422 | `false` | Malformed id (outside `[A-Za-z0-9._:-]{1,128}`), unknown path, or a non-GET |
| `caller_not_authorized` | 403 | `false` | Missing or wrong bearer — checked **first**, so an unauthenticated caller never learns whether an id exists |
| `producer_unavailable` | 503 | `true` | Store unreadable, a corrupt receipt, or a receipt that fails tagged validation (never served as a near-miss) |

A `pending` receipt is a normal 200.

**Producer-owned parts (§Published-interface ruling) — plantpal's choices:**

| | |
|---|---|
| Host | `127.0.0.1` only (D040). `serve` refuses any non-loopback `--host` |
| Port | **8185** (default `--port`; next free 81xx after 8184, probed free 2026-09-28). Registration in PLATFORM_STATE §3 is requested from `platform-vault` by demand |
| Credential (`deployCaller`) | A bearer token: `$DEV_DELIVERY_LOOKUP_TOKEN` if set, else `.dev-delivery/lookup-token` (32 random bytes, generated on first `serve`, gitignored). Read-only; grants these two GETs and nothing else. The caller obtains it from the deploying host |
| Process | Host process, not a container — the receipt store is `.dev-delivery/receipts/` in the checkout. Run it in the background: `python tools/dev-delivery/dev_delivery.py serve > .dev-delivery/logs/lookup.log 2>&1 &`. Access log carries method, path and status only — never the token |

Reaching it from a container (Factory's deployed form) needs the host's loopback
exposed to that network; that routing is `runtime`'s, not this repo's.

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
| 5 | Factory re-fetching the receipt (execution host, repo lock, or a reachable transport) | `contracts` → plantpal → `factory` | **plantpal leg done 2026-09-28.** contracts v0.37.0 published the route; `dev_delivery.py serve` implements it on `127.0.0.1:8185` (§3 "HTTP lookup route"). Open: `platform-vault` records port 8185; `factory` binds to the route; `runtime` routes to it if Factory runs in a container |

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

## 6. Review environment (an unmerged PR, contracts v0.38.0)

`dev_delivery.py review` runs a PR's **unmerged head** so the owner can look at it
before merge. Nothing in it merges, pushes, opens a PR or touches `main`, the dev
stack or production. Reference: `contracts` `docs/task-delivery.md` §Review
environments; the launcher API it backs is `schemas/launcher/review-environment.openapi.yaml`.

```bash
dev_delivery.py review start (--pr <n> | --branch <name>) [--revision <sha>]     [--idempotency-key <key>] [--delivery-id <uuid>] [--port 8186]
dev_delivery.py review status [--idempotency-key <key>]
dev_delivery.py review stop   [--idempotency-key <key>]
```

**`start`** resolves the head and refuses unless: the PR is open, same-repo and
targets `dev` (a branch is never `main` or `dev`); `--revision`, when given, still
equals the head (a moved head is refused, not followed); and **`Backend CI`,
`Frontend CI`, `Detect secrets` and `sonar-gate` are all `success` on that head**
(pending, skipped or missing is not a pass; a branch with no PR has no `sonar-gate`
and is refused). It then builds a clean `git archive` of the head, overlays this
checkout's `deploy/dev-delivery/` files so a PR branched before this change still
gets the review wiring (app code is never replaced), and brings up compose project
**`plantpal-review`**.

| | Dev (`deploy`) | Review (`review start`) |
|---|---|---|
| Compose project / volumes | `plantpal-devdelivery` | `plantpal-review` (own network and volumes; `stop` removes them) |
| Config and secrets | `.dev-delivery/dev.env`, `db-password` | `.dev-delivery/review.env`, `review-db-password`, all freshly generated, none shared |
| Images | `plantpal-devdelivery-*:<sha>` | `plantpal-review-*:<sha>` |
| Published port | `127.0.0.1:8184` | `127.0.0.1:8186` (`--port`; the dev and lookup ports are refused). Recorded as the next free 81xx; registration is requested from `platform-vault` |
| App reports | `environment: dev` | `environment: review` |
| Rollback, `rollback` receipt | yes | no: `kind: deploy`, `rollback: null`. A review is stopped, not rolled back |

Review receipts are stored beside the dev ones (`plantpal.dev-deployment-receipt/1`,
`environment: review`) but are **never** a dev rollback target or a dev
`--operation-key`.

**Receipt.** `start` prints the tagged `delivery.deployment-receipt` (v0.38.0),
validated before it is emitted: `environment.name: review`, `revisionRole: task`,
`mergedRevision` = the PR head, `branch` = the PR head branch, a `review` block
(`pullRequest` and `pullRequestUrl`, `null` for a branch-only review, never
defaulted), `environment.url` = the loopback frontend URL, `environment.apiDocsUrl`
= `/swagger-ui.html` only when the Swagger checks passed, and `observed` = the
running app's own `/actuator/info` identity at the PR revision. Checks are the dev
ones with `identity:environment` expecting `review`, plus `smoke:swagger-ui` and
`smoke:api-docs`. A review URL serving another revision is `failed` with `observed`
kept as reported; an unreported identity is `unknown`; neither is ever `passed`.
Exit `0` only when `passed`.

**One at a time, idempotent.** The latest review is recorded in
`.dev-delivery/review-current.json`.
- Starting a review whose head is **already running** (the live `/actuator/info`
  reports that revision and deployment) returns the existing receipt without
  rebuilding or running the gate again.
- Starting any other review **stops the previous one first** (`docker compose -p
  plantpal-review down -v`), but only after the new head has passed the gate, so a
  refused start leaves the running review alone.
- The same `--idempotency-key` with a different revision, branch or PR is refused:
  `idempotency_key_conflict`, exit `3`. Without a key it defaults to
  `review-<sha12>`.

**`status` and `stop`** print the launcher's `ReviewEnvironment` document
(`idempotencyKey`, `expectedRevision`, `status`, tagged `receipt`, `error`,
timestamps). `status` is `starting` (receipt `pending`), `ready` (`passed`),
`failed` (anything else, including a revision mismatch, never `ready`) or
`stopped`; it reports the last recorded observation, not a fresh probe. `stop` is
idempotent, removes the review's containers and volumes, and keeps the receipt
unchanged. A key that names no review is exit `4` with stderr
`review_environment_not_found: <key>` and nothing on stdout (a miss, never
`failed`). These are the commands `launcher` calls.

### Ready to test (seed, Claude, ai-gateway, identification)

A passed review receipt means the environment is usable, not only up. `review start`
seeds and then observes, through the app's own API, so each is a recorded check:

| Check | Meaning |
|---|---|
| `seed:test-account` | `review@plantpal.test` / `review-password-1` registers (or logs in if it already exists) |
| `seed:baseline-plant` | that account owns one plant, "Review Monty" (never duplicated) |
| `smoke:identification-endpoint` | `GET /api/v1/identifications` as the test account answers 200 |
| `smoke:claude-enabled` | the account prefers Claude (the default) and Claude is selectable for vision and reasoning |
| `smoke:ai-gateway` | `ai-gateway` answers on the host (default `127.0.0.1:8085`) |

The review backend runs with Spring profiles `dev,platform`, so every AI call (including
Claude identification) goes through `ai-gateway` at `REVIEW_AI_GATEWAY_URL` (default
`http://host.docker.internal:8085`, set it in the environment `review start` runs in); no
provider key is generated or needed in the review env. With the gateway on, Claude counts
as available without an Anthropic key (`AnthropicClient.isAvailable()`). state-feed
emission is off in review. The dev stack is unchanged (profile `dev`, direct clients).
If `ai-gateway` is not running, `smoke:ai-gateway` is `unavailable` and the receipt result is
`unknown`, not `passed`: the environment is up but AI is not testable. Whether `ai-gateway`
itself has Claude enabled is its own configuration and is not observed here.

## 7. Swagger (dev and review only)

| URL | Served by |
|---|---|
| `/swagger-ui.html`, `/swagger-ui/**` | the backend's springdoc UI |
| `/v3/api-docs` | the OpenAPI document |

The dev-delivery nginx (`deploy/dev-delivery/nginx.dev-delivery.conf`, used by both
the dev candidate and every review environment) and the local stack's
`frontend/nginx.conf` proxy all three to the backend, so they no longer fall through
to the Angular index. They are at `http://127.0.0.1:8184/swagger-ui.html` (dev) and
`http://127.0.0.1:8186/swagger-ui.html` (review; also the receipt's `apiDocsUrl`).

**Production does not expose them.** `application-prod.yml` sets
`springdoc.swagger-ui.enabled` and `springdoc.api-docs.enabled` to `false` (the
backend's security config still permits the paths, so they answer 404 rather than
serving a UI; `ApiDocsExposureTest` pins this). Production fronts the backend with
Caddy, which forwards every path, so the backend switch is the control. The
`staging` profile is unchanged.
