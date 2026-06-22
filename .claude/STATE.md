# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-06-22 (T7.1 backend shipped — see new section below;
> T7.2 frontend in progress in parallel; pre-Phase-5 cleanup pass entry below
> is from a prior session)

## Current Phase
**Phases 0–4 and 6 are fully shipped, plus a pre-Phase-5 cleanup pass closing
every gap flagged below. Phase 7's backend task (T7.1) is also shipped; T7.2
(frontend) is in progress. Phase 5 (Launch prep) and Phase 7's T7.3/T7.4
haven't started** — see TASK_PLAN.md for task breakdowns. One manual item is
stranded from Phase 3: T3.3 (on-device push notification + PWA testing) still
needs a real phone and hasn't been done.

| Phase | Status |
|---|---|
| 0 — Setup | ✅ Complete |
| 1 — Auth + Plant Management | ✅ Complete |
| 2 — AI Plant Identification | ✅ Complete |
| 3 — Reminders + Care Plans | ✅ Complete except T3.3 (manual device testing) |
| 4 — AI Chat | ✅ Complete, incl. streaming + conversation history |
| 5 — Launch prep | 🔲 Not started |
| 6 — Species & Treatment Domain Restructure | ✅ Complete (T6.1–T6.14, incl. T6.7/T6.8 which had shipped but were previously undocumented here) |
| 7 — Model Control, Batch Scanning, Multi-Treatment UX | 🔲 T7.1 (backend) done; T7.2 (frontend) in progress ← **current focus** |
| — Pre-Phase-5 cleanup pass | ✅ Complete (`feature/PP-038-pre-phase5-cleanup`) |

## T7.1 — Backend: Model Control + Structured AI Errors (2026-06-22, `feature/PP-039-model-control-backend`)
Full detail in BACKEND.md's "T7.1" section — summary here for cross-session
context. Landed on its own branch, merging straight to `dev` (no frontend
coupling needed for this part — T7.2 reads the new preference fields and
`retryAfterSeconds`/model-usage fields independently).
- Split AI model choice into `visionModelPreference`/`reasoningModelPreference`
  (migration 021, additive — old `aiModelPreference` kept, deprecated).
- Removed every silent cross-model fallback: `IdentificationServiceImpl`'s
  OLLAMA_LLAVA→GITHUB_GPT4O retry, and `DeepSeekAnnotationClient`'s
  429→Ollama fallback + empty-regions-on-exhaustion swallow. Both now
  propagate a real `PlantPalException`/429 instead.
- `GlobalExceptionHandler` now resolves the actual HTTP status from
  `PlantPalException.getErrorCode()` (was hardcoded to 500) — existing 429s
  reach the frontend correctly for the first time.
- New `RateLimitException` (carries `retryAfterSeconds` via Bucket4j's
  `ConsumptionProbe`) wired at 3 sites, including `TreatmentServiceImpl
  .craftPlan()` which had no rate limit at all before this.
- Model-usage tracking fields added across `CarePlanDto`, `CareCardDto`,
  `TreatmentResponse`, `SpeciesResponse` (migration 022) — these are what
  T7.2's "powered by" badges read.
- 198/198 unit tests pass (one test rewritten to match the no-fallback
  behavior); `mvn spotless:apply` clean.
- **Not done in T7.1**: no service yet actually reads
  `visionModelPreference`/`reasoningModelPreference` to pick a client — they're
  readable/writable via the API but not yet load-bearing. That wiring is
  follow-up work, naturally sequenced after T7.2 ships a UI to set them.

## Pre-Phase-5 Cleanup Pass (2026-06-21)
Closed every gap BACKEND.md/FRONTEND.md had flagged as open, plus three bugs
found via live testing, on one branch (`feature/PP-038-pre-phase5-cleanup`,
backend and frontend changes kept in separate commits):
- **Reminder double-completion**: a one-time reminder (e.g. a treatment step)
  could be completed more than once — backend guard
  (`ReminderServiceImpl.applyCompletionToReminder()` throws on a disabled
  non-recurring reminder) + frontend synchronous in-flight/done guard (see
  FRONTEND.md's new Established Pattern).
- **Duplicate treatment creation**: `DiseaseDetailPanelComponent` and
  `CareCardComponent` each used to call `TreatmentPlanService` directly,
  bypassing the `Treatment` entity's one-active-per-disease protection —
  consolidated onto `CareCardComponent` → `TreatmentService` as the sole path.
- **Species AI-preference routing**: `SpeciesEnrichmentServiceImpl` was
  hardcoded to DeepSeek regardless of the user's saved AI model choice — now
  threads `AiModelPreference` through from `IdentificationServiceImpl.
  resolveSpecies()`.
- **Structured species care cards**: species enrichment now also generates a
  small AI `careCards` array (migration 020), rendered on the Species
  Overview tab via the existing `CarePlanModule`.
- **Plant page care cards moved to Overview** (were previously buried in the
  Scans section, alongside scan-specific UI that has nothing to do with them).
- **Chat conversation history + SSE streaming**: backend `chatStream()` +
  `POST /chat/stream` (SseEmitter), frontend incremental rendering via
  Angular's `HttpDownloadProgressEvent.partialText` (keeps the JWT
  interceptor working, unlike a raw `fetch()` bypass) — closes out the Phase 4
  polish item that had been open since T4.2.
