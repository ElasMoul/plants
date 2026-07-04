# plantpal — Deployment

PlantPal is a pre-existing, already-deployed app — this document describes its
real deployment, not a platform-provisioned one (D009: PlantPal keeps its own
deploy, on its own cadence; the platform observes via `app.health`).

## Production

- **Backend** — Spring Boot 3.2 JAR, deployed to **Railway** (`railway up`).
- **Frontend** — Angular PWA production build, deployed to **Vercel**.
- **CI/CD** — `.github/workflows/deploy.yml`, triggered on push to `main`: builds
  backend JAR + frontend `dist/`, then deploys each independently. Requires
  `RAILWAY_TOKEN` (backend) and `VERCEL_TOKEN`/`VERCEL_ORG_ID`/`VERCEL_PROJECT_ID`
  (frontend) as GitHub repository secrets.
- **Database** — PostgreSQL 15, schema managed by **Liquibase** migrations
  (`backend/src/main/resources/db/changelog`), not owned or touched by the platform.
- **Cache / rate limiting** — Redis (Bucket4j).
- **Async pipeline** — Kafka (identification pipeline).

## Local (Docker Compose)

`docker-compose.yml` at repo root brings up the full stack: Postgres, Redis,
Zookeeper, Kafka, backend, frontend (Nginx).

### Prerequisites

- Java 21, Maven (backend)
- Node 18 (frontend)
- Docker + Docker Compose (full local stack)
- `contracts` checked out at `v0.3.0` alongside this repo (this chunk only wires
  `app.health`/`app.manifest` — no runtime dependency on `contracts` code yet)

### Environment variables (`backend/.env`, copy from `backend/.env.example`)

| Variable | Required | Description |
|---|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | Yes | Postgres credentials |
| `REDIS_HOST` / `REDIS_PORT` | Yes | Redis connection |
| `JWT_SECRET` | Yes | Min 64 chars — PlantPal's own auth, unrelated to platform identity |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | Yes (dev default) | Local AI — plant care planning & chat |
| `PLANTNET_API_KEY` | Yes | PlantNet species identification API |
| `GITHUB_TOKEN` / `GITHUB_BASE_URL` / `GITHUB_*_MODEL` | Yes | GitHub Models (vision identification/annotation, text) |
| `DEEPSEEK_MODEL` | Yes | Care-plan/cure-advice model (via GitHub Models endpoint) |
| `ANTHROPIC_API_KEY` / `ANTHROPIC_BASE_URL` / `ANTHROPIC_MODEL_*` | No | Claude vision/reasoning — blank disables Claude options |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | Async identification pipeline |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | Yes | Web push notifications |
| `CORS_ALLOWED_ORIGINS` | Yes | Frontend origin(s) |

**All of the above are held directly by PlantPal today** (Ollama, PlantNet, GitHub
Models, Anthropic) — these are provider keys PlantPal already owned before joining
the platform, unrelated to `ai-gateway`.

## AI provider keys — current state vs. the gateway swap

Per the owner's ruling (`spec-plantpal-room.md` §2-1, §5-2): the gateway swap
(Chunk 3, shipped) is **dev/local-only, profile-gated**. `platform.gateway.enabled`
defaults to `false` (`application.yml`) and is only set `true` in
`application-dev.yml`. **Production keeps calling providers directly with the
keys above — Railway's prod env simply never sets the flag, and `ai-gateway` is
never publicly exposed, so prod cannot reach it even if it wanted to.** All of
the provider keys above remain required in every environment: the gateway swap
is additive (an `if (platform.gateway.enabled)` branch at each in-scope call
site), not a replacement — the direct-client path and its keys are unchanged.

New env vars (`backend/.env`):

| Variable | Required | Description |
|---|---|---|
| `PLATFORM_GATEWAY_ENABLED` | No | Overrides `platform.gateway.enabled` (default `false`, `true` in dev profile) |
| `PLATFORM_GATEWAY_URL` | No | ai-gateway base URL (default `http://localhost:8085`) |

## Consuming `contracts`

Per D031, the Java binding has no package registry. Before building against a
pinned `contracts` version:

```bash
git -C ../contracts checkout v0.4.0
mvn install -f ../contracts/gen/java/pom.xml
```

Required as of Chunk 3: `backend/pom.xml` now depends on `io.platform:contracts:0.4.0`
for the `ai.request`/`ai.response`/`ai.blocked` Java types used by
`com.plantpal.gateway.GatewayClient`.

## Health check

`GET /actuator/health` — Spring Boot Actuator, public (`SecurityConfig` permits
it unauthenticated). Exposed endpoints: `health,info,metrics`
(`application.yml`).

## Security posture

- JWT-authenticated REST API — PlantPal's own auth, entirely unaffected by the
  platform (D009). `POST /api/v1/auth/register` and `POST /api/v1/auth/login`
  are the only unauthenticated write routes; `/actuator/health`,
  `/v3/api-docs/**`, `/swagger-ui/**`, and `/photos/**` are the other public
  reads.
- No platform-issued credentials are consumed or produced by PlantPal in this
  chunk.
