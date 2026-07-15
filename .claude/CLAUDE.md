> ## Platform integration notice
> This repo is now also a **platform Room** (`../platform/plantpal`, per
> `platform-vault/spec-plantpal.md`). For platform-delta work only:
> (a) `../CLAUDE.md` (the platform's standing orders) takes precedence over
> everything below for that work — this file remains the source of truth for
> PlantPal's own feature work; (b) the platform's root `PROGRESS.md` is the
> platform handoff file for delta sessions — PlantPal's own `.claude/STATE.md`
> continuity file is unaffected and stays in use for PlantPal's own feature
> work; (c) contracts/gateway/naming rules (D002, D009, D022, etc.) apply only
> to platform-facing code (currently: `HEXAGON.md`, `DEPLOYMENT.md`,
> `app-manifest.yaml` at repo root) — PlantPal's domain code below is
> unaffected and continues under this file's rules unchanged.

> ## Demand system
> Platform-delta only; platform standing orders in `../CLAUDE.md` take precedence
> for this. Demands are how this repo asks another repo for something it must not
> build itself. Full reference: `../DEMAND_SYSTEM.md`.
> - **Session start (both directions):** check for demand traffic — any demand
>   dispatched *to* plantpal to work (owner tells you at launch), and any demand
>   plantpal *raised* that is now satisfied (act on follow-ups, then move the file
>   to `demands/archive/`).
> - **To RAISE:** write the envelope file under `demands/`
>   (`YYYY-MM-DD-<target>-<slug>.md`), commit it as a small standalone
>   coordination commit to `main`, and push — that push is the raise. Never
>   implement any part of it in the target repo. (plantpal already raised the
>   ai-gateway/contracts demand this way: `demands/2026-07-09-ai-gateway-full-ai-coverage.md`.)
> - **To FULFILL a demand dispatched here:** work it in-repo as normal, then write
>   a report in `demands/fulfilled/<demand-id>-report.md`. "done" is a claim, not a
>   verdict — never self-certify and never notify the origin directly; the
>   coordinator validates and delivers the assembled summary.

---

# PlantPal — Claude Code Instructions

> This file is read automatically by Claude Code on every session.
> It defines the architecture, conventions, and current build state.
> Keep it lean — it loads every time. Durable patterns belong here or in
> ARCHITECT.md; full file-level inventories belong in BACKEND.md/FRONTEND.md;
> session history belongs in STATE.md. Update after every completed phase.

---

## Project Overview

**PlantPal** — A web app that helps plant enthusiasts care for their plants.
Core loop: take a photo → AI identifies the species and detects health issues
→ personalised care schedule with reminders → AI assistant for ongoing questions.
Phase 6 added a species-centric domain on top of this: shared botanical knowledge
per species, and a per-disease Treatment lifecycle.

**Team:** 2 developers | **Architecture:** Modular monolith (Spring Boot + Angular)
**Target:** Enterprise-grade, horizontally scalable

---

## Architecture Decision: Modular Monolith

> 💡 **Why this matters for learning:** We deliberately chose a modular monolith over microservices
> for the MVP. Each module (plant, identification, reminder, treatment, species, chat, user) is
> fully self-contained with its own layers. If PlantPal ever scales to need microservices, each
> module can be extracted independently without a full rewrite. This is the "monolith-first"
> pattern used by Shopify, Stack Overflow, and Basecamp.

---

## Project Structure
> High-level map only. For exact file-by-file inventories, see BACKEND.md
> (backend) and FRONTEND.md (frontend) — don't let this section and those
> drift into two competing copies of the same list.

```
plantpal/
├── backend/src/main/java/com/plantpal/
│   ├── PlantPalApplication.java
│   ├── plant/            Plant CRUD (entity, DTOs, service, controller, Redis cache)
│   ├── identification/   AI identification pipeline (Kafka async), visual annotation,
│   │                     care plans, actionable action-plans, species/plant matching
│   ├── reminder/         Reminders, care logs, web push, TreatmentPlan (generic
│   │                     multi-step action plan backed by Reminder rows)
│   ├── treatment/        Treatment entity — per-disease lifecycle, wraps TreatmentPlan
│   ├── species/          Species entity — shared botanical knowledge across users
│   ├── chat/             AI chat assistant (optional plant-specific context)
│   ├── dashboard/        Home page aggregate endpoint
│   ├── user/             Auth, JWT, user preferences (incl. AI model choice)
│   └── shared/           Security, caching, storage, exceptions, correlation IDs
│
│   db/changelog/migrations/   19 Liquibase migrations applied (001–019) — see
│                               BACKEND.md for the full annotated list
│
├── frontend/src/app/
│   ├── core/              auth service, JWT interceptor, guard
│   ├── shared/            mermaid-diagram, model-selector, image-lightbox,
│   │                      treatment-step-list, step-detail-dialog
│   └── features/
│       ├── auth/ plant/ identification/ reminder/ treatment/ species/
│       └── dashboard/ (Home + Garden Dashboard) / chat/
│   Bottom nav (5 items): Home | Garden | Identify | Reminders | Chat
│
├── docker-compose.yml       PostgreSQL 15 + Redis 7 + Kafka + Zookeeper
├── .github/workflows/{ci.yml,deploy.yml}
└── .claude/                 Agent memory — AGENTS, ARCHITECT, BACKEND, FRONTEND,
                              STATE, TASK_PLAN, CLAUDE.md (this file)
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
static final String BEARER_PREFIX = "Bearer ";

// Enums: PascalCase type, SCREAMING value
enum PlantStatus     { ACTIVE, ARCHIVED }
enum CareType        { WATERING, LIGHT, HUMIDITY, TEMPERATURE, FERTILIZING,
                        REPOTTING, PRUNING, PEST, SEASONAL, BEGINNER_TIP }
enum HealthStatus    { HEALTHY, ISSUES_DETECTED, UNKNOWN }
enum Confidence      { HIGH, MEDIUM, LOW }
enum TreatmentStatus { DRAFT, IN_PROGRESS, COMPLETED, DISMISSED }    // Treatment entity
enum TreatmentPlanStatus { ACTIVE, COMPLETED, ABANDONED }            // TreatmentPlan entity
                                                                       // ⚠️ two different enums
                                                                       // for two different
                                                                       // entities — see
                                                                       // ARCHITECT.md

// DB tables + columns: snake_case
@Table(name = "plants")
@Column(name = "common_name")
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

1. **Uniform API Response** — every endpoint returns `ApiResponse<T>`, never a raw object.
2. **Pagination on all list endpoints** — never return an unbounded list; always `Pageable`.
3. **Soft deletes everywhere** — never hard-delete; set `status = ARCHIVED`.
4. **Audit fields via `AuditableEntity`** (`@CreatedDate`/`@LastModifiedDate`/`@CreatedBy`/
   `@LastModifiedBy`, free via Spring Data Auditing) on every entity **except** Reminder,
   CareLog, PushSubscription, TreatmentPlan, Treatment (no audit columns on those tables —
   use `@CreationTimestamp`/`@UpdateTimestamp` instead). Species extends `AuditableEntity` but
   is the one entity with no per-row *ownership* — don't conflate the two ideas.
5. **Async AI calls** — `@Async("aiTaskExecutor")` + `CompletableFuture`, never block the HTTP
   thread on a 5–15s external call. Cross-bean calls use `@Async`; same-class fire-and-forget
   calls use `CompletableFuture.runAsync(..., aiTaskExecutor)` directly (Spring's `@Async` proxy
   has no effect on self-invocation).
6. **Rate limiting on AI endpoints** — Bucket4j, `Bandwidth.builder()` (not the deprecated
   `Bandwidth.simple()`).
7. **Correlation ID on every request** — `CorrelationIdFilter` adds `X-Correlation-ID`; logged
   via MDC.

See ARCHITECT.md for the deeper architectural patterns built on top of these (Kafka async
identification, Redis photo storage, the Species/Treatment domain model, etc.) — this section is
the baseline conventions, not the full picture.

---

## Database Schema

19 Liquibase migrations applied (`001`–`019`), executed in the order listed in
`db.changelog-master.xml` (NOT filename order — verify the XML, not just the filenames, before
assuming sequence). Full annotated migration list: BACKEND.md. Full schema: read the migration
files directly (`backend/src/main/resources/db/changelog/migrations/`) — don't trust a
hand-copied schema dump in this doc to stay in sync; only the migrations themselves are ground
truth. Every entity follows the same audit-column shape (see Enterprise Pattern #4):

```sql
-- The baseline shape AuditableEntity maps onto, e.g. plants:
CREATE TABLE plants (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id),
    nickname        VARCHAR(255) NOT NULL,
    species_id      BIGINT REFERENCES species(id),         -- added migration 017
    last_scan_id    BIGINT REFERENCES identifications(id), -- added migration 017
    active_treatment_id BIGINT REFERENCES treatments(id),  -- added migration 017/019
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
);
```

---

## Maven Dependencies (backend/pom.xml)

```xml
<!-- Spring Boot 3.2.x -->
spring-boot-starter-web, -security, -data-jpa, -validation, -actuator,
-data-redis, -aop

