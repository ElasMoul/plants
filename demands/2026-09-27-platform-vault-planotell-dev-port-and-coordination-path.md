---
id: plantpal-20260927-platform-vault-planotell-dev-port-and-coordination-path
date: 2026-09-27
from: plantpal
to: [platform-vault]
capability: "Record 127.0.0.1:8184 as plantpal's Planotell dev-delivery upstream (D040 port register), and rule how plantpal publishes coordination commits given that any push to main deploys production"
acceptance-criteria:
  - "PLATFORM_STATE.md's port register records 8184 = plantpal dev-delivery frontend (http, loopback-only, D113 Planotell dev candidate), or allocates a different port that plantpal then adopts"
  - "An owner ruling (or a recorded existing rule) states where plantpal's coordination commits (demand raises, fulfillment reports) are published, given that .github/workflows/deploy.yml deploys production on every push to main with no path filter"
  - "The ruling says whether plantpal may add a paths-ignore filter for demands/**, .brain/** and docs-only changes to deploy.yml's push trigger, which would take effect only on the owner's next dev→main release"
needs-owner: true
status: open
---

# Demand — Planotell dev-delivery port + plantpal's coordination-publication path

Raised while fulfilling `factory-20260927-dev-delivery` (D113).

## What we need

**1. Port 8184 (D040 register).** plantpal's dev-delivery stack
(`deploy/dev-delivery/docker-compose.yml`, compose project
`plantpal-devdelivery`) publishes **one** port: its nginx frontend, plain http,
on `127.0.0.1:${DEV_DELIVERY_PORT:-8184}`. It serves the SPA and proxies `/api/`
and `/actuator/{health,info}` to that stack's own backend. Postgres and Redis are
internal to the stack and publish nothing. Probed free on 2026-09-27. The
lowest free 81xx after 8183, per the register's own note. This is the upstream
`runtime`/`launcher` should point `planotell.platform.localhost` at. Launcher's
existing `planotell → :8444` points at the long-lived local stack, which is not
a verified dev candidate. The factory demand to `runtime` says so as well.

**2. Coordination-publication path.** `DEMAND_SYSTEM.md` §3/§4 says to commit
raises and reports to `main` and push. In plantpal, **every push to `main`
deploys production**: `deploy.yml` has `on: push: branches: [main]` with no
`paths` filter, so a demand-only commit would rebuild and redeploy the VPS
backend and both Vercel frontends. D113 clause 5 forbids coordination from
carrying implementation into production. Current practice is that plantpal
coordination commits land on `dev`, as local commits or through a PR, and the
coordinator reads them off disk. We need that recorded or corrected by a ruling. We
also ask whether plantpal may add
`paths-ignore: ['demands/**', '.brain/**', '**/*.md']` to the deploy trigger.
That change would reach `main` only with the owner's next release.

## Why / what is blocked

- Without (1), `runtime`'s `factory-20260927-dev-delivery-routing` has no
  approved upstream to route, and "planotell.platform.localhost" stays
  unverified.
- Without (2), every plantpal demand raise and report either breaks the
  documented convention or risks a production deploy.

## What we do once closed

(1) Adopt the allocated port as the default in `deploy/dev-delivery/` if it
differs. (2) Follow the ruling, and add the deploy-trigger filter on a task
branch only if it is ruled in.
