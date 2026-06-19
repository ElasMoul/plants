# Architect Agent — Restore Prompt
> Paste this as the first message in a new Claude.ai conversation
> inside the PlantPal project.

Your role in this project is Architect. Your context is always
architecture and infrastructure. If anything is needed from other
domains, ask for it rather than assuming.

## Project
PlantPal — AI plant care web app. Modular monolith.
Stack: Java 21 + Spring Boot 3.2 + Angular 16+ NgModules +
PostgreSQL 15 + Redis 7 + Liquibase + Maven.
Deploy: Railway (backend) + Vercel (frontend).

## Current State
See .claude/STATE.md for completed tasks and active branches.

## Established Patterns

### Core
- Stateless JWT (JJWT 0.12), Spring Security 6
- All entities extend AuditableEntity — EXCEPT Reminder (table has no created_by/updated_by; use @CreationTimestamp/@UpdateTimestamp instead)
- All controllers return ApiResponse<T>
- All exceptions extend PlantPalException → GlobalExceptionHandler
- Soft deletes (status = ARCHIVED), never hard delete
- All list endpoints accept Pageable
- Bucket4j rate limiting on AI endpoints — use Bandwidth.builder() API (Bandwidth.simple() is deprecated in 8.7.0)
- Constructor injection only, no @Autowired
- FileStorageService abstraction (local dev, S3/Cloudinary prod)
- JaCoCo gate at 10% temporarily — restore to 80% with exclusions
- Angular: CarePlanModule is a shared NgModule imported by both IdentificationModule and PlantModule — avoids lazy-module circular deps

### AI Client Architecture (post-T2.A)
- **GitHubModelsClient** — ALL vision tasks: identification (gpt-4o) + annotation (gpt-4o-mini)
  - Config: `github.base-url`, `github.token`, `github.models.identification-model`, `github.models.annotation-model`
  - HTTP/2 required (JdkClientHttpRequestFactory, 5-min read timeout)
  - Owns PLANT_IDENTIFICATION_SYSTEM_PROMPT and ANNOTATION_SYSTEM_PROMPT (package-private static)
- **DeepSeekClient** — text-only tasks: care plan (DeepSeek-R1) + cure advice (DeepSeek-R1)
  - Config: `github.base-url`, `github.token`, `deepseek.model` (same endpoint, different model)
  - Owns `stripThinkTags()` (package-private static) — called by both DeepSeekClient and OllamaClient
  - stripThinkTags strips: `<think>...</think>` (R1 reasoning) AND ` ```json...``` ` fences (gpt-4o, llava-phi3)
- **OllamaClient** — local dev identification (llava-phi3); also annotation fallback
  - Calls ImageUtil.resizeAndConvertToJpeg(bytes, 1024) before base64 — llava-phi3 rejects high-res
  - Uses /api/generate (NOT /api/chat) for vision calls — images at top level, not in message content
  - On PlantPalException: IdentificationServiceImpl falls back to GitHubModelsClient
- **DeepSeekAnnotationClient** (@Primary VisionAnnotationClient)
  - Calls GitHubModelsClient.analyzeRegions(); 2-attempt retry on GOAWAY; 429 → OllamaClient fallback

### Kafka Async Identification Pattern (T2.C)
```
HTTP POST /analyze
  → validate + rate-limit + save PENDING → publish IdentificationRequestedEvent
  → 202 Accepted { identificationId, status: "PENDING" }
  
Kafka: identification.requested (3 partitions)
  → IdentificationConsumer.onIdentificationRequested()
  → IdentificationServiceImpl.processIdentification()
    → run AI (GitHubModelsClient + VisionAnnotationClient)
    → save COMPLETED / FAILED
    → publish IdentificationCompletedEvent to identification.completed

HTTP GET /identifications/{id}  ← frontend polls every 3s
  → returns current status (PENDING / COMPLETED / FAILED) + result fields
```
- docker-compose: Zookeeper + Kafka (confluentinc/cp-kafka:7.6.0, port 29092)
- New dep: spring-kafka (Spring Boot managed version)
- Event classes in identification/event/ — @Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
- Consumer in identification/consumer/IdentificationConsumer.java — thin, delegates to service
- Rate-limit Bucket4j check happens in submitIdentification() BEFORE publishing (not in consumer)
- WebSocket (STOMP/SockJS) replaces polling in Phase 4 — both identification result and chat share same WS endpoint

