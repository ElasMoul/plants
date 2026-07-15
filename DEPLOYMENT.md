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
- **Async pipeline** — Kafka locally/dev; **production runs WITHOUT Kafka**
  (owner decision 2026-07-15, v1.0.0): `app.identification.transport=in-process`
  in the prod/staging profiles dispatches the identification pipeline on the
  in-app async executor instead of a broker. The 202+poll HTTP contract is
  unchanged. Revisit brokered async at scale.

### First-time production setup (T-DEPLOY.5 runbook)

1. **Railway** — create a project with two add-ons (PostgreSQL, Redis) and one
   service for the backend. The service builds via `backend/railway.json` →
   `backend/Dockerfile.railway` (runtime-only image; CI supplies the prebuilt
   JAR — the regular Dockerfile needs the contracts-m2 host context Railway
   doesn't have). Set the service env vars (see `backend/.env.example`,
   "Staging / Production" section): `SPRING_PROFILES_ACTIVE=prod`,
   `DATABASE_URL` (⚠️ Railway's native `postgres://` URI must be translated to
   JDBC form: `jdbc:postgresql://host:port/db?user=...&password=...`),
   `REDIS_URL`, `JWT_SECRET` (≥64 chars), `GITHUB_TOKEN` (rotated 2026-07-15),
   `VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY`, `CORS_ALLOWED_ORIGINS` (the Vercel
   domain, no trailing slash), optional `ANTHROPIC_API_KEY`/`PLANTNET_API_KEY`.
   Do NOT set `KAFKA_BOOTSTRAP_SERVERS` — prod is in-process by design.
2. **Vercel** — create the project once (`vercel link` locally or via
   dashboard); no build settings needed (CI deploys a prebuilt `dist/`).
3. **GitHub repo secrets** — `RAILWAY_TOKEN`, `VERCEL_TOKEN`, `VERCEL_ORG_ID`,
   `VERCEL_PROJECT_ID`, `VAPID_PUBLIC_KEY`, `SENTRY_DSN` (last two are baked
   into the frontend build); repo **variable** `BACKEND_PUBLIC_URL` = the
   Railway service's public URL (deploy.yml writes the Vercel `/api/*` rewrite
   from it and fails fast if unset).
4. **Deploy** — push to `main`; `.github/workflows/deploy.yml` builds and
   deploys both sides.
5. **Verify** — `GET <railway>/actuator/health` = UP; register/login on the
   Vercel domain; run one full identification (photo → 202 → poll → result)
   to prove the in-process pipeline; watch Railway logs (structured JSON with
   correlationId) while doing it.

## Local (Docker Compose)

`docker-compose.yml` at repo root brings up the full stack: Postgres, Redis,
Zookeeper, Kafka, backend, frontend (Nginx).

### Prerequisites

- Java 21, Maven (backend)
- Node 18 (frontend)
- Docker + Docker Compose (full local stack)
- `contracts` checked out and `mvn install`-ed locally at the version pinned in
  `backend/pom.xml` (authoritative source — currently `0.7.0`; do not trust the
  version number in this doc, it drifts)

### Self-signed dev TLS certs (`frontend/nginx/certs/`, per machine — not in git)

`frontend/Dockerfile` does `COPY nginx/certs /etc/nginx/certs`, so the pair must
exist before building the frontend image. It is gitignored (per-machine dev
artifact); generate it once per machine:

```bash
cd frontend/nginx/certs
openssl req -x509 -newkey rsa:2048 -keyout self.key -out self.crt -days 365 \
  -nodes -subj "/CN=localhost"
```

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

Per the owner's ruling (`spec-plantpal.md` §2-1, §5-2): the gateway swap
(Chunk 3, shipped) is **profile-gated**, and as of the platform-profile split it's
gated by a **Spring profile**, not a flag flipped inside a shared profile.
`platform.gateway.*` no longer appears in `application.yml` or
`application-dev.yml` at all — it lives entirely in `application-platform.yml`,
which only takes effect when the `platform` profile is active
(`SPRING_PROFILES_ACTIVE=dev,platform` locally). `GatewayProperties` binds
`enabled=false` by `@DefaultValue` when that prefix is absent, so:

- **No profile / `dev` profile (the default)** — zero `platform.*` keys are read.
  The app boots and serves fully standalone, AI calls go straight to the direct
  provider clients, exactly as pre-platform (D009).
