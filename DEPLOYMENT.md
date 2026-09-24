# plantpal — Deployment

PlantPal is a pre-existing, already-deployed app — this document describes its
real deployment, not a platform-provisioned one (D009: PlantPal keeps its own
deploy, on its own cadence; the platform observes via `app.health`).

## Production

- **Backend** — Spring Boot 3.2 JAR on an **OVH VPS** (replaced Railway
  2026-09-24): Docker Compose under `/opt/plantpal` runs Postgres 15, Redis 7,
  the backend (runtime-only image, `deploy/vps/backend/Dockerfile`) and **Caddy**,
  which terminates TLS for `https://api.plants.moulworks.com` with automatic
  Let's Encrypt certificates. Only ports 22/80/443 are open.
- **Frontend** — classic + atlas Angular builds on **Vercel**; each project's
  `/api/*` and `/photos/*` are rewritten to `BACKEND_PUBLIC_URL`.
- **CI/CD** — `.github/workflows/deploy.yml` on push to `main`: builds the JAR +
  both `dist/`s, then `scp`s `deploy/vps/{docker-compose.yml,Caddyfile,backend/Dockerfile}`
  and the JAR to the VPS as user `deploy` and runs `docker compose up -d --build`,
  failing the job unless the backend container turns `healthy`. Pushing `main`
  is a production deploy.
- **Database** — PostgreSQL 15 (compose volume `plantpal_postgres-data`), schema
  owned by **Liquibase**. Nightly `pg_dump` at 03:30 to `/opt/plantpal/backups`
  (14 days kept, cron in `/etc/cron.d/plantpal-backup`).
- **Cache / rate limiting** — Redis (password-protected, AOF persistence).
- **Photos** — Cloudinary (`STORAGE_TYPE=cloudinary`); `local` also works now
  that the `photos` volume survives redeploys.
- **Async pipeline** — production runs WITHOUT Kafka (owner decision 2026-07-15):
  `app.identification.transport=in-process` in the prod profile.

### First-time VPS setup (runbook)

1. **DNS** — add an `A` record `api.plants.moulworks.com` → the VPS IPv4 (and
   `AAAA` → IPv6 if you want it). Caddy cannot get a certificate until it resolves.
2. **Your SSH key on the VPS** (from your PC):
   `ssh-copy-id <ovh-user>@<vps-ip>` (OVH images log in as `ubuntu` or `debian`).
3. **Bootstrap** — copy and run the script:
   `scp deploy/vps/bootstrap.sh <ovh-user>@<vps-ip>:` then on the VPS
   `sudo bash bootstrap.sh`. It installs Docker, updates, 2G swap, `ufw`,
   fail2ban, the `deploy` user, `/opt/plantpal`, the backup cron, and switches
   SSH to key-only (only if your key is already installed).
4. **CI deploy key** (on your PC): `ssh-keygen -t ed25519 -f plantpal-deploy -N "" -C plantpal-ci`;
   append `plantpal-deploy.pub` to `/home/deploy/.ssh/authorized_keys` on the VPS.
5. **Server `.env`** — create `/opt/plantpal/.env` (owner `deploy`, `chmod 600`)
   from `deploy/vps/.env.example`: fresh `openssl rand -hex 32` passwords, a
   new `JWT_SECRET`, the **same** VAPID pair the frontends were built with, and
   the Anthropic / PlantNet / Cloudinary values.
6. **GitHub** — secrets `VPS_HOST` (IP), `VPS_SSH_KEY` (contents of the private
   `plantpal-deploy` file), `VPS_KNOWN_HOSTS` (output of `ssh-keyscan <vps-ip>`);
   variable `BACKEND_PUBLIC_URL=https://api.plants.moulworks.com`. `RAILWAY_TOKEN`
   is no longer used. Existing `VERCEL_*`, `VAPID_PUBLIC_KEY`, `SENTRY_DSN`,
   `CONTRACTS_READ_TOKEN` stay.
7. **Deploy** — merge to `main` (or re-run the Deploy workflow). The first boot
   runs every migration on the empty database; the job waits up to 4 minutes.
8. **Verify** — `curl https://api.plants.moulworks.com/actuator/health` = UP;
   register/login on `https://plants.moulworks.com`; one full identification.
9. **Decommission Railway** once verified (delete the project; nothing deploys there).

### Operating the VPS

All from `/opt/plantpal` as `deploy`:
- Logs: `docker compose logs -f --tail 200 backend`
- Restart after editing `.env`: `docker compose up -d backend`
- Roll back the JAR: `cp backend/app.jar.prev backend/app.jar && docker compose up -d --build backend`
- Restore a backup: `gunzip -c backups/<file>.sql.gz | docker compose exec -T postgres psql -U plantpal plantpal`
  (into an empty database)
- Copy backups off the box regularly — they live on the same disk as the data.

### Frontends on Vercel

