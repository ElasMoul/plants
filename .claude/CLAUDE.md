# PlantPal — Claude Code Instructions

> This file is read automatically by Claude Code on every session.
> It defines the full architecture, conventions, and current build state.
> Update it after every completed phase.

---

## Project Overview

**PlantPal** — A web app that helps plant enthusiasts care for their plants.
Core loop: take a photo → AI identifies the plant and detects health issues
→ personalised care schedule with reminders → AI assistant for ongoing questions.

**Team:** 2 developers | **Architecture:** Modular monolith (Spring Boot + Angular)
**AI:** Ollama (local) — model: phi3 | **Target:** Enterprise-grade, horizontally scalable

---

## Architecture Decision: Modular Monolith

> 💡 **Why this matters for learning:** We deliberately chose a modular monolith over microservices
> for the MVP. Each module (plant, identification, reminder, chat, user) is fully self-contained
> with its own layers. If PlantPal ever scales to need microservices, each module can be extracted
> independently without a full rewrite. This is the "monolith-first" pattern used by Shopify,
> Stack Overflow, and Basecamp.

---

## Project Structure
> ⚠️ This section reflects ACTUAL files on disk (verified 2026-06-14).
> Files listed here exist. Files marked [PLANNED] do not exist yet.

```
plantpal/
├── backend/
│   ├── src/main/java/com/plantpal/
│   │   ├── PlantPalApplication.java
│   │   │
│   │   ├── plant/                                    # ✅ Fully implemented
│   │   │   ├── controller/PlantController.java
│   │   │   ├── service/PlantService.java
│   │   │   ├── service/impl/PlantServiceImpl.java
│   │   │   ├── repository/PlantRepository.java
│   │   │   ├── entity/Plant.java
│   │   │   ├── entity/PlantStatus.java
│   │   │   ├── dto/CreatePlantRequest.java
│   │   │   ├── dto/UpdatePlantRequest.java
│   │   │   ├── dto/PlantResponse.java
│   │   │   └── mapper/PlantMapper.java
│   │   │
│   │   ├── identification/                           # ✅ Backend fully implemented
│   │   │   ├── controller/IdentificationController.java
│   │   │   ├── controller/AiTestController.java      # ⚠️ Not @Profile("dev") guarded
│   │   │   ├── service/IdentificationService.java
│   │   │   ├── service/impl/IdentificationServiceImpl.java
│   │   │   ├── client/PlantNetClient.java            # HTTP/1.1 forced (ALPN fix)
│   │   │   ├── client/OllamaClient.java              # phi3, local Ollama
│   │   │   ├── client/DeepSeekClient.java            # ✅ T2.6
│   │   │   ├── repository/IdentificationRepository.java
│   │   │   ├── entity/Identification.java
│   │   │   ├── entity/IdentificationStatus.java
│   │   │   ├── dto/IdentificationResponse.java
│   │   │   ├── dto/IdentifyRequest.java
│   │   │   ├── dto/CareCardDto.java                  # ✅ T2.6
│   │   ├── dto/CarePlanDto.java                  # ✅ T2.6
│   │   │   ├── dto/plantnet/PlantNetResponse.java
│   │   │   ├── dto/plantnet/PlantNetResult.java
│   │   │   ├── dto/plantnet/PlantNetSpecies.java
│   │   │   ├── dto/plantnet/PlantNetTaxon.java
│   │   │   └── mapper/IdentificationMapper.java
│   │   │
│   │   ├── reminder/                                 # ⚠️ Partial (T2.6 bootstrap only)
│   │   │   ├── entity/CareType.java                  # ✅ T2.6 — enum: WATERING/FERTILIZING/REPOTTING/PRUNING
│   │   │   ├── entity/Reminder.java                  # ✅ T2.6 — no AuditableEntity (table has no audit cols)
│   │   │   ├── repository/ReminderRepository.java    # ✅ T2.6 — minimal JpaRepository
│   │   │   └── [service, controller, scheduler planned in T3.1]
│   │   │
│   │   ├── chat/                                     # ❌ NOT STARTED
│   │   │   └── [all files planned in T4.1]
│   │   │
│   │   ├── user/                                     # ✅ Fully implemented
│   │   │   ├── controller/AuthController.java
│   │   │   ├── service/UserService.java
│   │   │   ├── service/impl/UserServiceImpl.java
│   │   │   ├── repository/UserRepository.java
│   │   │   ├── entity/User.java
│   │   │   ├── entity/UserStatus.java
│   │   │   ├── dto/RegisterRequest.java
│   │   │   ├── dto/LoginRequest.java
│   │   │   ├── dto/AuthResponse.java
│   │   │   ├── dto/UserResponse.java
│   │   │   └── mapper/UserMapper.java
│   │   │
│   │   └── shared/                                   # ✅ Fully implemented
│   │       ├── audit/AuditableEntity.java
│   │       ├── config/SecurityConfig.java
│   │       ├── config/AsyncConfig.java               # aiTaskExecutor (core=2, max=5)
│   │       ├── config/CacheConfig.java               # Redis, implements CachingConfigurer
│   │       ├── config/JpaConfig.java
│   │       ├── config/OpenApiConfig.java
│   │       ├── config/StorageConfig.java             # Serves /photos/** (dev)
│   │       ├── dto/ApiResponse.java
│   │       ├── dto/RestPage.java                     # Jackson-serialisable Page wrapper
│   │       ├── exception/PlantPalException.java
│   │       ├── exception/ResourceNotFoundException.java
│   │       ├── exception/UnauthorizedException.java
│   │       ├── exception/ValidationException.java
│   │       ├── exception/GlobalExceptionHandler.java
│   │       ├── filter/CorrelationIdFilter.java
│   │       ├── filter/JwtAuthFilter.java
│   │       ├── storage/FileStorageService.java       # Interface
│   │       ├── storage/LocalFileStorageService.java  # @Profile("!prod")
│   │       └── util/JwtUtil.java
│   │
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-test.yml
│   │   └── db/changelog/
│   │       ├── db.changelog-master.xml
│   │       └── migrations/
│   │           ├── 001_create_users.sql
│   │           ├── 002_create_plants.sql
│   │           ├── 003_create_identifications.sql
│   │           ├── 004_create_reminders_and_care_logs.sql
│   │           ├── 005_create_push_subscriptions.sql
│   │           ├── 006_alter_identifications.sql     # raw_response TEXT not JSONB
│   │           ├── 007_add_annotation_regions.sql    # [PLANNED — T2.9] ← NOT CREATED YET
│   │           ├── 008_add_care_plan.sql             # ✅ T2.6 — care_plan JSONB on identifications
│   │           └── 009_add_performance_indexes.sql   # [PLANNED — T5.2]
│   │
│   └── src/test/java/com/plantpal/
│       ├── AbstractIntegrationTest.java              # Testcontainers base (PG + Redis)
│       ├── testdata/PlantTestDataBuilder.java
│       ├── testdata/UserTestDataBuilder.java
│       ├── plant/unit/PlantServiceTest.java
│       ├── plant/integration/PlantControllerIT.java
│       ├── identification/unit/IdentificationServiceImplTest.java
│       ├── identification/unit/OllamaClientTest.java
│       ├── identification/unit/PlantNetClientTest.java
│       ├── identification/integration/IdentificationControllerIT.java  # [MISSING]
│       ├── user/unit/UserServiceTest.java
│       └── user/integration/AuthControllerIT.java
│
├── frontend/src/app/
│   ├── app.module.ts
│   ├── app-routing.module.ts
│   ├── app.component.{ts,html,scss}
│   │
│   ├── core/                                         # ✅ Fully implemented
│   │   ├── core.module.ts
│   │   ├── guards/auth.guard.ts
│   │   ├── interceptors/jwt.interceptor.ts
│   │   ├── models/api-response.model.ts
│   │   ├── models/user.model.ts
│   │   └── services/auth.service.ts
│   │
│   ├── shared/shared.module.ts
│   │
│   └── features/
│       ├── auth/                                     # ✅ login + register
│       │   ├── login/login.component.{ts,html,scss}
│       │   ├── register/register.component.{ts,html,scss}
│       │   ├── auth-routing.module.ts
│       │   └── auth.module.ts
│       │
│       ├── plant/                                    # ✅ Full CRUD
│       │   ├── components/plant-card/
│       │   ├── components/plant-detail/
│       │   ├── components/plant-form/
│       │   ├── components/plant-list/
│       │   ├── models/plant.model.ts
│       │   ├── services/plant.service.ts
│       │   ├── plant-routing.module.ts
│       │   └── plant.module.ts
│       │
│       ├── identification/                           # ✅ Implemented (PR #5 merged)
│       │   ├── components/identification-result/
│       │   ├── components/photo-upload/
│       │   ├── components/photo-annotator/           # [PLANNED — T2.9]
│       │   ├── components/preview-card/              # [PLANNED — T2.8]
│       │   ├── components/care-plan/                 # ✅ T2.7 — care-card + care-plan components + CarePlanModule
│       │   ├── identification-home/
│       │   ├── pages/identification-page/
│       │   ├── models/identification.model.ts
│       │   ├── services/identification.service.ts
│       │   ├── identification-routing.module.ts
│       │   └── identification.module.ts
│       │
│       ├── reminder/                                 # ❌ Stub only
│       │   └── reminder-list/ (stub)
│       │
│       ├── chat/                                     # ❌ Stub only
│       │   └── chat-home/ (stub)
│       │
│       └── ai-test/                                  # Dev tool — should be removed before prod
│
├── docker-compose.yml
├── .github/workflows/{ci.yml,deploy.yml}
└── .claude/                                          # Agent memory
    ├── AGENTS.md, ARCHITECT.md, BACKEND.md
    ├── FRONTEND.md, STATE.md, TASK_PLAN.md, CLAUDE.md
```

