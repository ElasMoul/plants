# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-06-15 (session 11)

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
- T2.9c Annotation list + disease detail — frontend (feature/PP-017-visual-annotation) ✅
  - AnnotationListComponent: region list with color dot/label/confidence/type badges, toggle-deselect, emits regionSelected(index|null)
  - DiseaseDetailPanelComponent: DISEASE-only panel, "Ask for cure" → getCureAdvice() (loading/success/error), "Add to care plan" disabled
  - IdentificationService.getCureAdvice(): POST /api/v1/identifications/{id}/cure-advice → Observable<string>
  - PhotoAnnotatorComponent: selectedRegionIndex @Input, non-selected dimmed rgba(200,200,200,0.04)+#ccc, selected 3px stroke
  - Wired in identification-page (preview state) and plant-detail (Last Scan tab)
  - CarePlanModule: +AnnotationListComponent, +DiseaseDetailPanelComponent, +MatCard/Spinner/Tooltip modules
- T2.9b Polygon canvas — frontend (feature/PP-017-visual-annotation) ✅
  - PolygonPoint interface added to identification.model.ts; AnnotationRegion.polygon?: PolygonPoint[]; boundingBox optional
  - PhotoAnnotatorComponent rewritten: drawPolygon() method, shared drawLabel(), polygon-first with bbox fallback, skip if neither
  - plant-form.component.ts: editId! → editId ?? 0 (lint fix, no-non-null-assertion)
  - app.component.html: user-menu button hidden on mobile (class="user-menu-btn" + display:none ≤768px)
  - nginx.conf: /photos/ → http://backend:8080/photos/ proxy block (requires docker-compose up --build frontend)
