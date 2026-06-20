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
PostgreSQL 15, Redis 7, Kafka (Zookeeper + confluentinc/cp-kafka:7.6.0), Liquibase,
JJWT 0.12.5, MapStruct 1.5.5, Lombok, Bucket4j 8.7.0, OllamaClient (RestClient),
DeepSeekClient + GitHubModelsClient (RestClient, HTTP/2, OpenAI-compatible, GitHub
Models endpoint), Testcontainers, JaCoCo 0.8.12, Checkstyle (google_checks.xml),
Spotless 2.43.0, springdoc-openapi 2.5.0, BouncyCastle 1.78.1 (web-push ECDH),
OkHttp MockWebServer (unit-testing RestClient), testcontainers-redis 2.2.2

## Current Status
**Phases 0–4 and 6 are shipped. Phase 5 (Launch prep) is next** — see
TASK_PLAN.md for the full task breakdown (prod config, performance, security
hardening, API docs, deploy). Full session-by-session history of how Phases
0–6 were built lives in STATE.md and git log, not here — this file is a
durable reference to what exists *now*, not a diary.

## AI Provider Map (current)
| Client | Model | Purpose | Endpoint |
|---|---|---|---|
| GitHubModelsClient (identificationModel) | gpt-4o | Plant photo identification + health + care plan incl. actionPlan (single call) | GitHub Models |
| GitHubModelsClient (annotationModel) | gpt-4o-mini | Polygon annotation regions | GitHub Models |
| DeepSeekClient (model) | DeepSeek-R1 | Care plan text, cure advice, disease description, species enrichment | GitHub Models |
| DeepSeekAnnotationClient (@Primary) | injects GitHubModelsClient | Polygon annotation; 2-attempt retry on EOF, falls back to OllamaClient on 429 | GitHub Models |
| OllamaClient | llava-phi3 | (1) OLLAMA_LLAVA identification preference (2) annotation 429 fallback | localhost:11434 |
| PlantNetAnnotationClient / PlantNetClient | — | Non-primary fallback; dead-code cleanup candidate (see Open Items) | plantnet.org |

See ARCHITECT.md's "AI Client Architecture" for why the split is vision-client vs.
text-client rather than one-client-per-feature, and the `stripThinkTags()` /
HTTP-2 / rate-limit details. Auth for both Azure-backed clients:
`Authorization: Bearer <GITHUB_TOKEN>` — **rotate this before going to prod**, it
was shared in chat sessions during development (Phase 5 / T5.3 item).

## Non-Negotiable Conventions
- Constructor injection only. Never @Autowired on fields.
- Member order: logger → static constants → final fields →
  non-final fields → constructor → public methods → private methods
- All entities extend AuditableEntity EXCEPT Reminder, CareLog, PushSubscription,
  TreatmentPlan, Treatment (no created_by/updated_by columns; use Hibernate's
  @CreationTimestamp/@UpdateTimestamp instead). Species DOES extend AuditableEntity
  but has no per-row ownership check (shared across users) — see ARCHITECT.md.
- All controllers return ApiResponse<T> — never raw objects
- All exceptions extend PlantPalException → GlobalExceptionHandler
- Soft deletes only (status = ARCHIVED)
- All list endpoints accept Pageable
- No hardcoded secrets — all via ${ENV_VAR}
- Bucket4j rate limiting on all AI/external API endpoints
  - Use Bandwidth.builder().capacity(N).refillIntervally(N, Duration.ofHours(1)).build()
  - Bandwidth.simple() is DEPRECATED in 8.7.0 — do NOT use it
- Async AI calls via @Async("aiTaskExecutor") + CompletableFuture for cross-bean
  calls; same-class fire-and-forget calls use CompletableFuture.runAsync(...,
  aiTaskExecutor) directly since @Async's proxy has no effect on self-invocation
- Store rawResponse (full external API JSON) always
- In unit tests: construct service manually in @BeforeEach when constructor has non-mockable params
- Cross-package coupling that would create a cycle (e.g. `treatment` needing to
  notify `reminder`, when `treatment` already depends on `reminder`) goes through a
  Spring application event (`ApplicationEventPublisher` + `@EventListener`), not a
  new direct injection — see `TreatmentPlanCompletedEvent` for the precedent.