<!-- Database -->
postgresql, liquibase-core

<!-- Messaging -->
spring-kafka

<!-- Auth -->
jjwt-api + jjwt-impl + jjwt-jackson (0.12.x)

<!-- Rate Limiting -->
com.bucket4j:bucket4j-core:8.7.0

<!-- Mapping + Boilerplate -->
mapstruct (1.5.x), lombok

<!-- Web Push -->
nl.martijndwars:web-push:5.1.1

<!-- API Documentation -->
springdoc-openapi-starter-webmvc-ui (2.x)

<!-- Tests -->
spring-boot-starter-test, mockito-core, assertj-core,
testcontainers (postgresql + redis)

<!-- Code Quality (build plugins) -->
checkstyle-plugin, spotless-plugin (Google Java Format), jacoco-plugin
```

> 💡 **Redis note:** Wiring Redis early means the cache is shared across instances the moment
> you scale horizontally — in-memory caching breaks that.

---

## AI Integration

### Provider Map
| Provider | Model | Purpose |
|---|---|---|
| GitHubModelsClient | gpt-4o | Photo identification + health + care plan (single vision call) — `VisionModelPreference.GITHUB_GPT4O` |
| GitHubModelsClient | gpt-4.1 | Alternate identification model — `VisionModelPreference.GITHUB_GPT41` |
| GitHubModelsClient | gpt-4o-mini | Visual annotation (polygon regions) — not preference-routed, always used |
| DeepSeekClient | DeepSeek-R1 | Care plan text, cure advice, disease description, species enrichment — `ReasoningModelPreference.DEEPSEEK_R1` |
| DeepSeekClient | o4-mini | Cure advice / disease description, via the same Azure endpoint — `ReasoningModelPreference.GITHUB_O4_MINI` (despite the class name, see its class-level Javadoc) |
| DeepSeekClient | gpt-4.1-mini | Same, faster/cheaper tier — `ReasoningModelPreference.GITHUB_GPT41_MINI` |
| OllamaClient | gemma3:4b | Local vision + reasoning — `VisionModelPreference`/`ReasoningModelPreference.OLLAMA_GEMMA3` (`OLLAMA_LLAVA` kept `@Deprecated`+parseable, routes identically) |
| AnthropicClient | claude-sonnet-4-6 | Vision (identification + annotation) AND reasoning (cure advice, disease description) — Claude is multimodal, one client serves both menus. `anthropic.api.key` has no required default; `isAvailable()` gates the option off when un-keyed (see `UserPreferencesResponse.visionModelAvailability`/`reasoningModelAvailability`) — `VisionModelPreference`/`ReasoningModelPreference.ANTHROPIC_CLAUDE` |

> **Endpoint:** `https://models.inference.ai.azure.com/chat/completions` (GitHub Models, incl. DeepSeekClient's o4-mini/gpt-4.1-mini routes)
> **Anthropic endpoint:** `https://api.anthropic.com/v1/messages`, `x-api-key` + `anthropic-version` headers, Apache HttpClient5-backed `RestClient`.
> **Auth:** GitHub PAT via `${GITHUB_TOKEN}` (Bearer) — **rotate before prod**, shared in dev chats. Anthropic via `${ANTHROPIC_API_KEY}` (optional).
> **HTTP/2 required** for both Azure-backed clients; 5-minute read timeout. Anthropic uses HTTP/1.1 via Apache HttpClient5.
> **DeepSeek-R1 quirk:** wraps output in `<think>...</think>` before JSON —
> `DeepSeekClient.stripThinkTags()` (package-private static, also used by GitHubModelsClient,
> OllamaClient, and AnthropicClient) handles this plus stray ` ```json...``` ` fences.
> **o4-mini quirk:** rejects `temperature`, uses `max_completion_tokens` instead — handled in
> `DeepSeekClient`'s shared `chatCompletion()` helper (checked by comparing the request model
> string against the injected `o4MiniModel` field).

