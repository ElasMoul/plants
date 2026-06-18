# Backend Agent — Restore Prompt
> Paste this as the first message in a new Claude Code conversation.
> Claude Code must have access to the backend/ directory.
> Update this file at the end of every session with anything learned.

You are the backend developer on PlantPal.

## Your Role
- Implement Java/Spring Boot features from task prompts
- Diagnose and fix backend issues
- Follow ALL conventions below without exception

## Stack
Java 21, Spring Boot 3.2, Spring Security 6, Spring Data JPA,
PostgreSQL 15, Redis 7, Liquibase, JJWT 0.12.5, MapStruct 1.5.5,
Lombok, Bucket4j 8.7.0, OllamaClient (RestClient),
DeepSeekClient (RestClient, HTTP/2, OpenAI-compatible, GitHub Models endpoint),
Testcontainers, JaCoCo 0.8.12, Checkstyle (google_checks.xml), Spotless 2.43.0,
springdoc-openapi 2.5.0, BouncyCastle 1.78.1 (for web-push ECDH),
OkHttp MockWebServer (unit-testing RestClient), testcontainers-redis 2.2.2

## Current Task — T3.4 Actionable care plans backend ✅ (branch: feature/PP-028-actionable-care-plans-2,
uncommitted as of end of session — run `git status` first thing in a new session)
ROUTINE reminders + multi-step TREATMENT plans, generated from AI care cards / cure advice. Highlights:
- Migration 012_add_treatment_plans.sql: new `treatment_plans` table; `reminders` gains `recurring`
  (default true), `treatment_plan_id` (FK CASCADE), `treatment_plan_title` (denormalized),
  `step_order`. Treatment steps are one-time `Reminder` rows, NOT a parallel entity.
- `CareType` expanded 4→10 values (mirrors CareCardType) — additive, no migration impact.
- New `TreatmentPlan`/`TreatmentPlanStatus` (ACTIVE|COMPLETED|ABANDONED) entity — same
  no-AuditableEntity pattern as `Reminder`.
- **Unified completion logic**: `ReminderService.applyCompletionToReminder(reminder, performedAt)`
  replaces what used to be two independent "log + reschedule" implementations in
  `ReminderServiceImpl.completeReminder()` and `CareLogServiceImpl.logCare()`. Recurring →
  reschedules; one-time → disables, and completes the parent TreatmentPlan if it was the last
  enabled step (`ReminderRepository.findByTreatmentPlanIdAndEnabledTrue` empty after disabling).
- New `ActionPlanValidator.normalize()` (identification/util/) — single choke-point every
  AI-sourced `ActionPlanDto` passes through; never throws, degrades to null on anything malformed.
  ROUTINE: frequencyDays 1-365 or reject. TREATMENT: non-empty steps, truncate to 10, clamp
  dueOffsetDays 0-180, **always re-numbers order 1..N from scratch** (ignores AI's order values
  entirely). Diagram kept only if format=="MERMAID" (case-insensitive) + non-blank + ≤2000 chars.
- `DeepSeekClient.generateCureAdvice()` now returns JSON (`{advice, actionPlan}`, response_format
  json_object) instead of plain text — `IdentificationServiceImpl.parseCureAdvice()` falls back to
  raw-string-as-advice on parse failure, which is exactly what made the pre-existing CureAdvice
  unit tests (mocking plain text) keep passing unmodified.
- New `TreatmentPlanService`/`Impl`/`Controller`: `POST /api/v1/treatment-plans`,
  `GET /api/v1/treatment-plans/{id}`.
- 39 new/updated unit tests (ActionPlanValidatorTest 21, TreatmentPlanServiceTest 10,
  ReminderServiceTest +4, CareLogServiceTest updated). Full suite: 132/132 passing, checkstyle clean.
See STATE.md "T3.4" entry for full notes. Next: T3.5 frontend (separate session — substantial
uncommitted frontend work already exists on this branch from another session but is NOT verified
against this backend contract yet); then T3.3 manual/device testing.

## Previous Task — T3.1 Reminder + Care Log module ✅ (branch: feature/PP-011-reminder-module)
Full CRUD + scheduler + web-push, all against tables that already existed since migration 004/005
(CareLog, PushSubscription entities just never existed in code until now). Highlights:
- ReminderService/Impl: createReminder, getUserReminders (bounded 200, batch-fetches plants to
  avoid N+1), completeReminder (writes CareLog + recalculates nextDueAt), deleteReminder (soft —
  enabled=false), calculateNextDueAt()
- CareLogService/Impl: logCare() ties back to a Reminder (not the plant directly) and recalculates
  its nextDueAt via ReminderService; getPlantCareLogs (paginated, ownership-checked)
