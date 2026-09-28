---
demandId: factory-20260928-plantpal-reachable-receipt-transport
worker: plantpal
date: 2026-09-28
status: done
shipped:
  - "demands/2026-09-28-contracts-app-deploy-lookup-route.md (commit 9e63240) — demand raised to `contracts` asking it to publish the app-deploy lookup route"
  - "docs/dev-delivery.md §3 — the explicit ruling criterion 4 asks for; §5 row 5 re-ruled from `Factory calling dev_delivery.py` to the transport question"
  - "no HTTP route, no code change, no tag — this closes as an explicit decision, not as a shipped transport"
summaryRef: "commit 9e63240 (ruling + raise) on chore/PP-118-app-deploy-lookup-route-demand"
---

# Fulfillment — offer the app-deploy receipt lookup over a transport Factory can reach

## State check first: the capability was not already shipped

Read before working, so this is not a no-op report:

- `docs/dev-delivery.md` §3 read **"There is no HTTP route."**
- `contracts` v0.36.0 `docs/task-delivery.md` §App-deploy read **"No HTTP route
  exists. If Factory needs to re-fetch from another host, plantpal must offer a new
  transport and contracts must publish it. The CLI is the transport this release
  names."**
- `deploy/dev-delivery/docker-compose.yml` and `nginx.dev-delivery.conf` publish one
  port (`127.0.0.1:8184`) and proxy only `/api/`, `/photos/` and
  `/actuator/{health,info}`; no service reads `.dev-delivery/receipts/`.
- `dev_delivery.py` has `lookup`/`receipt` subcommands and no serving mode.

So there was nothing to document as already-done, and nothing to redo.

## What shipped

**Nothing on the wire.** No route, no code change, no tag, no tests. The
capability the demand asks for — a lookup Factory can call — does **not** exist
after this session, and this report does not claim it does.

What shipped is the two things the demand itself directs:

1. **The ruling criterion 4 asks for** (`docs/dev-delivery.md` §3): the CLI named in
   `contracts` §App-deploy is the only **sanctioned** transport, recorded explicitly
   so Factory's consumption leg stalls visibly rather than silently. plantpal
   declines to hand-roll the alternative: an HTTP route Factory binds to is a
   cross-repo interface, and `contracts` v0.36.0 states the order itself —
   *"plantpal must offer a new transport and contracts must publish it"* — which is
   also this repo's standing rule that all cross-repo interfaces come from
   `contracts`.
2. **The offer, made through the right door** (commit `9e63240`):
   `plantpal-20260928-contracts-app-deploy-lookup-route` (to `contracts`) asks it to
   publish the route, with a concrete proposal so it can move quickly:

   | Route | Returns |
   |---|---|
   | `GET /delivery/v1/app-deploy/deployments/{deploymentId}/receipt` | `delivery.deployment-receipt` |
   | `GET /delivery/v1/app-deploy/deployments/{deploymentId}/producer-result` | `delivery.producer-result` |

   served by a service in the dev-delivery stack on that stack's **existing**
   published port, so answering a call runs **no process inside the plantpal
   checkout** — which is the property your criterion 1 asks for. The demand also
   asks `contracts` to rule the one question that gates the fastest path: whether a
   producer's lookup path, host and auth posture are part of the published interface
   or are the producer's own service design needing no contracts change. On the
   second reading, plantpal implements immediately with no further contracts step.

## Criterion by criterion

| # | Criterion | Verdict |
|---|---|---|
| 1 | Publish a lookup Factory can call; request the interface from `contracts` first, don't hand-roll a shared shape | **Partial.** The request is made (`9e63240`). The route is not published, because the order you asked for puts publication in `contracts`' hands before plantpal serves anything |
| 2 | Serve the tagged receipt / its `producer-result` mapping, miss machine-distinguishable from an error | **Not met.** Nothing is served, so there is no miss path to distinguish |
| 3 | Report the exact route, the tag it serves, and what an absent/unauthorized call returns, with live-call evidence | **Not met.** There is no route to call; producing that evidence would require the thing criterion 1 forbids hand-rolling |
| 4 | If plantpal holds the CLI is the only sanctioned transport, record it as the ruling and close as an explicit decision | **Met.** This is the terminal state, recorded in `docs/dev-delivery.md` §3 and here |

Criterion 3's evidence requirement is therefore unmet **by construction**, not
skipped: it asks for a live call against a route that does not exist.

## What the origin must know

- **This demand is closed as an explicit decision, not as a delivered transport.**
  Your reading is right: Factory should keep its consuming leg blocked. Nothing about
  your side changes today.
- **The unblock now sits with `contracts`, not with plantpal.** The sequence is:
  `contracts` publishes (or rules the route is producer service design) → plantpal
  implements and serves the tagged documents verbatim → plantpal raises a demand to
  `factory` with the exact route, the `contracts` tag it serves, and live evidence
  for hit, miss and unauthorized. If you want to track it, the open item is
  `plantpal-20260928-contracts-app-deploy-lookup-route`.
- **Do not bind to the native `plantpal.dev-deployment-receipt/1` document.**
  Unchanged, and it stays the reason the rollback identity is unread.
- **The miss contract you must implement is unchanged** and is the one already
  written down: a miss is `unavailable`, never `failed`, and never grounds to
  redeploy under the same operation key. The route demand asks `contracts` to give
  that a named code so a miss is machine-distinguishable from an unauthorized or
  store-unreachable call — the distinction your criterion 2 asks for.

## Not done / caveats

- **No route, no live call, no hit/miss/unauthorized evidence.** Criterion 3 is
  unmet and is not claimed.
- **Factory remains stalled.** The stall is now visible in three places —
  `docs/dev-delivery.md` §3 and §5 row 5, and the `contracts` demand — rather than
  silent, which is the outcome your criterion 4 pre-authorizes.
- **No code, tests or tag changed.** The only files touched are
  `docs/dev-delivery.md` and the new demand file. Both `dev_delivery.py` transports
  emit exactly what they emitted before; no consumer obligation moved (D031).
- **One judgement call, stated plainly:** plantpal could have served an HTTP route
  this session by inventing its path and status codes. It did not, because your
  criterion 1 forbids it and `contracts` v0.36.0 reserves publication to itself. If
  the owner's intent was to have the route built in parallel with the request, that
  is a one-line ruling away and the implementation is unblocked — but it would ship
  an interface Factory binds to before `contracts` has shaped it.
- The dev-delivery stack still publishes exactly one port; the proposal reuses it
  rather than asking D040 for a second allocation.
