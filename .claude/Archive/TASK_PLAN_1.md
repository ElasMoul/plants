# PlantPal — Task Plan

**Legend:**
- 👤 **Manual** — you do this yourself (external tools, config, real decisions)
- 🤖 **AI** — Claude Code generates the code entirely from the prompt provided
- 🤝 **Assisted** — you lead, Claude Code helps with specific parts
- 💡 **Suggestion** — architectural note worth understanding, not just following

**Branch format:** `feature/PP-{num}-{short-description}`
**Commit format:** `feat(scope): description` (Conventional Commits)

---

## PHASE 0 — Project Setup
> Goal: reproducible local dev, green CI, compilable skeleton on day one.
> Estimated time: 3–4 days.

---

### T0.1 — Create GitHub repository 👤 Manual
**Branch:** initial commit to `main`, then create `dev`

Steps:
1. Create private repo `plantpal` on GitHub
2. Initialize with a `README.md` and `.gitignore` (Java + Node + IntelliJ + `.env`)
3. Create `dev` branch from `main`
4. Configure branch protection rules:
   - `main`: require PR, 1 approval, CI must pass, no direct push
   - `dev`: CI must pass before merge

**Verify:** pushing directly to `main` is blocked.

---

### T0.2 — Set up local infrastructure 👤 Manual
**Branch:** N/A

Create `docker-compose.yml` at repo root:

```yaml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: plantpal_dev
      POSTGRES_USER: plantpal
      POSTGRES_PASSWORD: plantpal
    ports: ["5432:5432"]
    volumes: [plantpal_postgres:/var/lib/postgresql/data]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

volumes:
  plantpal_postgres:
```

```bash
docker-compose up -d
# Verify: psql -h localhost -U plantpal -d plantpal_dev
```

> 💡 **Why Redis on day one:** Redis is your cache layer. If you add it later, you have
> to retrofit caching into every service. Wiring it now costs 20 minutes; retrofitting costs days.

---

### T0.3 — Generate Spring Boot skeleton 🤖 AI
**Branch:** `feature/PP-001-backend-scaffold`

**Claude Code prompt:**
```
// Phase 0 — Scaffold the full Spring Boot backend for PlantPal

Generate the backend structure exactly as defined in CLAUDE.md.

1. backend/pom.xml — include all dependencies from CLAUDE.md:
   Spring Boot 3.2, Security, Data JPA, Validation, Actuator,
   Data Redis, AOP, PostgreSQL, Liquibase, JJWT 0.12, MapStruct,
   Lombok, anthropic-java 0.8.0, web-push 5.1.1, Bucket4j 8.7.0,
   springdoc-openapi 2.x, JUnit 5, Mockito, AssertJ, Testcontainers.
   Include JaCoCo plugin with 80% line coverage gate.
   Include Checkstyle and Spotless (Google Java Format) plugins.

2. PlantPalApplication.java — @SpringBootApplication, @EnableScheduling,
   @EnableAsync, @EnableJpaAuditing, CORS config allowing localhost:4200.

3. shared/dto/ApiResponse.java — generic <T> with fields:
   boolean success, T data, String message, String correlationId,
   Instant timestamp. Static factory methods: success(T), success(T, String),
   error(String), error(String, int errorCode).

4. shared/audit/AuditableEntity.java — @MappedSuperclass with
   @CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy.

5. shared/exception/:
   - PlantPalException.java (base, extends RuntimeException, has errorCode field)
   - ResourceNotFoundException.java
   - UnauthorizedException.java
   - ValidationException.java
   - GlobalExceptionHandler.java (@RestControllerAdvice — handles all 4 above
     + MethodArgumentNotValidException + generic Exception. Always returns ApiResponse.)

6. shared/filter/CorrelationIdFilter.java — sets X-Correlation-ID in MDC
   (generate UUID if header absent, always return it in response header).

7. shared/config/:
   - AsyncConfig.java — @EnableAsync, ThreadPoolTaskExecutor named "aiTaskExecutor"
     (corePoolSize=2, maxPoolSize=5, queueCapacity=100)
   - CacheConfig.java — @EnableCaching, RedisCacheManager with default TTL 10 min
   - OpenApiConfig.java — title "PlantPal API", version "1.0.0", JWT auth button

8. Empty package structure for: plant, identification, reminder, chat, user modules.

9. application.yml, application-dev.yml, application-test.yml —
   fully commented, all ${ENV_VAR} references, no hardcoded secrets.
   application-test.yml uses Testcontainers auto-configuration.

10. backend/.env.example with every required variable and a comment explaining each.

11. db/changelog/db.changelog-master.xml — empty master file ready for migrations.

Follow ALL conventions in CLAUDE.md (member order, no @Autowired, etc.).
```

**Verify:** `mvn clean compile` exits 0.

---

### T0.4 — Generate Angular skeleton 🤖 AI
**Branch:** `feature/PP-002-frontend-scaffold`

**Claude Code prompt:**
```
// Phase 0 — Scaffold the Angular frontend for PlantPal

Generate the Angular 16+ project in frontend/ using NgModules (not standalone).

1. Create project structure matching CLAUDE.md:
   core/ (services, guards, interceptors, models)
   shared/ (components, directives, pipes)
   features/ (plant/, identification/, reminder/, chat/)
   Each feature module declared in AppModule, lazy-loaded in AppRoutingModule.

2. core/models/api-response.model.ts — TypeScript interface mirroring Java ApiResponse<T>.

3. core/services/auth.service.ts — login(), register(), logout(), isLoggedIn(),
   getCurrentUser(). Store JWT in localStorage with expiry check.

4. core/interceptors/jwt.interceptor.ts — inject "Authorization: Bearer {token}"
   on every request. On 401 response, call logout() and redirect to /login.

5. core/guards/auth.guard.ts — redirect to /login if not authenticated.

6. AppRoutingModule with lazy routes:
   /login → AuthModule, /register → AuthModule
   /plants → PlantModule (default redirect after login)
   /identify → IdentificationModule
   /reminders → ReminderModule
   /chat → ChatModule

7. app.component.html — shell with responsive navbar (links to each feature,
   user menu with logout) + <router-outlet>.

8. environment.ts + environment.prod.ts with apiUrl.
   proxy.conf.json — proxy /api to http://localhost:8080 for dev.

9. ng add @angular/pwa — install PWA support.

10. angular.json — enable strict TypeScript, budgets for bundle size.
    src/styles.scss — import a simple CSS reset.

Use Angular Material or Tailwind CSS — pick whichever installs cleanest with ng add.
```

**Verify:** `ng serve --proxy-config proxy.conf.json` starts on port 4200.

---

### T0.5 — GitHub Actions CI/CD 🤝 Assisted
**Branch:** `feature/PP-003-ci-cd`

**Claude Code prompt:**
```
// Phase 0 — Generate GitHub Actions pipelines for PlantPal

1. .github/workflows/ci.yml — triggered on push to dev and PRs to dev/main:
   - Job backend-ci: Java 17, cache Maven, mvn clean verify
     (Testcontainers will spin up PostgreSQL + Redis automatically)
   - Job frontend-ci: Node 18, cache npm, npm ci, ng build --prod, ng lint
   - Both jobs must pass before merge is allowed

2. .github/workflows/deploy.yml — triggered on push to main only:
   - deploy-backend: mvn package → deploy to Railway via RAILWAY_TOKEN secret
   - deploy-frontend: ng build --prod → deploy to Vercel via VERCEL_TOKEN secret
   - deploy only after both build jobs succeed

3. .github/pull_request_template.md — include sections:
   Description, Type of change (checkboxes), Linked issues,
   Tests (unit added, integration added, all pass, coverage maintained),
   Frontend impact, Quality checklist (follows CLAUDE.md conventions,
   self-review done, no secrets committed).

4. backend/Dockerfile — multi-stage: (1) Maven build stage, (2) JRE 17 slim runtime.
   EXPOSE 8080, health check on /actuator/health.

5. frontend/Dockerfile — multi-stage: (1) Node build, (2) Nginx serving the dist/.
   nginx.conf with gzip, cache headers for assets, try_files for SPA routing.
```

**Verify:** open a draft PR from the scaffold branch — CI runs and passes.

---

### T0.6 — Generate VAPID keys and gather secrets 👤 Manual

```bash
npx web-push generate-vapid-keys
# Copy VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY into backend/.env
```

Also gather and fill `.env`:
- `ANTHROPIC_API_KEY` — from console.anthropic.com
- `JWT_SECRET` — generate: `openssl rand -hex 64`
- Database credentials from Railway

**Verify:** `backend/.env` has all variables filled. It is in `.gitignore`.

---

## PHASE 1 — Authentication + Plant Management
> Goal: a user can register, log in, and fully manage their plant collection.
> Estimated time: Weeks 1–2.

---

### T1.1 — Liquibase migrations (all tables) 🤖 AI
**Branch:** `feature/PP-004-db-migrations`

**Claude Code prompt:**
```
// Phase 1 — Create all Liquibase migration SQL files for PlantPal

Create files in backend/src/main/resources/db/changelog/migrations/
using the exact schema from CLAUDE.md (all tables, all indexes, snake_case columns).

Files:
- 001_create_users.sql
- 002_create_plants.sql
- 003_create_identifications.sql
- 004_create_reminders_and_care_logs.sql
- 005_create_push_subscriptions.sql

Update db.changelog-master.xml to include all 5 in order.

Each migration must be:
- Idempotent (use IF NOT EXISTS)
- Include all indexes defined in CLAUDE.md
- Include audit columns (created_at, updated_at, created_by, updated_by)
  on every table except care_logs (created_by only).
```

**Verify:** `mvn spring-boot:run -Dspring-boot.run.profiles=dev` — Liquibase applies all 5 changes, tables visible in DBeaver.

---

### T1.2 — User module — entity, DTOs, mapper, repository 🤖 AI
**Branch:** `feature/PP-005-user-module`

**Claude Code prompt:**
```
// Phase 1 — Generate the User module (entity, DTOs, mapper, repository)

In com.plantpal.user:

1. entity/User.java:
   - Extends AuditableEntity
   - Fields: id (Long), email, passwordHash, firstName, lastName, status (UserStatus enum)
   - @Entity, @Table(name="users"), @Getter @Setter @Builder (NOT @Data on JPA entities)
   - Implements UserDetails from Spring Security
   - getAuthorities() returns ROLE_USER

2. entity/UserStatus.java enum: ACTIVE, INACTIVE, SUSPENDED

3. dto/RegisterRequest.java: email (@Email @NotBlank), password (@Size(min=8)), firstName, lastName
4. dto/LoginRequest.java: email, password
5. dto/AuthResponse.java: token, tokenType ("Bearer"), expiresIn, userId, email, firstName
6. dto/UserResponse.java: id, email, firstName, lastName, status, createdAt

7. mapper/UserMapper.java (MapStruct):
   - toResponse(User) → UserResponse (exclude passwordHash)

8. repository/UserRepository.java:
   - findByEmail(String email): Optional<User>
   - existsByEmail(String email): boolean

Follow mandatory class member order from CLAUDE.md.
```

---

### T1.3 — Spring Security 6 + JWT 🤖 AI
**Branch:** `feature/PP-005-user-module` (same branch)

**Claude Code prompt:**
```
// Phase 1 — Implement Spring Security 6 + JWT for PlantPal

1. shared/util/JwtUtil.java:
   - generateToken(UserDetails user, Long userId) → String
   - validateToken(String token, UserDetails user) → boolean
   - extractEmail(String token) → String
   - extractUserId(String token) → Long
   - Use JJWT 0.12. Secret from @Value("${app.jwt.secret}").
   - Include userId as a custom claim in the token payload.

2. shared/filter/JwtAuthFilter.java:
   - OncePerRequestFilter
   - Extract Bearer token from Authorization header
   - Validate + set SecurityContext + set userId in MDC for logging

3. shared/config/SecurityConfig.java:
   - Public: POST /api/v1/auth/register, POST /api/v1/auth/login, /actuator/health
   - Everything else: authenticated
   - Stateless session (no cookies)
   - Add JwtAuthFilter before UsernamePasswordAuthenticationFilter
   - CORS: allow http://localhost:4200 (dev) and production URL from @Value

4. user/service/UserService.java (interface):
   - register(RegisterRequest) → AuthResponse
   - login(LoginRequest) → AuthResponse

5. user/service/impl/UserServiceImpl.java:
   - Implements UserService + UserDetailsService (Spring Security)
   - BCrypt passwordEncoder, strength 12
   - register(): check email uniqueness, hash password, save, return JWT
   - login(): authenticate, return JWT
   - All log statements use SLF4J at appropriate levels
   - Follow CLAUDE.md class member order strictly

6. user/controller/AuthController.java:
   - POST /api/v1/auth/register → ResponseEntity<ApiResponse<AuthResponse>>
   - POST /api/v1/auth/login    → ResponseEntity<ApiResponse<AuthResponse>>
   - @Valid on all @RequestBody
   - Never log passwords or tokens
```

---

### T1.4 — Plant module — full backend 🤖 AI
**Branch:** `feature/PP-006-plant-module-backend`

**Claude Code prompt:**
```
// Phase 1 — Generate the full Plant module backend

In com.plantpal.plant:

1. entity/Plant.java (extends AuditableEntity):
   Fields: id, userId (Long), nickname, species, commonName, photoUrl,
   location, notes, status (PlantStatus), acquiredAt (LocalDate),
   Annotations: @Entity @Table(name="plants") @Getter @Setter @Builder

2. entity/PlantStatus.java: ACTIVE, ARCHIVED

3. DTOs: CreatePlantRequest (nickname @NotBlank, location, notes, acquiredAt),
   UpdatePlantRequest (all optional), PlantResponse (all fields + createdAt + updatedAt)

4. mapper/PlantMapper.java (MapStruct):
   toResponse(Plant), toEntity(CreatePlantRequest) — never map userId in mapper

5. repository/PlantRepository.java:
   - findByIdAndUserId(Long id, Long userId): Optional<Plant>
   - findAllByUserIdAndStatus(Long userId, PlantStatus status, Pageable pageable): Page<Plant>
   - existsByIdAndUserId(Long id, Long userId): boolean

6. service/PlantService.java (interface) — ALL list methods use Pageable:
   - createPlant(CreatePlantRequest, Long userId) → PlantResponse
   - updatePlant(Long id, UpdatePlantRequest, Long userId) → PlantResponse
   - archivePlant(Long id, Long userId) — soft delete only
   - getUserPlants(Long userId, Pageable pageable) → Page<PlantResponse>
   - getPlant(Long id, Long userId) → PlantResponse

7. service/impl/PlantServiceImpl.java:
   - Throw ResourceNotFoundException("Plant not found or not owned by user") if plant
     doesn't exist OR belongs to another user (same message — security: don't leak existence)
   - archivePlant: set status=ARCHIVED, save — never DELETE
   - @Cacheable("plants") on getUserPlants, @CacheEvict on create/update/archive
   - Log every action at INFO level with userId and plantId

8. controller/PlantController.java:
   - GET    /api/v1/plants            → Page<PlantResponse> (@PageableDefault size=20)
   - POST   /api/v1/plants            → 201 Created
   - GET    /api/v1/plants/{id}       → PlantResponse
   - PUT    /api/v1/plants/{id}       → PlantResponse
   - DELETE /api/v1/plants/{id}       → 204 No Content (archives, does not delete)
   - Extract userId from SecurityContext (helper method, not @PathVariable)
   - @Operation and @ApiResponse annotations on every endpoint (SpringDoc)
```

> 💡 **Security note:** Returning the same error message whether a plant doesn't exist
> OR belongs to another user is intentional. This is called "resource existence concealment"
> — it prevents an attacker from enumerating other users' plant IDs.

---

### T1.5 — Unit tests — Phase 1 🤖 AI
**Branch:** `feature/PP-006-plant-module-backend` (same branch)

**Claude Code prompt:**
```
// Phase 1 — Generate unit tests for UserService and PlantService

1. testdata/UserTestDataBuilder.java — fluent builder:
   aUser().withEmail("test@example.com").withId(1L).build()
   aRegisterRequest().build()

2. testdata/PlantTestDataBuilder.java — fluent builder:
   aPlant().withId(1L).withUserId(1L).build()
   aCreatePlantRequest().withNickname("My Monstera").build()

3. user/unit/UserServiceTest.java (@ExtendWith(MockitoExtension.class)):
   - register: success, duplicate email throws exception
   - login: success, wrong password throws exception
   @DisplayName in English, Given/When/Then comments, @Nested by method

4. plant/unit/PlantServiceTest.java:
   - createPlant: success
   - getPlant: not found throws ResourceNotFoundException
   - getPlant: found but wrong userId throws ResourceNotFoundException (same exception!)
   - archivePlant: sets status to ARCHIVED, never calls delete
   - getUserPlants: returns page, empty page case

All assertions using AssertJ. Verify mock interactions with verify().
```

---

### T1.6 — Integration tests — Phase 1 🤖 AI
**Branch:** `feature/PP-006-plant-module-backend` (same branch)

**Claude Code prompt:**
```
// Phase 1 — Generate integration tests for Auth and Plant controllers

Use @SpringBootTest(webEnvironment = RANDOM_PORT) + @Testcontainers.
Use a shared abstract base class AbstractIntegrationTest with the
PostgreSQL and Redis containers so containers are reused across test classes.

1. AbstractIntegrationTest.java — static PostgreSQLContainer + GenericContainer (Redis),
   @DynamicPropertySource to override spring.datasource.url and spring.redis.host

2. user/integration/AuthControllerIT.java:
   - POST /api/v1/auth/register → 201, token in response
   - POST /api/v1/auth/register duplicate email → 400
   - POST /api/v1/auth/login valid → 200, token returned
   - POST /api/v1/auth/login wrong password → 401

3. plant/integration/PlantControllerIT.java:
   - Register + login to get token, use token in all subsequent calls
   - Full CRUD: create → get → update → archive (404 after archive)
   - GET another user's plant → 404 (not 403 — existence concealment)
   - GET without token → 401
```

> 💡 **Why Testcontainers over H2:** H2 is a different database with different SQL dialects.
> Your tests should run against the same engine as production. Testcontainers spins up a real
> PostgreSQL Docker container for each test run. Slower but tests that actually represent
> production behaviour. This is the enterprise standard.

---

### T1.7 — Plant module — Angular frontend 🤖 AI
**Branch:** `feature/PP-007-plant-module-frontend`

**Claude Code prompt:**
```
// Phase 1 — Generate the Plant Angular feature module

In frontend/src/app/features/plant/:

1. plant.module.ts + plant-routing.module.ts
   Routes: /plants (list), /plants/new (form), /plants/:id (detail), /plants/:id/edit (form)

2. models/plant.model.ts — TypeScript interfaces matching Java DTOs exactly

3. services/plant.service.ts:
   - getPlants(page, size): Observable<ApiResponse<Page<PlantResponse>>>
   - createPlant(request): Observable<ApiResponse<PlantResponse>>
   - updatePlant(id, request): Observable<ApiResponse<PlantResponse>>
   - archivePlant(id): Observable<ApiResponse<void>>
   - getPlant(id): Observable<ApiResponse<PlantResponse>>

4. components/plant-list/ — grid of PlantCardComponent, loading skeleton,
   empty state ("Add your first plant"), pagination controls

5. components/plant-card/ — card showing nickname, species (or "Unknown"),
   next care due badge, photo thumbnail, action menu

6. components/plant-form/ — reactive form, used for both create and edit,
   nickname required validation, date picker for acquiredAt

7. components/plant-detail/ — tabs: Overview, Care History, Identifications
```

---

### T1.8 — Auth module — Angular frontend 🤖 AI
**Branch:** `feature/PP-007-plant-module-frontend` (same branch)

**Claude Code prompt:**
```
// Phase 1 — Generate the Auth Angular feature module

1. features/auth/ with login and register pages
2. Reactive forms with real-time validation (email format, password min 8 chars,
   passwords match on register)
3. Complete core/services/auth.service.ts — store JWT in localStorage,
   decode expiry, auto-logout when token expires
4. Complete core/interceptors/jwt.interceptor.ts — attach token, handle 401
5. After successful login/register: navigate to /plants
6. Show loading spinner on form submit while API call is in progress
```

---

### T1.9 — Manual validation — Phase 1 👤 Manual
**Branch:** PR to `dev`

Checklist:
- [ ] `mvn clean verify` — all tests green, JaCoCo ≥ 80%
- [ ] `ng build --configuration production` — 0 errors
- [ ] Manual end-to-end: register → login → create plant → edit → archive
- [ ] Swagger UI (`/swagger-ui.html`) — all endpoints documented and testable
- [ ] Security: confirm unauthenticated requests to `/api/v1/plants` return 401
- [ ] Security: confirm user A cannot access user B's plants
- [ ] Rebase on `dev`, create PR using PR template, wait for green CI

---

## PHASE 2 — AI Plant Identification
> Goal: the user photographs a plant and gets an expert AI diagnosis + personalised care plan.
> Estimated time: Weeks 3–5.
>
> ⚠️ **Task order note (revised 2026-06-14):**
> T2.11 (DeepSeek) was moved to the top of the remaining queue.
> T2.8 was removed — it was Ollama-based care planning. T2.11 replaces it with DeepSeek
> from the start, so there is no wasted implementation.
> AI provider stack: PlantNet (identification) · DeepSeek (care plan + chat) · LLaVA (vision)
> Ollama phi3 has no remaining role and will be removed from the stack.

---

### T2.1 — Prepare AI environment 👤 Manual

- Get Anthropic API key from console.anthropic.com
- Add to `backend/.env`
- Decide photo storage for dev: local filesystem (`/tmp/plantpal/photos`)
- Create the folder and ensure the Spring Boot process can write to it

> 💡 **Suggestion for prod:** Use Cloudinary or AWS S3. Both give you CDN delivery,
> automatic image optimization, and you won't lose photos if the server restarts.
> The `FileStorageService` interface is already designed to swap implementations.

---

### T2.2 — ClaudeApiClient + FileStorageService 🤖 AI
**Branch:** `feature/PP-008-claude-api-client`

**Claude Code prompt:**
```
// Phase 2 — Implement ClaudeApiClient and FileStorageService

1. identification/client/ClaudeApiClient.java:
   - private static final String MODEL = "claude-sonnet-4-20250514"
   - MAX_TOKENS_IDENTIFICATION = 1024, MAX_TOKENS_CHAT = 2048
   - AnthropicClient built once in constructor from @Value("${anthropic.api.key}")
   - analyzePhoto(byte[] imageBytes, String mediaType) → String (raw JSON from Claude)
     Use the exact system prompt from CLAUDE.md identification section.
     Log start time and elapsed ms after response.
     Wrap SDK exceptions in PlantPalException with clear message.
   - askQuestion(String question, String gardenContext,
     List<ChatMessageDto> history) → String
     Use the exact system prompt from CLAUDE.md chat section.
   - Keep this class thin — no JSON parsing here.

2. shared/storage/FileStorageService.java (interface):
   - savePhoto(MultipartFile file) → String (returns URL/path)
   - deletePhoto(String url)

3. shared/storage/LocalFileStorageService.java:
   - @Profile("!prod") — only active outside prod
   - Save file as {UUID}.{extension} under app.storage.local-path
   - Validate: file not empty, type must be image/jpeg or image/png or image/webp,
     size max 10MB — throw ValidationException otherwise
   - Return relative URL like /photos/{uuid}.jpg

4. shared/config/StorageConfig.java:
   - Serve /photos/** as static resources from app.storage.local-path (dev only)

Unit test ClaudeApiClientTest — mock the AnthropicClient to avoid real API calls in tests.
```

