---
demandId: contracts-20260928-plantpal-implement-app-deploy-lookup-route
worker: plantpal
date: 2026-09-28
status: done
shipped:
  - "reviewed v0.37.0 app-deploy-lookup.openapi.yaml + delivery.error code table: accepted, no gaps raised"
  - "tools/dev-delivery/dev_delivery.py serve — GET /delivery/v1/app-deploys/{id} and .../receipt, {\"data\": ...} envelope"
  - "status table as published: 404 deployment_not_found (only 404), 403 caller_not_authorized (checked first), 503 producer_unavailable, 422 invalid_request; pending = 200"
  - "both routes share the CLI's document builders, so route data == CLI stdout (live-diffed)"
  - "host 127.0.0.1 (non-loopback refused), port 8185, bearer from $DEV_DELIVERY_LOOKUP_TOKEN or gitignored .dev-delivery/lookup-token"
  - "CLI unchanged: lookup/receipt same JSON, miss still exit 4"
  - "tests: 67 pass (11 new HttpLookupRoute cases)"
  - "docs/dev-delivery.md §3 HTTP lookup route + §5 row 5"
  - "demand raised to factory (route live) and to platform-vault (register port 8185)"
summaryRef: "branch chore/PP-118-app-deploy-lookup-route-demand"
---

# Fulfillment: app-deploy HTTP lookup route, consuming leg closed

## Criteria

1. **Review.** v0.37.0 accepted as published. No interface gaps.
2. **Two GETs.** Implemented exactly. The receipt route serves the same normalized
   v0.36.0 tagged document `receipt <id>` prints. It is derived deterministically
   from the native on-disk record and validated on every serve, as the CLI is.
   plantpal stores its native record, not the tagged one, so "verbatim" here means
   identical to the CLI emission. A `pending` receipt is a 200.
3. **Status table.** As published. Auth is checked before routing, so a 403 never
   turns into a 404. A receipt that fails tagged validation is a 503 and is never served.
4. **Host, port, credential.** Recorded in `docs/dev-delivery.md` §3 "HTTP lookup route":
   `127.0.0.1:8185`, loopback-only (D040), bearer token. PLATFORM_STATE §3 is the vault's
   to write, so demand `plantpal-20260928-platform-vault-lookup-port-8185` asks for it.
5. **CLI unchanged.** Existing `LookupTransport` tests pass untouched.
6. **Factory told.** Demand `plantpal-20260928-factory-bind-app-deploy-lookup-route`.

## Live evidence (2026-09-28, real store, `serve` on 127.0.0.1:8185)

| Call | Status | Body |
|---|---|---|
| `GET …/pla-dev-20260927104038-e3bcd1f889de` + bearer | 200 | producer-result |
| `GET …/pla-dev-20260927104038-e3bcd1f889de/receipt` + bearer | 200 | receipt, `rollback.deploymentId = pla-dev-20260927104017-35e82764a24f`; data equals `receipt <id>` stdout |
| `GET …/pla-dev-20260101000000-ffffffffffff` + bearer | 404 | `deployment_not_found`, retryable false |
| `GET …/a%20b` + bearer | 422 | `invalid_request` |
| no bearer / wrong bearer | 403 | `caller_not_authorized` |

Server stopped afterwards and port 8185 verified free.

## Not done
- It is not a standing service. Someone on the deploying host has to start `serve`.
- Reaching it from a container network is `runtime`'s routing, not plantpal's.
