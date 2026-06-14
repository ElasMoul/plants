# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-06-14 (session 2)

## Current Phase
Phase 2 — AI Plant Identification (extending scope — new tasks T2.6–T2.9 added)

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

## Active Branches
- feature/Update-Context-and-plan (this branch — .claude/ file updates only)

## Next Tasks (in order)
- T2.6 — Visual annotation: bounding boxes + disease overlay (feature/PP-017-visual-annotation)
- T2.7 — One-click validate & save flow (feature/PP-018-one-click-save)
- T2.8 — AI care plan for beginners, auto-creates reminders (feature/PP-019-care-plan)
- T2.9 — Garden health dashboard (feature/PP-020-garden-dashboard) [Architect suggestion]
- T2.11 — DeepSeek client + dynamic care card backend (feature/PP-021-deepseek-care-plan)
- T2.12 — Dynamic care plan frontend (same branch)
- T2.10 — Manual testing for all Phase 2 features

## Key Decisions Since Project Start
- Plant identification uses PlantNet API (not Claude Vision)
- Chat uses Ollama phi3 (local dev)
- Visual annotation uses Ollama LLaVA (multimodal) for bounding box generation
- **Care planning uses DeepSeek API** (deepseek-chat V3) — richer reasoning than phi3, OpenAI-compatible
- Care plan is a dynamic list of "care cards" — not hardcoded fields — so any plant species works without frontend changes
- JaCoCo gate temporarily at 10% until integration tests run in CI
- Identification raw_response stored as TEXT (not JSONB) for simplicity
- One-click save creates Plant + auto-generates Reminders from the care plan in a single action
- DEEPSEEK_API_KEY added to .env.example (needs to be filled)

## Open Items
- JaCoCo gate needs to be restored to 80% with proper exclusions (after T2 tests written)
- Integration tests not running in CI (Testcontainers phase isolation issue)
- AiTestController not @Profile("dev") guarded — will deploy to prod as-is (known issue)
- IdentificationController uses .get() on CompletableFuture — blocks HTTP thread (known issue)
- IdentificationControllerIT.java missing

## Infra Fixes Applied
- 2026-06-12: Nginx `client_max_body_size 15m` + proxy timeouts 120s (frontend/nginx.conf)
- 2026-06-12: Spring Boot multipart limit raised to 15MB (application.yml) — phone photos ~3-10MB were hitting 1MB default
- 2026-06-12: PlantNetClient forced to HTTP/1.1 (JdkClientHttpRequestFactory) — Java 21 HttpClient was negotiating HTTP/2 via ALPN; PlantNet drops HTTP/2 connections on large multipart bodies (EOFException from Http2TubeSubscriber)

## Pending DB Migrations (not yet created)
- 008_add_annotation_regions.sql — adds annotation_regions JSONB to identifications
- 009_add_care_plan_to_identifications.sql — adds care_plan JSONB to identifications

## Repo Structure
plants/
  backend/          Spring Boot 3.2, Java 21
  frontend/         Angular 16+, NgModules
  docker-compose.yml
  .github/workflows/
  .claude/          ← agent memory (this folder)
