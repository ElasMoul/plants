# PlantPal — Task Plan [ARCHIVE — snapshot before Phase 10 / Phase DEPLOY rename]
# Archived: 2026-06-27. See live TASK_PLAN.md for current version.
# This is the original file as-of Phase 9.5 completion.

**Legend:**
- 👤 **Manual** — you do this yourself (external tools, config, real decisions)
- 🤖 **AI** — Claude Code generates the code entirely from the prompt provided
- 🤝 **Assisted** — you lead, Claude Code helps with specific parts
- 💡 **Suggestion** — architectural note worth understanding, not just following

**Branch format:** `feature/PP-{num}-{short-description}`
**Commit format:** `feat(scope): description` (Conventional Commits)

> Completed phases are kept as one-line-per-task status records — the original
> Claude Code prompts aren't needed once shipped (see STATE.md for what was
> actually built, ARCHITECT.md for durable patterns, git history for the rest).
> **Active phases carrying full prompt detail: Phase 8 (PlantNet), Phase 9
> (Quality & Hardening), Phase 10 (Launch).** Recommended execution order:
> **Phase 9 foundation (T9.1–T9.3, T9.5) first or interleaved with Phase 8**, so
> PlantNet's new UI is covered as it's built rather than retroactively; then the
> rest of 8 and 9; **Phase 10 (Launch) last.** 👤 The 8-vs-9 interleave is your call.

---

## ✅ COMPLETED PHASES (summary — see STATE.md / git history for detail)

### PHASE 0 — Project Setup ✅
GitHub repo, local infra (Docker Compose: Postgres 15 + Redis 7), Spring Boot +
Angular skeletons, GitHub Actions CI/CD, VAPID keys + secrets. (T0.1–T0.6)

### PHASE 1 — Auth + Plant Management ✅
Liquibase migrations, User module, Spring Security 6 + JWT, Plant CRUD (backend
+ Angular), unit + integration tests, Auth frontend. (T1.1–T1.8)

### PHASE 2 — AI Plant Identification ✅
Photo → AI identification pipeline, now fully async (Kafka, `POST /analyze` →
202 → poll). gpt-4o vision + DeepSeek-R1 text, polygon disease annotation +
cure-advice, Redis photo storage (SHA-256 dedup), image-dimension locking,
garden health dashboard. (T2.1–T2.11 incl. T2.A–T2.F)

### PHASE 3 — Reminders + Push Notifications ✅ (except T3.3)
Reminder CRUD + scheduler (one daily push per user), web-push (VAPID),
actionable care plans (ROUTINE reminders / multi-step TREATMENT plans with
Mermaid diagrams). (T3.1–T3.5)
- **T3.3 — Manual on-device testing 👤 🔲 NOT DONE** — push delivery, PWA
  installability, offline reading; needs a real phone. (Now folded into Phase 9 /
  Phase DEPLOY beta.)

### PHASE 4 — AI Chat Assistant ✅
Chat backend + Angular frontend, single-turn with plant-context injection
(`?plantId=`), SSE streaming + conversation history (shipped in the pre-Phase-5
cleanup pass). (T4.1–T4.3)

### Pre-Phase-5 cleanup pass ✅
`feature/PP-038-pre-phase5-cleanup` — closed every open gap in
BACKEND.md/FRONTEND.md/STATE.md from Phases 0–4/6 plus 3 live-test bugs.

### PHASE 6 — Species & Treatment Domain Restructure ✅ (2026-06-20)
Plant-centric → species-centric: shared `Species` entity, per-disease `Treatment`
entity (wraps `TreatmentPlan`), 5-item bottom nav + Home screen, species-first
Garden, redesigned Plant/Treatment pages (sticky header + icon bar), 3-path
identification flow. (T6.1–T6.14)

### PHASE 7 — AI Model Control, Batch Scanning, Multi-Treatment UX ✅ (2026-06-22)
- **T7.1** — split `AiModelPreference` → Vision/Reasoning; removed fallbacks; fixed handler; RateLimitException. (`PP-039`)
- **T7.2** — two-dropdown model picker, AiErrorService, "powered by" badges, /preferences. (`PP-040`)
- **T7.3** — multi-select batch scan (BatchScanService). (`PP-041`)
- **T7.4** — multi-treatment picker + disease-description poll fix. (`PP-041`)

### PHASE 8 — PlantNet as a First-Class Identification Provider ✅ COMPLETE
T8.0 (Claude provider + 5-option model menus), T8.1 (PlantNet v2 client), T8.2 (ranked species-match),
T8.3 (candidate confirm UI), T8.4 (organ tagging + geolocation flora), T8.5 (disease cross-check),
T8.6 (GBIF/POWO/IUCN enrichment), T8.7 (quota telemetry). (PP-042–049, migrations 023–026)

### PHASE 8.5 — Identification Pipeline Resilience ✅ COMPLETE
T8.A (per-stage status D5, migration 027), T8.B (non-fatal annotation/candidate stages),
T8.C (predictedOrgans fix), T8.D (token-budget backpressure + annotation routing docs),
T8.E (PlantNet async enrichment D1 amendment), T8.F (retry endpoint), T8.G (stage-aware UI).
(PP-050–056)