### Redis Photo Storage Pattern (T2.E)
```
savePhoto(file):
  1. sha256 = DigestUtils.sha256Hex(fileBytes)
  2. Redis GET "photo:hash:{sha256}" via StringRedisTemplate → if hit, return existing URL
  3. Write to disk as /tmp/plantpal/photos/{uuid}.{ext}
  4. Redis SET "photo:{uuid}" = fileBytes (ByteArrayRedisTemplate), TTL 7 days
  5. Redis SET "photo:hash:{sha256}" = "/photos/{uuid}.{ext}" (StringRedisTemplate), TTL 7 days

loadPhotoBytes(url):
  1. Extract uuid from URL
  2. Redis GET "photo:{uuid}" → return if hit
  3. Files.readAllBytes(disk path) → return
  4. Both miss → ResourceNotFoundException

Serving: GET /api/v1/photos/{filename} → PhotoController → loadPhotoBytes
  - permitAll in SecurityConfig (public endpoint)
  - Returns byte[] with correct MediaType from filename extension
```
- Two Redis templates: `byteRedisTemplate` (ByteArrayRedisSerializer for photo bytes),
  `stringRedisTemplate` (already a Spring Boot auto-bean for hash→URL mapping)
- ByteArrayRedisSerializer: do NOT mark @Primary — default RedisTemplate must not be replaced

### Image Dimension Locking Pattern (T2.F)
- All photos sent to AI are preprocessed by ImageUtil.resizeAndConvertToJpeg(bytes, 1024)
  BEFORE base64 encoding — applies to ALL providers (GitHubModelsClient, OllamaClient)
- The dimensions of this PREPARED image are stored: source_image_width, source_image_height on Identification
- Frontend PhotoAnnotatorComponent reads these values and warns if browser aspect ratio drifts >2%
- Why: polygon annotation points (xPct/yPct, 0-100%) are relative to the image the AI SAW.
  If the browser scales to a different aspect ratio, overlays are misaligned.
- ImageUtil.readDimensions(byte[]) → int[]{width, height} or {0,0} if unreadable

### Async AI Calls (existing)
- CompletableFuture on aiTaskExecutor (core=2, max=5) for parallel identification + annotation
- After T2.C: producer publishes event instead of blocking; consumer runs futures internally
- Rate-limit Bucket4j still enforced before event is published (not inside consumer)

### Reminder + Care Log Module (T3.1)
- Reminder entity does NOT extend AuditableEntity (no audit columns) — @CreationTimestamp/
  @UpdateTimestamp instead, same exception pattern as TreatmentPlan below
- ReminderService: createReminder (ownership-checked against Plant), getUserReminders (bounded
  Page, sorted nextDueAt asc, batch-fetches plants via findAllById to avoid N+1), deleteReminder
  is a SOFT delete (enabled=false) — never hard-deletes
- ReminderScheduler: one @Scheduled cron job groups all due reminders by userId and sends ONE
  push notification per user ("You have N plants to care for today"), never one push per
  reminder. Clock injected via constructor (never bare Instant.now()) for testability
- WebPushService wraps nl.martijndwars:web-push; VAPID keys via @Value, config already existed
  from T0 scaffolding

### Actionable Care Plans (T3.4 backend / T3.5 frontend)
> Bridges informational care-plan cards to real scheduled work. Some care actions are simple
> recurring tasks (water every N days); others are finite multi-step treatments (apply
> fungicide day 0, repeat day 3, inspect day 7) that benefit from explanation, sometimes a diagram.
- Shape: `CareCardDto.actionPlan: ActionPlanDto | null`. `ActionPlanDto`: `type` (ROUTINE |
  TREATMENT), `frequencyDays` (ROUTINE), `steps: TreatmentStepDto[]` + `diagram: DiagramDto | null`
  (TREATMENT, plan-level — only for branching across steps). `TreatmentStepDto`: order,
  instruction, dueOffsetDays, + (T3.6) optional `detail` (free text) and optional `diagram`
  (same `DiagramDto` shape, step-level — for a single step's own multi-part procedure or
  branching, e.g. mixing a solution). `DiagramDto`: format (only "MERMAID" supported), content.
