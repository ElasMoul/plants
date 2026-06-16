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

**Feature description:**
- Dashboard card per plant: photo thumbnail + name + health badge + "next action" chip
  ("Water in 2 days", "Overdue: fertilize!", "All good")
- Overdue reminders highlighted in red at the top
- Today's tasks section: "Today you need to water 2 plants and fertilize 1"
- Health trend: if 2+ identifications exist for a plant, show "Getting better" / "Worsening"
  based on change in health_status between identifications
- Weekly streak: "You've cared for your plants 5 days in a row!" (gamification hook)

**Architecture notes:**
- New endpoint: GET /api/v1/dashboard → DashboardResponse
  DashboardResponse contains:
  - List<PlantSummaryDto> overduePlants
  - List<PlantSummaryDto> todayPlants
  - int currentStreak (days)
  - HealthSummaryDto (totalPlants, healthyCount, issuesCount, unknownCount)
- The streak is calculated from care_logs: count consecutive days where ≥ 1 log exists
- This is a read-heavy, cache-friendly endpoint → @Cacheable("dashboard::{userId}", TTL 5 min)
  @CacheEvict whenever a care log is added

---

### T2.11 — Manual testing — Phase 2 complete 👤 Manual
**Branch:** PR to `dev`

All features end-to-end:
- [ ] Take photo → species identified, bounding box drawn on photo?
- [ ] Disease area (yellow leaf) → red/orange highlight on photo?
- [ ] Preview card auto-filled: species, common name, confidence, care plan shown?
- [ ] Click "Save to garden" → plant created, navigated to detail page?
- [ ] Plant detail → Care Plan tab shows watering/fertilizing frequencies?
- [ ] Reminders auto-created for new plant (check reminders page)?
- [ ] Dashboard shows overdue reminders in red?
- [ ] Low-confidence result: notice "Low confidence" shown, still saveable?
- [ ] DeepSeek unavailable: care plan fallback (generic WATERING card) shown, no crash?
- [ ] Test on mobile (phone camera → full flow)?

---

## PHASE 3 — Reminders + Push Notifications
> Goal: users receive care reminders on their phone via push notifications.
> Estimated time: Weeks 5–6.

---

### T3.1 — Reminder module — full backend 🤖 AI
**Branch:** `feature/PP-011-reminder-module`

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

### T3.2 — Reminder module — Angular frontend + PWA 🤝 Assisted
**Branch:** `feature/PP-012-reminder-frontend`

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

### T3.3 — Manual testing — Phase 3 👤 Manual
**Branch:** PR to `dev`

- [ ] Create a watering reminder for a plant, set it due today
- [ ] Manually trigger scheduler (or set time to NOW for testing)
- [ ] Push notification appears in browser?
- [ ] Mark care as done → next due date recalculated correctly?
- [ ] Test on Chrome Android + Safari iOS (PWA installable?)
- [ ] Install PWA to home screen — does it work offline for reading plants?

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

## Task Summary

| Phase | Tasks | 👤 Manual | 🤖 AI | 🤝 Assisted |
|---|---|---|---|---|
| 0 — Setup | 6 | T0.1, T0.2, T0.6 | T0.3, T0.4 | T0.5 |
| 1 — Auth + Plants | 9 | T1.9 | T1.1–T1.6, T1.8 | T1.7 |
| 2 — AI Identification | 5 | T2.1, T2.5 | T2.2, T2.3 | T2.4 |
| 3 — Reminders | 3 | T3.3 | T3.1 | T3.2 |
| 4 — Chat | 3 | T4.3 | T4.1 | T4.2 |
| 5 — Launch | 8 | T5.5, T5.6, T5.8 | T5.1, T5.4 | T5.2, T5.3, T5.7 |
| **Total** | **34** | **11** | **16** | **7** |

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
