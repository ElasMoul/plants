# Changelog — plantpal

> PlantPal's own development history (85+ merged PRs, Phase 1 → Phase 10) lives in
> its git log and `.claude/STATE.md`/`TASK_PLAN.md` — this file tracks only the
> **platform-delta** work (D013), starting from Chunk 0.

## [Unreleased]

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

### Fixed
- Gateway-routed PLANTNET identification (`IdentificationServiceImpl.runIdentification`)
  now attaches `organs`/`project`/`lang` to the `AiRequest` context so
  ai-gateway's `PlantNetAdapter` receives them — previously only the image made
  the trip on the gateway path, silently dropping the user's PlantNet flora/lang
  preference and any explicit `organs` list. Direct (non-gateway) path was
  already correct and unaffected.
- `deploy.yml`'s `build-backend` job ran a plain `mvn package`, which fails on a
  GitHub runner since `backend/pom.xml` unconditionally depends on
  `io.platform:contracts:0.5.0` (no registry, built from a pinned tag per D031).
  Replicated `ci.yml`'s three contracts steps (read pinned version from the pom,
  checkout `contracts` at that tag, `mvn install` its Java bindings) before the
  Package step, so the two workflows can never disagree with the pom. Workflow
  stays disabled (owner re-enables after review) — only the build steps changed.

### Changed
- Gateway swap is now gated by a **Spring profile** (`platform`,
  `application-platform.yml`) instead of a boolean default living inside
  `application.yml`/`application-dev.yml`. `platform.gateway.*` no longer
  appears in either base file; `GatewayProperties` binds `enabled=false` via
  `@DefaultValue` when the prefix is entirely absent, so the default/standalone
  boot never reads a `platform.*` key (D009). Activate with
  `SPRING_PROFILES_ACTIVE=dev,platform` to route AI calls through `ai-gateway`
  (`platform.gateway.enabled` defaults `true` under that profile). Railway prod
  never activates `platform`. Added `GatewayStandaloneProfileIT`/
  `GatewayPlatformProfileIT` context tests for both cases.