---

## Naming Conventions

### Java
```java
// Classes: PascalCase with layer suffix
PlantService, PlantController, PlantRepository, PlantMapper

// Methods + variables: camelCase, verb-first
createPlant(), findPlantById(), archivePlant()
private String plantNickname;
private List<Plant> userPlants;

// Constants: SCREAMING_SNAKE_CASE
static final int MAX_PHOTO_SIZE_MB = 10;
static final int DEFAULT_WATERING_DAYS = 7;
static final String BEARER_PREFIX = "Bearer ";

// Enums: PascalCase type, SCREAMING value
enum PlantStatus  { ACTIVE, ARCHIVED }
enum CareType     { WATERING, FERTILIZING, REPOTTING, PRUNING }
enum HealthStatus { HEALTHY, ISSUES_DETECTED, UNKNOWN }
enum Confidence   { HIGH, MEDIUM, LOW }

// DB tables + columns: snake_case
@Table(name = "plants")
@Column(name = "common_name")
@Column(name = "acquired_at")
```

### Mandatory class member order (Java)
```java
public class PlantServiceImpl implements PlantService {
    // 1. Logger — always first
    private static final Logger log = LoggerFactory.getLogger(PlantServiceImpl.class);

    // 2. Static constants
    private static final String PLANT_CREATED_MSG = "Plant created successfully";

    // 3. Final instance fields (constructor-injected)
    private final PlantRepository plantRepository;
    private final PlantMapper plantMapper;

    // 4. Non-final fields (@Value only)
    @Value("${app.plant.max-per-user}")
    private int maxPlantsPerUser;

    // 5. Constructor(s) — constructor injection only, never @Autowired
    public PlantServiceImpl(PlantRepository plantRepository, PlantMapper plantMapper) {
        this.plantRepository = plantRepository;
        this.plantMapper = plantMapper;
    }

    // 6. Public methods (interface implementation)
    // 7. Package-private methods (for testing)
    // 8. Private methods (in order of use)
}
```

