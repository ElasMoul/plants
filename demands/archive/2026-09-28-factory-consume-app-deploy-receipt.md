---
id: plantpal-20260928-factory-consume-app-deploy-receipt
date: 2026-09-28
from: plantpal
to: [factory]
capability: "Factory re-fetches plantpal's app-deploy receipt over the tagged CLI transport, so its rollback-identity criterion is met from the v0.36.0 delivery.deployment-receipt rather than from plantpal's native JSON"
acceptance-criteria:
  - "Factory consumes contracts v0.36.0 for producer app-deploy: delivery.producer-result (unchanged in meaning since v0.31.0) and delivery.deployment-receipt (new, carries rollback identity)."
  - "Factory obtains rollback identity by re-fetching nativeRef plantpal:deployments/<id> through the CLI transport and reading `rollback` from the receipt, since delivery.producer-result deliberately gained no rollback field."
  - "Factory treats a lookup miss as unavailable, never failed: exit 4 with `deployment_not_found: <id>` on stderr and nothing on stdout, and never as grounds to redeploy under the same operation key."
  - "If Factory cannot re-fetch from the deploying host (its execution host is not built yet), it says so and this demand stays open rather than binding to plantpal's native plantpal.dev-deployment-receipt/1 document."
needs-owner: false
status: open
---

# Consume the tagged app-deploy receipt (contracts v0.36.0)

## What we need

`contracts` **v0.36.0** published `delivery.deployment-receipt` — the receipt your
`app-deploy` row asked for (deployment id, merged revision, per-component image
digests, result, smoke checks) **plus** the rollback identity your original
criterion names. plantpal's producing leg is now closed: `dev_delivery.py receipt
<id>` emits the tagged receipt, validated against the JSON Schema and the
cross-field rules on every emit.

**Rollback identity is not in `delivery.producer-result`** and never will be. It
lives in the receipt, which you re-fetch through `nativeRef`:

| Command (repo root, deploying host) | stdout | Shape |
|---|---|---|
| `python tools/dev-delivery/dev_delivery.py lookup <id>` | one JSON document | `delivery.producer-result` |
| `python tools/dev-delivery/dev_delivery.py receipt <id>` | one JSON document | `delivery.deployment-receipt`, incl. `rollback` |

Install: `platform-contracts @ git+https://github.com/elmoul/contracts.git@v0.36.0#subdirectory=gen/python`.

## Why

Your D113 criterion ("a dev deployment receipt … with lookup") is met only
natively until you bind to the tagged shape. The mapping is written down and
executable in contracts `docs/task-delivery.md` §App-deploy +
`tests/validate_delivery.py` `receipt_to_producer_result`.

## What we do once closed

Nothing further from plantpal — the transport and the shapes are shipped. The
open blocker is **your** execution host (see the prerequisites table in
`plantpal/docs/dev-delivery.md` §5): the lookup runs on the host that deployed,
so Factory calling it is `factory`/`agent-runner`'s work.

## Note on obligation

Per D031 this release is **additive** and no consumer is obligated to move.
This demand exists so the loop is closed explicitly rather than by assumption —
if you are not ready to bind, leave it open and say why; there is no deadline
attached to it from our side.
