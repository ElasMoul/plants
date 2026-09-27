---
demandId: launcher-20260926-plantpal-frontend-atlas-port-8182
worker: plantpal
date: 2026-09-27
status: done
shipped: ["docker-compose.yml: frontend-atlas ports 0.0.0.0:8182->80 / 0.0.0.0:8445->443 changed to 127.0.0.1:8183->80 / 127.0.0.1:8445->443 (branch bugfix/PP-110-atlas-loopback-port)"]
---

## What was found
Not previously shipped: `plantpal/docker-compose.yml` still published `"8182:80"` and
`"8445:443"` on all interfaces; the live container showed `0.0.0.0:8182->80/tcp`.

## What changed
- `frontend-atlas` now publishes `127.0.0.1:8183:80` and `127.0.0.1:8445:443` (D040 loopback-only).
- Port choice: PLATFORM_STATE.md §3 names **8183** as the lowest free 81xx (8180/8181/8182/8187/8188
  taken); probed free before use. 8445 is not allocated to any other repo in the registry
  (registry lists 8443 gateway, 8444 plantpal-frontend) — it was already atlas's own and is kept,
  now loopback-bound.
- `docker compose config -q` valid. Container recreated: `docker ps` →
  `127.0.0.1:8183->80/tcp, 127.0.0.1:8445->443/tcp`; `curl 127.0.0.1:8183` → 301 (atlas HTTP→HTTPS
  redirect); `curl 127.0.0.1:8182` → connection refused (port released).

## Not verified here / follow-ups
- Criterion 2 (tutor starting and serving on :8182 / tutor.platform.localhost) was not exercised:
  starting tutor is outside this repo's session. With plantpal up, 8182 is now unbound, so nothing
  in plantpal blocks it — the launcher should confirm on its side.
- Vault registry should record plantpal-frontend-atlas at **8183 (+8445 https)** — needs a
  `to: [platform-vault]` update (not raised by this session).
- Siblings `backend` (8180) and `frontend` (8181/8444) still publish on 0.0.0.0; out of this
  demand's scope (the D040-reach question is open per PLATFORM_STATE.md).
- Change is on branch `bugfix/PP-110-atlas-loopback-port`, pending PR into `dev`.
