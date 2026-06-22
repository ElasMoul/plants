# PlantPal — Task Plan

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
> Only the **next phase to execute** carries full prompt detail. Phase 5 is that
> phase now — everything before it is done.

---

## PHASE 0 — Project Setup ✅ DONE
### T0.1 — Create GitHub repository 👤 Manual
### T0.2 — Set up local infrastructure 👤 Manual
### T0.3 — Generate Spring Boot skeleton 🤖 AI
### T0.4 — Generate Angular skeleton 🤖 AI
### T0.5 — GitHub Actions CI/CD 🤝 Assisted
### T0.6 — Generate VAPID keys and gather secrets 👤 Manual

## PHASE 1 — Auth + Plant Management ✅ DONE
### T1.1 — Liquibase migrations (all tables) 🤖 AI
### T1.2 — User module — entity, DTOs, mapper, repository 🤖 AI
### T1.3 — Spring Security 6 + JWT 🤖 AI
### T1.4 — Plant module — full backend 🤖 AI
### T1.5 — Unit tests — Phase 1 🤖 AI
### T1.6 — Integration tests — Phase 1 🤖 AI
### T1.7 — Plant module — Angular frontend 🤖 AI
### T1.8 — Auth module — Angular frontend 🤖 AI

## PHASE 2 — AI Plant Identification ✅ DONE
### T2.1 — Prepare AI environment 👤 Manual
### T2.2 — ClaudeApiClient + FileStorageService 🤖 AI
### T2.3 — Identification module — full backend 🤖 AI
### T2.4 — Identification module — Angular frontend 🤝 Assisted
### T2.5 — Manual testing — identification core 👤 Manual
### T2.6 — DeepSeek client + dynamic care plan backend 🤖 AI
### T2.7 — Dynamic care plan frontend 🤝 Assisted
### T2.8 — One-click validate & save flow 🤝 Assisted
### T2.9 — Visual plant annotation (bounding boxes + disease overlay) 🤖 AI
### T2.9a — Polygon annotation — backend 🤖 AI
### T2.9b — Polygon annotation — frontend 🤖 AI
### T2.9c — Disease detail panel + annotation list 🤖 AI
### T2.9d — Cure-advice endpoint — backend 🤖 AI
### T2.A — GitHubModelsClient refactor — split vision from text client (Backend) 🤖 AI
### T2.B — Add GITHUB_GPT4O to AI model preferences (Both) 🤖 AI
### T2.C — Kafka async identification pipeline (Backend) 🤖 AI
### T2.D — Kafka polling — identification frontend (Frontend) 🤖 AI
### T2.E — Redis photo storage + SHA-256 deduplication (Backend) 🤖 AI
### T2.F — Image dimension locking — annotation alignment (Both) 🤖 AI
### T2.10 — Garden health dashboard 💡 Architect Suggestion
### T2.10a — Backend: fix Plant health/water data 🤖 AI
### T2.10b — Backend: dashboard aggregate endpoint 🤖 AI
### T2.10c — Frontend: plant photo timeline 🤝 Assisted
### T2.10d — Frontend: garden dashboard page 🤝 Assisted
### T2.11 — Manual testing — Phase 2 complete 👤 Manual

## PHASE 3 — Reminders + Push Notifications
### T3.1 — Reminder module — full backend 🤖 AI ✅ DONE
### T3.2 — Reminder module — Angular frontend + PWA 🤝 Assisted ✅ DONE
### T3.4 — Backend: actionable care plans (routines + treatment plans) 🤖 AI ✅ DONE
### T3.5 — Frontend: actionable care plans UI (reminders, treatment plans, diagrams) 🤝 Assisted ✅ DONE
### T3.3 — Manual testing — Phase 3 👤 Manual 🔲 **NOT DONE** — needs a real device:
  push notification delivery, PWA installability, offline reading. The only
  open item left in Phases 0–4.