- **Species "recently scanned" filter** now works (`lastScanAt` added).
- **Dead code removed**: `IdentificationResultComponent`.
- **4 missing controller integration tests written**
  (`IdentificationControllerIT`, `TreatmentPlanControllerIT`,
  `TreatmentControllerIT`, `SpeciesControllerIT`) — in writing them, discovered
  the ApplicationContext had *never* successfully booted under the test
  profile at all (`application-test.yml` was missing placeholders for
  `app.plantnet.api-key`/`github.token`, and the VAPID web-push keys were
  non-EC placeholder strings that fail real EC-point decoding) — fixed, so
  these and the pre-existing `PlantControllerIT`/`AuthControllerIT` can now
  actually run. See BACKEND.md's Test Inventory for the "run one at a time,
  not batched" caveat (Testcontainers connection-pool contention observed on
  this dev machine when running 4 in one JVM).
- **JaCoCo gate set to a real number**: 55%, just under the unit suite's
  actual ~58.9% line coverage — was a hardcoded, never-achieved 10% before.
- 198/198 unit tests passing, all 4 new ITs pass individually, `mvn clean
  verify` is BUILD SUCCESS (spotless clean, checkstyle clean, coverage gate
  met).

## What's Built

### Phases 0–1 — Setup, Auth, Plant CRUD
Spring Boot + Angular skeletons, Docker Compose (Postgres 15 + Redis 7), CI/CD,
JWT auth (Spring Security 6), full Plant CRUD with Redis caching. Stable, no open
items.

### Phase 2 — AI Plant Identification
Photo → AI identification pipeline, now fully async: `POST /analyze` publishes to
Kafka and returns 202 immediately, a consumer runs the AI calls off the HTTP
thread, the frontend polls `GET /{id}` every 3s. Identification uses
`GitHubModelsClient` (gpt-4o vision) with `DeepSeekClient` (DeepSeek-R1 text) for
care plans/cure advice; `OllamaClient` (llava-phi3) is the local-dev/fallback
option. Visual annotation draws polygon overlays (disease/plant/healthy-area
regions) on the photo, with a disease detail panel offering AI cure advice.
Photos are stored on disk with a Redis cache + SHA-256 dedup layer. A garden
health dashboard (now folded into the Home page, see Phase 6) aggregates
overdue/today reminders and health trends. See ARCHITECT.md for the Kafka, Redis
photo, and image-dimension-locking patterns in full — they're durable
architecture, not session history.

### Phase 3 — Reminders + Care Plans
Full reminder CRUD + scheduler (one daily push per user, not per reminder) +
web-push (VAPID). Care plan cards generated by AI can carry an `actionPlan`:
either a ROUTINE recurring reminder or a multi-step TREATMENT plan (backed by
one-time `Reminder` rows under a `TreatmentPlan`), with optional Mermaid-diagram
illustrations. `ReminderService.applyCompletionToReminder()` is the single place
"mark done" logic lives — see ARCHITECT.md. **Open:** T3.3, on-device manual
testing of push delivery and PWA installability — needs a human with a phone,
never completed.

### Phase 4 — AI Chat
Single-turn chat wired to Ollama with full garden context, later extended (T6.13)
to accept an optional `plantId` for plant-specific context (last scan health,
active treatment). Streaming responses and conversation history are known,
unstarted polish — not blocking launch.

### Phase 6 — Species & Treatment Domain Restructure
Restructured the domain from plant-centric to species-centric. All 14 tasks
shipped:

| Task | What it added |
|---|---|
| T6.1 | `Species` entity (shared across users) + `GET /species/{id}`, `GET /species/mine` |
| T6.2 | `Treatment` entity (per-plant disease lifecycle) — wraps `TreatmentPlan`, doesn't duplicate it |
| T6.3 | `Plant.speciesId/lastScanId/activeTreatmentId`, `Identification.speciesId` FK columns |
| T6.4 | Async AI species enrichment (description/careOverview/imageUrl) |
| T6.5 | Garden page restructured to species-first cards (`/garden`) |
| T6.6 | Species detail page (`/garden/species/:id`, Overview + Plants tabs) |
| T6.7 | Home page (`/home`) — greeting, quick stats, needs-attention, recent scans |
| T6.8 | Bottom nav expanded to 5 items: Home / Garden / Identify / Reminders / Chat |
| T6.9 | Identification 3-path species/plant matching flow (Garden vs. Species vs. Plant entry points) |
| T6.10 | Plant page rebuilt: sticky header + icon button bar (replaces `mat-tab-group`) |
| T6.11 | Plant page Scans section + "Start Treatment Plan" CTA |
| T6.12 | Treatment page (`/treatment/:id`) |
| T6.13 | Chat plant-context injection (`?plantId=` query param) |
| T6.14 | Backend event-driven sync: `TreatmentPlan` completion now flips the wrapping `Treatment` to COMPLETED automatically |