---

### T2.3 — Identification module — full backend 🤖 AI
**Branch:** `feature/PP-009-identification-module`

**Claude Code prompt:**
```
// Phase 2 — Generate the full Identification module backend

In com.plantpal.identification:

1. entity/Identification.java (extends AuditableEntity):
   Fields: id, plantId (nullable Long — user may identify without linking to a plant),
   userId, photoUrl, rawResponse (String — JSONB stored as String in Java),
   species, commonName, confidence (Confidence enum), healthStatus (HealthStatus enum),
   healthNotes, careTips (String — JSONB), createdAt

2. Enums: Confidence (HIGH, MEDIUM, LOW), HealthStatus (HEALTHY, ISSUES_DETECTED, UNKNOWN)

3. DTOs:
   AnalyzePhotoRequest: plantId (optional Long)
   IdentificationResponse: all fields mapped from entity + plant nickname if linked
   CareTipsDto: wateringFrequencyDays, sunlight, fertilizingFrequencyDays, commonIssues
     (used to deserialize the careTips JSONB)

4. repository/IdentificationRepository.java:
   findByPlantIdOrderByCreatedAtDesc(Long plantId, Pageable pageable): Page<Identification>

5. service/IdentificationService.java (interface):
   @Async("aiTaskExecutor")
   CompletableFuture<IdentificationResponse> analyzePhoto(
       MultipartFile photo, Long plantId, Long userId)
   Page<IdentificationResponse> getPlantIdentifications(Long plantId, Long userId, Pageable pageable)

6. service/impl/IdentificationServiceImpl.java:
   Step 1: Validate file (delegate to FileStorageService)
   Step 2: Save photo, get URL
   Step 3: Call claudeApiClient.analyzePhoto() — log timing
   Step 4: Parse JSON response safely (ObjectMapper, handle malformed JSON gracefully
           with a fallback IdentificationResponse where confidence=LOW and health=UNKNOWN)
   Step 5: Save identification entity (store rawResponse for debugging)
   Step 6: If plantId provided AND belongs to userId, update plant.species and plant.commonName
   Step 7: Return mapped IdentificationResponse

7. controller/IdentificationController.java:
   POST /api/v1/identifications/analyze — multipart/form-data (photo + optional plantId)
     → returns 202 Accepted immediately with the CompletableFuture result
     (or wait for completion — choose whichever is simpler for MVP)
   GET  /api/v1/identifications/plant/{plantId} → Page<IdentificationResponse>

8. Unit tests: mock ClaudeApiClient, cover malformed JSON fallback, cover plantId linking.
```

> 💡 **Why store rawResponse:** You're paying for every Claude API call. Storing the raw
> JSON means you can reprocess past identifications if you improve your prompt, without
> re-charging the API. It's also invaluable for debugging when Claude returns unexpected output.

---

### T2.4 — Identification module — Angular frontend 🤝 Assisted
**Branch:** `feature/PP-010-identification-frontend`

**Claude Code prompt:**
```
// Phase 2 — Generate the Identification Angular feature module

In frontend/src/app/features/identification/:

1. models, service (HTTP calls to backend endpoints)

2. components/photo-upload/:
   - Drag & drop zone + click to browse
   - On mobile: <input type="file" accept="image/*" capture="environment">
     (this opens the camera directly on mobile — critical for UX)
   - Show image preview before submitting
   - Plant selector dropdown (optional — link to existing plant)

3. components/identification-result/:
   - Show: species name (large), common name, confidence badge (green/amber/red),
     health status badge, health notes, care tips cards
   - Button: "Save as new plant" / "Update existing plant"
   - If confidence=LOW, show a notice: "Low confidence — consider retaking in better light"

4. pages/identification-page/:
   - State machine: idle → uploading → analyzing → result → error
   - Show progress indicator during analysis (can take 5-15 seconds)
   - Friendly error messages (network error, no plant detected, etc.)
```

---

### T2.5 — Manual testing — identification core 👤 Manual
**Branch:** PR to `dev`

Real-world tests (use actual plants):
- [ ] Monstera — correct species identified?
- [ ] Something exotic — confidence is LOW, graceful fallback shown?
- [ ] Blurry photo — handled gracefully?
- [ ] Non-plant photo (hand, background) — sensible response?
- [ ] Plant with yellow leaves — "ISSUES_DETECTED" returned?
- [ ] Photo is linked to an existing plant — species updated?
- [ ] Check `identifications` table — `raw_response` column populated?

---

### T2.6 — DeepSeek client + dynamic care plan backend 🤖 AI  ← NEXT
**Branch:** `feature/PP-021-deepseek-care-plan`

> **Goal:** Build the DeepSeek-powered care plan. This is the AI foundation for the whole
> "care for your plants" experience. 
> both depend on it being real from day one. 

> **Before starting:** Add `DEEPSEEK_API_KEY=<your-key>` to `backend/.env`.
> Get a key at platform.deepseek.com if needed. Never commit the key.

**Backend Claude Code prompt:**
```
// T2.6 — DeepSeek client + dynamic care plan

1. identification/client/DeepSeekClient.java:
   - Uses Spring RestClient (NOT OllamaClient — different API shape)
   - Base URL: @Value("${deepseek.base-url:https://api.deepseek.com}")
   - API key: @Value("${deepseek.api-key}") passed as "Authorization: Bearer {key}" header
   - Model: @Value("${deepseek.model:deepseek-chat}")
   - Single method: generateCarePlan(String species, String commonName, String healthNotes) → String
   - POST /chat/completions with body:
     {
       "model": model,
       "messages": [
         {"role": "system", "content": CARE_PLAN_SYSTEM_PROMPT},
         {"role": "user", "content": "Plant: {species} ({commonName})\nHealth notes: {healthNotes}"}
       ],
       "temperature": 0.3,
       "response_format": {"type": "json_object"}
     }
   - Extract content from choices[0].message.content
   - On non-2xx or timeout: log ERROR, throw PlantPalException("Care plan service unavailable", 503)
   - Constructor injection only. Force HTTP/1.1 (same JdkClientHttpRequestFactory pattern as PlantNetClient).

2. System prompt for DeepSeek (static final String in DeepSeekClient):
   """
   You are an expert botanist and horticulturist helping a beginner gardener.
   Given a plant species, generate a complete, beginner-friendly care plan.
   Return ONLY valid JSON (no markdown). Structure:
   {
     "wateringFrequencyDays": <int — how often to water in summer>,
     "fertilizingFrequencyDays": <int — 0 means never>,
     "repottingFrequencyMonths": <int>,
     "careCards": [
       {
         "type": "WATERING | LIGHT | HUMIDITY | TEMPERATURE | FERTILIZING | REPOTTING | PRUNING | PEST | SEASONAL | BEGINNER_TIP",
         "title": "<short title>",
         "icon": "<material icon name, e.g. water_drop, wb_sunny, thermostat>",
         "summary": "<one sentence, e.g. Water every 7 days>",
         "detail": "<2-4 sentences, plain English, no jargon>",
         "urgency": "LOW | MEDIUM | HIGH",
         "seasonalVariation": "<what changes in winter/summer, or null>"
       }
     ],
     "beginnerWarnings": ["warning1", "warning2"]
   }
   Include 4-8 care cards covering the most important aspects for this specific plant.
   For rare/unusual care requirements, add extra cards. Omit irrelevant types.
   Write for someone who has never owned a plant before.
   """

3. New DTOs in identification/dto/:
   CareCardDto: String type, String title, String icon, String summary,
                String detail, String urgency, String seasonalVariation
   CarePlanDto: int wateringFrequencyDays, int fertilizingFrequencyDays,
                int repottingFrequencyMonths, List<CareCardDto> careCards,
                List<String> beginnerWarnings

4. Add carePlan (CarePlanDto) field to IdentificationResponse.

5. In IdentificationServiceImpl, after PlantNet returns:
   - Fire DeepSeekClient.generateCarePlan() as a parallel @Async call
   - Parse response into CarePlanDto (handle malformed JSON with safe default:
     one WATERING card: "Water when the top 2cm of soil feels dry")
   - Persist as JSONB in identifications.care_plan column
   - Join both async results before returning IdentificationResponse
     (if T2.9 annotation is also running, join all three in parallel)

6. Auto-create reminders when plant saved (wired in PlantService.saveFromIdentification):
   - WATERING: every wateringFrequencyDays
   - FERTILIZING: every fertilizingFrequencyDays (skip if 0)
   - REPOTTING: every repottingFrequencyMonths * 30 days
   - nextDueAt = now() + frequencyDays for all

7. Liquibase migration 008_add_care_plan.sql:
   ALTER TABLE identifications ADD COLUMN IF NOT EXISTS care_plan JSONB;

8. application-dev.yml additions:
   deepseek:
     base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
     api-key: ${DEEPSEEK_API_KEY}
     model: ${DEEPSEEK_MODEL:deepseek-chat}

9. Add DEEPSEEK_API_KEY to backend/.env.example with comment.

10. Add Bucket4j rate limit: 20 DeepSeek calls/hour/user.

Unit tests (mock DeepSeekClient):
- Valid JSON → CarePlanDto parsed, all fields mapped
- Malformed JSON → safe default returned, not an exception
- Service throws → safe default returned, error logged, not propagated
- careCards never null (always ≥ 1 card in fallback)
- Verify 3 reminders created (WATERING, FERTILIZING, REPOTTING) when fertilizingFrequencyDays > 0
- Verify FERTILIZING reminder skipped when fertilizingFrequencyDays = 0
```

---

### T2.7 — Dynamic care plan frontend 🤝 Assisted
**Branch:** `feature/PP-021-deepseek-care-plan` (same branch)

> **Design principle:** The frontend renders care cards generically — it does not know
> about specific plant types. Adding an orchid or a bonsai requires zero frontend changes.

**Frontend Claude Code prompt:**
```
// T2.7 — Dynamic care plan UI

In features/identification/:

1. Update identification.model.ts:
   Add CareCardDto and CarePlanDto interfaces matching the backend DTOs.
   CareCardType = 'WATERING' | 'LIGHT' | 'HUMIDITY' | 'TEMPERATURE' |
     'FERTILIZING' | 'REPOTTING' | 'PRUNING' | 'PEST' | 'SEASONAL' | 'BEGINNER_TIP'

2. components/care-plan/care-card.component.ts + .html + .scss:
   @Input() card: CareCardDto
   - Icon from card.icon (Angular Material icon)
   - Color map (TypeScript Record<CareCardType, string>) — never a switch/case:
       WATERING → blue, LIGHT → amber, TEMPERATURE → orange,
       PEST → red, BEGINNER_TIP → green, SEASONAL → purple, default → grey
   - Urgency border: HIGH → warn accent, MEDIUM → warn-light, LOW → no border
   - Click to expand card.detail + card.seasonalVariation (if present)
   - "Seasonal tip" chip inside expanded view when seasonalVariation is set

3. components/care-plan/care-plan.component.ts + .html + .scss:
   @Input() carePlan: CarePlanDto | null
   - null → show 3 grey skeleton placeholder cards while loading
   - Summary bar: "Water every X days · Fertilize every Y days · Repot every Z months"
   - Responsive card grid: 2 cols tablet, 1 col mobile
   - "Beginner warnings" section below: yellow alert chip per warning

4. Wire into identification-result: show CarePlanComponent below species/health
   - Skeleton loader while carePlan is null (async from DeepSeek)

5. Wire into plant-detail "Care Plan" tab:
   - Fetch latest identification → show its carePlan
   - No identification yet: "Take a photo to get your care plan" CTA

6. Wire into PreviewCardComponent (T2.8):
   - Show first 3 care cards as quick preview before saving
```

---

### T2.8 — One-click validate & save flow 🤝 Assisted
**Branch:** `feature/PP-018-one-click-save`

> **Depends on T2.6 being merged** — the save flow triggers reminder creation
> from the care plan that DeepSeek already generated during identification.

> **Goal:** After AI processes a photo, the user sees a fully pre-filled plant profile card.
> One click on "Save to garden" creates the plant and links the identification.
> No manual form filling required.

**UX flow:**
```
Take photo → Processing spinner → Preview card (species + care plan preview) → [Save to garden] / [Edit] / [Discard]
```

**Backend Claude Code prompt:**
```
// T2.8 — Add one-click save endpoint

1. New DTO: SaveIdentificationAsPlantRequest:
   - identificationId (Long, required)
   - nickname (String, optional — defaults to common_name if blank)
   - location (String, optional)

2. New service method in PlantService (interface + impl):
   PlantResponse saveFromIdentification(SaveIdentificationAsPlantRequest request, Long userId)
   - Load Identification by id, verify userId matches
   - Create Plant from identification fields:
       nickname = request.nickname ?? identification.commonName ?? identification.species ?? "My Plant"
       species = identification.species
       commonName = identification.commonName
       photoUrl = identification.photoUrl
       status = ACTIVE
   - Save plant, link identification.plantId = plant.id
   - Trigger reminder auto-creation from identification.carePlan (async, see T2.11)
   - Return PlantResponse

3. New endpoint in PlantController:
   POST /api/v1/plants/from-identification
   → 201 Created with PlantResponse

4. Unit test: cover nickname fallback chain, verify identification.plantId is updated.
```

**Frontend Claude Code prompt:**
```
// T2.8 — One-click validate & save UI

In features/identification/:

1. After identification result arrives, show PreviewCardComponent:
   - Plant photo (full width, with annotation overlay from T2.6 if available)
   - Species name (large), common name (subtitle)
   - Confidence badge (green/amber/red)
   - Health status badge with icon
   - First 3 care cards from carePlan (via CarePlanComponent @Input with subset)
   - Optional nickname input (pre-filled with common_name)
   - Optional location input (e.g. "balcony", "living room window")

2. Three action buttons:
   [Save to garden] → POST /api/v1/plants/from-identification → navigate to /plants/{id}
   [Edit before saving] → open plant-form pre-filled with identification data
   [Discard] → back to photo upload

3. Loading state on [Save to garden] — disable buttons while saving.
4. On success: toast "Added to your garden!" then navigate to plant detail.
```

---

### T2.9 — Visual plant annotation (bounding boxes + disease overlay) 🤖 AI
**Branch:** `feature/PP-017-visual-annotation`

> **Depends on T2.6 being merged** (uses the same parallel async pattern in IdentificationServiceImpl).
>
> **AI provider note:** LLaVA (Ollama) is used here for vision/bounding boxes only —
> different use case from care planning (DeepSeek). LLaVA is free in dev with no API key.
> For prod, swap to DeepSeek VL (`deepseek-vl`) via the same DeepSeekClient
> by adding an `analyzeRegions(byte[] image)` method with the VL model.

> **Goal:** After a photo is processed, overlay named rectangles on identified plants
> and highlight areas with detected disease or stress (yellowing, spots, wilting).

**Backend Claude Code prompt:**
```
// T2.9 — Add visual annotation support to the identification pipeline

In com.plantpal.identification:

1. Extend OllamaClient with a new method:
   analyzeRegions(byte[] imageBytes, String mediaType) → String (raw JSON)
   - POST to Ollama /api/generate with model=llava
   - Encode image as base64, pass in "images" field
   - System prompt returns plant regions + disease areas as structured JSON:
     {
       "regions": [
         { "label": "Monstera deliciosa", "type": "PLANT", "confidence": "HIGH",
           "boundingBox": { "xPct": 10, "yPct": 5, "widthPct": 80, "heightPct": 70 } },
         { "label": "Yellowing — possible overwatering", "type": "DISEASE", "confidence": "MEDIUM",
           "boundingBox": { "xPct": 30, "yPct": 60, "widthPct": 20, "heightPct": 15 } }
       ]
     }
   - Ollama unavailable or malformed: return empty regions list, log WARN (never crash).

2. AnnotationRegionDto: String label, String type (PLANT/DISEASE/HEALTHY_AREA),
   String confidence, BoundingBoxDto (xPct, yPct, widthPct, heightPct)

3. Add annotationRegions (List<AnnotationRegionDto>) to IdentificationResponse.

4. In IdentificationServiceImpl: fire analyzeRegions() as parallel @Async call
   alongside DeepSeekClient.generateCarePlan(). Join all three results together.

5. Liquibase migration 007_add_annotation_regions.sql:
   ALTER TABLE identifications ADD COLUMN IF NOT EXISTS annotation_regions JSONB;

Unit test: mock OllamaClient.analyzeRegions(), verify empty list on malformed JSON.
```

**Frontend Claude Code prompt:**
```
// T2.9 — Visual annotation overlay

In features/identification/components/photo-annotator/:
1. PhotoAnnotatorComponent [@Input imageUrl, @Input regions]
   - <canvas> overlay sized to match the photo
   - PLANT → blue rectangle + label
   - DISEASE → red/orange semi-transparent rectangle + label
   - HEALTHY_AREA → green semi-transparent rectangle + label
   - regions empty/null → plain <img>, no canvas
2. "Show/Hide annotations" toggle above photo
3. Plug into identification-result, replacing the plain <img>
```

---

### T2.9a — Polygon annotation — backend 🤖 AI
**Branch:** `feature/PP-023-enhanced-annotation-backend`

> **Depends on T2.9 merged.**
> Migration 007 already exists as JSONB — no structural migration change needed.
> JSONB accepts any JSON shape, so switching to polygons is a pure code change.

**Goal:** Replace per-region bounding boxes with polygon point arrays so the canvas
can trace the actual leaf/area shape instead of a rectangle.

**Backend Claude Code prompt:**
```
// T2.9a — Switch annotation regions from bounding boxes to polygon points

In com.plantpal.identification:

1. New PolygonPointDto: int xPct, int yPct (both 0-100)
   @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
   No @JsonProperty needed — xPct/yPct are lowercase so Lombok generates getXPct()/getYPct()
   → Jackson decapitalize("XPct") = "XPct" (two consecutive uppercase). Add @JsonProperty("xPct")
   and @JsonProperty("yPct") same as BoundingBoxDto.

2. Update AnnotationRegionDto:
   - Add field: List<PolygonPointDto> polygon  (nullable — AI may not always return it)
   - Keep BoundingBoxDto boundingBox as nullable fallback for old DB records
   - Remove the @NonNull / required constraint from boundingBox if present

3. Update DeepSeekClient.ANNOTATION_SYSTEM_PROMPT:
   Replace the boundingBox block with:
   {
     "regions": [
       {
         "label": "<specific description>",
         "type": "PLANT | DISEASE | HEALTHY_AREA",
         "confidence": "HIGH | MEDIUM | LOW",
         "polygon": [
           { "xPct": <0-100>, "yPct": <0-100> },
           ... (8-16 points tracing the region boundary, min 4)
         ]
       }
     ]
   }
   Rules to add:
   - Polygon points must trace the boundary clockwise.
   - First and last point need NOT be identical (canvas will close the path).
   - All xPct/yPct must be integers 0-100 inclusive.
   - If the region shape is simple (whole plant body), 4 corner points are enough.
   - For complex disease areas (irregular spots), use 8-16 points.

4. In parseAnnotationRegions() (IdentificationServiceImpl or wherever it lives):
   - After parsing, validate each region: if polygon is non-null but has < 3 points,
     set polygon to null (degenerate polygon, canvas cannot draw it).
   - If polygon is null AND boundingBox is non-null: leave both as-is (legacy fallback).
   - If both are null: the region is still valid (no overlay drawn for it).

5. Update unit tests in IdentificationServiceImplTest:
   - validAnnotationJson() helper: switch to polygon format (8 points per region)
   - Keep one legacy bounding-box test verifying fallback path still works
   - Verify degenerate polygon (2 points) is cleared to null after parse

No new migration needed — annotation_regions JSONB column from T2.9 stores any shape.
```

---

### T2.9b — Polygon annotation — frontend 🤖 AI
**Branch:** `feature/PP-023-enhanced-annotation-backend` (same branch, frontend half)

> **Depends on T2.9a backend merged and deployed.**
> Backend now sends `polygon` (list of points) instead of `boundingBox`.
> Falls back to bounding box rect if `polygon` is null.

**Frontend Claude Code prompt:**
```
// T2.9b — Switch PhotoAnnotatorComponent from rect to polygon path rendering

In features/identification/components/photo-annotator/photo-annotator.component.ts:

1. Update AnnotationRegion model (identification.model.ts):
   - Add interface PolygonPoint { xPct: number; yPct: number; }
   - Add field polygon?: PolygonPoint[] to AnnotationRegion
   - Keep boundingBox?: AnnotationBoundingBox (nullable fallback for old records)

2. In PhotoAnnotatorComponent.drawAnnotations(), replace the rect drawing with:

   For each region:
   a. If region.polygon exists and has >= 3 points:
      const points = region.polygon.map(p => ({
        x: (p.xPct / 100) * w,
        y: (p.yPct / 100) * h,
      }));
      ctx.beginPath();
      ctx.moveTo(points[0].x, points[0].y);
      for (let i = 1; i < points.length; i++) {
        ctx.lineTo(points[i].x, points[i].y);
      }
      ctx.closePath();
      ctx.fillStyle = colors.fill;
      ctx.fill();
      ctx.strokeStyle = colors.stroke;
      ctx.lineWidth = 2;
      ctx.stroke();
      // Label: position at centroid of polygon points
      const cx = points.reduce((s, p) => s + p.x, 0) / points.length;
      const cy = points.reduce((s, p) => s + p.y, 0) / points.length;
      drawLabel(ctx, label, cx, cy - 10, colors.stroke);

   b. Else if region.boundingBox exists (fallback):
      // old rect logic — unchanged

   c. Else: skip (no overlay for this region)

3. Extract drawLabel(ctx, text, x, y, color) as a private method to avoid duplication
   between polygon centroid labels and bounding-box pill labels.

4. No module changes — PhotoAnnotatorComponent is already in CarePlanModule.
   No route changes needed.
```

---

### T2.9c — Disease detail panel + annotation list 🤖 AI
**Branch:** `feature/PP-024-disease-panel`

> **Depends on T2.9b (polygon canvas) AND T2.9d (cure-advice endpoint).**
> Frontend-only task (annotation panel + wiring to cure-advice API call).

**Goal:** Interactive annotation experience — click a region in the list to highlight it
on the canvas and see a disease detail panel with cure advice.

