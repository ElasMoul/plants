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

## Current Task — AddChooseAi (branch: AddChooseAi)
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

## AI Provider Map (current — branch: AddChooseAi)
| Client | Model | Purpose | Endpoint |
|---|---|---|---|
| DeepSeekClient (visionModel) | gpt-4o | Plant photo identification + health + care plan (single call) | GitHub Models (models.inference.ai.azure.com) |
| DeepSeekClient (model) | DeepSeek-R1 | Care plan text regeneration + cure advice | GitHub Models (models.inference.ai.azure.com) |
| DeepSeekAnnotationClient (@Primary) | gpt-4o via DeepSeekClient.analyzeRegions() | Polygon annotation regions; falls back to OllamaClient on 429 | GitHub Models |
| OllamaClient | llava-phi3 | (1) OLLAMA_LLAVA preference for identification (2) Annotation fallback on 429 | localhost:11434 |
| PlantNetAnnotationClient | — | Non-primary fallback; maps species results to full-image PLANT regions | plantnet.org |
| PlantNetClient | — | Dead code in main flow; used only by PlantNetAnnotationClient | plantnet.org |

### DeepSeekClient — key facts
- Single client handles both vision (gpt-4o) and text (DeepSeek-R1) via two config properties:
  - `${deepseek.model}` → text model for generateCarePlan(), generateCureAdvice()
  - `${deepseek.vision-model}` → vision model for identifyPlant(), analyzeRegions()
- Auth: `Authorization: Bearer <DEEPSEEK_API_KEY>` (GitHub PAT)
- HTTP/2 via JDK HttpClient (NO forced HTTP_1_1 — Azure endpoint requires HTTP/2)
- Read timeout: 5 minutes (gpt-4o vision can be slow)
- `stripThinkTags(String raw)`: **package-private static** — strips `<think>...</think>` (R1) AND
  markdown ` ```json...``` ` fences (gpt-4o sometimes ignores response_format). Used by both
  DeepSeekClient methods AND OllamaClient (which also wraps JSON in fences).
- Debug log of full raw response before stripping in ALL four methods (identifyPlant, analyzeRegions,
  generateCarePlan, generateCureAdvice) — log level DEBUG
- response_format: json_object on JSON-returning calls; generateCureAdvice() uses plain text (no format)

## Non-Negotiable Conventions
- Constructor injection only. Never @Autowired on fields.
- Member order: logger → static constants → final fields →
  non-final fields → constructor → public methods → private methods
- All entities extend AuditableEntity (EXCEPT Reminder — its DB table has no created_by/updated_by)
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
- config/OpenApiConfig.java     — Swagger/springdoc
- config/StorageConfig.java
- filter/CorrelationIdFilter.java — HIGHEST_PRECEDENCE, MDC + response header
- filter/JwtAuthFilter.java
- util/JwtUtil.java
- storage/FileStorageService.java (interface)
- storage/LocalFileStorageService.java

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
- dto/CureAdviceResponse.java      — ✅ T2.9d: String advice
- dto/CareCardDto.java             — ✅ T2.6
- dto/CarePlanDto.java             — ✅ T2.6
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
- dto/plantnet/ (PlantNetResponse, PlantNetResult, PlantNetSpecies, PlantNetTaxon) — kept but unused
- mapper/IdentificationMapper.java — ignores topResults, carePlan, AND annotationRegions (all set manually in service)
- repository/IdentificationRepository.java — findByPlantIdOrderByCreatedAtDesc(plantId, pageable)
- service/IdentificationService.java (interface) + service/impl/IdentificationServiceImpl.java
  - Constructor: 8 params — deepSeekClient, visionAnnotationClient, identificationRepository,
    identificationMapper, plantRepository, reminderRepository, fileStorageService, objectMapper
  - 8-step identify() flow: validate → savePhoto → persist PENDING → rateLimit →
    loadUserPreference → PARALLEL(runIdentification(preference,...), visionAnnotationClient.analyzeRegions) →
    parseIdentificationResult() → persist COMPLETED + annotationRegions → reminders
  - runIdentification(preference, ...): switch on AiModelPreference:
    PLANTNET → plantNetClient.identify(); OLLAMA_LLAVA → ollamaClient.identifyPlant() with
    DeepSeek fallback on PlantPalException; default (DEEPSEEK) → deepSeekClient.identifyPlant()
  - Parallel vision: CompletableFuture.supplyAsync() for both futures; identificationFuture.join()
    unwraps CompletionException; annotationFuture silently degrades to empty regions on failure
  - parseAnnotationRegions(String json): JsonNode API (objectMapper.readTree + convertValue with
    constructCollectionType) — NOT a private record (Jackson cannot access private nested records).
  - confidenceToScore(): "HIGH"→0.9, "MEDIUM"→0.6, default→0.3
  - fallbackCarePlan(): single WATERING card, 7-day frequency
  - getCureAdvice(id, request, userId): @Async, ownership check, cureAdviceBuckets (10/hour),
    calls deepSeekClient.generateCureAdvice(); throws ResourceNotFoundException if not owned,
    PlantPalException(429) if rate-limited, PlantPalException(503) if DeepSeek fails
  - CURE_ADVICE_RATE_LIMIT = 10; cureAdviceBuckets ConcurrentHashMap<Long, Bucket>
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
- controller/AiTestController.java — dev-only Ollama ping, NOT profile-guarded (known issue)

