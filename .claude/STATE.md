# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-06-19 (session 18 — Architect planning session)

## Current Phase
Phase 0 — Project Setup ✅ COMPLETE
Phase 1 — Auth + Plant Management ✅ COMPLETE
Phase 2 — AI Plant Identification ✅ COMPLETE
Phase 3 — ✅ COMPLETE
Phase 4 — AI Chat ✅ Complete (basic, single-turn) — streaming/history polish not started
Phase 5 — Launch prep 🔲 Not started (already fully defined as T5.1–T5.8 in TASK_PLAN.md —
  performance/caching, security hardening, API docs, deployment, beta testing, release)
Phase 6 — Species & Treatment Domain Restructure 🟡 IN PROGRESS (T6.1 ✅, T6.2 ✅ done; T6.3–T6.14
  below not started yet — full task prompts in TASK_PLAN.md)

> ⚠️ **Renumbering note (2026-06-19):** the user requested this session's new work be filed as
> "Phase 2 / T3.1–T3.14" with migrations 012–015. That collides with work that's already shipped:
> T3.1/T3.2/T3.4/T3.4b/T3.5/T3.6/T3.7/T3.8/T3.9 already exist (Reminder + Care Log + Actionable
> Care Plans + the EXISTING `TreatmentPlan` entity from T3.4 — a different concept from this
> session's new `Treatment` entity, see ARCHITECT.md's disambiguation note), and migrations
> 012–015 are already applied. A second collision turned up while drafting this: TASK_PLAN.md
> ALSO already has a pre-existing (unstarted) "Phase 5 — Launch" section with its own T5.1–T5.8.
> Filed as **Phase 6, T6.1–T6.14, migrations 016–019** to avoid both collisions.
> **Execution-order note:** despite the higher number, Phase 6 (this restructure) is intended to
> run BEFORE Phase 5 (Launch) — the phase numbers here reflect order of definition in the docs,
> not recommended execution order. See `.claude/PHASE6_SESSION_PROGRESS.md` for this planning
> session's resume trail if it was interrupted.

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
- T2.A GitHubModelsClient split — vision (gpt-4o/gpt-4o-mini) vs text (DeepSeek-R1) ✅
  - GitHubModelsClient.java: identification (gpt-4o) + annotation (gpt-4o-mini); HTTP/2; 5-min timeout
  - DeepSeekClient: vision methods removed; owns generateCarePlan() + generateCureAdvice() + stripThinkTags() only
  - DeepSeekAnnotationClient: now injects GitHubModelsClient (not DeepSeekClient) for analyzeRegions()
  - OllamaClient: references GitHubModelsClient.{PLANT_IDENTIFICATION,ANNOTATION}_SYSTEM_PROMPT
  - IdentificationServiceImpl: injects GitHubModelsClient; DEEPSEEK + GITHUB_GPT4O both route here
  - application-dev.yml: github.base-url / github.token / github.models.* properties; deepseek.* cleaned up
  - .env.example: GITHUB_TOKEN, GITHUB_BASE_URL, GITHUB_IDENTIFICATION_MODEL, GITHUB_ANNOTATION_MODEL
- T2.B GITHUB_GPT4O model preference ✅
  - AiModelPreference enum: DEEPSEEK | PLANTNET | OLLAMA_LLAVA | GITHUB_GPT4O
  - frontend AiModelPreference type updated to include 'GITHUB_GPT4O'
  - ModelSelectorComponent: 4 toggles; matTooltip rate-limit warnings on all four options
  - POST /api/v1/identifications/{id}/cure-advice → 202 Accepted, { advice: string }
  - CureAdviceRequest (@NotBlank regionLabel, nullable species), CureAdviceResponse (String advice)
  - DeepSeekClient.generateCureAdvice(): text model (DeepSeek-R1), plain text (no json_object), stripThinkTags()
  - Separate cureAdviceBuckets (10/hour) — independent from deepSeekBuckets (20/hour)
  - Ownership check before AI call; ResourceNotFoundException if not owned
  - 18 unit tests passing (4 new CureAdvice tests: happy path, rate-limited, not-owned, DeepSeek error)
- T2.C Kafka async identification pipeline ✅
  - spring-kafka dependency; spring.kafka.* config (consumer group plantpal-identification,
    JsonDeserializer/JsonSerializer, trusted packages com.plantpal.*)
  - docker-compose.yml: zookeeper + kafka (confluentinc 7.6.0) services; backend env override
    KAFKA_BOOTSTRAP_SERVERS=kafka:9092 (vs. localhost:29092 for host-side dev)
  - IdentificationRequestedEvent (identificationId, userId, photoUrl, aiModelPreference, organs,
    requestedAt) + IdentificationCompletedEvent (identificationId, status, completedAt)
  - KafkaTopicConfig (identification/config/): identification.requested + identification.completed
    topics, 3 partitions/1 replica each. KafkaConfig (shared/config/): KafkaTemplate<String,Object> bean
  - IdentificationServiceImpl.identify() split into submitIdentification() (validate → savePhoto →
    persist PENDING → rate-limit check → loadUserPreference → publish IdentificationRequestedEvent →
    return IdentificationPendingResponse) and processIdentification(event) (@Async("aiTaskExecutor");
    loads entity by id, FileStorageService.loadPhoto() re-reads bytes from disk since the event only
    carries photoUrl; runs parallel runIdentification + analyzeRegions; COMPLETED/FAILED + publishes
    IdentificationCompletedEvent either way; never propagates exceptions out of the @Async method)
  - runIdentification() signature dropped the `List<MultipartFile> images` param — PLANTNET branch now
    wraps imageBytes in a private ByteArrayMultipartFile adapter (same pattern as PlantNetAnnotationClient)
  - FileStorageService: added loadPhoto(String url) — LocalFileStorageService reads bytes back from disk
  - IdentificationConsumer (identification/consumer/): @KafkaListener on identification.requested,
    groupId plantpal-identification, delegates to identificationService.processIdentification(event)
  - IdentificationController: POST /analyze → submitIdentification(), 202 Accepted +
    IdentificationPendingResponse ("Analysis started — poll for result"); added GET /{id} →
    IdentificationResponse (ownership-checked) for polling
  - .env.example: KAFKA_BOOTSTRAP_SERVERS=localhost:29092
  - 22 unit tests passing in IdentificationServiceImplTest (new Kafka nested class: submit persists
    PENDING + publishes event, rate-limit blocks publish, processIdentification happy path marks
    COMPLETED + saves annotations, processIdentification AI failure marks FAILED without throwing)
- T2.D2 GET /api/v1/identifications (current-user list, paginated) ✅
  - IdentificationService.getUserIdentifications(userId, pageable); IdentificationServiceImpl mirrors
    getPlantIdentifications() exactly, minus the ownership check (userId itself scopes the query) —
    uses existing IdentificationRepository.findByUserIdOrderByCreatedAtDesc()
  - IdentificationController: GET (no path) → ApiResponse<Page<IdentificationResponse>>, 20/page default,
    sorted createdAt desc. No route collision with GET /{id} or GET /plant/{plantId} (distinct path-segment counts)
  - No new DTOs, no migration — reuses IdentificationResponse/ApiResponse<Page<T>>
  - 23 unit tests passing (new GetUserIdentifications nested class: mapped page, carePlan +
    annotationRegions parsed per item, ordering preserved from repository page)
