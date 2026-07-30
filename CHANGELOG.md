# Changelog — plantpal

> PlantPal's own development history (85+ merged PRs, Phase 1 → Phase 10) lives in
> its git log and `.claude/STATE.md`/`TASK_PLAN.md` — this file tracks only the
> **platform-delta** work (D013), starting from Chunk 0.

## [Unreleased] (opened 2026-07-04)

### Added
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