### Mandatory service pattern
```java
// Always interface + impl separated — never skip the interface
public interface PlantService {
    PlantResponse createPlant(CreatePlantRequest request, Long userId);
    PlantResponse updatePlant(Long id, UpdatePlantRequest request, Long userId);
    void archivePlant(Long id, Long userId);
    Page<PlantResponse> getUserPlants(Long userId, Pageable pageable); // ← always paginated
    PlantResponse getPlant(Long id, Long userId);
}
```

---

## Enterprise Patterns (apply consistently)

### 1. Uniform API Response
```java
// Every endpoint returns ApiResponse<T> — never raw objects
@PostMapping
public ResponseEntity<ApiResponse<PlantResponse>> createPlant(...) {
    PlantResponse plant = plantService.createPlant(request, getUserId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(plant, "Plant created successfully"));
}
```

### 2. Pagination on all list endpoints
```java
// Never return unbounded lists — always use Pageable
Page<PlantResponse> getUserPlants(Long userId, Pageable pageable);

// Controller:
@GetMapping
public ResponseEntity<ApiResponse<Page<PlantResponse>>> getUserPlants(
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
```

### 3. Soft deletes everywhere
```java
// Never hard-delete — set status = ARCHIVED
// Users can recover data, you can audit history
plantRepository.archivePlant(id);  // UPDATE plants SET status='ARCHIVED' WHERE id=?
```