- **`platform` profile** — `platform.gateway.enabled` defaults to `true` (still
  overridable via `PLATFORM_GATEWAY_ENABLED`), and the in-scope AI calls route
  through `ai-gateway` at `platform.gateway.url`.

**Production (Railway) runs with no profile override beyond its own — `platform`
is never activated there, and `ai-gateway` is never publicly exposed, so prod
cannot reach it even if it wanted to.** All of the provider keys above remain
required in every environment: the gateway swap is additive (an
`if (platform.gateway.enabled)` branch at each in-scope call site), not a
replacement — the direct-client path and its keys are unchanged.

New env vars (`backend/.env`, only read when the `platform` profile is active):

| Variable | Required | Description |
|---|---|---|
| `PLATFORM_GATEWAY_ENABLED` | No | Overrides `platform.gateway.enabled` (default `true` when the `platform` profile is active; the key doesn't exist at all otherwise) |
| `PLATFORM_GATEWAY_URL` | No | ai-gateway base URL (default `http://localhost:8085`) |
| `PLATFORM_STATEFEED_ENABLED` | No | Overrides `platform.statefeed.enabled` (default `true` when the `platform` profile is active; the key doesn't exist at all otherwise) |
| `PLATFORM_STATEFEED_URL` | No | state-feed base URL (default `http://localhost:8080` — state-feed's dev host port per the platform port-block ruling, see `PROGRESS.md`'s 2026-07-08 entry: PlantPal's own backend was remapped 8080 → 8180 specifically because 8080 collided with state-feed) |

`com.plantpal.statefeed.StateFeedEmitter` is entirely fire-and-forget: `POST
{PLATFORM_STATEFEED_URL}/events` on a background executor, 2s connect / 5s read
timeouts, any failure logged at WARN and swallowed — PlantPal never blocks on or
cares about the state-feed's availability (spec-state-feed.md §3, read-only
mirror). Emits `app.status` once on startup and `activity.count`
(`identification.completed`) each time an identification finishes.

## Consuming `contracts`

Per D031, the Java binding has no package registry. The pin is **`backend/pom.xml`
is the authoritative source** — this doc only paraphrases it and can drift; check
the pom before trusting any version number written here (currently `0.7.0`).
Before building on the host against the pinned version:

```bash
git -C ../contracts checkout v0.7.0
mvn install -f ../contracts/gen/java/pom.xml
```

`backend/pom.xml` depends on `io.platform:contracts:0.7.0` for the
`ai.request`/`ai.response`/`ai.blocked` types used by
`com.plantpal.gateway.GatewayClient`, the `dimension.event`
(`DimensionEvent`) type used by `com.plantpal.plant.event.PlantCountDimensionEmitter`,
and the `state.event` (`AppStatusEvent`/`ActivityCountEvent`) types used by
`com.plantpal.statefeed.StateFeedEmitter`.

### Docker build — contracts supply

`backend/Dockerfile` cannot build the `contracts` Java codegen inside the image
(jsonschema2pojo NPEs deterministically under Docker — see the Dockerfile's own
comment) even though it builds reliably on the host. Instead the Dockerfile
expects a **named build context** called `contracts-m2` containing the
already-built jar + POM from the host's local `.m2`:

```
COPY --from=contracts-m2 . /root/.m2/repository/io/platform/contracts/0.7.0/
```

Before building the image, run the host-side `mvn install` above once (populates
`~/.m2/repository/io/platform/contracts/0.7.0/`), then supply that path as the
`contracts-m2` context:

```bash
docker build \
  --build-context contracts-m2=$HOME/.m2/repository/io/platform/contracts/0.7.0 \
  -t plantpal-backend ./backend
```

Or via Compose (`docker-compose.yml`'s `backend.build.additional_contexts`,
already wired to read the `CONTRACTS_M2_0_7_0` env var):

```bash
export CONTRACTS_M2_0_7_0=$HOME/.m2/repository/io/platform/contracts/0.7.0   # bash
# $env:CONTRACTS_M2_0_7_0 = "$HOME\.m2\repository\io\platform\contracts\0.7.0"  # PowerShell
docker compose build backend
```

If the pin in `backend/pom.xml` moves, update all three of: the `COPY --from`
path in `backend/Dockerfile`, the `additional_contexts` path in
`docker-compose.yml`'s comment/env var name, and the `mvn install` version above.

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