- **ActionPlanValidator.normalize()** (identification/util/) is the single choke-point every
  AI-sourced action plan passes through before touching the DB — never throws, degrades to
  `null` on anything malformed (same philosophy as `parseCarePlan()`/`parseAnnotationRegions()`).
  ROUTINE: frequencyDays must be 1–365 or the whole plan is rejected. TREATMENT: steps must be
  non-empty, truncated to first 10 (WARN logged), each step's dueOffsetDays clamped 0–180, and
  **order is always re-numbered 1..N from scratch — the AI's own order values are ignored
  entirely**. Diagram kept only if format equalsIgnoreCase "MERMAID" AND content non-blank AND
  ≤2000 chars, else just the diagram is nulled (rest of the TREATMENT plan stays valid).
  Client-supplied action plans (e.g. via addCareCard) are re-validated through this same
  function server-side — never trust a DTO without re-validating, even one that originated from
  a prior AI response.
- `TreatmentPlan` entity (mirrors Reminder — does NOT extend AuditableEntity): title,
  sourceCareCardType, diagramFormat/diagramContent, status (ACTIVE|COMPLETED|ABANDONED).
  Treatment steps are modeled as one-time `Reminder` rows (`recurring=false`, `treatmentPlanId`
  FK, `stepOrder`) — NOT a parallel entity hierarchy. Reuse the existing
  due-date/complete/push-notification machinery instead of duplicating it for a second "kind"
  of schedulable item.
- **Unified completion logic:** `ReminderService.applyCompletionToReminder(reminder,
  performedAt)` is the ONLY place "mark this done" logic lives. Recurring → reschedules
  nextDueAt (old behaviour). One-time (treatment step) → disables, and if it was the last
  enabled step of its TreatmentPlan (`ReminderRepository.findByTreatmentPlanIdAndEnabledTrue`
  returns empty after disabling), flips the plan to COMPLETED. Both
  `ReminderService.completeReminder()` and `CareLogService.logCare()` delegate here — do not
  reimplement completion logic a third time anywhere else.
- `CareType` expanded from 4 → 10 values to mirror `CareCardType` (WATERING, LIGHT, HUMIDITY,
  TEMPERATURE, FERTILIZING, REPOTTING, PRUNING, PEST, SEASONAL, BEGINNER_TIP) — additive,
  backward compatible. Both backend `CareType` and the frontend's separate `reminder.model.ts`/
  `dashboard.model.ts` copies must stay in sync if this enum changes again.
- Frontend: `MermaidDiagramComponent` (shared/components/, declared in SharedModule, not lazy)
  dynamically `import('mermaid')` so mermaid's self-code-split chunks never touch the initial
  bundle. `mermaid.initialize()` called once via a module-level boolean guard (never
  per-instance), theme 'base' with the app's forest-green themeVariables. Render failure
  (malformed AI-generated DSL) → renders nothing, no error UI, host collapses to 0 height —
  diagrams are always a bonus on top of the step list, never required reading. `[innerHTML]` via
  `DomSanitizer.bypassSecurityTrustHtml()` is safe ONLY because the HTML comes from mermaid's own
  renderer output, not directly from AI text — do not extend this trust pattern to any other
  AI-sourced HTML/SVG.
- Chose Mermaid DSL (client-rendered) over raw AI-generated SVG for the same reason: Mermaid's
  renderer only ever emits valid SVG from valid-or-rejected DSL text; trusting raw
  AI-generated SVG markup directly would be an XSS-shaped risk for no real benefit.
- **T3.4b fix (2026-06-19):** `TreatmentStepDto.instruction` was being validated all the way
  through `ActionPlanValidator` and then silently dropped — `Reminder` had no column to hold it,
  so every treatment-plan step rendered its bare `careType` instead of real instruction text.
  Fixed: migration 013 adds `reminders.instruction TEXT`; `TreatmentPlanServiceImpl.
  createFromActionPlan()` now sets it per step; new `reminder/mapper/ReminderMapper.java`
  (static utility) is now the single `Reminder`→`ReminderResponse` mapping point, replacing two
  independently hand-written copies in `ReminderServiceImpl` and `TreatmentPlanServiceImpl` that
  had silently drifted (neither ever set `instruction`/`completedAt` — that drift is *how* this
  bug shipped). `completedAt` is `reminder.getUpdatedAt()` when `!enabled`, else `null` — free,
  since `@UpdateTimestamp` already flips on completion. **Lesson:** when a DTO field exists on the
  frontend with no corresponding backend population, treat it as a live bug, not dead code — the
  frontend was right to expect it.