### 4. Audit fields on every entity (via AuditableEntity)
```java
// Every entity extends this
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    @CreatedDate   private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
    @CreatedBy     private Long createdBy;  // userId from SecurityContext
    @LastModifiedBy private Long updatedBy;
}
```
> 💡 **Why:** Audit trails are free with Spring Data Auditing. Critical for debugging,
> support requests, and GDPR compliance if you ever open to the public.

### 5. Async AI calls
```java
// Claude API can take 5-15 seconds. Never block the HTTP thread.
// Return the job ID immediately, let the client poll or use SSE.
@Async("aiTaskExecutor")
public CompletableFuture<IdentificationResult> analyzePhotoAsync(byte[] image) { ... }
```
> 💡 **Why:** Under load, synchronous AI calls would exhaust your HTTP thread pool.
> Async processing is the enterprise pattern for slow external services.

### 6. Rate limiting on AI endpoints
```java
// Use Bucket4j to limit Claude API calls per user
// Prevents abuse and controls your Anthropic bill
@RateLimiter(name = "ai-identification", fallbackMethod = "rateLimitFallback")
public IdentificationResponse analyzePhoto(...) { ... }
```

### 7. Correlation ID on every request
```java
// CorrelationIdFilter adds X-Correlation-ID to every request/response
// Invaluable for tracing issues across logs
MDC.put("correlationId", UUID.randomUUID().toString());
```

---

## Database Schema

```sql
-- All entities extend AuditableEntity fields:
-- created_at, updated_at, created_by, updated_by

-- 001_users.sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT
);

-- 002_plants.sql
CREATE TABLE plants (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id),
    nickname        VARCHAR(255) NOT NULL,
    species         VARCHAR(255),            -- AI-identified
    common_name     VARCHAR(255),
    photo_url       TEXT,
    location        VARCHAR(100),            -- "living room", "balcony"
    notes           TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    acquired_at     DATE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT
);
CREATE INDEX idx_plants_user_id ON plants(user_id);
CREATE INDEX idx_plants_user_status ON plants(user_id, status);

-- 003_identifications.sql
CREATE TABLE identifications (
    id              BIGSERIAL PRIMARY KEY,
    plant_id        BIGINT REFERENCES plants(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    photo_url       TEXT NOT NULL,
    raw_response    JSONB,                   -- full Claude response stored for debugging
    species         VARCHAR(255),
    common_name     VARCHAR(255),
    confidence      VARCHAR(20),             -- HIGH, MEDIUM, LOW
    health_status   VARCHAR(30),             -- HEALTHY, ISSUES_DETECTED, UNKNOWN
    health_notes    TEXT,
    care_tips       JSONB,                   -- structured care advice
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      BIGINT
);
CREATE INDEX idx_identifications_plant_id ON identifications(plant_id);

-- 004_reminders.sql
CREATE TABLE reminders (
    id                  BIGSERIAL PRIMARY KEY,
    plant_id            BIGINT NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    care_type           VARCHAR(30) NOT NULL,
    frequency_days      INT  NOT NULL,
    next_due_at         TIMESTAMPTZ NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reminders_due ON reminders(next_due_at, enabled) WHERE enabled = TRUE;

-- 005_care_logs.sql
CREATE TABLE care_logs (
    id          BIGSERIAL PRIMARY KEY,
    plant_id    BIGINT NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    care_type   VARCHAR(30) NOT NULL,
    notes       TEXT,
    performed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_care_logs_plant_id ON care_logs(plant_id, performed_at DESC);

-- 006_push_subscriptions.sql
CREATE TABLE push_subscriptions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    endpoint    TEXT NOT NULL,
    key_p256dh  TEXT NOT NULL,
    key_auth    TEXT NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## Maven Dependencies (backend/pom.xml)

```xml
<!-- Spring Boot 3.2.x -->
spring-boot-starter-web
spring-boot-starter-security
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-starter-actuator
spring-boot-starter-data-redis    <!-- Caching layer -->
spring-boot-starter-aop           <!-- For rate limiting AOP -->

