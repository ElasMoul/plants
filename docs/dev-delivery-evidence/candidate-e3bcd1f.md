# Planotell dev candidate — `e3bcd1f` (D113, demand `factory-20260927-dev-delivery`)

Observed evidence for the first real dev deliveries through
`tools/dev-delivery/dev_delivery.py`. Procedure and hard rules:
`docs/dev-delivery.md`. This file is a review handoff, not a release authorisation:
nothing here touches production, and no push or merge to `main` was made.

## Candidate

- Revision: `e3bcd1f889de3e06caa191e1e3428373c873e2f6` — the head of `origin/dev`
  (PR #189's merge commit, i.e. a revision `dev` itself pointed at).
- Merged SHA, not a task-branch revision: the tool refuses anything that is not on
  `origin/dev`'s first-parent line (see "Refusals" below).
- Gate at that revision: `Backend CI`, `Frontend CI`, `Detect secrets` all `success`
  on the push run; `sonar-gate` `success` on PR #189's head `c21dc58` (D112).
- Served at `http://127.0.0.1:8184` (loopback only) by compose project
  `plantpal-devdelivery`. The requested hostname `http://planotell.platform.localhost`
  is **not** verified — see "Hostname".

## Observed identity

`GET /actuator/info` through the published URL, read back by the tool:

```json
{"deployment": {"appIdentity": "plantpal",
                "revision": "e3bcd1f889de3e06caa191e1e3428373c873e2f6",
                "deploymentId": "pla-dev-20260927104038-e3bcd1f889de",
                "environment": "dev"}}
```

Read independently of the tool (`curl http://127.0.0.1:8184/actuator/info`) at
2026-09-27T10:39:58Z, during the first deployment: identical shape, with that
deployment's id. The revision is not configured anywhere in the stack — it is stamped
into the image at build time from the exact archived tree.

## Deployments recorded (all `passed`, `exitCode` 0)

| Deployment id | Kind | Revision | Gating checks | `route:planotell` |
|---|---|---|---|---|
| `pla-dev-20260927103855-e3bcd1f889de` | deploy | `e3bcd1f` | 12/12 passed | `unavailable` |
| `pla-dev-20260927104017-35e82764a24f` | deploy | `35e8276` | 12/12 passed | `unavailable` |
| `pla-dev-20260927104038-e3bcd1f889de` | rollback | `e3bcd1f` | 12/12 passed | `unavailable` |

Gating checks per deployment: `ci:Backend CI`, `ci:Frontend CI`, `ci:Detect secrets`,
`quality:sonar-gate`, `identity:app`, `identity:revision`, `identity:deployment`,
`identity:environment`, `smoke:frontend-index` (`GET /` → 200 with `<app-root`),
`smoke:backend-health` (`GET /actuator/health` → 200 `UP`), `smoke:api-auth-guard`
(`GET /api/v1/plants` → 401), plus the one criterion passed on the command line
(`criterion:AC3-info`, `GET /actuator/info` → 200 containing `appIdentity`).
`route:planotell` is recorded but never gates.

The two native receipts and their contracts v0.31.0 `delivery.producer-result`
renderings are in `evidence/`. Both shapes are plantpal-native / contracts-tagged
respectively — the receipts are review copies of machine-local files (the
authoritative copies live in `.dev-delivery/receipts/`, which is gitignored).

## Rollback identity

- `pla-dev-20260927103855-e3bcd1f889de` — the first deployment, `rollback: null`.
- `pla-dev-20260927104017-35e82764a24f` — carries `rollback` naming the previous
  **passed** deployment: its `deploymentId`, its `revision` and **both** image digests.
- `pla-dev-20260927104038-e3bcd1f889de` — `kind: rollback`, `rollbackOf` = the second
  deployment, `restores` = the first. It redeployed the first deployment's retained
  images with no rebuild, only after re-checking that each image's digest still matched
  the receipt, then went through the same observation and checks and passed.

## Isolation and secrets (checked against the running stack, not the config)

- Published ports: only `127.0.0.1:8184->80/tcp` (the frontend). Postgres, Redis and
  the backend publish nothing to the host.
- Volumes: `plantpal-devdelivery_{postgres,redis,photos}_data`, distinct from the
  long-lived local stack's `plantpal_{...}` — separate data.
- No Kafka (`APP_IDENTIFICATION_TRANSPORT=in-process`), as in production.
- The candidate backend's environment carries **no** `DATABASE_URL`, `REDIS_URL`,
  `CLOUDINARY_URL`, `SENTRY_DSN`, `STORAGE_TYPE` or `VPS_*`.
- `GITHUB_TOKEN` and `PLANTNET_API_KEY` are the literal placeholder
  `unset-in-dev-delivery`; `ANTHROPIC_API_KEY` is empty. `JWT_SECRET` and the VAPID
  pair are generated fresh. No value in the candidate's env file is byte-identical to
  any value in `backend/.env` (compared by value, not by reading the generator).
  Consequence: AI features are not testable on this candidate unless the owner adds
  **dev-scoped** keys.
- Production is not stamped: `deploy/vps/` and `deploy.yml` set none of
  `APP_REVISION`/`APP_DEPLOYMENT_ID`/`APP_ENVIRONMENT`, so the now-public
  `/actuator/info` reports `null` for all three in production — no production revision
  or secret is published.

## Refusals (the dev-only boundary, exercised 2026-09-27)

```
$ dev_delivery.py deploy --revision dd47739…   # origin/main
dev-delivery: refused: dd47739… is not a revision of origin/dev itself
             (task-branch revisions are never deployed, even once merged)   [exit 2]

$ dev_delivery.py deploy --revision c21dc58…   # merged into dev via PR #189,
                                               # but not on dev's first-parent line
dev-delivery: refused: c21dc58… is not a revision of origin/dev itself …   [exit 2]
```

## Hostname

`http://planotell.platform.localhost` is **not** routed to this candidate, so it is not
reported as verified anywhere. Observed at 2026-09-27T10:40:55Z:
`dev_delivery.py observe` → `{"loopback": {…e3bcd1f…}, "planotell": null}`. The
launcher's `plantpal` entry (`launcher/apps.json`) maps hostname `planotell` to the
long-lived local stack (`openUrl https://localhost:8444`, per `launcher/README.md`),
whose frontend answers `/actuator/info` with the SPA's `index.html` (HTTP 200, not
identity JSON). Because a check made through the hostname cannot observe the deployed
revision, the receipt keeps `testUrl` on the loopback URL and records
`route:planotell: unavailable`. A configured hostname is never evidence.

## Reproduce

```bash
tools/dev-delivery/.venv/Scripts/python tools/dev-delivery/dev_delivery.py observe
tools/dev-delivery/.venv/Scripts/python tools/dev-delivery/dev_delivery.py list
tools/dev-delivery/.venv/Scripts/python tools/dev-delivery/dev_delivery.py lookup <deploymentId>
```

`down` stops the stack and keeps its volumes; `deploy` rebuilds it from the merged
revision. Prerequisites still owned by others are listed in `docs/dev-delivery.md` §5.