## PHASE 4 — AI Chat Assistant ✅ DONE (basic)
### T4.1 — Chat module — full backend 🤖 AI ✅ DONE
### T4.2 — Chat module — Angular frontend 🤝 Assisted ✅ DONE (basic single-turn,
  plant-context injection added in T6.13 — see STATE.md)
### T4.3 — Manual testing — Phase 4 👤 Manual ✅ DONE

> Streaming responses + conversation history shipped in the pre-Phase-5 cleanup
> pass below (`feature/PP-038-pre-phase5-cleanup`) — no longer an open item.

## Pre-Phase-5 cleanup pass ✅ DONE
`feature/PP-038-pre-phase5-cleanup` — closed out every open gap flagged in
BACKEND.md/FRONTEND.md/STATE.md from Phases 0–4/6 plus three bugs found via
live testing, before starting Phase 5. See STATE.md for the full list (reminder
double-completion, duplicate treatment creation, species AI-preference
enrichment + structured care cards, chat history + SSE streaming, dead
component removal, missing controller ITs, real JaCoCo gate at 55%).

## PHASE 5 — Launch Preparation
> Goal: deploy to production, beta test, release v1.0.0.
> **This is the only remaining phase.** Everything above is shipped; Phase 6
> (below) is also shipped. Start here.

---

### T5.1 — Production configuration 🤖 AI
**Branch:** `feature/PP-015-prod-config`

**Claude Code prompt:**
```
// Phase 5 — Generate production and staging Spring Boot configurations

1. application-staging.yml + application-prod.yml:
   - datasource: ${DATABASE_URL} (Railway injects this)
   - HikariCP: maximumPoolSize=20, minimumIdle=5, connectionTimeout=20000
   - Redis: ${REDIS_URL} (Railway Redis add-on)
   - Liquibase: enabled=true (runs migrations on startup)
   - show-sql: false
   - Logging: level INFO for com.plantpal, WARN for Spring internals, JSON format
   - Actuator: expose only health and info (not env, beans, etc.)
   - JPA: ddl-auto=validate (never create or update — Liquibase owns schema)
   - CORS: allowed origins from ${ALLOWED_ORIGINS} env var

2. Add Logback configuration for prod (logback-spring.xml):
   - JSON format in staging/prod profiles (structured logs — searchable in Railway)
   - Pattern format in dev (human readable)
   - Log correlation ID (from MDC) in every line
   - Structured JSON key: timestamp, level, correlationId, userId, message, exception

3. Add rate limit configuration to application.yml:
   app.rate-limit.ai-calls-per-hour: 20
   app.rate-limit.auth-attempts-per-minute: 5
   app.rate-limit.chat-messages-per-hour: 10
```

> 💡 **Why structured JSON logs:** When you have logs in Railway/CloudWatch/Datadog,
> JSON logs are searchable and filterable. You can find "all errors for userId 42 in the
> last hour" with a single query. Plain text logs require grep and guesswork.

---

### T5.2 — Performance optimizations 🤝 Assisted
**Branch:** `feature/PP-016-performance`

**Claude Code prompt:**
```
// Phase 5 — Add performance optimizations

1. New Liquibase migration 020_add_performance_indexes.sql (next free number — verify
   against db.changelog-master.xml before naming, current sequence ends at 019):
   - Composite index on identifications(plant_id, created_at DESC)
   - Partial index on reminders(next_due_at) WHERE enabled = TRUE (verify it doesn't
     already exist from migration 004)
   - Index on care_logs(user_id, performed_at DESC) for dashboard queries
   - Index on treatments(plant_id, status) and species(scientific_name) if not already
     covered by existing indexes — check migrations 016/018 first

2. Add @Cacheable to hot read paths not already cached (PlantServiceImpl's
   getUserPlants/getPlant are already @Cacheable — verify, don't re-add):
   - ChatServiceImpl.buildGardenContext → cache key "garden::{userId}", TTL 5 min
   - SpeciesServiceImpl.getSpecies → cache key "species::{id}", TTL 10 min (Species
     is shared/immutable-ish, a good cache candidate not yet covered)
   - Add @CacheEvict on all mutation methods (create, update, archive)

3. Angular bundle optimization:
   - Verify all feature modules are lazy-loaded (check ng build --stats-json)
   - Add OnPush ChangeDetectionStrategy to all list components
   - Add trackBy functions to all *ngFor directives
```

