# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-06-14 (session 4)

## Current Phase
Phase 2 — AI Plant Identification (in progress)

## Completed Tasks
- T0.1 GitHub repo + branch protection ✅
- T0.2 Docker Compose (PostgreSQL 15 + Redis 7) ✅
- T0.3 Spring Boot backend skeleton ✅
- T0.4 Angular frontend skeleton ✅
- T0.5 CI/CD pipelines + Dockerfiles ✅
- T1.1 Liquibase migrations (users, plants, identifications, reminders, push_subscriptions) ✅
- T1.2 User module (entity, DTOs, MapStruct mapper, repository) ✅
- T1.3 Spring Security 6 + JWT authentication ✅
- T1.4 Plant module — full backend CRUD ✅
- T1.7 Plant Angular feature module ✅
- T1.8 Auth Angular feature module ✅
- PlantNet integration — full backend ✅
- T2.4 Identification Angular feature module ✅ (PR #5 merged to dev)
- T2.6 DeepSeek client + dynamic care plan backend ✅ (feature/PP-021-deepseek-care-plan)
- T2.7 Dynamic care plan frontend (CarePlanModule) ✅ (same branch)

## Active Branches
- feature/PP-021-deepseek-care-plan (T2.6 + T2.7 done, uncommitted — needs spotless:apply then commit + PR)

## Next Tasks (in order)
- T2.8 — One-click validate & save flow (feature/PP-018-one-click-save) ← NEXT
- T2.9 — Visual annotation: bounding boxes + disease overlay (feature/PP-017-visual-annotation)
- T2.10 — Garden health dashboard (feature/PP-020-garden-dashboard)
- T2.11 — Manual testing for all Phase 2 features

## AI Keys Status
- DEEPSEEK_API_KEY: provided by user 2026-06-14 — in backend/.env (rotate it — was shared in chat)

## Key Decisions Since Project Start
- Plant identification: PlantNet API (not AI vision)
- Care planning: DeepSeek API (deepseek-chat V3) — parallel async call after PlantNet
- Care plan shape: dynamic list of "care cards" — no hardcoded fields per plant type
- Chat: Ollama phi3 placeholder for now — will be replaced by DeepSeek in Phase 4
- Visual annotation: Ollama LLaVA (dev, free); DeepSeek VL swap available for prod
- Ollama phi3: no remaining role after DeepSeek ships care planning (to be removed)
- JaCoCo gate: temporarily at 10%; restore to 80% with exclusions after T2 tests complete
- raw_response stored as TEXT (not JSONB) in identifications
- One-click save (T2.8): creates Plant + auto-generates Reminders from care plan in one action
- Reminder entity does NOT extend AuditableEntity — reminders table has no created_by/updated_by columns
- CarePlanModule is a shared NgModule (not lazy) imported by both IdentificationModule and PlantModule — avoids lazy-module circular dependency

## DB Migration Sequence
001_create_users.sql         ✅
002_create_plants.sql        ✅
003_create_identifications.sql ✅
004_create_reminders_and_care_logs.sql ✅
005_create_push_subscriptions.sql ✅
006_alter_identifications.sql ✅ (raw_response TEXT)
007_add_annotation_regions.sql [PLANNED — T2.9] ← does NOT exist yet
008_add_care_plan.sql        ✅ T2.6 — care_plan JSONB

⚠️ Migration sequence gap: master XML currently runs 001→006, then 008. When T2.9 is
implemented, insert 007 BEFORE 008 in db.changelog-master.xml (Liquibase uses file order,
not filename order). Do NOT add 007 after 008 — it will run after care_plan column exists.

## Open Items (technical debt)
- cd backend && mvn spotless:apply — fix CRLF line endings on T2.6 new files (Windows)
- Rotate DEEPSEEK_API_KEY — was shared in chat session; current key in .env is exposed
- JaCoCo gate needs to be restored to 80% with proper exclusions
- Integration tests not running in CI (Testcontainers phase isolation issue)
- AiTestController not @Profile("dev") guarded — will deploy to prod as-is
- IdentificationController uses .get() on CompletableFuture — blocks HTTP thread
- IdentificationControllerIT.java missing

## Infra Fixes Applied
- 2026-06-12: Nginx client_max_body_size 15m + proxy timeouts 120s (frontend/nginx.conf)
- 2026-06-12: Spring Boot multipart limit raised to 15MB — phone photos ~3-10MB were hitting 1MB default
- 2026-06-12: PlantNetClient forced to HTTP/1.1 (JdkClientHttpRequestFactory) — Java 21 HttpClient
  negotiates HTTP/2 via ALPN; PlantNet drops HTTP/2 on large multipart bodies (EOFException)

## Repo Structure
plants/
  backend/          Spring Boot 3.2, Java 21
  frontend/         Angular 16+, NgModules
  docker-compose.yml
  .github/workflows/
  .claude/          ← agent memory (this folder)