Full client-split rationale and the Kafka async pipeline built around these calls: ARCHITECT.md.

### Plant identification — response shape (paraphrased; see GitHubModelsClient for the literal prompt)
```json
{
  "species": "scientific name or null",
  "common_name": "...",
  "confidence": "HIGH | MEDIUM | LOW",
  "health_status": "HEALTHY | ISSUES_DETECTED | UNKNOWN",
  "health_notes": "...",
  "care_tips": { "watering_frequency_days": 7, "sunlight": "...", "...": "..." },
  "care_plan": { "careCards": [ { "actionPlan": { "...": "see ARCHITECT.md" } } ] }
}
```

### Chat assistant system prompt
```
You are PlantPal, a friendly and knowledgeable plant care assistant.

The user's garden:
{garden_context}

{optional plant-specific context block — see ARCHITECT.md's Domain Model section}

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
    url: jdbc:postgresql://localhost:15433/plantpal_dev
    username: ${DB_USERNAME:plantpal}
    password: ${DB_PASSWORD:plantpal}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:16379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:29192}
  jpa:
    hibernate.ddl-auto: validate      # Liquibase owns the schema
  liquibase:
    contexts: dev

github:
  base-url: ${GITHUB_BASE_URL:https://models.inference.ai.azure.com}
  token: ${GITHUB_TOKEN}                       # GitHub PAT with models:read scope
  models:
    identification-model: ${GITHUB_IDENTIFICATION_MODEL:gpt-4o}
    annotation-model: ${GITHUB_ANNOTATION_MODEL:gpt-4o-mini}

deepseek:
  model: ${DEEPSEEK_MODEL:DeepSeek-R1}

ollama:
  base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
  model: ${OLLAMA_MODEL:llava-phi3}

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

```
unit/          → Pure logic, no Spring context. Mockito. Fast (<1s per test).
integration/   → Full Spring context + real PostgreSQL via Testcontainers.
testdata/      → Builder pattern for all test fixtures. No magic strings in tests.
```

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
            var request = PlantTestDataBuilder.aCreatePlantRequest().build();
            var plant = PlantTestDataBuilder.aPlant().build();
            when(plantRepository.save(any())).thenReturn(plant);

            var result = plantService.createPlant(request, 1L);

            assertThat(result).isNotNull();
            verify(plantRepository).save(any(Plant.class));
        }
    }
}
```

