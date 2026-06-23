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


## PHASE 8 — PlantNet as a First-Class Identification Provider
> Goal: stop treating PlantNet as a degraded, species-only vision option
> (today: single guess, `healthStatus: UNKNOWN`, no care plan, no organ tags,
> `project=all`, no reference images, quota invisible) and turn it into the
> app's botanical ground-truth layer using the v2 surface we weren't touching:
> a **ranked candidate list with confidence scores + reference images**,
> **organ-tagged multi-image accuracy**, **geolocation-ranked floras**, a
> **dedicated disease/pest classifier**, **GBIF/POWO/IUCN linkage** carried on
> every result, and **daily-quota telemetry**.
>
> **API contract — CONFIRMED against the real swagger (My Pl@ntNet API 2.2.2);
> these supersede the earlier reconstructed notes:**
> - Base `https://my-api.plantnet.org`. **api-key is a query param.** Max POST
>   **52,428,800 bytes (50 MB)**. Statuses: **404** = "Species Not Found" (the
>   non-plant reject), **413** payload too large, **415** bad mime, **429** =
>   Too Many Requests (real rate-limit status, not a generic 500).
> - `POST /v2/identify/{project}` — multipart, `images`+`organs` repeated 1–5×.
>   **Always send `type=kt`** — `legacy` is deprecated and "will be dropped."
>   Params: `include-related-images`, `no-reject` (leave false), `nb-results`
>   (default 10), `lang`, `detailed` (adds genus/family `otherResults`).
>   Response (`Identification result`): `bestMatch`, `results[]`, `language`,
>   `preferedReferential`, **`switchToProject`** (a hint that another flora would
>   identify better — surface it), `predictedOrgans[]`, `version`,
>   `remainingIdentificationRequests`, `otherResults`.
>   Each `Result`: `score`, `species{scientificName, scientificNameWithoutAuthor,
>   scientificNameAuthorship, genus{}, family{}, commonNames[]}`, `images[]`
>   (reference photos), **`gbif{id}`**, **`powo{id}`**, **`iucn{id,category}`**.
>   Each reference `Image`: `organ, author, license, citation, date,
>   url{o,m,s}` — **license/author/citation travel with the image → attribution
>   is required if we display it.**
> - `organs` enum (full): auto, leaf, flower, fruit, bark, habit, scan, branch,
>   sheet, other, drawing, seed, bud, anatomy, aerial. **Practical consumer set:
>   auto, leaf, flower, fruit, bark, habit.**
> - `GET /v2/projects?lang=&type=kt&lat=&lon=` — **lat/lon filter and SORT floras
>   by location** (this is how a Casablanca user gets the right Mediterranean/N-
>   Africa flora automatically). `Project{id,title,description,speciesCount}`.
> - `GET /v2/languages` -> string[]. `GET /v2/diseases?lang=&prefix=` -> EPPO-coded
>   disease list (`{name:"1COCCF", label, categories[]}`).
> - `POST /v2/diseases/identify` — **multi-image** (up to 5) + organs, scoped to
>   **cultivated-plant** diseases. Response (`Model62`): `results[]` where each is
>   `{name, description, score, images[]}`, `version`,
>   `remainingIdentificationRequests`.
> - `GET /v2/quota/daily?day=YYYY-MM-DD` -> `{day, quota:{identify:{count, total,
>   remaining}}}`; `GET /v2/quota/history?year=YYYY` -> per-day usage. **Real quota
>   endpoints — don't rely only on the per-scan `remainingIdentificationRequests`.**
> - Optional/secondary, noted not scheduled: `POST /v2/varieties/identify`
>   (cultivar ID), `GET /v2/projects/{project}/species/align` (reconcile a
>   free-text name onto PlantNet's referential — synonyms/fuzzy),
>   `POST /v2/embeddings` (image vectors), `GET /v2/prediction/geo/species`
>   ("what grows near me" SDM — appears [private], confirm key access).
>
> **Two forks the human must rule on before T8.1 / T8.5 run — see "Open
> Decisions". Do not start T8.1 until D1 is answered.**


---

### T8.0 — Both: expand vision/reasoning model menus to 5 each + add Claude provider 🤝 Assisted
**Branch:** `feature/PP-042-model-lineup` (verify next free PP number)

> Foundational for the rest of Phase 8 — establishes the full enum that T8.1's
> PlantNet work layers on. Adds one genuinely new provider (Claude); everything
> else is a model-string on an existing client.
>
> Target menus:
> - **Vision:** PLANTNET · GITHUB_GPT41 · GITHUB_GPT4O · OLLAMA_GEMMA3 · ANTHROPIC_CLAUDE
> - **Reasoning:** GITHUB_O4_MINI · DEEPSEEK_R1 · GITHUB_GPT41_MINI · OLLAMA_GEMMA3 · ANTHROPIC_CLAUDE

**Claude Code prompt:**
```
// Phase 8 — Expand both AI model menus to 5 options each and add Claude as a new
// provider. Most additions are model-strings on existing clients; Claude is the
// only new client. Keep the T7.1 split (VisionModelPreference /
// ReasoningModelPreference) and the loadVisionPreference()/loadReasoningPreference()
// routing pattern — extend, don't rebuild.

BACKEND

1. New AnthropicClient (serves BOTH vision and reasoning — Claude is multimodal):
   - Implements the same vision-identification + text-completion contracts as
     GitHubModelsClient / the Azure text client respectively (so the routing
     switches can treat it like any other provider).
   - Anthropic Messages API: JSON body, images as base64 content blocks (NOT
     multipart like PlantNet, NOT /api/generate like Ollama). System prompt as the
     top-level `system` field. Reuse the existing defensive JSON parsing + the
     ```-fence stripping half of stripThinkTags() (Claude won't emit <think>, but
     may wrap JSON in fences).
   - Config: anthropic.api-key, anthropic.base-url (https://api.anthropic.com),
     anthropic.models.default (claude-sonnet-4-6), optional .cheap
     (claude-haiku-4-5) / .max (claude-opus-4-8). Same Apache HttpClient 5 setup
     as the other external clients.
   - 429 handling: reuse the RateLimitException/retryAfterSeconds shape (parse
     Retry-After). Never launder to 500.

2. VisionModelPreference: add GITHUB_GPT41, OLLAMA_GEMMA3, ANTHROPIC_CLAUDE
   (PLANTNET, GITHUB_GPT4O already exist). Mark OLLAMA_LLAVA @Deprecated but keep
   it PARSEABLE (parseVisionPreference() already falls back defensively — map a
   stored OLLAMA_LLAVA to OLLAMA_GEMMA3 so existing users move to the new local
   model). Mirror the same change in the frontend VisionModelPreference type.

3. ReasoningModelPreference: add GITHUB_O4_MINI, GITHUB_GPT41_MINI, OLLAMA_GEMMA3,
   ANTHROPIC_CLAUDE (DEEPSEEK_R1 already exists). Same OLLAMA_LLAVA deprecation +
   defensive remap as above.

4. Routing — extend the existing switches (do NOT add new client classes except
   AnthropicClient):
   - runIdentification() vision switch: GITHUB_GPT41 -> GitHubModelsClient with the
     gpt-4.1 model string; OLLAMA_GEMMA3 -> OllamaClient (gemma3 is multimodal —
     send the image the same way llava-phi3 is sent today, verify /api/generate vs
     /api/chat for gemma3); ANTHROPIC_CLAUDE -> AnthropicClient.
   - Reasoning switches (getCureAdvice / craftPlan / disease-description /
     species-enrichment): GITHUB_O4_MINI and GITHUB_GPT41_MINI -> the existing
     Azure text client (currently named DeepSeekClient — same endpoint, just new
     model strings; DO NOT rename it this phase, the blast radius is large, but add
     a class-level comment that it now serves multiple Azure text models, not only
     DeepSeek). OLLAMA_GEMMA3 -> OllamaClient text. ANTHROPIC_CLAUDE -> AnthropicClient.

5. o-series request shape (o4-mini) — FLAGGED GAP, handle explicitly: o4-mini uses
   `max_completion_tokens` (NOT `max_tokens`), does NOT accept a `temperature`
   override (omit it), and exposes no <think> tags (reasoning is server-side, so
   stripThinkTags() is a no-op for it — fine). Build the request per-model in the
   Azure text client so gpt-4.1-mini (standard chat shape) and o4-mini (reasoning
   shape) don't share one rigid body.

6. Provider availability: expose which providers are actually configured (e.g.
   add an `availableProviders` / per-option `available` flag to the preferences
   response, derived from whether each provider's key/host is set —
   anthropic.api-key present? ollama host reachable/configured?). The frontend uses
   this to disable un-configured options (Claude with no key MUST be unselectable,
   or every call 401s).

7. Config strings (application.yml): github.models.identification-model gains a
   gpt-4.1 entry alongside the existing gpt-4o; the Azure text client gains o4-mini
   + gpt-4.1-mini entries alongside deepseek; ollama gains gemma3:4b; anthropic
   block as in (1). Keep all existing keys; additive only.

8. OPTIONAL migration 023 (only if you take the default change + backfill — if you
   do, BUMP the Phase 8 PlantNet migrations to 024/025/026 and update the docs):
   - reasoning_model_preference DEFAULT 'DEEPSEEK_R1' -> 'GITHUB_GPT41_MINI'
     (the recommended default switch — fast structured-JSON generation, avoids
     DeepSeek-R1's 1-call/60s upstream cap; see ARCHITECT.md). Confirm before
     applying — this changes current behavior for new users.
   - Backfill any stored OLLAMA_LLAVA -> OLLAMA_GEMMA3 in both pref columns.
   - (No migration is needed merely to add enum VALUES — the columns are plain
     VARCHAR(30); new strings just fit.)

FRONTEND

9. ModelSelectorComponent: VISION_OPTIONS (5) + REASONING_OPTIONS (5). LABEL BY
   INTENT, model name as subtitle (names churn): e.g. vision = "Best (Cloud) ·
   gpt-4.1", "Balanced (Cloud) · gpt-4o", "Offline (Local) · gemma3",
   "Botanical · Pl@ntNet", "Frontier · Claude". Reasoning similarly
   ("Best · o4-mini" / "Deep · DeepSeek-R1" / "Balanced · gpt-4.1-mini" /
   "Offline · gemma3" / "Frontier · Claude"). Tooltip each with its tradeoff
   (Pl@ntNet = species-ID only, no care plan, sets healthStatus UNKNOWN; gemma3 =
   runs locally/offline, lower accuracy; Claude = needs an Anthropic key;
   o4-mini/R1 = slower, deeper reasoning).
   - Disable any option whose provider isn't available (step 6). Show a small
     "requires setup" hint on a disabled Claude/local option rather than hiding it.
   - Update the TS union types to match the new backend enums.
   - The existing "powered by {model}" badge (T7.2) already records the ACTUAL
     model used — no change needed; it keeps the labels honest.
```

> 💡 **Why label by intent, not model name:** gpt-4o became gpt-4.1 became
> gpt-5-chat inside a year. A user who picked "Balanced" should keep getting the
> current balanced model without re-choosing when the underlying string changes.
> The badge still shows the real model, so nothing is hidden.

---

### T8.1 — Backend: PlantNet v2 client upgrade — ranked candidates, organs, reference images, quota 🤖 AI
**Branch:** `feature/PP-043-plantnet-v2-client` (verify next free PP number)

> Depends on **D1** (always-on vs. only-when-selected) and **D2** (candidate storage).

**Claude Code prompt:**
```
// Phase 8 — Upgrade PlantNetClient from a single-result guesser to a full v2
// ranked-candidate identifier. CONFIRM whether the existing client already calls
// /v2/identify; if it's on v1, migrate. Keep Apache HttpClient 5 + the HTTP/1.1
// ALPN workaround (do NOT reintroduce Java's built-in HttpClient — see ARCHITECT.md).

1. PlantNetClient.identify(images, organs, project, lang):
   - multipart POST /v2/identify/{project}; api-key as QUERY PARAM.
   - ALWAYS send type=kt (legacy is deprecated/dropping).
   - `organs` repeated to match each image in order; default any unset image to
     `auto`. Restrict the app's selectable organs to the consumer set
     (auto/leaf/flower/fruit/bark/habit).
   - Query: include-related-images=true, nb-results=<config, default 6>,
     no-reject=false, lang=<param>.
   - Enforce the 50 MB total cap client-side before sending (sum of resized
     image bytes) — fail fast with a clear message rather than eating a 413.
   - Error mapping (real statuses, NOT laundered to 500): 404 -> friendly
     "doesn't look like a plant" PlantPalException (404). 429 -> RateLimitException
     (parse Retry-After; this is the existing T7.1 shape). 413/415 -> clear client
     error. Do NOT set no-reject=true.

2. Parse the FULL ranked results[] into a PlantNetCandidate list (do NOT collapse
   to results[0] as today):
   - score, scientificName, scientificNameWithoutAuthor, scientificNameAuthorship,
     genus, family, commonNames[], gbifId (results carry gbif{id}),
     powoId (powo{id}), iucnCategory (iucn{category}),
     referenceImages[] (each: url.s + url.m, organ, author, license, citation —
     KEEP author/license/citation for attribution, cap 3 per candidate).
   - Top-level: bestMatch, switchToProject (nullable), predictedOrgans[], version,
     remainingIdentificationRequests.
   - Defensive parse, never throw on a malformed field (ActionPlanValidator
     philosophy) — a missing optional field degrades to null/empty, never fails
     the whole identification.

3. Storage (migration 023 — VERIFY next free number against db.changelog-master.xml
   and STATE.md; sequence is at 022 but the brief has guessed wrong twice, see
   ARCHITECT.md Migration Sequencing). Per D2: JSONB `plantnet_candidates` on
   `identifications` (matches care_plan / annotation_regions JSONB precedent —
   recommended) or a side table. Also persist `plantnet_version`,
   `plantnet_best_match`, `plantnet_switch_to_project`, `plantnet_quota_remaining`
   (nullable). Store the raw response too (enterprise "raw AI response for
   reprocessing" checklist item). Append to changelog IN ORDER.

4. runIdentification() PLANTNET case: returns the ranked candidate list +
   bestMatch + switchToProject, not one species. healthStatus stays UNKNOWN for a
   PlantNet-only scan (identify does NOT assess health — that's T8.5). Do not
   fabricate a care plan from a PlantNet result. Per D1, build EITHER the
   always-on-alongside-gpt-4o path OR the gated-on-VisionModelPreference.PLANTNET
   path — not both.

5. DTO surface: PlantNetCandidateDto + add plantNetCandidates, plantNetBestMatch,
   plantNetSwitchToProject, plantNetQuotaRemaining (all nullable) to
   IdentificationResponse. Reuse the nullable-field convention so the frontend
   renders nothing when absent.

6. Config (application.yml): app.plantnet.nb-results (default 6),
   app.plantnet.lang (default app language). project default comes from user prefs
   (T8.4), falling back to "all". Leave api-key sourcing unchanged.
```

> 💡 **Candidate list, not a single answer:** PlantNet returns a *ranked
> distribution with confidence*; collapsing to `results[0]` throws away exactly
> the signal Flow-1 confirmation (T8.2/T8.3) needs. The single-guess shape was our
> limitation, not the API's.

---

### T8.2 — Backend: ranked-candidate species matching (Flow 1) wired to PlantNet 🤖 AI
**Branch:** `feature/PP-044-plantnet-species-match` (verify)

**Claude Code prompt:**
```
// Phase 8 — Make Flow-1 species confirmation a ranked pick-list, not a yes/no on
// one guess. Flow-1 endpoints already exist (T6.9): GET /{id}/species-match,
// POST /{id}/resolve-species. Extend, don't replace.

1. GET /{id}/species-match: return T8.1's candidate list (top-N: scientificName,
   commonName, score 0..1, referenceImages[] with attribution, gbifId, powoId,
   iucnCategory) + a bestMatch flag/index + switchToProject (nullable). Preserve
   the single-match fast path: when exactly one candidate clears
   app.plantnet.auto-confirm-score (default ~0.90) with no close runner-up, mark
   it auto-confirmable so the UI can skip the picker.

2. POST /{id}/resolve-species: accept the chosen candidate's scientificName (or a
   "none / rescan" signal). On a choice, run the EXISTING
   SpeciesService.findOrCreate(scientificName, commonName, reasoningPreference) —
   no parallel create path. Pass the candidate's gbifId/powoId/iucnCategory
   through to the Species row (these are free, factual, straight off the identify
   response — see T8.6; this is the cheap half of "factual enrichment" with no
   extra external call).

3. Do NOT touch Flow 2 / Flow 3 (they already know their species — ARCHITECT.md's
   3-path tree). Flow-1 only.

4. SpeciesMatchDto already exists — extend it to carry the candidate list (additive;
   don't break T6.9 callers in one shot — deprecate scalar fields in a comment if
   they become redundant).
```

---

### T8.3 — Frontend: multi-candidate species confirmation UI (confidence + reference images) 🤝 Assisted
**Branch:** `feature/PP-045-species-confirm-candidates` (verify)

**Claude Code prompt:**
```
// Phase 8 — Rework species-confirm-step from yes/no into a ranked chooser.
// Backend contract is T8.2's extended /species-match.

1. species-confirm-step.component: candidate cards (vertical list). Each: common
   name (bold) + scientific name (italic), a confidence chip (whole %), up to 3
   PlantNet reference thumbnails to compare against the user's own photo
   (lazy-loaded, alt = scientific name). Tap a candidate to confirm.
   - Auto-confirmable candidate (T8.2 threshold): keep today's frictionless
     one-tap confirm, but expose a "Not this one ->" link revealing the full list.
   - Always include "None of these — rescan" -> back to upload.

2. ATTRIBUTION (required — license/author/citation travel with each reference
   image): show a small caption/tooltip per thumbnail with author + license (e.g.
   "(c) {author}, {license}"). Do not display reference images without it.

3. Surface switchToProject when present: a one-line nudge ("These may identify
   better in the {X} flora — change it in Settings"), linking to T8.4's flora
   preference. Show predictedOrgans + a one-line organ-quality hint ("flowers and
   fruits identify most accurately; bark least"). Optional: an IUCN conservation
   chip when iucnCategory indicates a threatened category.

4. Reuse ModelUsageBadge ("Candidates from Pl@ntNet {version}") and AiErrorService
   (404 non-plant -> friendly "doesn't look like a plant", back to upload). No new
   error pattern. A broken/slow thumbnail must never block confirmation
   (leaf-placeholder fallback).
```

---

### T8.4 — Both: organ tagging + geolocation-ranked flora & common-name language 🤝 Assisted
**Branch:** `feature/PP-046-plantnet-organs-projects` (verify)

**Claude Code prompt:**
```
// Phase 8 — Per-image organ tags (PlantNet rewards them) and a location-ranked
// flora + common-name language. PhotoUploadComponent already handles multiple
// angles of ONE plant via organs[] — surface and forward it.

FRONTEND:
1. Multi-angle (single-plant) mode ONLY — not batch mode (batch = different
   plants, T7.3): a small per-image organ selector
   (auto|leaf|flower|fruit|bark|habit, default auto) on each thumbnail; forward
   organs[] on the analyze emit (parallel-indexed to files if processFiles only
   tracks files today).
2. Preferences page (/preferences): a "Pl@ntNet" subsection with "Flora / region"
   and "Common-name language". The flora list comes from /v2/projects RANKED BY
   THE USER'S LOCATION when available — default-select the top-ranked one, let the
   user override. Language defaults to the app language.

BACKEND:
3. Location source for project ranking: pass lat/lon to GET /v2/projects?lat&lon.
   FLAG — confirm where a user lat/lon comes from (browser geolocation captured on
   the prefs page? a stored profile field?). If none exists, ship the manual
   dropdown first and add location-ranking when a lat/lon source lands. Do NOT
   hardcode a region.
4. Migration 024 (VERIFY): users.plantnet_project VARCHAR (default 'all'),
   users.plantnet_lang VARCHAR (nullable -> app default). Additive.
5. Thread project + lang from prefs into T8.1's identify() call
   (loadPlantNetPreferences(userId), mirroring loadVisionPreference()).
6. Cached proxy endpoints (so the frontend never calls PlantNet directly or burns
   identify quota): GET /plantnet/projects (accepts optional lat/lon),
   GET /plantnet/languages — @Cacheable, TTL 24h, ALWAYS type=kt upstream.
```

---

### T8.5 — Backend: PlantNet disease/pest cross-check feeding the Treatment flow 🤖 AI
**Branch:** `feature/PP-047-plantnet-disease-crosscheck` (verify)

> Depends on **D4** (cross-check authority on disagreement). Heaviest task —
> sequence last, after T8.1–T8.4 are proven.

**Claude Code prompt:**
```
// Phase 8 — Add PlantNet's dedicated disease classifier as a SECOND OPINION on the
// Flow-3 health scan, cross-checking gpt-4o's annotation-based detection. NOT a
// replacement. NOTE: /v2/diseases/identify is scoped to CULTIVATED-plant diseases
// — treat a low/empty result as "no corroboration", not "healthy".

1. PlantNetDiseaseClient.identifyDisease(images, organs, lang): multipart POST
   /v2/diseases/identify (up to 5 images + organs — same multi-image shape as
   identify, NOT single-image), include-related-images=true, nb-results=<cap>,
   lang. Same HttpClient 5 / api-key-as-query / 50MB cap / defensive-parse / real-
   status-mapping conventions as T8.1. Parse results[] (each: name, description,
   score, images[]) + remainingIdentificationRequests. 404/empty -> empty result,
   NOT an error. Optionally resolve `name` against GET /v2/diseases (EPPO codes +
   labels + categories) for a human-readable, canonical disease label.

2. Flow 3 (plantId+speciesId known): run identifyDisease() alongside the existing
   gpt-4o annotation (parallel CompletableFuture on aiTaskExecutor — same pattern
   as identification+annotation). Cross-check per D4:
   - AGREEMENT: raise a confidence flag, prefer the EPPO/taxonomic label for
     Treatment.diseaseName, and SEED Treatment.diseaseDescription from PlantNet's
     result.description (it ships one — saves a DeepSeek call and is factual).
   - DISAGREEMENT (D4 default rec): keep gpt-4o's diseaseName (it drives downstream
     care-plan/cure-advice), attach PlantNet's result as a flagged "second
     opinion" (label + description + reference images), mark Treatment
     NEEDS_REVIEW so the uncertainty is visible.
   - Build only the policy D4 selects.

3. Storage (migration 025, VERIFY): persist PlantNet's disease result on the
   identification (JSONB) + surface on TreatmentResponse (second-opinion block +
   agreement flag + EPPO code). Reuse T7.1's model-usage-tracking pattern to record
   PlantNet corroboration.

4. Quota/rate-limit: a Flow-3 scan may now hit gpt-4o AND /v2/diseases/identify,
   each consuming PlantNet daily quota independently from our Bucket4j 20/hour.
   Surface remainingIdentificationRequests; on a 429/quota-exhausted, map to the
   existing RateLimitException/retryAfterSeconds UX — never swallow (the T7.1
   no-silent-fallback rule applies here too).
```

---

### T8.6 — Backend: factual species enrichment (IUCN/POWO free; GBIF deeper) 🤖 AI — secondary
**Branch:** `feature/PP-048-factual-species-enrichment` (verify)

> Depends on **D3** (how far to go now). The swagger DEFLATES this task: gbifId,
> powoId and iucnCategory already arrive on every identify result (captured in
> T8.1/T8.2) — so the cheap factual layer needs NO extra API call. A GBIF/POWO
> fetch is only for *deeper* data (distribution, extended vernaculars).

**Claude Code prompt:**
```
// Phase 8 — Prefer factual taxonomy over hallucination-prone DeepSeek enrichment.
// Species.externalDataSource already supports "WIKIPEDIA"|"MANUAL" beside "AI" —
// add "GBIF"/"POWO" as needed.

1. CHEAP LAYER (no new external call — do this regardless of D3): persist the
   gbifId/powoId/iucnCategory already passed from T8.2.resolve-species onto the
   Species row. Set externalDataSource where these came from PlantNet. This alone
   gives factual family/genus/commonNames/conservation status without any extra
   request.

2. DEEPER LAYER (only if D3 says "now"): GbifClient.fetchSpecies(gbifId) -> GET
   api.gbif.org/v1/species/{id} (confirm GBIF needs no auth for read) for
   distribution + extended vernaculars; defensive parse, never throws. When a
   Species has a gbifId, prefer GBIF for taxonomy/common-names, set
   externalDataSource="GBIF", externalDataFetchedAt=now; fall back to DeepSeek ONLY
   when no gbifId or GBIF returns nothing. Care-overview PROSE can still come from
   DeepSeek (GBIF won't have it) — split factual fields (GBIF) from prose (AI).

3. Add the new externalDataSource values; migration only if the column is
   constrained (check — it may be free-text). NEEDS_REVIEW semantics unchanged.
```

---

### T8.7 — Frontend: PlantNet quota + provider telemetry 🤝 Assisted — small
**Branch:** `feature/PP-049-plantnet-quota` (verify)

**Claude Code prompt:**
```
// Phase 8 — Surface PlantNet's daily quota proactively. The swagger gives real
// quota endpoints — use them, don't only scrape the last scan's leftover count.

1. Backend: cached proxy GET /plantnet/quota -> upstream GET /v2/quota/daily
   ({day, quota.identify.{count,total,remaining}}), short TTL (e.g. 5 min).
   Optional GET /plantnet/quota/history -> /v2/quota/history?year= for a usage chart.
2. Preferences Pl@ntNet subsection (T8.4): show "Pl@ntNet: {remaining}/{total}
   identifications left today" from /plantnet/quota. Treat unknown as unknown, not
   zero. Optional small history sparkline.
3. On a PlantNet 429/quota-exhausted (the mapped RateLimitException from T8.1/T8.5),
   reuse AiErrorService's snackbar with a PlantNet-specific message + the existing
   "switch your AI model in Settings" action (gpt-4o/Ollama don't share PlantNet's
   quota). When PlantNet is the selected vision model and quota is known-low, show
   a small inline hint in ModelSelectorComponent's vision dropdown.
```

---

## Open Decisions (resolve before the dependent tasks run)

- **D1 — PlantNet always-on vs. only-when-selected** *(blocks T8.1; biggest fork)*
  Today PlantNet runs only when the user picks `VisionModelPreference.PLANTNET`.
  The "best assistant" version runs PlantNet's botanical candidate list + reference
  images *alongside* gpt-4o on every Flow-1 scan (gpt-4o for structured care-plan-
  capable vision, PlantNet for ground-truth candidates). Trade-off: richer/
  trustworthier ID vs. an extra external call + PlantNet daily-quota burn per scan
  + a second rate-limit surface. *Architect rec:* always-on for Flow-1 candidates,
  behind a feature flag so it can be turned off if quota bites. **Need the call.**

- **D2 — Candidate storage shape** *(blocks T8.1)*
  JSONB `plantnet_candidates` on `identifications` (matches care_plan /
  annotation_regions precedent — rec) vs. a normalized side table. Candidates are
  read as a unit, never queried by field -> JSONB.

- **D3 — How far to take factual enrichment now** *(shapes T8.6)*
  The cheap layer (persist gbifId/powoId/iucnCategory off the identify response) is
  near-free and should ship regardless. The deeper GBIF fetch is a separate API.
  *Rec:* ship the cheap layer in T8.6; defer the GBIF fetch unless factual
  distribution data is a launch priority.

- **D4 — Disease cross-check authority** *(blocks T8.5)*
  When gpt-4o and PlantNet disagree, who owns `Treatment.diseaseName`? *Rec:*
  agreement -> high confidence + EPPO/taxonomic label + seed diseaseDescription from
  PlantNet's `description`; disagreement -> keep gpt-4o's label (drives downstream
  care/cure generation), attach PlantNet as a flagged second opinion, mark
  Treatment NEEDS_REVIEW. **Need the call.**

- **D-location** *(shapes T8.4 — non-blocking)*
  `/v2/projects?lat&lon` ranks floras by location, which is the better UX than a
  manual picker for a Morocco-centric base. But it needs a user lat/lon source
  (browser geolocation on the prefs page, or a stored profile field) that may not
  exist yet. Ship the manual dropdown first; add location-ranking when a lat/lon
  source lands. Don't hardcode a region default.

## Sequencing
T8.1 -> T8.2 -> T8.3 are the core "ranked-candidate identification" slice — ship
together (highest value, lowest risk). T8.4 layers accuracy (organs + flora). T8.5
(disease) is heaviest — after the core is proven. T8.6 (cheap factual layer) and
T8.7 (quota) are small/secondary, slot in opportunistically.

Sequencing vs. Phase 5 (launch) is the same open question as Phase 7: this is
feature work, not launch infra. Decide whether Phase 8 ships before or after v1.0.0.



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
