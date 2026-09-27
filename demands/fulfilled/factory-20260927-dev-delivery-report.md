---
demandId: factory-20260927-dev-delivery
worker: plantpal
date: 2026-09-27
status: done
shipped:
  - "tools/dev-delivery/dev_delivery.py — dev-only delivery tool (deploy, lookup, receipt, list, observe, reconcile, rollback, down), 717 lines, with 26 unit tests in tools/dev-delivery/test_dev_delivery.py (green locally 2026-09-27)"
  - "deploy/dev-delivery/{docker-compose.yml,frontend.Dockerfile,nginx.dev-delivery.conf} — isolated compose project plantpal-devdelivery, one loopback port 127.0.0.1:8184, own volumes, no Kafka, generated dev-only secrets"
  - "docs/dev-delivery.md — task-branch-to-dev procedure, the check gate, receipt/lookup/rollback, isolation rules and the prerequisite table"
  - "DEPLOYMENT.md section 'Dev delivery — Planotell dev candidate (D113)' and a CHANGELOG.md entry under Unreleased"
  - "backend DeploymentIdentityInfoContributor + public GET /actuator/info reporting deployment.{appIdentity,revision,deploymentId,environment} (null when unstamped or malformed), backend/Dockerfile APP_REVISION stamp, unit test; merged to dev through PR #188 (35e8276) and PR #189 (e3bcd1f)"
  - "Observed dev candidate: deployment pla-dev-20260927103855-e3bcd1f889de at merged revision e3bcd1f889de3e06caa191e1e3428373c873e2f6 — result passed, exitCode 0, 12/12 gating checks passed, route:planotell recorded unavailable (does not gate)"
  - "Rollback identity exercised: pla-dev-20260927104017-35e82764a24f records rollback -> the previous passed deployment (deploymentId, revision, both image digests), and rollback deployment pla-dev-20260927104038-e3bcd1f889de restored the earlier images with no rebuild and passed"
  - "Review copies of the three native receipts and their contracts v0.31.0 producer-results committed at docs/dev-delivery-evidence/evidence/ with the handoff doc candidate-e3bcd1f.md"
  - ".gitleaksignore — four documented historical fingerprints for the review copies' caller-chosen operationKey labels, which gitleaks' generic-api-key rule flags as a false positive; verified by A/B scan of the failing commit 4fcc50c with gitleaks 8.21.2 (4 leaks before, 0 after), commit 7086e77"
  - "Demands raised before consuming, per the criteria — plantpal-20260927-contracts-app-deploy-receipt-and-identity and plantpal-20260927-platform-vault-planotell-dev-port-and-coordination-path, both in commit 7569ea6"
summaryRef: "commits 8072f48, ec689d5, 1098d5e and c21dc58 reached dev through PR #188 and PR #189 (dev head e3bcd1f); the observed run and evidence are from 2026-09-27 10:38–10:41Z"
---

# Fulfillment — Provide Planotell dev-only delivery and observed candidate identity

## Current-state check first

A prior supervised session (2026-09-27 10:35Z) built and merged this capability but ended
without a report — its brain session closed auto-drafted with `status: failed`, and no
deployment had ever been recorded (`demands/fulfilled/` had nothing for this demand, and
`.dev-delivery/receipts/` and `.dev-delivery/build/` were empty). This session verified
that state, ran the capability end-to-end against the current dev head, and wrote the
evidence. **No implementation was redone or rewritten.** This session wrote no product
code: what it adds is the observed run, the committed review copies, this report, and two
refusals recorded as evidence.

## What shipped, per acceptance criterion

### 1. Task branch → dev integration with the required checks; no path to main

`docs/dev-delivery.md` §1 documents the flow (branch from `origin/dev`, PR **into `dev`**
only, merge through the ruleset). It is enforced, not just written down:

