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
> Goal: the user photographs a plant and gets an expert AI diagnosis.
> Estimated time: Weeks 3–4.

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

### T2.6 — Visual plant annotation (bounding boxes + disease overlay) 🤖 AI
**Branch:** `feature/PP-017-visual-annotation`

> **Goal:** After a photo is processed, overlay named rectangles on identified plants
> and highlight areas with detected disease or stress (yellowing, spots, wilting).

**Architectural notes:**
- PlantNet does NOT return bounding boxes — we need a second AI pass.
- Use Ollama with a multimodal model (`llava` or `bakllava`) for region detection.
  This is already in our stack (OllamaClient). Just extend it with image input support.
- Return format: structured JSON with `regions[]` — each region has `label`, `confidence`,
  `type` (PLANT | DISEASE | HEALTHY_AREA), and `boundingBox` (x, y, width, height as % of image).
- Frontend draws a Canvas overlay on the photo using the coordinates.
- Make it optional/progressive: if Ollama times out or is unavailable, show result without overlay.

**Backend Claude Code prompt:**
```
// T2.6 — Add visual annotation support to the identification pipeline

In com.plantpal.identification:

1. Extend OllamaClient with a new method:
   analyzeRegions(byte[] imageBytes, String mediaType) → String (raw JSON)
   - POST to Ollama /api/generate with model=llava (or bakllava)
   - Encode image as base64 and pass in the "images" field
   - System prompt asks for plant regions + disease areas as structured JSON:
     {
       "regions": [
         {
           "label": "Monstera deliciosa",
           "type": "PLANT",
           "confidence": "HIGH",
           "boundingBox": { "xPct": 10, "yPct": 5, "widthPct": 80, "heightPct": 70 }
         },
         {
           "label": "Yellowing — possible overwatering",
           "type": "DISEASE",
           "confidence": "MEDIUM",
           "boundingBox": { "xPct": 30, "yPct": 60, "widthPct": 20, "heightPct": 15 }
         }
       ]
     }
   - If Ollama unavailable or response malformed: return empty regions list, log WARN.

2. Add AnnotationRegion.java DTO:
   String label, String type (PLANT/DISEASE/HEALTHY_AREA), String confidence,
   BoundingBoxDto boundingBox (xPct, yPct, widthPct, heightPct)

3. Add annotationRegions (List<AnnotationRegion>) field to IdentificationResponse.

4. In IdentificationServiceImpl.identify():
   After PlantNet returns → fire analyzeRegions() as a separate @Async call.
   Both calls run in parallel. Join results before returning.
   annotationRegions may be empty — caller must handle gracefully.

5. New Liquibase migration 008_add_annotation_regions.sql:
   ALTER TABLE identifications ADD COLUMN annotation_regions JSONB;

Unit test: mock OllamaClient.analyzeRegions(), verify empty list on malformed JSON.
```

**Frontend Claude Code prompt:**
```
// T2.6 — Visual annotation overlay on identification result

In features/identification/components/photo-annotator/:

1. PhotoAnnotatorComponent — takes [imageUrl] and [regions] as @Input
   - Renders the original photo with a <canvas> overlay sized to match
   - For each region:
     * PLANT → draw blue rectangle + label chip at top-left of box
     * DISEASE → draw red/orange semi-transparent rectangle + label
     * HEALTHY_AREA → draw green semi-transparent rectangle + label
   - On mobile: make sure canvas touch events don't interfere with scrolling
   - If regions is empty or null: render plain <img>, no canvas

2. Plug PhotoAnnotatorComponent into identification-result component,
   replacing the plain <img> currently showing the photo.

3. Add a toggle: "Show annotations" / "Hide annotations" above the photo.
```

---

### T2.7 — One-click validate & save flow 🤝 Assisted
**Branch:** `feature/PP-018-one-click-save`

> **Goal:** After AI processes a photo, the user sees a fully pre-filled plant profile card.
> One click on "Save to garden" creates the plant and links the identification.
> No manual form filling required.

