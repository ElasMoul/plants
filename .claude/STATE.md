# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-07-15 (T-DEPLOY.5 code-prep merged, PR #126: in-process transport — v1.0.0 ships without Kafka — + Railway/Vercel plumbing; next = account-side runbook in DEPLOYMENT.md)
> Full session diary: Archive/STATE_2.md and Archive/STATE_1.md | git log for code history

---

## Current State

| Branch | Status |
|---|---|
| `dev` | Clean — phases 0–10 + T-DEPLOY.1–5(code) merged ✅ (PR #125 e5a9bf4, PR #126 e932b4a) |

**Migration sequence:** 001–032 applied. Next free: **033**.
**Next free PP branch number:** PP-092 (PP-089 = T-DEPLOY.1 prod config; PP-090 = T-DEPLOY.2–4 perf/security/docs; PP-091 = T-DEPLOY.5 in-process transport + deploy plumbing; (PP-071–075 = T10.A–F; PP-076 = T10.G voice fix; PP-077 = T10.H TTS; PP-078 = T10.I mic bug; PP-082–088 consumed by platform-delta/integration work — not phase-numbered T-tasks — namely the landing page, contracts/state-feed wiring, AI block-state UI, the SecretConfigValidator CI fix, PP-086 = self-declared business-tier, PP-087 = CI fix, PP-088 = ai-gateway full-coverage demand follow-ups (G1/G4/G5 gateway routing) — see PROGRESS.md's 2026-07-10 entry).

---

## Phase Status

| Phase | Status |
|---|---|
| 0 — Setup | ✅ Complete |
| 1 — Auth + Plant Management | ✅ Complete |
| 2 — AI Plant Identification | ✅ Complete |
| 3 — Reminders + Care Plans | ✅ Complete (T3.3 manual device testing → Phase DEPLOY beta) |
| 4 — AI Chat | ✅ Complete (streaming + history) |
| 5 — Launch prep | 🔲 Renamed Phase DEPLOY, runs last |
| 6 — Species & Treatment Restructure | ✅ Complete (T6.1–T6.14) |
| 7 — Model Control, Batch, Multi-Treatment | ✅ Complete (T7.1–T7.4) |
| 8 — PlantNet First-Class Provider | ✅ Complete (T8.0–T8.7) |
| 8.5 — Identification Pipeline Resilience | ✅ Complete (T8.A–T8.G) |
| 9 — Quality, Testing & Hardening | ✅ Complete — merged to dev |
| 9.5 — Species Card Harvest + Async Reliability | ✅ Complete — merged to dev |
| 10 — Contextual Scanning & Treatment Polish | ✅ Complete (T10.A–I, PP-071–078) — merged to dev incl. post-phase bugfixes |
| DEPLOY — Launch Preparation | ⚙️ T-DEPLOY.1–5(code) ✅ merged (PRs #125/#126); remaining: Railway/Vercel account setup (DEPLOYMENT.md runbook) → deploy → beta (T-DEPLOY.6–7) → v1.0.0 (T-DEPLOY.8) |

---

## What Was Built (brief per phase)

**Phase 0–1:** Spring Boot + Angular skeletons, Docker Compose (Postgres 15 + Redis 7), CI/CD, JWT auth, Plant CRUD.

**Phase 2:** Async Kafka identification pipeline (POST /analyze → 202 → poll), gpt-4o vision + DeepSeek-R1 text, polygon disease annotation, Redis photo storage with SHA-256 dedup, image-dimension locking.

**Phase 3:** Reminder CRUD + scheduler (one daily push per user), web-push VAPID, actionable care plans with ROUTINE reminders and multi-step TREATMENT plans, Mermaid diagrams.

**Phase 4:** AI chat (Ollama/GitHub Models), plant-context injection, SSE streaming, conversation history.

**Pre-Phase-5 cleanup:** Reminder double-completion fix, duplicate treatment fix, species care cards, chat history/SSE, 4 controller ITs, JaCoCo gate 55%.

**Phase 6:** Species entity (shared, not per-user), Treatment entity (disease lifecycle wrapping TreatmentPlan), 5-tab bottom nav, Home page, species-first Garden, Plant page icon-bar, 3-path identification flow (Flow 1/2/3), event-driven Treatment→TreatmentPlan completion sync.

**Phase 7:** VisionModelPreference + ReasoningModelPreference split, silent-fallback removal, RateLimitException + retryAfterSeconds, model-usage badges, batch scan (BatchScanService), multi-treatment picker, disease-description poll fix, LenientJsonParser.

**Phase 8:** PlantNet v2 (ranked candidates, organs, attribution, geolocation flora), disease cross-check (second opinion for Flow 3), GBIF/POWO/IUCN enrichment, quota telemetry, Claude (AnthropicClient) as 5th model option for both vision and reasoning.

**Phase 8.5:** Per-stage status model on Identification (identificationStatus/annotationStatus/candidateStatus, D5), non-fatal annotation + PlantNet stages, predictedOrgans fix, token-budget Bucket4j + finite executor queue + jittered retry, PlantNet enrichment moved to async fire-and-forget (D1 amendment), retry endpoint, stage-aware UI.

**Phase 9:** Frontend unit tests (Jest), Playwright E2E (Chromium + WebKit, AI calls stubbed), visual regression + a11y (axe) + Lighthouse CI, AI visual review (advisory), Testcontainers ITs wired into mvn verify + JaCoCo 80%, Sentry + X-Correlation-ID, gitleaks + Trivy + OWASP + Dependabot, nightly AI eval suite + prompt-injection guardrails.

**Phase 9.5:** PlantNet candidate data harvested onto Species at resolve time (family, genus, imageUrl, attribution), GenerationStatus (PENDING/READY/FAILED) on Species.descriptionStatus + Treatment.descriptionStatus, regenerate endpoints, Species overview redesigned with real PlantNet hero + candidate strip + status-driven prose, Treatment description poll + retry, dedicated Task Step page (full-width Mermaid, AI deep-links), plant-edit back-nav fix.

---

## Next Tasks (ordered)

1. **T-DEPLOY.5 — account-side deployment (manual, runbook in DEPLOYMENT.md):**
   Kafka decision DONE (in-process, PR #126); GITHUB_TOKEN rotation DONE (owner,
   2026-07-15). Remaining: create Railway project (Postgres+Redis add-ons, env
   vars per backend/.env.example — postgres://→JDBC translation for DATABASE_URL,
   do NOT set KAFKA_BOOTSTRAP_SERVERS) + Vercel project + GitHub secrets
   (RAILWAY_TOKEN, VERCEL_*, VAPID_PUBLIC_KEY, SENTRY_DSN) + repo variable
   BACKEND_PUBLIC_URL; push to main auto-deploys; verify health/login/one full
   identification (that boot doubles as the no-Kafka smoke test).
2. **Re-enable E2E in CI** — need `ng serve + wait-on` before Playwright runs (deferred T9.2).
3. **Re-enable Lighthouse CI** — fix dist path `dist/frontend` → `dist/plantpal` (deferred T9.3).
4. **T3.3** — manual on-device push/PWA testing — folded into Phase DEPLOY beta.

---

## Key Open Decisions

- **D10.1 — Annotation strategy:** Conservative skip chosen (skip when healthStatus ≠ ISSUES_DETECTED). Full merge-into-single-call is a follow-up option. ✅ Implemented.
- **D10.2 — Health-scan card draft state:** Client-side only confirmed. `@Input() isDraft` + `@Input() treatmentActive` on CareCardComponent. ✅ Implemented.
- **D10.3 — Treatment auto-progression:** Root cause was H3 (CareCardComponent + plant-detail chaining craftPlan). Fixed in T10.C + T10.F. ✅ Resolved.
- **Kafka prod story:** ✅ RESOLVED (owner, 2026-07-15) — v1.0.0 ships WITHOUT Kafka:
  `app.identification.transport=in-process` in prod/staging (PP-091, PR #126). Kafka stays
  the dev/local default. Brokered async revisited at scale.

---

## Phase 10 — T10.G + T10.H + T10.I (2026-06-28, PHASE10 branch)

**T10.G — Mic permission pre-check (PP-076, merged PHASE10):**
Original fix: added `getUserMedia` pre-check before `recognition.start()`. Later found to cause
mic contention — see T10.I below. Pre-check removed; pattern superseded by T10.I.

**T10.H — TTS Read Aloud (PP-077, merged PHASE10):**
`SpeechService` (`providedIn:'root'`, `NgZone`-safe callbacks) + `ReadAloudButtonComponent`
(SharedModule, `volume_up`/`stop_circle`). Wired into: CareCardComponent header,
SpeciesDetail About section (READY guard), TreatmentDetail disease description (READY guard),
TreatmentStepListComponent per-step, HomeComponent reminder rows (overdue + today,
`$event.stopPropagation()`), ScanDetailComponent userContext block. PlantModule gained
`SharedModule` import for ScanDetail access.

---

**T10.I — isSecureContext gate + mic contention root cause (PP-078, PHASE10 branch):**
- `speechSupported = !!SpeechRecognitionAPI` (hides button on Firefox) + `speechSecure = window.isSecureContext || location.hostname === 'localhost'` (disables + tooltip on plain HTTP over LAN).
- `/voice-test` diagnostic page added at `features/voice-test/` — two-panel layout (Step 1: getUserMedia mic test; Step 2: SpeechRecognition without prior getUserMedia). Accessible via user menu. Reveals that getUserMedia-before-recognition causes silent mic contention on Chromium.
- `startListening()` simplified: `getUserMedia` pre-check entirely removed, calls `doStartRecognition()` directly. `requestingPermission` flag and "Requesting access…" chip removed.
- ESLint CI fix: empty `() => {}` → `() => { /* ... */ }` in voice-test; `// eslint-disable-next-line` → block form to cover multi-line `(window as any)` declarations in both files.
- All callbacks wrapped in `ngZone.run()` (NgZone fix from the previous session for textarea not updating).
- Commits: `d9e3eaf` (voice input fix), `5691763` (ESLint fix).

## Post-Phase-10 Bugfixes (2026-06-28, PHASE10 branch)

Three frontend UX fixes (commit 6c10241):
- **Scans blank after submit** — `submitAddScan()` now calls `loadScanHistory()` immediately when `activeSection === 'scans'` instead of leaving the section blank.
- **Plant overview merged care plan** — Initial fetch changed to size=10; `mergedCarePlan` getter deduplicates care cards by `type` across all scans. `loadScanHistory()` updates `allIdentifications` as side-effect.
- **Species page botanical facts** — "Botanical facts" section (family/genus/IUCN badge) added to species-detail overview; visible regardless of `descriptionStatus`. `gbifId`/`powoId`/`iucnCategory` added to frontend `SpeciesResponse` model.

Backend race condition fix (commit d2c03e3):
- **`@Async` + `@Transactional` race in `SpeciesEnrichmentServiceImpl`** — `createSpecies()` was firing `enrich()` inline before the outer transaction committed; `enrich()`'s own `findById` returned null → enrichment silently skipped → `descriptionStatus` stuck at PENDING forever. Fixed with `TransactionSynchronizationManager.registerSynchronization(afterCommit)` in both `createSpecies()` and `regenerateDescription()`.

---

## Dev Infrastructure

**LAN HTTPS (2026-06-28):** Frontend Nginx now serves HTTPS on port 443 (self-signed cert) for
mobile device testing. Port 80 redirects to 443. Cert lives at `frontend/nginx/certs/self.crt`
(committed); key at `frontend/nginx/certs/self.key` (gitignored). One-time cert generation:
```
mkdir -p frontend/nginx/certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout frontend/nginx/certs/self.key \
  -out frontend/nginx/certs/self.crt \
  -subj "/CN=plantpal.local" \
  -addext "subjectAltName=IP:<LAN_IP>"
```
Then `docker compose build frontend && docker compose up -d frontend`. Phone: navigate to
`https://<LAN_IP>`, tap Advanced → Proceed. Verify via `/voice-test`: isSecureContext = true.
Production (Railway/Vercel) is unchanged — they already terminate TLS.

---

## Known Tech Debt

- JaCoCo gate at 80% (restored in T9.5 — verify still holds after Phase 9.5 additions).
- E2E disabled in CI (needs `ng serve + wait-on` setup).
- Lighthouse CI disabled (dist path issue).
- ~~GITHUB_TOKEN: rotate before prod (T9.7 reminder).~~ ✅ Rotated (owner, 2026-07-15).
- `generateCarePlan()` in DeepSeekClient is dead code (never called — safe to remove in a cleanup pass).
- `PlantNetClient` PLANTNET enum: if a future cleanup removes it, remove from both backend enum and frontend type simultaneously.
- SSE streaming (chat): written and unit-tested but never confirmed against a live Docker stack with actual incremental token delivery — re-verify before DEPLOY.
- DeepSeekAnnotationClient 429 shape differs from DeepSeekClient's RateLimitException — noted in ARCHITECT.md, not yet unified.

---

## 2026-07-09 — Platform demand system adopted (platform-delta)
- Completed §2 demand-folder structure (added `demands/README.md`, `demands/fulfilled/.gitkeep`, `demands/archive/.gitkeep`; existing live demand `demands/2026-07-09-ai-gateway-full-ai-coverage.md` left untouched) and documented the convention in a new **Demand system** section of `.claude/CLAUDE.md`. See `../DEMAND_SYSTEM.md`.
- Candidate demands scanned: none new — every cross-repo need in PROGRESS.md/STATE.md targets ai-gateway/contracts and is already subsumed by the raised full-ai-coverage demand (G1–G6).
- Next step: no action needed; at session start check demand traffic both directions (dispatched-to-plantpal, and the raised ai-gateway demand for satisfaction → archive when closed).

## 2026-07-10 — ai-gateway full-coverage demand closed: PlantPal's follow-ups (platform-delta, PP-088)
- Full detail in PROGRESS.md's 2026-07-10 entry (platform-delta home per the platform integration
  notice at the top of this repo's `.claude/CLAUDE.md`). Summary: gateway routing extended to
  GITHUB_GPT4O/GITHUB_GPT41 identification + the gpt-4o-mini annotation call (G1), PlantNet
  projects/languages/quota/disease-check now route through a new `PlantNetGatewayClient` (G4),
  `ai-model-manifest.yaml` committed at repo root (G5, declarative only pending upstream fixes).
  289/289 backend unit tests green. Branch `feature/PP-088-gateway-full-routing`, based on `main`
  (see PROGRESS.md's branch-base note — `dev` is 98 commits behind and lacks the gateway
  infrastructure entirely; needs a fast-forward). Demand archived to `demands/archive/`.
- Next step: architect review/merge; `dev` fast-forward; runtime `AI_MANIFEST_DIR` mount + contracts
  v0.9.0 defect fixes are prerequisites before the manifest is actually consumed.

## 2026-07-15 — Phase 10 closed, plants-vault removed, Phase DEPLOY started (T-DEPLOY.1)

**Phase 10 closure:** verified T10.A–I fully merged to dev (PHASE10 ancestor check + file-level
verification by agents); reconciled stale build-table/state rows (main `9a8667c`). dev was
fast-forwarded to main twice this session (last: `13f00c3`) — dev == main at session start of
DEPLOY work.

**plants-vault REMOVED (owner ruling, in-session):** the external vault never existed on disk;
owner ruled it removed — `.claude/` is the single memory layer, platform guidelines apply
(PROGRESS.md for platform-delta). Scrubbed CLAUDE.md/ARCHITECT.md/README/save-command, deleted
VAULT_SYNC_TEMPLATE.md, D6 superseded (main `aa1c7dc`). The vault-ruling demand raised earlier
was deleted entirely per owner (`13f00c3`). Phase-end rule is now "doc sync" (.claude files only).

**T-DEPLOY.1 (PP-089, `feature/PP-089-prod-config`, pushed, commits `2cbe5ab`+`77eb127`+`ba5ad5e`):**
staging/prod YAML (HikariCP 20/5/20000, ${DATABASE_URL}/${REDIS_URL}, env-driven CORS no-fallback,
actuator health+info), logback-spring.xml (JSON logstash-encoder 7.4 in staging/prod, pattern
elsewhere; needed `defaults.xml` include — %wEx is Spring-Boot-registered, first CI run
29385043867 failed all ITs on it), chat rate limit now `app.rate-limit.chat-messages-per-hour`
(default 10/h — was hardcoded 30/h; behavior change) via constructor @Value + 2 new unit tests.
295 unit tests + spotless green; frontend CI green.

**T-DEPLOY.1 CI aftermath (resolved same day):** run 29385261980 exposed two reds — (1) %wEx
is a Spring-Boot-registered logback conversion word, fixed by including defaults.xml in
logback-spring.xml (`ba5ad5e`); (2) pre-existing red inherited from main: GatewayPlatformProfileIT
asserted the pre-D045 localhost gateway default, stale since `5871c1f` — aligned (`b3fe65b`).
Run 29414291746 = fully green.

**T-DEPLOY.2–4 (PP-090, `feature/PP-090-perf-security-docs`, merged to dev via PR #125):**
- Migration **032**: idx identifications(plant_id, created_at DESC), care_logs(user_id,
  performed_at DESC), treatments(plant_id, status). reminders/species indexes NOT added —
  already covered (004's idx_reminders_due, 016's unique + idx_species_scientific_name).
- `GardenContextService` bean extracted from ChatServiceImpl (self-invocation can't proxy —
  same rule as @Async) with @Cacheable garden 5min; species::{id} 10min; @CacheEvict wired at
  plant create/update/archive/saveFromIdentification, species findOrCreate/regenerate/enrich,
  treatment create/craftPlan/complete.
- Security: nosniff/frame-deny/HSTS + same-origin CSP (Swagger paths get scoped 'unsafe-inline');
  AuthRateLimitFilter login 5/min/IP + register 3/h/IP (X-Forwarded-For aware, LRU-bounded,
  429+Retry-After) — `auth-attempts-per-minute` + new `register-per-hour` keys now REAL;
  `ai-calls-per-hour` now injected in IdentificationServiceImpl (was hardcoded 20);
  OWASP html-sanitizer on plant nickname/notes/location (HtmlSanitizer util, entity-unescape
  preserves emoji); ChatRequest @Size(2000). GITHUB_TOKEN rotation still MANUAL, outstanding.
- OpenAPI: all 13 controllers annotated, @Schema examples on key DTOs, dev+prod servers.
- Frontend: 9 components → OnPush (+markForCheck at async mutations) + trackBy; chat stream
  list & plant-detail intentionally NOT converted (in-place SSE mutation / out-of-zone
  IntersectionObserver); lazy loading verified per-module via stats build.
- Verification: backend 333/333 units (38 new), frontend 41/41 Jest + lint + prod build,
  CI run 29417796281 fully green (ITs + JaCoCo 80% + spotless). Behavior change shipped in
  T-DEPLOY.1: chat rate limit 30/h hardcoded → 10/h configurable.

**Tech debt found:** Testcontainers can't discover Docker locally (Docker 29.6.1 running, CLI
fine, discovery fails even unsandboxed) — full `mvn verify` gate currently only provable in CI.
Also: CI triggers only on push/PR to main + workflow_dispatch — feature branches need manual
`gh workflow run ci.yml --ref <branch>`; main pushes earlier on 2026-07-15 were red (stale
gateway IT) and nobody noticed.

**T-DEPLOY.5 code-prep (PP-091, `feature/PP-091-sync-identification-fallback`, merged PR #126):**
Owner decisions: Kafka = in-process transport for v1.0.0; GITHUB_TOKEN rotation confirmed done.
- `app.identification.transport` (kafka default; in-process in prod/staging YAML).
  IdentificationDispatcher: Kafka impl = old publish; InProcess impl calls
  processIdentification() cross-bean (existing @Async proxies; @Lazy breaks the bean cycle;
  202+poll contract unchanged). KafkaConfig/topic configs/IdentificationConsumer gated
  @ConditionalOnProperty(transport=kafka). Residual sends (identification.completed topic,
  PlantCountDimensionEmitter's plant_count dimension event) guarded by
  KafkaTransportProperties.isKafkaEnabled() — an autoconfigured KafkaTemplate send against a
  dead broker blocks up to 60s (max.block.ms), so the property guard, not bean-presence, is
  the safety. bootstrap-servers has an inert localhost default (KafkaProperties binds eagerly).
  Note: "BatchScanService" from Phase-7 docs does not exist as a backend class — batch scan is
  a frontend UX over the same submit path; inherits the dispatcher automatically.
- Deploy plumbing: backend/Dockerfile.railway (runtime-only; regular Dockerfile needs the
  contracts-m2 host context Railway lacks) + railway.json (healthcheck /actuator/health);
  deploy.yml now injects VAPID_PUBLIC_KEY/SENTRY_DSN into environment.prod.ts pre-build and
  generates vercel.json in dist (api rewrite from repo var BACKEND_PUBLIC_URL — fails fast if
  unset — + SPA fallback); DEPLOYMENT.md has the full first-time runbook.
- Verification: 349 unit tests green (16 new), spotless clean, CI run 29452096127 fully green.
  Residual risk: a real prod boot without KAFKA_BOOTSTRAP_SERVERS was annotation-tested but not
  executed — the first Railway staging boot is the definitive smoke test (watch for absence of
  Kafka connection-retry noise).