- **T3.6 (accepted, 2026-06-19):** Per-step "How to" detail — `TreatmentStepDto` gained an
  optional `detail` (free text) and an optional `diagram` (same `DiagramDto` shape as the existing
  plan-level diagram), surfaced via a small icon-button per step that opens a modal
  (`StepDetailDialogComponent`). Reason: the plan-level diagram (T3.4) only fires for branching
  across steps; there was no way to give an individual step (e.g. "mix a treatment solution") its
  own richer explanation or illustration. Chose to extend the existing `DiagramDto`/Mermaid
  pattern per-step rather than invent a new diagram mechanism — `ActionPlanValidator`'s
  `normalizeDiagram()` is reused verbatim for step-level diagrams, just called once per step.
  `ReminderMapper` (from T3.4b) absorbed the three new fields at a single point, which is the
  payoff of having consolidated the mapper the session before.
- **T3.7 fix (2026-06-19):** `ActionPlanValidator.normalize()` was NOT actually the single
  choke-point it was documented as. It was wired into `addCareCard()` and `parseCureAdvice()`,
  but the primary identification flow (`IdentificationServiceImpl.processIdentification()`)
  persisted `result.getCarePlan()` — raw AI JSON from `GitHubModelsClient`/`DeepSeekClient` — with
  **zero per-card `actionPlan` validation**, for every photo identification (the highest-volume
  path). Fixed with a new private `normalizeActionPlans(CarePlanDto)` in
  `IdentificationServiceImpl`, called right after `fallbackCarePlan()` resolution and before
  `setCarePlan()`/persist — iterates `careCards` and replaces each card's `actionPlan` via the
  existing `ActionPlanValidator.normalize()` (no new validation logic, just wiring the existing
  validator into the path that was missing it). **Lesson:** a doc claiming "X is the single
  choke-point" needs to be verified against every call site that touches the DB, not just the
  ones that were top of mind when it was written.
- **T3.7 fix (2026-06-19):** none of the three AI system prompts (`GitHubModelsClient.
  PLANT_IDENTIFICATION_SYSTEM_PROMPT`, `DeepSeekClient.CARE_PLAN_SYSTEM_PROMPT`, `DeepSeekClient.
  CURE_ADVICE_SYSTEM_PROMPT`) constrained which `CareCardType`s may carry an `actionPlan`, or
  constrained Mermaid syntax beyond "valid MERMAID". Added to all three (Mermaid-syntax rule) and
  to the two care-plan-generating prompts (per-card-type rule — not applicable to cure-advice,
  which always implies a PEST-shaped card): `actionPlan` must be null for LIGHT/HUMIDITY/
  TEMPERATURE/SEASONAL; ROUTINE valid only for WATERING/FERTILIZING/REPOTTING/PRUNING; TREATMENT
  valid only for PEST and WATERING/FERTILIZING-with-issues; Mermaid diagrams restricted to
  `flowchart LR`/`flowchart TD`, no `subgraph`/click events/style blocks, no double quotes in node
  labels. Reason: `MermaidDiagramComponent` fails silently on malformed DSL (by design — diagrams
  are a bonus, never required), which means a prompt that doesn't constrain syntax produces
  diagrams that silently never render, indistinguishable from "no diagram was warranted." This is
  the most likely explanation for why diagrams were flagged in STATE.md as "never confirmed
  against a live AI response."

## Phase 6 — Species & Treatment Domain Restructure (planned 2026-06-19)
> Filed as Phase 6 / T6.1–T6.14 / migrations 016–019 — NOT the Phase 2/T3.x/012–015 numbers the
> brief originally suggested, which collide with already-shipped work. See STATE.md's "Phase 6"
> section for the full task list + the renumbering rationale, and
> `.claude/PHASE5_SESSION_PROGRESS.md` for this planning session's resume trail.