- T2.9a Polygon annotation — backend (feature/PP-023-enhanced-annotation-backend, merged PR #15) ✅
  - PolygonPointDto (xPct/yPct with @JsonProperty fix)
  - AnnotationRegionDto: added List<PolygonPointDto> polygon (nullable); boundingBox kept nullable for legacy records
  - BoundingBoxDto: @JsonProperty("xPct")/@JsonProperty("yPct") production bug fixed
  - DeepSeekClient.ANNOTATION_SYSTEM_PROMPT updated to polygon schema (8–16 clockwise points, min 4)
  - DeepSeekAnnotationClient: 2-attempt retry on EOF (Azure HTTP/2 GOAWAY on parallel connections)
  - parseAnnotationRegions(): clears polygon to null if < 3 points (degenerate)
- T2.9d Cure-advice endpoint (feature/PP-023-enhanced-annotation-backend, merged PR #15) ✅
  - POST /api/v1/identifications/{id}/cure-advice → 202 Accepted, { advice: string }
  - CureAdviceRequest (@NotBlank regionLabel, nullable species), CureAdviceResponse (String advice)
  - DeepSeekClient.generateCureAdvice(): text model (DeepSeek-R1), plain text (no json_object), stripThinkTags()
  - Separate cureAdviceBuckets (10/hour) — independent from deepSeekBuckets (20/hour)
  - Ownership check before AI call; ResourceNotFoundException if not owned
  - 18 unit tests passing (4 new CureAdvice tests: happy path, rate-limited, not-owned, DeepSeek error)

## Active Branches
- feature/PP-023-enhanced-annotation-backend — merged to dev as PR #15 ✅
- feature/PP-017-visual-annotation — T2.9b + T2.9c complete, merged PR #17 ✅
- AddChooseAi — AI model preference feature (in progress, assigned to backend + frontend agents)

## Next Tasks (in order)
- AddChooseAi — AI model preference (backend + frontend) ← IN PROGRESS
- T2.A — GitHubModelsClient split: vision (gpt-4o/gpt-4o-mini) vs text (DeepSeek-R1) (feature/PP-025-github-models-client)
- T2.B — Add GITHUB_GPT4O to AiModelPreference + model selector 4th toggle (same branch)
- T2.C — Kafka async identification pipeline — return 202, consumer processes (feature/PP-026-kafka-async)
- T2.D — Frontend polling for pending identification (same branch)
- T2.E — Redis photo storage + SHA-256 deduplication (feature/PP-027-redis-photo-storage)
- T2.F — Image dimension locking + aspect-ratio warning in annotator (same branch)
- T2.10 — Garden health dashboard (feature/PP-020-garden-dashboard)
- T2.11 — Manual testing for all Phase 2 features

## AddChooseAi Feature (branch: AddChooseAi — in progress)
### Backend
- `AiModelPreference` enum in `user/`: DEEPSEEK | PLANTNET | OLLAMA_LLAVA | GITHUB_GPT4O (added in T2.B)
- `User` entity: `ai_model_preference VARCHAR(50) DEFAULT 'DEEPSEEK' NOT NULL`
- Migration: **`010_add_user_preferences.sql`** ← ⚠️ MUST be 010, not 009 (009 already exists)
- DTOs: `UserPreferencesRequest` (@NotNull preference), `UserPreferencesResponse`
- `UserService`: `getPreferences(userId)`, `updatePreferences(userId, request)`
- `UserController`: GET/PUT `/api/v1/users/me/preferences` (userId from SecurityContext)
- `IdentificationServiceImpl.identify()`: Step 0 loads user preference, switches AI client accordingly
  - DEEPSEEK → gitHubModelsClient.identifyPlant() (after T2.A)
  - GITHUB_GPT4O → gitHubModelsClient.identifyPlant() (explicit; same client, added in T2.B)
  - PLANTNET → plantNetClient.identify()
  - OLLAMA_LLAVA → ollamaClient.identifyPlant() (resizes image first); falls back to gitHubModelsClient on error

### Frontend
- `AiModelPreference` type + `UserPreferences` interface added to `core/models/user.model.ts`
  Values: 'DEEPSEEK' | 'PLANTNET' | 'OLLAMA_LLAVA' | 'GITHUB_GPT4O' (4th added in T2.B)
- `UserService` in `core/services/user.service.ts`: `getPreferences()` (cache-first via sessionStorage), `updatePreferences(pref)`
- `ModelSelectorComponent` in `shared/components/model-selector/`: mat-button-toggle-group (4 options after T2.B),
  loading spinner, revert-on-error, snack-bar feedback, matTooltip rate-limit warnings on each toggle
- Declared + exported from `SharedModule`
- Placed in `app.component.html` toolbar, hidden ≤768px

## AI Stack (current — as of 2026-06-15, session 12)
### Clients
- **GitHubModelsClient** (T2.A — planned): gpt-4o for identification, gpt-4o-mini for annotation
  → endpoint: https://models.inference.ai.azure.com; auth: GITHUB_TOKEN; HTTP/2; 5-min timeout
  → Replaces DeepSeekClient for ALL vision tasks
- **DeepSeekClient** (current): DeepSeek-R1 for care plan text + cure advice (text-only)
  → Same endpoint + auth as GitHubModelsClient; retains stripThinkTags() (package-private static)
- **OllamaClient** (local dev): llava-phi3 for identification (OLLAMA_LLAVA preference)
  → resizeAndConvertToJpeg() caps at 1024px before base64 (fixes llava-phi3 400 error)
  → Falls back to DeepSeek/GitHub if Ollama throws PlantPalException
- **DeepSeekAnnotationClient** (@Primary VisionAnnotationClient): uses GitHubModelsClient.analyzeRegions()
  → gpt-4o-mini (after T2.A); 2-attempt retry on HTTP/2 GOAWAY; 429 → OllamaClient.analyzeRegions()

### Provider routing
| Preference | Identification | Annotation | Care Plan | Cure Advice |
|---|---|---|---|---|
| DEEPSEEK (default) | GitHubModelsClient (gpt-4o) | gpt-4o-mini | DeepSeek-R1 | DeepSeek-R1 |
| GITHUB_GPT4O (T2.B) | GitHubModelsClient (gpt-4o) | gpt-4o-mini | DeepSeek-R1 | DeepSeek-R1 |
| OLLAMA_LLAVA | OllamaClient (llava-phi3) → fallback gpt-4o | gpt-4o-mini | DeepSeek-R1 | DeepSeek-R1 |
| PLANTNET | PlantNetClient (deprecated) | gpt-4o-mini | DeepSeek-R1 | DeepSeek-R1 |

### Current auth / env vars
- Pre-T2.A: `DEEPSEEK_API_KEY` (GitHub PAT) → used by DeepSeekClient
- Post-T2.A: `GITHUB_TOKEN` (same GitHub PAT) → used by both GitHubModelsClient + DeepSeekClient
- Rotate if shared in chat: github.com/settings/tokens

### Response parsing
- DeepSeek-R1 wraps output in `<think>...</think>`
- gpt-4o and llava-phi3 sometimes wrap JSON in ```json...``` fences
- DeepSeekClient.stripThinkTags() (package-private static) strips both; OllamaClient calls it too
- GitHub Models daily cap: ~50 gpt-4o vision calls/day; 429 on annotation → Ollama fallback

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
- PolygonPointDto (T2.9a ✅): @JsonProperty("xPct")/@JsonProperty("yPct") applied
- Cure-advice rate limit (T2.9d): separate Bucket — 10 calls/hour (not shared with the
  20/hour identification bucket)
- Reminder entity does NOT extend AuditableEntity — reminders table has no audit columns
- JaCoCo gate: temporarily at 10%; restore to 80% with exclusions after T2 tests complete
- raw_response stored as TEXT (not JSONB) in identifications
- **T2.A (accepted):** Split DeepSeekClient into GitHubModelsClient (vision) + DeepSeekClient (text).
  Reason: single bean was mixing model responsibilities and making per-model rate-limit tracking hard.
- **T2.B (accepted):** Add GITHUB_GPT4O as 4th AiModelPreference — users can explicitly select gpt-4o.
- **T2.B Suggestion B (accepted):** Use gpt-4o-mini for annotation (not gpt-4o). Annotation is not the
  primary result; gpt-4o-mini is 10x cheaper with acceptable quality for polygon detection.
  Separate @Value("${github.models.annotation-model:gpt-4o-mini}") config so it can be changed.
- **T2.C (accepted):** Kafka async pipeline — POST /analyze returns 202 immediately.
  Reason: current CompletableFuture.get() blocks HTTP thread 5-15s; exhausts thread pool under load.
  Polling approach (3s interval, max 10 attempts); WebSocket upgrade deferred to Phase 4.
- **T2.C WebSocket deferred:** Frontend polls until T2.D merges. WebSocket (STOMP/SockJS) is a
  Phase 4 upgrade to replace polling after the chat module ships (both features share the same WS infra).
- **T2.E (accepted):** Redis photo storage (key: `photo:{uuid}`, TTL 7 days) + SHA-256 deduplication.
  Reason: /tmp disk is ephemeral in containers; Redis gives consistency across restarts and enables CDN-like serving.
- **T2.E dedup:** SHA-256 of raw bytes → `photo:hash:{sha256}` in Redis (StringRedisTemplate).
  Same photo uploaded twice returns same URL without disk write. TTL resets on dedup hit.
- **T2.F (accepted):** Record sourceImageWidth + sourceImageHeight on Identification entity after resize.
  Include in IdentificationResponse. Frontend shows "⚠ Annotation may be misaligned" if browser
  aspect ratio drifts >2% from the AI's source aspect ratio.

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
010_add_user_preferences.sql          🔲 AddChooseAi — ai_model_preference VARCHAR(50) on users
011_add_image_dimensions.sql          🔲 T2.F — source_image_width INT, source_image_height INT on identifications

⚠️ No structural migration needed for T2.9a polygon switch — annotation_regions is already JSONB,
which stores any JSON shape. Switching from boundingBox to polygon is a pure code change.

⚠️ Migration 011 inserts AFTER 010. Verify db.changelog-master.xml order matches file numbering.

## Open Items (technical debt)
- **CRITICAL:** Rotate GITHUB_TOKEN (GitHub PAT) — was shared in chat sessions; regenerate at github.com/settings/tokens
  After T2.A, env var renames from DEEPSEEK_API_KEY → GITHUB_TOKEN; update backend/.env on all machines
- JaCoCo gate needs to be restored to 80% with proper exclusions
- Integration tests not running in CI (Testcontainers phase isolation issue)
- AiTestController not @Profile("dev") guarded — will deploy to prod as-is
- IdentificationControllerIT.java missing
- PlantNetClient + plantnet/ DTOs are dead code — remove at next cleanup sprint
- T2.8 frontend (one-click save UI) still pending
- WebSocket (STOMP/SockJS): deferred to Phase 4. Will replace HTTP polling in both the
  identification result flow (T2.D) and the chat module (T4.x). Both share the same WS endpoint.
- Kafka consumer error DLQ: currently failed identifications set status=FAILED and log ERROR.
  A proper dead-letter topic should be added in Phase 3 before prod load.
- gpt-4o-mini annotation quality: if polygon quality is insufficient after T2.B ships,
  fall back to gpt-4o by changing GITHUB_ANNOTATION_MODEL env var (no code change needed).

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
- 2026-06-15: DeepSeekAnnotationClient: 429 → OllamaClient.analyzeRegions() fallback
- 2026-06-15: OllamaClient.analyzeRegions() added (uses /api/generate + images top-level)
- 2026-06-15: stripThinkTags() extended to strip ```json...``` markdown fences (gpt-4o + llava-phi3)
              Made package-private static so OllamaClient can call it without duplication
- 2026-06-15: OllamaClient: resizeAndConvertToJpeg() added — caps at 1024px, converts to JPEG
              before base64 encoding; fixes llava-phi3 400 "Failed to load image or audio file"
- 2026-06-15: IdentificationServiceImpl: OLLAMA_LLAVA path falls back to DeepSeek on PlantPalException
- 2026-06-15: Raw response debug logs added to analyzeRegions() and generateCureAdvice() in DeepSeekClient

## Planned Infra Changes (upcoming tasks)
- T2.A: GitHubModelsClient split — env var DEEPSEEK_API_KEY → GITHUB_TOKEN; DEEPSEEK_BASE_URL → GITHUB_BASE_URL
- T2.A: New props: github.models.identification-model (gpt-4o), github.models.annotation-model (gpt-4o-mini)
- T2.C: Docker Compose additions — Zookeeper + Kafka (confluentinc/cp-kafka:7.6.0, port 29092)
- T2.C: New env var: KAFKA_BOOTSTRAP_SERVERS=localhost:29092
- T2.C: New Maven dep: spring-kafka
- T2.E: New Redis key patterns: `photo:{uuid}` (byte[]), `photo:hash:{sha256}` (string URL)
- T2.E: New bean: RedisTemplate<String, byte[]> byteRedisTemplate (ByteArrayRedisSerializer)
- T2.F: New Liquibase migration 011_add_image_dimensions.sql

## Repo Structure
plants/
  backend/          Spring Boot 3.2, Java 21
  frontend/         Angular 16+, NgModules
  docker-compose.yml
  .github/workflows/
  .claude/          ← agent memory (this folder)
