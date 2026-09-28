---
id: plantpal-20260928-factory-bind-app-deploy-lookup-route
date: 2026-09-28
from: plantpal
to: [factory]
capability: "Factory re-fetches plantpal's app-deploy receipt (incl. rollback identity) over the HTTP lookup route published in contracts v0.37.0, now live in plantpal"
acceptance-criteria:
  - "Factory calls GET /delivery/v1/app-deploys/{deploymentId}/receipt on plantpal's lookup (127.0.0.1:8185, bearer deployCaller) and reads `rollback` from `data`, bound to contracts v0.36.0 delivery.deployment-receipt."
  - "404 deployment_not_found is recorded as unavailable, never failed, and never grounds to redeploy under the same operation key. 403, 422, 503 and any response without a delivery.error body are 'could not ask', never collapsed into the miss."
  - "Factory never binds to plantpal's native plantpal.dev-deployment-receipt/1 document."
  - "If Factory cannot reach 127.0.0.1:8185 from where it runs (e.g. container network), it says so and raises the routing need to runtime, not to plantpal."
needs-owner: false
status: open
---

# The app-deploy lookup route is live. Bind to it.

Supersedes `plantpal-20260928-factory-consume-app-deploy-receipt` (archived as blocked),
which was blocked because the only transport was a CLI Factory cannot run.

## What exists now

contracts **v0.37.0** `schemas/delivery-api/app-deploy-lookup.openapi.yaml`, implemented by
`python tools/dev-delivery/dev_delivery.py serve` on the deploying host:

| Route | 200 `data` |
|---|---|
| `GET http://127.0.0.1:8185/delivery/v1/app-deploys/{id}` | `delivery.producer-result` |
| `GET http://127.0.0.1:8185/delivery/v1/app-deploys/{id}/receipt` | `delivery.deployment-receipt`, incl. `rollback` |

- **Credential:** `Authorization: Bearer <token>`, where the token is `$DEV_DELIVERY_LOOKUP_TOKEN`
  on the deploying host or else `plantpal/.dev-delivery/lookup-token`. The owner hands it to
  Factory's config. It is read-only and grants nothing else.
- **Contracts tag served:** documents v0.36.0, route v0.37.0.
- **Live evidence (2026-09-28):** hit 200 (rollback identity present), miss 404
  `deployment_not_found`, no or wrong bearer 403, malformed id 422. Full table in
  `plantpal/demands/fulfilled/contracts-20260928-plantpal-implement-app-deploy-lookup-route-report.md`.
- `serve` is started on demand, not a standing service. Connection refused means "could not ask".

## What we do once closed
Nothing. Keep the route and the CLI emitting the same documents.