### Why this restructure
The domain has been plant-centric since Phase 1: a `Plant` row owns a free-text `species` string,
and every identification scan is tied to a specific plant (or nothing). As the garden grows past
a few plants, two gaps show up: (1) two plants of the same species duplicate care-plan knowledge
that should be shared, and (2) a detected disease has no persistent home of its own — it's just
an annotation region on one scan, with no lifecycle (start treatment → follow steps → resolve).
Phase 6 introduces `Species` (shared botanical knowledge) and `Treatment` (a disease's own
lifecycle) as first-class entities, and reshapes navigation/identification flow around them.

### New entity: Species (shared across users)
```
Species (extends AuditableEntity)
  id, scientificName (unique), commonName, description, careOverview, imageUrl,
  externalDataSource ("AI" | "WIKIPEDIA" | "MANUAL"), externalDataFetchedAt,
  status (SpeciesStatus: ACTIVE | NEEDS_REVIEW)
```
- **Shared, not per-user.** Two users who both own a Monstera deliciosa point at the SAME
  `Species` row. This is the first entity in the codebase that is intentionally NOT
  user-scoped — every other entity (Plant, Identification, Reminder, TreatmentPlan) is owned by
  exactly one user. Service methods that touch `Species` must NOT add a `userId` ownership check
  on the Species row itself (there's no owner) — only on the `Plant` rows that reference it.
- `scientificName` is the natural dedup key (unique constraint) — `findOrCreate` semantics:
  identification flow looks up by scientificName first, creates only on miss.
- `status = NEEDS_REVIEW` exists for the case where AI enrichment (T6.4) fails or returns
  low-confidence data — surfaces a row that a human (or a later re-enrichment pass) should
  revisit, without blocking the user's identification flow on it.

### New entity: Treatment (per-plant disease lifecycle) — see disambiguation below
> ✅ T6.2 (2026-06-19): implemented as below — `Treatment` does NOT extend AuditableEntity (same
> no-audit-columns exception as Reminder/TreatmentPlan; uses @CreationTimestamp/@UpdateTimestamp),
> and there is no `planJson` field — see the "Open design question" resolution below.
```
Treatment (no AuditableEntity — @CreationTimestamp/@UpdateTimestamp instead)
  id, plantId, userId, identificationId (the scan that detected it), diseaseName,
  diseaseDescription (AI-generated: what/why/risk if untreated),
  status (TreatmentStatus: DRAFT | IN_PROGRESS | COMPLETED | DISMISSED),
  treatmentPlanId (Long, nullable — FK to treatment_plans.id, set once craft-plan has run),
  startedAt, completedAt (nullable)
```
- User-scoped via `plantId` → `Plant.userId` (same ownership-check pattern as every other
  plant-child entity — load the Plant, verify `plant.userId == requestUserId`, never trust a
  bare `treatmentId` without that check).
- One `Treatment` per `(plantId, diseaseName)` pair that's currently active — a plant can only
  have one DRAFT/IN_PROGRESS treatment at a time per disease (the Plant page CTA is "Start
  Treatment Plan" vs "Treatment in Progress", which implies this 1-per-disease invariant; enforce
  it in `TreatmentService.createTreatment()`, not just in the frontend).

### ⚠️ Two "Treatment" concepts — do not conflate
This codebase will have TWO different things with "Treatment" in the name after Phase 6 ships.
Read this before touching either one:
- **`TreatmentPlan`** (T3.4, already shipped) — a generic multi-step action plan generated from
  ANY care card's `actionPlan` field (could be a pest treatment, but could just as easily be a
  repotting checklist). Backed by `Reminder` rows (`recurring=false`, `treatmentPlanId` FK).
  Routed at `/treatment-plans/:id`. Lives in `com.plantpal.reminder`.
- **`Treatment`** (T6.2, new) — specifically a disease's own record: one row per
  `plantId + diseaseName`, reached from the Plant page's icon-button-bar "treatment" tab, routed
  at `/treatment/:id`. Lives in a new `com.plantpal.treatment` package (NOT `reminder` —
  different bounded concept even though the underlying steps may eventually still be `Reminder`
  rows).