- ReminderScheduler: @Scheduled daily 8am, groups due reminders by user, ONE push per user not one
  per reminder; Clock injected (same pattern as T2.10b's DashboardServiceImpl)
- 11 new unit tests; full suite 95/95 passing
NOTE: completeReminder()/logCare() rescheduling logic described above was superseded by
applyCompletionToReminder() in T3.4 above — see that entry, not this one, for current behaviour.

## Earlier Task — Phase 2 complete (branch: feature/PP-020-garden-dashboard, merged)
T2.F, T2.10a–d, T2.10e all done — see STATE.md for full notes. Highlights:
- New com.plantpal.dashboard module: DashboardController/Service/Impl, GET /api/v1/dashboard
  (healthSummary, overdueReminders, todayReminders, healthTrends). Deliberately not @Cacheable.
- PlantResponse now actually populates healthStatus + nextWaterDays (was declared, never set).
- New POST /api/v1/identifications/{id}/care-plan/cards — addCareCard(), appends a PEST-type
  CareCardDto built from a disease label + cure-advice text; idempotent on the card title.
- shared/util/ImageUtil.java: resizeAndConvertToJpeg() + readDimensions(), extracted so
  IdentificationServiceImpl can record sourceImageWidth/sourceImageHeight (migration 011).

## Earlier Task — T4.1 + T2.E ✅ (branch: chatfix, merged)
T4.1: new com.plantpal.chat module (ChatRequest/ChatResponse/ChatService/ChatController) wired to
OllamaClient.chat(String) with a garden-context-aware prompt; deleted dead AiTestController.
T2.E: Redis-backed photo storage with SHA-256 dedup on savePhoto(); FileStorageService.loadPhoto()
renamed to loadPhotoBytes() and made Redis-first/disk-fallback; new PhotoController serving
GET /api/v1/photos/{filename}. See STATE.md "T4.1" and "T2.E" entries for full implementation notes.

## Previous Task — T2.C Kafka async identification pipeline ✅ (merged to dev)
Replaced the blocking `.get()` in IdentificationController.analyze() with a Kafka-backed async
pipeline: POST /analyze persists PENDING + publishes IdentificationRequestedEvent, returns 202
immediately; IdentificationConsumer processes it off the HTTP thread; GET /{id} added for polling.
See STATE.md "T2.C Kafka async identification pipeline" entry for full implementation notes.

## Earlier Task — AddChooseAi (branch: AddChooseAi, merged)
Add user-level AI model preference stored in DB, exposed via REST, wired into identification pipeline.

### ⚠️ Migration number is 010, NOT 009
The feature spec says `009_add_user_preferences.sql` — **that number is taken** (`009_add_health_to_identifications.sql` already exists and is applied). Use **`010_add_user_preferences.sql`** and register it after 009 in `db.changelog-master.xml`.

### What to build
1. `user/entity/AiModelPreference.java` — enum: DEEPSEEK | PLANTNET | OLLAMA_LLAVA
2. `User` entity — add field: `@Enumerated(EnumType.STRING) @Column(name = "ai_model_preference", nullable = false) AiModelPreference aiModelPreference = AiModelPreference.DEEPSEEK`
3. Migration `010_add_user_preferences.sql`: `ALTER TABLE users ADD COLUMN ai_model_preference VARCHAR(50) DEFAULT 'DEEPSEEK' NOT NULL`
4. `user/dto/UserPreferencesRequest.java` — `@NotNull AiModelPreference aiModelPreference`
5. `user/dto/UserPreferencesResponse.java` — `AiModelPreference aiModelPreference`
6. `UserService` interface — add `getPreferences(Long userId)` and `updatePreferences(Long userId, UserPreferencesRequest)`
7. `UserServiceImpl` — implement both; `updatePreferences` loads user, sets field, saves, returns response DTO
8. `UserController` (existing) — add two endpoints using userId from SecurityContext:
   - `GET  /api/v1/users/me/preferences` → 200 `ApiResponse<UserPreferencesResponse>`
   - `PUT  /api/v1/users/me/preferences` → 200 `ApiResponse<UserPreferencesResponse>` (`@Valid` body)
9. `IdentificationServiceImpl.identify()` — Step 0: load `userRepository.findById(userId)`, switch on `aiModelPreference`:
   - `DEEPSEEK` → `deepSeekClient.identifyPlant(...)` (existing path)
   - `PLANTNET` → `plantNetClient.identify(...)` (PlantNetClient is still wired; was just unused)
   - `OLLAMA_LLAVA` → `ollamaClient.chat(PLANT_IDENTIFICATION_SYSTEM_PROMPT, "Identify this plant.")` ← text only; no image sent to Ollama

No rate limiting on preferences endpoints (plain DB operations, no AI spend).

---

## AI Provider Map (current — post T2.A split + T3.4)
| Client | Model | Purpose | Endpoint |
|---|---|---|---|
| GitHubModelsClient (identificationModel) | gpt-4o | Plant photo identification + health + care plan incl. actionPlan (single call) | GitHub Models |
| GitHubModelsClient (annotationModel) | gpt-4o-mini | Polygon annotation regions | GitHub Models |
| DeepSeekClient (model) | DeepSeek-R1 | Care plan text regeneration + cure advice (now `{advice, actionPlan}` JSON) | GitHub Models |
| DeepSeekAnnotationClient (@Primary) | injects GitHubModelsClient | Polygon annotation; 2-attempt retry on EOF, falls back to OllamaClient on 429 | GitHub Models |
| OllamaClient | llava-phi3 | (1) OLLAMA_LLAVA preference for identification (2) Annotation fallback on 429 | localhost:11434 |
| PlantNetAnnotationClient | — | Non-primary fallback; maps species results to full-image PLANT regions | plantnet.org |
| PlantNetClient | — | Used only by PlantNetAnnotationClient + PLANTNET preference | plantnet.org |

### GitHubModelsClient / DeepSeekClient split (T2.A) — key facts
- **GitHubModelsClient**: owns `identifyPlant()` + `analyzeRegions()` (vision). Two separate model
  configs: `${github.models.identification-model:gpt-4o}` / `${github.models.annotation-model:gpt-4o-mini}`.
  Holds `PLANT_IDENTIFICATION_SYSTEM_PROMPT` + `ANNOTATION_SYSTEM_PROMPT` (both include the T3.4
  `actionPlan` schema addition per care card now).
- **DeepSeekClient**: text-only — `generateCarePlan()` (CARE_PLAN_SYSTEM_PROMPT, also has the
  actionPlan schema addition, kept in sync with GitHubModelsClient's prompt) + `generateCureAdvice()`
  (CURE_ADVICE_SYSTEM_PROMPT — ✅ T3.4: now requests `{advice, actionPlan}` JSON, response_format
  json_object, NOT plain text anymore). Uses `${deepseek.model:DeepSeek-R1}`.
- Both clients: Auth `Authorization: Bearer <GITHUB_TOKEN>`; HTTP/2 via JDK HttpClient (NO forced
  HTTP_1_1 — Azure endpoint requires HTTP/2); read timeout 5 minutes.
- `DeepSeekClient.stripThinkTags(String raw)`: **package-private static**, lives on DeepSeekClient
  even though GitHubModelsClient calls it too — strips `<think>...</think>` (R1) AND markdown
  ` ```json...``` ` fences (gpt-4o sometimes ignores response_format). Used by GitHubModelsClient,
  DeepSeekClient, and OllamaClient (which also wraps JSON in fences).
- Debug log of full raw response before stripping in every vision/text method — log level DEBUG.

## Non-Negotiable Conventions
- Constructor injection only. Never @Autowired on fields.
- Member order: logger → static constants → final fields →
  non-final fields → constructor → public methods → private methods
- All entities extend AuditableEntity (EXCEPT Reminder, CareLog, PushSubscription, TreatmentPlan —
  these tables have no created_by/updated_by; Reminder/TreatmentPlan use Hibernate's
  @CreationTimestamp/@UpdateTimestamp instead)
- All controllers return ApiResponse<T> — never raw objects
- All exceptions extend PlantPalException → GlobalExceptionHandler
- Soft deletes only (status = ARCHIVED)
- All list endpoints accept Pageable
- No hardcoded secrets — all via ${ENV_VAR}
- Bucket4j rate limiting on all AI/external API endpoints
  - Use Bandwidth.builder().capacity(N).refillIntervally(N, Duration.ofHours(1)).build()
  - Bandwidth.simple() is DEPRECATED in 8.7.0 — do NOT use it
- Async AI calls via @Async("aiTaskExecutor") + CompletableFuture
- Store rawResponse (full external API JSON) always
- In unit tests: construct service manually in @BeforeEach when constructor has non-mockable params

## Module Structure & File Inventory

### shared/ — fully implemented
- dto/ApiResponse.java          — uniform wrapper; uses MDC correlationId
- dto/RestPage.java             — Jackson-serialisable Page wrapper
- audit/AuditableEntity.java    — base entity, Spring Data Auditing
- exception/PlantPalException.java
- exception/ResourceNotFoundException.java
- exception/UnauthorizedException.java
- exception/ValidationException.java
- exception/GlobalExceptionHandler.java
- config/SecurityConfig.java    — JWT filter, CORS, stateless
- config/AsyncConfig.java       — aiTaskExecutor (core=2, max=5, queue=100)
- config/JpaConfig.java
- config/CacheConfig.java       — Redis, implements CachingConfigurer
                                  ✅ T2.E: + byteRedisTemplate bean (RedisTemplate<String,byte[]>,
                                  StringRedisSerializer key / RedisSerializer.byteArray() value, NOT
                                  @Primary). Use RedisSerializer.byteArray() — ByteArrayRedisSerializer
                                  is package-private in spring-data-redis 3.2.5, won't compile.
- config/OpenApiConfig.java     — Swagger/springdoc
- config/StorageConfig.java     — static resource handler for /photos/** (separate from PhotoController)
- config/KafkaConfig.java       — ✅ T2.C: KafkaTemplate<String,Object> bean (ProducerFactory autoconfigured)
- controller/PhotoController.java — ✅ T2.E: GET /api/v1/photos/{filename} → raw bytes, Content-Type
                                  inferred from extension; calls fileStorageService.loadPhotoBytes()
- filter/CorrelationIdFilter.java — HIGHEST_PRECEDENCE, MDC + response header
- filter/JwtAuthFilter.java
- util/JwtUtil.java
- storage/FileStorageService.java (interface) — loadPhotoBytes(String photoUrl) → byte[]
                                  (renamed from loadPhoto in T2.E — consolidated dedup-aware Redis+disk
                                  logic into one method instead of adding a second near-duplicate)
- storage/LocalFileStorageService.java — ✅ T2.E: constructor now also takes
                                  RedisTemplate<String,byte[]> byteRedisTemplate + StringRedisTemplate
                                  (Spring Boot auto-configures the latter — no bean needed).
                                  savePhoto(): SHA-256 dedup via DigestUtils.sha256Hex (commons-codec,
                                  transitive) — checks "photo:hash:{hash}" before writing to disk;
                                  on hit returns the existing URL with zero disk I/O. On miss, saves to
                                  disk as before then writes "photo:{uuid}" (raw bytes) and
                                  "photo:hash:{hash}" (→ url), both 7-day TTL.
                                  loadPhotoBytes(): Redis "photo:{uuid}" first, disk fallback, throws
                                  ResourceNotFoundException if neither has it (auto-404 via
                                  GlobalExceptionHandler — no try/catch needed in PhotoController)

### user/ — fully implemented
- entity/User.java, entity/UserStatus.java
- dto/RegisterRequest.java, LoginRequest.java, AuthResponse.java, UserResponse.java
- mapper/UserMapper.java
- repository/UserRepository.java
- service/UserService.java (interface) + service/impl/UserServiceImpl.java
- controller/AuthController.java

### plant/ — fully implemented (T2.8 complete)
- entity/Plant.java, entity/PlantStatus.java
- dto/CreatePlantRequest.java, UpdatePlantRequest.java, PlantResponse.java
- dto/SaveIdentificationAsPlantRequest.java  — ✅ T2.8; identificationId (required), nickname, location
- mapper/PlantMapper.java
- repository/PlantRepository.java  — has existsByIdAndUserId, findByIdAndUserId,
                                      findByIdAndUserIdAndStatus
- service/PlantService.java (interface) + service/impl/PlantServiceImpl.java
  - PlantServiceImpl now takes 5 constructor params: PlantRepository, PlantMapper,
    IdentificationRepository, ReminderRepository, ObjectMapper
  - saveFromIdentification(): loads identification (verifies ownership), creates Plant with
    nickname fallback chain (request → commonName → scientificName → "My Plant"),
    links identification.plantId, creates reminders from carePlan JSON
- controller/PlantController.java — POST /api/v1/plants/from-identification → 201

### identification/ — fully implemented (T2.9 + T2.9a + T2.9d complete)
- entity/Identification.java       — care_plan JSONB (String), health_status VARCHAR(30), health_notes TEXT,
                                     annotation_regions JSONB (String, @JdbcTypeCode(SqlTypes.JSON))
- entity/IdentificationStatus.java (PENDING/COMPLETED/FAILED)
- dto/IdentificationResponse.java  — has CarePlanDto carePlan, healthStatus, healthNotes,
                                     List<AnnotationRegionDto> annotationRegions fields
- dto/CureAdviceRequest.java       — ✅ T2.9d: @NotBlank regionLabel, nullable species
- dto/CureAdviceResponse.java      — ✅ T2.9d + T3.4: String advice, ActionPlanDto actionPlan (new)
- dto/AddCareCardRequest.java      — ✅ T2.10e + T3.4: @NotBlank regionLabel, @NotBlank adviceText,
                                     ActionPlanDto actionPlan (new, optional — re-validated server-side
                                     via ActionPlanValidator.normalize() before attaching, never trusted as-is)
- dto/CareCardDto.java             — ✅ T2.6 + T3.4: +ActionPlanDto actionPlan (nullable)
- dto/CarePlanDto.java             — ✅ T2.6
- dto/ActionPlanDto.java           — ✅ T3.4: type ("ROUTINE"|"TREATMENT"), frequencyDays
                                     (ROUTINE only), List<TreatmentStepDto> steps (TREATMENT only),
                                     DiagramDto diagram (TREATMENT only, nullable)
- dto/TreatmentStepDto.java        — ✅ T3.4: order (int), instruction (String), dueOffsetDays (int)
- dto/DiagramDto.java              — ✅ T3.4: format (only "MERMAID" supported), content (mermaid DSL text)
- dto/AnnotationRegionDto.java     — ✅ T2.9a: label, type (PLANT/DISEASE/HEALTHY_AREA),
                                     confidence (HIGH/MEDIUM/LOW),
                                     List<PolygonPointDto> polygon (nullable — primary shape),
                                     BoundingBoxDto boundingBox (nullable — legacy fallback for old DB records)
- dto/PolygonPointDto.java         — ✅ T2.9a: xPct, yPct (int, 0-100)
                                     @JsonProperty("xPct")/@JsonProperty("yPct") — same Lombok decapitalize fix
- dto/BoundingBoxDto.java          — ✅ T2.9a fix: xPct, yPct, widthPct, heightPct (all int, 0-100%)
                                     @JsonProperty("xPct")/@JsonProperty("yPct") — Lombok getXPct()
                                     → Introspector.decapitalize("XPct") = "XPct"; annotation fixes key to "xPct"
- dto/DeepSeekPlantResult.java     — ✅ internal DTO for combined vision response:
                                     species, commonName, confidence, healthStatus, healthNotes, CarePlanDto
- dto/IdentifyRequest.java
- dto/IdentificationPendingResponse.java — ✅ T2.C: identificationId, status — returned by POST /analyze (202)
- dto/plantnet/ (PlantNetResponse, PlantNetResult, PlantNetSpecies, PlantNetTaxon) — kept but unused
- event/IdentificationRequestedEvent.java — ✅ T2.C: identificationId, userId, photoUrl,
                                     aiModelPreference (String), organs, requestedAt — published to
                                     "identification.requested"
- event/IdentificationCompletedEvent.java — ✅ T2.C: identificationId, status (String), completedAt —
                                     published to "identification.completed" (COMPLETED or FAILED)
- config/KafkaTopicConfig.java     — ✅ T2.C: NewTopic beans for both topics (3 partitions/1 replica);
                                     IDENTIFICATION_REQUESTED_TOPIC / IDENTIFICATION_COMPLETED_TOPIC constants
- consumer/IdentificationConsumer.java — ✅ T2.C: @KafkaListener(topics="identification.requested",
                                     groupId="plantpal-identification") → delegates to
                                     identificationService.processIdentification(event)
- mapper/IdentificationMapper.java — ignores topResults, carePlan, AND annotationRegions (all set manually in service)
- repository/IdentificationRepository.java — findByPlantIdOrderByCreatedAtDesc(plantId, pageable)
- util/ActionPlanValidator.java    — ✅ T3.4: static normalize(ActionPlanDto) — never throws, the
                                     single choke-point every AI-sourced action plan passes through.
                                     See "Current Task" section above for the exact clamp/reject rules.
- service/IdentificationService.java (interface) + service/impl/IdentificationServiceImpl.java
  - Constructor: 14 params — deepSeekClient, visionAnnotationClient, identificationRepository,
    identificationMapper, plantRepository, reminderRepository, fileStorageService, objectMapper,
    gitHubModelsClient, userRepository, plantNetClient, ollamaClient, kafkaTemplate, cacheManager
  - submitIdentification() (sync, fast): validate → savePhoto → persist PENDING → rate-limit check →
    loadUserPreference → publish IdentificationRequestedEvent → return IdentificationPendingResponse
  - processIdentification(event) (@Async("aiTaskExecutor"), called by IdentificationConsumer):
    loads entity by id → FileStorageService.loadPhoto() re-reads bytes from disk (event only carries
    photoUrl, not raw bytes) → PARALLEL(runIdentification(preference,...), visionAnnotationClient.analyzeRegions) →
    parseIdentificationResult() → persist COMPLETED + annotationRegions → reminders →
    publishCompletedEvent(COMPLETED). Catches all exceptions → markFailed() + publishCompletedEvent(FAILED);
    never propagates (it's the @Async Kafka listener's job method, not an HTTP-facing one).
  - getIdentification(id, userId): ownership-checked single-entity fetch for the GET /{id} poll endpoint
  - runIdentification(preference, imageBytes, mediaType, organs) — NO LONGER takes List<MultipartFile>;
    PLANTNET branch wraps imageBytes in a private ByteArrayMultipartFile adapter (same pattern as
    PlantNetAnnotationClient) since the Kafka consumer only has raw bytes, not the original upload.
    Switch on AiModelPreference: PLANTNET → plantNetClient.identify(); OLLAMA_LLAVA → ollamaClient.identifyPlant()
    with GitHubModels fallback on PlantPalException; DEEPSEEK/GITHUB_GPT4O/default → gitHubModelsClient.identifyPlant()
  - Parallel vision: CompletableFuture.supplyAsync() for both futures; identificationFuture.join()
    unwraps CompletionException; annotationFuture silently degrades to empty regions on failure
  - parseAnnotationRegions(String json): JsonNode API (objectMapper.readTree + convertValue with
    constructCollectionType) — NOT a private record (Jackson cannot access private nested records).
  - confidenceToScore(): "HIGH"→0.9, "MEDIUM"→0.6, default→0.3
  - fallbackCarePlan(): single WATERING card, 7-day frequency
  - getCureAdvice(id, request, userId): @Async, ownership check, cureAdviceBuckets (10/hour),
    calls deepSeekClient.generateCureAdvice() → ✅ T3.4: raw response is now JSON
    (`{advice, actionPlan}`), parsed via parseCureAdvice() into an internal CureAdviceJson holder
    (private static class — same Lombok-getter/setter style as DeepSeekPlantResult, NOT a record).
    On JsonProcessingException, falls back to advice=rawString, actionPlan=null (never lets a
    malformed response fail the call). actionPlan run through ActionPlanValidator.normalize().
    Throws ResourceNotFoundException if not owned, PlantPalException(429) if rate-limited,
    PlantPalException(503) if DeepSeek fails.
  - CURE_ADVICE_RATE_LIMIT = 10; cureAdviceBuckets ConcurrentHashMap<Long, Bucket>
  - addCareCard(id, req, userId): ✅ T2.10e — NOT @Async (no AI call, pure DB read+write), no rate
    limit. Ownership-checked like getCureAdvice. parseCarePlan() → defensive-copy careCards into a
    mutable ArrayList (fallbackCarePlan() returns List.of(...), which is immutable) → skip if a card
    with that exact title already exists → else append a PEST-type CareCardDto (icon "healing",
    urgency HIGH) built from req.regionLabel/adviceText + ✅ T3.4:
    ActionPlanValidator.normalize(req.getActionPlan()) → serializeToJson() → save → return the
    updated CarePlanDto
- client/VisionAnnotationClient.java  — ✅ T2.9: interface; analyzeRegions(byte[], String) → JSON String
- client/DeepSeekAnnotationClient.java— ✅ T2.9a + updated (AddChooseAi session):
                                         @Primary implementation; injects BOTH DeepSeekClient and OllamaClient.
                                         2-attempt retry on EOF (Azure HTTP/2 GOAWAY on parallel connections).
                                         429 detection: catches RestClientResponseException with status 429 → calls
                                         tryOllamaFallback() immediately (no retry). Ollama success logged at INFO.
                                         Returns {"regions":[]} only if BOTH DeepSeek (non-429 after retries)
                                         and Ollama both fail.
- client/PlantNetAnnotationClient.java— ✅ T2.9: non-primary implementation; calls PlantNetClient.identify(),
                                         maps top results to full-image PLANT regions (no real bounding boxes);
                                         inner ByteArrayMultipartFile adapts byte[] for PlantNetClient
- client/PlantNetClient.java       — HTTP/1.1 forced (ALPN fix). NOT called in main flow; used by PlantNetAnnotationClient.
- client/OllamaClient.java         — local Ollama llava-phi3 (vision). Active in two paths:
                                     (1) OLLAMA_LLAVA preference → identifyPlant() as primary identification
                                     (2) DeepSeekAnnotationClient 429 fallback → analyzeRegions()
                                     Key implementation details:
                                     • identifyPlant() + analyzeRegions(): use /api/generate with images[] at
                                       TOP LEVEL (not nested in /api/chat messages — llava-phi3 requires this)
                                     • resizeAndConvertToJpeg(byte[]): private method, caps at 1024px, converts
                                       to JPEG via BufferedImage+Graphics2D. Applied before base64 encoding in BOTH
                                       vision methods. llava-phi3 returns 400 on high-res photos without this.
                                       Gracefully returns original bytes if ImageIO cannot decode the image.
                                     • DeepSeekClient.stripThinkTags() called on all responses — Ollama also wraps
                                       JSON in ```json...``` fences even when not asked to.
                                     • IdentificationServiceImpl: if ollamaClient.identifyPlant() throws
                                       PlantPalException, falls back to deepSeekClient.identifyPlant() with WARN log.
- client/DeepSeekClient.java       — GitHub Models; HTTP/2; 5-min timeout; gpt-4o (vision)
                                     Four methods: generateCarePlan(), identifyPlant(), analyzeRegions(),
                                     generateCureAdvice(species, regionLabel)
                                     ANNOTATION_SYSTEM_PROMPT: ✅ T2.9a updated to polygon schema
                                     (8–16 clockwise points, min 4, integers 0-100)
                                     CURE_ADVICE_SYSTEM_PROMPT: plain text response (no json_object format);
                                     uses text model (DeepSeek-R1); stripThinkTags() applied
- controller/IdentificationController.java — POST /{id}/cure-advice → 202 Accepted (T2.9d)
                                             Unwraps ExecutionException for PlantPalException +
                                             ResourceNotFoundException from async getCureAdvice()
                                             ✅ T2.C: POST /analyze now calls submitIdentification(),
                                             returns 202 + IdentificationPendingResponse immediately
                                             (no more blocking .get() on the full AI pipeline).
                                             Added GET /{id} → IdentificationResponse for polling.

### reminder/ — fully implemented (T3.1 CRUD/scheduler/push + T3.4 treatment plans complete)
- entity/CareType.java   — ✅ T3.4: 10 values (WATERING, LIGHT, HUMIDITY, TEMPERATURE, FERTILIZING,
                            REPOTTING, PRUNING, PEST, SEASONAL, BEGINNER_TIP) — was 4, expanded to
                            mirror CareCardType; additive, no migration impact on existing rows
- entity/Reminder.java   — does NOT extend AuditableEntity (table has no created_by/updated_by);
                            uses @CreationTimestamp/@UpdateTimestamp from Hibernate instead.
                            ✅ T3.4 additions: boolean recurring (@Builder.Default true), Long
                            treatmentPlanId (nullable), String treatmentPlanTitle (nullable,
                            denormalized — avoids a join on every reminder-list fetch),
                            Integer stepOrder (nullable)
- entity/CareLog.java    — ✅ T3.1; maps onto the care_logs table (existed since migration 004,
                            entity just never existed in code before T3.1). NO createdAt field —
                            the table truly has no such column, only performed_at (learned the hard
                            way: an earlier draft added @CreationTimestamp created_at and broke
                            schema-validation on startup — do NOT re-add it)
- entity/PushSubscription.java — ✅ T3.1; maps onto push_subscriptions table (migration 005)
- entity/TreatmentPlan.java — ✅ T3.4: id, plantId, userId, title, sourceCareCardType (String,
                            nullable), diagramFormat, diagramContent, status (TreatmentPlanStatus),
                            createdAt/updatedAt via @CreationTimestamp/@UpdateTimestamp — same
                            no-AuditableEntity pattern as Reminder
- entity/TreatmentPlanStatus.java — ✅ T3.4: ACTIVE | COMPLETED | ABANDONED
- repository/ReminderRepository.java — findByUserIdAndEnabledTrue(userId) [List, used by dashboard]
                            + findByUserIdAndEnabledTrue(userId, Pageable) [Page, used by
                            getUserReminders], findByIdAndUserId, findAllDue(Instant) [scheduler],
                            findNearestWateringPerPlant(plantIds) [dashboard], ✅ T3.4:
                            findByTreatmentPlanIdAndEnabledTrue(planId) [completion check — "any
                            steps left?"], findByTreatmentPlanIdOrderByStepOrder(planId)
                            [detail view — ALL steps including completed/disabled ones]
- repository/CareLogRepository.java — ✅ T3.1; findByPlantIdOrderByPerformedAtDesc(plantId, pageable)
- repository/PushSubscriptionRepository.java — ✅ T3.1; findByUserIdAndEnabledTrue(userId)
- repository/TreatmentPlanRepository.java — ✅ T3.4: findByIdAndUserId, findByPlantIdAndUserId
- service/ReminderService.java (interface) + service/impl/ReminderServiceImpl.java
  - Constructor: 4 params — reminderRepository, plantRepository, careLogRepository,
    treatmentPlanRepository (the last one added in T3.4)
  - createReminder, getUserReminders (bounded 200, PageRequest.of(0,200,Sort.by("nextDueAt")),
    batch-fetches plants via findAllById to avoid N+1), deleteReminder (soft — enabled=false),
    calculateNextDueAt(lastDone, frequencyDays) = lastDone.plus(frequencyDays, DAYS)
  - completeReminder(id, userId): writes a CareLog, then ✅ T3.4: delegates the reschedule-or-disable
    decision to applyCompletionToReminder() (see below) instead of inlining it
  - ✅ T3.4 applyCompletionToReminder(reminder, performedAt) — the unified completion handler used by
    BOTH ReminderServiceImpl.completeReminder() and CareLogServiceImpl.logCare(): if
    reminder.isRecurring() → nextDueAt = calculateNextDueAt(performedAt, frequencyDays), save; else
    → enabled=false, save, then if treatmentPlanId != null check
    findByTreatmentPlanIdAndEnabledTrue(planId) — if now empty, load the TreatmentPlan and flip it
    to COMPLETED. Does NOT persist the CareLog itself (caller's job — notes differ per call site).
- service/CareLogService.java (interface) + service/impl/CareLogServiceImpl.java
  - logCare(MarkCareDoneRequest, userId): looks up the Reminder (not the plant directly), writes a
    CareLog with the reminder's careType + notes, then ✅ T3.4: calls
    reminderService.applyCompletionToReminder(reminder, performedAt) — does NOT call
    reminderRepository.save() or reminderService.calculateNextDueAt() itself anymore (that
    duplicated logic was the bug found while designing T3.4, now eliminated)
  - getPlantCareLogs(plantId, userId, pageable) — paginated, ownership-checked through the Plant
- service/WebPushService.java (interface) + service/impl/WebPushServiceImpl.java — ✅ T3.1:
  nl.martijndwars:web-push, VAPID keys from @Value (app.web-push.public-key/private-key/subject —
  config + .env.example already existed from T0 scaffolding)
- service/TreatmentPlanService.java (interface) + service/impl/TreatmentPlanServiceImpl.java — ✅ T3.4
  - createFromActionPlan(plantId, userId, title, sourceCareCardType, actionPlan): rejects
    non-TREATMENT or empty-steps plans (ValidationException), ownership-checks the plant via
    plantRepository.findByIdAndUserId, parses sourceCareCardType into a CareType
    (ValidationException if null/blank/unrecognised — CareType.valueOf() wrapped in try/catch),
    creates the TreatmentPlan row, then one Reminder per step (recurring=false, treatmentPlanId,
    treatmentPlanTitle=title, stepOrder=step.order, nextDueAt=now+dueOffsetDays, frequencyDays=0
    [unused when recurring=false])
  - getTreatmentPlan(id, userId): ownership-checked; returns ALL steps via
    findByTreatmentPlanIdOrderByStepOrder (deliberately not the enabled-only query)
- scheduler/ReminderScheduler.java — ✅ T3.1: @Scheduled(cron="0 0 8 * * *"),
  reminderRepository.findAllDue(Instant.now(clock)) grouped by userId, sends ONE push per user
  ("You have N plants to care for today") not one per reminder; Clock injected via constructor
  (same pattern as T2.10b's DashboardServiceImpl) for testability
- controller/ReminderController.java — GET/POST /api/v1/reminders, DELETE /{id}
- controller/CareLogController.java — POST /api/v1/care/done, GET /api/v1/care/plant/{plantId}
- controller/NotificationController.java — POST /api/v1/notifications/subscribe
- controller/TreatmentPlanController.java — ✅ T3.4: POST /api/v1/treatment-plans (201),
  GET /api/v1/treatment-plans/{id}. Both auto-protected by the existing anyRequest() security rule.

### chat/ — ✅ T4.1: basic single-turn chat wired to Ollama
- dto/ChatRequest.java  — @NotBlank message
- dto/ChatResponse.java — reply (no @Setter — mirrors CureAdviceResponse style)
- service/ChatService.java (interface) + service/impl/ChatServiceImpl.java
  - chat(request, userId): rate-limit check (chatBuckets, 30/hour, same Bucket4j pattern as
    IdentificationServiceImpl) → buildGardenContext(userId) → formats CLAUDE.md's chat system prompt
    (SYSTEM_PROMPT_TEMPLATE.formatted(gardenContext)) + "\n\nUser: " + message into ONE string →
    ollamaClient.chat(prompt) (single-arg — no separate system-message param on OllamaClient.chat())
  - buildGardenContext(): plantRepository.findAllByUserIdAndStatus(userId, ACTIVE, PageRequest.of(0,50)),
    "- " + nickname + " (" + commonName/species/"unknown species" + ")" per line, joined with \n;
    "No plants in the garden yet." if empty
- controller/ChatController.java — POST /api/v1/chat (bare path) → ApiResponse<ChatResponse>;
  userId via SecurityContextHolder, same pattern as IdentificationController.getCurrentUserId()

### dashboard/ — ✅ T2.10b: read-only aggregation, no new tables
- dto/DashboardResponse.java   — healthSummary, overdueReminders, todayReminders, healthTrends
- dto/HealthSummaryDto.java    — totalPlants, healthyCount, issuesCount, unknownCount
- dto/ReminderSummaryDto.java  — reminderId, plantId, plantNickname, plantPhotoUrl, careType,
                                  nextDueAt, daysOverdue (0 for today's items)
- dto/PlantHealthTrendDto.java — plantId, plantNickname, trend (IMPROVING/WORSENING/STABLE,
                                  computed from the 2 most recent identifications per plant)
- service/DashboardService.java (interface) + service/impl/DashboardServiceImpl.java
  - getDashboard(userId): plants bounded at PageRequest.of(0, 200) (not unpaged — personal-garden
    app, 200 is a generous cap); reuses IdentificationRepository.findLatestPerPlant() (added in
    T2.10a) for the health summary; ReminderRepository.findByUserIdAndEnabledTrue() (new) partitioned
    by nextDueAt vs start-of-today/start-of-tomorrow using an injected Clock (shared/config/
    ClockConfig.java — Clock.systemDefaultZone() bean, for testability)
  - Deliberately NOT @Cacheable — nothing evicts it yet; a stale dashboard would be misleading
- controller/DashboardController.java — GET /api/v1/dashboard → ApiResponse<DashboardResponse>

## DB Migrations (in order)
001_create_users.sql
002_create_plants.sql
003_create_identifications.sql
004_create_reminders_and_care_logs.sql
005_create_push_subscriptions.sql
006_alter_identifications.sql      ← raw_response TEXT not JSONB
007_add_annotation_regions.sql     ← ✅ T2.9 — adds annotation_regions JSONB to identifications
008_add_care_plan.sql              ← ✅ T2.6 — adds care_plan JSONB to identifications
009_add_health_to_identifications.sql ← ✅ adds health_status VARCHAR(30), health_notes TEXT
010_add_user_preferences.sql       ← ✅ AddChooseAi — users.ai_model_preference VARCHAR(50)
011_add_image_dimensions.sql       ← ✅ T2.F — identifications.source_image_width/height INT
012_add_treatment_plans.sql        ← ✅ T3.4 — new treatment_plans table; reminders gains
                                      recurring/treatment_plan_id/treatment_plan_title/step_order

Current master XML order: 001→012 inclusive, strictly sequential, never reordered.

## Test Inventory (full suite: 132/132 passing as of T3.4, checkstyle clean)
unit/UserServiceTest.java
unit/PlantServiceTest.java                ← +7 SaveFromIdentification tests (nickname fallbacks,
                                             ownership check, reminder creation); @Mock
                                             IdentificationRepository, ReminderRepository, @Spy
                                             ObjectMapper = new ObjectMapper()
unit/IdentificationServiceImplTest.java   ← nested classes: Identify, CarePlanParsing,
                                             AnnotationRegions, ReminderCreation, Kafka,
                                             GetUserIdentifications, CureAdvice, AddCareCard.
                                             Constructs IdentificationServiceImpl manually
                                             (14-param constructor — see DashboardServiceTest for the
                                             sibling pattern using an injected fixed Clock instead of
                                             Instant.now()). CureAdvice tests mock
                                             deepSeekClient.generateCureAdvice() returning PLAIN TEXT
                                             — this is intentional: plain text is invalid JSON, so it
                                             naturally exercises parseCureAdvice()'s fallback path
                                             (advice=rawString, actionPlan=null) without needing
                                             updates after the T3.4 JSON-response change.
unit/ActionPlanValidatorTest.java         ← ✅ T3.4, 21 tests — the highest-value test in that task;
                                             every clamp/reject/boundary case for normalize()
unit/PlantNetClientTest.java
unit/OllamaClientTest.java
reminder/unit/ReminderServiceTest.java    ← nested: CreateReminder, GetUserReminders,
                                             CompleteReminder, DeleteReminder, CalculateNextDueAt,
                                             ✅ T3.4 ApplyCompletionToReminder (4: recurring
                                             reschedules, one-time disables, last-step completes the
                                             plan, non-last-step leaves it ACTIVE)
reminder/unit/CareLogServiceTest.java     ← ✅ T3.4 updated: logCare() test now verifies delegation
                                             to reminderService.applyCompletionToReminder() and
                                             explicitly asserts reminderRepository.save()/
                                             calculateNextDueAt() are NOT called directly (proves no
                                             duplicate rescheduling logic remains in this class)
reminder/unit/TreatmentPlanServiceTest.java ← ✅ T3.4, 10 tests — step count/dueOffsetDays math,
                                             ROUTINE-plan rejection, ownership checks, diagram
                                             persistence, sourceCareCardType validation
dashboard/unit/DashboardServiceTest.java
chat/unit/ChatServiceImplTest.java
shared/unit/LocalFileStorageServiceTest.java
integration/AuthControllerIT.java
integration/PlantControllerIT.java
AbstractIntegrationTest.java    ← Testcontainers base (PostgreSQL + Redis)
testdata/PlantTestDataBuilder.java
testdata/UserTestDataBuilder.java
MISSING: IdentificationControllerIT.java, TreatmentPlanControllerIT.java


## Bug Fixes Applied
- IdentificationController.analyze(): organs changed @RequestPart → @RequestParam.
  @RequestPart requires application/json or text/plain; multipart string fields arrive as
  application/octet-stream which has no converter. @RequestParam handles them correctly.

- RestPage Redis 500: @JsonIgnoreProperties(ignoreUnknown=true) only ignores UNKNOWN fields.
  "pageable"/"sort" are KNOWN (inherited from PageImpl), so Jackson tried to reconstruct
  Sort with empty orders → IllegalArgumentException. Fixed by explicitly listing
  {"pageable","sort","last","first","empty","numberOfElements"} in @JsonIgnoreProperties.
  CacheConfig now implements CachingConfigurer + overrides errorHandler() with a logging
  handler so future Redis failures degrade to WARN + cache miss instead of 500.
  ACTION REQUIRED after deploying: docker exec -it plantpal-redis redis-cli FLUSHDB

- CORS wildcard: setAllowedOrigins("*") + allowCredentials=true rejected by Spring.
  Fixed by using setAllowedOriginPatterns("*").

## Known Issues / Open Items
- JaCoCo gate is at 10% (not 80%) — restore once integration tests run in CI
- ✅ RESOLVED (T4.1): AiTestController deleted entirely (was the "not @Profile(dev) guarded" risk —
  no longer exists, don't re-add it)
- ✅ RESOLVED (T2.C): IdentificationController no longer blocks the HTTP thread on the full AI
  pipeline — POST /analyze persists PENDING + publishes to Kafka and returns 202 immediately;
  GET /{id} polls for the result once IdentificationConsumer finishes processIdentification().
- IdentificationControllerIT missing; TreatmentPlanControllerIT also missing (T3.4, no integration
  test written — only unit tests against mocked repositories)
- Branch protection on main + dev configured but integration tests not running in CI
- Spotless (Google Java Format) flags CRLF line endings on new files written by Claude Code
  on Windows. Fix with: cd backend && mvn spotless:apply
- PlantNetClient is now called by PlantNetAnnotationClient (T2.9) — no longer completely dead code.
  However, PlantNetAnnotationClient is NOT @Primary, so DeepSeekAnnotationClient is used by default.
  PlantNet annotation path is a non-primary fallback; clean up only if vision annotation is fully removed.
- plantnet/ DTOs (PlantNetResponse, PlantNetResult, etc.) still in use by PlantNetAnnotationClient.
- OllamaClient (llava-phi3) is active — annotation fallback on 429 AND OLLAMA_LLAVA preference path.
- GITHUB_TOKEN in .env is a GitHub PAT — keep rotating if accidentally shared in chat.
  GitHub Models rate limits: 50 requests/day for gpt-4o (vision). 429 on annotation → Ollama fallback.
  429 on identification → PlantPalException 429 bubbles to user (no automatic fallback at that layer).
- T3.4 backend work is uncommitted as of end of this session on feature/PP-028-actionable-care-plans-2
  — commit it before starting anything else, or a `git stash`/branch switch will lose it.
- T3.5 frontend has substantial uncommitted work on the SAME branch from a separate session
  (set-reminder-dialog, treatment-plan.model.ts/.service.ts, mermaid-diagram component, modified
  care-card/disease-detail-panel) that has NOT been verified against the now-complete backend
  contract — don't assume it's done or correct without checking `ng build`/`ng lint` and the actual
  request/response shapes against ReminderResponse/TreatmentPlanResponse as implemented here.

## Key Files
backend/src/main/java/com/plantpal/shared/dto/ApiResponse.java
backend/src/main/java/com/plantpal/shared/exception/GlobalExceptionHandler.java
backend/src/main/java/com/plantpal/shared/config/SecurityConfig.java
backend/src/main/java/com/plantpal/identification/client/PlantNetClient.java
backend/src/main/java/com/plantpal/identification/client/DeepSeekClient.java
backend/src/main/java/com/plantpal/identification/client/GitHubModelsClient.java
backend/src/main/java/com/plantpal/identification/service/impl/IdentificationServiceImpl.java
backend/src/main/java/com/plantpal/identification/util/ActionPlanValidator.java
backend/src/main/java/com/plantpal/reminder/service/impl/ReminderServiceImpl.java
backend/src/main/java/com/plantpal/reminder/service/impl/TreatmentPlanServiceImpl.java
backend/src/main/resources/db/changelog/db.changelog-master.xml
backend/src/main/resources/application-dev.yml
backend/.env.example
