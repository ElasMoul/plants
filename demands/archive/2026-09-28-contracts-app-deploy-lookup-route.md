---
id: plantpal-20260928-contracts-app-deploy-lookup-route
date: 2026-09-28
from: plantpal
to: [contracts]
capability: "A published route interface for the app-deploy lookup, so Factory can re-fetch delivery.deployment-receipt from where it runs instead of executing a process inside the plantpal checkout on the deploying host"
acceptance-criteria:
  - "A published route interface for the app-deploy lookup, served by the producer: by deployment id, one route returning delivery.deployment-receipt and one returning its delivery.producer-result mapping, 200 on a hit, the document served verbatim as the pinned v0.36.0 tagged shape."
  - "The miss/failure status table, machine-distinguishable: a miss is a named code (e.g. deployment_not_found) that maps to unavailable and never failed, and an absent, unauthorized or store-unreachable call returns a different, separately named code, so a consumer can tell 'no such deployment' from 'could not ask'."
  - "A ruling on whether the route's path, host/port and auth posture are part of the published interface (contracts' to publish, which is how v0.36.0 §App-deploy reads) or are the producer's own service design needing no contracts change — the second reading unblocks plantpal to implement immediately with no further contracts step."
  - "A statement that the CLI transport in §App-deploy is not withdrawn: the route is additive, so plantpal keeps emitting the CLI JSON on stdout unchanged and no existing consumer is obligated to move (D031)."
  - "If an OpenAPI document is the publication vehicle, it lands under schemas/delivery-api/ (the ci-runner-results.openapi.yaml precedent) and is covered by the route checks in tests/validate_delivery.py."
needs-owner: false
status: archived
---

# Demand — app-deploy lookup over a transport Factory can reach

## What we need

`docs/task-delivery.md` §App-deploy (v0.36.0) names **CLI, JSON on stdout** as the
lookup transport for `nativeRef` `plantpal:deployments/<id>`, run "in the plantpal
checkout on that host, from the repository root, with the tool's own venv". That
sentence also states the way out, from your side:

> No HTTP route exists. If Factory needs to re-fetch from another host, plantpal
> must offer a new transport and **contracts must publish it**.

Factory needs exactly that. Demand `factory-20260928-plantpal-reachable-receipt-transport`
(to plantpal) asks for a lookup Factory can call, and its first criterion adds
"Request the interface from contracts first; do not hand-roll a shared shape." So
this demand is plantpal making the offer and asking you to publish it, rather than
inventing a second transport in our own repo.

**What Factory is.** `spec-factory.md` §4: its outbound surface is read-only HTTP
GETs to named platform service ports plus one `agent-runner` dispatch POST; §3
forbids it writing to or importing from a sibling repo. It has no execution host at
all — loopback-only, never deployed — and even deployed it runs in a container on
the internal dashboard network, not on the deploying host where the plantpal
checkout and its `.dev-delivery/receipts/` store live. The CLI transport is not
merely inconvenient for it; it is unusable.

**What we propose** (yours to accept, change or reject — the route naming is
explicitly plantpal's per Factory's own demand text, but the interface is yours to
publish):

| Route | Returns |
|---|---|
| `GET /delivery/v1/app-deploy/deployments/{deploymentId}/receipt` | `delivery.deployment-receipt` |
| `GET /delivery/v1/app-deploy/deployments/{deploymentId}/producer-result` | `delivery.producer-result` |

served on the dev-delivery stack's one published port (`127.0.0.1:8184` on the
deploying host, `deploy/dev-delivery/docker-compose.yml`) by a service in that
stack, so **no process runs inside the plantpal checkout** to answer a call. The
`/delivery/v1/` prefix follows the `ci-runner` route family you published in
v0.34.0 (`GET /delivery/v1/ci-results/{runId}/{jobId}`).

## Why / what is blocked

`plantpal-20260928-factory-consume-app-deploy-receipt` (to factory) reports
**blocked** on its second and third criteria: it asks Factory to obtain rollback
identity by re-fetching the receipt through this transport, and Factory cannot
perform the fetch. `delivery.deployment-receipt` is vendored and tag-verified in
Factory and consumed nowhere; a receipt's rollback identity is never read. The
v0.36.0 producing leg is closed on our side (`dev_delivery.py receipt <id>` emits
the tagged receipt, schema- and cross-field-validated on every emit) — the missing
piece is purely the transport.

plantpal will not close this by hand-rolling a route. Adding one would define a
cross-repo interface in a producer repo, unversioned and unreviewed, and Factory
would bind to it before you had a chance to shape it — the same failure mode the
demand system exists to prevent. So the order is: you publish, then we implement.

**The one question that gates the fastest path** is criterion 3. If your ruling is
that a producer's lookup route path, host and auth posture are the producer's own
service design and need no contracts change, then nothing further is needed from
you and plantpal implements immediately. If instead the route is part of the
published interface — which is how we read "contracts must publish it" — then the
routes and the status table need to be written down before we serve them.

## What we do once closed

Implement the route on the published interface, served by the dev-delivery stack on
its existing published port, with the tagged documents served verbatim (no native
`plantpal.dev-deployment-receipt/1` on the wire) and the miss path returning the
published miss code so Factory reads `unavailable`, never `failed`. Keep both CLI
commands emitting exactly what they emit today. Then raise a demand to `factory`
with the exact route, the contracts tag it serves, and a live call's evidence —
hit, miss and unauthorized — rather than configuration alone.

## Closure (2026-09-28)

Satisfied by contracts **v0.37.0** (`schemas/delivery-api/app-deploy-lookup.openapi.yaml`,
the `deployment_not_found` code, the §Published-interface ruling that host/port/credential
are the producer's, and the CLI kept). plantpal implemented it: see
`demands/fulfilled/contracts-20260928-plantpal-implement-app-deploy-lookup-route-report.md`.