## Module Structure & File Inventory

### shared/ — fully implemented
- dto/ApiResponse.java, dto/RestPage.java (Jackson-serialisable Page wrapper)
- audit/AuditableEntity.java — base entity, Spring Data Auditing
- exception/{PlantPalException, ResourceNotFoundException, UnauthorizedException,
  ValidationException, GlobalExceptionHandler}.java
- config/SecurityConfig.java — JWT filter, CORS, stateless
- config/AsyncConfig.java — aiTaskExecutor (core=2, max=5, queue=100)
- config/JpaConfig.java, config/OpenApiConfig.java
- config/CacheConfig.java — Redis, implements CachingConfigurer + errorHandler()
  (Redis failures degrade to WARN + cache miss, never a 500). Two RedisTemplates:
  default (Object,Object, for @Cacheable) + byteRedisTemplate (String,byte[] via
  RedisSerializer.byteArray() — NOT ByteArrayRedisSerializer, package-private in
  spring-data-redis 3.2.5, won't compile). byteRedisTemplate is NOT @Primary.
- config/StorageConfig.java — static resource handler for /photos/** (dev)
- config/KafkaConfig.java — KafkaTemplate<String,Object> bean
- controller/PhotoController.java — GET /api/v1/photos/{filename} → raw bytes
- filter/CorrelationIdFilter.java (HIGHEST_PRECEDENCE, MDC + response header), filter/JwtAuthFilter.java
- util/JwtUtil.java
- storage/FileStorageService.java (interface) — loadPhotoBytes(String url) → byte[]
- storage/LocalFileStorageService.java — SHA-256 dedup on savePhoto() via
  DigestUtils.sha256Hex; Redis-first/disk-fallback on loadPhotoBytes(); see
  ARCHITECT.md's Redis Photo Storage Pattern for the full read/write sequence

### user/ — fully implemented
entity/{User, UserStatus, AiModelPreference (DEEPSEEK|PLANTNET|OLLAMA_LLAVA|
GITHUB_GPT4O)}, dto/{RegisterRequest, LoginRequest, AuthResponse, UserResponse,
UserPreferencesRequest, UserPreferencesResponse}, mapper/UserMapper,
repository/UserRepository, service/UserService(+Impl), controller/AuthController.
`GET/PUT /api/v1/users/me/preferences` for AI model preference.

### plant/ — fully implemented
- entity/Plant.java — speciesId, lastScanId, activeTreatmentId (all nullable, T6.3)
  alongside the legacy free-text `species` column (kept as a fallback display value,
  not dropped)
- dto/{CreatePlantRequest, UpdatePlantRequest, PlantResponse, SaveIdentificationAsPlantRequest}
  — PlantResponse carries speciesId/lastScanId/activeTreatmentId/lastScanAt (T6.3/T6.6)
- mapper/PlantMapper.java
- repository/PlantRepository.java — findByIdAndUserId, findAllByUserIdAndStatus,
  findAllByUserIdAndSpeciesIdAndStatus (T6.6), findDistinctSpeciesIdsByUserIdAndStatus
  + findAllByUserIdAndStatusAndSpeciesIdIn (T6.3, backs SpeciesService.getUserSpecies)
- service/PlantService(+Impl) — getUserPlants has a speciesId-filtered overload
  (T6.6, both @Cacheable on the "plants" region, distinct cache key prefixes);
  shared buildEnrichedPage() private helper avoids duplicating the
  enrichWithHealthAndWater()/RestPage wrapping between the two overloads.
  archivePlant() cascades: disables every enabled Reminder for that plant
  (disableRemindersForPlant) so an archived plant stops generating pushes.
  saveFromIdentification(): nickname fallback chain (request → commonName →
  scientificName → "My Plant"), links identification.plantId, creates reminders
  from carePlan JSON.
- controller/PlantController.java — GET (list, optional ?speciesId=), POST,
  GET/PUT/DELETE /{id}, POST /from-identification

### identification/ — fully implemented
- entity/Identification.java — care_plan JSONB, health_status/health_notes,
  annotation_regions JSONB, source_image_width/height, ai_model_used, speciesId
  (nullable, T6.3), plantId (nullable — species-level scans have none)
- entity/IdentificationStatus.java (PENDING/COMPLETED/FAILED)
- dto/ — IdentificationResponse (carries CarePlanDto, healthStatus/Notes,
  annotationRegions, sourceImageWidth/Height, aiModelUsed, speciesId),
  CureAdviceRequest/Response (advice + actionPlan), AddCareCardRequest,
  CareCardDto/CarePlanDto (CareCardDto.actionPlan: ActionPlanDto, nullable),
  ActionPlanDto/TreatmentStepDto/DiagramDto (see ARCHITECT.md's Actionable Care
  Plans pattern for the exact shape/validation rules), AnnotationRegionDto +
  PolygonPointDto + BoundingBoxDto (polygon primary, boundingBox legacy fallback),
  DeepSeekPlantResult (internal combined-vision-response DTO),
  IdentificationPendingResponse (202 response), SpeciesMatchDto/
  ResolveSpeciesRequest/PlantMatchDto/ResolvePlantRequest (T6.9)
- event/{IdentificationRequestedEvent, IdentificationCompletedEvent} — published to
  Kafka topics identification.requested / identification.completed
- config/KafkaTopicConfig.java — NewTopic beans, 3 partitions/1 replica each
- consumer/IdentificationConsumer.java — @KafkaListener, delegates to
  identificationService.processIdentification(event)
- mapper/IdentificationMapper.java — ignores topResults/carePlan/annotationRegions
  (set manually in service)
- repository/IdentificationRepository.java — findByPlantIdOrderByCreatedAtDesc,
  findByUserIdOrderByCreatedAtDesc, findLatestPerPlant(plantIds) (batch, avoids N+1)
- util/ActionPlanValidator.java — static normalize(ActionPlanDto), see ARCHITECT.md
- service/IdentificationService(+Impl) — large constructor (15+ params: AI clients,
  repos, FileStorageService, ObjectMapper, KafkaTemplate, CacheManager). Key methods:
  - submitIdentification() (sync, fast): validate → savePhoto → persist PENDING →
    rate-limit → loadUserPreference → publish IdentificationRequestedEvent → 202
  - processIdentification(event) (@Async, called by the Kafka consumer): reloads
    photo bytes from disk (event only carries the URL) → parallel
    runIdentification() + analyzeRegions() → persist COMPLETED/FAILED →
    normalizeActionPlans() on every care card before persist (see ARCHITECT.md's
    note on verifying "single choke-point" claims) → updates plant.lastScanId when
    plantId present → publishes IdentificationCompletedEvent. Never propagates
    exceptions — it's a Kafka listener method.
  - getCureAdvice(id, request, userId): @Async, separate cureAdviceBuckets
    (10/hour, independent from the 20/hour identification bucket)
  - addCareCard(id, req, userId): NOT async (no AI call), idempotent on card title
  - species-match / resolve-species / plant-match / resolve-plant (T6.9): backend
    half of the Flow-1 species/plant disambiguation — see ARCHITECT.md's 3-path
    decision tree for the full flow these implement
- client/ — VisionAnnotationClient (interface), DeepSeekAnnotationClient (@Primary),
  PlantNetAnnotationClient (fallback), PlantNetClient (HTTP/1.1 forced — ALPN fix),
  OllamaClient (local llava-phi3, /api/generate not /api/chat, resizes to 1024px
  before base64), DeepSeekClient, GitHubModelsClient
- controller/IdentificationController.java — POST /analyze (202), GET /{id},
  GET /plant/{plantId}, POST /{id}/cure-advice, POST /{id}/care-plan/cards,
  GET /{id}/species-match, POST /{id}/resolve-species, GET /{id}/plant-match,
  POST /{id}/resolve-plant

### reminder/ — fully implemented
- entity/CareType.java — 10 values (WATERING, LIGHT, HUMIDITY, TEMPERATURE,
  FERTILIZING, REPOTTING, PRUNING, PEST, SEASONAL, BEGINNER_TIP)
- entity/Reminder.java — no AuditableEntity; recurring (default true),
  treatmentPlanId/treatmentPlanTitle/stepOrder, instruction, stepDetail/
  stepDiagramFormat/stepDiagramContent (all nullable, only set for treatment steps)
- entity/{CareLog, PushSubscription, TreatmentPlan, TreatmentPlanStatus
  (ACTIVE|COMPLETED|ABANDONED)}.java
- event/TreatmentPlanCompletedEvent.java (T6.14) — published by
  applyCompletionToReminder() when a TreatmentPlan's last step completes;
  consumed by treatment/event/TreatmentPlanCompletionListener (see below) to sync
  the wrapping Treatment's status without creating a reminder↔treatment package cycle
- repository/ReminderRepository.java — findByUserIdAndEnabledTrue (List + Page
  overloads), findByIdAndUserId, findAllDue(Instant), findNearestWateringPerPlant,
  findByTreatmentPlanIdAndEnabledTrue (completion check), findByTreatmentPlanIdOrderByStepOrder
  (full detail view, includes completed steps), findByPlantIdAndEnabledTrue
  (archive cascade)
- repository/{CareLogRepository, PushSubscriptionRepository, TreatmentPlanRepository}.java
- service/ReminderService(+Impl) — createReminder, getUserReminders (bounded 200,
  batch-fetches plants), deleteReminder (soft), calculateNextDueAt(),
  **applyCompletionToReminder(reminder, performedAt)** — the single completion
  choke-point (see ARCHITECT.md), now also publishes TreatmentPlanCompletedEvent
- service/CareLogService(+Impl) — logCare() delegates to applyCompletionToReminder(),
  getPlantCareLogs (paginated, ownership-checked)
- service/WebPushService(+Impl) — nl.martijndwars:web-push, VAPID keys via @Value
- service/TreatmentPlanService(+Impl) — createFromActionPlan(), getTreatmentPlan()
  (ALL steps, not just enabled-only)
- scheduler/ReminderScheduler.java — @Scheduled(cron="0 0 8 * * *"), Clock injected
  for testability, one push per user grouping all due reminders
- controller/{ReminderController, CareLogController, NotificationController,
  TreatmentPlanController}.java

### treatment/ — fully implemented (T6.2, T6.3, T6.14)
> NOT the same concept as reminder/'s TreatmentPlan — see ARCHITECT.md's "Two
> Treatment concepts" before touching either package.
- entity/Treatment.java — no AuditableEntity; plantId, userId, identificationId,
  diseaseName, diseaseDescription (nullable, async-filled), status
  (TreatmentStatus), treatmentPlanId (nullable FK), startedAt, completedAt
- entity/TreatmentStatus.java — DRAFT | IN_PROGRESS | COMPLETED | DISMISSED
- dto/{TreatmentResponse (incl. identificationId, T6.12), CreateTreatmentRequest}
- repository/TreatmentRepository.java — findByIdAndUserId, findByPlantIdAndUserId,
  findByPlantIdAndDiseaseNameAndStatusIn (one-active-per-disease check),
  findByTreatmentPlanId (completion-sync lookup)
- service/TreatmentService(+Impl) — createTreatment() (DRAFT, fires async
  disease-description generation via CompletableFuture.runAsync), craftPlan()
  (@Async, DRAFT-only, delegates to TreatmentPlanService.createFromActionPlan(),
  sets activeTreatmentId), getTreatment(), getActiveTreatmentForPlant(),
  completeTreatment() (manual, IN_PROGRESS-only), syncFromTreatmentPlanCompletion()
  (T6.14, event-driven — see below). completeTreatment() and the sync method share
  a private markCompleted(Treatment, Optional<Plant>) helper.
- event/TreatmentPlanCompletionListener.java (T6.14) — @EventListener consuming
  reminder/event/TreatmentPlanCompletedEvent, calls syncFromTreatmentPlanCompletion();
  no-ops if no Treatment wraps that plan, or it's not IN_PROGRESS
- controller/TreatmentController.java — POST /treatments, POST /{id}/craft-plan,
  GET /{id}, GET /plants/{id}/active-treatment, PATCH /{id}/complete

### species/ — fully implemented (T6.1, T6.4)
> Shared across users — no ownership check on the Species row itself. See
> ARCHITECT.md's Domain Model section.
- entity/Species.java — extends AuditableEntity; scientificName (unique),
  commonName, description, careOverview, imageUrl, externalDataSource
  ("AI"|"WIKIPEDIA"|"MANUAL"), externalDataFetchedAt, status
- entity/SpeciesStatus.java — ACTIVE | NEEDS_REVIEW
- dto/{SpeciesResponse, SpeciesSummaryDto (speciesId, scientificName, commonName,
  imageUrl, plantCount, healthSummary)}
- mapper/SpeciesMapper.java (MapStruct, toResponse only)
- repository/SpeciesRepository.java — findByScientificName, existsByScientificName
- service/SpeciesService(+Impl) — findOrCreate() (dedup + fires async enrichment),
  getSpecies(), getUserSpecies(userId, pageable) — groups the caller's plants by
  speciesId via PlantRepository's distinct-speciesId queries, healthSummary via
  the same findLatestPerPlant() batch pattern PlantServiceImpl uses
- service/SpeciesEnrichmentService(+Impl) — @Async enrich(speciesId), see
  ARCHITECT.md's enrichment pattern for the full success/failure handling
- controller/SpeciesController.java — GET /{id} (public read), GET /mine (paginated)

### chat/ — fully implemented (T4.1, T6.13)
- dto/ChatRequest.java — @NotBlank message, nullable plantId (T6.13)
- dto/ChatResponse.java — reply
- service/ChatService(+Impl) — 4-param constructor (OllamaClient, PlantRepository,
  IdentificationRepository, TreatmentRepository). chat(): rate-limit (30/hour) →
  if plantId present, buildPlantContext() (ownership-checked, throws
  ResourceNotFoundException before buildGardenContext() runs) prepended to
  buildGardenContext(); else garden context alone → one prompt string →
  ollamaClient.chat(prompt). buildPlantContext(): nickname/species line + optional
  last-scan health line (IdentificationRepository.findLatestPerPlant, reused) +
  optional active-treatment line (plant.activeTreatmentId → TreatmentRepository.findById())
- controller/ChatController.java — POST /api/v1/chat

### dashboard/ — fully implemented (T2.10b, extended T6.7)
- dto/DashboardResponse.java — healthSummary, overdueReminders, todayReminders,
  healthTrends, recentScans (List<RecentScanDto>, T6.7), speciesCount (T6.7)
- dto/{HealthSummaryDto, ReminderSummaryDto, PlantHealthTrendDto, RecentScanDto}
- service/DashboardService(+Impl) — getDashboard(userId): plants bounded at 200,
  reuses findLatestPerPlant() for health summary, Clock-injected day partitioning
  for overdue/today reminders, last-3 identifications for recentScans,
  distinct-non-null-speciesId count among ACTIVE plants for speciesCount.
  Deliberately NOT @Cacheable — nothing evicts it, a stale dashboard would mislead.
- controller/DashboardController.java — GET /api/v1/dashboard
  (the frontend's Home page, T6.7, consumes this same endpoint — no second one)

## DB Migrations (in order — canonical list, don't duplicate elsewhere)
```
001_create_users.sql
002_create_plants.sql
003_create_identifications.sql
004_create_reminders_and_care_logs.sql
005_create_push_subscriptions.sql
006_alter_identifications.sql            raw_response TEXT not JSONB
007_add_annotation_regions.sql           annotation_regions JSONB (before 008)
008_add_care_plan.sql                    care_plan JSONB
009_add_health_to_identifications.sql    health_status, health_notes
010_add_user_preferences.sql             users.ai_model_preference
011_add_image_dimensions.sql             identifications.source_image_width/height
012_add_treatment_plans.sql              treatment_plans table; reminders gains
                                          recurring/treatment_plan_id/title/step_order
013_add_reminder_instruction.sql         reminders.instruction TEXT
014_add_step_detail.sql                  reminders.step_detail/step_diagram_*
015_add_ai_model_used.sql                identifications.ai_model_used
016_create_species.sql                   species table
017_alter_plants_add_species_fk.sql      plants.species_id/last_scan_id/
                                          active_treatment_id (no inline FK on the
                                          last one — treatments doesn't exist until 018)
018_create_treatments.sql                treatments table
019_alter_identifications_add_plant_species_fk.sql
                                          identifications.species_id FK; deferred
                                          fk_plants_active_treatment constraint from 017
```
All 19 applied, in exactly this XML-listed order in db.changelog-master.xml
(Liquibase runs by XML order, not filename — see ARCHITECT.md before adding #020).

## Test Inventory
Full unit suite: 183/183 passing as of T6.14 (checkstyle clean). Layout:
```
unit/{UserServiceTest, PlantServiceTest, IdentificationServiceImplTest,
      ActionPlanValidatorTest, PlantNetClientTest, OllamaClientTest}.java
reminder/unit/{ReminderServiceTest, CareLogServiceTest, TreatmentPlanServiceTest}.java
treatment/unit/TreatmentServiceTest.java
species/unit/{SpeciesServiceTest, SpeciesEnrichmentServiceImplTest}.java
dashboard/unit/DashboardServiceTest.java
chat/unit/ChatServiceImplTest.java
shared/unit/LocalFileStorageServiceTest.java
integration/{AuthControllerIT, PlantControllerIT}.java
AbstractIntegrationTest.java    ← Testcontainers base (PostgreSQL + Redis)
testdata/{PlantTestDataBuilder, UserTestDataBuilder}.java
```
`IdentificationServiceImplTest` is the largest file — nested classes per concern
(Identify, CarePlanParsing, AnnotationRegions, Kafka, CureAdvice, AddCareCard,
species-matching). Constructed manually in `@BeforeEach` (15+ param constructor).
**Missing:** IdentificationControllerIT, TreatmentPlanControllerIT,
TreatmentControllerIT, SpeciesControllerIT — only unit tests exist for these.

## Known Issues / Open Items
- JaCoCo gate is at 10% (not 80%) — restore once integration tests run in CI (Phase 5)
- Integration tests not running in CI (Testcontainers phase isolation issue)
- Missing controller-level integration tests — see Test Inventory above
- **GITHUB_TOKEN must be rotated before prod** — was shared in chat sessions during dev
- PlantNetClient + plantnet/ DTOs are effectively dead code (only reachable via the
  non-primary PlantNetAnnotationClient fallback, or the PLANTNET preference) —
  cleanup candidate, not urgent
- GitHub Models rate limits: ~50 gpt-4o vision calls/day. 429 on annotation →
  Ollama fallback; 429 on identification → PlantPalException(429) bubbles to the
  user, no automatic fallback at that layer
- Kafka/Zookeeper has no production hosting decision yet — needed before T5.5
  (managed add-on, or fall back to synchronous identification for v1.0.0)
- Spotless (Google Java Format) flags CRLF line endings on new files written on
  Windows — fix with `cd backend && mvn spotless:apply`

## Key Files
```
backend/src/main/java/com/plantpal/shared/dto/ApiResponse.java
backend/src/main/java/com/plantpal/shared/exception/GlobalExceptionHandler.java
backend/src/main/java/com/plantpal/shared/config/SecurityConfig.java
backend/src/main/java/com/plantpal/identification/client/DeepSeekClient.java
backend/src/main/java/com/plantpal/identification/client/GitHubModelsClient.java
backend/src/main/java/com/plantpal/identification/service/impl/IdentificationServiceImpl.java
backend/src/main/java/com/plantpal/identification/util/ActionPlanValidator.java
backend/src/main/java/com/plantpal/reminder/service/impl/ReminderServiceImpl.java
backend/src/main/java/com/plantpal/treatment/service/impl/TreatmentServiceImpl.java
backend/src/main/java/com/plantpal/species/service/impl/SpeciesServiceImpl.java
backend/src/main/resources/db/changelog/db.changelog-master.xml
backend/src/main/resources/application-dev.yml
backend/.env.example
```
