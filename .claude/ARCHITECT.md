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
  Phases 0–4, 6, and 7 are shipped. Two phases are planned, not started:
  **Phase 8 (PlantNet first-class provider — T8.0–T8.7, with open decisions
  D1–D4)** and **Phase 9 (Quality, Testing & Hardening — T9.1–T9.8)**. **Launch
  was renumbered Phase 5 → Phase 10 and moved to the end** (you launch a tested,
  hardened build last). Recommended order: Phase 9 foundation (T9.1–T9.3, T9.5)
  first or interleaved with Phase 8, then the rest, then Phase 10. The only
  stranded manual item, T3.3 (on-device push/PWA), is now folded into T9.2's PWA
  E2E journey + T10.6 beta. See .claude/STATE.md for session history + active
  branches, .claude/TASK_PLAN.md for the Phase 8/9/10 breakdowns.

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
- JaCoCo gate at 55% (the unit suite's real ~58.9% line coverage) — restore to
    80% with proper exclusions in Phase 9 (T9.5), which also wires the Testcontainers
    ITs into `mvn verify`.
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
- **DeepSeekClient** — text-only tasks: care plan (DeepSeek-R1, currently unused — see below),
  cure advice (DeepSeek-R1), disease description (DeepSeek-R1, used by Treatment), species
  enrichment (DeepSeek-R1)
  - Config: `github.base-url`, `github.token`, `deepseek.model` (same endpoint, different model)
  - Owns `stripThinkTags()` (package-private static) — called by DeepSeekClient,
    GitHubModelsClient, and OllamaClient
  - stripThinkTags strips: `<think>...</think>` (R1 reasoning) AND ` ```json...``` ` fences (gpt-4o, llava-phi3)
  - One AI client class per *modality* (vision vs. text), not one per feature — new
    text-only AI features (disease description, species enrichment) were added as new
    methods on the existing DeepSeekClient rather than new client classes
  - **`generateCarePlan()` is dead code** — the single gpt-4o vision call already returns
    `care_plan` inline (see the Plant identification response shape in CLAUDE.md), so nothing
    calls this method. Don't assume it's load-bearing just because it exists.
  - **429 handling (added post-T7.1):** every method funnels its `RestClientResponseException`
    catch through a shared `toServiceException()` — a 429 specifically becomes a
    `RateLimitException` with a real `retryAfterSeconds` (parsed from the `Retry-After` header,
    falling back to a "wait N seconds" regex match against the error body, falling back to a
    60s default), everything else stays a generic 503. Upstream (GitHub Models/Azure) enforces a
    hard **1 DeepSeek-R1 call per 60s per user per model** cap independent of our own Bucket4j
    limits — two DeepSeek-R1 calls for the same user within ~60s of each other (e.g. Treatment's
    disease-description fire-and-forget immediately followed by a user clicking "Craft Treatment
    Plan") WILL collide with this. Any new DeepSeek-R1 call site should expect 429s under normal
    use, not just abuse, and should either reuse `toServiceException()`'s classification or accept
    its `RateLimitException` propagating to `GlobalExceptionHandler` as-is — never re-launder it
    back into a generic 503.
- **OllamaClient** — offline/LAN identification (gemma3:12b-it-qat on dedicated LAN machine, or
  gemma3:4b locally) + text-completion parity for `generateSpeciesEnrichment()`/`generateCureAdvice()`/
  `generateDiseaseDescription()` (each: concatenate the matching `DeepSeekClient.*_SYSTEM_PROMPT`
  constant — package-private, same package — with the user message, call `chat()`, run through
  `DeepSeekClient.stripThinkTags()`). `generateCarePlan`/`detectDuplicateCareCards` have no Ollama
  equivalent (the first is dead code per above, the second is an internal background dedup check
  the user never sees the model for, deliberately left DeepSeek-only).
  - **Dedicated LAN machine (bugfix/PP-044):** OLLAMA_GEMMA3 now targets a dedicated LAN host
    (Ryzen 5 5600G / GTX 1660 6GB / 16GB RAM), not localhost. Hardware: gemma3:12b-it-qat with
    ~2–3GB CPU offload → 60–120s vision, 30–60s text. The **read timeout is intentionally 5 minutes**
    (`ollama.read-timeout-minutes: 5`) — this is a known hardware characteristic, not a bug.
    Required env vars on the Ollama host: `OLLAMA_HOST=0.0.0.0:11434` (binds to LAN interface),
    `OLLAMA_KEEP_ALIVE=10m` (keeps model loaded between calls). App-side `keep_alive: 10m` is
    sent on every /api/generate and /api/chat request so the host-side timer resets on each call.
    Connect timeout stays short (10s) — if the host is unreachable that fails fast.
    **Fallback config:** if 12B proves too slow, switch to `gemma3:4b` (fits fully in 6GB VRAM,
    much faster, weaker reasoning) by setting `OLLAMA_MODEL=gemma3:4b` in the .env.
  - Calls ImageUtil.resizeAndConvertToJpeg(bytes, 1024) before base64 — keeps request size
    predictable regardless of model (applies to llava-phi3, gemma3, etc.)
  - Uses /api/generate (NOT /api/chat) for vision calls — images at top level, not in message content
  - **No silent cross-model fallback** (removed T7.1) — `IdentificationServiceImpl` no longer
    falls back from Ollama to GitHubModels on failure; an Ollama failure now genuinely fails.
- **DeepSeekAnnotationClient** (@Primary VisionAnnotationClient)
  - Calls GitHubModelsClient.analyzeRegions(); 2-attempt retry on GOAWAY only — a 429 is NOT
    retried, it's rethrown immediately as a 429 `PlantPalException`. Don't confuse this with
    DeepSeekClient's 429 handling above; they're different exception shapes (this one isn't a
    `RateLimitException`, has no `retryAfterSeconds`) and were never unified — flagged here so a
    future cleanup pass doesn't assume they already match.

### Vision/Reasoning Model Preference — now load-bearing (closed a T7.1 gap)
T7.1 (2026-06-22) split the single `AiModelPreference` into `VisionModelPreference`
(`GITHUB_GPT4O`/`OLLAMA_LLAVA`/`PLANTNET`) and `ReasoningModelPreference`
(`DEEPSEEK_R1`/`OLLAMA_LLAVA`) on `User`, and T7.2 shipped a picker UI for them — but T7.1
explicitly left both fields storage-only ("no service yet actually reads
visionModelPreference/reasoningModelPreference to pick a client"). A same-day follow-up session
closed that gap:
- `IdentificationServiceImpl.loadVisionPreference(userId)` drives `runIdentification()`'s vision
  client choice (replacing the legacy `AiModelPreference`-driven switch). The Kafka event still
  carries the choice in a field literally named `aiModelPreference` (deliberately NOT renamed —
  large test blast radius for zero behavioral gain since the enum constant names are identical
  strings) but the VALUE now comes from `VisionModelPreference`, parsed defensively
  (`parseVisionPreference()` falls back to `GITHUB_GPT4O` on any unparseable/legacy string —
  protects against stale enqueued Kafka messages from before this deployment).
- `IdentificationServiceImpl.loadReasoningPreference(userId)` now drives `getCureAdvice()`;
  `TreatmentServiceImpl` gained the same helper (plus `UserRepository`+`OllamaClient`
  constructor deps) driving `craftPlan()`'s cure-advice call and the disease-description
  fire-and-forget call.
- Species enrichment (`SpeciesEnrichmentServiceImpl.enrich()`) deliberately KEPT its
  `AiModelPreference`-typed parameter rather than being migrated to `ReasoningModelPreference` —
  changing that interface's signature would ripple through `SpeciesService.findOrCreate()` and
  multiple test files for no real benefit, since the two enums only ever differ on the
  OLLAMA_LLAVA-vs-everything-else axis for this one call site. `IdentificationServiceImpl
  .resolveSpecies()` now maps the real reasoning preference onto the legacy enum via a small
  `toLegacyReasoningPreference()` helper instead of reading the stale stored field. **Pattern for
  next time:** when a real preference needs to reach code that still types against the
  deprecated enum, map at the call site rather than cascading a signature change through an
  interface + all its implementers + their tests.
- PlantNet was never deleted backend-side — it was simply left out of the new
  `VisionModelPreference` enum/picker. If a future cleanup removes `PlantNetClient` for real,
  remove `PLANTNET` from `VisionModelPreference` (both backend enum and frontend type) and the
  `model-selector` option at the same time — don't let only one side drift.

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
- docker-compose: Zookeeper + Kafka (confluentinc/cp-kafka:7.6.0, host port 29192 —
  platform 81xx/1xxxx port block; container-internal port 9092 unchanged)
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

### Angular pattern: gate a feature in a shared component via @Input, don't fork it
`CareCardComponent` (`features/identification/components/care-plan/`) is rendered by THREE
different pages through `CarePlanComponent`: the scan-result preview (`preview-card.component`),
the Plant Detail page's Care section, and Species Detail. When the scan-result page needed its
"Start treatment plan" CTA removed while the other two kept it, the fix was an `@Input()
showTreatmentCta = true` on both `CarePlanComponent` and `CareCardComponent` (default `true`
preserves the other two pages untouched), threaded down and gating both the button's `*ngIf` AND
the now-pointless `checkActiveTreatment()` HTTP call when hidden — only
`preview-card.component.html` passes `[showTreatmentCta]="false"`. Same instinct as
`PhotoUploadComponent`'s `lockedPlantId`/`lockedSpeciesId` (gate behavior by an explicit context
input, not by which page happens to be the caller) — and the inverse move from "Shared
TreatmentStepListComponent" above: that section is about extracting a NEW shared component when
2+ consumers need identical UI; this is about NOT forking an EXISTING shared component when only
some consumers want to suppress one feature of it. Reach for a boolean `@Input` first before
duplicating a component over a single feature's visibility.

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

### SSE token streaming (chat)
First streaming precedent in the codebase (pre-Phase-5 cleanup pass) — read this before adding
any other streamed endpoint.
- **Backend:** `OllamaClient.chatStream(prompt, onToken)` posts to `/api/chat` with
  `"stream": true` and reads the response body line-by-line via `RestClient.exchange()` (NOT
  `.retrieve().body()` — that buffers the whole response; `.exchange()` exposes the raw
  `InputStream` needed to read Ollama's NDJSON streaming format incrementally).
  `ChatController`'s `POST /chat/stream` wraps this in an `SseEmitter` (60s timeout); the actual
  send runs via `CompletableFuture.runAsync(..., aiTaskExecutor)` so the emitter returns
  immediately and the HTTP thread isn't held for the stream's duration — same same-class
  fire-and-forget convention as `TreatmentServiceImpl`'s disease-description generation
  (`@Async`'s proxy has no effect on self-invocation, this is the only way to fire-and-forget
  from within the same class).
- **Frontend:** `chat.service.ts`'s `sendMessageStream()` uses Angular `HttpClient`'s
  `{ responseType: 'text', reportProgress: true, observe: 'events' }` and reads
  `HttpDownloadProgressEvent.partialText` as chunks arrive — NOT a raw `fetch()` call, which would
  bypass `jwt.interceptor.ts` and silently stop attaching the auth token.
- **Infra:** nginx buffers responses by default, which defeats streaming silently (the whole
  reply arrives at once, no visible incremental effect) — `nginx.conf` needs `proxy_buffering
  off; proxy_cache off; chunked_transfer_encoding on;` on the streaming route specifically, added
  before the generic `/api/` block so it matches first.
- **Caveat:** written and passes locally, but never confirmed against a live Docker stack in the
  session it shipped in (no Docker stack was running). Re-verify tokens visibly arrive
  incrementally — not all at once — before trusting this pattern is fully proven end-to-end.

### Derive persisted UI state from the real entity, not a duplicated flag
`CareCardComponent`'s "treatment started" button state used to be a session-only component
boolean (`treatmentStarted`), set only when the user clicked the button in that page load — a
refresh silently lost it and re-offered an action that was already done. Fixed by deriving it
from the real source of truth instead: on `ngOnChanges`, call
`treatmentService.getActiveTreatment(plantId)` and compare its `diseaseName` to the card's title.
**General pattern:** when a UI needs to know "has the user already done X," check whether an
entity already exists that tracks X (a `Treatment` row, a `Reminder`'s `enabled` flag, etc.)
before reaching for a new boolean/flag field — a second source of truth for the same fact will
eventually drift from the first, and the entity is usually already queryable cheaply. This is the
same instinct behind `applyCompletionToReminder()` being the *single* completion choke-point
(see below) — one place owns the fact, everything else reads it.

### Testing & Quality Pipeline (Phase 9)
> Durable model for how this project is tested and kept at enterprise standard.
> Read before adding any test infrastructure.

**Tooling: Playwright, not Cypress.** Decided 2026 against the current landscape.
Deciding factors for THIS project: the PWA must work on iOS Safari and Cypress has
no WebKit support; Playwright ships Chromium + Firefox + WebKit, free
parallelization, and built-in `toHaveScreenshot()` visual regression (no
Percy/Applitools subscription). Cypress's only real remaining edge is its
time-travel debugger — not decisive here.

**The four-layer UI-quality model — deterministic layers GATE, AI layer ADVISES:**
- **Layer 1 (gate)** — Playwright E2E: critical user journeys work at all. ~10–15
  journeys, not hundreds (over-testing is the trap that produces flaky suites
  nobody trusts).
- **Layer 2 (gate)** — visual regression (`toHaveScreenshot`): committed baselines;
  flags *changes*, not taste. Mask volatile regions (timestamps, AI-generated text).
- **Layer 3 (gate)** — accessibility (`@axe-core/playwright`) + Lighthouse CI
  budgets: contrast, labels, tap targets, ARIA, bundle size, PWA installability.
  Measurable floors, fully deterministic.
- **Layer 4 (advisory, NEVER gates)** — AI visual review: a vision model comments on
  PR screenshots with flagged issues/suggestions. Non-deterministic, so it must
  never block CI (an AI gate either flakes and fails good PRs or rubber-stamps and
  gets ignored — both destroy trust). It *proposes*; the human / Architect agent
  *disposes*. Keep it a standalone dev/CI tool, separate from the product's runtime
  AI codepaths.

**Load-bearing principle — stub external AI in E2E; eval real AI separately.** E2E
(Layers 1–3) `page.route`-stubs every gpt-4o / DeepSeek / PlantNet / Anthropic call
so journeys are deterministic, free, and rate-limit-free. The *real* AI quality is a
separate concern, owned by the **nightly eval suite (T9.8)**: a fixed golden set
asserting responses PARSE (the recurring malformed-JSON / concatenated-object pain —
see `LenientJsonParser`) and meet basic shape checks, run nightly because of cost +
the DeepSeek-R1 1-call/60s cap, kept OUT of the PR gate. Never let E2E depend on a
live model — that's the classic flaky-and-expensive failure.

**Frontend test runner:** Vitest + `@analogjs/vitest-angular` + Testing Library
(the 2026 Angular default, over the skeleton Karma/Jasmine). The frontend had ZERO
tests before Phase 9 — coverage gate starts low (~30%) and ratchets; don't fake 80%
on day one. Backend: Testcontainers ITs run serially (forkCount=1) to avoid the
known connection-pool contention; JaCoCo 55% → 80% with exclusions (T9.5).

**Observability:** error tracking first (Sentry, free tier) — highest ROI for a
small team. ONE correlation id flows frontend → backend → Sentry (the MDC
correlationId from prod logging, T10.1), so a frontend error links to its backend
stack trace. Metrics (Micrometer/Prometheus) are a later add.

**Supply chain / secrets (CI):** gitleaks (secret scanning — would have caught the
GITHUB_TOKEN that leaked into chat), Dependabot/Renovate, Trivy (image CVEs), OWASP
Dependency-Check (consolidated here in T9.7 — NOT duplicated in launch hardening).
A scanner doesn't un-leak a credential — rotate the token regardless.

**Prompt injection is its own attack class**, distinct from the HTML-XSS input
sanitization in launch hardening (T10.3): user chat + uploaded/OCR'd text reach AI
prompts. Delimit user content, keep system instructions authoritative, and
re-validate AI OUTPUT before persistence (`ActionPlanValidator` is the precedent).
Covered in T9.8.

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
    → TransactionSynchronizationManager.registerSynchronization(afterCommit)
         → @Async("aiTaskExecutor") SpeciesEnrichmentService.enrich(speciesId)
              1. DeepSeekClient.generateSpeciesEnrichment() — text-only, no image needed
              2. Parse defensively (same "never throw" philosophy as ActionPlanValidator) — on
                 failure, leave fields null and flip status to NEEDS_REVIEW / FAILED
              3. On success: update the row, externalDataSource="AI" (hardcoded), descriptionStatus=READY
  ```
  **⚠️ CRITICAL — always use `afterCommit()` when firing `@Async` from inside `@Transactional`:**
  `createSpecies()` is `@Transactional`. Calling `enrich(speciesId)` inline means the async thread
  starts before the outer transaction commits. `enrich()`'s own `@Transactional` opens a new DB
  connection and calls `findById(speciesId)` — species row doesn't exist yet → returns null →
  enrichment silently skipped → `descriptionStatus` stays PENDING forever.
  Fix pattern (applied to both `createSpecies()` and `regenerateDescription()`):
  ```java
  speciesEnrichmentService.ifPresent(service ->
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { service.enrich(speciesId, preference); }
      }));
  ```
  Do NOT inline the `enrich()` call directly. Detection: if you see "X not found for enrichment,
  skipping" in logs on a freshly created entity, this race is the cause.
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
- **Doc sync is AUTOMATIC after every phase completion — never wait to be told.**
  Once the final code commit lands, immediately update the `.claude/` files as part of
  the same session: STATE.md, CLAUDE.md build table, TASK_PLAN.md header, plus new
  decision/learning entries in this file. It is always the last step of
  "start Phase N" / "implement all". (The former external plants-vault was removed
  2026-07-15 — `.claude/` is the single memory layer.)




### PlantNet v2 — botanical ground-truth provider (Phase 8)
> Phase 8 reframed PlantNet from a degraded species-only vision option into the
> app's botanical ground-truth layer. Contract below is from the real swagger
> (My Pl@ntNet API 2.2.2). Read this before touching identification, species
> confirmation, or the disease path.

- **Auth + limits:** api-key is a **query param**. Max POST **50 MB**. Real status
  codes: **404** = non-plant reject ("Species Not Found"), **413** too large,
  **415** bad mime, **429** Too Many Requests. Map each to its true status — never
  launder to 500 (the T7.1 GlobalExceptionHandler fix already does the right thing
  if we throw the right exception).
- **`type=kt` is mandatory.** `legacy` floras/engine are deprecated and "will be
  dropped" — always send `type=kt` (2023+ POWO/WGSRPD engine).
- **Candidate list, not a single answer.** `/v2/identify/{project}` returns ranked
  `results[]` (score + taxonomy + commonNames + reference `images[]` +
  `gbif{id}` + `powo{id}` + `iucn{id,category}`), plus `bestMatch`,
  `switchToProject` (a hint another flora would do better — surface it),
  `predictedOrgans[]`, `version`, `remainingIdentificationRequests`. Keep the FULL
  list; collapsing to `results[0]` (pre-Phase-8) threw away the confidence signal
  Flow-1 confirmation needs.
- **Reference images carry attribution.** Each `Image` has `author`, `license`,
  `citation`, `url{o,m,s}` (original/medium/small). If we display a reference
  thumbnail, we must show author + license. Use `url.s`/`url.m`, never hot-link `o`.
- **Organs.** Forward per-image organ tags (multi-angle single-plant mode forwards
  `organs[]`). Practical consumer set: auto/leaf/flower/fruit/bark/habit. Accuracy
  order flower > fruit > leaf > entire > bark. Batch mode (different plants) does
  NOT tag organs.
- **Health is separate.** `/v2/identify` does not assess health — a PlantNet-only
  species scan keeps `healthStatus: UNKNOWN` honestly. Disease/pest detection is
  `/v2/diseases/identify` (T8.5, **multi-image, cultivated-plant-scoped**), whose
  results carry `{name, description, score, images[]}` — the `description` can seed
  `Treatment.diseaseDescription`. Diseases are EPPO-coded (`/v2/diseases` →
  `{name:"1COCCF", label, categories[]}`). Run it as a SECOND OPINION alongside
  gpt-4o, never a silent replacement (no-silent-fallback rule from T7.1).
- **Geolocation-ranked floras.** `GET /v2/projects?lat&lon&type=kt&lang` filters
  AND sorts floras by location — better UX than a manual picker for a Morocco-
  centric base. Needs a user lat/lon source (may not exist yet — ship manual
  dropdown first). Cache `/v2/projects` + `/v2/languages` server-side (@Cacheable,
  24h) and proxy them so the frontend never burns identify quota.
- **Quota is a real endpoint.** `GET /v2/quota/daily` →
  `{day, quota.identify.{count,total,remaining}}`; `/v2/quota/history?year=` for a
  chart. Use these proactively; `remainingIdentificationRequests` on each
  identify/disease response is the per-scan echo. Quota is daily, independent from
  our Bucket4j 20/hour — a Flow-3 scan that hits gpt-4o AND `/v2/diseases/identify`
  consumes PlantNet quota twice.
- **Factual taxonomy is partly free.** `gbifId`/`powoId`/`iucnCategory` arrive on
  every result — persist them onto `Species` with no extra call (the cheap half of
  T8.6). The `Species.externalDataSource` slot ("AI"|"WIKIPEDIA"|"MANUAL") gains
  "GBIF"/"POWO". A GBIF *fetch* (api.gbif.org) is only for deeper distribution/
  vernacular data. Also available: IUCN conservation badge (factual, free).
- **Reuse, don't reinvent:** Apache HttpClient 5 + HTTP/1.1 ALPN workaround (NOT
  Java's built-in HttpClient); defensive "never throw on a malformed field"
  parsing; nullable response fields; ModelUsageBadge / AiErrorService for the
  "powered by Pl@ntNet {version}" caption and the 404/quota UX.
- **Optional/secondary (noted, not scheduled):** `/v2/varieties/identify` (cultivar
  ID — result adds a `varieties[]` block), `/v2/projects/{project}/species/align`
  (reconcile a free-text species name onto PlantNet's referential, synonyms/fuzzy —
  a clean way to align existing `Species.scientificName` rows), `/v2/embeddings`
  (image vectors for visual similarity/dedup), `/v2/prediction/geo/species`
  ("what grows near me" SDM — tagged [private], confirm key access first).

**Two design forks recorded in TASK_PLAN.md Phase 8 (D1: PlantNet always-on vs.
only-when-selected; D4: disease cross-check authority) — both need a human ruling
before T8.1 / T8.5 run. Don't let an agent pick silently.**

### Migration sequencing note (refresh)
Sequence is at **022** (not 019 — the stale number in T5.2's prompt). Next free is
**023**. Phase 8 claims 023 (PlantNet candidates), 024 (PlantNet project/lang prefs),
025 (PlantNet disease result) — VERIFY against db.changelog-master.xml before each,
per the "migration numbers have collided twice" lesson above.

---

### AI model lineup (5 vision / 5 reasoning) — providers, clients, defaults
> Established T8.0 (Phase 8). The two axes stay independent (T7.1 split). Each
> option routes to exactly one client; only Claude is a new provider.

| Axis | Option (enum) | Model | Client / provider | Tier | Notes |
|---|---|---|---|---|---|
| Vision | GITHUB_GPT41 | gpt-4.1 | GitHubModelsClient (Azure) | Best | New model string on existing client |
| Vision | GITHUB_GPT4O | gpt-4o | GitHubModelsClient (Azure) | Balanced | Pre-existing |
| Vision | OLLAMA_GEMMA3 | gemma3:12b-it-qat (LAN) / gemma3:4b (local) | OllamaClient | Offline | Dedicated LAN machine; read timeout 5 min intentional (CPU offload); gemma3:4b fallback if 12B too slow |
| Vision | PLANTNET | — | PlantNetClient | Specialist | Species-ID only (+ disease via Phase 8); healthStatus UNKNOWN, no care plan; runs alongside a general model (see D1) |
| Vision | ANTHROPIC_CLAUDE | claude-sonnet-4-6 | AnthropicClient (NEW) | Frontier | Needs anthropic.api-key; multimodal so one client serves both axes |
| Reasoning | GITHUB_O4_MINI | o4-mini | Azure text client | Best | o-series request shape: max_completion_tokens, no temperature, no <think> tags |
| Reasoning | DEEPSEEK_R1 | DeepSeek-R1 | Azure text client (DeepSeekClient) | Deep | Pre-existing; 1-call/60s upstream cap — see toServiceException() |
| Reasoning | GITHUB_GPT41_MINI | gpt-4.1-mini | Azure text client | Balanced | **Recommended default** — fast structured-JSON generation, no 60s cap |
| Reasoning | OLLAMA_GEMMA3 | gemma3:12b-it-qat (LAN) / gemma3:4b (local) | OllamaClient | Offline | Same model as vision axis; one gemma3 process on the LAN host serves both |
| Reasoning | ANTHROPIC_CLAUDE | claude-sonnet-4-6 | AnthropicClient (NEW) | Frontier | Same client as vision Claude |

- **Cloud tier is cheap to grow** because GitHubModelsClient and the Azure text
  client (historically "DeepSeekClient") share one Azure inference endpoint — new
  GitHub Models options are config strings + a switch branch, not new classes. The
  "DeepSeekClient" name is now historical: it serves DeepSeek-R1, o4-mini, AND
  gpt-4.1-mini. Not renamed this phase (blast radius); documented instead.
- **AnthropicClient is the only new provider** and is multimodal, so it serves both
  the vision identification contract and the text reasoning contract. It must be
  gated on `anthropic.api-key` presence (the prefs response carries per-provider
  `available` flags) — an un-keyed Claude option must be unselectable or every call
  401s.
- **Recommended default switch:** reasoning default DEEPSEEK_R1 -> GITHUB_GPT41_MINI.
  The app's reasoning tasks (care plan / cure advice / disease description / species
  enrichment) are structured *generation*, not logic puzzles — R1's chain-of-thought
  is overkill and is the exact call site that keeps colliding with the 1-call/60s
  DeepSeek-R1 upstream cap (STATE.md history). gpt-4.1-mini is faster, cheaper, and
  has no such cap. Vision default stays a GitHub Models model (gpt-4o or gpt-4.1).
- **gemma3:4b on 4GB VRAM is marginal** — vision models spike VRAM under load (the
  reason llava-phi3 was originally chosen). Verify gemma3:4b identification on the
  actual GTX 960 before shipping it as the local vision default; keep llava-phi3 as
  a documented fallback.
- **o4-mini request shape** differs from chat models: `max_completion_tokens` (not
  `max_tokens`), no `temperature`, reasoning hidden server-side. Build the Azure
  text request per-model so o4-mini and gpt-4.1-mini don't share one rigid body.
- **UI labels by intent, not model name** (Best/Balanced/Offline/Specialist/
  Frontier with the model name as subtitle) — names churn; the "powered by {model}"
  badge keeps the actual model visible and honest.
- **Migration note:** adding enum VALUES needs no schema migration (pref columns are
  VARCHAR(30)). Only the optional default-change + OLLAMA_LLAVA->OLLAMA_GEMMA3
  backfill is a migration (claims 023; if used, bump the Phase 8 PlantNet migrations
  to 024/025/026).

---

### Architecture Decision D5 — Per-Stage Identification Status (Phase 8.5)
> Triggered by: 5 concurrent batch identifications all surfacing as FAILED despite
> gpt-4o returning valid care plans for 4/5. Root cause: `processIdentification()`
> had no concept of a partial result — any exception in annotation or PlantNet
> propagated up and failed the entire record.

**Decision:** introduce three independent stage-status fields on `Identification`,
replacing the single binary `status` as the outcome signal for each stage:

| Field | Type | Values | Meaning |
|---|---|---|---|
| `identificationStatus` | VARCHAR(30) | PENDING / COMPLETED / FAILED | Core AI identification (gpt-4o care plan + species + health). The **only** field that drives the record's top-level `status`. |
| `annotationStatus` | VARCHAR(30) | PENDING / COMPLETED / FAILED / SKIPPED | Polygon annotation via `analyzeRegions()`. FAILED here does NOT fail the record. |
| `candidateStatus` | VARCHAR(30) | PENDING / COMPLETED / FAILED / SKIPPED | PlantNet candidate enrichment. Failure is non-fatal. SKIPPED when not attempted (e.g. PlantNet IS the core call, not enrichment). |

Additional fields in migration 027 (next free — sequence ends at 026):
- `identificationModel` (VARCHAR 50): `VisionModelPreference` enum name used for the core call. Already resolved in `loadVisionPreference()` (T7.1); passed through to the save.
- `annotationModel` (VARCHAR 50): always `"gpt-4o-mini"` today (hardcoded by design — see T8.D annotation routing audit). Captured for the Phase 9 eval corpus.
- `failureReason` (TEXT): set when `identificationStatus = FAILED` only. Tags: `RATE_LIMITED` | `PARSE_ERROR` | `PROVIDER_ERROR` | `OTHER`. Gives T8.F's retry endpoint and Phase 9's Sentry integration something structured to act on.

**Top-level `status` column:** NOT dropped (too disruptive). Continues to be set as
before, but its meaning is now "mirrors `identificationStatus`". Future code should
prefer reading `identificationStatus` directly; `status` is the backward-compatible view.

**New Java enum:** `IdentificationStageStatus { PENDING, COMPLETED, FAILED, SKIPPED }`
in `identification/entity/`. Distinct from `IdentificationStatus` (PENDING/COMPLETED/FAILED)
— the stage enum adds SKIPPED.

**Backfill rule (migration 027):**
- `status = 'COMPLETED'` → `identification_status='COMPLETED'`, `annotation_status='COMPLETED'`, `candidate_status='SKIPPED'` (PlantNet was broken since Phase 8 — SKIPPED is honest, not COMPLETED)
- `status = 'FAILED'` → `identification_status='FAILED'`, `annotation_status='SKIPPED'`, `candidate_status='SKIPPED'`
- `status = 'PENDING'` → all three = `'PENDING'`

**Implementation rule:** `processIdentification()` wraps annotation and PlantNet stages in
try/catch. Each sets its own stage status on exception and never re-throws. Only the core
stage can set `status = 'FAILED'`. This converts partial pipeline failures into successes
at the record level. The retry endpoint (T8.F) uses the stage statuses to decide which
stages to re-run.

---

### D1 Amendment — PlantNet Candidates Deferred from Critical Path (Phase 8.5, T8.E)
> Original D1 (Phase 8): PlantNet always-on alongside gpt-4o vs. only-when-selected.
> Phase 8 implemented PlantNet as a synchronous concurrent call inside
> `processIdentification()`. Phase 8.5 revises this execution model.

**Finding:** N concurrent batch identifications × synchronous PlantNet calls × ~3 full-
base64 vision calls each = rapid exhaustion of the GitHub Models 60 000-token/60s ceiling.
The synchronous critical-path placement directly caused the observed 429 storm.

**D1 ruling (amended):** PlantNet always-on IS confirmed — not gated on
`VisionModelPreference.PLANTNET`. But the execution is deferred: PlantNet candidate
enrichment runs as a **fire-and-forget async enrichment AFTER the core identification
succeeds**, using the same pattern as `SpeciesEnrichmentService.enrich()`.
- After `identificationStatus` is set to `COMPLETED` and the record saves, a
  `CompletableFuture.runAsync(aiTaskExecutor)` launches the PlantNet call.
- `candidateStatus` starts `PENDING` when queued, transitions to `COMPLETED`/`FAILED` async.
- The frontend's existing 3s poll already re-fetches the full record — candidates appear
  automatically when `candidateStatus` transitions to `COMPLETED`.
- PlantNet quota is only consumed after the identification succeeds — no wasted quota on
  failed identifications.

**Exception:** when `VisionModelPreference.PLANTNET` is the user's explicit choice,
PlantNet IS the core call and continues running synchronously in the critical path.

---

### T8.C — PlantNet DTO Regression Note (Phase 8.5)
> Known regression introduced in Phase 8. Every PlantNet response has been silently
> failing since Phase 8 shipped — the always-on PlantNet integration has NEVER once
> succeeded in the live pipeline.

**Root cause:** `PlantNetResponse.predictedOrgans` was typed `List<String>`, but PlantNet
v2 actually returns a list of `{organ, score}` objects (confirmed against the swagger).
Jackson throws `Cannot deserialize ArrayList<Object> from Object value …
predictedOrgans[0]` on every response. The exception is caught by the "Always-on PlantNet
call failed, continuing without candidates" wrapper, so it degrades silently — no visible
error, no candidates populated.

**Fix (T8.C):** retype to `List<PlantNetPredictedOrgan>` where `PlantNetPredictedOrgan`
has `String organ` and `Double score`. Add a deserialization unit test with a faithful v2
fixture (`src/test/resources/fixtures/plantnet_identify_response.json`) that must fail
before the fix and pass after. The fixture also seeds Phase 9's T9.8 eval corpus.

**T8.D — Annotation Routing Audit Findings (Phase 8.5)**
Three facts about the identification pipeline's model routing that look like gaps but are
deliberate. Documented here (not as code comments) to prevent future re-investigation:
- **Annotation model deliberately pinned to gpt-4o-mini** via `GitHubModelsClient`,
  regardless of the user's `VisionModelPreference`. This is intentional — gpt-4o-mini is
  cheaper and sufficient for polygon-coordinate output.
- **`DeepSeekAnnotationClient` is NOT an independent-quota fallback** — it wraps
  `GitHubModelsClient.analyzeRegions()`, the same endpoint and same token bucket as the
  primary vision call. Since T7.1 removed cross-model fallback, a 429 on annotation sets
  `annotationStatus='FAILED'` immediately (T8.B). No cross-model annotation fallback is
  planned — complexity outweighs need given annotation is now non-fatal.
- **`ReasoningModelPreference` is NOT invoked in `processIdentification()`** — it drives
  only the standalone cure-advice, disease-description, and species-enrichment paths, not
  the main identification pipeline's vision or annotation calls.

---

## Project Memory

The `.claude/` files (this file, STATE.md, TASK_PLAN.md, BACKEND.md, FRONTEND.md) are the
**single, authoritative memory layer** for this repo. Decisions live as D-entries in this
file; learnings and phase history live in STATE.md (archived snapshots in `.claude/Archive/`).
The former external Obsidian vault (`../plants-vault`, D6) was **removed on 2026-07-15** —
D6 is superseded; do not look for or write to any external vault. Platform-delta session
outcomes go to `PROGRESS.md` per the platform standing orders (`../CLAUDE.md`).

---

## Phase 10 — Contextual Scanning & Scan Redesign (patterns, 2026-06-27)

### Scan User Context Pattern
`identifications.user_context TEXT` (migration 030, nullable) captures the user's optional
"what I want to know" text before scanning ("this leaf is yellow, I put it in the bathroom…").

- Flows through `SubmitIdentificationRequest` → `IdentificationEvent` → `processIdentification()`.
- Threaded into the AI prompt as **user-message content** (NOT system prompt — it is user input,
  not a system instruction). Phrasing: `"The user wants to know: {userContext}. Consider this…"`
- `IdentificationResponse.userContext` exposed so the scan detail page can display it.
- Frontend: `PhotoUploadComponent` shows a collapsible textarea + mic button (Web Speech API,
  client-side transcription, no backend audio). Mic hidden if `SpeechRecognition` unavailable.

### Annotation Skip Condition (D10.1 — conservative implementation)
`processIdentification()` skips the `analyzeRegions()` call when `healthStatus == HEALTHY || UNKNOWN`.
Sets `annotationStatus = SKIPPED` and logs the reason. Only calls annotation when `healthStatus == ISSUES_DETECTED`.
This halves annotation API calls for healthy scans at zero structural complexity.
**If a future session wants to merge annotation into the identification prompt (single call),**
extend `PLANT_IDENTIFICATION_SYSTEM_PROMPT` to return `annotation_regions` in the core JSON
and remove the separate `analyzeRegions()` call — do NOT add another stage, just remove the
conditional skip and parse the merged response. That migration was not taken in Phase 10
(conservative option chosen). The merged approach is cleaner but requires prompt engineering
validation and larger fixture updates.

### Scan Detail Page Navigation Pattern
`/plants/:plantId/scans/:scanId` — the dedicated scan detail page.
- Sticky header (same IntersectionObserver + `.collapsed` pattern as Plant/Treatment pages).
- Back button navigates to `/plants/:plantId?section=scans` (query param, NOT router state or
  `location.back()`) — `plant-detail` reads `?section=` on init to activate the right section.
- Same pattern used for step-detail back-nav (`?section=plan` on the Treatment detail page).
- **All "return to section" navigation uses query params**, never router state. Router state is
  lost on page refresh and confuses the browser's own back button history.

### Health-Scan Card Draft State Pattern (D10.2 — client-side only)
Cards from health scans (Flow 3) start in a "draft" presentation state — not yet added to the
plant's care plan. This is **client-side state only** (no new DB column):
- `CareCardComponent` gains `@Input() isDraft = false` and `@Input() treatmentActive = false`.
- `isDraft=true` → show "Add to care plan" CTA; gate `checkActiveTreatment()` call behind `!isDraft`.
- On "Add to care plan" success → flip `isDraft=false` locally (no reload needed).
- Disease/pest card with `!isDraft` → show "Initiate treatment" CTA.
- `treatmentActive=true` → show "Treatment in progress" tag (derived from `getActiveTreatments(plantId)`).
- Cards from first-identification scans (Flow 1/2): `isDraft=false` by default, "Initiate treatment"
  directly (no "Ask for cure" intermediate step), informational cards have no CTA.

### Scan CTA by Flow Type
Determines which CTAs appear on a scan detail page's care cards:
| Scan type | Card type | CTA |
|---|---|---|
| Flow 1/2 (first identification) | Disease/pest | "Initiate treatment" → POST /treatments → navigate /treatment/:id |
| Flow 1/2 | Other (watering, light…) | None (informational only) |
| Flow 3 (health scan, plant known) | Any card, draft | "Add to care plan" → addCareCard() → isDraft=false |
| Flow 3 | Disease card, after added | "Initiate treatment" |
| Flow 3 | Disease card, treatment in progress | "Treatment in progress" tag (no action) |

### Treatment Auto-Progression Rule (D10.3)
**`createTreatment()` must never call `craftPlan()`.** Period.
- `createTreatment()` only: persist Treatment in DRAFT + fire disease-description async.
- `craftPlan()` only on explicit user action (POST /treatments/:id/craft-plan).
- `CareCardComponent` navigates to `/treatment/:id` after `createTreatment()` — never chains to `craftPlan()`.
- `TreatmentDetailComponent` description poll checks `status === 'DRAFT' && descriptionStatus === 'PENDING'`
  — must never change status as a side effect.
- `craftPlan()` transition DRAFT → IN_PROGRESS: must NOT overwrite `diseaseDescription`.
  The description is generated independently; the plan adds steps only.

### Web Speech API — STT pattern (T10.G, revised T10.I)
The microphone button in `PhotoUploadComponent` uses Web Speech API
(`SpeechRecognition`/`webkitSpeechRecognition`).

**Current pattern (post-T10.I):** call `recognition.start()` directly — no `getUserMedia`
pre-check. SpeechRecognition manages its own mic capture and shows the browser's
native permission dialog when needed. `onerror` covers `not-allowed` as fallback.

**Why the T10.G getUserMedia pre-check was removed:** the pre-check was added to trigger
the OS permission dialog reliably on mobile cold-start. However, it caused mic contention
on Chromium desktop: even when tracks were stopped immediately, the sequence
`getUserMedia → recognition.start()` caused `onstart` to never fire and `onend` to arrive
silently ~4 s later. Diagnosed via `/voice-test` (Step 1 mic-only vs Step 2 recognition-only
panels confirmed mic was accessible but speech service was failing). Removing the pre-check
and calling `recognition.start()` directly fixed it. The `not-allowed` `onerror` handler
remains as the permission-denied signal on all platforms.
- **isSecureContext gate (T10.I — shipped):** `SpeechRecognition` requires HTTPS.
  Chrome allows `localhost` as an exception, but any LAN IP (`192.168.x.x`) over plain HTTP
  is blocked. Fix: `speechSupported = !!SpeechRecognitionAPI` (API exists — gates Firefox)
  + `speechSecure = window.isSecureContext || location.hostname === 'localhost'` (gates
  `[disabled]` and tooltip "Voice input requires HTTPS"; button visible but greyed, not
  hidden). Branch: `feature/PP-078-mic-bug`.
- **getUserMedia + SpeechRecognition mic contention (T10.I — root-caused via /voice-test):**
  Calling `getUserMedia({ audio: true })` to set up an `AudioContext`/`AnalyserNode` BEFORE
  calling `recognition.start()` silently kills recognition on Chromium. The speech service
  cannot acquire the audio device a second time — `onstart` never fires, `onend` fires ~2–5 s
  later with no error. Diagnosed by adding `console.trace` to `stopAll()` and checking
  whether `ngOnDestroy` or the user's toggle was the caller (it was the user's toggle after
  manually stopping a hung session — lifecycle was stable, mic was contended).
  **Rule:** SpeechRecognition manages its own mic; never hold an open `getUserMedia` stream
  when calling `recognition.start()`. If an audio level meter is needed alongside recognition,
  start it from inside `recognition.onstart` — recognition has already claimed the device by
  then and Chrome allows a secondary `getUserMedia` for the `AudioContext` source node.
  Related-but-distinct from the isSecureContext failure: both affect the same API family but
  the contention bug occurs even on localhost/HTTPS — the ordering of calls is the variable.

- **NgZone wrapping (T10.I critical fix):** `SpeechRecognition` callbacks (`onresult`, `onerror`,
  `onend`, `onstart`) fire outside Angular's change detection zone. Without wrapping in
  `ngZone.run(()=>{...})`, setting component properties (e.g. `this.contextText`) has no effect
  on the template — the textarea never updates. Rule: always wrap ALL SpeechRecognition callbacks
  in `ngZone.run()`. rAF animation loops use `ngZone.runOutsideAngular()` with only the
  level-state setter inside `ngZone.run()` (avoids per-frame change detection).
- **`/voice-test` diagnostic page** (`features/voice-test/`) — two-panel layout. Step 1: pure
  `getUserMedia` + AudioContext level meter (tests mic hardware). Step 2: pure `SpeechRecognition`
  with no prior getUserMedia (tests speech service + network). Accessible from the user menu.
  Useful to distinguish mic hardware failure from speech service (network) failure, since Chrome's
  SpeechRecognition silently times out (~4s first attempt, ~4ms circuit-breaker pattern) when it
  cannot reach Google's speech WebSocket API.

### Web Speech Synthesis — TTS "Read aloud" pattern (T10.H)
`SpeechService` (`shared/services/speech.service.ts`, `providedIn: 'root'`) wraps the
browser's `window.speechSynthesis` API with a simple stateful interface:
- `isSupported: boolean = 'speechSynthesis' in window` — always feature-detected; the
  read-aloud button hides itself entirely when this is false (no API, no button, no noise).
- `speak(text)`: cancels any current utterance (so a new click always wins), creates a new
  `SpeechSynthesisUtterance` at `rate=0.95`, updates `speaking`/`currentText` state via
  `onstart`/`onend`/`onerror` callbacks.
- `stop()` + `ngOnDestroy()`: cancel and reset state.
- `isReadingText(text): boolean`: used by the button to show the active (stop) state only
  when THIS text is being read — two buttons on the same page don't interfere.

`ReadAloudButtonComponent` (`shared/components/read-aloud-button/`, declared + exported
by `SharedModule`):
- `@Input() text: string` — parent assembles the full plain-text string.
- `@Input() ariaLabel = 'Read aloud'`
- A single `mat-icon-button` with `volume_up` icon at rest, `stop_circle` when `isReading`.
- `*ngIf="speechService.isSupported"` on the button itself — zero DOM impact when unsupported.
- `toggle()`: `stop()` if reading this text, else `speak(text)`.
- CSS `.reading` class colors the icon green (primary token) to signal active state.

**Where it's wired:**
| Surface | Text assembled as |
|---|---|
| `CareCardComponent` header | `card.title + '. ' + (card.advice ?? '')` |
| `SpeciesDetailComponent` description block | `species.description` (guard: `descriptionStatus === 'READY'`) |
| `TreatmentDetailComponent` disease description | `treatment.diseaseDescription` (guard: `descriptionStatus === 'READY'`) |
| `TreatmentStepListComponent` per-step | `'Step N: ' + stepInstruction(step) + (stepDetail ?? '')` |
| Dashboard reminder rows | `reminder.plantNickname + ': ' + reminder.careType.toLowerCase().replace(/_/g,' ')` (via `reminderReadText()` helper) |
| `ScanDetailComponent` userContext block | `identification.userContext` |

**Placement rule:** inline with the section heading or title row (icon only, no label text),
same visual weight as a secondary action. Never a separate full-width row. Never show a
label next to the icon — the `matTooltip` is sufficient.

**No new lazy module providers needed** — `SpeechService` is `providedIn:'root'`; the button
component is exported from `SharedModule` and consumable in any lazy feature module
(IdentificationModule, PlantModule, etc.) without re-providing.

### Mobile Mermaid Constraint (added to all AI system prompts with Mermaid output)
```
Mermaid diagrams must be optimized for mobile screens:
- Use flowchart TD (vertical). Never flowchart LR on mobile.
- Maximum 5 nodes. Node labels max 15 characters (abbreviate).
- No subgraphs, no click events, no style blocks, no double quotes in labels.
- Simple linear flows; branching at most 2 levels deep.
```
Frontend: `MermaidDiagramComponent` must have `width:100%; overflow-x:auto` on its host element.
