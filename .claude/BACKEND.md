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
**Phases 0–4 and 6 are shipped, plus a pre-Phase-5 cleanup pass
(`feature/PP-038-pre-phase5-cleanup`) closing every flagged gap. Phase 7's
backend task (T7.1, `feature/PP-039-model-control-backend`) is also shipped —
see below. Phase 5 (Launch prep) and Phase 7's remaining frontend tasks
(T7.2–T7.4) are next** — see TASK_PLAN.md for the full task breakdown. Full
session-by-session history of how Phases 0–6 were built lives in STATE.md and
git log, not here — this file is a durable reference to what exists *now*, not
a diary.

## AI Provider Map (current — updated T8.0, 2026-06-23)
| Client | Model | Purpose | Endpoint |
|---|---|---|---|
| GitHubModelsClient (identificationModel) | gpt-4o | `VisionModelPreference.GITHUB_GPT4O` — photo identification + health + care plan incl. actionPlan (single call) | GitHub Models |
| GitHubModelsClient (gpt41Model) | gpt-4.1 | `VisionModelPreference.GITHUB_GPT41` — same call, alternate model (`identifyPlantWithGpt41`) | GitHub Models |
| GitHubModelsClient (annotationModel) | gpt-4o-mini | Polygon annotation regions — not preference-routed, always used regardless of vision preference | GitHub Models |
| DeepSeekClient (model) | DeepSeek-R1 | `ReasoningModelPreference.DEEPSEEK_R1` — care plan text, cure advice, disease description, species enrichment | GitHub Models |
| DeepSeekClient (o4MiniModel) | o4-mini | `ReasoningModelPreference.GITHUB_O4_MINI` — cure advice/disease description only (`...ViaO4Mini` methods); `chatCompletion()` swaps `temperature`→`max_completion_tokens` for this model | GitHub Models |
| DeepSeekClient (gpt41MiniModel) | gpt-4.1-mini | `ReasoningModelPreference.GITHUB_GPT41_MINI` — same two calls (`...ViaGpt41Mini`) | GitHub Models |
| DeepSeekAnnotationClient (@Primary) | injects GitHubModelsClient | Polygon annotation; 2-attempt same-client retry on GOAWAY/EOF only — no cross-model fallback (removed in T7.1) | GitHub Models |
| OllamaClient | gemma3:4b (was llava-phi3 pre-T8.0) | `VisionModelPreference`/`ReasoningModelPreference.OLLAMA_GEMMA3` (no longer an automatic fallback target — T7.1); `OLLAMA_LLAVA` kept as a `@Deprecated` enum alias, routes identically | localhost:11434 |
| AnthropicClient | claude-sonnet-4-6 | `VisionModelPreference`/`ReasoningModelPreference.ANTHROPIC_CLAUDE` — one client serves both menus (Claude is multimodal); `isAvailable()` false when `anthropic.api.key` unset, surfaced via `UserPreferencesResponse.visionModelAvailability`/`reasoningModelAvailability` | api.anthropic.com |
| PlantNetAnnotationClient / PlantNetClient | — | PlantNetClient: live `VisionModelPreference.PLANTNET` choice, selectable in the model picker again (restored 2026-06-22, was never actually deleted — just dropped from the picker by T7.1). PlantNetAnnotationClient: still non-primary, effectively unreachable | plantnet.org |

See ARCHITECT.md's "AI Client Architecture" for why the split is vision-client vs.
text-client rather than one-client-per-feature, and the `stripThinkTags()` /
HTTP-2 / rate-limit details. Auth for the two Azure-backed clients:
`Authorization: Bearer <GITHUB_TOKEN>` — **rotate this before going to prod**, it
was shared in chat sessions during development (Phase 5 / T5.3 item). Anthropic
auth: `x-api-key: <ANTHROPIC_API_KEY>` (optional — `AnthropicClient` has no
required default so the app boots without it).