---

### T5.3 — Security hardening 🤖 AI
**Branch:** `feature/PP-016-performance` (same branch)

**Claude Code prompt:**
```
// Phase 5 — Add security hardening

1. Add security headers to SecurityConfig.java:
   - X-Content-Type-Options: nosniff
   - X-Frame-Options: DENY
   - X-XSS-Protection: 1; mode=block
   - Strict-Transport-Security (HSTS) in prod profile only
   - Content-Security-Policy: basic policy allowing own origins

2. Add rate limiting to AuthController using Bucket4j:
   - POST /auth/login: 5 attempts per minute per IP
   - POST /auth/register: 3 registrations per hour per IP
   - Return 429 Too Many Requests with Retry-After header when exceeded

3. Add input sanitization:
   - Plant nickname, notes, location: strip HTML tags (use OWASP Java HTML Sanitizer)
   - Chat messages: max 2000 characters, validated at controller level

4. Add a dependency vulnerability check to the Maven build:
   - OWASP Dependency Check plugin
   - Fail build on CVSS score >= 7 (high severity)

5. Rotate GITHUB_TOKEN before going to prod — see BACKEND.md's Open Items; it was
   shared in chat sessions during development and should not be reused as-is.
```

> 💡 **Why this matters for a "small" app:** Security habits are learned, not bolted on.
> Building these patterns now means they're automatic when you work on bigger projects.
> Rate limiting on auth endpoints is the difference between "hobby app" and "enterprise app".

---

### T5.4 — Complete API documentation 🤖 AI
**Branch:** `feature/PP-016-performance` (same branch)

**Claude Code prompt:**
```
// Phase 5 — Complete OpenAPI documentation for all controllers

Add @Operation, @ApiResponse, @Parameter annotations to every controller — see
BACKEND.md's module inventory for the current full list (Plant, Identification,
Reminder, TreatmentPlan, Chat, Auth, CareLog, Notification, Species, Treatment,
Dashboard, Photo controllers).

For each endpoint include:
- @Operation(summary = "...", description = "...")
- @ApiResponse for 200/201, 400, 401, 403, 404, 429, 500
- @Schema examples on all DTOs

Update OpenApiConfig.java:
- Title: "PlantPal API", version: "1.0.0"
- Description: "Enterprise-grade plant care API"
- JWT authentication button (users can authorize in Swagger UI and test endpoints)
- Server URLs for dev and prod
```

---

### T5.5 — Production deployment 👤 Manual

Steps:
1. Create Railway project → add PostgreSQL plugin → add Redis plugin
2. Set all environment variables in Railway (from `.env.example`)
3. Point Railway to GitHub repo → auto-deploy on push to `main`
4. Configure Vercel for frontend → link Angular build output
5. Set `environment.prod.ts` API URL to Railway backend URL
6. Merge a test commit to `main` → verify both deployments succeed
7. Check `https://your-backend.railway.app/actuator/health` → `{"status":"UP"}`
8. Check `https://your-frontend.vercel.app` → app loads and login works

> Note: Kafka + Zookeeper (T2.C) also need a hosted equivalent or to be dropped in
> favor of the synchronous path for launch — not addressed by the steps above.
> Decide before this task: managed Kafka add-on, or fall back to a simpler
> synchronous identification call for v1.0.0 and revisit async later.

---

### T5.6 — Beta testing 👤 Manual
> Recruit 5–10 people (friends, family) who own plants.

Test scenarios:
- [ ] Full journey: register → identify a plant → confirm species → create reminder → receive notification
- [ ] Disease path: scan → detect issue → start treatment plan → mark steps done → treatment completes
- [ ] Mobile: Chrome Android + Safari iOS
- [ ] Install PWA to home screen
- [ ] Ask the chat assistant "why are my leaves yellowing?", and ask it about a specific plant
- [ ] Collect: confusing UX moments, incorrect identifications, missing features