- The `dev` ruleset (id 17579521, `enforce`, `current_user_can_bypass: never`) requires
  exactly **`Frontend CI`**, **`Backend CI`** and **`sonar-gate`** — D112's gate is a
  required check. `sonar-gate` (`.github/workflows/ci.yml`) runs only for same-repo PRs
  whose base is `dev`, refuses fork heads, and has no bypass. Both D113 PRs merged with
  it green (PR #188 head `877f8da`, PR #189 head `c21dc58`).
- `Detect secrets` (`secret-scan.yml`, gitleaks) runs on every push and the deploy gate
  requires it, but it is **not yet in the `dev` ruleset** — prerequisite 1 below.
- No path to production: the tool only ever fetches and resolves `origin/dev` and refuses
  any revision that is not on `dev`'s first-parent line. Recorded today:

  ```
  deploy --revision dd47739…  (origin/main)          -> refused, exit 2
  deploy --revision c21dc58…  (merged into dev but
                               not on dev's first-parent line) -> refused, exit 2
  ```

  No PR from this work targeted `main`; `main` is untouched at `dd47739`.

### 2. App-owned dev deployment receipt + lookup mapped to `delivery.producer-result`

- Missing interfaces were **requested from contracts first** (commit `7569ea6`):
  `plantpal-20260927-contracts-app-deploy-receipt-and-identity` asks for a tagged
  receipt shape (with rollback identity and more than one artifact), a running-app
  identity shape, the lookup transport, and a Python binding. Until it closes, the
  receipt and the `/actuator/info` block stay **plantpal-native** and are documented as
  such — consumers must not bind to them.
- The native receipt (`plantpal.dev-deployment-receipt/1`, written to
  `.dev-delivery/receipts/<deploymentId>.json`) carries deployment id, merged revision +
  `revisionRole: merged`, per-component image digests, result, exit code, the observed
  identity, every check, the correlation block, the log path and the rollback identity.
- `lookup <id>` renders it as a contracts **v0.31.0** `DeliveryProducerResult`, validated
  by the tagged Python binding: `producer: app-deploy`, `operationId` = deployment id,
  `nativeRef: plantpal:deployments/<id>`, `artifactRef` = backend image digest,
  `outcome`/`exitCode`, and an `environment` that is built **only from observed values**
  (it is `null` when the app did not report a full revision).
- Rollback identity is real, not just a field: the second deployment records the
  previous passed deployment with its revision and both digests, and `rollback`
  redeployed those retained images after re-checking their digests, then passed the same
  observation and checks (`kind: rollback`, `rollbackOf`, `restores`).

### 3. Observed running identity + criterion smoke evidence; isolated; unknown stays unknown

Observed through the published URL during the run, and independently of the tool with
`curl` at 10:39:58Z:

```json
{"deployment": {"appIdentity": "plantpal",
                "revision": "e3bcd1f889de3e06caa191e1e3428373c873e2f6",
                "deploymentId": "pla-dev-20260927103855-e3bcd1f889de",
                "environment": "dev"}}
```

Every deployment recorded 12 gating checks: the four CI/quality checks, four identity
checks (app, revision, deployment, environment — compared against the expected values,
`unknown` when unreported), three smoke checks (SPA index, backend health, and
`/api/v1/plants` → 401 so the API's auth guard is proven live behind the proxy), plus a
`criterion:<id>` check from the command line. `unknown` never resolves to `passed`; a
timed-out deploy has `exitCode: null` and result `unknown`.

Isolation was verified against the running stack, not from configuration: only
`127.0.0.1:8184->80` is published (Postgres/Redis/backend publish nothing), the volumes
are `plantpal-devdelivery_*` and separate from the long-lived stack's, no Kafka, and the
candidate's environment carries no production-only key (`DATABASE_URL`, `REDIS_URL`,
`CLOUDINARY_URL`, `SENTRY_DSN`, `STORAGE_TYPE`, `VPS_*`). The AI keys are the literal
placeholder `unset-in-dev-delivery` (Anthropic empty), and no value in the candidate's
env file is byte-identical to any value in `backend/.env` — compared by value.

### 4. Hosting and the hostname coordinated through demands; prerequisites reported

Both coordination paths were raised as demands before this report: `platform-vault`
(record port 8184 in the D040 register, and rule how plantpal publishes coordination
commits given that every `main` push deploys) and `contracts` (the tagged shapes).
Managed hosting and the launcher hostname belong to `runtime`, which owns Factory's
follow-up demand `factory-20260927-dev-delivery-routing`.

The vault answered the port half while this session ran (commit `6e06b0b`): **8184 is in
the port register**, loopback-only per D040, and plantpal's default stands — nothing to
adopt. The coordination-publication half came back **blocked on an owner ruling**, not
refused: no rule in `PLATFORM_DECISIONS.md` (D001–D113) or `DEMAND_SYSTEM.md` §3/§4 covers
where a repo publishes coordination commits when a `main` push deploys production, and
the vault records rulings rather than making them. So the interim practice below stands
until the owner rules.

**The requested URL is not verified, and is not claimed anywhere.** Observed
2026-09-27T10:40:55Z: `observe` → `{"loopback": {… e3bcd1f …}, "planotell": null}`. The
launcher's `plantpal` entry maps hostname `planotell` to the long-lived local stack
(`openUrl https://localhost:8444`, `launcher/README.md`), whose frontend answers
`/actuator/info` with the SPA HTML (HTTP 200, no identity JSON). Because a check through
the hostname cannot observe the deployed revision, every receipt kept `testUrl` on the
loopback URL and recorded `route:planotell: unavailable` — recorded, never gating. A
configured hostname is never taken as evidence.

Prerequisites that remain (**none of them plantpal's to close**):

| # | Prerequisite | Owner | Status |
|---|---|---|---|
| 1 | Add `Detect secrets` to the `dev` ruleset's required checks | owner (repo settings) | open — the check runs and the deploy gate enforces it, but a PR merge does not require it. It false-positives on a committed receipt; see the caveat on `operationKey` below |
| 2 | Tagged receipt / identity / lookup shapes incl. rollback identity | `contracts` | demand `plantpal-20260927-contracts-app-deploy-receipt-and-identity` |
| 3 | Port 8184 in the D040 register — **done**; a ruling on where plantpal publishes coordination commits — **open at the owner** | `platform-vault` recorded the port; the ruling is the owner's | `plantpal-20260927-platform-vault-planotell-dev-port-and-coordination-path` — the vault reported while this session ran: criterion 1 done ("8184 = plantpal dev-delivery frontend … plantpal's default stands, so plantpal has nothing to adopt"), criteria 2–3 blocked because no existing rule covers the case and the vault records rulings rather than making them |
| 4 | Managed hosting of the candidate, and `planotell.platform.localhost` → `127.0.0.1:8184` | `runtime` (+ `launcher`) | Factory demand `factory-20260927-dev-delivery-routing` (after this one) |
| 5 | Factory invoking `dev_delivery.py` (execution host, repo lock) | `factory` / `agent-runner` | not built |

## What the origin must know

- Reference values for your follow-up: candidate revision
  `e3bcd1f889de3e06caa191e1e3428373c873e2f6` (dev head), served at
  `http://127.0.0.1:8184`; deployments `pla-dev-20260927103855-e3bcd1f889de` (deploy,
  passed), `pla-dev-20260927104017-35e82764a24f` (deploy, passed, carries the rollback
  identity), `pla-dev-20260927104038-e3bcd1f889de` (rollback, passed). All are
  `nativeRef: plantpal:deployments/<id>`.
- `docs/dev-delivery-evidence/candidate-e3bcd1f.md` plus the six files in its
  `evidence/` directory are the readable evidence: three native receipts and their
  v0.31.0 producer-results.
- The producer-result's `environment` block only appears when the app really reported
  itself; treat a `null` there as "not observed", not as "not configured".
- `route:planotell` is a recorded observation only. Do not read a `passed` receipt as
  evidence that `http://planotell.platform.localhost` serves the candidate — that
  requires runtime/launcher to repoint the name at 8184 and is not done.

## Not done / caveats

- **The receipts are machine-local.** `.dev-delivery/` is gitignored, so the
  authoritative receipts live on this host; Factory re-fetches them with
  `dev_delivery.py lookup <id>` (a repo-local CLI printing JSON on stdout — the transport
  the contracts demand asks to have named). If Factory must read a receipt from another
  host, that needs a durable or remote lookup and is not built; it is criterion 4 of the
  contracts demand.
- **Committing a receipt trips the secret scanner.** gitleaks' `generic-api-key` rule
  matches a receipt's `correlation.operationKey` (a caller-chosen idempotency label, not a
  credential), so the evidence commit `4fcc50c` came back red on `Detect secrets` with four
  findings — the first time this flow has tripped that check. Fixed on this branch with
  four `.gitleaksignore` fingerprints bound to that commit (`7086e77`), verified locally
  with gitleaks 8.21.2, the build the workflow pins, over the range CI failed on: 4 leaks
  without the entry, 0 with it, one commit scanned in both runs. Two consequences the
  owner needs: the **green re-run on PR #190 does not exercise the ignore** (its range
  starts at the already-scanned commit, so it would be green either way — the A/B above is
  the evidence, not the tick), and a **squash-merge rewrites `4fcc50c`, which invalidates
  the fingerprints** and makes the dev push flag those four lines again; a merge commit
  preserves it. The durable fix is a config-level allowlist, but that changes gitleaks'
  config resolution for the whole repo and is the owner's call. `Detect secrets` is not in
  the `dev` ruleset (prerequisite 1), so it did not block the PR.
