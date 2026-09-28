---
id: plantpal-20260928-runtime-consume-deployment-identity
date: 2026-09-28
from: plantpal
to: [runtime]
capability: "runtime verifies which revision http://planotell.platform.localhost serves by reading the tagged app/deployment-identity block from GET /actuator/info"
acceptance-criteria:
  - "runtime consumes contracts v0.36.0 and reads app/deployment-identity (appIdentity plus nullable revision, deploymentId, environment) from plantpal's public GET /actuator/info, top-level key `deployment`."
  - "runtime reads the identity THROUGH the URL under test and treats a null field as not reported — never as matching an expected value and never filled with a default."
  - "runtime states that route:planotell in plantpal's receipt is recorded but non-gating, so a passing identity through the hostname is plantpal's evidence and does not by itself constitute runtime's verification."
needs-owner: false
status: open
---

# Consume the tagged running-app identity (contracts v0.36.0)

## What we need

`contracts` **v0.36.0** published `app/deployment-identity`: what a running app
reports about itself. plantpal serves exactly that shape, public, at
`GET /actuator/info` under the top-level key `deployment`, and the dev nginx
proxies `/actuator/{health,info}`:

```json
{"deployment": {"appIdentity": "plantpal", "revision": "<40-hex|null>",
                "deploymentId": "<id|null>", "environment": "dev|null"}}
```

`revision` is baked in at build time (`APP_REVISION`); `deploymentId` and
`environment` come from the deploying tool. Each is **nullable, and null means
not reported** — a local run that knows its name but not its revision serves
`null`, never a branch name or a configured default. TypeScript bindings:
`../contracts-worktrees/v0.36.0/gen/ts` re-exports `AppDeploymentIdentity`.

## Why

Your verification that `http://planotell.platform.localhost` serves the merged
revision otherwise has no tagged shape to bind to, and the fields you need
(`revision`, `deploymentId`) are exactly the ones that can legitimately be
`null`. The identity is also embedded in plantpal's receipt as `observed`, so
reading it live and reading it from the receipt must agree.

## What we do once closed

Nothing further from plantpal. Note the hostname itself is still **your**
prerequisite (prerequisites table in `plantpal/docs/dev-delivery.md` §5): today
`planotell.platform.localhost` resolves to the long-lived stack, not to the dev
candidate's `127.0.0.1:8184`, so plantpal records `route:planotell` as
`unavailable` on every deployment and reports the loopback URL as the observed
one.

## Note on obligation

Per D031 this release is **additive** and no consumer is obligated to move. This
demand exists so the loop is closed explicitly rather than by assumption.