---

### T5.7 — Beta bug fixes 🤝 Assisted
**Branch:** `bugfix/PP-{num}-{description}` per bug

For each beta bug:
1. Create dedicated `bugfix/PP-{N}` branch from `dev`
2. If the bug is in a service, ask Claude Code for a targeted fix
3. **Always add a test that would have caught the bug first**
4. PR → `dev` with description of the bug, root cause, and fix

---

### T5.8 — Release v1.0.0 👤 Manual

```bash
git checkout dev && git pull origin dev
git checkout -b release/v1.0.0

# Update version in pom.xml
mvn versions:set -DnewVersion=1.0.0
mvn versions:commit

# Write CHANGELOG.md

git commit -m "chore(release): prepare version 1.0.0"

# Merge into main
git checkout main
git merge --no-ff release/v1.0.0
git tag -a v1.0.0 -m "PlantPal v1.0.0 — MVP Launch"
git push origin main --tags

# Merge back into dev
git checkout dev
git merge --no-ff release/v1.0.0
git push origin dev

git branch -d release/v1.0.0
git push origin --delete release/v1.0.0
```

---

## PHASE 6 — Species & Treatment Domain Restructure ✅ DONE (2026-06-20)
> Restructured the domain from plant-centric to species-centric: a shared `Species`
> entity, a per-disease `Treatment` entity (wraps the existing `TreatmentPlan` rather
> than duplicating its reminder/step machinery — see ARCHITECT.md's "Two Treatment
> concepts" before touching either one), a 5-item bottom nav + Home screen, a
> species-first Garden page, and a redesigned Plant/Treatment page (sticky header +
> icon button bar). Full historical implementation notes are in STATE.md; the
> durable domain model, decision tree, and lifecycle state machine live in
> ARCHITECT.md — read that before extending any of this.

### T6.1 — Species entity + migrations + endpoints 🤖 AI ✅ DONE
### T6.2 — Treatment entity + migrations + endpoints 🤖 AI ✅ DONE
### T6.3 — Plant entity updates + scan flow changes 🤖 AI ✅ DONE
### T6.4 — Species data enrichment async service 🤖 AI ✅ DONE
### T6.5 — Garden species-first restructure 🤖 AI ✅ DONE
### T6.6 — Species detail page 🤖 AI ✅ DONE
### T6.7 — Home page 🤖 AI ✅ DONE
### T6.8 — Bottom nav 5 items + routing 🤖 AI ✅ DONE
### T6.9 — Identification flow redesign — species matching 🤖 AI ✅ DONE
### T6.10 — Plant page: sticky header + icon button bar 🤖 AI ✅ DONE
### T6.11 — Plant page: scans tab + treatment CTA 🤖 AI ✅ DONE
### T6.12 — Treatment page 🤖 AI ✅ DONE
### T6.13 — Chat: plant context injection 🤖 AI ✅ DONE
### T6.14 — Reminders: wire treatment plan steps 🤖 AI ✅ DONE — closed the
  completion-sync gap via a Spring application event (`TreatmentPlanCompletedEvent`),
  avoiding a `reminder`↔`treatment` package cycle. Last task of Phase 6.

---

## PHASE 7 — AI Model Control, Batch Scanning, Multi-Treatment UX
> Goal: (1) split "vision model" from "reasoning model" as two independent user
> choices, remove every silent model-substitution fallback, and surface AI
> errors (incl. rate limits, with a wait time) to the UI instead of swallowing
> them; (2) let a user queue multiple photos in one upload session, each
> becoming its own scan, instead of reopening the dialog per plant; (3) let a
> user pick which active treatment to open when a plant has more than one, and
> fix the bug where a treatment's AI-generated disease description never
> appears even though the backend successfully generates it.
>
> **Sequencing relative to Phase 5:** not decided. Phase 5 (launch prep) was
> the prior "current focus" and hasn't started. This phase is real feature
> work, not launch infra — 👤 decide whether it ships before or after Phase 5
> before starting T7.1.
>
> Investigation already done (don't re-derive — see ARCHITECT.md once these
> tasks are recorded there):
> - **Real bug found:** `GlobalExceptionHandler.handlePlantPal()` hardcodes
>   `HttpStatus.INTERNAL_SERVER_ERROR` for every `PlantPalException`, ignoring
>   `ex.getErrorCode()`. The 429s already thrown by `IdentificationServiceImpl`
>   (AI rate limit, cure-advice rate limit) and `TreatmentServiceImpl` arrive at
>   the frontend today as a generic 500, not a 429 — there is no rate-limit UX
>   today because the status code itself is wrong.
> - **Real bug found (disease description):** it IS generated correctly —
>   `TreatmentServiceImpl.createTreatment()` → `fireDiseaseDescriptionGeneration()`
>   → async `generateAndSaveDiseaseDescription()` → saves to the row. The bug is
>   purely frontend: `TreatmentDetailComponent.loadTreatment()` fetches the
>   treatment exactly once in `ngOnInit`. Since the user navigates to
>   `/treatment/:id` immediately after `createTreatment()` returns (description
>   still null at that instant), the page shows the "Gathering info…" pending
>   state and never looks again — the async write lands seconds later into a
>   page that already stopped asking.
> - **Multi-treatment backend mostly already exists, unused:**
>   `TreatmentService.getActiveTreatmentsForPlant()` (plural),
>   `GET /plants/{id}/active-treatments`, and even
>   `TreatmentService.getActiveTreatments()` on the Angular `treatment.service.ts`
>   are all already shipped and correct. No component calls them. Both
>   `plant-detail.component.ts` and `care-card.component.ts` instead call the
>   *singular* `getActiveTreatment()` (most-recent-only) and match it against
>   one disease name — which is exactly why a second, different active disease
>   on the same plant is currently invisible to the UI.
> - **Three places with silent model fallback / silent error-swallowing to
>   remove or scope explicitly:**
>   1. `IdentificationServiceImpl.runIdentification()` — `OLLAMA_LLAVA` case
>      catches `PlantPalException` and silently re-runs on `GITHUB_GPT4O`.
>   2. `DeepSeekAnnotationClient.analyzeRegions()` — on a 429 it silently calls
>      `OllamaClient` instead; if both fail it returns `EMPTY_REGIONS` rather
>      than surfacing an error. (Its 2-attempt *retry* on the same model for a
>      GOAWAY/EOF is connection resilience, not model substitution — judgment
>      call on whether that retry survives this phase; default to keeping it,
>      only removing the cross-model substitution and the error-swallow.)
>   3. `UserServiceImpl`/`IdentificationServiceImpl.loadUserPreference()` —
>      defaults to `AiModelPreference.DEEPSEEK` when a user row is missing a
>      preference. This is a default value, not an error-path fallback; fine to
>      keep an equivalent default for the new split preferences, just flagging
>      so it isn't confused with the other two.
> - `AiModelPreference` today is one enum (`DEEPSEEK, PLANTNET, OLLAMA_LLAVA,
>   GITHUB_GPT4O`) covering both vision and text tasks at once — it doesn't
>   model "vision model" and "reasoning model" as independent choices. Next
>   free migration number is **021** (020 was `add_species_care_cards` from the
>   pre-Phase-5 cleanup pass).
> - `IdentificationResponse.aiModelUsed` (frontend model) already exists as a
>   single string field — a precedent to extend, not a green field.
> - `PhotoUploadComponent` already accepts multiple images per session, but
>   they're treated as multiple *angles of one plant* (`organs[]`, one
>   `analyze` emit, one Identification) — not multiple *different* plants each
>   becoming their own scan. `IdentificationUploadDialogComponent` closes the
>   moment `onAnalyze()` fires once, which is the "open, close, open, close"
>   friction described — confirmed at all three entry points that share this
>   dialog (Garden FAB / Species "add plant" / Plant "scan").

