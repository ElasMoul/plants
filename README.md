# 🌱 PlantPal

> Photograph a plant → AI identifies species & detects health issues → personalized care schedule with push reminders → chat assistant for ongoing questions.

Built by a duo as a real-world, daily-use application — architected from day one for thousands of users.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21 · Spring Boot 3.2 · Spring Security 6 · Spring Data JPA |
| Frontend | Angular 16+ · TypeScript (strict) · @angular/pwa |
| Database | PostgreSQL 15 · Liquibase migrations |
| Cache | Redis (Spring Data Redis) |
| Messaging | Kafka (async identification pipeline; also emits platform `dimension.event`) |
| Auth | JWT stateless (JJWT 0.12) |
| AI | Multi-provider — see [Provider Map](#ai-provider-map) below |
| Rate limiting | Bucket4j (per-user AI call limits, `Bandwidth.builder()`) |
| Push notifications | Web Push VAPID |
| Build | Maven |
| Tests | JUnit 5 · Mockito · AssertJ · Testcontainers |
| CI/CD | GitHub Actions — `ci.yml`, `secret-scan.yml` (gitleaks), `nightly-evals.yml`; `deploy.yml` present but disabled in the GitHub UI pending owner |
| Local infra | Docker · docker-compose (PostgreSQL + Redis + Kafka + Zookeeper) |
| Deploy | Railway (backend) · Vercel (frontend) |

### AI provider map

PlantPal routes AI calls to five providers depending on task and user preference
(full detail: `.claude/CLAUDE.md`'s "Provider Map" section):

| Provider | Model | Purpose |
|---|---|---|
| GitHub Models | gpt-4o | Photo identification + health + care plan (vision) |
| GitHub Models | gpt-4.1 | Alternate identification model |
| GitHub Models | gpt-4o-mini | Visual annotation (polygon regions) |
| DeepSeek (via GitHub Models / Azure endpoint) | DeepSeek-R1, o4-mini, gpt-4.1-mini | Care plan text, cure advice, disease description, species enrichment |
| Ollama | gemma3:4b | Local vision + reasoning fallback |
| Anthropic | claude-sonnet-4-6 | Vision AND reasoning (multimodal); optional — gated on `ANTHROPIC_API_KEY` being set |

---

## Repository Structure

```
plantpal/
├── backend/src/main/java/com/plantpal/
│   ├── PlantPalApplication.java
│   ├── plant/              # Plant CRUD, Redis cache, plant_count dimension events
│   ├── identification/     # AI identification pipeline (Kafka async), visual annotation,
│   │                       # care plans, action plans, species/plant matching
│   ├── reminder/           # Reminders, care logs, web push, TreatmentPlan
│   ├── treatment/          # Treatment entity — per-disease lifecycle
│   ├── species/            # Species entity — shared botanical knowledge across users
│   ├── chat/                # AI chat assistant (SSE streaming)
│   ├── dashboard/          # Home page aggregate endpoint
│   ├── user/               # Auth, JWT, user preferences (incl. AI model choice)
│   └── shared/             # Security, caching, storage, exceptions, correlation IDs
├── backend/src/main/resources/db/changelog/   # 19 Liquibase migrations (001–019)
├── backend/.env.example                        # Required environment variables template
├── backend/Dockerfile
├── frontend/                                    # Angular PWA
├── docker-compose.yml                           # Local dev: Postgres + Redis + Kafka + Zookeeper
├── .github/workflows/                           # ci, secret-scan, nightly-evals, deploy (disabled)
├── .claude/                                     # Agent memory (see below)
├── HEXAGON.md · DEPLOYMENT.md · CHANGELOG.md    # Platform self-describing files
└── README.md
```

### `.claude/` file locations

PlantPal is developed with Claude Code as a standing collaborator. The durable
project knowledge lives in `.claude/`, not scattered across ad hoc docs:

| File | Purpose |
|---|---|
| `.claude/CLAUDE.md` | Architecture, conventions, current build state — read automatically every session. Also carries the platform-integration notice at its top (this repo doubles as the `platform/plantpal` Room). |
| `.claude/AGENTS.md` | Agent roles/workflow for this repo |
| `.claude/ARCHITECT.md` | Deeper architectural patterns (Kafka async pipeline, Redis photo storage, Species/Treatment domain model) |
| `.claude/BACKEND.md` / `.claude/FRONTEND.md` | File-by-file inventories for each side |
| `.claude/STATE.md` | Session-by-session history |
| `.claude/TASK_PLAN.md` | Full build-task list with prompts |
| `.claude/VAULT_SYNC_TEMPLATE.md` | End-of-phase sync steps into `../plants-vault` |
| `.claude/commands/`, `.claude/design pages/`, `.claude/Archive/` | Slash commands, design references, archived snapshots |

Project history/decisions beyond `.claude/` live in the knowledge vault at
`../plants-vault` (see `.claude/CLAUDE.md`'s "Knowledge Vault" section for the
read order — `wiki/hot.md` first).

---

## Prerequisites

- **Java 21**
- **Node.js 18+** & npm
- **Maven 3.9+**
- **Docker & docker-compose**
- At least one configured AI provider (see [Provider Map](#ai-provider-map)) —
  GitHub Models token, DeepSeek/Azure endpoint access, a local Ollama install,
  and/or an Anthropic API key (optional, unlocks the Claude options)
- **VAPID keys** for push notifications:
  ```bash
  npx web-push generate-vapid-keys
  ```

---

## Getting Started

### 1. Clone the repo

```bash
git clone https://github.com/ElasMoul/plants.git plantpal
cd plantpal
```

### 2. Start local infrastructure

```bash
docker-compose up -d
# Starts PostgreSQL (:5432), Redis (:6379), Zookeeper, Kafka (:29092)
```

### 3. Configure the backend

```bash
cp backend/.env.example backend/.env
# Fill in: DB_USERNAME/DB_PASSWORD, JWT_SECRET, GITHUB_TOKEN, DEEPSEEK_MODEL,
# OLLAMA_BASE_URL/OLLAMA_MODEL, PLANTNET_API_KEY, VAPID keys.
# ANTHROPIC_API_KEY is optional — leave blank to disable the Claude options.
```

### 4. Run the backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# API available at http://localhost:8080
# Swagger UI at http://localhost:8080/swagger-ui.html
```

### 5. Run the frontend

```bash
cd frontend
npm install
ng serve --proxy-config proxy.conf.json
# App available at http://localhost:4200
```

---

## MVP Features — shipped

| # | Feature | Status |
|---|---|---|
| 1 | **AI Plant ID** — photo upload → multi-provider vision → species + health diagnosis + care tips | ✅ |
| 2 | **Plant profiles** — create, edit, view, archive plants with full care history | ✅ |
| 3 | **Care reminders** — watering, fertilizing, repotting + PWA push notifications | ✅ |
| 4 | **Care log** — record completed actions, update reminder due dates | ✅ |
| 5 | **AI chat assistant** — contextual Q&A using the user's actual garden as context, SSE streaming | ✅ |

Beyond MVP: a species-centric domain (shared botanical knowledge, per-disease
`Treatment` lifecycle), PlantNet as a first-class identification provider,
per-user AI model selection, and platform integration (see below). Full
phase-by-phase build status: `.claude/CLAUDE.md`'s "Current Build Status" table.

---

## Key Architectural Decisions

- **Modular monolith** — clean module boundaries today; each module can be extracted to a microservice later.
- **Stateless JWT auth** — scales horizontally without server-side sessions.
- **Redis cache from day one** — works with horizontal scaling; in-memory cache doesn't.
- **Async AI calls** (`@Async("aiTaskExecutor")` + `CompletableFuture`) — prevents HTTP thread pool exhaustion on 5–15s external calls.
- **Bucket4j rate limiting** on all AI endpoints — protects provider spend.
- **Soft deletes everywhere** (`status = ARCHIVED`) — no accidental data loss.
- **Pageable on all list endpoints** — no list endpoint ever returns unbounded results.
- **Testcontainers** for integration tests — real PostgreSQL/Redis, not H2 mocks.
- **Kafka dimension events emitted only after commit** (`@TransactionalEventListener(AFTER_COMMIT)`) — a rolled-back transaction never leaks a phantom `plant_count` delta to the platform's Treasury metering.

---

## Running Tests

```bash
# Unit tests
cd backend && mvn test

# Integration tests (requires Docker, Testcontainers)
cd backend && mvn verify

# Coverage report
cd backend && mvn jacoco:report
# Report at: backend/target/site/jacoco/index.html
```

---

## Branch Strategy

`main` is the working branch — pushed directly, protected by CI (`ci.yml` +
`secret-scan.yml`). There is no separate long-lived `dev` integration branch
(dev was consolidated into `main`, see commit `dea0d56`). Feature work happens
on short-lived `feature/*`/`fix/*` branches merged back to `main` via PR.

Commit message format: `<type>(<scope>): <description>`
Types: `feat` · `fix` · `docs` · `style` · `refactor` · `test` · `chore`

---

## Environment Variables

See [`backend/.env.example`](backend/.env.example) for the full list.
Critical variables:

| Variable | Description |
|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | Postgres credentials |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection |
| `JWT_SECRET` | HMAC signing secret, min 64 chars — generate with `openssl rand -base64 64` |
| `GITHUB_TOKEN` / `GITHUB_BASE_URL` / `GITHUB_*_MODEL` | GitHub Models (vision identification/annotation, text) |
| `DEEPSEEK_MODEL` | Care-plan/cure-advice model (via GitHub Models endpoint) |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | Local AI fallback |
| `PLANTNET_API_KEY` | PlantNet species identification API |
| `ANTHROPIC_API_KEY` | Optional — from console.anthropic.com; blank disables the Claude provider options |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | Generated with `npx web-push generate-vapid-keys` |
| `KAFKA_BOOTSTRAP_SERVERS` | Async identification pipeline + platform dimension events |

Platform-integration-only variables (read only when the `platform` Spring
profile is active — see `DEPLOYMENT.md`): `PLATFORM_GATEWAY_ENABLED`,
`PLATFORM_GATEWAY_URL`.

---

## Deployment

| Target | Platform | Trigger |
|---|---|---|
| Backend | Railway | Push to `main` via GitHub Actions (`deploy.yml`, currently disabled in the GitHub UI pending owner) |
| Frontend | Vercel | Push to `main` via GitHub Actions (same gate) |

Full details, including the platform Docker build's `contracts-m2` build
context requirement: [`DEPLOYMENT.md`](DEPLOYMENT.md).

---

## Platform integration

PlantPal is also "tenant zero" of a separate, private meta-platform
(`../platform/plantpal`) — an additive, profile-gated integration delta
(gateway swap, `plant_count` business-dimension metering) documented in
[`HEXAGON.md`](HEXAGON.md) and [`DEPLOYMENT.md`](DEPLOYMENT.md). None of
PlantPal's own roadmap depends on it; see `.claude/CLAUDE.md`'s platform
integration notice at the top of that file for the precedence rules between
PlantPal's own conventions and the platform's standing orders.

---

## Contributing

This is a two-person project. See [`.claude/TASK_PLAN.md`](.claude/TASK_PLAN.md)
for the full build plan with exact prompts for Claude Code-assisted tasks, and
[`.claude/CLAUDE.md`](.claude/CLAUDE.md) for all code conventions and standards.

---

*PlantPal — built with 🌿 and enterprise-grade Java.*
