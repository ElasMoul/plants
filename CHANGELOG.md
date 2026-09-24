# Changelog — plantpal

> PlantPal's own development history (85+ merged PRs, Phase 1 → Phase 10) lives in
> its git log and `.claude/STATE.md`/`TASK_PLAN.md` — this file tracks only the
> **platform-delta** work (D013), starting from Chunk 0.

## [Unreleased] (opened 2026-07-04)

### Added
- Backend hosting moved from Railway to an OVH VPS (2026-09-24): `deploy/vps/`
  holds the Compose stack (Postgres 15, Redis 7 with password + AOF, runtime-only
  backend image, Caddy TLS for `api.plants.moulworks.com`), a one-time
  `bootstrap.sh` (Docker, ufw, fail2ban, swap, `deploy` user, key-only SSH,
  nightly `pg_dump`) and `.env.example`. `deploy.yml`'s backend job now `scp`s the
  stack + JAR and waits for the container healthcheck; `backend/railway.json` and
  `Dockerfile.railway` removed. Fresh database (no Railway data migrated).
- Chunk 0 — platform Room bootstrap: `HEXAGON.md` and `DEPLOYMENT.md` added
  (PlantPal already carries `README.md`/`SECURITY.md`; this is the first real
  retrofit of the D013 convention onto a pre-existing, already-deployed app).
  `app-manifest.yaml` static registry record (`app.manifest` contract, routes
  drawn from the real `@RequestMapping` paths). `.claude/CLAUDE.md` gained a
  platform-integration notice prepended above PlantPal's own 516 lines of
  instructions (untouched).
  No application code changed — repo cloned as-is at `dev` (post PHASE10 merge)
  onto a new `platform-integration` branch.