---

### T7.1 — Backend: split vision/reasoning model preference, remove fallback, structured AI errors 🤖 AI ✅ DONE
**Branch:** `feature/PP-039-model-control-backend` — merged. Summary in STATE.md/BACKEND.md.

**Claude Code prompt:**
```
// Phase 7 — Split AI model choice into vision + reasoning, remove model
// fallback, and make rate-limit/AI errors structured and surfaced correctly.

1. Migration 021_split_ai_model_preference.sql:
   - Add `vision_model_preference` VARCHAR(30) NOT NULL DEFAULT 'GITHUB_GPT4O'
     and `reasoning_model_preference` VARCHAR(30) NOT NULL DEFAULT 'DEEPSEEK'
     to `users`.
   - Backfill from the existing `ai_model_preference` column: GITHUB_GPT4O/
     OLLAMA_LLAVA/PLANTNET → vision_model_preference; DEEPSEEK → reasoning_model_preference
     (both columns get a sane default regardless, old column untouched — don't
     drop it this phase, mark deprecated in a comment only).
   - Append to db.changelog-master.xml in order (after 020 — verify, don't
     assume, per CLAUDE.md's Database Schema section).

2. New enums replacing single-purpose use of AiModelPreference:
   - VisionModelPreference { GITHUB_GPT4O, OLLAMA_LLAVA }  (drop PLANTNET — dead
     code per BACKEND.md's Open Items, do not carry it into the new enum)
   - ReasoningModelPreference { DEEPSEEK_R1, OLLAMA_LLAVA }
   - Keep AiModelPreference only where it's still load-bearing (check
     UserPreferencesRequest/Response usage); add the two new fields alongside,
     don't break existing callers in one shot — additive this task.

3. Remove model-fallback (do NOT remove retry-on-same-model connection
   resilience — see investigation notes above for the precise boundary):
   - IdentificationServiceImpl.runIdentification(): delete the
     OLLAMA_LLAVA catch-and-retry-on-GITHUB_GPT4O branch. On failure, let the
     PlantPalException propagate with the real provider's error message.
   - DeepSeekAnnotationClient.analyzeRegions(): delete tryOllamaFallback() and
     the EMPTY_REGIONS-on-exhaustion swallow. Keep the existing 2-attempt
     same-client retry for the GOAWAY/EOF case. On exhaustion, throw a
     PlantPalException carrying the real failure instead of returning empty
     regions silently.

4. Fix GlobalExceptionHandler.handlePlantPal(): use
   HttpStatus.resolve(ex.getErrorCode()) (falling back to 500 only if the code
   isn't a valid HTTP status) instead of hardcoding INTERNAL_SERVER_ERROR. This
   makes existing 429s (AI rate limit, cure-advice rate limit, Treatment AI
   rate limit) actually arrive at the frontend as 429, not 500.

5. Add a RateLimitException extends PlantPalException carrying
   retryAfterSeconds (Long). Add `retryAfterSeconds` (Long, NON_NULL-included)
   to ApiResponse. Compute it via Bucket4j's verbose API
   (bucket.asVerbose().tryConsumeAndReturnRemaining(1)... or, simpler given the
   fixed-window Bandwidth config already in use, the bucket's own refill
   period) at each of the three existing rate-limit throw sites
   (IdentificationServiceImpl x2, TreatmentServiceImpl) — replace their plain
   `new PlantPalException(msg, 429)` with `new RateLimitException(msg, retryAfterSeconds)`.
   Add a dedicated GlobalExceptionHandler.handleRateLimit() before the generic
   PlantPalException handler (RestControllerAdvice matches most-specific type)
   that also sets a `Retry-After` response header.

6. Add model-usage tracking to AI-generating responses that don't have it yet
   (IdentificationResponse.aiModelUsed already exists for identification —
   extend the same idea, don't reinvent the field name/shape):
   - TreatmentResponse: add `diseaseDescriptionModel` (which reasoning model
     generated diseaseDescription) and `treatmentPlanModel` (which reasoning
     model generated the craft-plan action plan).
   - CarePlanDto / CareCardDto: add a plan-level field for which model(s)
     generated the care plan (vision model alone if folded into the
     identification call, vision+reasoning if DeepSeek regenerated it).
   - SpeciesResponse (or wherever enrichment surfaces): same pattern for
     SpeciesEnrichmentService's reasoning-model output.
```