4. **Atlas frontend (second Vercel project)** — the atlas is a whole second app
   (its own Vercel project, its own domain, the same backend). Live setup:
   project `plants-atlas`, domain `https://plants-atlas.moulworks.com`, beside
   the classic `plants-qvj8` / `https://plants.moulworks.com`.
   1. In the atlas Vercel project → Settings: **Root Directory empty** and
      **Ignored Build Step = `exit 0`**. Both matter — deploy.yml uploads a
      PREBUILT `dist` and a non-empty Root Directory is appended to the upload
      path (the "provided path does not exist" failure), while any framework
      auto-detect re-runs `ng build` inside the upload and exits 127.
   2. `cd frontend && vercel link --project plants-atlas`, then read
      `.vercel/project.json` → `projectId`. Set it as repo **secret**
      `VERCEL_PROJECT_ID_ATLAS` (`orgId` is the already-set `VERCEL_ORG_ID`).
      Do this BEFORE the next step: `DEPLOY_ATLAS=true` with no project id
      makes the deploy-atlas job fail the whole Deploy run.
   3. Repo **variables**: `DEPLOY_ATLAS=true` (the job's gate),
      `ATLAS_PUBLIC_URL` and `CLASSIC_PUBLIC_URL` — deploy.yml seds these into
      both apps' `environment.prod.ts`, which is what makes the login page's
      "Continue into the Atlas" checkbox and the atlas's "Open in PlantPal"
      links point at each other instead of at localhost.
   4. Add BOTH domains to the backend's `CORS_ALLOWED_ORIGINS` in the VPS
      `/opt/plantpal/.env` (comma-separated, no spaces) and run
      `docker compose up -d backend` — Vercel's
      rewrite proxy forwards the browser's Origin header, so Spring sees each
      frontend's own domain and 403s an unlisted one.
   5. DNS for the subdomain: add the CNAME Vercel shows for
      `plants-atlas.moulworks.com` at the registrar, same as the classic one.
   Note `VAPID_PUBLIC_KEY` is baked into BOTH apps (each carries its own push
   subscribe); unset, push is refused in words rather than breaking.
   **If a NEW Vercel project deploys with `Error: Project not found` while the
   older one still deploys fine, suspect the token, not the ids.** A Vercel
   token can keep working for projects it already knew while failing to resolve
   an account at all — the tell is `vercel whoami --token=...` answering
   `Error: User not found.` Ids and org can be provably correct (compare a
   sha256 of the stored secret against the dashboard's Project ID) and the
   deploy still fails. Fix: issue a fresh token scoped to the account/team that
   owns the projects and update `VERCEL_TOKEN`; `whoami` naming the user and
   `vercel project ls` listing the project are the proof it is healthy.
5. **Photo storage (Cloudinary)** — create a free Cloudinary account, copy the
   **API Environment variable** (`cloudinary://api_key:api_secret@cloud_name`) and set
   `STORAGE_TYPE=cloudinary` + `CLOUDINARY_URL=<that value>` in the VPS `.env`.
   Same `/photos/{uuid}.{ext}` URL contract and Redis cache as local mode.

## Local (Docker Compose)

`docker-compose.yml` at repo root brings up the full stack: Postgres, Redis,
Zookeeper, Kafka, backend, frontend (Nginx).

### Prerequisites

- Java 21, Maven (backend)
- Node 20 (frontend — Angular 20)
- Docker + Docker Compose (full local stack)
- `contracts` checked out and `mvn install`-ed locally at the version pinned in
  `backend/pom.xml` (authoritative source — currently `0.17.0`; do not trust the
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

**Production (OVH VPS) runs with no profile override beyond its own — `platform`
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
the pom before trusting any version number written here (currently `0.17.0`).
Before building on the host against the pinned version:

```bash
git -C ../contracts checkout v0.17.0
mvn install -f ../contracts/gen/java/pom.xml
```

`backend/pom.xml` depends on `io.platform:contracts:0.17.0` for the
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
COPY --from=contracts-m2 . /root/.m2/repository/io/platform/contracts/0.17.0/
```

Before building the image, run the host-side `mvn install` above once (populates
`~/.m2/repository/io/platform/contracts/0.17.0/`), then supply that path as the
`contracts-m2` context:

```bash
docker build \
  --build-context contracts-m2=$HOME/.m2/repository/io/platform/contracts/0.17.0 \
  -t plantpal-backend ./backend
```

Or via Compose (`docker-compose.yml`'s `backend.build.additional_contexts`,
already wired to read the `CONTRACTS_M2_0_17_0` env var):

```bash
export CONTRACTS_M2_0_17_0=$HOME/.m2/repository/io/platform/contracts/0.17.0   # bash
# $env:CONTRACTS_M2_0_17_0 = "$HOME\.m2\repository\io\platform\contracts\0.17.0"  # PowerShell
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