**JaCoCo coverage gate:** Updated in T9.5 (was 55%). See BACKEND.md for current value.
`*IT.java` integration tests are now wired into `mvn verify` via maven-failsafe-plugin (T9.5).
`AbstractIntegrationTest` uses the **Singleton Container Pattern** — containers start once in a
`static {}` block, never restarted between classes, so Spring's context cache stays valid.
`application-test.yml` has `spring.kafka.listener.auto-startup: false` (no Kafka in CI).
100% is a vanity metric; the goal is testing critical paths without wasting time on trivial getters.

---

## Git Workflow

```bash
# Branches
main / master → production, never commit directly
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
plant | identification | reminder | treatment | species | chat | user | auth | shared | config | ci | infra
```

---

## Running the Project

```bash
# Start local infrastructure (PostgreSQL :15433, Redis :16379, Kafka :29192, Zookeeper :12181
# — platform 81xx/1xxxx port block, see docker-compose.yml)
docker-compose up -d

# Backend
cd backend
cp .env.example .env        # fill in GITHUB_TOKEN, JWT_SECRET, VAPID keys
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# → http://localhost:8080
# → http://localhost:8080/swagger-ui.html
# → http://localhost:8080/actuator/health
# NOTE: server.port is hardcoded to 8080 (application.yml) — a natively-run backend
# (this command) is NOT reachable at 8180, which is only where the dockerized
# `backend` compose service is published. proxy.conf.json now targets 8180 to
# match the dockerized service; override --server.port=8180 or repoint the proxy
# if you run the backend natively.

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

> Full session-by-session history: STATE.md. Full task prompts: TASK_PLAN.md.
> Archived snapshots: .claude/Archive/.

| Phase | Status |
|---|---|
| 0 — Project Setup | ✅ Complete |
| 1 — Auth + Plant Management | ✅ Complete |
| 2 — AI Plant Identification | ✅ Complete |
| 3 — Reminders + Care Plans | ✅ Complete (T3.3 manual device testing → Phase DEPLOY beta) |
| 4 — AI Chat | ✅ Complete (streaming + conversation history) |
| 6 — Species & Treatment Domain Restructure | ✅ Complete (T6.1–T6.14) |
| 7 — Model Control, Batch Scanning, Multi-Treatment UX | ✅ Complete (T7.1–T7.4) |
| 8 — PlantNet First-Class Provider | ✅ Complete (T8.0–T8.7, all merged to dev) |
| 8.5 — Identification Pipeline Resilience | ✅ Complete (T8.A–T8.G, all merged to dev) |
| 9 — Quality, Testing & Hardening | ✅ Complete (T9.1–T9.8, merged to dev) |
| 9.5 — Species Card Harvest + Async Reliability | ✅ Complete (T9.A–T9.F, merged to dev) |
| 10 — Contextual Scanning & Treatment Polish | ✅ Complete (T10.A–I, PP-071–078, merged to dev incl. post-phase bugfixes) |
| DEPLOY — Launch Preparation | 🔲 Not started (T-DEPLOY.1–8, PP-079+; was Phase 5/10) |
| — Pre-Phase-5 cleanup pass | ✅ Complete (`feature/PP-038-pre-phase5-cleanup`) |

---

## Hard Rules — Never Break These

- **No `@Autowired`** — constructor injection only
- **No `null` returns** from services — throw `ResourceNotFoundException` or return `Optional`
- **No unbounded list queries** — always use `Pageable`
- **No direct DB writes from controllers** — always through the service layer
- **No AI calls from controllers** — always through service → client
- **No hard deletes** — set `status = ARCHIVED`
- **No `ddl-auto: create-drop` or `update`** outside test profile — Liquibase owns the schema
- **No secrets in code or YAML** — always `${ENV_VAR}` references
- **No method longer than 50 lines** — decompose
- **No `@Data` on JPA entities** — use `@Getter @Setter @Builder` separately
  (Hibernate's lazy loading breaks with Lombok `@Data`)
- **No new direct cross-package injection that creates a cycle** — go through a Spring
  application event instead (see ARCHITECT.md's Treatment/Reminder completion-sync example)
- **Doc sync is the last step of every phase implementation — always, automatically.**
  After the final code commit, update the `.claude/` files (STATE.md, CLAUDE.md build
  table, TASK_PLAN.md; decisions/learnings go in ARCHITECT.md). The user does not need
  to ask. "Start Phase N" and "implement all" both include this sync. Platform-delta
  sessions additionally append their handoff to `PROGRESS.md` per `../CLAUDE.md`.
  (The former external `plants-vault` was removed 2026-07-15 — `.claude/` is the
  single memory layer; project memory follows the platform guidelines.)