---

### T7.2 — Frontend: vision/reasoning model picker, error + rate-limit UX, "powered by" badges 🤝 Assisted ✅ DONE
**Branch:** `feature/PP-040-model-control-frontend` — summary in STATE.md/FRONTEND.md.

**Claude Code prompt:**
```
// Phase 7 — Surface model choice, AI errors, and which model served each result.

1. Update the user preferences page + shared `model-selector` component: split
   the single model dropdown into two — "Vision model" (identification +
   annotation) and "Reasoning model" (care plans, cure advice, disease
   description, species enrichment) — bound to the new backend
   visionModelPreference/reasoningModelPreference fields from T7.1.

2. Global AI-error handling (wherever AI-calling endpoints are invoked —
   identification submit, cure advice, craft-plan, chat): on a 429 response,
   read `retryAfterSeconds` from the ApiResponse error body and show a
   snackbar/toast: "Rate limit reached — try again in {time}, or switch your
   AI model in Settings" with an action button routing to preferences. On any
   other AI-call error, surface the actual backend message (now that
   GlobalExceptionHandler returns real status codes/messages per T7.1) instead
   of a generic "something went wrong" toast.

3. Add a small "Identified using: {vision model} + {reasoning model}" chip/
   badge — reusing the new per-response model-usage fields from T7.1 — to:
   identification-result, the disease detail panel, care-card (when a card's
   actionPlan came from a reasoning-model regeneration), treatment-detail
   (disease description + plan), species detail (enrichment). Keep it
   unobtrusive — a small caption under the relevant content block, not a
   prominent banner.
```