<!-- Database -->
postgresql
liquibase-core

<!-- Auth -->
jjwt-api + jjwt-impl + jjwt-jackson  (0.12.x)

<!-- AI — Ollama via Spring RestClient (no extra dependency needed) -->
<!-- base-url and model configured in application-*.yml via ollama.* properties -->

<!-- Rate Limiting -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>

<!-- Mapping + Boilerplate -->
mapstruct (1.5.x)
lombok

<!-- Web Push -->
<dependency>
    <groupId>nl.martijndwars</groupId>
    <artifactId>web-push</artifactId>
    <version>5.1.1</version>
</dependency>

<!-- API Documentation -->
springdoc-openapi-starter-webmvc-ui (2.x)

<!-- Tests -->
spring-boot-starter-test
mockito-core
assertj-core
testcontainers (postgresql + redis)

<!-- Code Quality (build plugins) -->
checkstyle-plugin
spotless-plugin (Google Java Format)
jacoco-plugin  <!-- Code coverage — fail build if < 80% -->
```

> 💡 **Redis note:** Even if you start with a single instance, wiring Redis early means
> your cache is shared across multiple instances the moment you scale horizontally.
> In-memory caching breaks horizontal scaling.

---

## AI Integration

### Provider Map
| Provider | Purpose | When |
|---|---|---|
| PlantNet API | Species identification from photo | Always (production API) |
| Ollama phi3 (local) | Basic text generation, dev testing | Dev only |
| Ollama LLaVA (local) | Visual annotation — bounding boxes, disease detection | Dev only (T2.6) |
| DeepSeek API | Care plan generation — species-specific, dynamic | Dev + Prod (T2.11) |

> **Why DeepSeek for care plans:** Plant care is deeply species-specific. A cactus needs
> water once a month; a fern needs daily misting. DeepSeek-chat (V3) generates richer,
> more reasoned care advice than phi3. It's OpenAI-compatible, cheap, and fast.
> DeepSeek endpoint: `https://api.deepseek.com/chat/completions`
> Model: `deepseek-chat` (V3). API key via `${DEEPSEEK_API_KEY}`.

### Ollama — local dev (vision only after T2.11)
> ⚠️ Ollama phi3 has no remaining role after T2.11 ships. It will be removed from the stack.
> Ollama LLaVA stays for visual annotation (bounding boxes) — free vision model in dev.
> For prod, swap LLaVA calls to DeepSeek VL via the same DeepSeekClient.

### OllamaClient pattern

```java
@Component
public class OllamaClient {

    private static final String DEFAULT_MODEL = "phi3";

    private final RestClient restClient;
    private final String model;

    public OllamaClient(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:phi3}") String model) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.model = model;
    }

    // Returns raw response string — parsing is the caller's responsibility
    public String chat(String systemPrompt, String userMessage) { ... }
}
```

