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
  pin: v0.7.0
  binding: java
  used:
    - app.health
    - app.manifest
    - ai.request
    - ai.response
    - dimension.event
    - state.event
---

# plantpal — Hexagon Descriptor

| Field | Value |
|---|---|
| Side | host |
| Type | app hexagon |
| Class | low-stakes (D010) |
| Decisions | D006 (owner-first; PlantPal is tenant zero), D009 (loose coupling — PlantPal is standalone and pre-existing, runs with the platform entirely absent), D010 (app classes — low-stakes posture), D022 (all AI calls eventually flow through ai-gateway — not yet wired, see below), D024 (business-dimension metering — `plant_count` reported to Treasury via `dimension.event`), D027 (self-declared tiering; plant count as a ceiling — enforcement lives in Treasury, not here) |
| Contracts pinned | `contracts` v0.5.1 (Java binding); authoritative source: `backend/pom.xml` |
| Status | active — PlantPal is a real, already-deployed app (Railway + Vercel), unlike a Room scaffolded from nothing |

> **Chunk 3 note:** additive gateway swap, gated by the **`platform` Spring profile** (not a flag inside a shared profile — see the platform-profile-split delta). `platform.gateway.*` lives only in `application-platform.yml`; `application.yml`/`application-dev.yml` carry none of it, so the default/standalone boot never reads a `platform.*` key at all, and `GatewayProperties` binds `enabled=false` via `@DefaultValue` when the prefix is absent. New `com.plantpal.gateway.GatewayClient` port routes the in-scope AI calls through `ai-gateway`'s `ai.request`/`ai.response` when the `platform` profile is active (`platform.gateway.enabled` defaults `true` there); every call site keeps its pre-existing direct-client path unchanged otherwise (which is always, in prod — Railway never activates the `platform` profile, and `ai-gateway` is never publicly exposed). **Not** a full hexagonal refactor: `VisionModelPreference.GITHUB_GPT4O`/`GITHUB_GPT41` (ai-gateway's `OpenAiAdapter` is text-only — routing them would silently drop the photo) and `PlantNetDiseaseClient`/PlantNet's non-identify endpoints stay direct always, profile or no profile. Full in-scope-vs-always-direct list: `PROGRESS.md`.

> **Dimension-event note (this chunk):** `PlantServiceImpl` now emits a `dimension.event` (`plant_count`, delta `+1`/`-1`) to the `dimension.events` Kafka topic on plant create and archive, for Treasury's business-dimension metering (D024/D027). Always-on, no feature flag — unlike the gateway swap, this isn't provider-facing and has no direct-path fallback to preserve.

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
| ai-gateway (`com.plantpal.gateway.GatewayClient`, `POST /ai/request`) | ai-gateway | `ai.request`/`ai.response` | sync, **gated by the `platform` Spring profile** (`application-platform.yml`, never active in prod) — the in-scope calls only, see PROGRESS.md |
| Kafka producer (`dimension.event`, topic `dimension.events`) | Treasury (consumer) | `dimension.event` | async, always-on — `plant_count` delta `+1` on `PlantServiceImpl.createPlant()`, `-1` on `archivePlant()` |
| (existing, not platform-facing) | Postgres, Redis, Kafka | none — PlantPal's own datastore/cache/async pipeline | — |
| (existing, not platform-facing, always direct regardless of flag) | Ollama (vision) / GitHub Models (`GITHUB_GPT4O`/`GITHUB_GPT41` vision, `PLANTNET`'s non-identify endpoints) / PlantNetDiseaseClient | none — direct provider calls | sync/async |

## Dependencies

- `contracts` (pinned `v0.5.1`, Java binding; authoritative source: `backend/pom.xml`) — `app.health`, `app.manifest`, `ai.request`/`ai.response` (Chunk 3), and `dimension.event` (plant_count metering).
- `io.platform:contracts:0.5.1` declared in `backend/pom.xml`; local `mvn install` of the `contracts` checkout required before building on the host, or the `contracts-m2` Docker build context for image builds (see DEPLOYMENT.md).

## Key invariants

- **User auth stays PlantPal's own** (JWT, `SecurityConfig`/`AuthController`, as-is) — platform identity, when it arrives, is attribution-only (D009). The platform never issues or validates PlantPal's tokens.
- **Its database stays its own.** No platform tables inside PlantPal's Postgres schema; Postgres/Redis/Kafka are pre-existing infrastructure PlantPal already ran before joining the platform, not platform-provisioned.
- **Its deploy stays independent** — Railway (backend) + Vercel (frontend), on its own cadence and its own CI (`.github/workflows/deploy.yml`); the platform observes via `app.health`, it does not gate PlantPal's releases.
- **No platform-awareness in domain logic** — the integration delta lives entirely in adapters/config (this chunk: root-level files only). If a domain class (e.g. `com.plantpal.plant.*`, `com.plantpal.identification.*`) ever imports a platform concept, that is out of bounds per `spec-plantpal-room.md` §3.
- **Launch is not blocked** — this Room's self-describing files and the future gateway swap are additive; none of PlantPal's own roadmap (Phase 9.5 → 10 → launch) waits on the platform.