- T4.1 Chat module — connect /chat to Ollama, remove AI Test scratch endpoint ✅ (branch: chatfix)
  - New com.plantpal.chat module: ChatRequest (@NotBlank message), ChatResponse (reply),
    ChatService/ChatServiceImpl, ChatController
  - POST /api/v1/chat → ApiResponse<ChatResponse>; userId from SecurityContextHolder, same pattern
    as IdentificationController.getCurrentUserId()
  - ChatServiceImpl.chat(): builds garden context from plantRepository.findAllByUserIdAndStatus(userId,
    ACTIVE, PageRequest.of(0,50)) — "- nickname (commonName/species/"unknown species")" per line,
    "No plants in the garden yet." if empty; concatenates CLAUDE.md's chat system prompt + garden
    context + user message into one string (OllamaClient.chat(String) is single-arg, no separate
    system-message param); chatBuckets rate limit (30/hour) — same Bucket4j pattern as
    IdentificationServiceImpl's consumeRateLimit()/consumeCureRateLimit()
  - Deleted identification/controller/AiTestController.java (dead code — frontend /ai-test page
    already removed); SecurityConfig: removed now-dead /api/v1/test/** permitAll entry
  - 3 unit tests passing (ChatServiceImplTest: garden context + reply, empty garden, 429 rate limit)
- T2.E Redis photo storage + SHA-256 dedup ✅ (branch: chatfix)
  - FileStorageService.loadPhoto(String) renamed to loadPhotoBytes(String) — consolidated rather than
    adding a second near-duplicate method; updated the one call site (IdentificationServiceImpl.
    processIdentification()) and all mock stubs in IdentificationServiceImplTest
  - CacheConfig: new byteRedisTemplate bean (RedisTemplate<String,byte[]>, StringRedisSerializer key +
    RedisSerializer.byteArray() value — NOT ByteArrayRedisSerializer, which is package-private in
    spring-data-redis 3.2.5 and fails to compile). Not @Primary — default RedisTemplate<Object,Object>
    for @Cacheable untouched.
  - LocalFileStorageService: constructor now also takes RedisTemplate<String,byte[]> byteRedisTemplate +
    StringRedisTemplate (Spring Boot auto-configures the latter). savePhoto() computes
    DigestUtils.sha256Hex(fileBytes) (org.apache.commons.codec, transitive), checks
    "photo:hash:{sha256}" in Redis first — dedup hit returns existing URL with no disk write;
    otherwise saves to disk as before, then stores raw bytes at "photo:{uuid}" and the hash→url
    mapping, both with 7-day TTL. loadPhotoBytes() tries "photo:{uuid}" in Redis first, falls back to
    disk, throws ResourceNotFoundException if neither has it (404 via existing GlobalExceptionHandler
    — no try/catch needed in the controller)
  - New shared/controller/PhotoController.java: GET /api/v1/photos/{filename} → raw bytes,
    Content-Type inferred from extension (jpeg/png/webp)
  - SecurityConfig: added "/api/v1/photos/**" to the permitAll list (existing "/photos/**" static
    StorageConfig handler left untouched)
  - 5 unit tests passing (LocalFileStorageServiceTest: dedup hit/miss on savePhoto, Redis hit/disk
    fallback/not-found on loadPhotoBytes) — mocks RedisTemplate/StringRedisTemplate, no real Redis
- T2.F Image dimension locking ✅ (branch: feature/PP-027-redis-photo-storage, merged)
  - ImageUtil.resizeAndConvertToJpeg(bytes, maxSide) + ImageUtil.readDimensions(bytes) — shared
    helper extracted out of OllamaClient into shared/util/ so IdentificationServiceImpl can reuse it
  - processIdentification(): normalizes the photo once (1024px cap) before sending to ANY provider,
    then records the resulting width/height as sourceImageWidth/sourceImageHeight on Identification
    (migration 011_add_image_dimensions.sql) — guarantees the recorded dimensions match what every
    AI provider actually saw, regardless of which one served the request
  - IdentificationResponse: +sourceImageWidth, +sourceImageHeight (nullable — null for pre-T2.F rows)
- T2.10 Garden health dashboard ✅ (branch: feature/PP-020-garden-dashboard) — split into 4 sub-tasks:
  - T2.10a: PlantResponse now populates healthStatus + nextWaterDays (previously declared but never
    set — plant-card.component.html had been rendering the badge/chip markup against permanently-null
    fields since T2.9). IdentificationRepository.findLatestPerPlant() + ReminderRepository.
    findNearestWateringPerPlant() batch-fetch per page to avoid N+1; PlantServiceImpl enriches inside
    the existing @Cacheable methods so the cached DTO is already complete. IdentificationServiceImpl
    now injects CacheManager and clears the "plants" cache after a COMPLETED re-scan.
  - T2.10b: new GET /api/v1/dashboard → DashboardResponse (healthSummary, overdueReminders,
    todayReminders, healthTrends). New com.plantpal.dashboard module. Deliberately NOT @Cacheable —
    nothing evicts it yet and a stale overdue-reminders list would be actively misleading.
    ReminderRepository.findByUserIdAndEnabledTrue() added for the overdue/today partition (injected
    Clock via shared/config/ClockConfig.java for testability).
  - T2.10c: new PlantPhotoTimelineComponent — horizontally-scrollable strip of every past scan for a
    plant (oldest→newest, left→right; API returns newest-first so the component reverses it),
    health-colored thumbnail border, click → /identify/:id. Reuses the existing paginated
    GET /api/v1/identifications/plant/{plantId} endpoint, no backend change. Sits at the top of
    plant-detail's "Overview" tab (not a new tab — avoids stacking next to the still-placeholder
    "Care History" tab).
  - T2.10d: new /dashboard landing page (DashboardModule, lazy). Root route redirect changed from
    'plants' to 'dashboard'. Bottom nav intentionally left at 4 items (Garden/Identify/Reminders/
    Chat) — reached via the toolbar brand link instead of a 5th icon.
  - Verified live end-to-end with Playwright (Docker stack already running): dashboard renders
    overdue/today sections correctly, plant-card health badge + water chip now populated.
- T2.10e Session polish — annotation overlap, "Add to care plan", garden "add" entry points ✅
  (branch: feature/PP-020-garden-dashboard, session 2026-06-18)
  - **CSS cascade bug (root cause of "Hide annotations does nothing"):** styles.scss has a global
    modern-reset rule `img, picture, video, canvas, svg { display: block; }`. Per CSS cascade rules,
    normal-importance AUTHOR rules always beat normal-importance USER-AGENT rules regardless of
    specificity — so that global reset was silently overriding the browser's own
    `[hidden] { display: none; }` default the whole time. The canvas's `hidden` attribute was
    toggling correctly (confirmed via Playwright — button label + DOM attribute both flipped, zero
    console errors) but the computed `display` never changed. Fixed in
    photo-annotator.component.scss: `canvas[hidden] { display: none !important; }` — now an
    author-vs-author fight where the attribute selector's higher specificity (and the `!important`
    insurance) wins. **Lesson:** testing `getAttribute('hidden')` is not sufficient to verify
    visibility — must check computed `display` style too.
  - PhotoAnnotatorComponent.drawAnnotations(): when a region is selected, non-selected regions are
    now skipped entirely (`continue`) instead of drawn dimmed-grey — they heavily overlap the
    selected region so dimming still visually competed with it. Removed the now-dead DIMMED_COLORS
    constant.
  - "Add to care plan" made functional (was permanently disabled since T2.9c): new
    POST /api/v1/identifications/{id}/care-plan/cards (AddCareCardRequest: regionLabel, adviceText)
    → IdentificationServiceImpl.addCareCard() loads the care plan, appends a PEST-type CareCardDto
    built from the disease label + cure-advice text (skips re-adding if a card with that exact title
    already exists — defends against double-click), persists, returns the updated CarePlanDto.
    Ownership-checked like getCureAdvice; no rate limit (pure DB read+write, no AI call).
    DiseaseDetailPanelComponent calls this after advice loads, emits `carePlanUpdated` so
    identification-preview-section and plant-detail replace their local carePlan/latestCarePlan
    reference immediately (no refetch needed). The pre-advice button stays disabled — nothing to add
    yet — tooltip corrected from the stale "Available after saving plant" to "Get cure advice first".
  - plant-list's FAB and empty-state "Add your first plant" CTA now open
    IdentificationUploadDialogComponent (same dialog identify-page uses) instead of routing to the
    manual /plants/new form — primary path to add a plant is via AI identification. Cross-module
    component import (PlantModule → IdentificationModule's dialog component) split webpack's
    `features-plant-plant-module` lazy chunk into two (~68KB extra) — expected trade-off, not a bug.
    plant.module.ts: +MatDialogModule.
  - 3 new backend unit tests (AddCareCard: append, idempotent-on-repeat, not-owned). Full suite:
    82/82 passing. Frontend: `ng build` + `ng lint` both clean.
- T2.11 Manual testing — Phase 2 complete ✅ — covered ad-hoc via the above session's live
  Playwright + manual verification rather than a separate checklist pass. **Phase 2 is done.**
- T3.1 Reminder + Care Log module backend ✅ (branch: feature/PP-011-reminder-module)
  - entity/CareLog.java, entity/PushSubscription.java — map onto the care_logs and
    push_subscriptions tables created back in migration 004/005; no new migration needed
  - ReminderService/Impl: createReminder (ownership-checked against Plant), getUserReminders
    (bounded at 200 via PageRequest — not unpaged, sorted nextDueAt ascending, batch-fetches
    plants via findAllById to avoid N+1), completeReminder (writes a CareLog + recalculates
    nextDueAt), deleteReminder (soft — sets enabled=false, never hard-deletes),
    calculateNextDueAt(lastDone, frequencyDays) = lastDone.plus(frequencyDays, DAYS)
  - CareLogService/Impl: logCare(MarkCareDoneRequest, userId) — looks up the Reminder (not the
    plant directly), writes a CareLog with the reminder's careType, recalculates the reminder's
    nextDueAt via ReminderService.calculateNextDueAt(); getPlantCareLogs (paginated,
    ownership-checked through the Plant)
  - WebPushService/Impl: nl.martijndwars:web-push, VAPID keys from @Value (web-push.public-key /
    private-key — config + .env.example already existed from T0 scaffolding, no changes needed)
  - ReminderScheduler: @Scheduled(cron = "0 0 8 * * *"), reminderRepository.findAllDue(Instant.now)
    groups due reminders by userId, sends ONE push per user ("You have N plants to care for
    today") not one per reminder; Clock injected via constructor (not bare Instant.now()) for
    testability — same pattern as T2.10b's DashboardServiceImpl
  - ReminderController (GET/POST /api/v1/reminders, DELETE /{id}), CareLogController
    (POST /api/v1/care/done, GET /api/v1/care/plant/{plantId}), NotificationController
    (POST /api/v1/notifications/subscribe)
  - 11 new unit tests (ReminderServiceTest: calculateNextDueAt, completeReminder, createReminder,
    deleteReminder, getUserReminders; CareLogServiceTest: logCare, getPlantCareLogs). Full backend
    suite: 95/95 passing.
- T3.2 Reminder Angular frontend + PWA push ✅ (branch: feature/PP-011-reminder-module — landed on
  the same branch as T3.1, not split into a separate feature branch as the original plan assumed)
  - features/reminder/: ReminderListComponent rewired from mock data to real
    ReminderService.getUserReminders()/completeReminder(); CreateReminderFormComponent (plant
    selector, care type, frequency, first due date); CareCalendarComponent (7-day due view)
  - care-log/ — new CareLogModule + CareLogComponent (timeline of past care actions), plugged into
    plant-detail's "Care History" tab — that tab had been a static "coming in Phase 3" placeholder
    since T2.9
  - core/services/push-notification.service.ts: requestPermission() (Notification.requestPermission
    wrapped as a Promise<boolean>), subscribeToNotifications() (ServiceWorkerRegistration
    PushSubscription → POST /api/v1/notifications/subscribe)
  - app.component: inline "Get reminders on your phone" banner shown on first load (logged in +
    not yet asked) with Accept/Dismiss — deliberately NOT the raw browser permission prompt first
  - environment.ts/.prod.ts: +vapidPublicKey
  - plant.module.ts: +CareLogModule, +CareLogService, +MatDialogModule (the last one already added
    in T2.10e for the identification-upload dialog)
  - `ng build` + `ng lint` both clean
  - **Bug found via live user testing (2026-06-18), fixed same session:** `CareLogService.baseUrl`
    was `${environment.apiUrl}/care-logs` but `CareLogController` is mapped at `/api/v1/care` (not
    `/api/v1/care-logs`) — every `getPlantCareLogs()` call 404'd. `CareLogComponent.ngOnInit()`'s
    `error` callback just sets `loading = false`, no toast/message, so the "Care History" tab
    silently rendered an empty state regardless of how much real care-log data existed — looked
    like "no data" rather than "broken request." This is why `ng build`/`ng lint`/unit tests all
    passed clean despite the bug: unit tests mock the service layer directly and never exercise the
    real HTTP path, and there's no `CareLogControllerIT`. Fixed by correcting the baseUrl to
    `${environment.apiUrl}/care`. **Lesson:** a route typo between a frontend service's baseUrl and
    its controller's `@RequestMapping` is invisible to build/lint/unit-test gates — only an actual
    network call (manual click-through, or an integration test) catches it. Re-verify live before
    trusting this fixed.
- T3.4 Backend: actionable care plans — ROUTINE reminders + multi-step TREATMENT plans ✅
  (branch: feature/PP-028-actionable-care-plans-2, session 2026-06-18)
  - Migration 012_add_treatment_plans.sql: new `treatment_plans` table (title,
    source_care_card_type, diagram_format, diagram_content, status); `reminders` gains
    `recurring` (default true), `treatment_plan_id` (FK, ON DELETE CASCADE),
    `treatment_plan_title` (denormalized — avoids a join on every reminder-list fetch),
    `step_order`
  - `CareType` expanded from 4 values to mirror `CareCardType`'s 10 (WATERING, LIGHT, HUMIDITY,
    TEMPERATURE, FERTILIZING, REPOTTING, PRUNING, PEST, SEASONAL, BEGINNER_TIP) — additive,
    existing rows unaffected
  - New `TreatmentPlan` entity + `TreatmentPlanStatus` (ACTIVE|COMPLETED|ABANDONED) — does NOT
    extend AuditableEntity, same pattern as `Reminder`. Treatment steps are one-time `Reminder`
    rows (`recurring=false`), NOT a parallel entity.
  - **Unified completion logic** (the actual bug found while designing this): `ReminderService.
    completeReminder()` and `CareLogService.logCare()` were two independent "log + reschedule"
    code paths. Replaced both with one `ReminderService.applyCompletionToReminder(reminder,
    performedAt)`: recurring → reschedules nextDueAt (old behaviour, unchanged); one-time →
    disables, and if it was the treatment plan's last enabled step
    (`ReminderRepository.findByTreatmentPlanIdAndEnabledTrue` returns empty after disabling),
    flips the plan to COMPLETED. `CareLogServiceImpl.logCare()` no longer touches
    `reminderRepository.save()` itself — pure delegation.
  - New `com.plantpal.identification.dto`: `ActionPlanDto` (type ROUTINE|TREATMENT,
    frequencyDays, steps, diagram), `TreatmentStepDto` (order, instruction, dueOffsetDays),
    `DiagramDto` (format — only "MERMAID" supported, content). `CareCardDto.actionPlan` added
    (nullable — most cards stay informational-only).
  - New `ActionPlanValidator.normalize()` (identification/util/) — the single choke-point every
    AI-sourced action plan passes through before touching the DB: never throws, degrades to
    null on anything malformed. ROUTINE: frequencyDays must be 1-365 or the whole plan is
    rejected (null). TREATMENT: steps must be non-empty; truncated to first 10 (WARN logged);
    each step's dueOffsetDays clamped to 0-180; **order is always re-numbered 1..N from scratch
    — the AI's own order values are ignored entirely**, guaranteeing no gaps/dupes/out-of-
    sequence regardless of what the model returns. Diagram kept only if format
    equalsIgnoreCase "MERMAID" AND content non-blank AND ≤2000 chars, else just the diagram is
    nulled (rest of the TREATMENT plan stays valid).
  - `GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT` and `DeepSeekClient.
    CARE_PLAN_SYSTEM_PROMPT` both updated with the same actionPlan schema addition per care
    card (kept in sync — both feed the same `parseCarePlan()`), plus the "only TREATMENT for
    3+ distinct actions" / "only diagram when there's real branching logic" guidance.
  - `DeepSeekClient.generateCureAdvice()`: response_format changed to `json_object`; new
    `CURE_ADVICE_SYSTEM_PROMPT` requests `{advice, actionPlan}` instead of plain text.
    `IdentificationServiceImpl.parseCureAdvice()` parses via an internal `CureAdviceJson`
    holder class; **on JSON parse failure, falls back to treating the entire raw string as
    `advice` with `actionPlan=null`** — this is why the pre-existing CureAdvice unit tests
    (which mock plain-text responses) kept passing unmodified: plain text is simply invalid
    JSON, so it naturally exercises the fallback path. `CureAdviceResponse` gained
    `actionPlan`.
  - `AddCareCardRequest` gained an optional `actionPlan` field; `IdentificationServiceImpl.
    addCareCard()` re-validates it through `ActionPlanValidator.normalize()` before attaching
    — never trusts a client-supplied DTO without re-validating server-side, since this is the
    one action-plan entry point that isn't AI-generated in the same request.
  - New `TreatmentPlanService`/`Impl`: `createFromActionPlan(plantId, userId, title,
    sourceCareCardType, actionPlan)` — rejects non-TREATMENT or empty-steps plans
    (ValidationException), ownership-checks the plant, parses `sourceCareCardType` into a
    `CareType` (ValidationException if missing/unrecognised), creates the `TreatmentPlan` row,
    then one `Reminder` per step (`nextDueAt = now + dueOffsetDays`). `getTreatmentPlan(id,
    userId)` — ownership-checked, returns ALL steps via the new
    `ReminderRepository.findByTreatmentPlanIdOrderByStepOrder` (deliberately not the
    enabled-only query — the detail view needs completed steps too).
  - New `TreatmentPlanController`: `POST /api/v1/treatment-plans` (201), `GET
    /api/v1/treatment-plans/{id}`. Both auto-protected by the existing `anyRequest()` security
    rule — no SecurityConfig change needed.
  - 39 new/updated unit tests: `ActionPlanValidatorTest` (21 — every clamp/reject/boundary
    case, the highest-value test in this task), `TreatmentPlanServiceTest` (10),
    `ReminderServiceTest` `ApplyCompletionToReminder` nested class (4: recurring reschedules,
    one-time disables, last-step completes the plan, non-last-step leaves it ACTIVE),
    `CareLogServiceTest` updated (verifies `logCare()` delegates to `applyCompletionToReminder`
    and no longer duplicates rescheduling logic itself). Full backend suite: **132/132
    passing**, checkstyle clean.
  - Frontend (T3.5) appears partially underway on the same branch (untracked
    `set-reminder-dialog.component.*`, `treatment-plan.model.ts`, `treatment-plan.service.ts`,
    `shared/components/mermaid-diagram/`, plus modified care-card/disease-detail-panel files)
    from a separate session — not verified or touched in this backend session.
- T3.5 Frontend: actionable care plans UI — Mermaid diagrams, "Set reminder" / "Start treatment
  plan" ✅ (branch: feature/PP-028-actionable-care-plans-2, session 2026-06-18)
  - `npm install mermaid` (official package, ESM) — dynamically `import('mermaid')`'d only inside
    `MermaidDiagramComponent`, so mermaid's own internal per-diagram-type chunks (flowchart, gantt,
    sequence, etc. — it code-splits itself) never touch the initial bundle, only load the first
    time a diagram actually renders. Confirmed via `ng build`: initial bundle size unchanged.
  - `identification.model.ts`: +`ActionPlanDto`/`TreatmentStepDto`/`DiagramDto`,
    +`CareCardDto.actionPlan` — mirrors the backend DTOs from T3.4 field-for-field
  - `identification.service.ts`: `getCureAdvice()` now returns `{advice, actionPlan}` (was
    `Observable<string>`); `addCareCard()` gained an optional `actionPlan` param sent in the body
  - **`reminder.model.ts`: `CareType` widened from 4 values to the same 10 as `CareCardType`** —
    found mid-session by reading T3.4's STATE.md entry (not assumed), since the backend already
    expanded `CareType` to mirror `CareCardType`. Without this, a ROUTINE actionPlan on a
    LIGHT/HUMIDITY/TEMPERATURE/PEST/SEASONAL/BEGINNER_TIP card couldn't type-check against
    `createReminder()`'s `careType` param. Propagated the same 4→10 fix to `dashboard.model.ts`'s
    separate `CareType` duplicate — it would have compiled fine either way but silently shown no
    icon for any reminder summary carrying one of the 6 new types. New
    `reminder/models/care-icon.util.ts` (one `CARE_ICONS` map + `careIcon()` fn) replaces four
    independently hand-maintained copies of the same Record (reminder-list, care-calendar,
    care-log, dashboard) — dashboard kept its own copy since it's a separate lazy module with no
    existing dependency on `reminder/`, not worth adding one just for an icon map.
  - `ReminderResponse` also gained `treatmentPlanId`/`treatmentPlanTitle`/`stepOrder`/`recurring`
    (matches the backend's `reminders` table additions) plus two fields **not confirmed against an
    actual backend DTO field list** — flagged below.
  - `reminder.service.ts`: removed the old `completeReminder()` (`POST /reminders/{id}/complete` —
    never matched a real backend route) and replaced with `markCareDone(reminderId)` →
    `POST /api/v1/care/done` with `{reminderId}` body — matches the real `CareLogController`
    endpoint from T3.1. `reminder-list.component.ts`'s mark-done button now calls this.
  - New `reminder/services/treatment-plan.service.ts`: `createFromActionPlan()` → POST
    `/api/v1/treatment-plans`, `getTreatmentPlan(id)` → GET `/api/v1/treatment-plans/{id}` —
    matches `TreatmentPlanController` from T3.4 exactly (path, both DTO shapes)
  - New `shared/components/mermaid-diagram/` (declared+exported in `SharedModule`, not lazy):
    `mermaid.initialize()` called once via a module-level boolean guard (not per-instance) with
    `theme: 'base'` + the app's forest-green `themeVariables`; renders via
    `mermaid.render(uniqueId, definition)` in a try/catch; success → SVG string through
    `DomSanitizer.bypassSecurityTrustHtml()` (safe — SVG comes from mermaid's own renderer, not
    raw AI text) into `[innerHTML]`; failure (malformed AI-generated mermaid syntax) → renders
    nothing at all, no error UI, host collapses to 0 height — diagrams are always a bonus on top
    of the step list, never required reading.
  - `care-card.component.ts/html`: new action row behind a thin divider, only rendered when
    `card.actionPlan` is non-null AND a real `plantId` is available (nullable — see below).
    ROUTINE → outlined "Set reminder" opens new `SetReminderDialogComponent` (~360px MatDialog,
    frequency pre-filled from `actionPlan.frequencyDays`) → `ReminderService.createReminder()`;
    success swaps the button to a disabled "Reminder set" state (mirrors disease-detail-panel's
    existing "Added to care plan" pattern — not a new visual language). New `@Input()
    existingCareTypes: CareType[]` — if the plant already has an enabled reminder of this exact
    careType, skips straight to the disabled "Reminder set" state so users aren't invited to
    create a duplicate. TREATMENT → filled "Start treatment plan" calls `TreatmentPlanService.
    createFromActionPlan()` directly (no dialog); spinner + "Starting…" while in flight; success →
    snackbar ("Treatment plan started" + "View" action → `/treatment-plans/{id}`) and the button
    becomes a disabled "Plan in progress" state.
  - `care-plan.component.ts/html` gained `@Input() plantId: number | null` + `@Input()
    existingCareTypes`, passed straight through to every `app-care-card`. **`plantId` is
    nullable** because `<app-care-plan>` is also used in `preview-card` before a plant exists
    (wired to `result.plantId`, non-null only if the user linked an existing plant during upload)
    — the action row simply doesn't render when `plantId` is null. `plant-detail.component.ts`
    now also calls `ReminderService.getReminders()` and filters to this plant's enabled care
    types for `existingCareTypes`.
  - `disease-detail-panel.component.ts/html` gained the same `[plantId]` input (from `plant.id` in
    plant-detail, `result.plantId` in identification-preview-section) and a second "Start
    treatment plan" button beside "Add to care plan" — shown only when `askForCure()`'s response
    includes a TREATMENT actionPlan; the common case (null or ROUTINE) is visually unchanged from
    before this session.
  - New `reminder/pages/treatment-plan-detail/` — route `/treatment-plans/:id`. Header (back via
    `Location.back()`, not a fixed routerLink, since this page is reachable from three different
    places: a care-card snackbar, a disease-panel snackbar, or a reminder-list chip) + status chip
    (Active/Completed/Abandoned) + "N of M steps complete" progress line + optional "How this
    works" `<app-mermaid-diagram>` card (only if `diagramContent` is present) + a numbered-circle
    vertical step timeline (same connecting-line visual language as `care-log`'s timeline) with a
    compact "Mark done" per pending step → `markCareDone()` → refetches the whole plan so the
    progress line and status chip update together — no separate completion endpoint needed.
  - **Routing restructure — read before touching reminder routes again:** `/treatment-plans/:id`
    needed to be a clean top-level URL, not nested under `/reminders`. Rather than mount
    `ReminderModule` twice, `app-routing.module.ts`'s `reminders` entry changed from
    `path: 'reminders'` to `path: ''` (commented), and `reminder-routing.module.ts` now defines
    `'reminders'` and `'treatment-plans/:id'` as sibling routes under that empty mount.
    `canActivate: [AuthGuard]` still guards the whole subtree regardless of the empty path. There
    are now two `path: ''` entries in `app-routing.module.ts` — intentional, not a bug; array
    order is what makes both work (the pre-existing auth-module catch-all stays last).
  - `reminder-list.component.html`: rows where `reminder.treatmentPlanId` is set show a small
    clickable `medical_services` chip ("Step N · {{ treatmentPlanTitle }}", routerLink to the
    detail page, `stopPropagation()` so it doesn't also trigger the row's `/plants/:id`
    navigation) instead of the plain `{{ careType | titlecase }}` text.
  - `ng build` (dev + prod) and `ng lint` all clean. Production build also confirms
    `ngsw.json`/service-worker generation still succeeds with mermaid in the dependency graph.

  **Known gaps — flag for the next backend or frontend pass:**
  - "Plan in progress" / "Reminder set" button states are session-only component booleans, not
    derived from anything persisted on `CareCardDto` — a page refresh re-shows the actionable
    button even after a plan/reminder was already created from that exact card. Lowest-risk fix:
    a `CareCardDto.actionTaken` boolean from the backend, set once something has actually been
    created from that specific card.
  - ✅ RESOLVED 2026-06-19 (see T3.4b below): `ReminderResponse.instruction`/`.completedAt` are no
    longer dead frontend fields — backend now populates both.

### T3.4b — Fix: treatment-plan steps showed bare careType instead of instructions ✅
  (branch: feature/PP-028-actionable-care-plans-2, session 2026-06-19)
  - **Bug, found via live screenshot:** every step in a treatment-plan detail page rendered the
    plan's `careType` ("PEST") instead of the AI-generated instruction text — confirmed the
    T3.5 "known gap" above was the root cause, not a rendering bug. `TreatmentStepDto.instruction`
    (validated by `ActionPlanValidator`) was discarded the moment a step became a `Reminder` row:
    `Reminder` entity had no column to hold it, so `TreatmentPlanServiceImpl.
    createFromActionPlan()` never had anywhere to put it. `ReminderResponse` had no
    `instruction`/`completedAt` fields either — the frontend had speculatively declared both as
    optional (`reminder.model.ts`) anticipating a backend that never shipped them, so
    `stepInstruction()`'s `step.instruction ?? step.careType` fallback fired on every single step.
  - Migration `013_add_reminder_instruction.sql`: `reminders.instruction TEXT` (nullable —
    only treatment-plan steps set it, routine reminders stay null)
  - `Reminder` entity: +`instruction` field
  - `TreatmentPlanServiceImpl.createFromActionPlan()`: each generated `Reminder` now sets
    `.instruction(step.getInstruction())`
  - New `reminder/mapper/ReminderMapper.java` (static utility, same pattern as
    `ActionPlanValidator`): the single `Reminder`+`Plant` → `ReminderResponse` mapping point.
    Replaces two independent hand-written copies that had drifted (`ReminderServiceImpl.
    toResponse()` and `TreatmentPlanServiceImpl.toReminderResponse()` — neither one ever set
    `instruction`/`completedAt`, which is exactly how this bug shipped unnoticed). Both service
    classes now delegate their private `toResponse()`/`toReminderResponse()` methods to
    `ReminderMapper.toResponse()`.
  - `completedAt` populated as `reminder.getUpdatedAt()` when `!enabled`, else `null` — free, since
    `@UpdateTimestamp` already flips the moment `applyCompletionToReminder()` disables a step; no
    new timestamp tracking needed.
  - No frontend changes required — `reminder.model.ts`'s `instruction`/`completedAt` fields were
    already wired up and waiting.
  - Diagram (the other thing flagged in the screenshot) is very likely NOT a bug: both
    `GitHubModelsClient`'s and `DeepSeekClient`'s prompts explicitly say "only include a diagram
    when the steps have real branching/decision logic" — a linear 4-step pest treatment has none,
    so a null diagram there is expected. Re-check against a treatment plan with genuine branching
    before assuming this needs a fix too.
  - Full backend suite still green: 132/132 passing after the change.

### T3.9 — Scans tab inline selection, cure-advice caching, "Add scan" button, AI model badge ✅
  (branch: dev, session 2026-06-19, requested ad-hoc by the user)
  - **Inline scan selection:** clicking a thumbnail in the Scans tab's photo timeline used to
    navigate away to `/identify/:id`. `PlantPhotoTimelineComponent` now emits `@Output()
    scanSelected` (full `IdentificationResponse`, already carries `carePlan`/`annotationRegions`
    from `getPlantIdentifications` — no extra fetch needed) instead of using `routerLink`; also
    auto-emits the newest scan once loaded if nothing is selected yet, and accepts `@Input()
    selectedScanId` to highlight the active thumbnail (`box-shadow` ring). `ngOnInit`'s body moved
    into a private `loadScans()`, with a new public `reload()` wrapper for the "Add scan" flow
    below. `PlantDetailComponent` now holds `selectedScan: IdentificationResponse | null` and
    renders the photo-annotator/annotation-list/disease-detail-panel block from it instead of the
    old `latestPhotoUrl`/`latestAnnotationRegions`/`latestIdentificationId` trio (removed — fully
    superseded). `latestIdentificationId` was kept, but repurposed: it's only used now to detect
    whether `onCarePlanUpdated()`'s edited scan is the SAME one the Care Plan tab is showing
    (`latestCarePlan`), so adding a cure-advice care card to a non-latest scan doesn't silently
    overwrite the Care Plan tab with stale data.
  - **Cure-advice caching:** `DiseaseDetailPanelComponent` previously reset `advice`/`actionPlan`
    to `null` on every `region` input change (including re-selecting a region already asked
    about), forcing a fresh "Ask for cure" click every time. New `Map<string, CureCacheEntry>`
    keyed `${identificationId}:${regionLabel}` — `ngOnChanges` checks the cache first and restores
    `advice`/`actionPlan`/`addedToPlan`/`treatmentStarted` immediately if present, skipping
    straight to the advice view instead of "Ask for cure". Written to on every successful
    `askForCure()`/`addToCarePlan()`/`startTreatmentPlan()` response. Keyed by identificationId so
    switching scans (see above) never leaks one scan's advice into another, even if region labels
    coincide.
  - **"Add scan" button:** new button in the Scans tab header opens
    `IdentificationUploadDialogComponent` pre-locked to the current plant — `PhotoUploadComponent`
    gained `@Input() lockedPlantId`, which pre-sets `selectedPlantId` and hides the "Link to
    existing plant" dropdown entirely (nothing to pick, already known) instead of just
    pre-selecting it. `IdentificationUploadDialogComponent` now accepts optional `MAT_DIALOG_DATA`
    (`{plantId, plantNickname}`, `@Optional()` since `plant-list`'s existing unlocked usage passes
    no data at all) and shows "Add a scan for {{ plantNickname }}" as the dialog title when
    present. `PlantDetailComponent.openAddScanDialog()` mirrors `plant-list`'s existing
    `openIdentifyDialog()`/`submitIdentification()` pattern; on success it calls
    `timeline.reload()` (via a new `@ViewChild`) rather than navigating away, so the new (still
    PENDING) scan appears in the strip immediately without leaving the page.
  - **AI model badge:** new `Identification.aiModelUsed` column (migration
    `015_add_ai_model_used.sql`) + `IdentificationResponse.aiModelUsed` — set in
    `IdentificationServiceImpl.processIdentification()` from a new `runIdentification()` return
    type, `private record IdentificationOutcome(String rawJson, String providerUsed)`, instead of
    just `String`. This was a deliberate accuracy fix, not just plumbing: the OLLAMA_LLAVA → 503 →
    GitHubModels fallback path (existing behavior, unchanged) means the actual provider can differ
    from the user's requested `AiModelPreference`; recording the *requested* preference alone
    would have mislabeled GPT-4o-served identifications as "Ollama". Two new tests cover both the
    direct case and the fallback case. Frontend: `aiModelLabel()` helper in
    `identification.model.ts` maps the four preference values to display labels (`DEEPSEEK` →
    "GPT-4o" — DeepSeek-R1 only ever generates care-plan *text*, gpt-4o still does the actual
    identification, so labeling it "DeepSeek" would have been misleading to a user reading the
    badge); shown as a small chip next to the date in `IdentificationListComponent`'s rows (the
    "identification cards" the user meant).
  - Backend: 142/142 tests passing, `mvn compile` clean. Frontend: `ng build` + `ng lint` clean.

### T3.8 — Four small fixes: reminder cascade-archive, cropped care-card text, Scans tab refactor,
  image lightbox ✅ (branch: dev, session 2026-06-19, requested ad-hoc by the user)
  - **Archive cascade bug:** `PlantServiceImpl.archivePlant()` archived the `Plant` row but left all
    of its `Reminder`s (including treatment-plan steps, since those are just `recurring=false`
    `Reminder` rows) enabled forever — an archived plant kept generating push notifications and
    showing up in `getUserReminders()`/dashboard "Today"/"Needs attention" sections indefinitely.
    Fixed: new `ReminderRepository.findByPlantIdAndEnabledTrue(plantId)` + private
    `disableRemindersForPlant(plantId)` in `PlantServiceImpl`, called right after the plant save in
    `archivePlant()` — disables (never deletes) every enabled reminder for that plant. New unit
    test `shouldDisableRemindersOnArchive`.
  - **Cropped care-card header text:** `care-card.component.scss`'s `.card-summary` had
    `white-space: nowrap; overflow: hidden; text-overflow: ellipsis` — any summary sentence longer
    than the available header width silently truncated with no way to read the rest (expanding the
    card didn't help, since the truncated text was in the always-visible header, not the
    expand-only detail section). Fixed by removing the truncation rules, letting it wrap normally.
  - **"Last Scan" → "Scans" tab refactor:** the tab only ever showed the single latest annotated
    scan, with no way to see past scans or which photo went with which result. `<app-plant-photo-
    timeline>` (built in T2.10c, was sitting in the Overview tab) moved into this tab instead —
    semantically it's scan history, not general plant info — and the tab itself renamed "Last Scan"
    → "Scans" (in both the tab label and the Overview tab's "Check the Scans tab" cross-reference
    text). Layout: photo timeline (full scan history, click → `/identify/:id`) on top, "Latest scan"
    subheading + divider, then the existing photo-annotator/annotation-list/disease-detail-panel
    block unchanged below. No new component — pure relocation + a small heading.
  - **Image lightbox:** new shared `ImageLightboxComponent` (`shared/components/image-lightbox/`,
    declared+exported from `SharedModule`, opened via `MatDialog.open()`) — a plain MatDialog
    showing the full-size image with a close button and click-outside-to-close, transparent dialog
    panel (`.image-lightbox-panel` global override in `styles.scss`, since the default Material
    dialog surface is an opaque white card that looked wrong around a photo). Wired into
    `PlantCardComponent`'s photo (the garden/plant-list grid — interpreted "home plant area" as the
    main plant grid, not the dashboard rows or plant-detail hero, since that's the page most
    centrally about viewing plant photos; flagged to the user as a scoping call they can redirect)
    — clicking the image opens the lightbox (`stopPropagation` so it doesn't also trigger the
    card's `routerLink` navigation), clicking elsewhere on the card still navigates as before.
  - `ng build` + `ng lint` clean (only pre-existing SCSS budget warnings). Backend: 140/140 passing.

### T3.7 — Fix: actionPlan validation gap + missing prompt constraints ✅
  (branch: dev, session 2026-06-19 — found via gap audit against a stale planning brief, not a
  reported bug)
  - **Bug:** `ActionPlanValidator.normalize()` was documented (ARCHITECT.md) as "the single
    choke-point every AI-sourced action plan passes through before touching the DB," but it was
    only wired into `addCareCard()` and `parseCureAdvice()`. The primary identification flow
    (`IdentificationServiceImpl.processIdentification()`) persisted `result.getCarePlan()` —
    straight from `GitHubModelsClient`/`DeepSeekClient`'s raw JSON — with zero per-card
    `actionPlan` validation, for every single photo identification (the highest-volume path).
    No frequencyDays clamping, no step truncation/reordering, no diagram length/format checks, no
    degenerate-plan nulling, for that path.
  - Fix: new private `IdentificationServiceImpl.normalizeActionPlans(CarePlanDto)` — iterates
    `careCards`, replaces each card's `actionPlan` via the existing `ActionPlanValidator.normalize()`
    (no new validation logic). Called once, right after `fallbackCarePlan()` resolution and before
    `setCarePlan()`/persist in `processIdentification()`.
  - Also added to all three AI system prompts: Mermaid diagrams restricted to `flowchart LR`/
    `flowchart TD` only, no `subgraph`/click events/style blocks, no double quotes in node labels
    (`MermaidDiagramComponent` fails silently on malformed DSL, so unconstrained syntax was
    producing diagrams that silently never render — likely explains why diagrams were never
    confirmed live). Also added the per-card-type restriction (actionPlan null for LIGHT/HUMIDITY/
    TEMPERATURE/SEASONAL; ROUTINE only for WATERING/FERTILIZING/REPOTTING/PRUNING; TREATMENT only
    for PEST and WATERING/FERTILIZING-with-issues) to the two care-plan-generating prompts.
  - Full backend suite: 139/139 still passing, `mvn compile` clean. No DTO/schema changes — purely
    wiring an existing validator into a path that was missing it, plus prompt text.
  - See ARCHITECT.md "T3.7 fix" entries for the full reasoning.

### T3.6 — Per-step "How to" detail: substeps text + optional Mermaid diagram ✅
  (branch: feature/PP-028-actionable-care-plans-2, session 2026-06-19)
  - **Feature request, not a bug:** user wanted a step like "create a solution to add to pot" to
    carry richer guidance than a one-line instruction — either AI-generated explanatory text
    (substeps/precautions) or an illustration. Two options were on the table: per-step Mermaid
    diagrams (reusing the existing plan-level diagram pattern), or a "How to" button opening a
    modal with text. Went with both, combined: each `TreatmentStepDto` can now carry an optional
    `detail` (free text) AND an optional `diagram` (same `DiagramDto` shape as the plan-level one)
    — a step row only shows the "How to" icon-button when at least one is present; the modal
    renders whichever the AI actually provided. This is additive to (not a replacement for) the
    plan-level diagram from T3.4, which still only fires for branching logic *across* steps.
  - `TreatmentStepDto` (identification/dto/): +`detail` (String), +`diagram` (DiagramDto)
  - `ActionPlanValidator.normalizeTreatment()`: each step's `detail` is trimmed, blank→null,
    capped at 1000 chars (`normalizeStepDetail()`); each step's `diagram` reuses the exact same
    `normalizeDiagram()` private method already used for the plan-level diagram — same
    MERMAID-only / non-blank / ≤2000-char rules, just called once per step too. 7 new
    `ActionPlanValidatorTest` cases (nested `StepDetailAndDiagram`), including one proving a
    step's diagram is validated independently from the plan's (one can be present while the other
    is null).
  - Migration `014_add_step_detail.sql`: `reminders.step_detail TEXT`,
    `reminders.step_diagram_format VARCHAR(20)`, `reminders.step_diagram_content TEXT` — all
    nullable, only ever set for treatment-plan steps that got per-step detail from the AI.
  - `Reminder` entity, `ReminderResponse`, `ReminderMapper`: all three fields plumbed through —
    `ReminderMapper` (introduced in T3.4b above) meant this was a single mapping point to update
    instead of two.
  - `TreatmentPlanServiceImpl.createFromActionPlan()`: copies `step.getDetail()` and
    `step.getDiagram()` onto each generated `Reminder` row.
  - All three AI system prompts updated in lockstep (the established T3.4 pattern —
    `GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT`, `DeepSeekClient.
    CURE_ADVICE_SYSTEM_PROMPT`, `DeepSeekClient.CARE_PLAN_SYSTEM_PROMPT`): each step in the JSON
    schema gained `detail` and `diagram` fields, with explicit guidance that both are rare — "only
    fill detail when the step needs more than the one-line instruction (mixing ratio, multi-part
    procedure, precautions)" and "only give a step its own diagram when it has its own
    multi-part procedure or branching — this is rarer than even the plan-level diagram."
  - Frontend: `identification.model.ts` `TreatmentStepDto` +`detail?`/+`diagram?`;
    `reminder.model.ts` `ReminderResponse` +`stepDetail?`/+`stepDiagramFormat?`/
    +`stepDiagramContent?`.
  - New `reminder/components/step-detail-dialog/` (`StepDetailDialogComponent`, declared in
    `ReminderModule` — `MatDialogModule` was already imported there from `SetReminderDialogComponent`):
    plain MatDialog, header = step instruction, body = `stepDetail` text (if present) +
    `<app-mermaid-diagram>` (if `stepDiagramContent` present) — same dynamic-import/render-failure-
    is-silent behavior as every other Mermaid usage in the app, nothing new to harden there.
  - `treatment-plan-detail.component.ts/html`: each step row's instruction text is now wrapped in
    a flex row (`.step-instruction-row`) alongside a small `mat-icon-button` ("How to" /
    `info_outline`, `matTooltip="Additional info on how to"`) — `*ngIf="hasStepDetail(step)"` so
    the button only renders when the step actually has `stepDetail` or `stepDiagramContent`; click
    opens `StepDetailDialogComponent` with the step's data. No change to the plan-level diagram
    section above the step list — that's still T3.4/T3.5's behavior, untouched.
  - Backend: 139/139 tests passing (132 + 7 new). `mvn compile` clean.
  - Frontend: `ng build` and `ng lint` both clean (only pre-existing SCSS budget warnings, one of
    which grew slightly from the new `.step-instruction-row`/`.btn-how-to` styles — not a new
    warning, an existing one on this same file from T3.4/T3.5's step-list CSS).

## Active Branches
- feature/PP-023-enhanced-annotation-backend — merged to dev as PR #15 ✅
- feature/PP-017-visual-annotation — T2.9b + T2.9c complete, merged PR #17 ✅
- AddChooseAi — AI model preference feature — merged PR #20 ✅
- feature/PP-025-github-models-client — T2.A + T2.B complete, commit 086cd07 ✅ (open PR or merge to dev)
- chatfix — T4.1 + T2.E complete, merged ✅
- feature/PP-020-garden-dashboard — T2.10a-d + T2.10e + T2.11 complete, 3 commits ✅ (Phase 2 done;
  open PR or merge to dev)
- feature/PP-011-reminder-module — T3.1 + T3.2 complete, commit d9b82c1 ✅ (T3.3 manual testing
  remaining before merge)
- feature/PP-028-actionable-care-plans-2 (current) — T3.4 + T3.5 + T3.4b + T3.6 all complete merged to dev as PR #33 ✅
- feature/PP-030-treatment-entity (current) — T6.1 + T6.2 completed , next T6.3
## Next Tasks (in order)
- Commit T3.4 + T3.5 + T3.4b + T3.6 on feature/PP-028-actionable-care-plans-2 (currently
  uncommitted — see `git status` for the full file list), then open a PR / merge to dev
- Re-verify live (Docker stack): treatment-plan-detail shows real instruction text per step
  (T3.4b), and the new "How to" button + modal appears/works for AI-generated steps that actually
  came back with `stepDetail`/`stepDiagramContent` (T3.6) — neither has been confirmed against a
  live AI response yet, only unit-tested and build-verified
- T3.3 — Manual testing — Phase 3 (now also covers T3.4/T3.5 flows; still needs a human with a
  phone for the push/PWA checks)
- T4.1 already done (basic chat); Phase 4 polish (streaming, history) not started
- Phase 6 — Species & Treatment domain restructure (T6.1–T6.14 below) — recommended to execute
  BEFORE Phase 5, see TASK_PLAN.md for full prompts
- Phase 5 — Launch prep (T5.1–T5.8, already defined in TASK_PLAN.md, not started)

## Phase 6 — Species & Treatment Domain Restructure (planned 2026-06-19, session 18)
> Restructures the core domain from plant-centric to species-centric: a new shared `Species`
> entity (one row per botanical species, shared across users), a new `Treatment` entity (active
> disease-treatment plan for a specific plant+disease), a 5-item bottom nav + new Home screen, a
> species-first Garden page, and a redesigned Plant page (sticky header, icon button bar nav).
> Full task prompts are in TASK_PLAN.md. None of T6.1–T6.14 have been started — this is a
> planning-only session. See ARCHITECT.md for the new domain model patterns, the identification
> 3-path decision tree, the Treatment lifecycle state machine, and the sticky-header Angular
> pattern.

> ⚠️ **Naming collision to watch for during implementation:** this Phase introduces a `Treatment`
> entity (disease treatment for a plant). T3.4 already introduced a DIFFERENT `TreatmentPlan`
> entity (multi-step reminder-backed plan, ACTIVE|COMPLETED|ABANDONED, routed at
> `/treatment-plans/:id`). They are NOT the same thing and will coexist:
> - `TreatmentPlan` (T3.4, existing) — generic multi-step action plan generated from ANY care
>   card's `actionPlan` (could be routine watering reminders OR a pest treatment), backed by
>   `Reminder` rows.
> - `Treatment` (T6.2, new) — specifically a disease-treatment record tied to one
>   `plant_id + identification_id + diseaseName`, with its own `planJson` and lifecycle, reached
>   from the Plant page's new icon-button-bar "treatment" tab.
> T6.2's `Treatment.planJson` likely OVERLAPS conceptually with what `TreatmentPlan` already
> does. Flagged in TASK_PLAN.md's T6.2 prompt — whoever implements T6.2 should re-evaluate
> whether `Treatment` should just wrap/reference a `TreatmentPlan` row instead of duplicating the
> careCard/actionPlan JSON storage, before writing the migration.

| Task | Name | Agent | Branch | Depends on | Status |
|---|---|---|---|---|---|
| T6.1 | Species entity + migrations + endpoints | Backend | `feature/PP-029-species-entity` | — | ✅ |
| T6.2 | Treatment entity + migrations + endpoints | Backend | `feature/PP-030-treatment-entity` | T6.1 | ✅ |
| T6.3 | Plant entity updates + scan flow changes | Backend | `feature/PP-031-plant-species-fk` | T6.1, T6.2 | 🔲 |
| T6.4 | Species data enrichment async service | Backend | `feature/PP-029-species-entity` (same) | T6.1 | 🔲 |
| T6.5 | Garden species-first restructure | Frontend | `feature/PP-032-garden-species-first` | T6.1, T6.3 | 🔲 |
| T6.6 | Species detail page | Frontend | `feature/PP-032-garden-species-first` (same) | T6.1, T6.4 | 🔲 |
| T6.7 | Home page | Frontend | `feature/PP-033-home-page` | T6.3 | 🔲 |
| T6.8 | Bottom nav 5 items + routing | Frontend | `feature/PP-033-home-page` (same) | T6.7 | 🔲 |
| T6.9 | Identification flow redesign — species matching | Frontend + Backend | `feature/PP-034-identification-species-matching` | T6.1, T6.3 | 🔲 |
| T6.10 | Plant page: sticky header + icon button bar | Frontend | `feature/PP-035-plant-page-redesign` | T6.3 | 🔲 |
| T6.11 | Plant page: scans tab + treatment CTA | Frontend | `feature/PP-035-plant-page-redesign` (same) | T6.2, T6.10 | 🔲 |
| T6.12 | Treatment page | Frontend | `feature/PP-036-treatment-page` | T6.2 | 🔲 |
| T6.13 | Chat: plant context injection | Frontend + Backend | `feature/PP-037-chat-plant-context` | T6.3 | 🔲 |
| T6.14 | Reminders: wire treatment plan steps | Backend | `feature/PP-030-treatment-entity` (same) | T6.2 | 🔲 |

- T6.1 Species entity + migrations + endpoints ✅ (backend, branch `feature/PP-029-species-entity`,
  session 2026-06-19)
  - New `com.plantpal.species` package: `Species` entity (extends `AuditableEntity`, NOT user-scoped
    — no userId field, no ownership check on the row itself), `SpeciesStatus` (ACTIVE|NEEDS_REVIEW),
    `SpeciesRepository` (findByScientificName, existsByScientificName), `SpeciesMapper` (MapStruct,
    toResponse only), `SpeciesResponse`/`SpeciesSummaryDto` DTOs, `SpeciesService`/`SpeciesServiceImpl`,
    `SpeciesController` (`GET /api/v1/species/{id}` public read, `GET /api/v1/species/mine` paginated).
  - Migration `016_create_species.sql`, registered in db.changelog-master.xml after 015.
    ⚠️ The task brief's SQL used `created_by`/`updated_by BIGINT`, but `AuditableEntity` stores
    those as `String` (every existing migration uses `VARCHAR(255)` for these columns) — corrected
    to `VARCHAR(255)` to avoid a Hibernate schema-validation failure at startup.
  - `findOrCreate(scientificName, commonName)`: looks up by scientificName, creates with
    status=ACTIVE on miss, then fires `SpeciesEnrichmentService.enrich(id)` via an
    `Optional<SpeciesEnrichmentService>` constructor dependency — no implementation exists yet
    (T6.4 will add one); `Optional.empty()` makes the call a safe no-op in the meantime, and the
    enrichment call itself is not blocking (no `@Async` needed on this service — that lives on
    T6.4's future implementation).
  - ⚠️ **Known gap, intentionally deferred:** `getUserSpecies(userId, pageable)` is currently a
    **stub** — it logs a WARN and returns `Page.empty(pageable)`. The task brief specified grouping
    the caller's ACTIVE `Plant` rows by `speciesId`, but `Plant.speciesId` does not exist yet
    (planned for **T6.3 / migration 017**, not yet built) — confirmed via grep, no `speciesId`
    anywhere in the codebase before this session. Flagged to the user via AskUserQuestion; chosen
    resolution was to stub this one method and keep migration boundaries exactly as ARCHITECT.md's
    Phase 6 plan laid them out, rather than pulling `plants.species_id` forward into this migration.
    **T6.3 must wire this up for real** — reuse `IdentificationRepository.findLatestPerPlant()` for
    `healthSummary`, same batch-fetch pattern as `PlantServiceImpl.enrichWithHealthAndWater()`;
    exclude plants with `speciesId IS NULL`. The TODO comment in `SpeciesServiceImpl.getUserSpecies()`
    has the same note.
  - New `SpeciesServiceTest` (5 tests, unit/mocked): findOrCreate hit/miss + enrichment-fired
    verification, getSpecies found/not-found, and a stub-behavior test for getUserSpecies
    (asserts empty page — full coverage of grouping/plantCount/healthSummary lands with T6.3).
    `Optional<SpeciesEnrichmentService>` isn't `@InjectMocks`-friendly, so the service is
    constructed manually in `@BeforeEach` (same documented pattern as other non-mockable-param
    services).
  - `mvn clean compile`, full unit suite (`-Dtest='!*IT'`), and `checkstyle:check` all pass;
    `spotless:apply` run (reformatted only, no logic changes).
  - **Next:** T6.2 (Treatment entity) and T6.3 (Plant FK columns — unblocks the getUserSpecies
    stub above) are next per the Phase 6 dependency table.

- T6.2 Treatment entity + migrations + endpoints ✅ (backend, branch `feature/PP-030-treatment-entity`,
  session 2026-06-19)
  - New `com.plantpal.treatment` package (deliberately separate from `com.plantpal.reminder`,
    despite depending on `TreatmentPlanService` there — see ARCHITECT.md's "Two Treatment
    concepts" disambiguation): `Treatment` entity (DRAFT|IN_PROGRESS|COMPLETED|DISMISSED,
    no `AuditableEntity` — same no-audit-columns exception as `Reminder`/`TreatmentPlan`, uses
    `@CreationTimestamp`/`@UpdateTimestamp`), `TreatmentRepository`, `TreatmentResponse`/
    `CreateTreatmentRequest` DTOs, `TreatmentService`/`TreatmentServiceImpl`, `TreatmentController`
    (`POST /api/v1/treatments`, `POST /{id}/craft-plan`, `GET /{id}`,
    `GET /api/v1/plants/{id}/active-treatment`, `PATCH /{id}/complete`).
  - Migration `018_create_treatments.sql` — registered **directly after 016** in
    db.changelog-master.xml (017/T6.3 doesn't exist yet, exactly as the task brief anticipated);
    left an XML comment explaining the gap so T6.3 inserts 017 above 018 instead of renumbering.
    Partial unique index `idx_treatments_active_per_disease ON (plant_id, disease_name) WHERE
    status IN ('DRAFT','IN_PROGRESS')` enforces "one active treatment per plant+disease" at the
    DB level, backed up by an application-level check in `createTreatment()`.
  - **Wraps, doesn't duplicate, `TreatmentPlan`** (the recommended design from the task brief):
    `craftPlan()` generates a TREATMENT-type `ActionPlanDto` via AI, then delegates reminder/step
    creation entirely to the existing `TreatmentPlanService.createFromActionPlan(plantId, userId,
    diseaseName, "PEST", actionPlan)` — `Treatment.treatmentPlanId` just stores the resulting
    plan's id. No reminder-creation logic duplicated.
  - AI reuse: `craftPlan()`'s action plan comes from the **existing**
    `DeepSeekClient.generateCureAdvice(species, diseaseName)` (already returns
    `{advice, actionPlan}` JSON in the TREATMENT shape) — parsed via a private holder class that
    mirrors `IdentificationServiceImpl`'s `CureAdviceJson` exactly, then run through the existing
    `ActionPlanValidator.normalize()`. Only `actionPlan` is used; `advice` is discarded since
    `createTreatment()` already generates a dedicated `diseaseDescription`.
  - Added **one new method** to `DeepSeekClient`: `generateDiseaseDescription(species,
    diseaseName)` (new `DISEASE_DESCRIPTION_SYSTEM_PROMPT`, plain-text response, not JSON) — fired
    fire-and-forget from `createTreatment()` via `CompletableFuture.runAsync(...,
    aiTaskExecutor)` (NOT Spring's `@Async`, to sidestep the self-invocation/proxy problem since
    this is a same-class call, not a call through a separate injected bean like T6.1's
    `SpeciesEnrichmentService`). Failures degrade to a null `diseaseDescription` (logged WARN),
    never block `createTreatment()`'s response. Rate-limited via a new per-user Bucket4j bucket
    (`TREATMENT_AI_RATE_LIMIT = 10/hour`, shared between this fire-and-forget call and `craftPlan()`).
  - ⚠️ **One deliberate deviation from the task brief's literal method signature:** the brief wrote
    `craftPlan(id, userId): TreatmentResponse` (synchronous), but `craftPlan()` makes a real AI call
    (5-15s) — CLAUDE.md's hard rule #5 ("Async AI calls... never block the HTTP thread") and every
    other AI-calling service method in this codebase (`IdentificationServiceImpl.getCureAdvice()`)
    use `@Async("aiTaskExecutor")` + `CompletableFuture<T>`. Followed the established codebase
    pattern instead of the brief's literal signature — `TreatmentController.craftPlan()` mirrors
    `IdentificationController.getCureAdvice()`'s exact `.get()`/`ExecutionException`-unwrapping shape.
  - ⚠️ **Known gap, carried over from T6.1, NOT re-solved here:** `plant.activeTreatmentId` does
    not exist yet (T6.3/migration 017). `craftPlan()` and `completeTreatment()` both have a
    `// TODO(T6.3)` at the exact point they'd set/clear it — left as the brief explicitly
    instructed ("if T6.3 hasn't landed yet, leave this line as a clearly marked TODO rather than
    guessing the column name"), so no AskUserQuestion was needed for this one (T6.1's gap already
    established the pattern and got the user's sign-off on the general approach).
  - New `TreatmentServiceTest` (7 tests, unit/mocked): createTreatment success + duplicate-active
    rejection + ownership check; craftPlan success (verifies the exact args passed to
    `TreatmentPlanService.createFromActionPlan`) + DRAFT-only rejection; completeTreatment success
    + IN_PROGRESS-only rejection. Constructed manually in `@BeforeEach` with `Runnable::run` as the
    executor (deterministic synchronous fire-and-forget in tests, no real thread pool needed) —
    this also means `createTreatment()`'s test sees 2 `save()` calls (DRAFT row + the
    disease-description update), not 1; asserted with `times(2)` + `getAllValues().get(0)` for the
    first save's content. Also discovered while writing the DRAFT-rejection test for `craftPlan()`:
    without a real Spring AOP proxy, `@Async` has no effect in a unit test, so the
    `ValidationException` propagates synchronously rather than via `ExecutionException` — asserted
    directly (`isInstanceOf`, no `.get()`/cause-chasing), same pattern already established by
    `IdentificationServiceImplTest`'s rate-limit test.
  - `mvn clean compile`, full unit suite (`-Dtest='!*IT'`), and `checkstyle:check` all pass;
    `spotless:apply` run (reformatted only, no logic changes). Did **not** spin up the full
    docker-compose stack (Postgres/Kafka/Redis/GITHUB_TOKEN) to manually exercise the brief's
    `POST /treatments` → `POST /{id}/craft-plan` → `GET /api/v1/treatment-plans/{treatmentPlanId}`
    end-to-end check — wiring is verified at the compile/unit-test level only; flagged to the user.
  - **Next:** T6.3 (Plant FK columns) is the natural next step — it unblocks both this task's two
    `activeTreatmentId` TODOs and T6.1's `getUserSpecies()` stub in one migration.

- T2.D3 Identification UX polish + navbar fix ✅ (frontend, session 2026-06-17)
  - `identification-page`: now shows the inline upload form only when the list is empty
    (`hasIdentifications === false`); once at least one identification exists, the form is replaced by a
    `mat-fab` ("add_a_photo") that opens `IdentificationUploadDialogComponent` (MatDialog) — same
    `add-fab` fixed-position pattern as `plant-list`/`reminder-list` (bottom 80px mobile / 24px desktop)
  - `IdentificationListComponent`: new `@Output() hasItemsChange` emitted from `fetchPage()`'s map step —
    drives the page's form-vs-FAB decision; `hasIdentifications` stays `null` (shows neither) until the
    first fetch resolves, avoiding a form/FAB flash
  - `components/identification-upload-dialog/`: NEW — thin MatDialog wrapper around `app-photo-upload`;
    closes immediately with the `AnalyzeEmitPayload` on submit (fire-and-forget, no internal HTTP call —
    the page's existing `onAnalyze()` handles the actual POST + snackbar + `trackNew()` after the dialog
    closes); no subscriptions inside the dialog itself, nothing to leak
  - `ModelSelectorComponent`: replaced the 4-option `mat-button-toggle-group` with a standalone compact
    `mat-select` pill (icon + label trigger, options keep their rate-limit `matTooltip`s) — this was also
    the root cause of the profile icon vanishing (the toggle group's natural width could exceed available
    toolbar space and squeeze the rightmost `account_circle` button out)
  - `SharedModule`: swapped `MatButtonToggleModule` → `MatSelectModule` (toggle group no longer used anywhere)
  - `app.component.scss`: added `flex-shrink: 0` to `.brand`, `.login-btn`, `.user-menu-btn` so the profile
    button can never be squeezed off-screen by toolbar content again, regardless of viewport width

- T2.D2 Identification list/detail redesign ✅ (frontend; backend endpoint pending)
  - Replaced the full-screen analyzing/pending/preview/error state machine on the upload page with a
    persistent list: upload form + `IdentificationListComponent` always visible together.
  - `identification.service.ts`: added `getUserIdentifications(page, size)` → GET `/api/v1/identifications`
    (paginated, current user, all plants) — **new backend endpoint required, not yet implemented**
  - `components/identification-list/`: NEW — fetches first page on init; rows show thumbnail/name/date/
    status chip (PENDING spinner / COMPLETED check / FAILED error); polls every 3s via `interval` +
    `switchMap` while any row is PENDING, stops once none are; `trackNew(id)` called by the parent page
    right after submit to optimistically prepend a placeholder row + refetch; clicking a non-PENDING row
    navigates to `/identify/:id`
  - `components/identification-preview-section/`: NEW — extracted the preview-card + annotation-list +
    disease-detail-panel trio (with region-selection state) out of identification-page so it can be reused
    by both the post-scan flow and the standalone detail page
  - `pages/identification-detail-page/`: NEW — route `/identify/:id`; fetches by id; PENDING → polls via
    `pollUntilComplete`; FAILED → error screen with "back to upload"; COMPLETED + `plantId` already set →
    redirects to `/plants/:plantId` (canonical view is the plant's "Last Scan" tab, avoids duplicating that
    UI); COMPLETED + `plantId` null → renders `identification-preview-section` (still unsaved, can save/edit/discard)
  - `identification-page.component.ts/html`: collapsed to just photo-upload + identification-list; submit
    flow shows a snackbar ("Identification started…") instead of taking over the screen; `submitting` flag
    disables the upload form's Identify button with an inline spinner during the POST
  - `photo-upload.component.ts/html`: added `@Input() submitting` — disables button + shows inline spinner
    text "Submitting…" instead of the old full-page "analyzing" screen
  - `identification-routing.module.ts`: added `{ path: ':id', component: IdentificationDetailPageComponent }`
  - Old T2.D states (`analyzing`/`pending`/`preview`/`error` on the upload page itself) removed entirely —
    superseded by the list-driven UX. `pollUntilComplete()` and `getById()` from T2.D are still used,
    just called from the list and detail page instead of the upload page.

- T2.D Frontend polling for async identification result ✅ (superseded by T2.D2 above — kept for history)
  - `identification.model.ts`: added `IdentificationPendingResponse { identificationId, status }`
  - `identification.service.ts`: `analyze()` now returns `Observable<ApiResponse<IdentificationPendingResponse>>`;
    added `getById(id)` (GET `/{id}`); added `pollUntilComplete(id)` — `interval(3000)` + `startWith(0)` +
    `switchMap` to `getById` + `takeWhile(status==='PENDING', inclusive)` + `filter` + throw on `FAILED` +
    `take(1)` + `timeout(32000)`
  - `identification-page.component.ts`: state machine extended `idle | analyzing | pending | preview | error`;
    `onAnalyze()` now sets `pending` + `pendingIdentificationId` then calls private `startPolling()`;
    polling subscription uses `takeUntil(this.destroy$)` so it stops automatically on navigate-away
  - `identification-page.component.html`: new `pending` block — spinner + "Analysing your plant…
    (usually 10–20 seconds)" + indeterminate `mat-progress-bar` (module already imported in IdentificationModule)
  - FAILED status throws inside `pollUntilComplete` (not just timeout) so the component doesn't render
    a preview card with empty AI fields — deviates slightly from the literal task pseudocode, which let
    FAILED fall through to `next()`

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
- `ModelSelectorComponent` in `shared/components/model-selector/`: standalone `mat-select` dropdown pill
  (4 options after T2.B; switched from mat-button-toggle-group in T2.D3 — see above), loading spinner,
  revert-on-error, snack-bar feedback, matTooltip rate-limit warnings on each option
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
- **T3.4 (accepted):** Single ActionPlanValidator.normalize() choke-point for all AI-sourced action
  plans (ROUTINE/TREATMENT) — never throws, degrades to null on anything malformed. Reason: same
  reliability problem as care-plan/annotation JSON parsing, now compounded by a richer nested shape
  (steps, diagram) — one normalization point is easier to harden than validating at every call site.
- **T3.4 (accepted):** Treatment steps modeled as one-time Reminder rows (recurring=false,
  treatmentPlanId FK), not a parallel entity hierarchy. Reason: reuse the existing due-date/complete/
  push-notification machinery instead of duplicating it for a second "kind" of schedulable item.
- **T3.4 (accepted):** Unified completion logic — ReminderService.applyCompletionToReminder() is now
  the only place "mark done" logic lives; both completeReminder() and CareLogService.logCare()
  delegate to it. Reason: found two independent reimplementations of the same reschedule logic while
  designing treatment-step completion; fixed instead of adding a third.
- **T3.5 (accepted):** Mermaid DSL (client-rendered) over raw AI-generated SVG for diagrams. Reason:
  Mermaid's renderer only ever emits valid SVG from valid-or-rejected DSL text; trusting raw
  AI-generated SVG markup directly would be an XSS-shaped risk for no real benefit.

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
010_add_user_preferences.sql          ✅ AddChooseAi — ai_model_preference VARCHAR(50) on users
011_add_image_dimensions.sql          ✅ T2.F — source_image_width INT, source_image_height INT on identifications
012_add_treatment_plans.sql           ✅ T3.4 — treatment_plans table; reminders gains recurring/treatment_plan_id/treatment_plan_title/step_order
013_add_reminder_instruction.sql      ✅ T3.4b — reminders.instruction TEXT (nullable)
014_add_step_detail.sql               ✅ T3.6 — reminders.step_detail TEXT, step_diagram_format VARCHAR(20), step_diagram_content TEXT (all nullable)
015_add_ai_model_used.sql             ✅ T3.9 — identifications.ai_model_used VARCHAR(50) (nullable)
                                       ⚠️ this entry was missing from this list until 2026-06-19 — the
                                       migration shipped correctly in T3.9, only this doc list lagged.
                                       Cross-check db.changelog-master.xml as the source of truth if
                                       this list ever looks stale again.
016_create_species.sql                ✅ T6.1 — new species table
017_alter_plants_add_species_fk.sql   🔲 T6.3 — plants.species_id/last_scan_id/active_treatment_id FK,
                                       drops plants.species (String) (Phase 6, planned) — NOTE:
                                       018 already shipped ahead of this one (see below); when 017
                                       lands it inserts ABOVE 018 in db.changelog-master.xml, in its
                                       correct numeric position — Liquibase applies by listed XML
                                       order, not filename, so this is safe.
018_create_treatments.sql             ✅ T6.2 — new treatments table; registered directly after 016
                                       since 017 (T6.3) didn't exist yet when this shipped
019_alter_identifications_add_plant_species_fk.sql 🔲 T6.3 — identifications.plant_id nullable
                                       (species-level scans), identifications.species_id FK (Phase 6, planned)

⚠️ No structural migration needed for T2.9a polygon switch — annotation_regions is already JSONB,
which stores any JSON shape. Switching from boundingBox to polygon is a pure code change.

⚠️ Migration 011 inserts AFTER 010. Verify db.changelog-master.xml order matches file numbering.

⚠️ Phase 6 migration numbers (016–019) deliberately skip the user's originally-requested
012–015 — those numbers are already used by T3.4/T3.4b/T3.6/T3.9. See the "Phase 6" section
above for the full renumbering rationale.

## Open Items (technical debt)
- **CRITICAL:** Rotate GITHUB_TOKEN (GitHub PAT) — was shared in chat sessions; regenerate at github.com/settings/tokens
  After T2.A, env var renames from DEEPSEEK_API_KEY → GITHUB_TOKEN; update backend/.env on all machines
- JaCoCo gate needs to be restored to 80% with proper exclusions
- Integration tests not running in CI (Testcontainers phase isolation issue)
- IdentificationControllerIT.java missing
- PlantNetClient + plantnet/ DTOs are dead code — remove at next cleanup sprint
- IdentificationResultComponent is dead code (orphaned — no route or template references it
  anymore, superseded by identification-preview-section in the T2.D2 redesign); safe to delete
- WebSocket (STOMP/SockJS): deferred to Phase 4. Will replace HTTP polling in both the
  identification result flow (T2.D) and the chat module (T4.x). Both share the same WS endpoint.
- Kafka consumer error DLQ: currently failed identifications set status=FAILED and log ERROR.
  A proper dead-letter topic should be added in Phase 3 before prod load.
- gpt-4o-mini annotation quality is visibly imprecise on disease contours (verified against a real
  photo — polygon was in the right vicinity but didn't trace the actual damaged area). Not a code
  bug — confirmed no EXIF/coordinate mismatch on the photo checked. If it keeps being unsatisfying,
  switch GITHUB_ANNOTATION_MODEL back to gpt-4o (no code change needed) and compare.
- CSS gotcha for future sessions: styles.scss's global `canvas { display: block; }` reset beats the
  browser's own `[hidden] { display: none; }` default (author origin always wins over user-agent
  origin, regardless of specificity). Any future component relying on `[hidden]` for a `<canvas>`,
  `<img>`, `<video>`, or `<svg>` needs its own `tag[hidden] { display: none !important; }` override
  — see photo-annotator.component.scss for the pattern.
- ✅ RESOLVED: AiTestController was already deleted in T4.1 (dead code, /ai-test page removed)
- ✅ RESOLVED: T2.8 frontend (one-click save UI) — preview-card's save form has been live all along

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