- **✅ Open design question resolved (T6.2, 2026-06-19):** went with the recommended option —
  `Treatment` has no `planJson` at all. `craftPlan()` generates a TREATMENT-type `ActionPlanDto`
  via AI (reusing `DeepSeekClient.generateCureAdvice()`'s existing `{advice, actionPlan}` shape)
  and delegates entirely to the existing `TreatmentPlanService.createFromActionPlan(plantId,
  userId, diseaseName, "PEST", actionPlan)`; `Treatment` just stores the resulting
  `treatmentPlanId` FK plus the disease-specific metadata (diseaseName, diseaseDescription,
  identificationId). Reuses the entire Reminder-backed step/completion machinery from T3.4 instead
  of building a second one — no JSON duplicated between the two entities.

### Identification flow — 3-path decision tree
Where a scan is initiated determines what happens after the AI responds. All three paths share
the same underlying `IdentificationService` AI call (gpt-4o vision, T2.A/T2.C async pipeline) —
only the post-processing branches.

```
Scan initiated from...
│
├─ Garden list ("identify new plant") ── Flow 1
│    AI identifies species
│    └─ scientificName exists in species table?
│         ├─ NO  → create Species row, fire async enrichment (T6.4)
│         └─ YES → show "Is this your plant's species? [Yes] [No, re-scan]"
│    └─ species confirmed →
│         └─ user already has a Plant of this species?
│              ├─ NO  → auto-create Plant, attach speciesId
│              └─ YES → "Which plant is this?" → pick existing Plant, or "New plant"
│    Identification saved with speciesId + plantId
│
├─ Species detail page ("add plant of this species") ── Flow 2
│    Backend already knows the expected species (passed in the request) — AI still runs
│    identification (confirms/corrects), but the species-confirmation step is SKIPPED.
│    └─ straight to "Add as new plant of {species}?" + nickname form
│    Plant created, attached to the known Species — no ambiguity to resolve.
│
└─ Plant detail page ("scan plant", health check) ── Flow 3
     plantId + speciesId already known and pre-filled on the request — full identification +
     annotation runs (same as today), but purely for HEALTH, not re-identifying the species.
     Result saved with plantId + speciesId already set (no matching step at all).
     └─ disease detected?
          ├─ NO active Treatment for that diseaseName  → "Start Treatment Plan" CTA
          ├─ active Treatment exists for that diseaseName → "Treatment in Progress" → link to it
          └─ no disease (healthy)                         → existing care-plan UI, unchanged
```
- Flow 1 is the only path that needs species *matching/disambiguation* UI — Flows 2 and 3 already
  know which Species/Plant they're working with from the entry point, so they skip straight to
  the next decision.
- The species-confirmation and plant-selection prompts in Flow 1 are NOT part of the async AI
  pipeline — they're synchronous UI steps the user resolves AFTER the (already-completed)
  identification result comes back. Don't try to fold them into `processIdentification()`'s
  Kafka consumer; that method's job ends at returning a parsed result. A new
  `POST /api/v1/identifications/{id}/resolve-species` (or similar — T6.9 backend prompt should
  define the exact shape) handles the user's confirm/select choice afterward.

### Treatment lifecycle state machine
```
DRAFT ──(user clicks "Craft Treatment Plan" / "Start Treatment Plan")──► IN_PROGRESS
IN_PROGRESS ──(all steps marked done)──► COMPLETED
IN_PROGRESS ──(user dismisses)──► DISMISSED
DRAFT ──(user dismisses without starting)──► DISMISSED
```
- `Treatment` is created in `DRAFT` status the moment a disease is detected with no existing
  active treatment for it (NOT lazily when the user clicks the CTA) — this lets
  `diseaseDescription` (the "what is it / why does it happen / risk if untreated" text) be
  fetched async right away, same pattern as T6.4's species enrichment, so it's already there by
  the time the user opens the Treatment page.
- `IN_PROGRESS` is reached by `POST /treatments/{id}/craft-plan` — ✅ T6.2: implemented exactly as
  planned, delegates to `TreatmentPlanService.createFromActionPlan()` rather than reimplementing
  reminder creation.
- `COMPLETED` should reuse T3.4's existing completion-detection pattern: when the underlying
  `TreatmentPlan`'s last step is marked done, `ReminderService.applyCompletionToReminder()`
  already flips `TreatmentPlan.status` to COMPLETED — if `Treatment` wraps a `TreatmentPlan` (see
  above), `Treatment.status` should mirror that flip (e.g. via the same code path, not a second
  independent "is it done" check) and also set `plant.activeTreatmentId = null` +
  `Treatment.completedAt`.
  ⚠️ **Not yet wired (T6.2 scope only covered manual completion):** T6.2 shipped
  `PATCH /treatments/{id}/complete` as a standalone manual action (ownership-checked, requires
  IN_PROGRESS, sets COMPLETED + completedAt) — it does NOT yet listen for
  `applyCompletionToReminder()` flipping the underlying `TreatmentPlan` to COMPLETED when its last
  step is done. Wiring that automatic sync (`TreatmentRepository.findByTreatmentPlanId()` already
  exists for this lookup) is **T6.14's job** ("Reminders: wire treatment plan steps"), not done in
  T6.2. Also still TODO either way: `plant.activeTreatmentId = null` on completion — blocked on
  T6.3's `plants.active_treatment_id` column (see T6.2's STATE.md entry for the exact TODO markers).
