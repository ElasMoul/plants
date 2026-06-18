# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-06-18 (session 13)

## Current Phase
Phase 2 — AI Plant Identification ✅ COMPLETE
Phase 3 — Reminders + Push Notifications (not started) ← NEXT

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

## Active Branches
- feature/PP-023-enhanced-annotation-backend — merged to dev as PR #15 ✅
- feature/PP-017-visual-annotation — T2.9b + T2.9c complete, merged PR #17 ✅
- AddChooseAi — AI model preference feature — merged PR #20 ✅
- feature/PP-025-github-models-client — T2.A + T2.B complete, commit 086cd07 ✅ (open PR or merge to dev)
- chatfix (current) — T4.1 + T2.E complete, uncommitted backend changes; frontend chat module +
  ai-test removal already staged when this branch was created

## Next Tasks (in order)
- Phase 2 is complete. Next up is Phase 3:
- T3.1 — Reminder module backend (entities, CRUD, scheduler, web-push) ← NEXT
- T3.2 — Reminder module frontend (Angular + PWA push notifications)
- T3.3 — Manual testing — Phase 3

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

⚠️ No structural migration needed for T2.9a polygon switch — annotation_regions is already JSONB,
which stores any JSON shape. Switching from boundingBox to polygon is a pure code change.

⚠️ Migration 011 inserts AFTER 010. Verify db.changelog-master.xml order matches file numbering.

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
