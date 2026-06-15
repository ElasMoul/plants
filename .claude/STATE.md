# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-06-15 (session 5)

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
- PlantNet integration — full backend ✅ (superseded — PlantNetClient is dead code)
- T2.4 Identification Angular feature module ✅ (PR #5 merged to dev)
- T2.6 DeepSeek client + dynamic care plan backend ✅ (merged via PR)
- T2.7 Dynamic care plan frontend (CarePlanModule) ✅ (merged via PR)
- T2.8 One-click save flow — backend ✅ (feature/PP-018-one-click-save merged)
- T2.9 Visual annotation — bounding boxes (feature/PP-017-visual-annotation) ✅
  - Backend: VisionAnnotationClient interface, DeepSeekAnnotationClient (@Primary),
    PlantNetAnnotationClient (fallback), AnnotationRegionDto, BoundingBoxDto,
    migration 007, parallel async in IdentificationServiceImpl, 49 unit tests
  - Frontend: PhotoAnnotatorComponent (canvas, show/hide toggle), declared in CarePlanModule,
    wired into preview-card + "Last Scan" tab in plant-detail

## Active Branches
- feature/PP-017-visual-annotation — T2.9 complete, 2 commits ahead of origin, ready to push + PR

## Next Tasks (in order)
- T2.9a — Polygon annotation backend (feature/PP-023-enhanced-annotation-backend) ← NEXT BACKEND
- T2.9d — Cure-advice endpoint (same branch as T2.9a — same backend session)
- T2.9b — Polygon canvas frontend (feature/PP-023-enhanced-annotation-backend, frontend half)
- T2.9c — Annotation list panel + disease detail (feature/PP-024-disease-panel) ← needs T2.9b + T2.9d
- T2.10 — Garden health dashboard (feature/PP-020-garden-dashboard)
- T2.11 — Manual testing for all Phase 2 features

## AI Stack (current — as of 2026-06-15)
- Identification (photo → species + health + care plan): gpt-4o via GitHub Models
- Care plan text regeneration: DeepSeek-R1 via GitHub Models
- Visual annotation (photo → regions): gpt-4o via GitHub Models (DeepSeekAnnotationClient)
- Cure advice (T2.9d, planned): DeepSeek-R1 via GitHub Models (plain text, no json_object format)
- Endpoint: https://models.inference.ai.azure.com
- Auth: GitHub PAT in DEEPSEEK_API_KEY (backend/.env) — rotate if shared in chat
- HTTP/2 required for Azure endpoint; 5-minute read timeout on DeepSeekClient
- R1 wraps output in <think>...</think> — DeepSeekClient.stripThinkTags() handles this

## Key Decisions Since Project Start
- Plant identification: gpt-4o (GitHub Models, vision) — replaced PlantNet (unreliable)
- Care planning: DeepSeek-R1 (GitHub Models) — parallel async call after identification
- Care plan shape: dynamic list of "care cards" — no hardcoded fields per plant type
- Visual annotation: gpt-4o (GitHub Models) — bounding boxes now, polygons in T2.9a
- VisionAnnotationClient interface: DeepSeekAnnotationClient (@Primary) + PlantNetAnnotationClient
  (non-primary fallback mapping species results to full-image PLANT boxes)
- CarePlanModule is a shared NgModule (not lazy) imported by IdentificationModule and PlantModule
- PhotoAnnotatorComponent declared in CarePlanModule (not IdentificationModule) — avoids
  lazy-module-imports-lazy-module circular dependency
- Canvas uses [hidden] not *ngIf — *ngIf removes element, breaking @ViewChild resolution
- BoundingBoxDto: @JsonProperty("xPct")/@JsonProperty("yPct") — Lombok getXPct() causes
  Jackson to produce key "XPct" (two consecutive uppercase chars); @JsonProperty fixes it
- PolygonPointDto (T2.9a, planned): same @JsonProperty fix needed on xPct/yPct
- Cure-advice rate limit (T2.9d): separate Bucket — 10 calls/hour (not shared with the
  20/hour identification bucket)
- Reminder entity does NOT extend AuditableEntity — reminders table has no audit columns
- JaCoCo gate: temporarily at 10%; restore to 80% with exclusions after T2 tests complete
- raw_response stored as TEXT (not JSONB) in identifications

## DB Migration Sequence
001_create_users.sql              ✅
002_create_plants.sql             ✅
003_create_identifications.sql    ✅
004_create_reminders_and_care_logs.sql ✅
005_create_push_subscriptions.sql ✅
006_alter_identifications.sql     ✅ (raw_response TEXT)
007_add_annotation_regions.sql    ✅ T2.9 — annotation_regions JSONB (inserted BEFORE 008 in master XML)
008_add_care_plan.sql             ✅ T2.6 — care_plan JSONB
009_add_health_to_identifications.sql ✅ — health_status VARCHAR(30), health_notes TEXT

⚠️ No structural migration needed for T2.9a polygon switch — annotation_regions is already JSONB,
which stores any JSON shape. Switching from boundingBox to polygon is a pure code change.

## Open Items (technical debt)
- Rotate DEEPSEEK_API_KEY (GitHub PAT) — was shared in chat sessions; regenerate at github.com/settings/tokens
- JaCoCo gate needs to be restored to 80% with proper exclusions
- Integration tests not running in CI (Testcontainers phase isolation issue)
- AiTestController not @Profile("dev") guarded — will deploy to prod as-is
- IdentificationController uses .get() on CompletableFuture — blocks HTTP thread (known, deferred)
- IdentificationControllerIT.java missing
- PlantNetClient + plantnet/ DTOs are dead code — remove at next cleanup sprint
- OllamaClient (phi3) is dead code in main flow — dev-only, remove before prod
- T2.8 frontend (one-click save UI) still pending

## Architectural Risks for T2.9a–T2.9d
- Polygon degenerate case: AI may return < 3 points. Backend must null-out polygon < 3 points;
  frontend must skip drawing (< 3 points = cannot form a closed path).
- BoundingBoxDto backward-compat: keep boundingBox nullable on AnnotationRegionDto even after
  T2.9a ships — existing identifications in DB have bounding box format, not polygon.
  Frontend must check polygon first, fall back to boundingBox, skip if both null.
- R1 cure-advice response is plain text (no response_format json_object) — stripThinkTags()
  still needed. The returned string may contain newlines; preserve them in the UI.
- Canvas interaction (T2.9c): AnnotationListComponent emits selectedIndex → parent passes
  @Input selectedRegionIndex to PhotoAnnotatorComponent → redraw with dimmed unselected regions.
  Use @Input/@Output (not a service) — keeps state in the parent, components stay pure.

## Infra Fixes Applied
- 2026-06-12: Nginx client_max_body_size 15m + proxy timeouts 120s (frontend/nginx.conf)
- 2026-06-12: Spring Boot multipart limit raised to 15MB
- 2026-06-12: PlantNetClient forced to HTTP/1.1 — fixed EOFException on large multipart bodies
- 2026-06-14: DeepSeekClient switched to HTTP/2 (Azure requires it); 5-min read timeout added
- 2026-06-14: stripThinkTags() added to DeepSeekClient for DeepSeek-R1 reasoning output
- 2026-06-14: DeepSeekAnnotationClient: retry once on HTTP/2 GOAWAY from Azure

## Repo Structure
plants/
  backend/          Spring Boot 3.2, Java 21
  frontend/         Angular 16+, NgModules
  docker-compose.yml
  .github/workflows/
  .claude/          ← agent memory (this folder)
