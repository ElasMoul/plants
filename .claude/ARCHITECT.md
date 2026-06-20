# Architect Agent — Restore Prompt
> Paste this as the first message in a new Claude.ai conversation
> inside the PlantPal project.

Your role in this project is Architect. Your context is always
architecture and infrastructure. If anything is needed from other
domains, ask for it rather than assuming.

## Project
PlantPal — AI plant care web app. Modular monolith.
Stack: Java 21 + Spring Boot 3.2 + Angular 16+ NgModules +
PostgreSQL 15 + Redis 7 + Kafka + Liquibase + Maven.
Deploy: Railway (backend) + Vercel (frontend).

## Current State
Phases 0–4 and 6 are shipped. **Phase 5 (Launch prep) is the only phase left**,
plus one stranded manual item (T3.3 — on-device push/PWA testing). See
.claude/STATE.md for the session-by-session history and active branches, and
.claude/TASK_PLAN.md for Phase 5's task breakdown.

## Established Patterns

### Core
- Stateless JWT (JJWT 0.12), Spring Security 6
- All entities extend AuditableEntity — EXCEPT Reminder, CareLog, PushSubscription,
  TreatmentPlan, Treatment (none of these tables have created_by/updated_by;
  use @CreationTimestamp/@UpdateTimestamp instead). Species DOES extend
  AuditableEntity but is the one entity with no per-row ownership (see Domain
  Model section below) — don't confuse "extends AuditableEntity" with "is
  user-owned."
- All controllers return ApiResponse<T>
- All exceptions extend PlantPalException → GlobalExceptionHandler
- Soft deletes (status = ARCHIVED), never hard delete
- All list endpoints accept Pageable
- Bucket4j rate limiting on AI endpoints — use Bandwidth.builder() API (Bandwidth.simple() is deprecated in 8.7.0)
- Constructor injection only, no @Autowired
- FileStorageService abstraction (local dev, S3/Cloudinary prod)
- JaCoCo gate at 10% temporarily — restore to 80% with exclusions (Phase 5 item)
- Angular: CarePlanModule is a shared NgModule imported by both IdentificationModule and PlantModule — avoids lazy-module circular deps
- Angular: every lazy feature module re-provides the `@Injectable()` services it needs
  in its own `providers:` array (none of PlantService/IdentificationService/
  ReminderService/TreatmentService/TreatmentPlanService are `providedIn: 'root'`) —
  this is deliberate, not an oversight; expect to add a provider line when a new
  lazy module starts calling an existing service.

### AI Client Architecture
- **GitHubModelsClient** — ALL vision tasks: identification (gpt-4o) + annotation (gpt-4o-mini)
  - Config: `github.base-url`, `github.token`, `github.models.identification-model`, `github.models.annotation-model`
  - HTTP/2 required (JdkClientHttpRequestFactory, 5-min read timeout)
  - Owns PLANT_IDENTIFICATION_SYSTEM_PROMPT and ANNOTATION_SYSTEM_PROMPT (package-private static)
- **DeepSeekClient** — text-only tasks: care plan (DeepSeek-R1), cure advice (DeepSeek-R1),
  disease description (DeepSeek-R1, used by Treatment), species enrichment (DeepSeek-R1)
  - Config: `github.base-url`, `github.token`, `deepseek.model` (same endpoint, different model)
  - Owns `stripThinkTags()` (package-private static) — called by DeepSeekClient,
    GitHubModelsClient, and OllamaClient
  - stripThinkTags strips: `<think>...</think>` (R1 reasoning) AND ` ```json...``` ` fences (gpt-4o, llava-phi3)
  - One AI client class per *modality* (vision vs. text), not one per feature — new
    text-only AI features (disease description, species enrichment) were added as new
    methods on the existing DeepSeekClient rather than new client classes