**UX flow:**
```
Take photo → Processing spinner (5-20s) → Preview card auto-filled → [Save to garden] / [Edit] / [Discard]
```

**Backend Claude Code prompt:**
```
// T2.7 — Add one-click save endpoint

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
   - Trigger care plan generation (T2.8 service call, async)
   - Return PlantResponse

3. New endpoint in PlantController:
   POST /api/v1/plants/from-identification
   → 201 Created with PlantResponse

4. Unit test: cover nickname fallback chain, verify identification.plantId is updated.
```

**Frontend Claude Code prompt:**
```
// T2.7 — One-click validate & save UI

In features/identification/:

1. After identification result arrives, show a PreviewCardComponent:
   - Plant photo (full width, with annotation overlay from T2.6 if available)
   - Species name (large), common name (subtitle)
   - Confidence badge (green/amber/red)
   - Health status badge with icon
   - Care summary chips: "Water every X days", "Partial shade", "Fertilize every Y days"
   - Optional nickname input (pre-filled with common_name, user can rename)
   - Optional location input (text field, e.g. "balcony", "living room window")

2. Three action buttons at the bottom:
   [Save to garden] → POST /api/v1/plants/from-identification → navigate to /plants/{id}
   [Edit before saving] → open plant-form pre-filled with identification data
   [Discard] → back to photo upload

3. Loading state on [Save to garden] — disable buttons while saving.

4. On success: show brief toast "Added to your garden!" then navigate to plant detail.
```

---

### T2.8 — AI care plan for beginners 🤖 AI
**Branch:** `feature/PP-019-care-plan`

> **Goal:** Every plant gets a personalised, beginner-friendly care schedule generated by AI.
> The user doesn't need gardening knowledge — the AI tells them exactly what to do and when.
> Care plan auto-creates the reminders (T3 module) with correct frequencies.

**Why this matters:** An amateur gardener doesn't know that a Monstera needs water every 7-10 days
in summer but only every 14 days in winter. We ask the AI so the user doesn't have to.

**Backend Claude Code prompt:**
```
// T2.8 — AI-generated care plan for new plants

In com.plantpal.identification (or a new care/ sub-package within plant/):

1. New DTO CarePlanDto:
   - wateringFrequencyDays (int)
   - fertilizingFrequencyDays (int)
   - repottingFrequencyMonths (int)
   - pruningFrequencyDays (int, nullable — not all plants need it)
   - sunlightRequirement (String: FULL_SUN | PARTIAL_SHADE | SHADE)
   - beginnerTips (List<String>) — 3-5 plain English tips, no jargon
     e.g. "Water when the top 2cm of soil feels dry"
     e.g. "Yellow leaves usually mean too much water, not too little"
   - seasonalNotes (String) — one paragraph, what changes in winter/summer

2. New method in OllamaClient:
   generateCarePlan(String species, String commonName, String healthNotes) → String (raw JSON)
   System prompt:
     "You are a friendly plant care expert helping a complete beginner.
      Given the plant species below, return ONLY a JSON object (no markdown):
      {
        \"wateringFrequencyDays\": <int, summer value>,
        \"fertilizingFrequencyDays\": <int>,
        \"repottingFrequencyMonths\": <int>,
        \"pruningFrequencyDays\": <int or null>,
        \"sunlightRequirement\": \"FULL_SUN | PARTIAL_SHADE | SHADE\",
        \"beginnerTips\": [\"tip1\", \"tip2\", \"tip3\"],
        \"seasonalNotes\": \"one paragraph\"
      }
      Plant: {species} ({commonName})
      Health notes from photo: {healthNotes}"

3. New service method in IdentificationService:
   @Async("aiTaskExecutor")
   CompletableFuture<CarePlanDto> generateCarePlan(Long identificationId, Long userId)
   - Load identification, call OllamaClient.generateCarePlan()
   - Parse JSON, return CarePlanDto
   - On failure (Ollama unavailable): return a safe default CarePlanDto with generic values

4. Add carePlan (CarePlanDto) field to IdentificationResponse — populated after generation.

5. Auto-create reminders (calls ReminderService):
   When a plant is saved via T2.7's saveFromIdentification():
   - Create WATERING reminder with frequency = carePlan.wateringFrequencyDays
   - Create FERTILIZING reminder with frequency = carePlan.fertilizingFrequencyDays
   - Create REPOTTING reminder with frequency = carePlan.repottingFrequencyMonths * 30
   - If pruningFrequencyDays != null: create PRUNING reminder
   - All nextDueAt = now() + frequencyDays (start counting immediately)
   Note: ReminderService must be injected into PlantService for this. Wire via constructor.

6. Liquibase migration 009_add_care_plan_to_identifications.sql:
   ALTER TABLE identifications ADD COLUMN care_plan JSONB;

Unit tests: mock OllamaClient, verify fallback defaults are valid, verify all 4 reminder types created.
```