**Frontend Claude Code prompt:**
```
// T2.9c — Annotation list panel + disease detail panel

In features/identification/components/:

1. AnnotationListComponent (new, declared in CarePlanModule)
   @Input regions: AnnotationRegion[]
   @Input species: string | null
   @Input identificationId: number
   @Output regionSelected = new EventEmitter<number | null>()  // emits region index

   Template:
   - List of mat-list-item per region:
     [color dot] [label] [confidence badge] [type badge]
     Clicking → emits regionSelected with the index
   - If no regions: show "No regions detected"

2. DiseaseDetailPanelComponent (new, declared in CarePlanModule)
   @Input region: AnnotationRegion | null
   @Input species: string | null
   @Input identificationId: number
   Outputs: none (calls API internally)

   Shows (only when region?.type === 'DISEASE'):
   - Region label (h3)
   - Confidence badge
   - "Ask for cure" mat-button → calls IdentificationService.getCureAdvice()
     - Loading: mat-spinner
     - Success: show advice text in <mat-card>
     - Error: show "Could not load advice, please try again"
   - "Add to care plan" mat-button [disabled] matTooltip="Available after saving plant"

   Hides entirely when region is null or type is not DISEASE.

3. Update PhotoAnnotatorComponent:
   @Input selectedRegionIndex: number | null = null
   In drawAnnotations(): when selectedRegionIndex is set, draw all OTHER regions at 20%
   fill opacity (override colors.fill alpha to 0.04) and grey stroke (#ccc),
   draw the selected region at full opacity + 3px stroke.

4. Wire everything in plant-detail "Last Scan" tab AND identification-page:
   - Add <app-annotation-list> and <app-disease-detail-panel> below <app-photo-annotator>
   - On (regionSelected): update selectedRegionIndex on PhotoAnnotatorComponent
     and pass selected region to DiseaseDetailPanelComponent
   - Use takeUntil(this.destroy$) for any subscriptions

5. IdentificationService: add method
   getCureAdvice(identificationId: number, regionLabel: string, species: string): Observable<string>
   POST /api/v1/identifications/{id}/cure-advice
   Body: { regionLabel, species }
   Returns: response.data.advice

No new module/routing changes needed.
```

---

### T2.9d — Cure-advice endpoint — backend 🤖 AI
**Branch:** `feature/PP-023-enhanced-annotation-backend` (same as T2.9a — same agent, same session)

> **Depends on T2.9 merged.**
> Can be implemented in the SAME backend session as T2.9a (different class, no conflicts).

**Goal:** New endpoint that generates a short, step-by-step cure procedure for a detected
disease region, using DeepSeek-R1 via GitHub Models.

**Backend Claude Code prompt:**
```
// T2.9d — POST /api/v1/identifications/{id}/cure-advice

In com.plantpal.identification:

1. New DTOs:
   CureAdviceRequest: @NotBlank String regionLabel, String species
   CureAdviceResponse: String advice

2. New method in DeepSeekClient:
   public String generateCureAdvice(String species, String regionLabel)
   - Uses model (DeepSeek-R1, text model)
   - System prompt:
     "You are a plant pathologist. Answer in plain English for a beginner gardener.
      Be direct and practical. Do NOT use markdown. No headers, no bullet symbols — write
      numbered steps as plain text: '1. Remove affected leaves. 2. Apply neem oil...'"
   - User message: "My {species} has the following issue: {regionLabel}. Provide a
     concise cure procedure in 3-5 numbered steps."
   - temperature: 0.3, NO response_format json_object (plain text response)
   - Apply stripThinkTags() before returning (R1 wraps reasoning in <think> blocks)
   - Timeout: same 5-minute factory (inherited from constructor)
   - Error: throw PlantPalException("Cure advice unavailable", 503)

3. New method in IdentificationService interface + IdentificationServiceImpl:
   @Async("aiTaskExecutor")
   CompletableFuture<CureAdviceResponse> getCureAdvice(Long id, CureAdviceRequest req, Long userId)
   - Load identification by id, verify ownership (userId match) — throw ResourceNotFoundException if not found
   - consumeRateLimit(userId) — use a SEPARATE Bucket (cureAdviceBuckets) with 10 calls/hour
     (distinct from the 20/hour identification bucket — cure advice is lighter but separate quota)
   - Call deepSeekClient.generateCureAdvice(req.getSpecies(), req.getRegionLabel())
   - Return CompletableFuture.completedFuture(new CureAdviceResponse(advice))

4. New endpoint in IdentificationController:
   POST /api/v1/identifications/{id}/cure-advice
   @RequestBody @Valid CureAdviceRequest req
   → ResponseEntity<ApiResponse<CureAdviceResponse>> (202 Accepted, async)
   Authentication: same @AuthenticationPrincipal pattern as other endpoints

5. Unit tests (IdentificationServiceImplTest — new nested class CureAdvice):
   - Happy path: deepSeekClient returns advice text → response contains advice
   - Rate limited: returns 429 PlantPalException
   - Identification not owned by user: throws ResourceNotFoundException
   - DeepSeek throws: PlantPalException 503 propagated
```

---

---

### T2.A — GitHubModelsClient refactor — split vision from text client (Backend) 🤖 AI
**Branch:** `feature/PP-025-github-models-client`
**Depends on:** AddChooseAi merged to dev

> **Why:** DeepSeekClient currently mixes vision tasks (gpt-4o) and text tasks (DeepSeek-R1)
> in a single Spring bean. This creates rate-limit confusion and prevents optimising each
> model independently. After this split: GitHubModelsClient owns all image analysis;
> DeepSeekClient owns text-only tasks (care plan, cure advice).

**Backend Claude Code prompt:**
```
// T2.A — Split DeepSeekClient into GitHubModelsClient (vision) + DeepSeekClient (text)

Read BACKEND.md, STATE.md, DeepSeekClient.java, DeepSeekAnnotationClient.java,
IdentificationServiceImpl.java, and OllamaClient.java before starting.

CONTEXT: DeepSeekClient currently has 4 methods:
  identifyPlant()     → uses deepseek.vision-model (gpt-4o)
  analyzeRegions()    → uses deepseek.vision-model (gpt-4o)
  generateCarePlan()  → uses deepseek.model (DeepSeek-R1)
  generateCureAdvice()→ uses deepseek.model (DeepSeek-R1)

GOAL: Move vision methods to new GitHubModelsClient; keep text methods in DeepSeekClient.

1. Create identification/client/GitHubModelsClient.java:
   Constructor @Value params:
     @Value("${github.base-url:https://models.inference.ai.azure.com}") String baseUrl
     @Value("${github.token}") String token
     @Value("${github.models.identification-model:gpt-4o}") String identificationModel
     @Value("${github.models.annotation-model:gpt-4o-mini}") String annotationModel
   - Same JdkClientHttpRequestFactory + Duration.ofMinutes(5) read timeout as existing DeepSeekClient
   - Authorization: "Bearer " + token (same header pattern)
   - Move PLANT_IDENTIFICATION_SYSTEM_PROMPT (package-private static) from DeepSeekClient here
   - Move ANNOTATION_SYSTEM_PROMPT (package-private static) from DeepSeekClient here
   - Move identifyPlant(byte[], String) — unchanged logic, use identificationModel
   - Move analyzeRegions(byte[], String) — unchanged logic, use annotationModel
   - Both methods call DeepSeekClient.stripThinkTags() for fence/think stripping
     (it stays in DeepSeekClient as package-private static — do not move it)

2. Update DeepSeekClient.java:
   - Remove visionModel field and identifyPlant()/analyzeRegions() methods
   - Remove PLANT_IDENTIFICATION_SYSTEM_PROMPT and ANNOTATION_SYSTEM_PROMPT constants
   - Change @Value("${deepseek.api-key}") → @Value("${github.token}")
   - Change constructor params to:
     @Value("${github.base-url:https://models.inference.ai.azure.com}") String baseUrl
     @Value("${github.token}") String token
     @Value("${deepseek.model:DeepSeek-R1}") String model
   - Keep: CARE_PLAN_SYSTEM_PROMPT, CURE_ADVICE_SYSTEM_PROMPT
   - Keep: generateCarePlan(), generateCureAdvice(), stripThinkTags() (static)

3. Update DeepSeekAnnotationClient.java:
   - Replace DeepSeekClient injection with GitHubModelsClient for analyzeRegions() call
   - Constructor: GitHubModelsClient gitHubModelsClient, OllamaClient ollamaClient
   - All retry/fallback logic unchanged; just call gitHubModelsClient.analyzeRegions()

4. Update IdentificationServiceImpl.java:
   - Add GitHubModelsClient to constructor (9th param)
   - Replace deepSeekClient.identifyPlant() with gitHubModelsClient.identifyPlant()
   - In runIdentification() switch:
     case DEEPSEEK → gitHubModelsClient.identifyPlant(imageBytes, mediaType)
     case GITHUB_GPT4O → gitHubModelsClient.identifyPlant(imageBytes, mediaType)
     case OLLAMA_LLAVA → (unchanged, with DeepSeek fallback as before)
     case PLANTNET → (unchanged)
   - Keep deepSeekClient for generateCarePlan() parallel call

5. Update OllamaClient.java:
   - Change DeepSeekClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT →
     GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT
   - Change DeepSeekClient.ANNOTATION_SYSTEM_PROMPT →
     GitHubModelsClient.ANNOTATION_SYSTEM_PROMPT
   - Keep calling DeepSeekClient.stripThinkTags() (it stays there)

6. application-dev.yml — replace deepseek.vision-model and deepseek.api-key with:
   github:
     base-url: ${GITHUB_BASE_URL:https://models.inference.ai.azure.com}
     token: ${GITHUB_TOKEN}
     models:
       identification-model: ${GITHUB_IDENTIFICATION_MODEL:gpt-4o}
       annotation-model: ${GITHUB_ANNOTATION_MODEL:gpt-4o-mini}

7. backend/.env.example:
   - Remove DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL, DEEPSEEK_VISION_MODEL
   - Add GITHUB_TOKEN=<github-pat-with-models-read-scope>
   - Add GITHUB_BASE_URL=https://models.inference.ai.azure.com
   - Add GITHUB_IDENTIFICATION_MODEL=gpt-4o
   - Add GITHUB_ANNOTATION_MODEL=gpt-4o-mini
   - Keep DEEPSEEK_MODEL=DeepSeek-R1 (still used for text)

8. Update unit tests:
   - Any test mocking DeepSeekClient.identifyPlant() → mock GitHubModelsClient.identifyPlant()
   - IdentificationServiceImplTest constructor call needs GitHubModelsClient @Mock added

Run mvn compile after step 2 and after step 4 to catch injection errors early.
```

**Verify:** `mvn test` passes. POST /analyze returns species in response. Logs show "GitHubModelsClient" for identification and "gpt-4o-mini" for annotation.

---

### T2.B — Add GITHUB_GPT4O to AI model preferences (Both) 🤖 AI
**Branch:** `feature/PP-025-github-models-client` (same branch)
**Depends on:** T2.A (GitHubModelsClient must exist)

**Backend Claude Code prompt:**
```
// T2.B (backend) — Add GITHUB_GPT4O to AiModelPreference enum

Read user/entity/AiModelPreference.java and IdentificationServiceImpl.java.

1. Add GITHUB_GPT4O to AiModelPreference enum:
   public enum AiModelPreference { DEEPSEEK, PLANTNET, OLLAMA_LLAVA, GITHUB_GPT4O }

2. In IdentificationServiceImpl.runIdentification(), ensure GITHUB_GPT4O is an
   explicit case (not just falling through to default):
   case GITHUB_GPT4O -> gitHubModelsClient.identifyPlant(imageBytes, mediaType);

No DB migration needed — users.ai_model_preference is VARCHAR(50), 'GITHUB_GPT4O' fits.
No DTO change needed — UserPreferencesResponse already serialises enum as String.
```

**Frontend Claude Code prompt:**
```
// T2.B (frontend) — GITHUB_GPT4O toggle + rate-limit tooltips in ModelSelectorComponent

Read FRONTEND.md, shared/components/model-selector/model-selector.component.ts.

1. core/models/user.model.ts — add 'GITHUB_GPT4O' to AiModelPreference type:
   export type AiModelPreference = 'DEEPSEEK' | 'PLANTNET' | 'OLLAMA_LLAVA' | 'GITHUB_GPT4O';

2. model-selector.component.html — add fourth mat-button-toggle:
   value="GITHUB_GPT4O", icon: smart_toy, label: "GPT-4o"
   Position: between DEEPSEEK and OLLAMA_LLAVA (most capable to most local left→right)

3. Add matTooltip warnings on rate-limited options (import MatTooltipModule into SharedModule):
   GITHUB_GPT4O: matTooltip="~50 vision calls/day on free tier" [matTooltipShowDelay]="300"
   DEEPSEEK:     matTooltip="~20 identification calls/hour"     [matTooltipShowDelay]="300"
   OLLAMA_LLAVA: matTooltip="Fully local — no API quota"        [matTooltipShowDelay]="300"
   PLANTNET:     matTooltip="Species only — no health analysis" [matTooltipShowDelay]="300"

No service changes — UserService.updatePreferences() already accepts any string value.
Use takeUntil(this.destroy$) on any new subscriptions.
```

**Verify:** Model selector shows 4 toggles. Selecting GITHUB_GPT4O persists to DB. Tooltips appear on hover.

---

### T2.C — Kafka async identification pipeline (Backend) 🤖 AI
**Branch:** `feature/PP-026-kafka-async`
**Depends on:** AddChooseAi merged (AiModelPreference.GITHUB_GPT4O in DB), T2.A+T2.B merged

> **Why:** POST /analyze currently blocks the HTTP thread for 5-15 seconds while AI calls run.
> CompletableFuture.get() on the HTTP thread exhausts the thread pool under load.
> Kafka decouples HTTP acceptance from AI processing: return 202 immediately, process async.

**Backend Claude Code prompt:**
```
// T2.C — Kafka async pipeline for plant identification

Read BACKEND.md, STATE.md, IdentificationServiceImpl.java, IdentificationController.java,
and docker-compose.yml before starting.

MAVEN DEPENDENCY — add to pom.xml:
  <dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
  </dependency>

APPLICATION-DEV.YML additions:
  spring:
    kafka:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:29092}
      consumer:
        group-id: plantpal-identification
        auto-offset-reset: earliest
        key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
        value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
        properties:
          spring.json.trusted.packages: "com.plantpal.*"
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

DOCKER-COMPOSE.YML — add after the redis service:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    ports: ["2181:2181"]
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]
    ports: ["29092:29092"]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

NEW EVENT CLASSES in identification/event/:

1. IdentificationRequestedEvent.java:
   @Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
   Fields: Long identificationId, Long userId, String photoUrl,
           String aiModelPreference, List<String> organs, Instant requestedAt

2. IdentificationCompletedEvent.java:
   @Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
   Fields: Long identificationId, String status, Instant completedAt

KAFKA TOPIC CONFIG in identification/config/KafkaTopicConfig.java (@Configuration):
3. @Bean NewTopic identificationRequestedTopic() →
     TopicBuilder.name("identification.requested").partitions(3).replicas(1).build()
   @Bean NewTopic identificationCompletedTopic() →
     TopicBuilder.name("identification.completed").partitions(3).replicas(1).build()

KAFKA TEMPLATE BEAN in shared/config/KafkaConfig.java (@Configuration):
4. @Bean KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf)
   (Spring Boot auto-configures ProducerFactory — just expose the template)

REFACTOR IdentificationServiceImpl — SPLIT identify() INTO TWO METHODS:
5. Rename current identify() → processIdentification(IdentificationRequestedEvent event):
   - @Async("aiTaskExecutor")
   - Load the Identification entity from DB by event.identificationId
   - Load user AiModelPreference from userRepository
   - Run existing parallel AI logic (runIdentification + visionAnnotationClient.analyzeRegions)
   - On completion: entity.status=COMPLETED, save, publish IdentificationCompletedEvent
   - On error: entity.status=FAILED, save, log ERROR, publish IdentificationCompletedEvent(FAILED)
   - Do NOT re-run rate-limit check here (it was already checked in submitIdentification)

6. New method submitIdentification(List<MultipartFile> images, Long plantId, Long userId,
                                    List<String> organs):
   → CompletableFuture<IdentificationPendingResponse>
   - Validate files (unchanged)
   - Save photo to storage (unchanged)
   - Persist Identification with status=PENDING (no AI call yet)
   - Rate-limit check stays here (before publishing) — throws 429 if exceeded
   - Publish IdentificationRequestedEvent to "identification.requested"
     (include aiModelPreference from loadUserPreference(userId))
   - Return immediately: new IdentificationPendingResponse(id, "PENDING")

NEW DTO: dto/IdentificationPendingResponse.java:
   Long identificationId, String status

NEW CONSUMER: identification/consumer/IdentificationConsumer.java (@Component):
7. @KafkaListener(topics = "identification.requested", groupId = "plantpal-identification",
                  containerFactory = "kafkaListenerContainerFactory")
   public void onIdentificationRequested(IdentificationRequestedEvent event) {
     identificationService.processIdentification(event); // delegates to service
   }

UPDATE IdentificationController:
8. POST /api/v1/identifications/analyze:
   - Call submitIdentification() → ResponseEntity.accepted()
     .body(ApiResponse.success(pendingResponse, "Analysis started — poll for result"))
   - Remove the old join/wait pattern
   - Return type: ResponseEntity<ApiResponse<IdentificationPendingResponse>>

9. Ensure GET /api/v1/identifications/{id} exists and returns IdentificationResponse
   with status field. Frontend polls this until status != "PENDING".

10. Update Identification entity and IdentificationResponse to include
    String status (PENDING / COMPLETED / FAILED) if not already present.

BACKEND/.ENV.EXAMPLE additions:
  KAFKA_BOOTSTRAP_SERVERS=localhost:29092

UNIT TESTS — new nested class Kafka in IdentificationServiceImplTest:
- submitIdentification: verify Identification persisted as PENDING,
  verify kafkaTemplate.send("identification.requested", event) called with correct identificationId
- processIdentification happy path: verify entity status set to COMPLETED, annotationRegions saved
- processIdentification AI failure: verify entity status set to FAILED, no exception propagated
Mock KafkaTemplate<String, Object> with @Mock; verify send() with any(String.class), any().
```

**Verify:** `docker-compose up -d` includes Kafka. POST /analyze returns 202 with identificationId. GET /{id} initially shows PENDING, then COMPLETED after ~10-15s. Consumer logs show "Processing identification event".

---

### T2.D — Kafka polling — identification frontend (Frontend) 🤖 AI
**Branch:** `feature/PP-026-kafka-async` (same branch)
**Depends on:** T2.C backend merged and deployed locally

**Frontend Claude Code prompt:**
```
// T2.D — Frontend polling for async identification result

Read FRONTEND.md, STATE.md, identification-page.component.ts, identification.service.ts,
and identification.model.ts before starting.

CONTEXT: POST /analyze now returns 202 immediately:
  { data: { identificationId: number, status: "PENDING" }, message: "Analysis started" }
The frontend must poll GET /api/v1/identifications/{id} every 3 seconds.

1. identification.model.ts:
   Add: export interface IdentificationPendingResponse { identificationId: number; status: string; }
   Add status: string field to IdentificationResponse.

2. identification.service.ts:
   - Change analyze() return type to Observable<ApiResponse<IdentificationPendingResponse>>
   - Add: getById(id: number): Observable<ApiResponse<IdentificationResponse>>
     GET /api/v1/identifications/{id}
   - Add: pollUntilComplete(id: number): Observable<IdentificationResponse>
     Implementation:
       return interval(3000).pipe(
         startWith(0),
         switchMap(() => this.getById(id)),
         map(r => r.data),
         takeWhile(result => result.status === 'PENDING', /* inclusive */ true),
         filter(result => result.status !== 'PENDING'),
         take(1),
         timeout(32000),  // 32s total (10 polls × 3s + margin)
       );
     On timeout or FAILED status in the filter step, the subscriber's error handler fires.

3. identification-page.component.ts — extend the state machine:
   Current states: idle | analyzing | preview | error
   Add state: 'pending' (between analyzing and preview)
   
   type PageState = 'idle' | 'analyzing' | 'pending' | 'preview' | 'error';
   pendingIdentificationId: number | null = null;
   
   onPhotoSubmit():
     this.state = 'analyzing';
     this.identificationService.analyze(formData).subscribe({
       next: r => {
         this.pendingIdentificationId = r.data.identificationId;
         this.state = 'pending';
         this.startPolling(r.data.identificationId);
       },
       error: _ => this.state = 'error'
     });
   
   startPolling(id: number):
     this.identificationService.pollUntilComplete(id)
       .pipe(takeUntil(this.destroy$))
       .subscribe({
         next: result => { this.result = result; this.state = 'preview'; },
         error: _ => { this.state = 'error'; this.errorMessage = 'Analysis timed out — please try again.'; }
       });
   
   In the template:
   - state === 'analyzing': spinner + "Uploading photo..."
   - state === 'pending': spinner + "Analysing your plant… (usually 10–20 seconds)"
     Show a subtle progress indicator (indeterminate mat-progress-bar)
   - state === 'preview': existing PreviewCardComponent
   - state === 'error': show this.errorMessage

4. In ngOnDestroy(), the takeUntil(this.destroy$) stops the poll automatically.
   No explicit cleanup needed beyond what is already in the component.

No new modules, routes, or services beyond these changes.
```

**Verify:** Upload a photo → spinner shows "Analysing your plant…" → result appears after ~15s. Cancel/navigate away → polling stops (no console errors). If backend is slow (>32s), error message appears.

---

### T2.E — Redis photo storage + SHA-256 deduplication (Backend) 🤖 AI
**Branch:** `feature/PP-027-redis-photo-storage`
**Depends on:** None (independent — can start after AddChooseAi merged)

> **Why:** Photos currently live only on /tmp disk, which is ephemeral in containers.
> Redis-backed storage gives persistence, instant CDN-like serving from memory, and
> deduplication prevents storing the same photo twice.

