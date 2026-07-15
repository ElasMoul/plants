---
id: plantpal-20260715-tenant-app-networking-d040
date: 2026-07-15
from: plantpal
to: [platform-vault]
capability: Document (or rule on) the reachability model for a tenant app that runs in its OWN docker-compose network calling the loopback-bound (D040) platform host-services — so the next tenant app doesn't rediscover "ai-gateway unreachable" the hard way.
needs-owner: true
status: archived
---

# Demand — tenant-app ↔ platform-service networking convention under D040

## The problem (found live 2026-07-15)

PlantPal's backend runs in **its own `docker-compose.yml` network**, separate
from the `runtime/docker-compose.local.yml` network the platform host-services
live in. Under **D040** every runtime service publishes on `127.0.0.1` only.

Consequence: a container in PlantPal's network **cannot** reach a platform
service by `localhost` (that's the container itself) and **cannot** reach it by
docker service name (different compose network, no shared network). The only
working address is **`host.docker.internal:<port>`** — Docker Desktop resolves
it to the host loopback, where D040's `127.0.0.1`-bound published ports live.

PlantPal's committed defaults (and its local `.env`) used `localhost:8085` for
`PLATFORM_GATEWAY_URL`, so a dockerized backend failed every AI call with
`com.plantpal.shared.exception.PlantPalException: ai-gateway unreachable`
(`GatewayClient.java:67`) — the request never left the container; ai-gateway's
logs show nothing arriving. Fixed in PlantPal (its own repo) by defaulting the
platform-profile URLs to `host.docker.internal` and correcting the local `.env`.

## What we need from the vault (a ruling + a doc, not code)

This is an **architecture gap D040 created**, and it will bite `pregnancy`,
`tutor`, and any future tenant app the same way. The vault owns the
architecture/spec; PlantPal can't set a platform-wide convention. Please
**rule on and document** one of:

1. **Sanction `host.docker.internal`** as THE tenant-app → platform-service
   address for local-first dev (tenant apps stay in their own compose network),
   and record it in `PLATFORM_ARCHITECTURE.md` / the D040 entry as the
   compensating-control's known cross-network access path; or
2. **Rule that tenant apps join a shared external `platform-net`** and reach
   services by name — which needs a matching change in `runtime`'s compose (a
   demand the vault would then dispatch), and a decision on whether that widens
   the D040 loopback posture; or
3. some other owner-preferred model.

Either way the outcome is a short, findable convention (one paragraph +
example) so the next app's first dockerized run just works. Non-blocking for
V1 (PlantPal is fixed locally) — this prevents recurrence.

## Evidence

- Working proof after fix: `POST http://host.docker.internal:8085/ai/request`
  from inside `plantpal-backend` (appId `plantpal`) → `200 {"result":"ok",...}`.
- Failing config: `PLATFORM_GATEWAY_URL=http://localhost:8085` inside the
  container → request never leaves; "ai-gateway unreachable".
- Related: D040 (loopback binding), `runtime/docker-compose.local.yml` (no
  shared external network declared for tenant apps to join today).

## Resolution (owner-approved, 2026-07-15)

`platform-vault` ruled **option 1**: `host.docker.internal:<port>` sanctioned
as the tenant-app → platform-service address. Shipped: D045 (new decision
entry, incl. clone-model consequences), `PLATFORM_ARCHITECTURE.md` §11
(one-paragraph convention + example `PLATFORM_GATEWAY_URL=http://host.docker.internal:8085`),
and a D040 cross-reference addendum. No `runtime` change needed. Nothing
further owed by plantpal — PlantPal's own fix is now the platform
convention. Archived per the usual loop-close flow.