**Frontend Claude Code prompt:**
```
// T2.8 — Display care plan in plant detail and identification result

1. In PreviewCardComponent (T2.7): add a "Care plan" section below the identification result:
   - Icon + "Water every X days"
   - Icon + "Fertilize every Y days"
   - Icon + "Repot every Z months"
   - Sunlight chip
   - Expandable panel "Beginner tips" showing the tips list
   - Expandable panel "Seasonal notes"

2. In plant-detail component, add a "Care Plan" tab alongside existing tabs.
   Fetch from GET /api/v1/identifications/plant/{plantId}?size=1 (latest identification).
   Show the care plan from the most recent identification.
   If no identification yet: show "Take a photo to get a personalized care plan" CTA.
```

---

### T2.9 — Garden health dashboard 💡 Architect Suggestion
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

### T2.11 — DeepSeek client + dynamic care plan backend 🤖 AI
**Branch:** `feature/PP-021-deepseek-care-plan`

> **Goal:** Replace Ollama phi3 for care planning with DeepSeek (smarter, production-grade).
> The care plan is **dynamic** — it adapts completely to each plant species.
> Instead of a fixed DTO with hardcoded fields, the AI returns a list of "care cards"
> that the frontend renders generically. Adding a new exotic plant species just works,
> no code changes needed.

**Why dynamic care cards:**
A monstera care plan has: watering, light, humidity, fertilizing, repotting, common issues.
A cactus care plan has: watering (very infrequent), light (full sun), drainage warning, winter dormancy.
A bonsai care plan has: watering, wiring, pruning schedule, seasonal repotting, pest management.
These are structurally different — hardcoded fields can't capture this. A card array can.

**Backend Claude Code prompt:**
```
// T2.11 — DeepSeek client + dynamic care plan

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

2. System prompt for DeepSeek (store as static final String in DeepSeekClient):
   ```
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
         "summary": "<one sentence, e.g. 'Water every 7 days'>",
         "detail": "<2-4 sentences, plain English, no jargon>",
         "urgency": "LOW | MEDIUM | HIGH",
         "seasonalVariation": "<optional: what changes in winter/summer, or null>"
       }
     ],
     "beginnerWarnings": ["warning1", "warning2"]
   }
   Include 4-8 care cards covering the most important aspects for this specific plant.
   For rare/unusual requirements, add extra cards. Omit irrelevant ones.
   Write for someone who has never owned a plant before.
   ```

3. New DTOs in identification/dto/:
   CareCardDto: String type, String title, String icon, String summary,
                String detail, String urgency, String seasonalVariation
   CarePlanDto: int wateringFrequencyDays, int fertilizingFrequencyDays,
                int repottingFrequencyMonths, List<CareCardDto> careCards,
                List<String> beginnerWarnings

4. Add carePlan (CarePlanDto) field to IdentificationResponse.

5. In IdentificationServiceImpl, after PlantNet returns:
   - Fire DeepSeekClient.generateCarePlan() as @Async call (parallel with annotation if T2.6 is done)
   - Parse response into CarePlanDto (ObjectMapper — handle malformed JSON with a safe default:
     one WATERING card saying "General care: water when topsoil is dry")
   - Persist care plan as JSONB in identifications.care_plan column
   - Include in IdentificationResponse

6. Liquibase migration 008_add_care_plan.sql:
   ALTER TABLE identifications ADD COLUMN IF NOT EXISTS care_plan JSONB;

7. In application-dev.yml, add:
   deepseek:
     base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
     api-key: ${DEEPSEEK_API_KEY}
     model: ${DEEPSEEK_MODEL:deepseek-chat}

8. Add DEEPSEEK_API_KEY to backend/.env.example with comment.

9. Add Bucket4j rate limit for DeepSeek calls: 20/hour/user (same as PlantNet).

Unit tests (mock DeepSeekClient):
- generateCarePlan() returns valid JSON → CarePlanDto parsed correctly
- generateCarePlan() returns malformed JSON → safe default CarePlanDto returned
- generateCarePlan() throws exception → safe default returned, not propagated to caller
- Verify careCards[] is never null (always at least 1 card in fallback)
```