- Dimension-event emission (D024/D027): `PlantServiceImpl` publishes a
  `dimension.event` (`plant_count`, delta `+1`/`-1`) to Kafka topic `dimension.events`
  on plant create and archive, for Treasury's business-dimension metering. Bumped
  `contracts` pin to `v0.5.0` (adds the `DimensionEvent` schema). New
  `com.plantpal.plant.config.PlantKafkaTopicConfig` (topic bean, mirrors
  `identification.config.KafkaTopicConfig`'s pattern).
- State-feed emitter (D029): new `com.plantpal.statefeed.StateFeedEmitter`
  outbound port, gated by the `platform` Spring profile
  (`application-platform.yml`, same pattern as the gateway swap). Emits
  `state.event`'s `app.status` once on `ApplicationReadyEvent` and
  `activity.count` (`identification.completed`) each time the existing
  `IdentificationCompletedEvent` fires (now also published via
  `ApplicationEventPublisher` alongside its pre-existing Kafka send — no new
  cross-package injection needed). Fire-and-forget over `POST
  {platform.statefeed.url}/events`, 2s connect / 5s read timeouts, any failure
  logged at WARN and swallowed — the feed is a read-only mirror
  (spec-state-feed.md §3), never load-bearing. New
  `StateFeedProperties`/`StateFeedEmitter` unit tests (7 + 1 cases): default
  gating, payload shape, transport-failure swallowing.

- Auth characterization suites made blocking CI evidence (Factory feature
  mission `9636b730-d536-4ad3-99e8-80c2797f9be3`, demand
  `app-studio-20260907-plantpal-t1-*`) — **delivered waves only, feature not
  complete**. Shipped: T1.20 auth surface inventory (23 frontend route
  records, 48 backend endpoint records, 3 defect pins) and structural
  verifiers; T1.22 protected-endpoint denial characterization
  (`ProtectedEndpointAuthTest`, 88 cases); T1.23 AuthGuard
  defect-characterization coverage; T1.24 JwtInterceptor
  defect-characterization coverage; T1.25 cross-origin session-handoff
  characterization (8 cases); T1.26 made the four characterization runners
  (`ProtectedEndpointAuthTest`, AuthGuard, JwtInterceptor, session-handoff)
  a blocking `Auth Characterization CI` job in `.github/workflows/ci.yml`
  on every push/PR, with a structured JUnit-report artifact and mutation-run
  evidence that a real assertion break fails the job. Reports:
  `demands/fulfilled/app-studio-20260907-plantpal-t1-{20,22,23,24,25,26}-report.md`.
  **Outstanding:** T1.27 (owner-observation unit) and any further planned
  waves of this mission are not yet dispatched/shipped — no v1.0.0 tag or
  app-birth release claim applies to this feature.

- Factory mission `9b774285-8a9f-4763-9d27-7310127bc931` Waves 2–4 implementation
  checkpoint (candidate only; no deployment or enforcement activation): protected-route
  return URLs are validated and restored after login; concurrent protected-API 401s use one
  sign-out/redirect flow; and explicit sign-out best-effort revokes the registry record.
  The new PlantPal-owned Redis session registry issues a per-token `jti`, maintains a 30-minute
  sliding record plus a signed 12-hour absolute cap, distinguishes revocation reasons, and is
  deliberately **inert by default** (`app.session.enforcement-enabled=false`). The client polls
  only the explicit non-renewing status endpoint, warns at two minutes, renews only after an
  explicit user action, and preserves a safe return destination on an expiry verdict. Verified
  in the recovered session: backend unit suite 451/451, focused auth/session suite 108/108,
  and frontend expiry regression 11/11 plus production build. That earlier Docker limitation was
  corrected in the follow-up candidate checkpoint: Testcontainers was updated for Docker Desktop
  29 and the test profile selects the supported in-process identification transport, allowing the
  full verified suite to run. Wave 5's separate enforcement/release/deployed-live-verification
  decision remains outstanding.

- Factory mission `9b774285-8a9f-4763-9d27-7310127bc931` acceptance-evidence pass
  (2026-09-16, branch `feature/PP-100-session-hardening-waves-2-4`; waves 2–4 remain a
  candidate — **no enforcement activation, no deployment, no live verification, no merge**).
  Added `SessionEnforcementFilterTest` (14 cases): the wave-3 criterion "rejected and expired
  sessions cannot call protected APIs" previously had no test at all — the registry test only
  proved the service returns `invalid(...)`, and the existing denial suite ran with
  `enforcementEnabled=false`, so the branch that withholds authentication was never exercised.
  The suite now proves every `RevokedReason` yields 401 plus the `X-Session-Revoked-Reason`
  header, that an unavailable registry fails closed once enforcing and open while inert, that the
  same rejected session is still admitted while the flag is off (pinning ADR-8's inert default and
  the wave-5 cutover boundary), and that the non-renewing status endpoint peeks rather than slides
  — server-side proof that background polling cannot extend a session, which was previously
  untested. It is a surefire `*Test` needing no Spring context and no Testcontainers, so the
  enforcement boundary is verifiable where Docker is unavailable. Also applied `spotless` to the
  mission's own sources (it is not lifecycle-bound, so `mvn clean verify` never ran it) and
  recorded the wave-2 disposition of the wave-1 defect pins in
  `docs/auth-hardening/defect-pins.md`. Verified: backend unit 465/465, frontend Jest 549/549
  across 44 suites, production build exit 0, `spotless:check` clean. The later candidate
  checkpoint establishes backend integration verification after the Testcontainers/test-profile
  correction; its raw evidence and remaining Wave 5 boundary are in
  `docs/auth-hardening/candidate-375dfa9.md`.

- Factory mission `9b774285-8a9f-4763-9d27-7310127bc931` current-candidate checkpoint
  `6981a45` (2026-09-19, branch `feature/PP-100-session-hardening-waves-2-4`) — again
  waves 2–4 only, still **no enforcement activation, no merge, no deployment, no live
  verification**. CI and Secret Scanning both pass at this revision (runs `35437826917`,
  `35437826943`); the preceding `f2f5a44` head was red because the Testcontainers
  correction's new registry test failed lifecycle-bound `spotless:check` while all tests
  passed, which `a75070a` corrected. Receipts were re-measured at `6981a45` rather than
  inherited: backend focused 111/111 (88 protected-endpoint denial, 14 enforcement-filter,
  9 registry) and frontend session specs 26/26 across 5 suites, each with its own exit
  code. Reviewing wave-2's "a safe local destination is restored after login" clause
  against the suite showed it unasserted — `login.component.spec.ts` covered only the
  no-`returnUrl` default — so the restore path and its unsafe-input fallback are now
  covered, the first proven non-vacuous by mutation. The 2026-09-16 fulfillment report
  for this mission, which the origin ruled out of
  the active `demands/fulfilled/` scan, is preserved as history at
  `docs/auth-hardening/evidence/mission-9b774285-historical-partial-fulfillment.md`; the
  current candidate record is `docs/auth-hardening/candidate-6981a45.md`. Wave 5's
  separate, still-unauthorized enforcement/release decision remains the outstanding
  criterion.

### Fixed
- **Identification AI-JSON parsing broke on a markdown-fenced response**
  (`IdentificationServiceImpl.parseIdentificationResult`), causing a
  successful identification to be logged as `Malformed identification JSON
  from DeepSeek, using fallback` and silently saved as "Unknown Plant"
  confidence 0.3 even when the model correctly identified the plant
  (confirmed live: PlantNet independently matched "Coleus spp." on the same
  photo). Root cause: the provider (or the ai-gateway passthrough path, which
  bypasses each client's own `stripThinkTags()`) wrapped its JSON in a
  ` ```json ... ``` ` code fence, and `objectMapper.readValue()` was called on
  the raw fenced string. Added a single reusable `extractJson()` helper
  (mirrors sentinel-hub's `parse.py`): strips a leading/trailing
  ` ```json`/` ``` `/`~~~` fence if present, otherwise falls back to
  extracting the substring from the first `{` to the last `}`. Wired into
  every AI-JSON parse site in the class — `parseIdentificationResult`,
  `parseCarePlan`, `parseCureAdvice`, `parseDuplicateGroups` — all of which
  shared the same fence vulnerability. Existing fallback behavior for
  genuinely malformed output is unchanged; this only rescues
  fenced/prose-wrapped-but-otherwise-valid JSON. 4 new regression tests
  (`IdentificationServiceImplTest$AiJsonFenceRecovery`): fenced JSON now
  parses to real values, bare JSON still works, prose-then-JSON works,
  genuinely-garbage output still hits the fallback.
- Gateway-routed PLANTNET identification (`IdentificationServiceImpl.runIdentification`)
  now attaches `organs`/`project`/`lang` to the `AiRequest` context so
  ai-gateway's `PlantNetAdapter` receives them — previously only the image made
  the trip on the gateway path, silently dropping the user's PlantNet flora/lang
  preference and any explicit `organs` list. Direct (non-gateway) path was
  already correct and unaffected.
- **Gateway-routed identification JSON was truncated mid-array, falling back to "Unknown
  Plant"** (live: `WARN IdentificationServiceImpl - Malformed identification JSON ...
  Unexpected end-of-input: expected close marker for Array`) — the fence-stripping fix above
  was already working; this failure was pure truncation. Root cause: ai-gateway's
  `AnthropicAdapter` defaults `max_tokens` to 2048 when the request `context` carries no
  `maxTokens` entry, but none of `IdentificationServiceImpl`'s gateway request builders set
  one, silently halving the budget the direct clients use
  (`AnthropicClient.DEFAULT_MAX_TOKENS` / `DeepSeekClient.O4_MINI_MAX_COMPLETION_TOKENS`, both
  4096). Added `.putContextItem("maxTokens", ...)` to every gateway `AiRequest` whose response
  is parsed as structured JSON: the main identification+care-plan request
  (`identificationGatewayRequest`, new `GATEWAY_IDENTIFICATION_MAX_TOKENS = 8192` — its response
  nests species/confidence/health fields plus a full multi-card care plan, comfortably bigger
  than the others), the cure-advice reasoning request and the annotation (region polygons)
  request (both `GATEWAY_MAX_TOKENS = 4096`, matching the direct-path floor). PlantNet's gateway
  passthrough is unaffected — it's a REST proxy, not an LLM call, so the extra context field is
  a no-op there. New regression assertions in `IdentificationServiceImplTest` (identification,
  cure-advice, and annotation gateway-routing tests) assert `context.maxTokens >= 4096`.
- `deploy.yml`'s `build-backend` job ran a plain `mvn package`, which fails on a
  GitHub runner since `backend/pom.xml` unconditionally depends on
  `io.platform:contracts:0.5.0` (no registry, built from a pinned tag per D031).
  Replicated `ci.yml`'s three contracts steps (read pinned version from the pom,
  checkout `contracts` at that tag, `mvn install` its Java bindings) before the
  Package step, so the two workflows can never disagree with the pom. Workflow
  stays disabled (owner re-enables after review) — only the build steps changed.

### Changed
- Re-pinned `contracts` `v0.7.0` → `v0.17.0` (2026-07-30). Of the intervening
  releases only **v0.12.0** is breaking, and it only pattern-constrains
  `hexagon.descriptor.json`'s `contracts.used` items — PlantPal's `HEXAGON.md`
  frontmatter ids (`app.health`, `app.manifest`, `ai.request`, `ai.response`,
  `dimension.event`, `state.event`) all already conform, so nothing to fix.
  Everything PlantPal actually consumes in Java (`AiRequest`,
  `AiRequestMediaInner`, `AiResponse`, `BlockedResponse`, `DimensionEvent`,
  `AppStatusEvent`/`AppStatusPayload`, `ActivityCountEvent`/`ActivityCountPayload`)
  is additive-only across the range; v0.13.0's `AiResponse` widening
  (`result`/`model`/`provider` now optional, new `skipped` field) is
  source-compatible with PlantPal's usage. `HEXAGON.md` frontmatter pin bumped
  to match; Docker build-context wiring (`docker-compose.yml`,
  `backend/Dockerfile`, `DEPLOYMENT.md`) renamed `CONTRACTS_M2_0_7_0` →
  `CONTRACTS_M2_0_17_0`. CI workflows read the pin dynamically off
  `backend/pom.xml` — unaffected.
- Re-pinned `contracts` `v0.5.1` → `v0.7.0` (all intervening releases —
  v0.6.0/v0.6.1/v0.6.2/v0.7.0 — are additive or patch-only per contracts'
  `CHANGELOG.md`; no breaking change touches anything PlantPal consumes).
  `HEXAGON.md` frontmatter pin bumped to match; Docker build-context wiring
  (`docker-compose.yml`, `backend/Dockerfile`) renamed
  `CONTRACTS_M2_0_5_1` → `CONTRACTS_M2_0_7_0`. CI workflows already read the
  pinned version dynamically off `backend/pom.xml` — unaffected.
- Gateway swap is now gated by a **Spring profile** (`platform`,
  `application-platform.yml`) instead of a boolean default living inside
  `application.yml`/`application-dev.yml`. `platform.gateway.*` no longer
  appears in either base file; `GatewayProperties` binds `enabled=false` via
  `@DefaultValue` when the prefix is entirely absent, so the default/standalone
  boot never reads a `platform.*` key (D009). Activate with
  `SPRING_PROFILES_ACTIVE=dev,platform` to route AI calls through `ai-gateway`
  (`platform.gateway.enabled` defaults `true` under that profile). Railway prod
  never activates `platform`. Added `GatewayStandaloneProfileIT`/
  `GatewayPlatformProfileIT` context tests for both cases. (PP-081, PR #107)

## 2026-07-07

### Fixed
- **FIX-12** — `plant_count` dimension-event Kafka emit moved out of the
  `@Transactional` service method body into an intra-JVM
  `PlantCountChangedEvent` forwarded to Kafka by a new
  `PlantCountDimensionEmitter` bound to
  `@TransactionalEventListener(phase = AFTER_COMMIT)`. A rolled-back
  transaction can no longer leak a phantom `plant_count` delta to Treasury.
- **SEC-4** — `backend/.env.example`'s `JWT_SECRET` no longer commits a
  real-looking generated value; replaced with an explicit
  `<generate: openssl rand -base64 64>` placeholder. `application-test.yml`'s
  test secret replaced with an obviously-fake repeating constant (still valid
  Base64, still >= JJWT's 256-bit HS256 minimum). `.gitignore`'s malformed
  `./backend/.env` line removed.
- Docker build was unbuildable from a clean checkout: `backend/Dockerfile`
  relied on a BuildKit cache mount that only holds the `contracts` jar if a
  prior build happened to populate it. Switched to `COPY --from=contracts-m2`,
  a named additional build context (matches `../ai-gateway/Dockerfile`'s
  pattern); `docker-compose.yml` wires it via `CONTRACTS_M2_0_5_1`. Documented
  in `DEPLOYMENT.md`.

### Changed
- Contracts re-pinned `0.5.0` → `0.5.1` (`backend/pom.xml`, `HEXAGON.md`) —
  picks up the v0.5.1 `runId` int32→int64 overflow correctness fix.
- `HEXAGON.md`/`DEPLOYMENT.md` pin prose now points at `backend/pom.xml` as
  the authoritative source instead of hardcoding a version number that drifts.
- `README.md` overhauled: Java 21 (was 17), real 5-provider AI stack (was
  Anthropic-only), MVP table flipped to shipped, `.claude/` file-location
  table added, stale dev-branch strategy removed.