---

### T7.3 — Frontend: multi-select batch scan mode 🤝 Assisted ✅ DONE
**Branch:** `feature/PP-041-batch-scan` — summary in STATE.md/FRONTEND.md.

**Claude Code prompt:**
```
// Phase 7 — Let a user queue several different plants' photos in one upload
// session instead of reopening the dialog per plant. No backend changes
// needed — POST /analyze is already async (Kafka) and already rate-limited
// per user before publish; this is N independent calls to the existing
// endpoint, not a new batch endpoint.

1. PhotoUploadComponent: add a "Scan multiple plants" checkbox, visible only
   when there's no lockedPlantId/speciesId (i.e. only at the plain Garden
   entry point — Species/Plant entry points are inherently single-plant, see
   investigation notes; confirm this scoping before building, don't add the
   checkbox to all three entry points by default).
   - When checked: each selected file becomes its own independent queue entry
     (no organ/multi-angle grouping — that grouping is for multiple photos of
     ONE plant, which is a different, still-supported mode). Reuse the
     existing multi-file drag/drop handling in processFiles(), just change
     what an "entry" represents in this mode.

2. IdentificationUploadDialogComponent: when batch mode is active, don't close
   the dialog on the first onAnalyze(). Instead submit one /analyze call per
   queued file sequentially, reusing the existing 3s-poll-to-completion logic
   per item, and render a per-item status row (Pending / Scanning / Done /
   Failed — using T7.2's real error surfacing if a 429 hits partway through
   the batch, so the user sees exactly which items failed and why, with a
   "retry remaining" affordance rather than the batch silently stalling).
   Close (or offer a "Done") only once every item has resolved or the user
   cancels.

3. Garden page: confirm the FAB still opens the same dialog unchanged — only
   the dialog's internal behavior changes in batch mode.
```

---

