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
Lombok, Bucket4j 8.7.0, PlantNet API client, OllamaClient (RestClient),
Testcontainers, JaCoCo 0.8.12, Checkstyle (google_checks.xml), Spotless 2.43.0,
springdoc-openapi 2.5.0, BouncyCastle 1.78.1 (for web-push ECDH),
OkHttp MockWebServer (unit-testing RestClient), testcontainers-redis 2.2.2

## Non-Negotiable Conventions
- Constructor injection only. Never @Autowired on fields.
- Member order: logger → static constants → final fields →
  non-final fields → constructor → public methods → private methods
- All entities extend AuditableEntity
- All controllers return ApiResponse<T> — never raw objects
- All exceptions extend PlantPalException → GlobalExceptionHandler
- Soft deletes only (status = ARCHIVED)
- All list endpoints accept Pageable
- No hardcoded secrets — all via ${ENV_VAR}
- Bucket4j rate limiting on all AI/external API endpoints
- Async AI calls via @Async("aiTaskExecutor") + CompletableFuture
- Store rawResponse (full external API JSON) always

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
- config/CacheConfig.java       — Redis
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

### plant/ — fully implemented
- entity/Plant.java, entity/PlantStatus.java
- dto/CreatePlantRequest.java, UpdatePlantRequest.java, PlantResponse.java
- mapper/PlantMapper.java
- repository/PlantRepository.java  — has existsByIdAndUserId, findByIdAndUserId
- service/PlantService.java (interface) + service/impl/PlantServiceImpl.java
- controller/PlantController.java

### identification/ — fully implemented
- entity/Identification.java, entity/IdentificationStatus.java (PENDING/COMPLETED/FAILED)
- dto/IdentificationResponse.java, IdentifyRequest.java
- dto/plantnet/PlantNetResponse.java, PlantNetResult.java, PlantNetSpecies.java, PlantNetTaxon.java
- mapper/IdentificationMapper.java
- repository/IdentificationRepository.java — findByPlantIdOrderByCreatedAtDesc(plantId, pageable)
- service/IdentificationService.java (interface) + service/impl/IdentificationServiceImpl.java
- client/PlantNetClient.java       — calls PlantNet API, multipart
- client/OllamaClient.java         — calls local Ollama REST API (phi3)
- controller/IdentificationController.java
- controller/AiTestController.java — dev-only Ollama ping, NOT profile-guarded (known issue)

### reminder/ — NOT STARTED (DB migrations exist)
### chat/     — NOT STARTED

## DB Migrations (in order)
001_create_users.sql
002_create_plants.sql
003_create_identifications.sql
004_create_reminders_and_care_logs.sql
005_create_push_subscriptions.sql
006_alter_identifications.sql   ← added post-initial (raw_response stored as TEXT not JSONB)

## Test Inventory
unit/UserServiceTest.java
unit/PlantServiceTest.java
unit/IdentificationServiceImplTest.java
unit/PlantNetClientTest.java
unit/OllamaClientTest.java
integration/AuthControllerIT.java
integration/PlantControllerIT.java
AbstractIntegrationTest.java    ← Testcontainers base (PostgreSQL + Redis)
testdata/PlantTestDataBuilder.java
testdata/UserTestDataBuilder.java
MISSING: IdentificationControllerIT.java

## Known Issues / Open Items
- JaCoCo gate is at 10% (not 80%) — restore once integration tests run in CI
- AiTestController is not @Profile("dev") guarded — will deploy to prod
- IdentificationController calls .get() on CompletableFuture — blocks HTTP thread,
  making @Async a no-op. Intended pattern (job-ID + poll) not yet implemented.
- IdentificationControllerIT missing
- Branch protection on main + dev configured but integration tests not running in CI

## Key Files
backend/src/main/java/com/plantpal/shared/dto/ApiResponse.java
backend/src/main/java/com/plantpal/shared/exception/GlobalExceptionHandler.java
backend/src/main/java/com/plantpal/shared/config/SecurityConfig.java
backend/src/main/java/com/plantpal/identification/client/PlantNetClient.java
backend/src/main/resources/db/changelog/db.changelog-master.xml
backend/src/main/resources/application-dev.yml
backend/.env.example