The full domain model (Species/Treatment shapes, the "two Treatment concepts"
disambiguation, the identification decision tree, the lifecycle state machine) now
lives in ARCHITECT.md as a permanent reference — read that, not this list, before
extending any of this.

## Active Branches
Current branch: **`feature/PP-038-pre-phase5-cleanup`** — see the cleanup pass
section above for what it carries. Not yet merged to `dev`/`master` — needs a PR.

`feature/PP-030-treatment-entity` (T6.2 + T6.14) was already merged to `dev`
via PR #50 (confirmed in git log) — superseded, the old "open a PR for it"
task below is stale and removed.

All other Phase 6 feature branches (`PP-029`, `031`–`037`) are already merged to
`dev` via PR (confirmed in git log). Phases 0–2's feature branches
(`PP-001`–`PP-027`, `AddChooseAi`, `chatfix`, etc.) are long-merged and still
sitting locally/remotely — safe cleanup candidate whenever convenient, not
urgent.

## Next Tasks (in order)
1. Open a PR / merge `feature/PP-038-pre-phase5-cleanup` to `dev`.
2. **Phase 7 — Model Control, Batch Scanning, Multi-Treatment UX** (TASK_PLAN.md
   T7.1–T7.4) — planned 2026-06-22, not yet started. Sequencing vs. Phase 5 is
   an open decision (see below).
3. **Phase 5 — Launch prep** (TASK_PLAN.md T5.1–T5.8): prod config, performance,
   security hardening, API docs, deploy to Railway/Vercel, beta test, release
   v1.0.0.
4. 👤 **Decide Phase 5 vs. Phase 7 ordering** — Phase 7 is real feature work
   (model control, batch scanning, multi-treatment UX), Phase 5 is launch
   infra; neither blocks the other technically.
5. T5.5 needs a decision on Kafka/Zookeeper's production story (managed add-on,
   or fall back to synchronous identification for v1.0.0) — not yet decided, see
   ARCHITECT.md's Kafka pattern note.
6. T3.3 — manual on-device testing (push notifications, PWA installability,
   offline reading) — needs a real phone, can happen any time before launch.
7. Live-verify chat SSE streaming against a real Docker stack (written and
   passes locally but never confirmed end-to-end this session — see
   FRONTEND.md's Open Items).

## Phase 7 — Planning Session (2026-06-22)
Not started, planned only. Full task breakdown in TASK_PLAN.md (T7.1–T7.4).
Three asks, all grounded against the real code before planning (see
TASK_PLAN.md's Phase 7 investigation notes for the full detail — not
duplicated here):
- **Model control:** split the single `AiModelPreference` into independent
  vision/reasoning choices, remove silent cross-model fallback (found in
  `IdentificationServiceImpl.runIdentification()`'s OLLAMA_LLAVA→GITHUB_GPT4O
  catch and `DeepSeekAnnotationClient`'s 429→Ollama fallback +
  empty-regions-on-exhaustion swallow), and fix a real bug:
  `GlobalExceptionHandler.handlePlantPal()` hardcodes HTTP 500 for every
  `PlantPalException`, so the 429s already thrown by `IdentificationServiceImpl`/
  `TreatmentServiceImpl` arrive at the frontend mislabeled as 500 today — there
  is no working rate-limit UX yet because the status code itself is wrong.
- **Batch scanning:** frontend-only — `POST /analyze` is already async/Kafka
  and already rate-limited per user, so queuing N independent scans is N calls
  to the existing endpoint, not a new batch endpoint.
- **Multi-treatment picker:** the backend is mostly already shipped and
  unused — `TreatmentService.getActiveTreatmentsForPlant()` (plural),
  `GET /plants/{id}/active-treatments`, and even
  `TreatmentService.getActiveTreatments()` on the Angular service all exist
  and work; no component calls them. Also found and scoped a real bug:
  `TreatmentDetailComponent` fetches the treatment once in `ngOnInit` and
  never again, so the async-generated `diseaseDescription` (which the backend
  does correctly generate and save within seconds) never appears on-screen —
  not a generation bug, a missing-poll bug.

## Known Tech Debt
See BACKEND.md and FRONTEND.md's own "Open Items" sections for the current,
maintained list (JaCoCo gate at 55% not 80%, ITs not wired into `mvn verify`,
PlantNetClient dead-code cleanup, GITHUB_TOKEN rotation before prod, etc.) —
not duplicated here to avoid the two copies drifting apart.