### PHASE 9 — Quality, Testing & Hardening ✅ COMPLETE (code, 2026-06-25 — PHASE9 branch pending CI)
T9.1 (frontend unit tests — Jest), T9.2 (Playwright E2E skeleton), T9.3 (visual regression + a11y + Lighthouse),
T9.4 (AI visual review agent), T9.5 (Testcontainers ITs + JaCoCo 80%), T9.6 (Sentry + correlation IDs),
T9.7 (gitleaks + Trivy + OWASP + Dependabot), T9.8 (AI eval suite + prompt-injection guardrails).
(PP-057–064)

### PHASE 9.5 — Species Card Harvest + Async-Description Reliability ✅ COMPLETE (PHASE9.5 branch)
T9.A (PlantNet candidate harvest → Species), T9.B (per-field generation status + regenerate endpoints),
T9.C (Species overview redesign), T9.D (Treatment description status-driven UI), T9.E (Task Step page),
T9.F (plant-edit back-nav fix). (PP-065–070, migrations 028–029)

---

## PHASE 10 — Launch Preparation 🟡 NOT STARTED (was Phase 5)
> Goal: deploy to production, beta test, release v1.0.0.

### T10.1 — Production configuration 🤖 AI
**Branch:** `feature/PP-065-prod-config`
```
// Generate staging + prod Spring Boot configs.
1. application-staging.yml + application-prod.yml: ${DATABASE_URL}, HikariCP
   (max 20/min 5/timeout 20000), ${REDIS_URL}, Liquibase enabled, show-sql false,
   logging INFO com.plantpal / WARN spring / JSON, Actuator health+info only,
   JPA ddl-auto=validate, CORS from ${ALLOWED_ORIGINS}.
2. logback-spring.xml: JSON in staging/prod, pattern in dev, MDC correlationId on
   every line (timestamp, level, correlationId, userId, message, exception).
3. app.rate-limit.* config (ai-calls-per-hour 20, auth-attempts-per-minute 5,
   chat-messages-per-hour 10).
```

### T10.2 — Performance optimizations 🤝 Assisted
**Branch:** `feature/PP-066-performance`
```
1. Migration (VERIFY next free — sequence now at 029 from Phase 9.5):
   composite idx identifications(plant_id, created_at DESC); partial idx
   reminders(next_due_at) WHERE enabled; idx care_logs(user_id, performed_at DESC);
   idx treatments(plant_id, status) + species(scientific_name) if not covered.
2. @Cacheable on: ChatServiceImpl.buildGardenContext, SpeciesServiceImpl.getSpecies.
3. Angular: lazy-loading verified, OnPush on list components, trackBy on *ngFor.
```

### T10.3 — Security hardening 🤖 AI
**Branch:** `feature/PP-066-performance` (same branch)
```
1. Security headers in SecurityConfig: X-Content-Type-Options, X-Frame-Options DENY,
   HSTS (prod), basic CSP.
2. Bucket4j on AuthController: login 5/min/IP, register 3/hour/IP, 429 + Retry-After.
3. Input sanitization: plant fields strip HTML, chat max 2000 chars.
4. (OWASP Dependency-Check in T9.7.) Confirm GITHUB_TOKEN rotated.
```

### T10.4 — Complete API documentation 🤖 AI
**Branch:** `feature/PP-066-performance` (same branch)
```
@Operation/@ApiResponse/@Parameter on every controller. @ApiResponse for
200/201/400/401/403/404/429/500. OpenApiConfig: "PlantPal API" v1.0.0, JWT button.
```

### T10.5 — Production deployment 👤 Manual
Railway (Postgres + Redis, env vars from .env.example) + Vercel.
> **Decide first:** Kafka/Zookeeper production story.

### T10.6 — Beta testing 👤 Manual
5–10 plant owners. Mobile (Chrome Android + Safari iOS), PWA install, full journey.

### T10.7 — Beta bug fixes 🤝 Assisted
Per bug: `bugfix/PP-{N}` from dev, failing test first.

### T10.8 — Release v1.0.0 👤 Manual
`release/v1.0.0`, `mvn versions:set 1.0.0`, CHANGELOG, tag `v1.0.0`.

---

## Status Summary (as-of archive date)

| Phase | Status |
|---|---|
| 0 — Setup | ✅ Done |
| 1 — Auth + Plants | ✅ Done |
| 2 — AI Identification | ✅ Done |
| 3 — Reminders | ✅ Done except T3.3 |
| 4 — Chat | ✅ Done |
| 6 — Species & Treatment | ✅ Done |
| 7 — Model Control, Batch | ✅ Done |
| 8 — PlantNet | ✅ Done |
| 8.5 — Pipeline Resilience | ✅ Done |
| 9 — Quality & Hardening | ✅ Code complete (PHASE9 branch) |
| 9.5 — Species Card Harvest | ✅ Done (PHASE9.5 branch) |
| 10 — Launch | 🔲 Not started |