### Plant identification system prompt
```
You are an expert botanist and plant pathologist.
Analyze the described plant and return ONLY a valid JSON object
(no markdown, no preamble, no trailing text):
{
  "species": "scientific name or null",
  "common_name": "common name",
  "confidence": "HIGH | MEDIUM | LOW",
  "health_status": "HEALTHY | ISSUES_DETECTED | UNKNOWN",
  "health_notes": "brief description of any visible issues, or null",
  "care_tips": {
    "watering_frequency_days": <integer>,
    "sunlight": "FULL_SUN | PARTIAL_SHADE | SHADE",
    "fertilizing_frequency_days": <integer>,
    "common_issues": ["issue1", "issue2"]
  }
}
```

### Chat assistant system prompt
```
You are PlantPal, a friendly and knowledgeable plant care assistant.

The user's garden:
{garden_context}

Guidelines:
- Be warm, practical, and specific to the user's actual plants when relevant.
- Keep answers concise: 2-4 short paragraphs.
- If unsure, say so — never guess about plant health.
- If the question is about a plant not in their garden, answer generally.
```

---

## Environment Variables

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/plantpal_dev
    username: ${DB_USERNAME:plantpal}
    password: ${DB_PASSWORD:plantpal}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  jpa:
    hibernate.ddl-auto: validate      # Liquibase owns the schema
    show-sql: false                    # Use query logging instead
  liquibase:
    contexts: dev

ollama:
  base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
  model: ${OLLAMA_MODEL:phi3}

app:
  jwt:
    secret: ${JWT_SECRET}              # Min 64 chars
    expiration-ms: 86400000            # 24h
  storage:
    type: local                        # 'local' | 's3' | 'cloudinary'
    local-path: /tmp/plantpal/photos
  web-push:
    public-key: ${VAPID_PUBLIC_KEY}
    private-key: ${VAPID_PRIVATE_KEY}
    subject: mailto:contact@plantpal.app
  rate-limit:
    ai-calls-per-hour: 20              # Per user
    auth-attempts-per-minute: 5        # Brute force protection
```

---

## Testing Strategy

### Three layers — all required

```
unit/          → Pure logic, no Spring context. Mockito. Fast (<1s per test).
integration/   → Full Spring context + real PostgreSQL via Testcontainers.
               → Tests the full stack from HTTP request to DB response.
testdata/      → Builder pattern for all test fixtures. No magic strings in tests.
```

### Unit test template
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("PlantService - Unit Tests")
class PlantServiceTest {

    @Mock private PlantRepository plantRepository;
    @Mock private PlantMapper plantMapper;
    @InjectMocks private PlantServiceImpl plantService;

    @Nested
    @DisplayName("createPlant()")
    class CreatePlant {

        @Test
        @DisplayName("should create plant successfully when request is valid")
        void shouldCreatePlantSuccessfully() {
            // Given
            var request = PlantTestDataBuilder.aCreatePlantRequest().build();
            var plant = PlantTestDataBuilder.aPlant().build();
            when(plantRepository.save(any())).thenReturn(plant);

            // When
            var result = plantService.createPlant(request, 1L);

            // Then
            assertThat(result).isNotNull();
            verify(plantRepository).save(any(Plant.class));
        }
    }
}
```

### JaCoCo coverage gate
```xml
<!-- Fail the build if coverage drops below 80% -->
<configuration>
    <rules>
        <rule>
            <element>BUNDLE</element>
            <limits>
                <limit>
                    <counter>LINE</counter>
                    <value>COVEREDRATIO</value>
                    <minimum>0.80</minimum>
                </limit>
            </limits>
        </rule>
    </rules>
</configuration>
```
> 💡 **Why 80%:** 100% is a vanity metric. 80% forces you to test critical paths
> while not wasting time on trivial getters. This is the industry standard threshold.

---

## Git Workflow