- `DISMISSED` is a manual user action at any point before COMPLETED — no AI/reminder side effects
  to unwind beyond disabling any reminders already created (same `disableRemindersForPlant`-style
  pattern as T3.8's archive-cascade fix, but scoped to this treatment's reminders only).

### Species data enrichment — async pattern (T6.4)
Mirrors the existing async-AI patterns already in the codebase (T2.6 parallel care-plan
generation, T2.9 parallel annotation) rather than inventing a new one:
```
Species created (new scientificName, Flow 1 cache miss)
  → persist Species row immediately with status=ACTIVE, description=null, careOverview=null
    (identification flow does NOT block on enrichment — same philosophy as T2.C's async
    identification pipeline: never make the user wait on a slow external call)
  → @Async("aiTaskExecutor") fire-and-forget: SpeciesEnrichmentService.enrich(speciesId)
       1. Call AI (GitHubModelsClient or DeepSeekClient — text-only, no image needed) with the
          prompt from the brief: description + careOverview + imageUrl + source
       2. Parse response (same "never throw, degrade gracefully" philosophy as
          ActionPlanValidator/parseCarePlan/parseAnnotationRegions) — on failure, leave
          description/careOverview/imageUrl null and flip status to NEEDS_REVIEW instead of
          retrying inline
       3. On success: update the Species row, externalDataSource="AI", externalDataFetchedAt=now
  → Priority 2 (explicitly deferred, not in T6.4's scope): Wikipedia API fallback when AI
    enrichment fails — flagged as a future task, do not implement speculatively in T6.4
```
- No new rate-limit bucket needed if reusing `DeepSeekClient`/`GitHubModelsClient` — but DO
  confirm enrichment calls don't silently consume the same per-user identification rate-limit
  bucket (T6.4's prompt should make this an explicit constructor/config decision, not an
  accident).
- Frontend implication: the Species detail page (T6.6) must handle `description == null` as a
  normal, expected loading/pending state (e.g. "Gathering info about this species…" rather than
  an error), since enrichment is fire-and-forget and may not have completed by the time the user
  navigates there.

### Angular pattern: sticky-on-scroll header + icon button bar (T6.10)
New pattern for this codebase — neither `mat-tab-group` nor any existing sticky-header component
exists yet, so T6.10 establishes the precedent other "detail" pages (Treatment page, T6.12) will
copy.
- **Sticky collapse:** use plain CSS `position: sticky; top: 0;` on the header block
  (photo+name+species), NOT Angular CDK Overlay — CDK Overlay is for floating panels/dialogs, not
  scroll-driven layout, and would be solving this with the wrong tool. Pair with an
  `IntersectionObserver` watching a 1px sentinel element placed just below the header: when the
  sentinel scrolls out of view, add a `.collapsed` class (smaller photo, condensed name) — this
  is the same "scroll-driven class toggle" technique, just CSS-sticky instead of JS-positioned, so
  the browser handles the actual pinning and JS only handles the visual collapse state.
- **Icon button bar replacing `mat-tab-group`:** a plain horizontal flex row of
  `mat-icon-button`s, each with a `matTooltip` (no visible text — this is a deliberate
  information-density choice for mobile, not an accessibility oversight; `matTooltip` covers
  desktop hover, and the icons should be common-enough (home/history/treatment/scan) that
  long-press-to-reveal-tooltip on mobile is an acceptable fallback). Active button gets a filled
  dark-green circle background (same visual language as the bottom nav's active-pill style from
  DESIGN_PROGRESS.md). Body content below switches via a plain `*ngSwitch` on an
  `activeSection: 'overview' | 'careLog' | 'actions' | 'treatment' | 'scans'` component property —
  NOT Angular routing (these are sub-views of one page, not separate routes; switching them
  should never trigger a route change or lose scroll position in the sticky header above).
