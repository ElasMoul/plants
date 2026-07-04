---
functionalName: plantpal
kind: app
side: host
status: active
class: low-stakes
spec: spec-plantpal-room.md
decisions:
  - D006
  - D009
  - D010
  - D022
  - D027
deps:
  - contracts
infra:
  - postgres
  - redis
  - kafka
contracts:
  pin: v0.3.0
  binding: java
  used:
    - app.health
    - app.manifest
---

# plantpal — Hexagon Descriptor

| Field | Value |
|---|---|
| Side | host |
| Type | app hexagon |
| Class | low-stakes (D010) |
| Decisions | D006 (owner-first; PlantPal is tenant zero), D009 (loose coupling — PlantPal is standalone and pre-existing, runs with the platform entirely absent), D010 (app classes — low-stakes posture), D022 (all AI calls eventually flow through ai-gateway — not yet wired, see below), D027 (self-declared tiering; plant count as a ceiling — enforcement lives in Treasury, not here) |
| Contracts pinned | `contracts` v0.3.0 (Java binding) |
| Status | active — PlantPal is a real, already-deployed app (Railway + Vercel), unlike a Room scaffolded from nothing |

> **Chunk 0 note:** this is the clone + Room-files chunk only (per `spec-plantpal-room.md` §6, Phase A). No application code changed. The gateway swap (`ai-gateway` dependency, `ai.request`/`ai.response` in `contracts.used`) is Chunk 3 — PlantPal still calls its AI providers (Ollama, GitHub Models, PlantNet, optional Anthropic) directly today, per `backend/.env.example`.

## Inbound ports (driving)

| Port | Contract | Sync/async |
|---|---|---|
| `GET /actuator/health` | `app.health` | sync |
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | none (PlantPal's own JWT issuance) | sync |
| `/api/v1/{plants,species,identifications,care,treatment-plans,reminders,dashboard,notifications,chat,plantnet,users}/**` | none (app-internal REST API, JWT-authenticated) | sync |

## Outbound ports (driven)

| Port | Callee | Contract | Sync/async |
|---|---|---|---|
| Registration | control-plane | `app.manifest` | static record this phase (`app-manifest.yaml`) — see below |
| (existing, not platform-facing) | Postgres, Redis, Kafka | none — PlantPal's own datastore/cache/async pipeline | — |
| (existing, not platform-facing) | Ollama / GitHub Models / PlantNet / Anthropic | none — direct provider calls, pre-gateway | sync/async |

## Dependencies

- `contracts` (pinned `v0.3.0`, Java binding) — `app.health` and `app.manifest` only, this chunk.
- No `ai-gateway` dependency yet. That lands in Chunk 3 (the gateway swap), per `spec-plantpal-room.md` §2-1 and §6.

## Key invariants

- **User auth stays PlantPal's own** (JWT, `SecurityConfig`/`AuthController`, as-is) — platform identity, when it arrives, is attribution-only (D009). The platform never issues or validates PlantPal's tokens.
- **Its database stays its own.** No platform tables inside PlantPal's Postgres schema; Postgres/Redis/Kafka are pre-existing infrastructure PlantPal already ran before joining the platform, not platform-provisioned.
- **Its deploy stays independent** — Railway (backend) + Vercel (frontend), on its own cadence and its own CI (`.github/workflows/deploy.yml`); the platform observes via `app.health`, it does not gate PlantPal's releases.
- **No platform-awareness in domain logic** — the integration delta lives entirely in adapters/config (this chunk: root-level files only). If a domain class (e.g. `com.plantpal.plant.*`, `com.plantpal.identification.*`) ever imports a platform concept, that is out of bounds per `spec-plantpal-room.md` §3.
- **Launch is not blocked** — this Room's self-describing files and the future gateway swap are additive; none of PlantPal's own roadmap (Phase 9.5 → 10 → launch) waits on the platform.