**Backend Claude Code prompt:**
```
// T2.E — Redis photo storage with SHA-256 deduplication

Read BACKEND.md, STATE.md, LocalFileStorageService.java, CacheConfig.java,
and SecurityConfig.java before starting.

1. Extend FileStorageService interface (shared/storage/FileStorageService.java):
   Add method: byte[] loadPhotoBytes(String photoUrl)
   (Existing savePhoto() and deletePhoto() signatures unchanged)

2. RedisTemplate<String, byte[]> bean — add to CacheConfig.java:
   @Bean
   public RedisTemplate<String, byte[]> byteRedisTemplate(RedisConnectionFactory factory) {
     RedisTemplate<String, byte[]> tpl = new RedisTemplate<>();
     tpl.setConnectionFactory(factory);
     tpl.setKeySerializer(new StringRedisSerializer());
     tpl.setValueSerializer(new ByteArrayRedisSerializer());
     return tpl;
   }
   (Do NOT add @Primary — the default RedisTemplate<Object,Object> for caching must not be replaced)

3. Update LocalFileStorageService.java:
   Inject: RedisTemplate<String, byte[]> byteRedisTemplate
   
   In savePhoto(MultipartFile file) — add deduplication before saving:
   a. Read file bytes: byte[] fileBytes = file.getBytes()
   b. Compute hash: String hash = DigestUtils.sha256Hex(fileBytes)
      (org.apache.commons.codec — already transitive via spring-boot-starter)
   c. Check Redis: String existing = (String) byteRedisTemplate.opsForValue()
                   .get("photo:hash:" + hash) — hmm, this needs a String template for the hash key
      Better: Use StringRedisTemplate for hash→url mapping (inject as second dependency)
      @Value-inject or use byteRedisTemplate with manual string conversion:
      Actually: store the hash→URL mapping in a separate StringRedisTemplate:
      Inject: StringRedisTemplate stringRedisTemplate
      
      Check: String existingUrl = stringRedisTemplate.opsForValue().get("photo:hash:" + hash);
      If non-null: log.info("Photo dedup hit [hash={}]", hash); return existingUrl;
   
   d. Otherwise: generate UUID, build filename, save to disk (unchanged)
   e. Store bytes in Redis: byteRedisTemplate.opsForValue()
        .set("photo:" + uuid, fileBytes, Duration.ofDays(7))
   f. Store hash→url: stringRedisTemplate.opsForValue()
        .set("photo:hash:" + hash, "/photos/" + uuid, Duration.ofDays(7))
   g. Return "/photos/" + uuid (unchanged)
   
   Implement loadPhotoBytes(String photoUrl):
   - Extract uuid from URL (e.g. "/photos/abc-123.jpg" → "abc-123"):
     String filename = photoUrl.substring(photoUrl.lastIndexOf('/') + 1);
     String uuid = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
   - Try Redis first:
     byte[] bytes = byteRedisTemplate.opsForValue().get("photo:" + uuid);
     if (bytes != null) return bytes;
   - Fall back to disk:
     Path path = Paths.get(localStoragePath, filename); // inject @Value("${app.storage.local-path}")
     if (!Files.exists(path)) throw new ResourceNotFoundException("Photo not found: " + photoUrl);
     return Files.readAllBytes(path);

4. New controller: shared/controller/PhotoController.java (@RestController):
   @GetMapping("/api/v1/photos/{filename}")
   public ResponseEntity<byte[]> getPhoto(@PathVariable String filename):
     - Construct URL: "/photos/" + filename
     - Call fileStorageService.loadPhotoBytes(url)
     - Detect content type from filename extension (jpeg/png/webp → appropriate MediaType)
     - Return ResponseEntity.ok().contentType(contentType).body(bytes)
     - On ResourceNotFoundException: return 404

5. SecurityConfig.java — permit photos endpoint:
   .requestMatchers("/api/v1/photos/**").permitAll()
   (Add this alongside the existing /actuator/health permit)

6. Unit tests (LocalFileStorageServiceTest — new file or extend existing):
   @ExtendWith(MockitoExtension.class) — mock RedisTemplate, StringRedisTemplate, do NOT use real Redis
   - savePhoto: first upload → bytes stored in Redis, hash key stored, uuid returned
   - savePhoto: same bytes second time → dedup hit, same URL returned, no disk write
   - loadPhotoBytes: Redis hit → bytes returned without disk access (verify Files.readAllBytes not called)
   - loadPhotoBytes: Redis miss → disk read
   - loadPhotoBytes: neither → ResourceNotFoundException

All constructor injection. No @Autowired.
```

**Verify:** Upload photo → GET /api/v1/photos/{uuid}.jpg returns bytes. Upload same photo twice → same URL (check logs for "dedup hit"). Redis CLI: `keys photo:*` shows entries.

---

### T2.F — Image dimension locking — annotation alignment (Both) 🤖 AI
**Branch:** `feature/PP-027-redis-photo-storage` (same branch)
**Depends on:** T2.E (same branch)

> **Why:** Polygon annotation points are percentage-based (0-100%) relative to the exact
> image dimensions sent to the AI. If the browser scales the image, overlays drift.
> Recording the source dimensions at analysis time and validating on render catches misalignment.

**Backend Claude Code prompt:**
```
// T2.F (backend) — Record source image dimensions at analysis time

Read BACKEND.md, IdentificationServiceImpl.java, Identification.java,
IdentificationResponse.java, OllamaClient.java.

1. New shared utility class: shared/util/ImageUtil.java
   public final class ImageUtil {
     private ImageUtil() {}
     
     public static byte[] resizeAndConvertToJpeg(byte[] original, int maxSide) {
       // Move the EXACT implementation from OllamaClient.resizeAndConvertToJpeg() here.
       // Identical logic — BufferedImage, Graphics2D bilinear, JPEG output.
     }
     
     public static int[] readDimensions(byte[] imageBytes) {
       // Returns [width, height] or [0, 0] if unreadable
       try {
         BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
         return img != null ? new int[]{img.getWidth(), img.getHeight()} : new int[]{0, 0};
       } catch (Exception e) { return new int[]{0, 0}; }
     }
   }

2. Update OllamaClient.java:
   - Remove the private resizeAndConvertToJpeg() method
   - Replace both calls with: ImageUtil.resizeAndConvertToJpeg(imageBytes, OLLAMA_MAX_IMAGE_SIDE_PX)
   - Keep the OLLAMA_MAX_IMAGE_SIDE_PX constant

3. Liquibase migration: 011_add_image_dimensions.sql
   ALTER TABLE identifications
     ADD COLUMN IF NOT EXISTS source_image_width  INT,
     ADD COLUMN IF NOT EXISTS source_image_height INT;
   Register in db.changelog-master.xml AFTER 010_add_user_preferences.sql entry.

4. Identification.java entity — add fields:
   @Column(name = "source_image_width")  Integer sourceImageWidth;
   @Column(name = "source_image_height") Integer sourceImageHeight;

5. IdentificationResponse.java — add fields:
   Integer sourceImageWidth;
   Integer sourceImageHeight;
   (IdentificationMapper picks them up automatically if field names match)

6. In IdentificationServiceImpl.identify() (or submitIdentification() if T2.C is applied):
   After photo is read into byte[] imageBytes and BEFORE sending to AI:
   a. Prepare the bytes: byte[] prepared = ImageUtil.resizeAndConvertToJpeg(imageBytes, 1024)
      (Same 1024px cap as Ollama — ensures dimensions stored match what AI sees for ALL providers)
   b. Read dimensions: int[] dims = ImageUtil.readDimensions(prepared)
   c. Store on entity before first save:
      identification.setSourceImageWidth(dims[0]);
      identification.setSourceImageHeight(dims[1]);
   d. Use prepared bytes (not original imageBytes) for ALL AI calls going forward.
      This ensures GitHubModelsClient, OllamaClient all receive the same preprocessed image.

7. Unit tests (IdentificationServiceImplTest):
   - Verify sourceImageWidth and sourceImageHeight are set on the saved Identification.
   - Test with a real small JPEG byte[] (create a 100x75 test image via BufferedImage+ImageIO)
     to verify actual dimensions are read, not zeros.
```

**Frontend Claude Code prompt:**
```
// T2.F (frontend) — Aspect ratio warning in PhotoAnnotatorComponent

Read FRONTEND.md, photo-annotator.component.ts, identification-result.component.html,
plant-detail.component.html, preview-card.component.html.

1. PhotoAnnotatorComponent — add two new @Input properties:
   @Input() sourceImageWidth: number | null = null;
   @Input() sourceImageHeight: number | null = null;
   
   Add property: aspectRatioMismatch = false;

2. In the draw() method (after canvas dimensions are set), add:
   private checkAspectRatio(): void {
     if (!this.sourceImageWidth || !this.sourceImageHeight) {
       this.aspectRatioMismatch = false;
       return;
     }
     const aiAspect = this.sourceImageWidth / this.sourceImageHeight;
     const img = this.imageEl?.nativeElement;
     if (!img?.naturalWidth) return;
     const renderAspect = img.naturalWidth / img.naturalHeight;
     this.aspectRatioMismatch = Math.abs(aiAspect - renderAspect) / aiAspect > 0.02;
   }
   Call checkAspectRatio() at the end of the draw() method.

3. In template — add warning below the canvas/image:
   <div *ngIf="aspectRatioMismatch" class="aspect-warning">
     ⚠ Annotation may be misaligned — image was resized by your browser
   </div>
   Style (.aspect-warning): small amber chip, font-size 0.75rem, margin-top 4px.
   Use Angular Material color tokens — do not hardcode hex colors.

4. Pass new inputs from all three parent components:
   identification-result.component.html:
     <app-photo-annotator [sourceImageWidth]="result.sourceImageWidth"
                          [sourceImageHeight]="result.sourceImageHeight" ...>
   plant-detail.component.html (Last Scan tab):
     <app-photo-annotator [sourceImageWidth]="latestIdentification?.sourceImageWidth"
                          [sourceImageHeight]="latestIdentification?.sourceImageHeight" ...>
   preview-card.component.html:
     <app-photo-annotator [sourceImageWidth]="identification.sourceImageWidth"
                          [sourceImageHeight]="identification.sourceImageHeight" ...>

5. Update identification.model.ts — add to IdentificationResponse interface:
   sourceImageWidth?: number | null;
   sourceImageHeight?: number | null;

No new modules, routes, or services.
```

**Verify:** After identification, inspect IdentificationResponse — `sourceImageWidth` and `sourceImageHeight` are non-zero. Upload a portrait photo → canvas displays without misalignment warning. Check DB: `SELECT source_image_width, source_image_height FROM identifications` — values present.

---

### T2.10 — Garden health dashboard 💡 Architect Suggestion
**Branch:** `feature/PP-020-garden-dashboard`

> **Why:** Once you have multiple plants with care plans, the user needs a single view
> to understand the state of their whole garden. This is what turns PlantPal from a tool
> into a habit.

> **Revised 2026-06-18 — split into 4 ordered sub-tasks.** The original single-prompt spec
> assumed `care_logs` had a write path (for the weekly streak) — it doesn't; T3.1 (reminder
> "mark care done") hasn't started, so `care_logs` is an empty table with no entity/repository
> at all. **Streak is dropped from this task and deferred to T3.1.** Also discovered while
> planning: `PlantResponse.healthStatus` / `nextWaterDays` are already rendered by
> `plant-card.component.html` (health badge, water chip) but the backend never populates
> either field — every plant card today silently shows neither. T2.10a fixes that first since
> T2.10b's dashboard needs the exact same "latest identification per plant" batch query.

---

### T2.10a — Backend: fix Plant health/water data 🤖 AI
**Branch:** `feature/PP-020-garden-dashboard`

**Claude Code prompt:**
```
// Phase 2 — Fix Plant overview data: populate healthStatus + nextWaterDays on PlantResponse

Context: PlantResponse already declares fields that plant-card.component.html binds to
(health badge, water chip), but PlantMapper only maps raw Plant entity columns — neither
field has ever been populated. Fix this by deriving both server-side from existing data.
No new tables.

In com.plantpal.plant:

1. dto/PlantResponse.java — add two fields:
   private String healthStatus;   // mirrors Identification.healthStatus: HEALTHY | ISSUES_DETECTED | UNKNOWN | null
   private Integer nextWaterDays;  // days until next WATERING reminder is due; negative = overdue; null = none

2. com.plantpal.identification.repository.IdentificationRepository — add:
   @Query("SELECT i FROM Identification i WHERE i.id IN " +
          "(SELECT MAX(i2.id) FROM Identification i2 WHERE i2.plantId IN :plantIds GROUP BY i2.plantId)")
   List<Identification> findLatestPerPlant(@Param("plantIds") List<Long> plantIds);

3. com.plantpal.reminder.repository.ReminderRepository — add:
   @Query("SELECT r FROM Reminder r WHERE r.plantId IN :plantIds AND r.careType = 'WATERING' " +
          "AND r.enabled = true AND r.nextDueAt = (SELECT MIN(r2.nextDueAt) FROM Reminder r2 " +
          "WHERE r2.plantId = r.plantId AND r2.careType = 'WATERING' AND r2.enabled = true)")
   List<Reminder> findNearestWateringPerPlant(@Param("plantIds") List<Long> plantIds);

4. PlantServiceImpl:
   - Add private helper enrichWithHealthAndWater(List<PlantResponse> responses, List<Long> plantIds):
     batch-fetch both queries above, build Map<Long,String> (plantId→healthStatus) and
     Map<Long,Integer> (plantId→nextWaterDays, via (int) ChronoUnit.DAYS.between(Instant.now(),
     reminder.getNextDueAt())), then rebuild each PlantResponse via toBuilder() (add
     @Builder(toBuilder = true) to PlantResponse — keep it immutable, do NOT add @Setter)
   - getUserPlants(): call the helper over all plant IDs on the fetched page before returning
   - getPlant(): call the same helper with a singleton list
   - Both call sites are inside @Cacheable methods — enrich BEFORE returning so the cached
     value is already complete (no partial DTOs cached)

5. IdentificationServiceImpl.processIdentification(): after persisting status COMPLETED
   (success path only — not FAILED), evict the "plants" cache so re-scanning an existing plant
   immediately reflects its new healthStatus on the next plant-list fetch. Inject CacheManager
   via constructor, call cacheManager.getCache("plants").clear() right after the COMPLETED save.

6. Unit tests:
   - PlantServiceTest: getUserPlants() returns healthStatus from latest identification and
     nextWaterDays from nearest enabled WATERING reminder; both null when neither exists;
     nextWaterDays is negative when the reminder is overdue
   - IdentificationServiceImplTest: processIdentification() COMPLETED path calls
     cacheManager.getCache("plants").clear(); FAILED path does not
```
**Verify:** GET /api/v1/plants for a plant with an identification + watering reminder returns non-null `healthStatus` and `nextWaterDays`. No frontend change needed — plant-card immediately shows the health badge and water chip.

---

### T2.10b — Backend: dashboard aggregate endpoint 🤖 AI
**Branch:** `feature/PP-020-garden-dashboard` (after T2.10a)

**Claude Code prompt:**
```
// Phase 2 — Garden health dashboard backend: GET /api/v1/dashboard

New module com.plantpal.dashboard — read-only aggregation over existing plant/identification/
reminder data. No new tables. Depends on T2.10a's IdentificationRepository.findLatestPerPlant().

1. dto/DashboardResponse.java:
   HealthSummaryDto healthSummary;
   List<ReminderSummaryDto> overdueReminders;
   List<ReminderSummaryDto> todayReminders;
   List<PlantHealthTrendDto> healthTrends;   // only plants with >= 2 identifications

2. dto/HealthSummaryDto.java: totalPlants, healthyCount, issuesCount, unknownCount (all int)

3. dto/ReminderSummaryDto.java: reminderId, plantId, plantNickname, plantPhotoUrl,
   careType (String), nextDueAt (Instant), daysOverdue (int — 0 for today's items, positive N
   for N days overdue)

4. dto/PlantHealthTrendDto.java: plantId, plantNickname, trend (String):
   - "IMPROVING": previous healthStatus == ISSUES_DETECTED AND latest == HEALTHY
   - "WORSENING": previous healthStatus == HEALTHY AND latest == ISSUES_DETECTED
   - "STABLE": anything else (including UNKNOWN combinations)

5. service/DashboardService.java (interface) + service/impl/DashboardServiceImpl.java:
   getDashboard(Long userId):
   a) plantRepository.findAllByUserIdAndStatus(userId, ACTIVE, PageRequest.of(0, 200)) —
      bounded, not unpaged; this is a personal-garden app, 200 active plants is a generous cap
   b) healthSummary: batch-fetch latest identification per plantId (reuse
      IdentificationRepository.findLatestPerPlant), count by healthStatus
      (no identification → counts toward unknownCount)
   c) overdueReminders / todayReminders: add ReminderRepository.findByUserIdAndEnabledTrue(Long)
      (List<Reminder>, no pagination — bounded by plant count); partition by nextDueAt vs
      start-of-today / start-of-tomorrow (inject Clock via constructor, default bean
      Clock.systemDefaultZone(), for testability); sort ascending by nextDueAt; map to
      ReminderSummaryDto by joining against a Map<Long,Plant> built from the plant list fetched
      in (a) — skip reminders whose plant isn't in that map (archived)
   d) healthTrends: for each plant, identificationRepository.findByPlantIdOrderByCreatedAtDesc(
      plantId, PageRequest.of(0, 2)); skip plants with < 2 results; compare healthStatus per
      the rules above

   NOTE: deliberately NOT @Cacheable. Nothing currently evicts on reminder changes, and a cached
   dashboard showing stale overdue/today counts would be actively misleading. Revisit once T3.1
   (reminder mark-done) exists and a real eviction trigger can be wired in.

6. controller/DashboardController.java:
   GET /api/v1/dashboard → ApiResponse<DashboardResponse>, userId from SecurityContext
   (same getCurrentUserId() pattern as IdentificationController/ChatController)

7. Unit tests: DashboardServiceTest — health summary counts; overdue vs today partitioning using
   an injected fixed Clock; trend IMPROVING/WORSENING/STABLE; empty garden returns a zeroed
   summary + empty lists (never null)
```
**Verify:** with one plant overdue on watering and one due today, GET /api/v1/dashboard returns both in the correct buckets. A plant with two identifications (ISSUES_DETECTED then HEALTHY) appears in `healthTrends` as IMPROVING.

---

### T2.10c — Frontend: plant photo timeline 🤝 Assisted
**Branch:** `feature/PP-020-garden-dashboard`

**Claude Code prompt:**
```
// Phase 2 — Plant photo timeline: visually track a plant's progress over time

Reuses the existing GET /api/v1/identifications/plant/{plantId} endpoint (already implemented,
paginated, sorted createdAt DESC) — no backend changes needed.

1. features/plant/components/plant-photo-timeline/ — NEW PlantPhotoTimelineComponent:
   @Input() plantId!: number
   On init: identificationService.getPlantIdentifications(plantId, 0, 20) — up to 20 most
   recent scans. Reverse the fetched array before rendering (API returns newest-first; a
   progress timeline should read oldest→newest, left→right).
   Render a horizontally-scrollable strip (CSS overflow-x: auto, no carousel library): each
   item = photo thumbnail + short date + small health dot (HEALTHY=mint/ISSUES=coral/UNKNOWN=grey,
   use --color-success/--color-error/--color-text-secondary tokens)
   Click → navigate to /identify/:id (existing IdentificationDetailPageComponent route)
   Empty state: small muted "No scans yet — your first photo will start the timeline"
   Loading: reuse the skeleton pattern already used in care-plan.component

2. plant-detail.component.html — add <app-plant-photo-timeline [plantId]="plant.id"> at the TOP
   of the existing "Overview" tab, above .info-grid. (Decision: not a 5th tab — a 5th tab would
   sit next to "Care History," which is also still a Phase-3 placeholder; one richer Overview
   tab beats two thin ones.)

3. plant.module.ts — declare PlantPhotoTimelineComponent. IdentificationService is already
   provided at PlantModule level (confirmed in plant-detail.component.ts) — no new providers.

4. No model changes needed — IdentificationResponse already has photoUrl, healthStatus, createdAt.

Style: thumbnail 80x80px, border-radius 8px, 2px solid border in the health color, 8px gap,
date at 0.7rem in --color-text-secondary below each thumbnail.
```
**Verify:** a plant with 3+ scans shows a scrollable thumbnail strip at the top of its Overview tab, oldest on the left / most recent on the right, each with a date and health-colored border; clicking one opens that scan's detail page.

---

### T2.10d — Frontend: garden dashboard page 🤝 Assisted
**Branch:** `feature/PP-020-garden-dashboard` (after T2.10b ships)

**Claude Code prompt:**
```
// Phase 2 — Garden health dashboard: new landing page after login

1. features/dashboard/ — NEW lazy module (DashboardModule), structured like features/reminder/:
   - dashboard.module.ts, dashboard-routing.module.ts (single route: '' → GardenDashboardComponent)
   - models/dashboard.model.ts: DashboardResponse, HealthSummaryDto, ReminderSummaryDto,
     PlantHealthTrendDto — mirror T2.10b's backend DTOs field-for-field
   - services/dashboard.service.ts: getDashboard() → GET /api/v1/dashboard,
     Observable<ApiResponse<DashboardResponse>>

2. pages/garden-dashboard/garden-dashboard.component.{ts,html,scss}:
   - On init: dashboardService.getDashboard(), takeUntil(destroy$)
   - Loading: skeleton state (reuse care-plan.component's skeleton pattern)
   - Sections top to bottom:
     a. Health summary strip: 3 stat chips (Healthy N / Issues N / Unknown N), mint/coral/grey
     b. "Needs attention" (overdueReminders): red-left-border rows — plant photo thumb +
        nickname + careType icon (water_drop/eco/yard) + "Overdue by N days"; omit section if empty
     c. "Today" (todayReminders): same row style, neutral border; if empty show
        "Nothing due today 🌿"
     d. "Health trends": only plants where trend !== 'STABLE'; chip "↑ Improving" (mint) or
        "↓ Worsening" (coral); omit section if empty
     e. If healthSummary.totalPlants === 0: replace all of the above with a single empty state
        — "Your garden is empty" + pill CTA button to /plants/new
   - Every plant row navigates to /plants/:id on click

3. app-routing.module.ts:
   - Add: { path: 'dashboard', loadChildren: () => import('./features/dashboard/dashboard.module')
     .then(m => m.DashboardModule), canActivate: [AuthGuard] }
   - Change root redirect: { path: '', redirectTo: 'dashboard', pathMatch: 'full' } (was 'plants')
   - Do NOT touch the 'plants' route — the full garden list still lives at /plants via the
     bottom-nav "Garden" icon, unchanged

4. Do NOT add a 5th bottom-nav icon. The bottom nav has 4 fixed items (Garden/Identify/
   Reminders/Chat); T2.D3 already found that squeezing a 5th item risks pushing the profile
   button off-screen. The dashboard is reached via the post-login redirect and the toolbar
   brand link (app.component.html's "PlantPal" brand already routerLinks to "/" — resolves to
   /dashboard automatically, no template change needed).

Style: reuse --color-primary/--color-success/--color-error/--radius-card/--shadow-card tokens.
No new colors.
```
**Verify:** logging in lands on /dashboard. A plant with an overdue watering reminder shows under "Needs attention." Archiving the last plant shows the empty-garden state. Clicking any plant row navigates to its detail page. Bottom nav still shows exactly 4 icons.

---

### T2.11 — Manual testing — Phase 2 complete ✅ DONE
Covered ad-hoc across the T2.10/T2.10e session (2026-06-18) instead of a separate checklist pass:
photo → species + bounding/polygon annotation → disease highlight → preview card → save to garden →
care plan tab → reminders auto-created → dashboard overdue/today sections → mobile viewport (390×844)
all exercised live via Playwright + manual review during the dashboard and annotation-fix work.
Phase 2 is complete.

---

## PHASE 3 — Reminders + Push Notifications
> Goal: users receive care reminders on their phone via push notifications.
> Estimated time: Weeks 5–6.

---

### T3.1 — Reminder module — full backend 🤖 AI ✅ DONE (2026-06-18)
**Branch:** `feature/PP-011-reminder-module`
> Implemented largely as specified, with two deviations: `getUserReminders` is bounded at 200
> (PageRequest, not unpaged — personal-garden app) rather than a raw findAll, and
> `ReminderScheduler` takes an injected `Clock` (not bare `Instant.now()`) for testability — same
> pattern as T2.10b's `DashboardServiceImpl`. See STATE.md for full notes.