- **The candidate is running right now** as compose project `plantpal-devdelivery` on
  `127.0.0.1:8184`, and is left up because it *is* the deliverable runtime has to route.
  `dev_delivery.py down` stops it and keeps the volumes.
- **AI features are not testable on the candidate**: the AI keys are placeholders, so
  identification/chat criteria cannot be smoke-checked until the owner adds dev-scoped
  keys. The three smoke checks and the criterion hook work regardless.
- **`/actuator/info` is public in every profile, production included.** It reports
  `null` for revision/deployment/environment in production (nothing stamps them there),
  so no production revision or secret is exposed — but the exposure itself is a change
  the owner should be aware of.
- **Rollback does not roll back data.** Liquibase migrations are forward-only, so
  rolling back across a migration can fail the health check and be recorded `failed`
  rather than silently succeeding.
- **Dev-only, unmanaged.** Everything here ran on the owner's Windows host with Docker
  Desktop. Nothing was deployed to a managed host, and nothing was pushed or merged to
  `main` — production still runs `dd47739`.
- **Coordination publication deviates from `DEMAND_SYSTEM.md` §3/§4** (commit to `main`
  and push): a push to `plantpal`'s `main` runs `deploy.yml` and ships production, so
  this report is committed on a task branch through a PR into `dev`. The vault demand
  asks for that practice to be ruled or corrected, and for an optional `paths-ignore`
  filter on the deploy trigger.