```bash
# Branches
main          → production, always stable, never commit directly
dev           → integration branch, base for all features
feature/PP-{num}-{short-description}
bugfix/PP-{num}-{short-description}
hotfix/PP-{num}-{short-description}
release/v{semver}

# Commit format (Conventional Commits)
feat(plant): add plant creation endpoint
fix(auth): correct JWT expiration handling
test(identification): add unit tests for AI response parsing
chore(deps): upgrade Spring Boot to 3.2.1
docs(api): document identification endpoints
refactor(reminder): extract due-date calculation to helper
perf(plant): add missing index on plants.user_id

# Module scopes
plant | identification | reminder | chat | user | auth | shared | config | ci | infra
```

---

## Running the Project

```bash
# Start local infrastructure (PostgreSQL + Redis)
docker-compose up -d

# Backend
cd backend
cp .env.example .env        # fill in ANTHROPIC_API_KEY, JWT_SECRET, VAPID keys
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# → http://localhost:8080
# → http://localhost:8080/swagger-ui.html
# → http://localhost:8080/actuator/health

# Frontend
cd frontend
npm install
ng serve --proxy-config proxy.conf.json
# → http://localhost:4200

# Run all tests (includes integration via Testcontainers)
cd backend && mvn clean verify

# Run only unit tests (fast feedback loop)
cd backend && mvn test -Dexclude="**/*IT.java"

# Check code coverage report
open backend/target/site/jacoco/index.html
```

---

## Current Build Status

> **Update after each completed phase.**

| Phase | Status | Notes |
|---|---|---|
| 0 — Project Setup | ✅ Complete | pom.xml, shared infra, all 5 module skeletons, YAML configs |
| 1 — DB Migrations | ✅ Complete | 6 Liquibase SQL files (006 alters identifications), Ollama phi3 |
| 1 — Auth + User module | ✅ Complete | JWT, Spring Security 6, UserService, AuthController |
| 1 — Plant CRUD | ✅ Complete | Entity, DTOs, MapStruct mapper, service, controller, Redis cache |
| 1 — Unit + Integration Tests | ✅ Complete | UserService & PlantService unit tests; Auth & Plant controller ITs |
| 2 — PlantNet identification backend | ✅ Complete | PlantNetClient, OllamaClient, IdentificationService, controller |
| 2 — Identification Angular frontend | ✅ Complete | Photo upload, result display (PR #5 merged) |
| 2 — DeepSeek care plan backend (T2.6) | ✅ Complete | DeepSeekClient, CareCardDto/CarePlanDto, parallel async, reminder bootstrap, migration 008 |
| 2 — Dynamic care plan frontend (T2.7) | ✅ Complete | CarePlanModule, care-card + care-plan components, wired in identification-result + plant-detail |
| 2 — One-click save flow (T2.8) | ✅ Complete (backend) | SaveIdentificationAsPlantRequest, POST /api/v1/plants/from-identification, auto-reminders, 7 unit tests; frontend pending |
| 2 — Visual annotation (T2.9) | 🔲 Not started | Bounding boxes + disease overlay via LLaVA; migration 007 needed first |
| 2 — Garden dashboard (T2.10) | 🔲 Not started | Overview of all plants + health + overdue reminders |
| 3 — Reminders + Push | 🔲 Not started | |
| 4 — AI Chat | 🔲 Not started | |
| 5 — Launch | 🔲 Not started | |

---

## Hard Rules — Never Break These

- **No `@Autowired`** — constructor injection only
- **No `null` returns** from services — throw `ResourceNotFoundException` or return `Optional`
- **No unbounded list queries** — always use `Pageable`
- **No direct DB writes from controllers** — always through the service layer
- **No Ollama/AI calls from controllers** — always through service → client
- **No hard deletes** — set `status = ARCHIVED`
- **No `ddl-auto: create-drop` or `update`** outside test profile — Liquibase owns the schema
- **No secrets in code or YAML** — always `${ENV_VAR}` references
- **No method longer than 50 lines** — decompose
- **No `@Data` on JPA entities** — use `@Getter @Setter @Builder` separately
  (Hibernate's lazy loading breaks with Lombok `@Data`)