**Claude Code prompt:**
```
// Phase 3 — Generate the full Reminder + Care Log module backend

In com.plantpal.reminder:

1. Entities:
   Reminder.java: id, plantId, userId, careType (CareType enum), frequencyDays,
     nextDueAt (Instant), enabled (boolean), + audit fields
   CareLog.java: id, plantId, userId, careType, notes, performedAt (Instant), createdAt
   PushSubscription.java: id, userId, endpoint, keyP256dh, keyAuth, enabled, createdAt
   CareType enum: WATERING, FERTILIZING, REPOTTING, PRUNING

2. DTOs:
   CreateReminderRequest: plantId, careType, frequencyDays, nextDueAt
   ReminderResponse: all fields + plant nickname
   MarkCareDoneRequest: reminderId, notes (optional)
   CareLogResponse: all fields + plant nickname
   PushSubscriptionRequest: endpoint, keyP256dh, keyAuth

3. Repositories:
   ReminderRepository: findByUserIdAndEnabledTrue(Long userId, Pageable),
     @Query find all enabled reminders where nextDueAt <= now() (for scheduler)
   CareLogRepository: findByPlantIdOrderByPerformedAtDesc(Long plantId, Pageable)

4. Services + impls:
   ReminderService: CRUD + calculateNextDueAt(Instant lastDone, int frequencyDays) helper
   CareLogService: logCare(MarkCareDoneRequest, userId) — saves log + updates reminder.nextDueAt
   WebPushService: saveSubscription(PushSubscriptionRequest, userId),
     sendNotification(Long userId, String title, String body)
     Use nl.martijndwars:web-push with VAPID keys from @Value

5. scheduler/ReminderScheduler.java:
   @Scheduled(cron = "0 0 8 * * *") — every day at 8am
   Find all due reminders, group by userId, send one push per user
   (one push with "You have N plants to care for today" — not N pushes)
   Log: how many notifications sent, how many failed

6. Controllers:
   ReminderController: GET/POST /api/v1/reminders, DELETE /api/v1/reminders/{id}
   CareLogController: POST /api/v1/care/done, GET /api/v1/care/plant/{plantId}
   NotificationController: POST /api/v1/notifications/subscribe

7. Unit tests: ReminderServiceTest (date calculation), CareLogServiceTest
```

---

### T3.2 — Reminder module — Angular frontend + PWA 🤝 Assisted ✅ DONE (2026-06-18)
**Branch:** `feature/PP-011-reminder-module` (landed on the same branch as T3.1, not a separate one)
> CareLogComponent wired into plant-detail's "Care History" tab (was a Phase-3 placeholder since
> T2.9). See STATE.md for full notes. Remaining: T3.3 manual/device testing below.
> ⚠️ Found via live testing (2026-06-18) and fixed same session: `care-log.service.ts` called
> `/api/v1/care-logs` instead of the real `/api/v1/care` route — Care History tab silently showed
> empty for every plant. See STATE.md's T3.2 entry for the full root-cause note.

**Claude Code prompt:**
```
// Phase 3 — Generate the Reminder Angular module + PWA push notifications

1. features/reminder/ — full module:
   - ReminderListComponent: cards sorted by nextDueAt, overdue highlighted in red
   - CreateReminderFormComponent: plant selector, care type selector, frequency, first due date
   - CareCalendarComponent: 7-day view showing what's due each day
   - CareLogComponent: timeline of past care actions per plant

2. core/services/push-notification.service.ts:
   - requestPermission(): Promise<boolean>
   - subscribeToNotifications(): calls backend POST /api/v1/notifications/subscribe
     with the ServiceWorkerRegistration PushSubscription object
   - Load VAPID_PUBLIC_KEY from environment.ts

3. In app.component.ts:
   On first load, if user is logged in + notifications not yet requested:
   show a friendly inline banner ("Get reminders on your phone") with Accept/Dismiss.
   Don't use browser prompt directly — users dismiss those. Use your own UI first.

4. ngsw-config.json (PWA service worker):
   - Cache app shell assets
   - Network-first strategy for /api/** calls
   - Enable background push notifications

5. Update environment.ts to include vapidPublicKey
```

---

### T3.4 — Backend: actionable care plans (routines + treatment plans) 🤖 AI
**Branch:** `feature/PP-028-actionable-care-plans-2`

> **Why:** care actions aren't all the same shape. Some are simple recurring routines
> (water every 7 days), some are finite multi-step treatments (apply neem oil day 1, recheck
> day 7). Today every care card is just text — there's no way to turn either shape into
> something scheduled. This task adds that, plus fixes a gap where one-time completed actions
> had nowhere to "finish" (every Reminder currently assumes infinite recurrence).

**Claude Code prompt:**
```
// Phase 3 — Actionable care plans: ROUTINE reminders + multi-step TREATMENT plans

Design: a care card's action is either a ROUTINE (recurring, just needs a frequency) or a
TREATMENT (a finite ordered sequence of steps, optionally with one Mermaid flowchart for the
whole sequence). Treatment steps are modelled as one-time Reminders, NOT a parallel entity —
reuse Reminder/CareLog end to end.

1. Migration 012_add_treatment_plans.sql (register in db.changelog-master.xml after 011):
   CREATE TABLE treatment_plans (
       id BIGSERIAL PRIMARY KEY,
       plant_id BIGINT NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
       user_id BIGINT NOT NULL REFERENCES users(id),
       title VARCHAR(255) NOT NULL,
       source_care_card_type VARCHAR(30),
       diagram_format VARCHAR(20),
       diagram_content TEXT,
       status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
   );
   CREATE INDEX idx_treatment_plans_plant_id ON treatment_plans(plant_id);
   ALTER TABLE reminders ADD COLUMN recurring BOOLEAN NOT NULL DEFAULT TRUE;
   ALTER TABLE reminders ADD COLUMN treatment_plan_id BIGINT REFERENCES treatment_plans(id) ON DELETE CASCADE;
   ALTER TABLE reminders ADD COLUMN treatment_plan_title VARCHAR(255);
   ALTER TABLE reminders ADD COLUMN step_order INT;
   CREATE INDEX idx_reminders_treatment_plan_id ON reminders(treatment_plan_id);

2. com.plantpal.reminder.entity.CareType — expand to mirror CareCardType exactly (10 values):
   WATERING, LIGHT, HUMIDITY, TEMPERATURE, FERTILIZING, REPOTTING, PRUNING, PEST, SEASONAL,
   BEGINNER_TIP. (Was only WATERING/FERTILIZING/REPOTTING/PRUNING — additive, no existing data
   affected.) Treatment-plan steps inherit the originating care card's own type — no generic
   "TREATMENT" bucket needed.

3. com.plantpal.reminder.entity.Reminder — add: boolean recurring (default true via @Builder.Default),
   Long treatmentPlanId (nullable), String treatmentPlanTitle (nullable, denormalized so reminder
   lists don't need a join), Integer stepOrder (nullable)

4. New com.plantpal.reminder.entity.TreatmentPlan (id, plantId, userId, title, sourceCareCardType,
   diagramFormat, diagramContent, status (TreatmentPlanStatus: ACTIVE|COMPLETED|ABANDONED),
   createdAt/updatedAt via @CreationTimestamp/@UpdateTimestamp — same pattern as Reminder, does
   NOT extend AuditableEntity)

5. com.plantpal.reminder.repository.ReminderRepository — add:
   List<Reminder> findByTreatmentPlanIdAndEnabledTrue(Long treatmentPlanId)
   com.plantpal.reminder.repository.TreatmentPlanRepository (new) — standard JpaRepository, plus
   Optional<TreatmentPlan> findByIdAndUserId(Long id, Long userId);
   List<TreatmentPlan> findByPlantIdAndUserId(Long plantId, Long userId);

6. Fix existing duplication before adding the new branch: ReminderService gets a new method
   `void applyCompletionToReminder(Reminder reminder, Instant performedAt)` — mutates (does not
   persist the CareLog, caller does that part since notes differ) and SAVES the reminder:
     - if reminder.isRecurring(): nextDueAt = calculateNextDueAt(performedAt, frequencyDays) [existing behaviour]
     - else: enabled = false (disable, do not reschedule); if treatmentPlanId != null, check
       reminderRepository.findByTreatmentPlanIdAndEnabledTrue(treatmentPlanId) — if now empty,
       load the TreatmentPlan and set status = COMPLETED, save it
   ReminderServiceImpl.completeReminder(id, userId): unchanged CareLog-writing, but now calls
   applyCompletionToReminder() instead of inlining the reschedule.
   CareLogServiceImpl.logCare(): replace its own `reminder.setNextDueAt(...)` line with a call to
   reminderService.applyCompletionToReminder(reminder, performedAt) — removes the duplicated
   rescheduling logic that previously lived only in CareLogServiceImpl.

7. com.plantpal.reminder.dto.ReminderResponse — add: Long treatmentPlanId, String treatmentPlanTitle,
   Integer stepOrder, boolean recurring (all nullable/default-false-safe; existing recurring
   reminders just have these as null/true/false respectively)

8. New DTOs in com.plantpal.identification.dto (action plans are generated alongside care plans,
   same module):
   ActionPlanDto: String type ("ROUTINE"|"TREATMENT"), Integer frequencyDays (ROUTINE only),
     List<TreatmentStepDto> steps (TREATMENT only), DiagramDto diagram (TREATMENT only, nullable)
   TreatmentStepDto: int order, String instruction, int dueOffsetDays
   DiagramDto: String format (only "MERMAID" supported), String content (mermaid syntax)
   Add CareCardDto.actionPlan (ActionPlanDto, nullable)

9. New com.plantpal.identification.util.ActionPlanValidator (static utility, mirrors the
   defensive-parsing philosophy already used for care plans/annotations — never throws):
   static ActionPlanDto normalize(ActionPlanDto raw) — returns null if raw is null or invalid:
     - type must case-insensitively match ROUTINE or TREATMENT, else null
     - ROUTINE: frequencyDays must be present, clamp/reject outside [1, 365] (reject = return null)
     - TREATMENT: steps must be non-empty; truncate to first 10 if more (log WARN); clamp each
       step's dueOffsetDays to [0, 180]; re-number order sequentially 1..N regardless of AI input
       (ignore whatever order values the AI sent — guarantees no gaps/dupes/out-of-sequence)
     - diagram: keep only if format equalsIgnoreCase "MERMAID" AND content non-blank AND
       content.length() <= 2000, else null out just the diagram (rest of the TREATMENT still valid)
   Call this on every CareCardDto.actionPlan after parsing AI JSON, and on CureAdviceResponse's
   actionPlan (see step 11) — both in IdentificationServiceImpl.

10. Update prompts (both must add the SAME schema addition to each care card, kept in sync):
    GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT — add to each careCards[] object:
      "actionPlan": {
        "type": "ROUTINE | TREATMENT | null — null if this card is purely informational",
        "frequencyDays": "<int, ROUTINE only>",
        "steps": [ { "order": <int>, "instruction": "<string>", "dueOffsetDays": <int> } ],
        "diagram": { "format": "MERMAID", "content": "<mermaid flowchart syntax>" } or null
      }
    Rules to add: "Only set actionPlan.type=TREATMENT for genuinely multi-step processes (3+
    distinct actions over time) — a single one-off tip should have actionPlan: null. Only include
    a diagram when the steps have real branching/decision logic worth visualising — most linear
    step lists do not need one."
    DeepSeekClient.CARE_PLAN_SYSTEM_PROMPT — same addition (this prompt's care plan shape must
    stay in sync with GitHubModelsClient's, they're consumed by the same parseCarePlan()).

11. DeepSeekClient.generateCureAdvice() — change response_format to json_object; new system prompt
    text (replace CURE_ADVICE_SYSTEM_PROMPT) asking for:
      { "advice": "<plain text, numbered steps as before — kept for display>",
        "actionPlan": <same ActionPlanDto shape as above, or null> }
    Returns the raw JSON string (same pattern as analyzeRegions() — parsing happens in the service
    layer, not the client). CureAdviceResponse: add `ActionPlanDto actionPlan` field.
    IdentificationServiceImpl.getCureAdvice(): parse the JSON via objectMapper into an internal
    record {advice, actionPlan}; on parse failure, fall back to treating the ENTIRE raw string as
    `advice` with actionPlan=null (never let a malformed response fail the whole call — this
    mirrors parseCarePlan()'s fallback philosophy). Run actionPlan through ActionPlanValidator.

12. AddCareCardRequest — add optional `ActionPlanDto actionPlan` field (the disease panel already
    has the cure-advice response with its actionPlan in memory by the time "Add to care plan" is
    clicked — pass it through so the resulting card stays actionable later from the Care Plan tab,
    not just immediately from the disease panel). IdentificationServiceImpl.addCareCard(): copy
    req.getActionPlan() onto the new CareCardDto (through ActionPlanValidator.normalize() again —
    never trust a client-supplied DTO without re-validating server-side).

13. New com.plantpal.reminder.service.TreatmentPlanService (interface) + impl:
    createFromActionPlan(Long plantId, Long userId, String title, String sourceCareCardType,
      ActionPlanDto actionPlan): validate actionPlan.type == TREATMENT (else throw
      ValidationException), ownership-check the plant, create the TreatmentPlan row, then for each
      step create a Reminder: recurring=false, treatmentPlanId, treatmentPlanTitle=title,
      stepOrder=step.order, careType=<the source care card's type, parsed from
      sourceCareCardType>, frequencyDays=0 (unused when recurring=false),
      nextDueAt=Instant.now().plus(step.dueOffsetDays, DAYS), enabled=true. Returns
      TreatmentPlanResponse (id, plantId, title, diagramFormat, diagramContent, status,
      List<ReminderResponse> steps ordered by stepOrder).
    getTreatmentPlan(Long id, Long userId): ownership-checked fetch + its steps
      (reminderRepository.findByTreatmentPlanIdAndEnabledTrue is wrong here — need ALL steps
      including completed/disabled ones for the detail view; add a plain
      findByTreatmentPlanIdOrderByStepOrder(Long) query instead)

14. New com.plantpal.reminder.controller.TreatmentPlanController:
    POST /api/v1/treatment-plans — body {plantId, title, sourceCareCardType, actionPlan} →
      ApiResponse<TreatmentPlanResponse>, 201
    GET /api/v1/treatment-plans/{id} → ApiResponse<TreatmentPlanResponse>

15. Unit tests: ActionPlanValidatorTest (the normalization rules — this is the highest-value test
    in this task, exercise every clamp/reject case), TreatmentPlanServiceTest (creates correct
    number of steps with correct due dates, rejects ROUTINE actionPlan), ReminderServiceTest
    additions (applyCompletionToReminder: recurring reschedules, one-time disables, last step of
    a plan marks the plan COMPLETED, non-last step leaves plan ACTIVE), CareLogServiceTest update
    (logCare on a one-time reminder disables instead of rescheduling — verify no duplicate
    rescheduling logic remains)
```
**Verify:** `mvn test` green. Manually: POST a TREATMENT actionPlan to /api/v1/treatment-plans,
confirm N reminders created with ascending nextDueAt matching dueOffsetDays. Complete all of them
via POST /api/v1/reminders/{id}/complete — confirm the TreatmentPlan flips to COMPLETED after the
last one. Confirm a ROUTINE actionPlan still only ever produces ordinary recurring reminders
(unchanged behaviour). Feed `ActionPlanValidator.normalize()` a malformed/oversized AI response —
confirm it degrades to null rather than throwing.

---

### T3.5 — Frontend: actionable care plans UI (reminders, treatment plans, diagrams) 🤝 Assisted
**Branch:** `feature/PP-028-actionable-care-plans-2` (same branch as T3.4)

