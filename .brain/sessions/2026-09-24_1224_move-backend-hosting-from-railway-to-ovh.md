---
session_id: 2026-09-24_1224_move-backend-hosting-from-railway-to-ovh
agent: plantpal
model: claude-opus-5-5
started: 2026-09-24T12:24:35+00:00
ended: 2026-09-24T12:37:46+00:00
task: "Move backend hosting from Railway to OVH VPS (compose + Caddy + SSH deploy)"
priority: 1
status: partial
launch: interactive
decisions:
  - id: D-2026-09-24-01
    text: "Owner: backend on OVH VPS at api.plants.moulworks.com, frontends stay on Vercel, fresh DB (no Railway data migration)"
    supersedes: null
changes:
  - "deploy/vps/: compose stack (postgres, redis, runtime backend, caddy TLS api.plants.moulworks.com), bootstrap.sh, .env.example"
  - "deploy.yml backend job: scp stack+JAR to VPS as deploy user, wait for container healthy; Railway files removed"
  - "DEPLOYMENT.md production section rewritten as VPS runbook; CHANGELOG entry"
lessons:
  - "Prod SecretConfigValidator rejects placeholder VAPID keys (must be 65/32-byte base64url) - smoke tests need a real-format pair (node crypto ECDH prime256v1)"
context_missing: []
notes_used: []
vault_sync: spec-plantpal deploy target changed Railway->OVH VPS; no demand raised yet
close: confirmed
---


## Log

**12:24 Session opened** via `brain session open`.

**12:37 Session closed via `brain session close` (status: partial).**
