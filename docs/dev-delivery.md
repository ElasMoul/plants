# Planotell dev delivery (D113)

How a task reaches a **dev** candidate of plantpal, and how Factory observes it.
Planotell is the product name. `plantpal` is the repository and YouTrack project
`PLA` is the tracker. Governing documents: D112, D113,
`factory/docs/YOUTRACK_DELIVERY.md`, and contracts `v0.31.0`
`docs/task-delivery.md` (§Producers, row `app-deploy`).

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
tools/dev-delivery/.venv/Scripts/pip install -r tools/dev-delivery/requirements.txt   # contracts v0.31.0 binding
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

The native receipt is stored at `.dev-delivery/receipts/<deploymentId>.json`
with schema `plantpal.dev-deployment-receipt/1`. It is plantpal-native until
contracts tags a shape (demand
`plantpal-20260927-contracts-app-deploy-receipt-and-identity`). It contains
`deploymentId`, `revision` (merged), `revisionRole: merged`, `imageDigests`
(`backend`, `frontend`; local docker image ids, no registry), `result`,
`exitCode`, `observed`, `checks`, `correlation`, `rollback`, and the log path.

| Command | Output |
|---|---|
| `dev_delivery.py lookup <id>` | contracts v0.31.0 `DeliveryProducerResult` (validated by the tagged Python binding): `producer: app-deploy`, `operationId` = deployment id, `nativeRef: plantpal:deployments/<id>`, `artifactRef` = backend image digest, `environment` built **only from observed values** (`null` if the app did not report a full revision) |
| `dev_delivery.py receipt <id>` | the native receipt |
| `dev_delivery.py list` | every deployment: id, kind, revision, result |
| `dev_delivery.py observe` | what the loopback URL and the Planotell URL report right now |
| `dev_delivery.py reconcile <id>` | settles a receipt left `pending` by a crash, by re-observing only (never redeploys); the result is `unknown` or `failed`, never `passed` |

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
| 2 | Tagged receipt / identity / lookup shapes (incl. rollback identity) | `contracts` | demand `plantpal-20260927-contracts-app-deploy-receipt-and-identity` |
| 3 | Record port 8184 and rule how plantpal publishes coordination commits (a push to `main` deploys) | `platform-vault` (owner) | port 8184 **recorded** in the D040 register (vault commit `6e06b0b`, 2026-09-27) and plantpal's default stands; the coordination-publication ruling came back **blocked at the owner** (no existing rule covers it), so the practice in §1 stands until ruled |
| 4 | Managed hosting of the candidate, and `planotell.platform.localhost` → `127.0.0.1:8184` (launcher name proxy on port 80; today launcher maps `planotell` to `:8444`, the long-lived local stack) | `runtime`, which raises its own `launcher`/`gateway` demands | Factory demand `factory-20260927-dev-delivery-routing` (after this one) |
| 5 | Factory calling `dev_delivery.py` (execution host, repo lock) | `factory` / `agent-runner` | not built |

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