## T7.1 — Model control + structured AI errors (2026-06-22, `feature/PP-039-model-control-backend`)
- **Vision/reasoning preference split**: `users` gained `vision_model_preference`
  (`VisionModelPreference{GITHUB_GPT4O,OLLAMA_LLAVA}`) and
  `reasoning_model_preference` (`ReasoningModelPreference{DEEPSEEK_R1,OLLAMA_LLAVA}`)
  columns (migration 021, backfilled from the old `ai_model_preference`). The old
  enum/column/DTO field are kept (deprecated, not dropped) — additive this phase,
  nothing currently reads the two new fields end-to-end yet (no service threads
  `VisionModelPreference`/`ReasoningModelPreference` into AI calls; that's the
  next increment once T7.2's frontend picker exists). `UserPreferencesRequest`'s
  two new fields are nullable — omitting them leaves the stored preference
  untouched, so old frontend callers don't break.
- **Model fallback removed**: `IdentificationServiceImpl.runIdentification()`'s
  OLLAMA_LLAVA→GITHUB_GPT4O catch-and-retry is gone — Ollama failures now mark
  the identification FAILED and propagate the real error.
  `DeepSeekAnnotationClient` no longer falls back to `OllamaClient` on a 429 or
  swallows exhausted retries into `EMPTY_REGIONS` — it now throws
  `PlantPalException` (429 or 500). The 2-attempt same-client retry for
  GOAWAY/EOF connection resets is unchanged (that's resilience, not model
  substitution).
- **`GlobalExceptionHandler.handlePlantPal()`** now does
  `HttpStatus.resolve(ex.getErrorCode())` instead of hardcoding 500 — existing
  429s (identification rate limit, cure-advice rate limit) now actually reach
  the frontend as 429.
- **`RateLimitException extends PlantPalException`** carries `retryAfterSeconds`
  (computed via Bucket4j's `tryConsumeAndReturnRemaining`/`ConsumptionProbe`,
  not `tryConsume`). `ApiResponse` gained a `retryAfterSeconds` field;
  `GlobalExceptionHandler.handleRateLimit()` (matched before the generic
  `PlantPalException` handler) also sets a `Retry-After` response header. Wired
  at three sites: `IdentificationServiceImpl.submitIdentification()`,
  `IdentificationServiceImpl.getCureAdvice()`, and
  `TreatmentServiceImpl.craftPlan()` (which previously had **no** rate limit at
  all on its synchronous `deepSeekClient.generateCureAdvice()` call — now uses
  the existing `aiBuckets`/`TREATMENT_AI_RATE_LIMIT`). The disease-description
  fire-and-forget path in `TreatmentServiceImpl.fireDiseaseDescriptionGeneration()`
  deliberately still degrades to a logged skip rather than throwing — it runs
  synchronously inside `createTreatment()` and throwing there would block
  treatment creation for a background enrichment call.
- **Model-usage tracking fields** (migration 022 adds
  `treatments.disease_description_model`/`treatment_plan_model` and
  `species.enrichment_model`): `CarePlanDto.generatedByModel` (vision model used,
  set in `processIdentification()`), `CareCardDto.actionPlanModel` (set only in
  `addCareCard()`, since that's the one path where a card's actionPlan comes
  from a standalone reasoning-model regeneration rather than the original
  vision call), `TreatmentResponse.diseaseDescriptionModel`/`treatmentPlanModel`,
  `SpeciesResponse.enrichmentModel` (auto-mapped by `SpeciesMapper`, no
  `@Mapping` override needed — field names match). All currently hardcode
  `ReasoningModelPreference.DEEPSEEK_R1`/`OLLAMA_LLAVA` based on which client
  was actually called, since none of these call sites take a per-user
  reasoning-model preference parameter yet.
- `IdentificationServiceImplTest.shouldPersistFallbackAiModelUsed` rewritten to
  `shouldMarkFailedWithoutFallbackWhenOllamaFails` — it had been asserting the
  now-removed fallback behavior.
- 198/198 unit tests pass; `mvn spotless:apply` clean.

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
  choke-point (see ARCHITECT.md), now also publishes TreatmentPlanCompletedEvent.
  Guards against re-completing a non-recurring reminder that's already disabled
  (`ValidationException` — pre-Phase-5 cleanup pass fix; recurring reminders are
  untouched, completing them again later is intended)
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
- service/SpeciesService(+Impl) — findOrCreate(scientificName, commonName,
  AiModelPreference) (dedup + fires async enrichment, preference now threaded
  through from the caller's saved AI model choice instead of hardcoded DeepSeek),
  getSpecies() (parses careCards JSON on read, defensive — malformed JSON
  degrades to null, never throws), getUserSpecies(userId, pageable) — groups the
  caller's plants by speciesId via PlantRepository's distinct-speciesId queries,
  healthSummary + lastScanAt (max across the species' plants) via the same
  findLatestPerPlant() batch pattern PlantServiceImpl uses (zero extra queries)
- service/SpeciesEnrichmentService(+Impl) — @Async enrich(speciesId,
  AiModelPreference) — OLLAMA_LLAVA routes through OllamaClient.chat() with the
  same SPECIES_ENRICHMENT_SYSTEM_PROMPT (made package-visible on DeepSeekClient
  for this); every other preference still goes through DeepSeekClient. Also
  generates a small `careCards` array (CareCardDto shape minus actionPlan — no
  plant-specific reminders at the species level) alongside the existing
  description/careOverview/imageUrl fields, persisted as JSON on
  `Species.careCards` (migration 020). See ARCHITECT.md's enrichment pattern for
  the full success/failure handling.
- controller/SpeciesController.java — GET /{id} (public read), GET /mine (paginated)

### chat/ — fully implemented (T4.1, T6.13, pre-Phase-5 cleanup pass)
- dto/ChatRequest.java — @NotBlank message, nullable plantId (T6.13), nullable
  `history: List<ChatMessageDto>` (cleanup pass)
- dto/ChatMessageDto.java — `role` ("user"|"assistant"), `content`
- dto/ChatResponse.java — reply
- service/ChatService(+Impl) — 4-param constructor (OllamaClient, PlantRepository,
  IdentificationRepository, TreatmentRepository). Prompt assembly extracted into
  a private buildPrompt(request, userId), shared by both chat() and the new
  chatStream(). History renders as a "Previous conversation:" block capped to
  the most recent 10 messages (front-truncated — never trust unbounded client
  input, mirrors ActionPlanValidator's convention); absent/empty history →
  byte-for-byte unchanged behavior. chat(): rate-limit (30/hour) → buildPrompt()
  → ollamaClient.chat(prompt). chatStream(request, userId, onToken): same
  rate-limit check, delegates to ollamaClient.chatStream(prompt, onToken).
  buildPlantContext() (used by buildPrompt() when plantId present,
  ownership-checked, throws ResourceNotFoundException before garden context
  runs): nickname/species line + optional last-scan health line
  (IdentificationRepository.findLatestPerPlant, reused) + optional
  active-treatment line (plant.activeTreatmentId → TreatmentRepository.findById())
- controller/ChatController.java — POST /api/v1/chat, POST /api/v1/chat/stream
  (returns SseEmitter, 60s timeout; the actual send runs via
  `CompletableFuture.runAsync(..., aiTaskExecutor)` — same same-class
  fire-and-forget convention as TreatmentServiceImpl's disease-description
  generation, since `@Async` has no effect on self-invocation — so the emitter
  returns immediately and the controller isn't blocked for the stream's duration)

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
020_add_species_care_cards.sql           species.care_cards TEXT (nullable JSON,
                                          same storage pattern as
                                          identifications.care_plan)
021_split_ai_model_preference.sql        users.vision_model_preference /
                                          reasoning_model_preference, backfilled
                                          from the old ai_model_preference (kept,
                                          deprecated, not dropped)
022_add_ai_model_usage_tracking.sql      treatments.disease_description_model /
                                          treatment_plan_model;
                                          species.enrichment_model
```
All 22 applied, in exactly this XML-listed order in db.changelog-master.xml
(Liquibase runs by XML order, not filename — see ARCHITECT.md before adding #023).

## Test Inventory
Full unit suite: 198/198 passing as of the pre-Phase-5 cleanup pass (checkstyle
clean). Layout:
```
unit/{UserServiceTest, PlantServiceTest, IdentificationServiceImplTest,
      ActionPlanValidatorTest, PlantNetClientTest, OllamaClientTest}.java
reminder/unit/{ReminderServiceTest, CareLogServiceTest, TreatmentPlanServiceTest}.java
treatment/unit/TreatmentServiceTest.java
species/unit/{SpeciesServiceTest, SpeciesEnrichmentServiceImplTest}.java
dashboard/unit/DashboardServiceTest.java
chat/unit/ChatServiceImplTest.java
shared/unit/LocalFileStorageServiceTest.java
{user,plant}/integration/{AuthControllerIT, PlantControllerIT}.java
identification/integration/IdentificationControllerIT.java
reminder/integration/TreatmentPlanControllerIT.java
treatment/integration/TreatmentControllerIT.java
species/integration/SpeciesControllerIT.java
AbstractIntegrationTest.java    ← Testcontainers base (PostgreSQL + Redis, static
                                   shared containers across all *IT.java subclasses)
testdata/{PlantTestDataBuilder, UserTestDataBuilder}.java
```
`IdentificationServiceImplTest` is the largest file — nested classes per concern
(Identify, CarePlanParsing, AnnotationRegions, Kafka, CureAdvice, AddCareCard,
species-matching). Constructed manually in `@BeforeEach` (15+ param constructor).

**Running ITs:** no failsafe plugin is wired in — Surefire's default include
pattern (`**/*Test.java`) never picks up `*IT.java` files, so `mvn verify` only
ever runs the unit suite. Run an IT explicitly: `mvn test -Dtest=SomeControllerIT`.
Each one passes cleanly alone but **running several `*IT.java` classes
back-to-back in the same `-Dtest=A,B,C` invocation has caused Hikari/Lettuce
connection-pool exhaustion** on a resource-constrained Windows dev machine
(each spins up a full Spring context + new Testcontainers connections) — run
them one at a time, don't batch.

## Known Issues / Open Items
- JaCoCo gate is at 55% (the unit suite's real achieved line coverage, with a
  small margin) — was a hardcoded, never-achieved 10%/80% before the
  pre-Phase-5 cleanup pass. Still doesn't include IT coverage since ITs aren't
  wired into `verify` (see Test Inventory above) — raise further only once
  that's solved.
- **GITHUB_TOKEN must be rotated before prod** — was shared in chat sessions during dev
- PlantNetClient is live again (not dead code) — restored to `VisionModelPreference` and the
  model-selector picker 2026-06-22 (see STATE.md/ARCHITECT.md). `PlantNetAnnotationClient`
  (the non-primary annotation fallback) remains effectively unreachable — still a real cleanup
  candidate if its dual-use with PlantNetClient ever gets disentangled.
- GitHub Models rate limits: ~50 gpt-4o vision calls/day. Since T7.1, a 429 on
  annotation or identification always bubbles to the user as a structured
  `RateLimitException`/429 (with `retryAfterSeconds`) — no automatic
  cross-model fallback anywhere in the identification pipeline anymore.
- Kafka/Zookeeper has no production hosting decision yet — needed before T5.5
  (managed add-on, or fall back to synchronous identification for v1.0.0)
- T7.1's `VisionModelPreference`/`ReasoningModelPreference` columns exist and
  are readable/writable via `/users/me/preferences`, but no service yet reads
  them to choose which AI client to call — every AI-calling service still
  branches on the old `AiModelPreference`. Wiring that through is follow-up
  work once T7.2's frontend picker lands (the two new fields would otherwise
  have no UI to set them).
- Spotless (Google Java Format) flags CRLF line endings on new files written on
  Windows — fix with `cd backend && mvn spotless:apply`

## Key Files
```
backend/src/main/java/com/plantpal/shared/dto/ApiResponse.java
backend/src/main/java/com/plantpal/shared/exception/GlobalExceptionHandler.java
backend/src/main/java/com/plantpal/shared/exception/RateLimitException.java
backend/src/main/java/com/plantpal/shared/config/SecurityConfig.java
backend/src/main/java/com/plantpal/identification/client/DeepSeekClient.java
backend/src/main/java/com/plantpal/identification/client/GitHubModelsClient.java
backend/src/main/java/com/plantpal/identification/client/AnthropicClient.java
backend/src/main/java/com/plantpal/identification/service/impl/IdentificationServiceImpl.java
backend/src/main/java/com/plantpal/identification/util/ActionPlanValidator.java
backend/src/main/java/com/plantpal/reminder/service/impl/ReminderServiceImpl.java
backend/src/main/java/com/plantpal/treatment/service/impl/TreatmentServiceImpl.java
backend/src/main/java/com/plantpal/species/service/impl/SpeciesServiceImpl.java
backend/src/main/resources/db/changelog/db.changelog-master.xml
backend/src/main/resources/application-dev.yml
backend/.env.example
```