**Claude Code prompt:**
```
// Phase 3 — Actionable care plans frontend: "Set reminder" / "Start treatment plan" + Mermaid

1. npm install mermaid (official npm package; ESM, works directly with Angular 16's build).

2. identification/models/identification.model.ts — add:
   ActionPlanType = 'ROUTINE' | 'TREATMENT'
   DiagramDto { format: 'MERMAID'; content: string }
   TreatmentStepDto { order: number; instruction: string; dueOffsetDays: number }
   ActionPlanDto { type: ActionPlanType; frequencyDays?: number; steps?: TreatmentStepDto[]; diagram?: DiagramDto | null }
   Add `actionPlan?: ActionPlanDto | null` to CareCardDto.
   CureAdviceResponse-equivalent: identification.service.ts's getCureAdvice() currently returns
   Observable<string> — change to Observable<{ advice: string; actionPlan: ActionPlanDto | null }>
   (update the one call site in disease-detail-panel.component.ts accordingly: `this.advice =
   result.advice; this.actionPlan = result.actionPlan;`)

3. reminder/models/reminder.model.ts — add to ReminderResponse: treatmentPlanId?: number | null,
   treatmentPlanTitle?: string | null, stepOrder?: number | null, recurring: boolean
   New reminder/models/treatment-plan.model.ts:
   TreatmentPlanResponse { id, plantId, title, diagramFormat: 'MERMAID' | null, diagramContent:
     string | null, status: 'ACTIVE' | 'COMPLETED' | 'ABANDONED', steps: ReminderResponse[] }
   CreateTreatmentPlanRequest { plantId: number; title: string; sourceCareCardType: string;
     actionPlan: ActionPlanDto }

4. reminder/services/treatment-plan.service.ts (new): createFromActionPlan(request) → POST
   /api/v1/treatment-plans; getTreatmentPlan(id) → GET /api/v1/treatment-plans/{id}

5. shared/components/mermaid-diagram/ (new, declared in SharedModule):
   MermaidDiagramComponent: @Input() definition: string
   States, in order of what the user actually sees:
   - While rendering: nothing visible yet — no spinner, no placeholder box. Mermaid renders
     synchronously fast enough (it's small text, not an AI call) that a loading state would just
     flicker. ngOnChanges dynamically `import('mermaid')`, calls
     mermaid.render(uniqueId, this.definition) inside try/catch.
   - On success: the returned SVG string is bound via [innerHTML] using
     DomSanitizer.bypassSecurityTrustHtml() (safe here — the HTML comes from mermaid's own
     renderer, not directly from AI text), wrapped in a card matching the app's existing card
     treatment (--radius-card, --shadow-card, white surface, 16px padding) so the diagram doesn't
     float loose on the page.
   - On failure (malformed mermaid syntax from the AI): set a `renderFailed` flag and render
     NOTHING — no error message, no broken-image icon, the component's host element collapses to
     zero height. Diagrams are always a bonus on top of the step list, never required reading, so
     a failure should be invisible rather than alarming.

6. identification/components/care-plan/care-card.component.ts/.html — every care card currently
   ends after its detail text. Add a thin divider + a new action row beneath it, but ONLY when
   card.actionPlan is non-null (cards without one — most BEGINNER_TIP/SEASONAL cards — look
   exactly as they do today, no empty row, no visual change at all):
   - actionPlan.type === 'ROUTINE': a single outlined button, icon `notifications_active`, label
     "Set reminder". Clicking opens a small MatDialog (reuse the compact dialog sizing already
     established for IdentificationUploadDialogComponent — width ~360px) titled "Set a reminder"
     with one line of explanatory text ("We'll remind you to {{ card.title | lowercase }} every:")
     and a number input pre-filled with actionPlan.frequencyDays, suffixed "days", with Cancel /
     "Set reminder" buttons. Confirming calls ReminderService.createReminder({plantId, careType:
     card.type, frequencyDays, firstDueAt: now}). On success, the button in the card itself is
     REPLACED by a small green checkmark + "Reminder set" text (mirrors exactly how
     disease-detail-panel's "Add to care plan" button already swaps to "Added to care plan" after
     success — same visual language, don't invent a new pattern). If a reminder of this exact
     careType already exists and is enabled for this plant (passed down as @Input()
     existingCareTypes: CareType[] from whichever parent already holds the plant's reminder list),
     skip the button entirely and show the same "Reminder set" state immediately, greyed — the
     user shouldn't be invited to create a duplicate.
   - actionPlan.type === 'TREATMENT': a single filled button, icon `medical_services`, label
     "Start treatment plan". No dialog — clicking goes straight to
     TreatmentPlanService.createFromActionPlan({plantId, title: card.title, sourceCareCardType:
     card.type, actionPlan: card.actionPlan}). While the request is in flight, the button shows
     an inline spinner + "Starting…" (same disabled-button-with-spinner pattern used elsewhere in
     this app, e.g. preview-card's "Saving…"). On success, a snack-bar appears: "Treatment plan
     started" with a "View" action button that navigates to /treatment-plans/{response.id}; the
     card's button itself becomes a disabled "Plan in progress" state so it can't be started twice.
   care-plan.component needs a new @Input() plantId for this to construct requests; pass it down
   from plant-detail's <app-care-plan [carePlan] [plantId]="plant.id">

7. identification/components/disease-detail-panel/disease-detail-panel.component.ts/.html: the
   panel's `.panel-actions` row currently holds "Ask for cure" then, once advice loads, just
   "Add to care plan". Extend it:
   - askForCure() now also stores `this.actionPlan = result.actionPlan` alongside the existing
     advice text.
   - Once advice has loaded AND actionPlan?.type === 'TREATMENT', the actions row shows TWO
     buttons side by side: the existing "Add to care plan" (outlined, keeps the card as a
     permanent reference even after the plan finishes) and a new "Start treatment plan" (filled,
     same TreatmentPlanService call as step 6, title = region.label, sourceCareCardType = 'PEST').
     If actionPlan is null or ROUTINE (the AI judged this cure doesn't need a scheduled sequence —
     e.g. "just keep an eye on it for a few days"), the row looks exactly as it does today: just
     "Add to care plan". This is the common case, not the exception — most cure advice is a quick
     fix, not a multi-day protocol, so most users will never see the second button.
   - "Add to care plan" call (IdentificationService.addCareCard) now also passes
     `actionPlan: this.actionPlan` in the request body, so a card added this way stays actionable
     later from the Care Plan tab too (step 6's action row), not just in this immediate moment.

8. reminder/services/identification.service.ts (addCareCard signature) — add optional actionPlan
   param, included in the POST body.

9. New reminder/pages/treatment-plan-detail/ (route: /treatment-plans/:id, added to
   reminder-routing.module.ts, guarded like other routes). This is a full page, reached either
   from a care-card's "View" snackbar action or from a reminder-list chip (step 10). Layout, top
   to bottom, matching this app's existing page conventions (cream background, card-based
   sections, font-heading for the title):
   - Header: back arrow (browser history, like plant-detail's hero-back), the plan's title in
     font-heading, and a status chip beside it — "Active" (sage/mint outline), "Completed" (mint
     filled, with a small check icon), or "Abandoned" (grey, only reachable if abandon support is
     added later — not in this task).
   - Progress line directly under the header, plain text not a component: "2 of 4 steps complete"
     (computed client-side from the fetched steps — count where !reminder.enabled).
   - If diagramContent is present: a card labelled "How this works" above the step list,
     containing <app-mermaid-diagram [definition]="plan.diagramContent">. If diagramContent is
     null (most plans — most treatments are linear and the AI was told not to over-use diagrams),
     this section doesn't exist at all, the step list starts right after the progress line.
   - Step list: a vertical timeline, visually similar to how reminder-list already groups
     Today/Tomorrow/Next week with a small coloured dot — here each step is a numbered circle
     (1, 2, 3...) connected by a thin vertical line to the next, consistent left-aligned column.
     Each step row: the numbered circle + the instruction text + a due-date line underneath in
     muted small text ("Due in 2 days" / "Overdue by 1 day" / "Completed Jun 18" once done,
     formatted the same relative-date way reminder-list already does). Completed steps: circle
     fills solid green with a checkmark instead of the number, instruction text gets a subtle
     strikethrough, due-date line shows the completion date instead. Pending steps: circle stays
     outlined, and the row ends with a small "Mark done" button (not a giant CTA — this is a
     repeated list item, keep it compact) that calls the EXISTING POST /api/v1/care/done
     (MarkCareDoneRequest{reminderId: step.id}) and then refetches the plan so the progress line
     and status chip update together — no new completion endpoint needed, steps are reminders
     under the hood.
   - Loading state: skeleton blocks for the header + 3 fake step rows (reuse the pulse-animation
     skeleton pattern already used in care-plan.component and plant-photo-timeline — don't invent
     a fourth skeleton style in this codebase).
   - Error state (plan not found / not owned): same placeholder-tab pattern used elsewhere
     (camera_enhance-style icon swapped for something like `report_problem`, "Treatment plan not
     found", a button back to the plant or to /reminders).

10. reminder-list.component.html — today every reminder row shows its care type as plain text
    ("Watering", "Fertilizing", etc. via the `| titlecase` pipe already in use). For rows where
    reminder.treatmentPlanId is set, replace that plain text with a small pill/chip instead:
    icon `medical_services` + "Step {{ stepOrder }} · {{ treatmentPlanTitle }}", same chip sizing
    and rounded styling as the existing health-badge chips elsewhere in this component, clickable
    (routerLink to /treatment-plans/{{ treatmentPlanId }}) so a user looking at their daily list
    can jump straight into the full plan context instead of just seeing an isolated step.

Style: reuse existing tokens (--color-primary/--color-success/--radius-card/--shadow-card). No new
colors. Mermaid's default theme will look foreign against the app's cream/forest-green palette —
call mermaid.initialize({ theme: 'base', themeVariables: { primaryColor: '#e5efe9', primaryBorderColor:
'#1a3c2a', lineColor: '#7a8c80', fontFamily: 'Inter, Roboto, sans-serif' } }) once at module load
(in MermaidDiagramComponent's constructor or a one-time app-level init) so diagrams match the
design tokens instead of mermaid's defaults.
```
**Verify:** on a care card with a ROUTINE actionPlan, "Set reminder" creates a real reminder
visible in /reminders. On a card with a TREATMENT actionPlan, "Start treatment plan" navigates to
a detail page showing the right number of steps with correct due dates; if the AI included a
diagram, it renders (or silently doesn't, if malformed — no broken UI). Completing every step
flips the plan's status chip to COMPLETED without a page reload being required on next visit.
Disease panel's cure advice, when multi-step, offers "Start treatment plan" alongside the existing
"Add to care plan". `ng build` + `ng lint` clean.

---

### T3.3 — Manual testing — Phase 3 👤 Manual
**Branch:** PR to `dev`

- [ ] Create a watering reminder for a plant, set it due today
- [ ] Manually trigger scheduler (or set time to NOW for testing)
- [ ] Push notification appears in browser?
- [ ] Mark care as done → next due date recalculated correctly?
- [ ] Test on Chrome Android + Safari iOS (PWA installable?)
- [ ] Install PWA to home screen — does it work offline for reading plants?
- [ ] (T3.4/T3.5) Trigger a disease cure-advice with a genuinely multi-step treatment — does
      "Start treatment plan" appear, and do the created reminders have sensible due-date spacing?
- [ ] (T3.4/T3.5) Complete every step of a treatment plan — does it flip to COMPLETED, and does
      each step show up in the plant's Care History tab?
- [ ] (T3.4/T3.5) Find a case where the AI's actionPlan is malformed or absent — confirm the card
      just stays informational with no action buttons, no error shown to the user

---

## PHASE 4 — AI Chat Assistant
> Goal: users can ask natural language questions about their plants.
> Estimated time: Weeks 7–8.

---

### T4.1 — Chat module — full backend 🤖 AI
**Branch:** `feature/PP-013-chat-module`

**Claude Code prompt:**
```
// Phase 4 — Generate the full Chat module backend with SSE streaming

In com.plantpal.chat:

1. DTOs:
   ChatMessageRequest: content (String), history (List<ChatMessageDto> max 10)
   ChatMessageDto: role ("user" | "assistant"), content
   GardenContextDto: built from user's plants — nickname, species, nextCareDue

2. service/ChatService.java (interface):
   SseEmitter chat(ChatMessageRequest request, Long userId)

3. service/impl/ChatServiceImpl.java:
   - Build garden context: fetch user's active plants + their next 3 due reminders
   - Format as readable string: "Plant: Monstera (Monstera deliciosa) | Next: Watering in 2 days"
   - Truncate history to last 10 messages (prevent token bloat)
   - Call claudeApiClient.askQuestion() with context
   - Stream response via SseEmitter (send chunks as they arrive)
   - SseEmitter timeout: 60 seconds
   - On error: send error event then complete
   - @RateLimiter(name = "ai-chat") — limit chat calls per user using Bucket4j
     (configure: 10 messages per hour per user)
   - Log: userId, question length (truncated to 50 chars), response time

4. controller/ChatController.java:
   POST /api/v1/chat/message
   produces = MediaType.TEXT_EVENT_STREAM_VALUE
   Returns SseEmitter

5. Unit tests: mock ClaudeApiClient, verify garden context is injected,
   verify history is capped at 10 messages.
```

---

### T4.2 — Chat module — Angular frontend 🤝 Assisted
**Branch:** `feature/PP-014-chat-frontend`

**Claude Code prompt:**
```
// Phase 4 — Generate the Chat Angular feature module

1. features/chat/ — full module

2. services/chat.service.ts:
   - sendMessage(request: ChatMessageRequest): Observable<string>
   - Use EventSource or fetch with ReadableStream to consume the SSE endpoint
   - Emit text chunks progressively as they arrive

3. components/chat-interface/:
   - Message bubbles: user messages right-aligned, AI messages left-aligned
   - Typing indicator ("PlantPal is thinking...") while streaming
   - Auto-scroll to bottom on new content
   - Input bar + send button (disabled while response streaming)
   - "Clear conversation" button
   - Display garden context summary at top: "I know about your 3 plants"

4. History management in the component:
   - Keep in-memory (no persistence in MVP)
   - Cap at 10 exchanges before sending to service
   - Show total message count if near limit

5. Welcome message on first load:
   "Hi! I'm PlantPal. I know about your [N] plants. Ask me anything!"
```

---

### T4.3 — Manual testing — Phase 4 👤 Manual
**Branch:** PR to `dev`

Real conversation tests:
- [ ] "When should I water my [plant nickname]?" — gives answer specific to that plant?
- [ ] "My [plant] has yellow leaves, what's wrong?" — useful diagnosis?
- [ ] "What plants go well with a monstera?" — general answer (not in garden)?
- [ ] Does Claude remember context mid-conversation (multi-turn)?
- [ ] Does streaming display progressively (not all at once)?
- [ ] Send 11th message — rate limit kicks in gracefully?
- [ ] What happens if API key is invalid? (graceful error message)

---

## PHASE 5 — Launch Preparation
> Goal: deploy to production, beta test, release v1.0.0.
> Estimated time: Weeks 9–10.

---

### T5.1 — Production configuration 🤖 AI
**Branch:** `feature/PP-015-prod-config`

**Claude Code prompt:**
```
// Phase 5 — Generate production and staging Spring Boot configurations

1. application-staging.yml + application-prod.yml:
   - datasource: ${DATABASE_URL} (Railway injects this)
   - HikariCP: maximumPoolSize=20, minimumIdle=5, connectionTimeout=20000
   - Redis: ${REDIS_URL} (Railway Redis add-on)
   - Liquibase: enabled=true (runs migrations on startup)
   - show-sql: false
   - Logging: level INFO for com.plantpal, WARN for Spring internals, JSON format
   - Actuator: expose only health and info (not env, beans, etc.)
   - JPA: ddl-auto=validate (never create or update — Liquibase owns schema)
   - CORS: allowed origins from ${ALLOWED_ORIGINS} env var

2. Add Logback configuration for prod (logback-spring.xml):
   - JSON format in staging/prod profiles (structured logs — searchable in Railway)
   - Pattern format in dev (human readable)
   - Log correlation ID (from MDC) in every line
   - Structured JSON key: timestamp, level, correlationId, userId, message, exception

3. Add rate limit configuration to application.yml:
   app.rate-limit.ai-calls-per-hour: 20
   app.rate-limit.auth-attempts-per-minute: 5
   app.rate-limit.chat-messages-per-hour: 10
```

> 💡 **Why structured JSON logs:** When you have logs in Railway/CloudWatch/Datadog,
> JSON logs are searchable and filterable. You can find "all errors for userId 42 in the
> last hour" with a single query. Plain text logs require grep and guesswork.

---

### T5.2 — Performance optimizations 🤝 Assisted
**Branch:** `feature/PP-016-performance`

**Claude Code prompt:**
```
// Phase 5 — Add performance optimizations

1. New Liquibase migration 007_add_performance_indexes.sql:
   - Composite index on identifications(plant_id, created_at DESC)
   - Partial index on reminders(next_due_at) WHERE enabled = TRUE (already in 004, verify it exists)
   - Index on care_logs(user_id, performed_at DESC) for dashboard queries

2. Add @Cacheable to hot read paths:
   - PlantServiceImpl.getUserPlants → cache key "plants::{userId}", TTL 5 min
   - PlantServiceImpl.getPlant → cache key "plant::{plantId}", TTL 10 min
   - ChatServiceImpl.buildGardenContext → cache key "garden::{userId}", TTL 5 min
   - Add @CacheEvict on all mutation methods (create, update, archive)

3. Angular bundle optimization:
   - Verify all feature modules are lazy-loaded (check ng build --stats-json)
   - Add OnPush ChangeDetectionStrategy to all list components
   - Add trackBy functions to all *ngFor directives
```

---

### T5.3 — Security hardening 🤖 AI
**Branch:** `feature/PP-016-performance` (same branch)

**Claude Code prompt:**
```
// Phase 5 — Add security hardening

1. Add security headers to SecurityConfig.java:
   - X-Content-Type-Options: nosniff
   - X-Frame-Options: DENY
   - X-XSS-Protection: 1; mode=block
   - Strict-Transport-Security (HSTS) in prod profile only
   - Content-Security-Policy: basic policy allowing own origins

2. Add rate limiting to AuthController using Bucket4j:
   - POST /auth/login: 5 attempts per minute per IP
   - POST /auth/register: 3 registrations per hour per IP
   - Return 429 Too Many Requests with Retry-After header when exceeded

3. Add input sanitization:
   - Plant nickname, notes, location: strip HTML tags (use OWASP Java HTML Sanitizer)
   - Chat messages: max 2000 characters, validated at controller level

4. Add a dependency vulnerability check to the Maven build:
   - OWASP Dependency Check plugin
   - Fail build on CVSS score >= 7 (high severity)
```

> 💡 **Why this matters for a "small" app:** Security habits are learned, not bolted on.
> Building these patterns now means they're automatic when you work on bigger projects.
> Rate limiting on auth endpoints is the difference between "hobby app" and "enterprise app".

---

### T5.4 — Complete API documentation 🤖 AI
**Branch:** `feature/PP-016-performance` (same branch)

**Claude Code prompt:**
```
// Phase 5 — Complete OpenAPI documentation for all controllers

Add @Operation, @ApiResponse, @Parameter annotations to:
PlantController, IdentificationController, ReminderController,
ChatController, AuthController, CareLogController, NotificationController.

For each endpoint include:
- @Operation(summary = "...", description = "...")
- @ApiResponse for 200/201, 400, 401, 403, 404, 429, 500
- @Schema examples on all DTOs

Update OpenApiConfig.java:
- Title: "PlantPal API", version: "1.0.0"
- Description: "Enterprise-grade plant care API"
- JWT authentication button (users can authorize in Swagger UI and test endpoints)
- Server URLs for dev and prod
```

---

### T5.5 — Production deployment 👤 Manual

Steps:
1. Create Railway project → add PostgreSQL plugin → add Redis plugin
2. Set all environment variables in Railway (from `.env.example`)
3. Point Railway to GitHub repo → auto-deploy on push to `main`
4. Configure Vercel for frontend → link Angular build output
5. Set `environment.prod.ts` API URL to Railway backend URL
6. Merge a test commit to `main` → verify both deployments succeed
7. Check `https://your-backend.railway.app/actuator/health` → `{"status":"UP"}`
8. Check `https://your-frontend.vercel.app` → app loads and login works

---

### T5.6 — Beta testing 👤 Manual
> Recruit 5–10 people (friends, family) who own plants.

Test scenarios:
- [ ] Full journey: register → identify a plant → create reminder → receive notification
- [ ] Mobile: Chrome Android + Safari iOS
- [ ] Install PWA to home screen
- [ ] Ask the chat assistant "why are my leaves yellowing?"
- [ ] Collect: confusing UX moments, incorrect identifications, missing features

---

### T5.7 — Beta bug fixes 🤝 Assisted
**Branch:** `bugfix/PP-{num}-{description}` per bug

For each beta bug:
1. Create dedicated `bugfix/PP-{N}` branch from `dev`
2. If the bug is in a service, ask Claude Code for a targeted fix
3. **Always add a test that would have caught the bug first**
4. PR → `dev` with description of the bug, root cause, and fix

---

### T5.8 — Release v1.0.0 👤 Manual

```bash
git checkout dev && git pull origin dev
git checkout -b release/v1.0.0

# Update version in pom.xml
mvn versions:set -DnewVersion=1.0.0
mvn versions:commit

# Write CHANGELOG.md

git commit -m "chore(release): prepare version 1.0.0"

# Merge into main
git checkout main
git merge --no-ff release/v1.0.0
git tag -a v1.0.0 -m "PlantPal v1.0.0 — MVP Launch"
git push origin main --tags

# Merge back into dev
git checkout dev
git merge --no-ff release/v1.0.0
git push origin dev

git branch -d release/v1.0.0
git push origin --delete release/v1.0.0
```

---

## PHASE 6 — Species & Treatment Domain Restructure
> Planned 2026-06-19 (session 18). Restructures the core domain from plant-centric to
> species-centric, adds a Treatment system, redesigns the Plant page navigation, and adds a Home
> screen. See ARCHITECT.md's "Phase 6" section for the full domain model, the identification
> 3-path decision tree, and the Treatment lifecycle state machine — read it before starting any
> task below.
>
> ⚠️ **Numbered Phase 6 instead of Phase 2 / "T3.x" as originally requested** — T3.1–T3.9 and
> migrations 012–015 are already in use (Reminder + Care Log + Actionable Care Plans), and a
> pre-existing "Phase 5 — Launch" (T5.1–T5.8, above) already claimed Phase 5. See STATE.md's
> "Phase 6" section and `.claude/PHASE6_SESSION_PROGRESS.md` for the full rationale.
>
> **Recommended execution order:** T6.1 → T6.4 → T6.2 → T6.3 → (T6.5/T6.6 and T6.7/T6.8 in
> parallel) → T6.9 → T6.10 → T6.11 → T6.12 → T6.14 → T6.13. T6.4 and T6.14 can also land on their
> parent branch (T6.1's / T6.2's) rather than opening separate PRs — noted per task below.

---

### T6.1 — Species entity + migrations + endpoints 🤖 AI
**Branch:** `feature/PP-029-species-entity`
**Depends on:** None

> **Why:** `Species` is the new shared-across-users entity everything else in Phase 6 hangs off
> of — Plant's new `speciesId` FK, the Garden species-first restructure, and the Species detail
> page all need it to exist first.

**Backend Claude Code prompt:**
```
// T6.1 — Species entity, migration, repository, service, endpoints

Read CLAUDE.md, BACKEND.md, ARCHITECT.md's "Phase 6" section (Species entity definition) before
starting.

In com.plantpal.species (NEW package):

1. entity/Species.java (extends AuditableEntity):
   Fields: id, scientificName (String, unique, @NotBlank), commonName (String),
   description (TEXT, nullable), careOverview (TEXT, nullable), imageUrl (TEXT, nullable),
   externalDataSource (String, nullable — "AI" | "WIKIPEDIA" | "MANUAL"),
   externalDataFetchedAt (Instant, nullable), status (SpeciesStatus, default ACTIVE)
   @Entity @Table(name = "species") @Getter @Setter @Builder (NOT @Data — JPA entity)

   ⚠️ Species is SHARED across users — unlike every other entity in this codebase, it has no
   userId/ownership field. Do not add one. Service methods must never apply a per-user ownership
   check directly on a Species row (only on the Plant rows that reference it).

2. entity/SpeciesStatus.java enum: ACTIVE, NEEDS_REVIEW

3. Migration backend/src/main/resources/db/changelog/migrations/016_create_species.sql:
   CREATE TABLE IF NOT EXISTS species (
     id BIGSERIAL PRIMARY KEY,
     scientific_name VARCHAR(255) NOT NULL UNIQUE,
     common_name VARCHAR(255),
     description TEXT,
     care_overview TEXT,
     image_url TEXT,
     external_data_source VARCHAR(20),
     external_data_fetched_at TIMESTAMPTZ,
     status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
     created_by BIGINT,
     updated_by BIGINT
   );
   CREATE INDEX IF NOT EXISTS idx_species_scientific_name ON species(scientific_name);
   Register as the 16th entry in db.changelog-master.xml, immediately after
   015_add_ai_model_used.sql's entry — do NOT insert it earlier in the list.

4. dto/SpeciesResponse.java: id, scientificName, commonName, description, careOverview,
   imageUrl, externalDataSource, status (all fields — this is the Species detail page's main DTO)

5. dto/SpeciesSummaryDto.java (for the Garden species-first list — see endpoint 3 below):
   speciesId, scientificName, commonName, imageUrl, plantCount (int),
   healthSummary (String — "All healthy" | "N issue(s)", computed not stored)

6. mapper/SpeciesMapper.java (MapStruct): toResponse(Species) → SpeciesResponse

7. repository/SpeciesRepository.java:
   findByScientificName(String scientificName): Optional<Species>
   existsByScientificName(String scientificName): boolean

8. service/SpeciesService.java (interface) + service/impl/SpeciesServiceImpl.java:
   - findOrCreate(String scientificName, String commonName): Species
     — looks up by scientificName first; on miss, creates with status=ACTIVE and fires
     SpeciesEnrichmentService.enrich(speciesId) asynchronously (T6.4 — inject as an optional
     dependency for now; T6.4 will implement the actual enrichment, this method just needs to
     call it). NEVER block on enrichment — identification flow must not wait for it.
   - getSpecies(Long id): SpeciesResponse — throws ResourceNotFoundException if missing
     (no ownership check — Species has no owner)
   - getUserSpecies(Long userId, Pageable pageable): Page<SpeciesSummaryDto> — joins the
     caller's ACTIVE Plants grouped by speciesId; for each distinct species the user owns at
     least one plant of, compute plantCount and healthSummary from those plants' latest
     identification healthStatus (reuse the existing
     IdentificationRepository.findLatestPerPlant() batch-fetch pattern from T2.10a to avoid N+1
     — do not write a second one-off query for this). Plants with speciesId IS NULL (legacy,
     pre-Phase-6 plants) are excluded from this list — they have nothing to group by yet.

9. controller/SpeciesController.java:
   GET /api/v1/species/{id} → ApiResponse<SpeciesResponse> (public read, no ownership check)
   GET /api/v1/species/mine → ApiResponse<Page<SpeciesSummaryDto>> (@PageableDefault size=20,
     userId from SecurityContext — same getCurrentUserId() pattern as every other controller)
   No POST/PUT/DELETE — Species rows are only ever created via findOrCreate() from the
   identification flow (T6.9), never directly by a user-facing endpoint.

10. Unit tests (SpeciesServiceTest):
    - findOrCreate: existing scientificName → returns existing row, does NOT create a duplicate
    - findOrCreate: new scientificName → creates with status=ACTIVE, enrichment fired
      (verify the enrichment call happened, don't assert on its result — that's T6.4's test)
    - getSpecies: not found → ResourceNotFoundException
    - getUserSpecies: plants grouped correctly, plantCount accurate, plants with null speciesId
      excluded

Follow CLAUDE.md's mandatory class member order and service-interface-first pattern.
```

**Verify:** `mvn clean compile` passes. `GET /api/v1/species/{id}` for a manually-inserted row
returns 200. `db.changelog-master.xml` lists `016_create_species.sql` after `015`.

---

### T6.2 — Treatment entity + migrations + endpoints 🤖 AI
**Branch:** `feature/PP-030-treatment-entity`
**Depends on:** T6.1

> **Read ARCHITECT.md's "Two Treatment concepts" note before starting.** This task introduces a
> NEW `Treatment` entity that is NOT the same as the existing `TreatmentPlan` (T3.4). The
> recommended design (below) has `Treatment` wrap a `TreatmentPlan` rather than duplicating its
> careCard/actionPlan JSON storage — re-evaluate only if there's a concrete reason this doesn't
> fit once you're looking at the real `TreatmentPlanService` code.

**Backend Claude Code prompt:**
```
// T6.2 — Treatment entity (disease-treatment lifecycle), migration, service, endpoints

Read ARCHITECT.md's "Phase 6" section (Treatment entity + lifecycle state machine + the "Two
Treatment concepts" disambiguation), TreatmentPlanService.java, TreatmentPlanServiceImpl.java,
and ReminderService.applyCompletionToReminder() before starting.

In com.plantpal.treatment (NEW package — deliberately NOT com.plantpal.reminder, even though it
will end up depending on TreatmentPlanService there):

1. entity/Treatment.java — does NOT extend AuditableEntity (same no-audit-columns exception as
   Reminder/TreatmentPlan — use @CreationTimestamp/@UpdateTimestamp instead):
   Fields: id, plantId, userId, identificationId, diseaseName, diseaseDescription (TEXT,
   nullable — filled async, see below), status (TreatmentStatus), treatmentPlanId (Long,
   nullable FK to treatment_plans.id — set once craft-plan has run), startedAt (Instant,
   nullable — set when status moves to IN_PROGRESS), completedAt (Instant, nullable)

2. entity/TreatmentStatus.java enum: DRAFT, IN_PROGRESS, COMPLETED, DISMISSED

3. Migration backend/src/main/resources/db/changelog/migrations/018_create_treatments.sql:
   CREATE TABLE IF NOT EXISTS treatments (
     id BIGSERIAL PRIMARY KEY,
     plant_id BIGINT NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
     user_id BIGINT NOT NULL REFERENCES users(id),
     identification_id BIGINT REFERENCES identifications(id),
     disease_name VARCHAR(255) NOT NULL,
     disease_description TEXT,
     status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
     treatment_plan_id BIGINT REFERENCES treatment_plans(id),
     started_at TIMESTAMPTZ,
     completed_at TIMESTAMPTZ,
     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
   );
   CREATE INDEX IF NOT EXISTS idx_treatments_plant_id ON treatments(plant_id);
   CREATE UNIQUE INDEX IF NOT EXISTS idx_treatments_active_per_disease
     ON treatments(plant_id, disease_name) WHERE status IN ('DRAFT', 'IN_PROGRESS');
   Register as the 18th changelog entry, after 017 (T6.3 — note 017 may not exist yet if T6.2
   lands before T6.3; if so, register 018 directly after 016 and let T6.3 insert 017 in its
   correct numeric position when it lands — do NOT renumber 018 to fill the gap).

   ⚠️ The partial unique index is what enforces "one active Treatment per plant+disease" at the
   DB level — don't rely on application-code checking alone for this invariant.

4. dto/TreatmentResponse.java: id, plantId, diseaseName, diseaseDescription, status,
   treatmentPlanId (nullable), startedAt, completedAt
5. dto/CreateTreatmentRequest.java: plantId (@NotNull), identificationId (@NotNull),
   diseaseName (@NotBlank)

6. repository/TreatmentRepository.java: findByIdAndUserId, findByPlantIdAndUserId,
   findByPlantIdAndDiseaseNameAndStatusIn(plantId, diseaseName, List<TreatmentStatus>)
   [for the one-active-per-disease check before insert — belt-and-suspenders with the DB
   constraint above], findByTreatmentPlanId [for the completion-sync hook in T6.14]

7. service/TreatmentService.java (interface) + service/impl/TreatmentServiceImpl.java:
   - createTreatment(CreateTreatmentRequest, userId): TreatmentResponse
     — ownership-check the plant (plantRepository.findByIdAndUserId), reject if an active
     (DRAFT/IN_PROGRESS) Treatment already exists for this plant+diseaseName
     (ValidationException — the DB unique index is the backstop, not the primary check), create
     with status=DRAFT, fire an async call to generate diseaseDescription (reuse
     DeepSeekClient or GitHubModelsClient — text-only prompt: "explain what {diseaseName} is,
     why it happens, and the risk if left untreated, 2-4 sentences, plain English for a
     beginner gardener" — same "never block, degrade to null on failure" philosophy as T6.4's
     species enrichment)
   - craftPlan(id, userId): TreatmentResponse — ownership-checked (load Treatment, verify
     plant ownership), must be DRAFT (ValidationException otherwise), generates the treatment's
     actionPlan (reuse the existing AI care-plan generation shape — a single TREATMENT-type
     ActionPlanDto), then delegates to the EXISTING
     `TreatmentPlanService.createFromActionPlan(plantId, userId, diseaseName, "PEST",
     actionPlan)` rather than re-implementing reminder creation — stores the returned plan's id
     as treatmentPlanId, sets status=IN_PROGRESS, startedAt=now, and sets
     plant.activeTreatmentId = treatment.id (PlantRepository — requires T6.3's
     activeTreatmentId column; if T6.3 hasn't landed yet, leave this line as a clearly marked
     TODO rather than guessing the column name)
   - getTreatment(id, userId): TreatmentResponse — ownership-checked
   - getActiveTreatmentForPlant(plantId, userId): TreatmentResponse or null — used by
     GET /api/v1/plants/{id}/active-treatment
   - completeTreatment(id, userId): TreatmentResponse — ownership-checked, must be IN_PROGRESS,
     sets status=COMPLETED, completedAt=now, plant.activeTreatmentId=null

8. controller/TreatmentController.java:
   POST   /api/v1/treatments                       → 201, TreatmentResponse
   POST   /api/v1/treatments/{id}/craft-plan        → 200, TreatmentResponse
   GET    /api/v1/treatments/{id}                   → 200, TreatmentResponse
   GET    /api/v1/plants/{id}/active-treatment      → 200, TreatmentResponse or 404 if none
   PATCH  /api/v1/treatments/{id}/complete          → 200, TreatmentResponse

9. Unit tests (TreatmentServiceTest):
   - createTreatment: success; rejects when an active Treatment for the same plant+disease
     already exists; ownership check (wrong user → ResourceNotFoundException)
   - craftPlan: success path delegates to TreatmentPlanService.createFromActionPlan with the
     right args; rejects if not DRAFT; sets plant.activeTreatmentId
   - completeTreatment: success, rejects if not IN_PROGRESS, clears plant.activeTreatmentId
```

**Verify:** `mvn clean compile` passes. `POST /api/v1/treatments` then
`POST /api/v1/treatments/{id}/craft-plan` produces a `TreatmentPlanResponse`-shaped result
reachable via the existing `GET /api/v1/treatment-plans/{treatmentPlanId}` (T3.4 endpoint,
unchanged) — confirms the wrap-don't-duplicate design actually works end to end.

---

### T6.3 — Plant entity updates + scan flow changes 🤖 AI
**Branch:** `feature/PP-031-plant-species-fk`
**Depends on:** T6.1, T6.2

**Backend Claude Code prompt:**
```
// T6.3 — Plant gains speciesId/lastScanId/activeTreatmentId; Identification gains
// plantId-nullable + speciesId

Read Plant.java, PlantServiceImpl.java, PlantMapper.java, Identification.java,
IdentificationServiceImpl.processIdentification() before starting.

1. Migration backend/src/main/resources/db/changelog/migrations/017_alter_plants_add_species_fk.sql:
   ALTER TABLE plants
     ADD COLUMN IF NOT EXISTS species_id BIGINT REFERENCES species(id),
     ADD COLUMN IF NOT EXISTS last_scan_id BIGINT REFERENCES identifications(id),
     ADD COLUMN IF NOT EXISTS active_treatment_id BIGINT REFERENCES treatments(id);
   -- The legacy `species` TEXT column is deliberately NOT dropped in this migration — existing
   -- rows keep their free-text species name as a fallback display value until every Plant has
   -- been backfilled with a speciesId via a re-scan. Drop it in a LATER cleanup migration once
   -- speciesId backfill is confirmed complete in prod, not here.
   CREATE INDEX IF NOT EXISTS idx_plants_species_id ON plants(species_id);
   Register as changelog entry 17 (between 016 and 018 — if T6.2 already landed and registered
   018 directly after 016, move 018's XML entry so the final order is 016→017→018; the FILE
   numbers don't need to change, only the listed order in db.changelog-master.xml does).

2. Migration backend/src/main/resources/db/changelog/migrations/019_alter_identifications_add_plant_species_fk.sql:
   ALTER TABLE identifications ALTER COLUMN plant_id DROP NOT NULL;
   ALTER TABLE identifications ADD COLUMN IF NOT EXISTS species_id BIGINT REFERENCES species(id);
   -- plant_id was already nullable in the original 003 migration per CLAUDE.md's schema — verify
   -- this against the live schema before assuming the DROP NOT NULL is even needed; if it's
   -- already nullable, this migration only needs the ADD COLUMN line. Check, don't assume.
   Register as changelog entry 19, after 018.

3. Plant.java entity: add speciesId (Long, nullable), lastScanId (Long, nullable),
   activeTreatmentId (Long, nullable). Keep the existing `species` (String) field — do not
   remove it from the entity yet (see migration note above on why the column stays for now).

4. PlantResponse.java: add speciesId, lastScanId, activeTreatmentId (all nullable) — these are
   what the redesigned Plant page (T6.10/T6.11) and Home page (T6.7) need to decide which icon
   buttons to show (e.g. "treatment" tab only when activeTreatmentId != null).

5. PlantMapper.java: pick up the 3 new fields automatically if names match (MapStruct default
   field-name mapping) — verify, don't assume; add explicit @Mapping only if it doesn't.

6. Identification.java entity: add speciesId (Long, nullable). plantId field's @Column
   nullable=true (confirm matches the migration).

7. IdentificationResponse.java: add speciesId (nullable).

8. IdentificationServiceImpl.processIdentification(): when status becomes COMPLETED AND
   entity.getPlantId() is non-null, update plant.lastScanId = identification.getId() (load the
   Plant, set, save) — this is the ONLY scan-flow change in this task; the species-matching
   logic (deciding speciesId/plantId in the first place) is T6.9's job, not this task's. This
   task just makes sure that ONCE a scan is tied to a plant, that plant's lastScanId stays
   current.

9. Unit tests:
   - PlantServiceTest: PlantResponse includes the 3 new fields when present on the entity
   - IdentificationServiceImplTest: processIdentification COMPLETED with non-null plantId →
     plant.lastScanId updated to the new identification's id; processIdentification with
     plantId null (species-level scan) → no plant lookup attempted, no exception
```

**Verify:** `mvn clean compile` passes. Full migration sequence `001→...→019` applies cleanly
against a fresh database. Existing `PlantControllerIT`/`IdentificationServiceImplTest` suites
still pass unmodified except where explicitly extended above.

---

### T6.4 — Species data enrichment async service 🤖 AI
**Branch:** `feature/PP-029-species-entity` (same branch as T6.1 — same package, same session)
**Depends on:** T6.1

> See ARCHITECT.md's "Species data enrichment — async pattern" section for the full design —
> this prompt just wires it up.

**Backend Claude Code prompt:**
```
// T6.4 — SpeciesEnrichmentService: async AI-sourced description/careOverview/imageUrl

Read ARCHITECT.md's "Species data enrichment" section, DeepSeekClient.java (for the
text-completion call pattern), and SpeciesServiceImpl.findOrCreate() (T6.1) before starting.

In com.plantpal.species:

1. service/SpeciesEnrichmentService.java (interface) + service/impl/SpeciesEnrichmentServiceImpl.java:
   - @Async("aiTaskExecutor")
     void enrich(Long speciesId)
     1. Load the Species row (if missing, log WARN and return — don't throw from an @Async
        fire-and-forget method)
     2. Call deepSeekClient (or a new method on it — reuse the existing text-completion call
        shape, do NOT create a third AI client class for this) with a prompt asking for:
        description (2-4 sentences), careOverview (1-2 sentence summary), imageUrl (a
        representative photo URL), source: "AI". Return ONLY valid JSON, same
        "no markdown, no preamble" instruction style as every other system prompt in this
        codebase.
     3. Parse the response the same defensive way as parseCarePlan()/parseAnnotationRegions() —
        on success: update description/careOverview/imageUrl/externalDataSource="AI"/
        externalDataFetchedAt=now, status stays ACTIVE. On ANY failure (AI error, malformed
        JSON, timeout): log WARN, set status=NEEDS_REVIEW, leave description/careOverview/
        imageUrl as null. NEVER throw out of this method — it's fire-and-forget, called from
        SpeciesServiceImpl.findOrCreate() with no caller waiting on the result.

2. Update SpeciesServiceImpl.findOrCreate() (T6.1) to actually call
   speciesEnrichmentService.enrich(species.getId()) after the initial save, instead of the
   placeholder/TODO left there in T6.1.

3. Confirm (do not just assume) that this enrichment call does NOT consume the same per-user
   identification rate-limit bucket (Bucket4j) as the main identification flow — enrichment is
   triggered by Species creation, not directly by a user action, and has no natural per-user
   key to rate-limit against anyway (Species is shared). No new Bucket4j bucket needed unless
   you find evidence during implementation that GitHub Models' daily cap is shared in a way that
   makes this a real risk — don't add a bucket speculatively if it isn't.

4. Unit tests (SpeciesEnrichmentServiceImplTest):
   - Successful AI response → all 4 fields populated, status stays ACTIVE
   - Malformed JSON → status flips to NEEDS_REVIEW, fields stay null, no exception thrown
   - AI client throws → status flips to NEEDS_REVIEW, no exception propagated
   - Species not found → logs WARN, returns cleanly, no exception
```

**Verify:** Creating a Plant via Flow 1 (new species) results in the Species row's
`description`/`careOverview` becoming populated within a few seconds, visible via
`GET /api/v1/species/{id}`, without the identification request itself being slowed down.

---

### T6.5 — Garden species-first restructure 🤖 AI
**Branch:** `feature/PP-032-garden-species-first`
**Depends on:** T6.1, T6.3

**Frontend Claude Code prompt:**
```
// T6.5 — Garden page: Species cards instead of Plant cards

Read FRONTEND.md, plant-list.component.ts/html/scss, ARCHITECT.md's "Phase 6" section before
starting. Backend endpoint GET /api/v1/species/mine (T6.1) is the data source — confirm it
exists and matches the shape below before wiring against it.

1. features/species/ (NEW lazy module — species.module.ts, species-routing.module.ts):
   Route: /garden → SpeciesListComponent (this REPLACES plant-list as the /garden landing
   content — do not delete PlantModule/plant-list, they're still used for plant-detail and
   reachable from a species's "Plants" tab in T6.6)

2. models/species.model.ts: SpeciesSummaryDto { speciesId, scientificName, commonName,
   imageUrl, plantCount, healthSummary }

3. services/species.service.ts: getMySpecies(page, size) → GET /api/v1/species/mine

4. components/species-card/: representative image, scientific name (font-heading serif,
   matches DESIGN_PROGRESS.md's plant-card treatment), common name subtitle, plant count badge
   ("3 plants"), health summary chip (mint "All healthy" / coral "N issue(s)") — same visual
   token set as plant-card.component.scss (--radius-card, --shadow-card, etc. from
   DESIGN_PROGRESS.md), do not invent new tokens.

5. pages/species-list/: "My Garden" heading (unchanged from current plant-list), grid of
   species-card (not plant-card), FAB opens the existing IdentificationUploadDialogComponent
   (same dialog plant-list already uses — Flow 1 entry point per ARCHITECT.md's decision tree).
   Filter chips: All / With Issues / Recently scanned (client-side filter over the already-
   fetched page — do not add new backend query params for this in T6.5; if pagination makes
   client-side filtering wrong, flag it rather than silently fetching all pages).

6. app-routing.module.ts: '/garden' now lazy-loads SpeciesModule instead of PlantModule.
   PlantModule's own routing keeps '/plants/:id' (detail) — only the LIST route moves.

No backend changes in this task — GET /api/v1/species/mine already returns everything needed.
```

**Verify:** `/garden` shows species cards (not plant cards) for a user with 2 plants of the same
species — exactly one card, "2 plants" badge. `ng build`/`ng lint` clean.

---

### T6.6 — Species detail page 🤖 AI
**Branch:** `feature/PP-032-garden-species-first` (same branch as T6.5)
**Depends on:** T6.1, T6.4

**Frontend Claude Code prompt:**
```
// T6.6 — Species detail page: /garden/species/:id, Overview + Plants tabs

Read species.service.ts (T6.5), plant-detail.component.ts (for the existing tab pattern — this
page uses mat-tab-group, NOT the new icon-button-bar from T6.10; that pattern is Plant-page-only
for this phase) before starting.

1. species.service.ts: add getSpecies(id) → GET /api/v1/species/{id} → SpeciesResponse
   species.model.ts: add SpeciesResponse { id, scientificName, commonName, description,
   careOverview, imageUrl, externalDataSource, status }

2. plant.service.ts: confirm getUserPlants already supports filtering — if not, add a
   speciesId query param to GET /api/v1/plants (backend: PlantController accepts an optional
   @RequestParam Long speciesId, PlantRepository gains findAllByUserIdAndSpeciesIdAndStatus).
   Flag this as a small backend addition if it turns out getUserPlants has no way to filter by
   species — check before assuming.

3. pages/species-detail/ (route '/garden/species/:id'):
   mat-tab-group, two tabs:
   - "Overview": hero image (species.imageUrl, graceful placeholder if null), scientific name
     (font-heading serif, large), common name subtitle, description (handle null as a
     "Gathering info about this species…" pending state per ARCHITECT.md's enrichment-pattern
     note — NOT an error state), care overview summary, externalDataSource badge ("AI" /
     "Wikipedia") only rendered when non-null
   - "Plants": list of the current user's plants of this species (via the speciesId-filtered
     plant query above), each row: nickname + photo + health badge + last scan date (reuse
     existing plant-card.component or a slimmer row variant — do not duplicate the badge-color
     logic, import the existing health-badge styling), "Add plant of this species" button opens
     IdentificationUploadDialogComponent pre-seeded with the known speciesId (Flow 2 entry
     point per ARCHITECT.md — full wiring of Flow 2's skip-confirmation behavior is T6.9's job;
     this button just needs to open the dialog with the right context, not implement Flow 2
     itself)

3. species.module.ts: declare SpeciesDetailComponent, route it in species-routing.module.ts
```

**Verify:** Navigating to a species with `description: null` shows the pending state, not a
blank/error page. A species with 0 plants on the "Plants" tab shows an empty state with the
"Add plant" CTA, not a silent blank list.

---

### T6.7 — Home page 🤖 AI
**Branch:** `feature/PP-033-home-page`
**Depends on:** T6.3

> **Backend note before starting:** the brief's Home page content (quick stats, needs
> attention, care due today, recent scans) overlaps heavily with the EXISTING
> `GET /api/v1/dashboard` (T2.10b) — `healthSummary`, `overdueReminders`, `todayReminders` are
> already there. Only "recent scans" (last 3 identifications) and a species count (vs. plant
> count) are missing. Extend `DashboardResponse` rather than building a parallel endpoint — see
> step 1 below. This is a small backend addition bundled into this otherwise-frontend task
> because the gap is too small to justify a separate task.

