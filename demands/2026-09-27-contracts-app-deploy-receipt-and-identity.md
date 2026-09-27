---
id: plantpal-20260927-contracts-app-deploy-receipt-and-identity
date: 2026-09-27
from: plantpal
to: [contracts]
capability: "Tagged shapes for the app-deploy producer: a dev deployment receipt (with rollback identity), its lookup, and a running-app identity/revision record, so Factory and runtime never consume plantpal's native JSON"
acceptance-criteria:
  - "A tagged schema (e.g. delivery.deployment-receipt) carries deploymentId, repository, branch, mergedRevision (40-hex), imageDigests per component, result (passed|failed|unknown|unavailable|pending), observedAt, smoke checks, environment (name const dev) and a rollback identity (previous deploymentId + its revision + digests, nullable when none exists)"
  - "delivery.producer-result either gains an optional rollback reference or docs/task-delivery.md states that rollback identity is read from the receipt via nativeRef; either way the mapping for producer app-deploy is written down"
  - "A tagged schema for the running app's identity (appIdentity, revision nullable, deploymentId nullable, environment nullable) that runtime and Factory can read to prove which revision a URL serves; null means not reported, never a default"
  - "The lookup transport for app-deploy nativeRef plantpal:deployments/<id> is named (CLI JSON on stdout, file, or HTTP route) so Factory knows how to re-fetch it"
  - "Python binding for the new shapes at a tagged release; a Java binding only if contracts judges one needed (plantpal's producer is Python, see below)"
needs-owner: false
status: open
---

# Demand — app-deploy receipt, lookup and running-app identity shapes (D113)

Raised while fulfilling `factory-20260927-dev-delivery` (D113, contracts
`v0.31.0` `docs/task-delivery.md` §Producers, row `app-deploy`). That row names
the gap itself: *"Needs: a dev deployment receipt (deployment id, merged
revision, image digest, result) with lookup, and a running-app identity/revision
endpoint."*

## What we need

1. **Deployment receipt schema.** What plantpal's dev-delivery tool records per
   deployment. v0.31.0 `delivery.producer-result` covers deployment id
   (`operationId`), merged revision (`revision`), one digest (`artifactRef`),
   result (`outcome`) and smoke checks (`checks`). It has **no rollback
   identity** and only one artifact slot, and plantpal deploys two images
   (backend and frontend). Factory's demand asks for rollback identity in the
   receipt explicitly.
2. **Running-app identity schema.** `app.health` has no revision or deployment
   id. plantpal now reports `{appIdentity, revision, deploymentId, environment}`
   from `GET /actuator/info` (key `deployment`), each field nullable. `runtime`
   has to verify that `http://planotell.platform.localhost` serves the merged
   revision, so it will read this too. That makes it cross-repo and it should
   have a tagged shape.
3. **Lookup transport.** State how Factory re-fetches `plantpal:deployments/<id>`.
   Today it is a repo-local CLI (`python tools/dev-delivery/dev_delivery.py
   lookup <id>`) that prints a v0.31.0 `DeliveryProducerResult` as JSON.
4. **Bindings.** plantpal's producer is Python and uses the v0.31.0 Python
   binding, so **no Java binding is needed** for `producer-result`. If the
   identity record is also consumed in Java somewhere, contracts decides.

## Why / what is blocked

Until this ships, plantpal emits **only** the tagged v0.31.0
`DeliveryProducerResult`. The richer receipt, including rollback identity, and
the `/actuator/info` identity block stay **plantpal-native** and are documented
in `docs/dev-delivery.md`. Consumers must not bind to them. Factory's
rollback-identity criterion is met only natively until then.

## What we do once closed

Repin the dev-delivery tool to the new tag. Emit the receipt and identity
through the new bindings, keeping the native fields as they are. Add
round-trip tests against the new schemas. Tell Factory and runtime which tag to
consume.