- **OllamaClient** — local dev identification (llava-phi3); also annotation fallback
  - Calls ImageUtil.resizeAndConvertToJpeg(bytes, 1024) before base64 — llava-phi3 rejects high-res
  - Uses /api/generate (NOT /api/chat) for vision calls — images at top level, not in message content
  - On PlantPalException: IdentificationServiceImpl falls back to GitHubModelsClient
- **DeepSeekAnnotationClient** (@Primary VisionAnnotationClient)
  - Calls GitHubModelsClient.analyzeRegions(); 2-attempt retry on GOAWAY; 429 → OllamaClient fallback

### Kafka Async Identification Pattern
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
- Event classes in identification/event/ — @Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
- Consumer in identification/consumer/IdentificationConsumer.java — thin, delegates to service
- Rate-limit Bucket4j check happens in submitIdentification() BEFORE publishing (not in consumer)
- WebSocket (STOMP/SockJS) was deferred to a future phase as a polling replacement — not started,
  not blocking launch. Note for T5.5: Kafka/Zookeeper need a real production hosting story
  (managed add-on, or fall back to synchronous identification for v1.0.0) — not yet decided.

### Redis Photo Storage Pattern
```
savePhoto(file):
  1. sha256 = DigestUtils.sha256Hex(fileBytes)
  2. Redis GET "photo:hash:{sha256}" via StringRedisTemplate → if hit, return existing URL
  3. Write to disk as /tmp/plantpal/photos/{uuid}.{ext}
  4. Redis SET "photo:{uuid}" = fileBytes (byteRedisTemplate), TTL 7 days
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
- Two Redis templates: `byteRedisTemplate` (RedisSerializer.byteArray() for photo bytes —
  NOT ByteArrayRedisSerializer, which is package-private and won't compile),
  `stringRedisTemplate` (Spring Boot auto-bean for hash→URL mapping)
- byteRedisTemplate: do NOT mark @Primary — default RedisTemplate must not be replaced

### Image Dimension Locking Pattern
- All photos sent to AI are preprocessed by ImageUtil.resizeAndConvertToJpeg(bytes, 1024)
  BEFORE base64 encoding — applies to ALL providers (GitHubModelsClient, OllamaClient)
- The dimensions of this PREPARED image are stored: source_image_width, source_image_height on Identification
- Frontend PhotoAnnotatorComponent reads these values and warns if browser aspect ratio drifts >2%
- Why: polygon annotation points (xPct/yPct, 0-100%) are relative to the image the AI SAW.
  If the browser scales to a different aspect ratio, overlays are misaligned.
- ImageUtil.readDimensions(byte[]) → int[]{width, height} or {0,0} if unreadable

### Async AI Calls
- CompletableFuture on aiTaskExecutor (core=2, max=5) for parallel identification + annotation
- Producer publishes a Kafka event instead of blocking; consumer runs futures internally
- Rate-limit Bucket4j still enforced before the event is published (not inside the consumer)
- Same-class fire-and-forget calls (e.g. Treatment's disease-description generation) use
  `CompletableFuture.runAsync(..., aiTaskExecutor)` directly rather than `@Async` — Spring's
  `@Async` proxy has no effect on self-invocation (a method calling another method on `this`)

### Reminder + Care Log Module
- Reminder entity does NOT extend AuditableEntity (table has no audit columns) — @CreationTimestamp/
  @UpdateTimestamp instead, same exception pattern as TreatmentPlan/Treatment below
- ReminderService: createReminder (ownership-checked against Plant), getUserReminders (bounded
  Page, sorted nextDueAt asc, batch-fetches plants via findAllById to avoid N+1), deleteReminder
  is a SOFT delete (enabled=false) — never hard-deletes
- ReminderScheduler: one @Scheduled cron job groups all due reminders by userId and sends ONE
  push notification per user ("You have N plants to care for today"), never one push per
  reminder. Clock injected via constructor (never bare Instant.now()) for testability
- WebPushService wraps nl.martijndwars:web-push; VAPID keys via @Value
- **Archive cascade:** `PlantServiceImpl.archivePlant()` disables (never deletes) every enabled
  Reminder for that plant — an archived plant must not keep generating push notifications or
  showing up in reminder/dashboard views. Apply this same cascade to any future per-plant
  schedulable entity.

### Actionable Care Plans (Care Cards → Reminders / Treatment Plans)
> Bridges informational care-plan cards to real scheduled work. Some care actions are simple
> recurring tasks (water every N days); others are finite multi-step treatments (apply
> fungicide day 0, repeat day 3, inspect day 7) that benefit from explanation, sometimes a diagram.
- Shape: `CareCardDto.actionPlan: ActionPlanDto | null`. `ActionPlanDto`: `type` (ROUTINE |
  TREATMENT), `frequencyDays` (ROUTINE), `steps: TreatmentStepDto[]` + `diagram: DiagramDto | null`
  (TREATMENT, plan-level — only for branching across steps). `TreatmentStepDto`: order,
  instruction, dueOffsetDays, optional `detail` (free text) and optional `diagram` (same
  `DiagramDto` shape, step-level — for a single step's own multi-part procedure, e.g. mixing a
  solution; surfaced via a small icon-button per step that opens `StepDetailDialogComponent`).
  `DiagramDto`: format (only "MERMAID" supported), content.
- **`ActionPlanValidator.normalize()`** (identification/util/) is the single choke-point every
  AI-sourced action plan passes through before touching the DB — never throws, degrades to
  `null` on anything malformed (same philosophy as `parseCarePlan()`/`parseAnnotationRegions()`).
  ROUTINE: frequencyDays must be 1–365 or the whole plan is rejected. TREATMENT: steps must be
  non-empty, truncated to first 10 (WARN logged), each step's dueOffsetDays clamped 0–180, and
  **order is always re-numbered 1..N from scratch — the AI's own order values are ignored
  entirely**. Diagram kept only if format equalsIgnoreCase "MERMAID" AND content non-blank AND
  ≤2000 chars, else just the diagram is nulled. Each step's `detail`/`diagram` are normalized
  independently of the plan-level diagram — one can be present while the other is null.
  Client-supplied action plans (e.g. via addCareCard) are re-validated through this same
  function server-side — never trust a DTO without re-validating, even one that originated from
  a prior AI response.
  - **Verify this is actually wired into every call site that persists an `actionPlan`**, not
    just the ones that feel central — it was once documented as "the single choke-point" while
    actually missing from the highest-volume path (the main identification flow persisted raw AI
    JSON with zero validation for a full release). Treat "X is the single choke-point" claims in
    this file as something to grep-verify, not assume, before relying on them.
- `TreatmentPlan` entity (mirrors Reminder — does NOT extend AuditableEntity): title,
  sourceCareCardType, diagramFormat/diagramContent, status (ACTIVE|COMPLETED|ABANDONED).
  Treatment steps are modeled as one-time `Reminder` rows (`recurring=false`, `treatmentPlanId`
  FK, `stepOrder`) — NOT a parallel entity hierarchy. Reuse the existing
  due-date/complete/push-notification machinery instead of duplicating it for a second "kind"
  of schedulable item.
- **Unified completion logic:** `ReminderService.applyCompletionToReminder(reminder,
  performedAt)` is the ONLY place "mark this done" logic lives. Recurring → reschedules
  nextDueAt. One-time (treatment step) → disables, and if it was the last enabled step of its
  TreatmentPlan (`ReminderRepository.findByTreatmentPlanIdAndEnabledTrue` returns empty after
  disabling), flips the plan to COMPLETED **and publishes `TreatmentPlanCompletedEvent`** so the
  `treatment` package can sync a wrapping `Treatment`'s status too (see Domain Model section
  below — this is how the two packages stay decoupled). Both `ReminderService.completeReminder()`
  and `CareLogService.logCare()` delegate here — do not reimplement completion logic a third
  time anywhere else.
- `CareType` has 10 values mirroring `CareCardType` (WATERING, LIGHT, HUMIDITY, TEMPERATURE,
  FERTILIZING, REPOTTING, PRUNING, PEST, SEASONAL, BEGINNER_TIP). Both backend `CareType` and the
  frontend's separate `reminder.model.ts`/`dashboard.model.ts` copies must stay in sync if this
  enum changes again.
- `ReminderMapper` (reminder/mapper/, static utility) is the single `Reminder`→`ReminderResponse`
  mapping point — both `ReminderServiceImpl` and `TreatmentPlanServiceImpl` delegate to it. Two
  independent hand-written copies of this mapping once silently drifted (neither set
  `instruction`/`completedAt`) for a full release before being caught — **when a DTO field exists
  on the frontend with no corresponding backend population, treat it as a live bug, not dead
  code.** `completedAt` is `reminder.getUpdatedAt()` when `!enabled`, else `null` — free, since
  `@UpdateTimestamp` already flips on completion.
- All three AI system prompts (`GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT`,
  `DeepSeekClient.CARE_PLAN_SYSTEM_PROMPT`, `DeepSeekClient.CURE_ADVICE_SYSTEM_PROMPT`) constrain
  Mermaid diagrams to `flowchart LR`/`flowchart TD`, no `subgraph`/click events/style blocks, no
  double quotes in node labels — `MermaidDiagramComponent` fails silently on malformed DSL (by
  design, diagrams are always a bonus, never required), so an unconstrained prompt produces
  diagrams that silently never render. The two care-plan-generating prompts also constrain which
  `CareCardType`s may carry an `actionPlan` at all (null for LIGHT/HUMIDITY/TEMPERATURE/SEASONAL;
  ROUTINE only for WATERING/FERTILIZING/REPOTTING/PRUNING; TREATMENT only for PEST and
  WATERING/FERTILIZING-with-issues).

### Shared `TreatmentStepListComponent`
`treatment-plan-detail.component` (the original `/treatment-plans/:id` page) and the newer
`/treatment/:id` Treatment page both need to render a `TreatmentPlanResponse`'s `steps` —
diagram + step list + mark-done + step-detail-dialog. That UI lives once, in
`shared/components/treatment-step-list/` (`TreatmentStepListComponent`, declared + exported by
`SharedModule`) — `@Input() steps`/`@Input() diagramContent`, `@Output() stepCompleted` (parent
owns reloading the plan). `StepDetailDialogComponent` lives alongside it in `shared/components/`
for the same reason: a dialog opened via `MatDialog.open()` must be declared in an NgModule the
caller has loaded, and `ReminderModule`/`TreatmentModule` are independent lazy modules that both
need it. **Pattern for next time:** when a second lazy module needs a component/dialog only one
other lazy module currently owns, move it to `SharedModule` rather than duplicating it — two
consumers justifies extraction (same call made for `health-badge.util.ts`).

### Angular pattern: sticky-on-scroll header + icon button bar
Used by the Plant page and the Treatment page — the precedent for any future "detail" page that
needs more sections than comfortably fit a `mat-tab-group` on mobile.
- **Sticky collapse:** plain CSS `position: sticky; top: 0;` on the header block (photo+name),
  NOT Angular CDK Overlay (that's for floating panels/dialogs, not scroll-driven layout). Paired
  with an `IntersectionObserver` watching a 1px sentinel placed just below the header: sentinel
  out of view → add a `.collapsed` class (smaller photo, condensed name). The browser handles
  the actual pinning; JS only handles the visual collapse state.
- **Icon button bar replacing `mat-tab-group`:** a plain horizontal flex row of
  `mat-icon-button`s, each with a `matTooltip` (icon-only — deliberate information-density choice
  for mobile, not an accessibility oversight). Active button gets a filled dark-green circle
  background (same token as the bottom nav's active-pill style). Body content switches via a
  plain `*ngSwitch` on an `activeSection` component property — NOT Angular routing (these are
  sub-views of one page; switching them must never trigger a route change or lose scroll
  position in the sticky header above).
- The Species detail page deliberately uses `mat-tab-group` instead — this icon-button-bar
  pattern is Plant/Treatment-page-specific, not a blanket replacement for tabs everywhere.
- No shared `StickyHeaderComponent` exists yet — both pages duplicate the CSS/
  IntersectionObserver wiring. Worth extracting if a third page needs this pattern.

## Domain Model: Species & Treatment
> Introduced 2026-06-19/20 (Phase 6) to fix two plant-centric gaps: two plants of the same
> species couldn't share care-plan knowledge, and a detected disease had no persistent
> lifecycle of its own (just an annotation on one scan). Both entities are now fully shipped
> and load-bearing — read this section before touching identification, the Plant page, or
> anything disease/treatment-related.

### Species (shared across users)
```
Species (extends AuditableEntity)
  id, scientificName (unique), commonName, description, careOverview, imageUrl,
  externalDataSource ("AI" | "WIKIPEDIA" | "MANUAL"), externalDataFetchedAt,
  status (SpeciesStatus: ACTIVE | NEEDS_REVIEW)