---

### T2.12 — Dynamic care plan frontend 🤝 Assisted
**Branch:** `feature/PP-021-deepseek-care-plan` (same branch)

> **Design principle:** The frontend renders care cards generically.
> It does not know about specific plant types. The AI decides what cards to show.
> Adding an orchid or a bonsai requires ZERO frontend changes.

**Frontend Claude Code prompt:**
```
// T2.12 — Dynamic care plan UI

In features/identification/:

1. Update identification.model.ts:
   Add CareCardDto and CarePlanDto interfaces matching the backend DTOs.
   CareCardType = 'WATERING' | 'LIGHT' | 'HUMIDITY' | 'TEMPERATURE' |
     'FERTILIZING' | 'REPOTTING' | 'PRUNING' | 'PEST' | 'SEASONAL' | 'BEGINNER_TIP'

2. components/care-plan/care-card.component.ts + .html + .scss:
   @Input() card: CareCardDto
   - Icon from card.icon (Angular Material icon)
   - Color-coded by type:
       WATERING → blue, LIGHT → yellow, TEMPERATURE → orange,
       PEST → red, BEGINNER_TIP → green, SEASONAL → purple, default → grey
   - Color-coded border by urgency: HIGH → accent color, MEDIUM → warn-light, LOW → none
   - Expandable: click card to reveal card.detail + card.seasonalVariation
   - If seasonalVariation exists: show a small "Seasonal tip" chip inside the expanded view

3. components/care-plan/care-plan.component.ts + .html + .scss:
   @Input() carePlan: CarePlanDto | null
   - If carePlan is null: show skeleton loader (3 grey placeholder cards)
   - Render careCards as a responsive CSS grid (2 cols on tablet, 1 col on mobile)
   - Below the cards: "Beginner warnings" section — each warning as a yellow alert chip
   - Summary bar at top: "Water every X days · Fertilize every Y days · Repot every Z months"
     (uses the numeric fields, not the cards — quick reference)

4. Wire into identification-result component:
   - Show CarePlanComponent below the species/health result
   - While waiting for carePlan (it's async): show the skeleton loader
   - The identification result page polls GET /api/v1/identifications/{id} until
     carePlan is populated (or show it if already present in the initial response)

5. Wire into plant-detail component:
   - "Care Plan" tab fetches the most recent identification for this plant
   - Shows CarePlanComponent with that identification's carePlan
   - If no identification yet: show "Take a photo to get your care plan" with a CTA button
     that navigates to /identify with the plantId pre-selected

6. Wire into preview card (T2.7 PreviewCardComponent):
   - Below species/confidence: show the first 3 care cards as a quick preview
   - "See full plan after saving" link

IMPORTANT: Never hardcode care types in switch/case. The card color mapping uses a
TypeScript Record<CareCardType, string> — adding a new type only requires updating that map.
```

---

### T2.10 — Manual testing — Phase 2 complete 👤 Manual
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
- [ ] Ollama unavailable: care plan uses sensible defaults, no crash?
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