### reminder/ — MINIMAL (T2.6 bootstrap; full implementation T3.1)
- entity/CareType.java   — ✅ T2.6; enum: WATERING, FERTILIZING, REPOTTING, PRUNING
- entity/Reminder.java   — ✅ T2.6; does NOT extend AuditableEntity (table has no created_by/updated_by)
                            Uses @CreationTimestamp/@UpdateTimestamp from Hibernate instead
- repository/ReminderRepository.java — ✅ T2.6; minimal JpaRepository<Reminder, Long>

### chat/ — NOT STARTED

## DB Migrations (in order)
001_create_users.sql
002_create_plants.sql
003_create_identifications.sql
004_create_reminders_and_care_logs.sql
005_create_push_subscriptions.sql
006_alter_identifications.sql      ← raw_response TEXT not JSONB
007_add_annotation_regions.sql     ← ✅ T2.9 — adds annotation_regions JSONB to identifications
008_add_care_plan.sql              ← ✅ T2.6 — adds care_plan JSONB to identifications
009_add_health_to_identifications.sql ← ✅ feature/PP-deepseek-identification — adds health_status VARCHAR(30), health_notes TEXT

Current master XML order: 001→009 inclusive (007 inserted BEFORE 008, correct order).

## Test Inventory
unit/UserServiceTest.java
unit/PlantServiceTest.java                ← updated T2.8: +7 SaveFromIdentification tests
                                             (nickname fallbacks, ownership check, reminder creation)
                                             Now has @Mock IdentificationRepository, ReminderRepository,
                                             @Spy ObjectMapper = new ObjectMapper()
unit/IdentificationServiceImplTest.java   ← 12 tests total; 4 nested classes:
                                            Identify (3): happy path, DeepSeek throws→FAILED, not-owned plant skip
                                            CarePlanParsing (4): valid/malformed/null/empty carePlan
                                            ReminderCreation (3): with fertilizing, without fertilizing, correct frequencyDays
                                            AnnotationRegions (2): ✅ T2.9 — malformed JSON→empty, valid JSON→2 regions with types/confidence/widthPct
                                            NOTE: all tests construct IdentificationServiceImpl manually (8-param constructor).
                                            @Mock VisionAnnotationClient visionAnnotationClient injected as 2nd ctor arg.
unit/PlantNetClientTest.java
unit/OllamaClientTest.java
integration/AuthControllerIT.java
integration/PlantControllerIT.java
AbstractIntegrationTest.java    ← Testcontainers base (PostgreSQL + Redis)
testdata/PlantTestDataBuilder.java
testdata/UserTestDataBuilder.java
MISSING: IdentificationControllerIT.java


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
- AiTestController is not @Profile("dev") guarded — will deploy to prod
- IdentificationController calls .get() on CompletableFuture — blocks HTTP thread,
  making @Async a no-op. Intended pattern (job-ID + poll) not yet implemented.
- IdentificationControllerIT missing
- Branch protection on main + dev configured but integration tests not running in CI
- Spotless (Google Java Format) flags CRLF line endings on new files written by Claude Code
  on Windows. Fix with: cd backend && mvn spotless:apply
- PlantNetClient is now called by PlantNetAnnotationClient (T2.9) — no longer completely dead code.
  However, PlantNetAnnotationClient is NOT @Primary, so DeepSeekAnnotationClient is used by default.
  PlantNet annotation path is a non-primary fallback; clean up only if vision annotation is fully removed.
- plantnet/ DTOs (PlantNetResponse, PlantNetResult, etc.) still in use by PlantNetAnnotationClient.
- OllamaClient (llava-phi3) is now active — annotation fallback on 429 AND OLLAMA_LLAVA preference path.
  AiTestController still references it and is not @Profile("dev") guarded (known issue).
- DEEPSEEK_API_KEY in .env is a GitHub PAT — keep rotating if accidentally shared in chat.
  GitHub Models rate limits: 50 requests/day for gpt-4o (vision). 429 on annotation → Ollama fallback.
  429 on identification → PlantPalException 429 bubbles to user (no automatic fallback at that layer).

## Key Files
backend/src/main/java/com/plantpal/shared/dto/ApiResponse.java
backend/src/main/java/com/plantpal/shared/exception/GlobalExceptionHandler.java
backend/src/main/java/com/plantpal/shared/config/SecurityConfig.java
backend/src/main/java/com/plantpal/identification/client/PlantNetClient.java
backend/src/main/java/com/plantpal/identification/client/DeepSeekClient.java
backend/src/main/java/com/plantpal/identification/service/impl/IdentificationServiceImpl.java
backend/src/main/resources/db/changelog/db.changelog-master.xml
backend/src/main/resources/application-dev.yml
backend/.env.example