### T7.4 — Frontend: multi-treatment picker modal + fix disease-description-never-shows bug 🤝 Assisted ✅ DONE
**Branch:** `feature/PP-041-batch-scan` (built in the same branch as T7.3, not its
own `feature/PP-042-multi-treatment-picker`) — summary in STATE.md/FRONTEND.md.

**Claude Code prompt:**
```
// Phase 7 — Most of the backend for this already exists and is correct (see
// investigation notes) — this is primarily a frontend wiring + bug-fix task.

1. New TreatmentPickerSheetComponent (MatBottomSheet), modeled directly on the
   existing PlantScanHistorySheetComponent pattern (same module, same
   open/close convention) — lists each entry from
   TreatmentService.getActiveTreatments(plantId) (diseaseName, status chip,
   startedAt, scan thumbnail via identificationId if present). Tapping a row
   navigates to /treatment/:id.

2. Wire it into both existing call sites that currently use the *singular*
   getActiveTreatment() and a diseaseName-equality guess:
   - plant-detail.component.ts's Scans-section CTA
   - care-card.component.ts's checkActiveTreatment()/treatment button
   Switch both to TreatmentService.getActiveTreatments() (plural, already
   exists, unused today). If the array has exactly one entry, preserve today's
   UX (navigate/match directly, no extra click). If more than one, open
   TreatmentPickerSheetComponent instead of guessing.

3. Fix TreatmentDetailComponent: it fetches the treatment exactly once in
   ngOnInit (loadTreatment()), so the AI-generated diseaseDescription — which
   really is generated async server-side seconds later — never appears
   because the page never asks again. Add the same poll-until-resolved pattern
   already established for Kafka identification polling (3s interval, same
   convention): while treatment.status === 'DRAFT' &&
   treatment.diseaseDescription == null, keep polling getTreatment(id); stop
   once diseaseDescription is non-null or after a bounded timeout (mirror
   identification's COMPLETED/FAILED stop condition — don't poll forever on a
   treatment whose generation silently failed, see TreatmentServiceImpl's
   catch-and-log-null path).
```

---

## Status Summary

| Phase | Status |
|---|---|
| 0 — Setup | ✅ Done |
| 1 — Auth + Plants | ✅ Done |
| 2 — AI Identification | ✅ Done |
| 3 — Reminders | ✅ Done except T3.3 (manual on-device testing) |
| 4 — Chat | ✅ Done (basic) — streaming/history polish not started |
| 5 — Launch | 🔲 Not started |
| 6 — Species & Treatment Restructure | ✅ Done |
| 7 — Model Control, Batch Scanning, Multi-Treatment UX | ✅ Done (T7.1–T7.4) |

---

## Enterprise Patterns Checklist

Final audit before launch — re-check every item against the real code, don't
assume from the table above:

- [ ] All list endpoints paginated (`Pageable`)
- [ ] All deletes are soft deletes (`status = ARCHIVED`)
- [ ] All entities have audit fields (`createdAt`, `updatedAt`, `createdBy`) — except
      the documented exceptions (Reminder, CareLog, PushSubscription, TreatmentPlan,
      Treatment — see CLAUDE.md's Hard Rules)
- [ ] Redis cache on all hot read paths
- [ ] Rate limiting on all AI endpoints and auth endpoints
- [ ] JaCoCo gate restored to 80% (currently 10% — see BACKEND.md Open Items)
- [ ] Testcontainers for integration tests (real PostgreSQL, not H2)
- [ ] OWASP dependency check in CI
- [ ] Structured JSON logging in prod with correlation IDs
- [ ] All secrets in environment variables, never in code — GITHUB_TOKEN rotated
- [ ] Security headers on all responses
- [ ] Swagger UI documents every endpoint with examples
- [ ] Docker + docker-compose for reproducible local dev
- [ ] Raw AI responses stored in DB for future reprocessing
- [ ] `ResourceNotFoundException` message never reveals whether resource exists or just isn't yours
- [ ] Kafka/Zookeeper has a real production story (managed add-on or fallback plan — see T5.5)