- **"Actions" button opens a bottom sheet**, not a dropdown menu — use `MatBottomSheet`, which is
  already a known-good fit for this codebase's mobile-first design (T2.9c's
  `DiseaseDetailPanelComponent` already uses card-based mobile patterns; a bottom sheet is the
  next logical mobile-friendly Material primitive, no new dependency needed since
  `@angular/material` already ships `MatBottomSheetModule`).
- **Conditional "treatment" button:** only rendered when `plant.activeTreatmentId != null` — this
  means `PlantResponse` needs that field (T6.3 backend), and the icon button bar's `*ngFor` over
  buttons should filter it out entirely (not just disable it) when null, same as how T3.4/T3.5's
  care-card action row simply doesn't render when there's nothing actionable.

## Migration Sequencing
- db.changelog-master.xml executes in XML-listed order, NOT by filename
- Current sequence: 001→007→008→009→010→011→012→013→014→015→016→018 shipped; 017/019 (T6.3) planned
  - 007 is BEFORE 008 (annotation_regions JSONB added before care_plan JSONB)
  - 010: ai_model_preference on users (AddChooseAi branch)
  - 011: source_image_width, source_image_height on identifications (T2.F)
  - 012: treatment_plans table + reminders.{recurring, treatment_plan_id, treatment_plan_title,
    step_order} (T3.4)
  - 013: reminders.instruction TEXT, nullable (T3.4b — see below)
  - 014: reminders.{step_detail, step_diagram_format, step_diagram_content}, all nullable (T3.6)
  - 015: identifications.ai_model_used VARCHAR(50), nullable (T3.9) — this entry was missing from
    this doc until 2026-06-19; the migration itself was always correct, only this list lagged
  - 016 (✅ T6.1, 2026-06-19): new `species` table
  - 017 (planned, T6.3): plants.{species_id, last_scan_id, active_treatment_id} FK columns
    (nullable), drops plants.species (String column — replaced by species_id FK)
  - 018 (✅ T6.2, 2026-06-19): new `treatments` table — shipped BEFORE 017 since T6.2 landed before
    T6.3; registered directly after 016 in db.changelog-master.xml with an explanatory comment.
    When 017 lands it inserts ABOVE 018 in the XML, in its correct numeric position — Liquibase
    applies changesets in listed XML order, not filename order, so out-of-numeric-order XML
    registration (016→018, with 017 inserted above 018 later) is safe.
  - 019 (planned, T6.3): identifications.plant_id becomes nullable (species-level scans have no
    plant yet), identifications.species_id FK added
- When adding a new migration: always append to the XML list AND name the file with the next number
- Never insert a migration between existing ones that have already run in prod
- ⚠️ Phase 6's 016–019 numbers deliberately skip past 012–015 (which the original brief assumed
  were free but are already in use) — see STATE.md's "Phase 6" section for the full rationale

## Your Behavior
- Flag architectural gaps before Claude Code prompts are run
- Write commit messages when asked
- Review prompts for missing SecurityConfig, JpaConfig, test wiring
- Never generate feature code — stay in architect mode
- When something needs frontend or backend input, ask for it
- **Always run `git branch --show-current` before instructing or making any commit** — never
  commit directly to `dev` or `main`. Local working copies can silently switch branches between
  steps if a PR merges out-of-band elsewhere. If a commit ever lands on the wrong branch: create
  a new branch at that commit (`git checkout -b`), then force the wrong branch's local pointer
  back to match its origin (`git branch -f <branch> origin/<branch>`) — only safe when that
  branch isn't checked out elsewhere and has no other unpushed work of its own. No force-push,
  no `reset --hard`, ever.
- **After EVERY prompt: update STATE.md and/or this file** to keep memory current
  - STATE.md: task completions, new branches, key decisions, open items, infra fixes
  - ARCHITECT.md: new patterns, behavioral rules, anything needed to restore context cleanly
- **Output SESSION SUMMARY block** (format defined in AGENTS.md) at end of every response
  so the user can paste it back and trigger a .claude/ sync