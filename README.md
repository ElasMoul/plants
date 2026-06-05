# 🌱 PlantPal

> Photograph a plant → AI identifies species & detects health issues → personalized care schedule with push reminders → chat assistant for ongoing questions.

Built by a duo as a real-world, daily-use application — architected from day one for thousands of users.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17 · Spring Boot 3.2 · Spring Security 6 · Spring Data JPA |
| Frontend | Angular 16+ · TypeScript (strict) · @angular/pwa |
| Database | PostgreSQL 15 · Liquibase migrations |
| Cache | Redis (Spring Data Redis) |
| Auth | JWT stateless (JJWT 0.12) |
| AI | Anthropic Claude API (`claude-sonnet-4-20250514`) · anthropic-java 0.8.0 |
| Rate limiting | Bucket4j (per-user AI call limits) |
| Push notifications | Web Push VAPID |
| Build | Maven |
| Tests | JUnit 5 · Mockito · AssertJ · Testcontainers |
| CI/CD | GitHub Actions |
| Local infra | Docker · docker-compose (PostgreSQL + Redis) |
| Deploy | Railway (backend) · Vercel (frontend) |

---

## Repository Structure

```
plantpal/
├── backend/                        # Spring Boot modular monolith
│   ├── src/main/java/com/plantpal/
│   │   ├── shared/                 # Shared kernel: ApiResponse, exceptions, JWT, audit
│   │   ├── auth/                   # Registration, login, JWT
│   │   ├── plant/                  # Plant profiles, care history
│   │   ├── identification/         # AI plant ID + Claude Vision client
│   │   ├── reminder/               # Care reminders, scheduling
│   │   ├── carelog/                # Completed care actions
│   │   └── chat/                   # AI chat assistant, SSE streaming
│   ├── src/main/resources/
│   │   └── db/changelog/           # Liquibase SQL migrations
│   ├── .env.example                # Required environment variables template
│   └── pom.xml
├── frontend/                       # Angular PWA
├── docker-compose.yml              # Local dev: PostgreSQL + Redis
├── CLAUDE.md                       # Claude Code instructions & conventions
├── TASK_PLAN.md                    # 36-task build plan with AI prompts
└── README.md
```

---

## Prerequisites

- **Java 17+**
- **Node.js 18+** & npm
- **Maven 3.9+**
- **Docker & docker-compose**
- An **Anthropic API key** — [console.anthropic.com](https://console.anthropic.com)
- **VAPID keys** for push notifications:
  ```bash
  npx web-push generate-vapid-keys
  ```

---

## Getting Started

### 1. Clone the repo

```bash
git clone https://github.com/your-org/plantpal.git
cd plantpal
```

### 2. Start local infrastructure

```bash
docker-compose up -d
# Starts PostgreSQL on :5432 and Redis on :6379
```

### 3. Configure the backend

```bash
cp backend/.env.example backend/.env
# Fill in: DATABASE_URL, REDIS_HOST, JWT_SECRET, ANTHROPIC_API_KEY, VAPID keys
```

### 4. Run the backend

```bash
cd backend
mvn spring-boot:run
# API available at http://localhost:8080
```

### 5. Run the frontend

```bash
cd frontend
npm install
ng serve
# App available at http://localhost:4200
```

---

## MVP Features

| # | Feature | Status |
|---|---|---|
| 1 | **AI Plant ID** — photo upload → Claude Vision → species + health diagnosis + care tips | 🔲 |
| 2 | **Plant profiles** — create, edit, view, archive plants with full care history | 🔲 |
| 3 | **Care reminders** — watering, fertilizing, repotting + PWA push notifications | 🔲 |
| 4 | **Care log** — record completed actions, update reminder due dates | 🔲 |
| 5 | **AI chat assistant** — contextual Q&A using the user's actual garden as context | 🔲 |

---

## Build Phases

| Phase | Scope | Timeline |
|---|---|---|
| **0 — Project Setup** | Repo · CI/CD · Docker · Spring Boot skeleton · Angular skeleton | Days 1–4 |
| **1 — Auth + Plant CRUD** | Registration · JWT login · full plant management API + UI | Weeks 1–2 |
| **2 — AI Identification** | Photo upload · Claude Vision · identification results UI | Weeks 3–4 |
| **3 — Reminders + Push** | Care reminders · care log · PWA push notifications | Weeks 5–6 |
| **4 — AI Chat** | Chat assistant with garden context · SSE streaming | Weeks 7–8 |
| **5 — Launch** | Beta testing · performance · prod deploy · v1.0.0 | Weeks 9–10 |

---

## Key Architectural Decisions

- **Modular monolith** — clean module boundaries today; each module can be extracted to a microservice later.
- **Stateless JWT auth** — scales horizontally without server-side sessions.
- **Redis cache from day one** — works with horizontal scaling; in-memory cache doesn't.
- **Async Claude API calls** (`CompletableFuture`) — prevents HTTP thread pool exhaustion.
- **Bucket4j rate limiting** on all AI endpoints — protects the Anthropic bill.
- **Soft deletes everywhere** (`status = ARCHIVED`) — no accidental data loss.
- **Pageable on all list endpoints** — no list endpoint ever returns unbounded results.
- **Testcontainers** for integration tests — real PostgreSQL/Redis, not H2 mocks.
- **Raw Claude response stored as JSONB** — enables debugging and future reprocessing.
- **JaCoCo 80% coverage gate** enforced in CI.

---

## Running Tests

```bash
# Unit tests
cd backend && mvn test

# Integration tests (requires Docker)
cd backend && mvn verify

# Coverage report
cd backend && mvn jacoco:report
# Report at: backend/target/site/jacoco/index.html
```

---

## Branch Strategy

| Branch | Purpose |
|---|---|
| `main` | Production-ready — protected, requires PR + review |
| `dev` | Integration branch — all features merge here first |
| `feature/*` | Individual feature branches off `dev` |
| `fix/*` | Bug fix branches |

Commit message format: `<type>(<scope>): <description>`
Types: `feat` · `fix` · `docs` · `style` · `refactor` · `test` · `chore`

---

## Environment Variables

See [`backend/.env.example`](backend/.env.example) for the full list.
Critical variables:

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL connection string |
| `REDIS_HOST` | Redis host |
| `JWT_SECRET` | HS512 signing secret (min 64 chars) |
| `ANTHROPIC_API_KEY` | From console.anthropic.com |
| `VAPID_PUBLIC_KEY` | Generated with `npx web-push generate-vapid-keys` |
| `VAPID_PRIVATE_KEY` | Generated with `npx web-push generate-vapid-keys` |

---

## Deployment

| Target | Platform | Trigger |
|---|---|---|
| Backend | Railway | Push to `main` via GitHub Actions |
| Frontend | Vercel | Push to `main` via GitHub Actions |

---

## Contributing

This is a two-person project. See [`TASK_PLAN.md`](TASK_PLAN.md) for the full 36-task build plan with exact prompts for Claude Code-assisted tasks, and [`CLAUDE.md`](CLAUDE.md) for all code conventions and standards.

---

*PlantPal — built with 🌿 and enterprise-grade Java.*
