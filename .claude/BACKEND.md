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

## AI Provider Map (current — feature/PP-deepseek-identification)
| Client | Model | Purpose | Endpoint |
|---|---|---|---|
| DeepSeekClient (visionModel) | gpt-4o | Plant photo identification + health + care plan (single call) | GitHub Models (models.inference.ai.azure.com) |
| DeepSeekClient (model) | DeepSeek-R1 | Care plan text regeneration (standalone) | GitHub Models (models.inference.ai.azure.com) |
| PlantNetClient | — | No longer called in main flow; class still exists | plantnet.org |
| OllamaClient | phi3 | Dev testing only | localhost:11434 |

### DeepSeekClient — key facts
- Single client handles both vision (gpt-4o) and text (DeepSeek-R1) via two config properties:
  - `${deepseek.model}` → text model for generateCarePlan()
  - `${deepseek.vision-model}` → vision model for identifyPlant()
- Auth: `Authorization: Bearer <DEEPSEEK_API_KEY>` (GitHub PAT)
- HTTP/2 via JDK HttpClient (NO forced HTTP_1_1 — Azure endpoint requires HTTP/2)
- Read timeout: 5 minutes (gpt-4o vision can be slow)
- `stripThinkTags(String)`: strips `<think>...</think>` blocks emitted by DeepSeek-R1 before JSON parsing
- Debug log of full raw response before parsing (log level DEBUG)
- response_format: json_object on both calls

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

### identification/ — fully implemented (feature/PP-deepseek-identification)
- entity/Identification.java       — care_plan JSONB (String), health_status VARCHAR(30), health_notes TEXT
- entity/IdentificationStatus.java (PENDING/COMPLETED/FAILED)
- dto/IdentificationResponse.java  — has CarePlanDto carePlan, healthStatus, healthNotes fields
- dto/CareCardDto.java             — ✅ T2.6
- dto/CarePlanDto.java             — ✅ T2.6
- dto/DeepSeekPlantResult.java     — ✅ internal DTO for combined vision response:
                                     species, commonName, confidence, healthStatus, healthNotes, CarePlanDto
- dto/IdentifyRequest.java
- dto/plantnet/ (PlantNetResponse, PlantNetResult, PlantNetSpecies, PlantNetTaxon) — kept but unused
- mapper/IdentificationMapper.java — ignores topResults AND carePlan (set manually in service)
- repository/IdentificationRepository.java — findByPlantIdOrderByCreatedAtDesc(plantId, pageable)
- service/IdentificationService.java (interface) + service/impl/IdentificationServiceImpl.java
  - PlantNetClient NO LONGER injected — identification is now fully DeepSeek-vision-based
  - 8-step identify() flow: validate → savePhoto → persist PENDING → rateLimit →
    deepSeekClient.identifyPlant(bytes, mediaType) → parseIdentificationResult() →
    persist COMPLETED (species/commonName/confidence/health/carePlan) → reminders
  - confidenceToScore(): "HIGH"→0.9, "MEDIUM"→0.6, default→0.3
  - parseIdentificationResult(): Jackson parse with fallback on malformed JSON
  - fallbackCarePlan(): single WATERING card, 7-day frequency
- client/PlantNetClient.java       — HTTP/1.1 forced (ALPN fix). NO LONGER CALLED in main flow.
- client/OllamaClient.java         — local Ollama phi3 (text). dev testing only.
- client/DeepSeekClient.java       — ✅ GitHub Models; HTTP/2; 5-min timeout; gpt-4o (vision) + DeepSeek-R1 (text)
- controller/IdentificationController.java
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
008_add_care_plan.sql              ← ✅ T2.6 — adds care_plan JSONB to identifications
009_add_health_to_identifications.sql ← ✅ feature/PP-deepseek-identification — adds health_status VARCHAR(30), health_notes TEXT

NOTE: Migration 007 does NOT exist yet (T2.9 not started). The numbering skips intentionally.
Current master XML order: 001→006, then 008, then 009.
When T2.9 is implemented: create 007_add_annotation_regions.sql and insert it BEFORE 008 in db.changelog-master.xml.

## Test Inventory
unit/UserServiceTest.java
unit/PlantServiceTest.java                ← updated T2.8: +7 SaveFromIdentification tests
                                             (nickname fallbacks, ownership check, reminder creation)
                                             Now has @Mock IdentificationRepository, ReminderRepository,
                                             @Spy ObjectMapper = new ObjectMapper()
unit/IdentificationServiceImplTest.java   ← FULLY REWRITTEN (feature/PP-deepseek-identification): 10 tests for DeepSeek vision flow
                                            No PlantNetClient mock. Mocks deepSeekClient.identifyPlant(any(), any()).
                                            Tests: happy path (confidence=0.9, healthStatus, topResults empty),
                                            FAILED status when DeepSeek throws, not-owned plant skip,
                                            valid/malformed/null/empty carePlan parsing,
                                            reminder creation (with/without fertilizing, correct frequencyDays).
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
- Migration 007_add_annotation_regions.sql is PLANNED (T2.9); insert BEFORE 008 in master XML when created.
- PlantNetClient and plantnet/ DTOs are dead code — no longer called. Remove at next cleanup.
- OllamaClient (phi3) is dev-only and has no role in the current identification flow.
- DEEPSEEK_API_KEY in .env is a GitHub PAT — keep rotating if accidentally shared in chat.
  GitHub Models rate limits apply (free tier); gpt-4o vision calls are quota-heavy.

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