```
- **Shared, not per-user.** Two users who both own a Monstera deliciosa point at the SAME
  `Species` row — the only entity in the codebase intentionally NOT user-scoped. Service methods
  touching `Species` must never apply a per-user ownership check on the Species row itself — only
  on the `Plant` rows that reference it.
- `SpeciesServiceImpl.findOrCreate(scientificName, commonName)` is the dedup entry point —
  looks up by scientificName first, creates only on miss, then fires
  `SpeciesEnrichmentService.enrich(speciesId)` async (never blocks the identification flow on it).
- `status = NEEDS_REVIEW` marks a row where AI enrichment failed or returned low-confidence
  data — surfaces for a human or a later re-enrichment pass without blocking identification.
- **Species data enrichment (async, fire-and-forget):**
  ```
  Species created (new scientificName)
    → persist immediately, status=ACTIVE, description=null, careOverview=null
      (never block the user on a slow external call)
    → @Async("aiTaskExecutor") SpeciesEnrichmentService.enrich(speciesId)
         1. DeepSeekClient.generateSpeciesEnrichment() — text-only, no image needed
         2. Parse defensively (same "never throw" philosophy as ActionPlanValidator) — on
            failure, leave fields null and flip status to NEEDS_REVIEW
         3. On success: update the row, externalDataSource="AI" (hardcoded — never trust the
            AI's own echoed "source" field), externalDataFetchedAt=now
  ```
  No dedicated rate-limit bucket — confirmed via grep that `com.plantpal.species` has no Bucket4j
  usage and enrichment shares no bucket with the per-user identification limits.
  Frontend implication: any page showing `Species.description` must treat `null` as a normal
  "Gathering info about this species…" pending state, not an error — enrichment may simply not
  have finished yet.

### Treatment (per-plant disease lifecycle)
```
Treatment (no AuditableEntity — @CreationTimestamp/@UpdateTimestamp instead)
  id, plantId, userId, identificationId (the scan that detected it), diseaseName,
  diseaseDescription (AI-generated: what/why/risk if untreated),
  status (TreatmentStatus: DRAFT | IN_PROGRESS | COMPLETED | DISMISSED),
  treatmentPlanId (Long, nullable — FK to treatment_plans.id, set once craft-plan has run),
  startedAt, completedAt (nullable)
```
- User-scoped via `plantId` → `Plant.userId` — load the Plant, verify `plant.userId ==
  requestUserId`, never trust a bare `treatmentId` without that check.
- One `Treatment` per `(plantId, diseaseName)` pair may be DRAFT/IN_PROGRESS at a time —
  enforced both by a partial unique DB index (`idx_treatments_active_per_disease`) and an
  application check in `TreatmentService.createTreatment()`.
- **`Treatment` wraps `TreatmentPlan`, it does not duplicate it.** `craftPlan()` generates a
  TREATMENT-type `ActionPlanDto` via AI (reusing `DeepSeekClient.generateCureAdvice()`'s
  existing `{advice, actionPlan}` shape) and delegates entirely to the existing
  `TreatmentPlanService.createFromActionPlan(plantId, userId, diseaseName, "PEST", actionPlan)`;
  `Treatment` just stores the resulting `treatmentPlanId` FK plus disease-specific metadata. No
  step/reminder logic is duplicated between the two entities.
- `Treatment` is created in `DRAFT` status the moment a disease is detected with no existing
  active treatment for it (not lazily on user click) — this lets `diseaseDescription` generate
  async right away, same pattern as Species enrichment, so it's already there by the time the
  user opens the Treatment page.

### ⚠️ Two "Treatment" concepts — do not conflate
This codebase has TWO different things with "Treatment" in the name. Read this before touching
either one:
- **`TreatmentPlan`** — a generic multi-step action plan generated from ANY care card's
  `actionPlan` field (could be a pest treatment, but could just as easily be a repotting
  checklist). Backed by `Reminder` rows (`recurring=false`, `treatmentPlanId` FK). Routed at
  `/treatment-plans/:id`. Lives in `com.plantpal.reminder`.
- **`Treatment`** — specifically a disease's own record: one row per `plantId + diseaseName`,
  reached from the Plant page's icon-button-bar "treatment" tab, routed at `/treatment/:id`.
  Lives in `com.plantpal.treatment`.
- `com.plantpal.treatment` depends on `com.plantpal.reminder` (for `TreatmentPlanService`). The
  reverse dependency (reminder → treatment) would create a package cycle — see the lifecycle
  sync below for how that's avoided.

### Treatment lifecycle state machine
```
DRAFT ──(user clicks "Craft Treatment Plan")──► IN_PROGRESS
IN_PROGRESS ──(all steps marked done)──► COMPLETED
IN_PROGRESS ──(user dismisses)──► DISMISSED
DRAFT ──(user dismisses without starting)──► DISMISSED
```
- `IN_PROGRESS` is reached by `POST /treatments/{id}/craft-plan`, which delegates to
  `TreatmentPlanService.createFromActionPlan()` and sets `plant.activeTreatmentId`.
- **`COMPLETED` is fully wired backend-side.** When `ReminderService.applyCompletionToReminder()`
  flips a `TreatmentPlan` to COMPLETED (its last step disabled), it publishes a Spring
  `TreatmentPlanCompletedEvent` (`com.plantpal.reminder.event`). A `@EventListener` in
  `com.plantpal.treatment.event.TreatmentPlanCompletionListener` consumes it and calls
  `TreatmentService.syncFromTreatmentPlanCompletion(treatmentPlanId)` — looks up the `Treatment`
  via `TreatmentRepository.findByTreatmentPlanId()`, no-ops if none found or not IN_PROGRESS
  (covers plain ROUTINE `TreatmentPlan`s with no wrapping `Treatment`). `completeTreatment()`
  (manual `PATCH /treatments/{id}/complete`) and this event-driven sync share a private
  `markCompleted(Treatment, Optional<Plant>)` helper so the status-flip + `activeTreatmentId`
  clearing logic isn't duplicated. **Why an event instead of direct injection:** `treatment`
  already depends on `reminder`; injecting `TreatmentService` into `ReminderServiceImpl` would
  have created a package cycle, so the dependency runs through a published event instead.
  - The Treatment page's frontend also calls `completeTreatment()` itself after reloading a
    plan that hit `COMPLETED` — now redundant with the backend sync above, but harmless
    (idempotent; the backend usually wins the race). Not worth removing, low risk to leave.
- `DISMISSED` is a manual user action at any point before COMPLETED — disables any reminders
  already created for that treatment (same `disableRemindersForPlant`-style pattern as the
  archive-cascade fix, scoped to this treatment's reminders only).

### Identification flow — 3-path decision tree
Where a scan is initiated determines what happens after the AI responds. All three paths share
the same underlying `IdentificationService` AI call — only the post-processing branches.
```
Scan initiated from...
│
├─ Garden list ("identify new plant") ── Flow 1
│    AI identifies species
│    └─ scientificName exists in species table?
│         ├─ NO  → create Species row, fire async enrichment
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
│    Plant created, attached to the known Species — no ambiguity to resolve.
│
└─ Plant detail page ("scan plant", health check) ── Flow 3
     plantId + speciesId already known and pre-filled — full identification + annotation
     runs, but purely for HEALTH, not re-identifying the species.
     └─ disease detected?
          ├─ NO active Treatment for that diseaseName  → "Start Treatment Plan" CTA
          ├─ active Treatment exists for that diseaseName → "Treatment in Progress" → link to it
          └─ no disease (healthy)                         → existing care-plan UI, unchanged
```
- Flow 1 is the only path needing species *matching/disambiguation* UI (`species-confirm-step`,
  `plant-select-step` components) — Flows 2/3 already know their Species/Plant from the entry
  point. Backend endpoints: `GET .../species-match`, `POST .../resolve-species`,
  `GET .../plant-match`, `POST .../resolve-plant` on `IdentificationController`.
- These confirm/select steps are NOT part of the async Kafka pipeline — they're synchronous UI
  steps the user resolves AFTER the (already-completed) identification result comes back.
  `processIdentification()`'s job still ends at returning a parsed result.
- Note: the backend tracks only **one** active treatment per plant (the most recent), not one
  per disease — `plant.activeTreatmentId` is a single value. The Plant page's "Treatment in
  Progress" vs. "Start Treatment Plan" choice is a diseaseName-equality check against that one
  value, not a real per-disease lookup.

## Migration Sequencing
- db.changelog-master.xml executes in XML-listed order, NOT by filename — when adding a new
  migration, always append to the XML list AND name the file with the next number; never insert
  a migration between existing ones that have already run in prod.
- Current sequence: 001 → 019, all shipped, strictly applied in that order. Full table with what
  each migration does lives in BACKEND.md (the canonical copy — don't duplicate it here).
- Phase 6 deliberately used numbers 016–019 instead of continuing the brief's originally-assumed
  012–015, because those were already in use by Reminder/Care-Log/Actionable-Care-Plan work
  (012–015) and a pre-existing "Phase 5" already claimed that name. If a future phase's task
  brief assumes migration/phase numbers, verify them against the actual changelog and STATE.md
  before trusting them — this has collided twice already.
- One real ordering bug worth remembering: 017 (Plant FK columns, including
  `active_treatment_id`) runs BEFORE 018 (creates the `treatments` table) in the final order, so
  017 could NOT declare `active_treatment_id` as an inline FK to a table that doesn't exist yet
  at that point in the sequence. Fixed by adding the column as a plain `BIGINT` in 017 and the
  `ADD CONSTRAINT` (Postgres has no `ADD CONSTRAINT IF NOT EXISTS`, unlike `ADD COLUMN`) in 019,
  after 018 has run. **Lesson:** when a brief's migration SQL references a table from a
  not-yet-applied later migration, defer the FK constraint to a migration that actually runs
  after it — don't just inline it and assume the brief accounted for ordering.

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
  - STATE.md: task completions, new branches, key decisions, open items
  - ARCHITECT.md: new patterns, behavioral rules, anything needed to restore context cleanly
- **When a phase/file grows stale or bloated with completed-task narrative**, archive the
  current version (`Archive/<FileName>_<N>.md`) and rewrite the live file lean — durable patterns
  stay here, current status stays in STATE.md, implementation inventory stays in
  BACKEND.md/FRONTEND.md. Don't let session-by-session diary entries accumulate indefinitely in
  any of them — that's what the Archive is for.
- **Output SESSION SUMMARY block** (format defined in AGENTS.md) at end of every response
  so the user can paste it back and trigger a .claude/ sync