**Backend Claude Code prompt (small, bundle into the same PR as the frontend below):**
```
// T6.7 (backend half) — Extend DashboardResponse with recentScans + speciesCount

Read DashboardServiceImpl.java, DashboardResponse.java before starting.

1. DashboardResponse.java: add List<RecentScanDto> recentScans, int speciesCount
2. New dto/RecentScanDto.java: identificationId, plantId (nullable), plantNickname (nullable —
   null for species-level scans), photoUrl, healthStatus, createdAt
3. DashboardServiceImpl.getDashboard(): fetch the user's last 3 identifications across all
   plants (reuse IdentificationRepository.findByUserIdOrderByCreatedAtDesc — already exists
   from T2.D2 — with PageRequest.of(0, 3)); speciesCount via a new
   SpeciesRepository-adjacent count, OR simpler: count distinct non-null plant.speciesId among
   the user's ACTIVE plants (avoid adding a new repository method to SpeciesRepository for a
   single count if PlantRepository can answer it directly — prefer the simpler query).
4. Unit test: DashboardServiceTest — recentScans populated and ordered newest-first,
   speciesCount correct for a user with 2 plants of the same species (count = 1, not 2)
```

**Frontend Claude Code prompt:**
```
// T6.7 (frontend) — Home page: /home, default landing after login

Read dashboard.service.ts, garden-dashboard.component.ts (T2.10), app-routing.module.ts before
starting.

1. dashboard.model.ts: add RecentScanDto, recentScans + speciesCount on DashboardResponse
   (mirrors the backend additions above field-for-field)

2. pages/home/ (NEW, in the existing DashboardModule — do not create a new lazy module just for
   this; Home and the existing garden-dashboard are closely related and already share
   DashboardService):
   - Greeting: "Good morning, {firstName}" (time-of-day logic: morning <12:00, afternoon <18:00,
     evening after — local browser time, no backend involvement)
   - Quick stats row: speciesCount, healthSummary.issuesCount, todayReminders.length
   - "Needs attention" section: plants with ISSUES_DETECTED last scan (reuse
     garden-dashboard's existing rendering for this if it already has an equivalent section —
     check before duplicating)
   - "Care due today": top 3 of todayReminders
   - "Recent scans": recentScans, each → /identify/:id (existing route)
   - FAB (camera icon) → opens IdentificationUploadDialogComponent (same dialog used
     everywhere else — Flow 1 entry point)
   - Empty/zero-plant state: distinct from garden-dashboard's existing empty state — Home's
     empty state should point to "Identify your first plant" rather than assuming any data
     exists at all

3. app-routing.module.ts: root redirect changes from 'dashboard' to 'home'. Keep the existing
   '/dashboard' route working (garden-dashboard stays reachable, just no longer the default
   landing) — do not delete GardenDashboardComponent, the two pages serve different purposes
   (Home = quick glance + entry points, Dashboard = the fuller health-trends view from T2.10).

4. dashboard.module.ts: declare HomeComponent, route it
```

**Verify:** Logging in lands on `/home`, not `/dashboard`. `/dashboard` still works when
navigated to directly. Backend: `GET /api/v1/dashboard` response includes `recentScans` (≤3
items, newest first) and `speciesCount`.

---

### T6.8 — Bottom nav 5 items + routing 🤖 AI
**Branch:** `feature/PP-033-home-page` (same branch as T6.7)
**Depends on:** T6.7

**Frontend Claude Code prompt:**
```
// T6.8 — Bottom nav: Home | Garden | Identify | Reminders | Chat (5 items, was 4)

Read app.component.html/scss/ts, DESIGN_PROGRESS.md's "Bottom Nav" section before starting.

1. app.component.ts: navLinks array gains a 5th entry — Home (icon: home), inserted FIRST
   (Home | Garden | Identify | Reminders | Chat). Garden's existing icon (local_florist) and
   Identify's existing icon (document_scanner) + raised-circle active style are UNCHANGED — only
   add Home, don't restyle the other four.

2. app.component.html: bottom nav now renders 5 routerLink items instead of 4. The "Identify"
   button keeps its existing raised/circle active treatment (per DESIGN_PROGRESS.md, this is a
   deliberate visual emphasis — do not apply the same raised style to Home).

3. app.component.scss: adjust the 5-item flex distribution (was `justify-content` over 4 equal
   items, now 5 — verify icon/label sizing still fits comfortably on a 360px-wide mobile
   viewport, the project's de facto minimum target; shrink icon/font-size slightly if 5 items
   feels cramped, rather than letting items wrap or overflow).

4. Confirm root redirect (T6.7) and the bottom nav's "Home" link both point at the same '/home'
   route — no divergence between the toolbar brand link (if any) and the bottom nav.

No new modules/services — purely template + style + the navLinks array.
```

**Verify:** Bottom nav shows exactly 5 items on mobile viewport (≤768px). Tapping each navigates
correctly. Visual check at 360px width — no icon/label overlap or wrapping.

---

### T6.9 — Identification flow redesign — species matching 🤖 AI
**Branch:** `feature/PP-034-identification-species-matching`
**Depends on:** T6.1, T6.3

> **Read ARCHITECT.md's "Identification flow — 3-path decision tree" section before starting —
> this task implements exactly that tree.** This is the largest task in Phase 6; consider
> splitting backend and frontend into separate Claude Code sessions even though they're listed
> together below, since the backend's new resolve-species endpoint needs to exist and be
> verified before the frontend half can be wired against it.

