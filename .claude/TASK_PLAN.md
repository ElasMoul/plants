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
### T0.1 — Create GitHub repository 👤 Manual
### T0.2 — Set up local infrastructure 👤 Manual
### T0.3 — Generate Spring Boot skeleton 🤖 AI
### T0.4 — Generate Angular skeleton 🤖 AI
### T0.5 — GitHub Actions CI/CD 🤝 Assisted
### T0.6 — Generate VAPID keys and gather secrets 👤 Manual
### T1.1 — Liquibase migrations (all tables) 🤖 AI
### T1.2 — User module — entity, DTOs, mapper, repository 🤖 AI
### T1.3 — Spring Security 6 + JWT 🤖 AI
### T1.4 — Plant module — full backend 🤖 AI
### T1.5 — Unit tests — Phase 1 🤖 AI
### T1.6 — Integration tests — Phase 1 🤖 AI
### T1.7 — Plant module — Angular frontend 🤖 AI
### T1.8 — Auth module — Angular frontend 🤖 AI
## PHASE 2 — AI Plant Identification
### T2.1 — Prepare AI environment 👤 Manual
### T2.2 — ClaudeApiClient + FileStorageService 🤖 AI
### T2.3 — Identification module — full backend 🤖 AI
### T2.4 — Identification module — Angular frontend 🤝 Assisted
### T2.5 — Manual testing — identification core 👤 Manual
### T2.6 — DeepSeek client + dynamic care plan backend 🤖 AI  ← NEXT
### T2.7 — Dynamic care plan frontend 🤝 Assisted
### T2.8 — One-click validate & save flow 🤝 Assisted
### T2.9 — Visual plant annotation (bounding boxes + disease overlay) 🤖 AI
### T2.9a — Polygon annotation — backend 🤖 AI
### T2.9b — Polygon annotation — frontend 🤖 AI
### T2.9c — Disease detail panel + annotation list 🤖 AI
### T2.9d — Cure-advice endpoint — backend 🤖 AI
### T2.A — GitHubModelsClient refactor — split vision from text client (Backend) 🤖 AI
### T2.B — Add GITHUB_GPT4O to AI model preferences (Both) 🤖 AI
### T2.C — Kafka async identification pipeline (Backend) 🤖 AI
### T2.D — Kafka polling — identification frontend (Frontend) 🤖 AI
### T2.E — Redis photo storage + SHA-256 deduplication (Backend) 🤖 AI
### T2.F — Image dimension locking — annotation alignment (Both) 🤖 AI
### T2.10 — Garden health dashboard 💡 Architect Suggestion
### T2.10a — Backend: fix Plant health/water data 🤖 AI
### T2.10b — Backend: dashboard aggregate endpoint 🤖 AI
### T2.10c — Frontend: plant photo timeline 🤝 Assisted
### T2.10d — Frontend: garden dashboard page 🤝 Assisted
### T2.11 — Manual testing — Phase 2 complete ✅ DONE
## PHASE 3 — Reminders + Push Notifications
### T3.1 — Reminder module — full backend 🤖 AI ✅ DONE (2026-06-18)
### T3.2 — Reminder module — Angular frontend + PWA 🤝 Assisted ✅ DONE (2026-06-18)
### T3.4 — Backend: actionable care plans (routines + treatment plans) 🤖 AI ✅ DONE
### T3.5 — Frontend: actionable care plans UI (reminders, treatment plans, diagrams) 🤝 Assisted
### T3.3 — Manual testing — Phase 3 👤 Manual ✅ DONE
## PHASE 4 — AI Chat Assistant ✅ DONE
### T4.1 — Chat module — full backend 🤖 AI ✅ DONE
### T4.2 — Chat module — Angular frontend 🤝 Assisted
### T4.3 — Manual testing — Phase 4 👤 Manual ✅ DONE
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