**Backend Claude Code prompt:**
```
// T6.9 (backend) — Species/plant resolution after a Garden-entry-point scan (Flow 1)

Read IdentificationServiceImpl.java (submitIdentification/processIdentification),
SpeciesServiceImpl.findOrCreate() (T6.1), PlantServiceImpl.java before starting.

CONTEXT: Flows 2 and 3 (scanning from a Species page or a Plant page) already know their
speciesId/plantId at request time — pass them straight through on the existing
submitIdentification() call (add optional speciesId/plantId params if not already present from
T6.3) and skip everything below entirely for those two flows. This task is ONLY for Flow 1
(scan from the Garden list, entry point unknown).

1. Add an `entryPoint` field to the identification submission request:
   AnalyzePhotoRequest (or whatever the current multipart request DTO is called) gains:
   entryPoint (String: "GARDEN" | "SPECIES" | "PLANT", default "GARDEN" if absent for backward
   compat), speciesId (Long, nullable — required if entryPoint=SPECIES),
   plantId (Long, nullable — required if entryPoint=PLANT, also the existing health-check path)

2. New DTOs:
   SpeciesMatchDto: matched (boolean), speciesId (nullable), scientificName, commonName
   ResolveSpeciesRequest: confirmed (boolean — true = "yes it's this species", false = discard
     the match and treat as a new species)
   PlantMatchDto: candidatePlants (List<PlantSummaryDto> — id, nickname, photoUrl — the user's
     existing plants of the matched species)
   ResolvePlantRequest: plantId (Long, nullable — null means "create a new plant")

3. After processIdentification() completes for a GARDEN-entry-point scan with no plantId/
   speciesId already set, the result is NOT immediately finalized the way Flow 2/3 are. Instead:
   - speciesRepository.findByScientificName(result.species) →
     - hit: identification.speciesId is NOT set yet (leave null) — frontend must call the new
       resolve-species endpoint to confirm before it's attached
     - miss: same — do not auto-create the Species row yet either; wait for user confirmation
       (auto-creating before the user has even seen "Is this your plant?" risks creating
       duplicate/garbage Species rows from misidentified photos)

4. New endpoints on IdentificationController:
   GET  /api/v1/identifications/{id}/species-match → ApiResponse<SpeciesMatchDto>
     (ownership-checked; looks up result.species against species table, does NOT create
     anything — pure read)
   POST /api/v1/identifications/{id}/resolve-species → ApiResponse<SpeciesMatchDto>
     Body: ResolveSpeciesRequest
     - confirmed=true AND species existed → identification.speciesId = existing species id
     - confirmed=true AND species didn't exist → speciesService.findOrCreate(...) (fires T6.4
       enrichment), identification.speciesId = new species id
     - confirmed=false → no speciesId set on the identification; frontend should prompt the
       user to re-scan or manually search (manual search UI is OUT of scope for this task —
       leave a clearly visible gap, don't invent a search endpoint speculatively)
   GET  /api/v1/identifications/{id}/plant-match → ApiResponse<PlantMatchDto>
     (ownership-checked; requires speciesId already resolved — 400 if not; lists the user's
     existing ACTIVE plants with that speciesId)
   POST /api/v1/identifications/{id}/resolve-plant → ApiResponse<IdentificationResponse>
     Body: ResolvePlantRequest
     - plantId provided → ownership-checked, identification.plantId = plantId, plant.lastScanId
       = identification.id (reuses T6.3's pattern)
     - plantId null → auto-create a new Plant (reuse PlantService.saveFromIdentification()'s
       EXISTING nickname-fallback logic from T2.8 — do not write a second nickname-fallback
       chain), attach speciesId, set identification.plantId to the new plant's id

5. Unit tests (new nested class in IdentificationServiceImplTest or a new
   IdentificationSpeciesMatchingTest, your call based on file size):
   - species-match: existing scientificName → matched=true with correct speciesId; new
     scientificName → matched=false, no Species row created yet
   - resolve-species: confirmed=true on existing → speciesId set, no new row; confirmed=true on
     new → findOrCreate called, new row's id set; confirmed=false → speciesId stays null
   - plant-match: returns only ACTIVE plants of the matched species, ownership-scoped
   - resolve-plant: existing plantId → attaches + updates lastScanId; null → creates new plant
     via the existing saveFromIdentification nickname chain
```

**Frontend Claude Code prompt:**
```
// T6.9 (frontend) — Flow 1 UI: species confirmation + plant selection after a Garden scan

Read identification-detail-page (T2.D2), identification.service.ts, preview-card.component.ts
before starting. Depends on the backend endpoints above existing and being verified first.

1. identification.service.ts: add getSpeciesMatch(id), resolveSpecies(id, confirmed),
   getPlantMatch(id), resolvePlant(id, plantId | null) — thin wrappers over the 4 new endpoints

2. After a Garden-entry-point scan completes (identification-detail-page, COMPLETED status,
   plantId still null): instead of going straight to the existing preview-card "unsaved result"
   view, insert two new sequential steps:
   a. Species confirmation: getSpeciesMatch() result — if matched, show "Is this your plant's
      species: {scientificName}?" [Yes] [No, re-scan] (re-scan = back to /identify upload form);
      if not matched, show "This looks like a new species: {scientificName} — add it?"
      [Yes, add it] [No, re-scan] — both call resolveSpecies(id, confirmed) on [Yes]
   b. Plant selection (only after species resolved): getPlantMatch() — if candidatePlants is
      non-empty, show "Which plant is this?" with a list + "New plant" option; if empty, skip
      straight to "create new plant" (no pointless single-option chooser). Calls resolvePlant().
   c. After resolvePlant() succeeds, navigate to the resulting plant's detail page (same
      redirect behavior the existing COMPLETED+plantId-set path already has in
      identification-detail-page — reuse it, don't duplicate the redirect logic)

3. New components: species-confirm-step/ and plant-select-step/ (both in
   features/identification/components/, declared in IdentificationModule) — simple card-based
   steps matching this app's existing DESIGN_PROGRESS.md visual language, not a wizard library.
```

**Verify:** Scanning a brand-new species from the Garden FAB walks through species-confirm →
plant-select (or auto-create, if no candidates) → lands on the new plant's detail page. Scanning
a species the user already has a plant of offers the existing-plant choice. Flows 2/3 (species
page, plant page) are completely unaffected — verify their existing scan behavior still works
unchanged.

---

### T6.10 — Plant page: sticky header + icon button bar 🤖 AI
**Branch:** `feature/PP-035-plant-page-redesign`
**Depends on:** T6.3

> See ARCHITECT.md's "Angular pattern: sticky-on-scroll header + icon button bar" section for
> the full design rationale before starting — this prompt implements exactly that pattern.

**Frontend Claude Code prompt:**
```
// T6.10 — Plant page navigation redesign: sticky header + icon button bar (replaces mat-tabs)

Read plant-detail.component.ts/html/scss in full before starting — this is a structural rewrite
of the page's navigation, not an additive change.

1. plant-detail.component.html: wrap the existing [plant photo + name + species name] block in
   a `.sticky-header` container with `position: sticky; top: 0; z-index: 10;`. Add a 1px
   `.scroll-sentinel` div immediately after it (used by the IntersectionObserver below).

2. plant-detail.component.ts:
   - ViewChild the sentinel element; in ngAfterViewInit, set up an IntersectionObserver that
     toggles a `headerCollapsed: boolean` property based on the sentinel's visibility
     (sentinel out of view → collapsed = true). Disconnect the observer in ngOnDestroy.
   - Remove mat-tab-group entirely. Replace the `selectedTab` index-based state with
     `activeSection: 'overview' | 'careLog' | 'actions' | 'treatment' | 'scans' = 'overview'`.

3. plant-detail.component.html: replace `<mat-tab-group>` with a horizontal row of
   `mat-icon-button`s (icon-only, `matTooltip` for each — home/history/more_horiz/healing/
   document_scanner per ARCHITECT.md's spec), each setting `activeSection` on click. Active
   button gets `.active-section-btn` (filled dark-green circle, same token as the bottom nav's
   active pill from DESIGN_PROGRESS.md). The "treatment" button (healing icon) is only rendered
   `*ngIf="plant?.activeTreatmentId"` (requires T6.3's field on PlantResponse).

4. Below the button bar, render each section's content behind `*ngSwitch="activeSection"` —
   MOVE the existing Overview/Care History/Scans tab content bodies over unchanged (the content
   itself doesn't change in this task, only how it's selected/displayed). The "actions" case
   opens a MatBottomSheet (see T6.11 for its contents) rather than rendering inline content —
   clicking "actions" should immediately open the sheet and NOT also change activeSection
   visually underneath it.

5. plant-detail.component.scss: `.sticky-header.collapsed` — smaller photo height, condensed
   name font-size, transition on the relevant properties (not `all`, list them explicitly) for
   a smooth collapse, not an instant snap.

6. plant.module.ts: add MatBottomSheetModule (needed by T6.11, fine to add here since this task
   already touches the module imports for the icon-button-bar's Material modules).
```

**Verify:** Scrolling the Plant page collapses the header smoothly; scrolling back up expands it
again. Switching icon buttons changes content without a route change (URL stays `/plants/:id`,
browser back button doesn't step through sections). No `mat-tab-group` remains in the template.

---

### T6.11 — Plant page: scans tab + treatment CTA 🤖 AI
**Branch:** `feature/PP-035-plant-page-redesign` (same branch as T6.10)
**Depends on:** T6.2, T6.10

**Frontend Claude Code prompt:**
```
// T6.11 — "Scans" section content + "actions" bottom sheet + treatment CTA wiring

Read plant-photo-timeline.component.ts (T2.10c), photo-annotator/annotation-list/
disease-detail-panel components, treatment.service.ts (does not exist yet — create it, see
step 1) before starting.

1. NEW reminder-adjacent or plant-adjacent treatment.service.ts (pick whichever feature module
   already has the lightest dependency footprint relative to where this is used — likely
   features/plant/services/, since this is invoked from plant-detail): createTreatment(plantId,
   identificationId, diseaseName), craftPlan(treatmentId), getActiveTreatment(plantId) — thin
   wrappers over T6.2's 5 endpoints. treatment.model.ts: TreatmentResponse mirroring the backend
   DTO.

2. "scans" section body (inside the *ngSwitch from T6.10): floating "New Scan" mat-fab (camera
   icon, dark green) → reuses the EXISTING openAddScanDialog() from T3.9 (do not duplicate it).
   "History" button → opens a MatBottomSheet listing all scans (reuse
   plant-photo-timeline's data-fetching, presented as a list instead of the horizontal strip
   inside the sheet) — selecting one loads it into the main view via the EXISTING
   selectedScan mechanism from T3.9. Main view below: the existing photo-annotator +
   annotation-list + disease-detail-panel block (T2.9c/T3.9), UNCHANGED, except:
   - When the selected scan has a DISEASE region AND `getActiveTreatment(plantId)` returns
     null/404 for that diseaseName → show "Start Treatment Plan" button (coral, prominent,
     per the brief) ABOVE the disease-detail-panel — clicking it calls
     treatmentService.createTreatment(...) then craftPlan(...), then navigates to
     `/treatment/:id` (T6.12's route)
   - When an active Treatment already exists for that diseaseName → show a "Treatment in
     Progress" chip instead → tap navigates to `/treatment/:id`
   - When healthy (no disease region selected) → existing care-plan UI, completely unchanged

3. "actions" bottom sheet (MatBottomSheet, opened from T6.10's button bar) — a new
   PlantActionsSheetComponent with 4 list items:
   - "Update Plant Profile Image" → existing photo-update flow if one exists; if not, flag as a
     gap rather than inventing a new upload mechanism in this task
   - "Archive Plant" → confirm dialog ("Are you sure?"), then the EXISTING archivePlant() call
     (unchanged backend, T3.8 already wired the reminder-cascade fix)
   - "Scan Plant" → same as the "scans" section's "New Scan" FAB (reuse, don't duplicate)
   - "Ask Me About {plant.nickname}" → router.navigate(['/chat'], { queryParams: { plantId:
     plant.id } }) — full chat-side handling of this query param is T6.13's job, this button
     just needs to navigate correctly

4. plant-detail.module declarations: PlantActionsSheetComponent (declared in PlantModule,
   entryComponents not needed in Angular 16+ JIT-compiled MatBottomSheet usage — confirm against
   this project's Angular version before assuming, but it's already 16+ per FRONTEND.md so this
   should be a non-issue).
```

**Verify:** A plant with a DISEASE-type scan and no active Treatment shows "Start Treatment
Plan"; clicking it lands on `/treatment/:id` with a freshly crafted plan. A plant with an
existing active Treatment shows "Treatment in Progress" instead, linking to the same page. The
"actions" sheet's "Ask Me About X" navigates to `/chat?plantId=N`.

---

### T6.12 — Treatment page 🤖 AI
**Branch:** `feature/PP-036-treatment-page`
**Depends on:** T6.2

> Mirrors the Plant page's sticky-header + icon-button-bar structure from T6.10 — reuse the
> pattern, don't reinvent it for this page.

**Frontend Claude Code prompt:**
```
// T6.12 — Treatment page: /treatment/:id

Read T6.10's sticky-header + icon-button-bar implementation (once it exists) before starting —
copy the structural pattern, do not invent a second sticky-header mechanism. Read
treatment-plan-detail.component.ts (T3.5) — this NEW page is for the disease-level `Treatment`
entity, NOT the existing `/treatment-plans/:id` page, but the step-list/progress-line/Mermaid
rendering for whatever `treatmentPlanId` the Treatment wraps CAN reuse
treatment-plan-detail's internals (consider extracting a shared step-list sub-component rather
than copy-pasting the template, if the overlap is as large as it looks).

1. features/treatment/ (NEW lazy module, OR add to the existing reminder feature module if the
   step-list reuse from treatment-plan-detail makes that the more natural home — your call based
   on how much is actually shared once you're looking at the real component).
   Route: '/treatment/:id'

2. pages/treatment-detail/:
   - Header (sticky, same pattern as T6.10): scan photo (identification.photoUrl, NOT the
     plant's profile photo), disease name (font-heading serif, large — same visual weight as
     the Plant page's plant-name), "Back to {plant.nickname}" link (routerLink to
     /plants/:plantId, NOT Location.back() — unlike treatment-plan-detail's ambiguous-origin
     back button, this page always has exactly one correct back destination: the plant it
     belongs to)
   - Icon button bar: 2 buttons — "overview" (home icon), "plan" (assignment icon)
   - "overview" section: disease name, status chip (Draft/In Progress/Completed — reuse the
     status-chip styling from treatment-plan-detail if it already has equivalent chip styles),
     diseaseDescription text (handle null as a brief pending state, same philosophy as T6.6's
     species description), "Craft Treatment Plan" button (only when status=DRAFT) → calls
     treatmentService.craftPlan(id) → on success, switch activeSection to 'plan' automatically
     (don't make the user manually click over)
   - "plan" section: if treatmentPlanId is set, fetch and render it via the SAME
     step-list/Mermaid/mark-done UI as treatment-plan-detail (extract-and-reuse per the note
     above) — including the existing "Mark treatment complete" affordance once all steps are
     done. When the underlying TreatmentPlan completes, also call
     treatmentService.completeTreatment(id) to flip the Treatment's own status (do not rely on
     an automatic backend sync unless T6.14 has implemented one by the time this lands — check
     T6.14's status first).

3. treatment.service.ts (T6.11): add getTreatment(id) if not already present.
```

**Verify:** A DRAFT treatment shows the description (or pending state) and the "Craft Treatment
Plan" button; clicking it generates and displays the step list. Completing all steps flips the
status chip to "Completed" and clears the Plant page's "Treatment in Progress" chip (T6.11).

---

### T6.13 — Chat: plant context injection 🤖 AI
**Branch:** `feature/PP-037-chat-plant-context`
**Depends on:** T6.3

**Backend Claude Code prompt:**
```
// T6.13 (backend) — ChatRequest gains optional plantId; inject plant-specific context

Read ChatServiceImpl.java (buildGardenContext), ChatController.java before starting.

1. ChatRequest.java: add plantId (Long, nullable, optional)

2. ChatServiceImpl.chat(): when plantId is present (ownership-checked — load the Plant, verify
   userId match, ResourceNotFoundException otherwise), build an ADDITIONAL context block
   prepended before the existing garden-context block:
   "The user is asking specifically about their plant '{nickname}' ({species/commonName}).
   Its last scan ({date}) showed health status: {healthStatus}.
   {if activeTreatmentId present: "It currently has an active treatment in progress for
   {diseaseName}."}"
   — fetched via the existing plantRepository + a single identification lookup (reuse
   IdentificationRepository's existing latest-per-plant query rather than writing a new one if
   it already gives you what's needed for one plant).
   When plantId is absent, behavior is COMPLETELY unchanged from today (general garden context
   only) — this is purely additive.

3. Unit tests: chat with plantId → context includes the plant-specific block; chat with plantId
   for a plant the user doesn't own → ResourceNotFoundException; chat without plantId →
   unchanged existing behavior (no regression).
```

**Frontend Claude Code prompt:**
```
// T6.13 (frontend) — Chat page reads ?plantId= and threads it through

Read chat-home.component.ts, chat.service.ts before starting.

1. chat.service.ts: sendMessage(message, plantId?) → includes plantId in the POST body when
   present (mirrors the backend's optional field)

2. chat-home.component.ts: ngOnInit reads `plantId` from ActivatedRoute.queryParams (set by
   T6.11's "Ask Me About {nickname}" navigation) — if present, store it and pass it on every
   subsequent sendMessage() call for the rest of the chat session (not just the first message);
   show a small context chip in the chat header ("Chatting about {nickname}" — fetch the plant's
   nickname via the existing PlantService.getPlant(plantId) for display, dismissible to clear
   the context without leaving the page).

3. No changes needed to chat-routing — queryParams work on the existing '/chat' route as-is.
```

**Verify:** Navigating from a Plant page's "Ask Me About X" lands on `/chat?plantId=N` showing
the context chip; messages sent get plant-specific replies. Navigating to `/chat` directly
(no plantId) behaves exactly as before — no regression in the general chat flow.

---

### T6.14 — Reminders: wire treatment plan steps 🤖 AI
**Branch:** `feature/PP-030-treatment-entity` (same branch as T6.2)
**Depends on:** T6.2

> This task is the completion-sync hook flagged as a TODO in T6.2's `craftPlan()` — it makes the
> existing `TreatmentPlan` completion machinery (T3.4) also flip the NEW `Treatment` entity's
> status, instead of requiring an explicit frontend call to `PATCH /treatments/{id}/complete`.

**Backend Claude Code prompt:**
```
// T6.14 — Sync Treatment.status when its underlying TreatmentPlan completes

Read ReminderService.applyCompletionToReminder() (T3.4 — the single place a TreatmentPlan flips
to COMPLETED), TreatmentServiceImpl.java (T6.2), TreatmentRepository.findByTreatmentPlanId
(T6.2) before starting.

1. TreatmentService.java (interface): add a package-visible-or-public method
   syncFromTreatmentPlanCompletion(Long treatmentPlanId) — looks up the Treatment via
   TreatmentRepository.findByTreatmentPlanId(treatmentPlanId); if found and currently
   IN_PROGRESS, sets status=COMPLETED, completedAt=now, and clears plant.activeTreatmentId
   (mirrors completeTreatment()'s effects — consider having completeTreatment() and this new
   method share a single private helper rather than duplicating the 3-field update).

2. ReminderServiceImpl.applyCompletionToReminder(): at the exact point where it currently flips
   `TreatmentPlan.status` to COMPLETED (the "last enabled step" branch), add a call to
   treatmentService.syncFromTreatmentPlanCompletion(treatmentPlan.getId()). This requires
   injecting TreatmentService into ReminderServiceImpl — check for a circular-dependency risk
   first (com.plantpal.treatment would now depend on com.plantpal.reminder for
   TreatmentPlanService, AND com.plantpal.reminder would depend on com.plantpal.treatment for
   this call — that IS a package cycle). If Spring/the module boundary objects, prefer an
   application event instead: publish a TreatmentPlanCompletedEvent from
   ReminderServiceImpl (no new dependency needed, just an event class + ApplicationEventPublisher)
   and have a new @EventListener in com.plantpal.treatment consume it. Choose whichever keeps
   the dependency direction clean — do not force the direct-injection approach if it creates a
   cycle; this is exactly the kind of judgment call to make once you're looking at the actual
   package structure, not before.

3. Unit tests: completing a TreatmentPlan's last step (via the existing
   ApplyCompletionToReminder test fixture pattern from T3.4) → verify Treatment.status flips to
   COMPLETED and plant.activeTreatmentId clears, for a Treatment that wraps that TreatmentPlan.
   A TreatmentPlan completing with NO associated Treatment (e.g. a plain ROUTINE reminder plan)
   → no-op, no exception, confirms this doesn't regress T3.4's existing non-Treatment-wrapped
   TreatmentPlan completion flow.
```

**Verify:** Marking the last step of a Treatment's underlying plan done (via the existing
`/treatment-plans/:id` "Mark done" UI, T3.5) automatically flips the Treatment page's status
chip to "Completed" on next load — no separate explicit "complete" action required from the
user. Full backend suite still green — confirms no regression to T3.4's reminder/treatment-plan
completion logic for plans that aren't wrapped by a `Treatment`.

---

## Task Summary

| Phase | Tasks | 👤 Manual | 🤖 AI | 🤝 Assisted |
|---|---|---|---|---|
| 0 — Setup | 6 | T0.1, T0.2, T0.6 | T0.3, T0.4 | T0.5 |
| 1 — Auth + Plants | 9 | T1.9 | T1.1–T1.6, T1.8 | T1.7 |
| 2 — AI Identification | 5 | T2.1, T2.5 | T2.2, T2.3 | T2.4 |
| 3 — Reminders | 3 | T3.3 | T3.1 | T3.2 |
| 4 — Chat | 3 | T4.3 | T4.1 | T4.2 |
| 5 — Launch | 8 | T5.5, T5.6, T5.8 | T5.1, T5.4 | T5.2, T5.3, T5.7 |
| 6 — Species & Treatment Restructure | 14 | — | T6.1–T6.8, T6.10–T6.12, T6.14 | T6.9, T6.13 |
| **Total** | **48** | **11** | **27** | **9** |

> Phase 6 row added 2026-06-19 (session 18) — none of T6.1–T6.14 started yet. See this file's
> "PHASE 6 — Species & Treatment Domain Restructure" section above for full task prompts, and
> STATE.md/ARCHITECT.md for the domain model and renumbering rationale.

---

## Enterprise Patterns Checklist

These are baked into the task plan. Use this as a final audit before launch:

- [ ] All list endpoints paginated (`Pageable`)
- [ ] All deletes are soft deletes (`status = ARCHIVED`)
- [ ] All entities have audit fields (`createdAt`, `updatedAt`, `createdBy`)
- [ ] Redis cache on all hot read paths
- [ ] Rate limiting on all AI endpoints and auth endpoints
- [ ] JaCoCo gate at 80% — fails CI if coverage drops
- [ ] Testcontainers for integration tests (real PostgreSQL, not H2)
- [ ] OWASP dependency check in CI
- [ ] Structured JSON logging in prod with correlation IDs
- [ ] All secrets in environment variables, never in code
- [ ] Security headers on all responses
- [ ] Swagger UI documents every endpoint with examples
- [ ] Docker + docker-compose for reproducible local dev
- [ ] Raw Claude API responses stored in DB for future reprocessing
- [ ] `ResourceNotFoundException` message never reveals whether resource exists or just isn't yours
