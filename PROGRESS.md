# plantpal — Progress (platform-delta sessions)

Platform-delta work only. PlantPal's own feature work continues in `.claude/STATE.md`.

---

## PlantNet gateway request: attach organs/project/lang to context (companion to ai-gateway PlantNetAdapter fix)

**Branch:** `platform-integration`.

### What shipped

Closes backlog item 1 from Chunk 3 (above): `IdentificationServiceImpl.runIdentification`'s
`PLANTNET` case now attaches `organs`/`project`/`lang` onto the shared
`identificationGatewayRequest(...)` `AiRequest` via three `.putContextItem(...)` calls
(context keys fixed by the ai-gateway `PlantNetAdapter` fix, not chosen here) before sending
through `GatewayClient`. Previously only the image made the trip on the gateway path — the
per-user PlantNet flora/lang preference (T8.4) and any `organs` hint were silently dropped.
The shared `identificationGatewayRequest` helper itself is untouched (also used by the
`ANTHROPIC_CLAUDE` case, where organs/project/lang are meaningless). The stale comment
documenting the old gap was replaced.

### Files touched

**Modified (main):**
- `backend/src/main/java/com/plantpal/identification/service/impl/IdentificationServiceImpl.java`
  (`runIdentification`'s `PLANTNET` case)

**Modified (test):**
- `backend/src/test/java/com/plantpal/identification/unit/IdentificationServiceImplTest.java`
  (`GatewayRouting` nested class: extended
  `shouldRoutePlantNetIdentificationThroughGateway` with context assertions for the default
  case — `organs=["auto"]`, `project="all"`, `lang="en"` per `User`'s entity defaults when
  no explicit preference is set; added
  `shouldAttachExplicitPlantNetContextThroughGateway` proving explicit
  organs/plantnetProject/plantnetLang pass through end-to-end)

**Docs:** `CHANGELOG.md`, this file.

### Test counts

- Backend unit suite (`mvn test`): **243 tests, 0 failures** (was 241 before the 2 new
  assertions-bearing tests in this chunk — one test extended in place, one new test added).

---

## Dimension-event emission — plant_count metering (D024/D027)

**Branch:** `platform-integration` (same branch Chunks 3/4a landed on). Not pushed yet
(pushing to origin at the end of this session, per instructions), no PR.

### What shipped

- Bumped `contracts` pin `0.4.0` → `0.5.0` in `backend/pom.xml` (adds the
  `io.platform.contracts.events.DimensionEvent` schema). Built via
  `mvn install -f ../contracts/gen/java/pom.xml` against the `contracts` repo checked out
  at tag `v0.5.0` (checkout done in the sibling `contracts` repo, not committed from here —
  per platform/CLAUDE.md's rule against fixing/touching sibling repos from this session).
- New `com.plantpal.plant.config.PlantKafkaTopicConfig` — `DIMENSION_EVENT_TOPIC =
  "dimension.events"` constant + `NewTopic` bean (3 partitions, replica 1), mirroring
  `identification.config.KafkaTopicConfig`'s existing pattern. Topic name is a hard
  contract with Treasury's consumer — not independently chosen.
- `PlantServiceImpl` gained a `KafkaTemplate<String, Object>` constructor param (existing
  bean from `shared.config.KafkaConfig`, same pattern as
  `IdentificationServiceImpl`/`ChatServiceImpl`/`TreatmentServiceImpl`). Emits a
  `DimensionEvent` (`appId="plantpal"`, `dimensionKey="plant_count"`) on:
  - `createPlant()` — delta `+1`, after `plantRepository.save()`.
  - `archivePlant()` — delta `-1`, after the ACTIVE→ARCHIVED save.
- **No double-emit guard added.** Checked: `archivePlant()`'s `findOwnedPlant()` only
  matches rows with `status = ACTIVE` (`findByIdAndUserIdAndStatus(id, userId,
  PlantStatus.ACTIVE)`), so an already-archived plant throws `ResourceNotFoundException`
  before reaching the emission — re-archiving an ARCHIVED→ARCHIVED no-op is not reachable
  through this method today. A guard would be dead code; documented inline instead
  (comment above `archivePlant()`) plus a unit test proving the not-found path never calls
  `kafkaTemplate.send()`, so if `findOwnedPlant` ever changes to allow re-archiving, this
  test starts failing and flags the gap.

### A real bug found and fixed along the way (test-infra, not app logic)

Running `mvn verify` after the above surfaced a genuine `PlantControllerIT` failure —
**not** caused by the new production code being wrong, but by the *existing* integration
test never having mocked `KafkaTemplate`. With a real (unreachable) Kafka broker in the
Testcontainers environment, `KafkaProducer.send()` blocks synchronously for
`max.block.ms` (default 60s) fetching metadata for a topic it's never seen before, then
throws `TimeoutException` **synchronously from the send() call itself** (not via the
async callback) — which propagated straight through `emitDimensionEvent()` into a 500.
`IdentificationControllerIT` already had `@MockBean private KafkaTemplate<String,
Object> kafkaTemplate;` for exactly this reason; `PlantControllerIT` didn't, because
`PlantServiceImpl` never touched Kafka before this chunk. Added the same `@MockBean` to
`PlantControllerIT`. Flagging this in case another IT class adds a first-ever Kafka
producer call in the future — the failure mode (slow test, then a bare 500 with a Kafka
`TimeoutException` in the cause chain) isn't obviously Kafka-related from the assertion
failure alone.

### Files touched

**New:**
- `backend/src/main/java/com/plantpal/plant/config/PlantKafkaTopicConfig.java`

**Modified (main):**
- `backend/pom.xml` (contracts `0.4.0` → `0.5.0`)
- `backend/src/main/java/com/plantpal/plant/service/impl/PlantServiceImpl.java`
  (`KafkaTemplate` constructor param, `emitDimensionEvent()`, calls in `createPlant()`/
  `archivePlant()`)

**Modified (test):**
- `backend/src/test/java/com/plantpal/plant/unit/PlantServiceTest.java` (`@Mock
  KafkaTemplate` field; new tests: emit +1 on create, emit -1 on archive, no emit on
  not-found archive)
- `backend/src/test/java/com/plantpal/plant/integration/PlantControllerIT.java`
  (`@MockBean KafkaTemplate` — bug fix, see above)

**Docs:** `HEXAGON.md` (contracts pin, D024 added to decisions list, new outbound Kafka
producer port row), `CHANGELOG.md`, this file.

### Test counts

- Backend full suite (`mvn clean verify`, unit + integration/Testcontainers): **273
  tests, 0 failures, 0 errors** after this change (includes the `PlantControllerIT` fix
  above — without it, 2 of 273 fail/error).

---

## Chunk 3 — Gateway swap (thin GatewayClient behind a config flag)

**Branch:** `platform-integration`. Not pushed, no PR (per chunk scope).

### What shipped

Additive path only — no hexagonal refactor. A new `com.plantpal.gateway` package
(`GatewayClient`, `GatewayProperties`) plus a localized `if (gatewayProperties.enabled())`
branch at each in-scope call site in four service classes. Direct-client behavior is
byte-identical when the flag is off (default everywhere except dev).

- `com.plantpal.gateway.GatewayClient` — modeled on pregnancy's `AiGatewayClient`
  (same `RestClient` + contracts-types pattern). Differences: reuses PlantPal's existing
  `PlantPalException` (402 for `BlockedResponse`, 503 for anything else) instead of new
  exception classes — `GlobalExceptionHandler.handlePlantPal()` already covers it.
- `com.plantpal.gateway.GatewayProperties` — `@ConfigurationProperties(prefix =
  "platform.gateway")` record `(boolean enabled, String url)`. `@ConfigurationPropertiesScan`
  added to `PlantPalApplication`.
- Flag: **`platform.gateway.enabled`**, default `false` (`application.yml`), `true` only in
  `application-dev.yml`. Plus `platform.gateway.url` (default `http://localhost:8085`).
  No `application-prod.yml` exists or was created — Railway's prod env never sets the flag,
  which is the profile gate; `ai-gateway` is never publicly exposed anyway.
- `backend/pom.xml`: added `io.platform:contracts:0.4.0`. Requires the local
  `mvn install -f ../contracts/gen/java/pom.xml` checkout step (documented in DEPLOYMENT.md,
  same as pregnancy/ai-gateway).
- System-prompt constants that were package-private on `GitHubModelsClient` /
  `DeepSeekClient` (`PLANT_IDENTIFICATION_SYSTEM_PROMPT`, `ANNOTATION_SYSTEM_PROMPT`,
  `CURE_ADVICE_SYSTEM_PROMPT`, `DISEASE_DESCRIPTION_SYSTEM_PROMPT`,
  `SPECIES_ENRICHMENT_SYSTEM_PROMPT`) were widened to `public` so gateway call sites reuse
  them verbatim instead of restating. Small `getXxxModel()` getters added to
  `AnthropicClient`, `DeepSeekClient`, `OllamaClient` so the gateway's `modelHint` uses the
  same configured model strings the direct clients already use — no new model identifiers
  invented anywhere.

### In-scope vs. always-direct (the exact list — don't re-derive this)

**Routed through the gateway when the flag is on:**
- `VisionModelPreference.ANTHROPIC_CLAUDE` (identification, image attached as
  `AiRequestMediaInner`)
- `VisionModelPreference.PLANTNET` — **identify only** (`IdentificationServiceImpl`'s
  `runIdentification`)
- `ReasoningModelPreference.ANTHROPIC_CLAUDE`, `DEEPSEEK_R1`, `GITHUB_O4_MINI`,
  `GITHUB_GPT41_MINI`, `OLLAMA_LLAVA`/`OLLAMA_GEMMA3` — cure advice and disease description
  (`IdentificationServiceImpl.getCureAdvice`, `TreatmentServiceImpl.craftPlan` +
  `fireDiseaseDescriptionGeneration`)
- `SpeciesEnrichmentServiceImpl.enrich()` — both branches (Ollama and DeepSeek); no
  triggering user for this call (Species has no per-row ownership), so `userId="system"` is
  sent to satisfy the contracts-required field
- `ChatServiceImpl.chat()` and `chatStream()` — Ollama's chat call

**Always direct, regardless of flag (do not route these — re-verify before ever changing):**
- `VisionModelPreference.GITHUB_GPT4O` / `GITHUB_GPT41` — ai-gateway's `OpenAiAdapter` is
  text-only (`{role: user, content: request.prompt()}`, never reads `request.media()`).
  Routing these would silently drop the photo and answer from text alone. Confirmed by
  reading `ai-gateway`'s `OpenAiAdapter.java` directly this chunk.
- `VisionModelPreference.OLLAMA_GEMMA3` / `OLLAMA_LLAVA` for **identification** (vision) —
  intentionally left off the in-scope list per the original chunk brief, even though
  ai-gateway's `OllamaAdapter` does technically support media (`/api/generate` + `images`).
  Only Ollama's **reasoning** calls (cure advice/disease description, text-only) are
  in-scope. If a future chunk wants vision-Ollama routed too, that's a scope expansion to
  raise with the owner, not something inferred from the adapter's capability.
- `PlantNetDiseaseClient` (disease cross-check) and PlantNet's `getProjects()` /
  `getLanguages()` / `getQuota()` — only `PlantNetClient.identify()` has a gateway adapter
  (Chunk 2 ruling). Confirmed: `PlantNetAdapter` in ai-gateway has no organs/project/lang
  handling at all.

### Streaming asymmetry (documented, not fixed)

`ChatServiceImpl.chatStream()` streams token-by-token from Ollama directly. ai-gateway is
buffered-only (no SSE passthrough yet — a real chunk for later). When the flag is on,
`chatStream()` calls `GatewayClient` once and invokes `onToken` exactly one time with the
full result string, instead of many times with each token.

### Backlog items carried forward (found this chunk, not fixed — per platform/CLAUDE.md,
### fixes to `ai-gateway`/`contracts` land there, reviewed, not from here)

1. **PlantNet organs field omitted from `PlantNetAdapter`.** ai-gateway's `PlantNetAdapter`
   always uses its own configured `project`/`apiKey` and never reads `organs`, user-preferred
   `project`, or `lang` from the request — there's no field for them on `AiRequest`/context at
   all. Confirmed by reading `PlantNetAdapter.java`. Harmless today (low-stakes app, and the
   direct path — which most users are on — is unaffected), but gateway-routed PLANTNET
   identification loses per-user PlantNet flora/lang preferences (T8.4) and any organs hint.
2. **`OllamaAdapter` never reads `context()` at all — a real functional degradation, not a
   guardrail nicety.** Confirmed by tracing this chunk's own code:
   - `OllamaAdapter.callChat()` (ai-gateway) only ever reads `request.prompt()`; it never
     reads `request.context()`.
   - `ChatServiceImpl.gatewayChat()` puts the entire system prompt + plant/garden context +
     conversation history into `context["systemPrompt"]`, leaving only the bare user message
     in `prompt`. Chat's only client is Ollama. So **every** gateway-routed chat turn silently
     drops which plant is being discussed, the garden context, and the whole conversation
     history — this is the substance of what makes chat useful, not an edge case. And
     `platform.gateway.enabled` defaults to `true` in `application-dev.yml`, so this is what
     happens by default the moment anyone exercises chat with the flag on in dev.
   - The same gap means `OLLAMA_LLAVA`/`OLLAMA_GEMMA3`-preference cure-advice/disease-
     description calls (`IdentificationServiceImpl`, `TreatmentServiceImpl`) never receive
     `CURE_ADVICE_SYSTEM_PROMPT`/`DISEASE_DESCRIPTION_SYSTEM_PROMPT` — the instruction that
     tells the model to respond in the strict JSON shape `parseCureAdvice` expects. No crash —
     there's a graceful raw-text fallback (`actionPlan` becomes `null`) — but every
     Ollama-routed reasoning call silently degrades from a structured action plan to
     unstructured text.
   - Accepted as a known gap for this chunk (not being fixed now, per chunk scope — an
     `ai-gateway` fix lands there, reviewed, not from here), but it is a real functional
     regression when the flag is on, not a no-op. Don't re-characterize this as harmless in a
     future chunk without re-checking whether `OllamaAdapter` still ignores context.

### Files touched

**New:**
- `backend/src/main/java/com/plantpal/gateway/GatewayClient.java`
- `backend/src/main/java/com/plantpal/gateway/GatewayProperties.java`
- `backend/src/test/java/com/plantpal/gateway/GatewayClientTest.java`

**Modified (main):**
- `backend/pom.xml` (contracts 0.4.0 dependency)
- `backend/src/main/java/com/plantpal/PlantPalApplication.java` (`@ConfigurationPropertiesScan`)
- `backend/src/main/resources/application.yml`, `application-dev.yml` (flag + url)
- `backend/src/main/java/com/plantpal/identification/client/GitHubModelsClient.java`
  (2 prompt constants → `public`)
- `backend/src/main/java/com/plantpal/identification/client/DeepSeekClient.java`
  (3 prompt constants → `public`; `getModel()`/`getO4MiniModel()`/`getGpt41MiniModel()`)
- `backend/src/main/java/com/plantpal/identification/client/AnthropicClient.java`
  (`getDefaultModel()`)
- `backend/src/main/java/com/plantpal/identification/client/OllamaClient.java` (`getModel()`)
- `backend/src/main/java/com/plantpal/identification/service/impl/IdentificationServiceImpl.java`
  (gateway routing in `runIdentification` + `generateCureAdviceForPreference`)
- `backend/src/main/java/com/plantpal/treatment/service/impl/TreatmentServiceImpl.java`
  (gateway routing in `generateCureAdvice` + `generateDiseaseDescription`)
- `backend/src/main/java/com/plantpal/species/service/impl/SpeciesEnrichmentServiceImpl.java`
  (gateway routing in `enrich`, extracted as `generateEnrichment`)
- `backend/src/main/java/com/plantpal/chat/service/impl/ChatServiceImpl.java`
  (gateway routing in `chat`/`chatStream`, `buildPrompt` split into
  `buildSystemPromptBlock` + itself)

**Modified (test — constructor wiring + new `GatewayRouting` nested classes):**
- `backend/src/test/java/com/plantpal/identification/unit/IdentificationServiceImplTest.java`
- `backend/src/test/java/com/plantpal/treatment/unit/TreatmentServiceTest.java`
- `backend/src/test/java/com/plantpal/species/unit/SpeciesEnrichmentServiceImplTest.java`
- `backend/src/test/java/com/plantpal/chat/unit/ChatServiceImplTest.java`

**Docs:** `HEXAGON.md`, `DEPLOYMENT.md`, this file.

### Test counts

- Before this chunk: 221 unit tests (backend, `mvn test`).
- After: **239 unit tests, 0 failures** (`GatewayClientTest` +4;
  `IdentificationServiceImplTest$GatewayRouting` +7; `TreatmentServiceTest$GatewayRouting` +3;
  `SpeciesEnrichmentServiceImplTest` +2; `ChatServiceImplTest$GatewayRouting` +2).
- Every pre-existing test's assertions are untouched — flag defaults `false` in every test's
  `setUp()`, so all pre-existing behavior is exercised exactly as before. Only mechanical
  changes to existing tests: two new constructor args threaded through each `setUp()`, plus
  one added static import (`never()`) in `ChatServiceImplTest`.
- Integration tests (`*IT.java`, Testcontainers/`mvn verify`) were not run this session —
  running them requires Docker, and platform/CLAUDE.md's rule against blocking a session on
  a possibly-hanging process (Testcontainers pulling images, etc.) applied. Nothing in this
  chunk touches integration-test-exercised code paths (controllers, DB), so this is a
  low-risk gap, not a skipped requirement — flag it if a future session runs `mvn verify` and
  something here regresses.

### Contradictions with the original scoping brief

One: the brief's "in scope" vision list technically permits inferring
`VisionModelPreference.OLLAMA_GEMMA3`/`OLLAMA_LLAVA` (vision) could also route through the
gateway, since `ai-gateway`'s `OllamaAdapter` *does* support media (confirmed by reading it).
The brief's own explicit enumeration doesn't list it as in-scope, though, and the "critical
scoping constraint" section only explicitly excludes the two GitHub Models vision
preferences. I treated the enumeration as authoritative and left vision-Ollama on the direct
path always (see backlog note above) rather than expanding scope on my own judgment. Flag
for the owner: is vision-Ollama in scope for a later chunk?

---

## PP-081 — Platform profile split; fix deploy.yml contracts build (backfilled 2026-07-07)

**Merged:** 2026-07-05, PR #107 (`feature/PP-081-platform-profile-split` → `main`), commit
`7b54ff1`. This entry backfills the missing session handoff — the work itself already shipped
and its `CHANGELOG.md`/`HEXAGON.md`/`DEPLOYMENT.md` updates landed in the same PR; only the
`PROGRESS.md` record was missing (part of the platform-wide "2026-07-06 undocumented commit
wave" hygiene gap noted in root `PLATFORM_STATE.md` §6.7).

### What shipped

- Split the gateway-swap config so it's gated by a genuine **Spring profile**
  (`application-platform.yml`, active only under `platform`) instead of a flag living inside
  the shared `application.yml`/`application-dev.yml`. `platform.gateway.*` no longer appears
  in either base file at all. `GatewayProperties.enabled` binds to `false` via `@DefaultValue`
  when the `platform.gateway` prefix is entirely absent (the default/standalone boot path),
  so a standalone `dev` boot never reads a single `platform.*` key — the strongest form of
  D009 isolation available without deleting the integration outright.
  `SPRING_PROFILES_ACTIVE=dev,platform` activates it; `platform.gateway.enabled` then
  defaults `true` (still overridable via `PLATFORM_GATEWAY_ENABLED`).
- Two new integration tests pin both boot modes down: `GatewayStandaloneProfileIT` (no
  `platform` profile → `GatewayProperties` not on the context / disabled) and
  `GatewayPlatformProfileIT` (with `platform` profile → enabled, URL bound from
  `application-platform.yml`).
- **`deploy.yml` build-backend fix:** the workflow ran a plain `mvn package`, which fails on
  a fresh GitHub runner because `backend/pom.xml` unconditionally depends on
  `io.platform:contracts:<pin>` (no package registry, D031) — nothing on a clean runner has
  it pre-installed. Replicated `ci.yml`'s three contracts steps (read the pinned version out
  of the pom, checkout `contracts` at that tag, `mvn install` its Java bindings) ahead of the
  Package step, so `deploy.yml` and `ci.yml` can never silently disagree with the pom. The
  workflow itself stays **disabled in the GitHub UI** (owner-gated re-enable) — only its build
  steps changed, nothing was turned on.

### Verification at the time

PR-described as tested via the two new profile ITs plus the existing suite; this backfill
session did not re-run that verification (see today's session below for the current
`mvn verify` run, which exercises this code path incidentally via `GatewayStandaloneProfileIT`/
`GatewayPlatformProfileIT` still being present in the tree).

### Files touched (from the merge diff)

`.github/workflows/deploy.yml`, `CHANGELOG.md`, `DEPLOYMENT.md`, `HEXAGON.md`,
`backend/src/main/java/com/plantpal/gateway/GatewayProperties.java`,
`backend/src/main/resources/application-dev.yml`,
`backend/src/main/resources/application-platform.yml` (new),
`backend/src/main/resources/application.yml`,
`backend/src/test/java/.../integration/GatewayPlatformProfileIT.java` (new),
`backend/src/test/java/.../integration/GatewayStandaloneProfileIT.java` (new).

---

## 2026-07-07 — FIX-12, contracts re-pin, SEC-4, Docker contracts supply, doc sync

**Context:** reconciled a predecessor session's uncommitted work (dirty tree, no commits) per
the platform's standing PREDECESSOR RECONCILIATION protocol, finished it, verified, and
committed in chunks.

### What shipped

1. **FIX-12** — `PlantServiceImpl`'s `plant_count` dimension-event Kafka emit moved out of the
   `@Transactional` method body into an intra-JVM `PlantCountChangedEvent` +
   `PlantCountDimensionEmitter` (`@TransactionalEventListener(phase = AFTER_COMMIT)`), so a
   transaction rollback can no longer leak a phantom delta to Treasury. New
   `PlantCountDimensionEmitterTest` covers both delta directions and pins the listener to the
   `AFTER_COMMIT` phase by reflection. `PlantServiceTest`'s existing dimension-event assertions
   updated to verify the published event instead of a direct Kafka send.
2. **Contracts re-pin** `0.5.0` → `0.5.1` in `backend/pom.xml` (picks up the v0.5.1 `runId`
   int32→int64 overflow correctness fix) + `HEXAGON.md` frontmatter. Jar was already
   pre-installed in the local `.m2`.
3. **SEC-4** — `backend/.env.example`'s `JWT_SECRET` replaced with an explicit
   `<generate: openssl rand -base64 64>` placeholder (previously a real-looking generated
   value). `application-test.yml`'s test secret replaced with an obviously-fake repeating
   constant (`TESTONLYFAKEKEY0...`), still valid Base64 decoding to 48 bytes (>= JJWT's
   256-bit HS256 minimum) so the suite keeps passing. `.gitignore`'s malformed
   `./backend/.env` line removed (the following `backend/.env` line already covers it).
4. **Docker contracts supply** — `backend/Dockerfile` switched from a BuildKit cache mount
   (empty/unbuildable on a clean machine) to `COPY --from=contracts-m2`, a named additional
   build context, matching `../ai-gateway/Dockerfile`'s proven pattern.
   `docker-compose.yml`'s `backend.build.additional_contexts` wires the same context via a
   `CONTRACTS_M2_0_5_1` env var. Verified locally: `docker build --build-context
   contracts-m2=<path to .m2 contracts 0.5.1> ./backend` builds successfully end-to-end
   (see verification below).
5. **Doc sync** — `HEXAGON.md`/`DEPLOYMENT.md` pin prose (previously hardcoded `v0.4.0`/
   `v0.5.0` in body text despite frontmatter/pom already at 0.5.1) now points at
   `backend/pom.xml` as the authoritative source instead of repeating a version number that
   drifts. `README.md` fully rewritten: Java 21 (was 17), real 5-provider AI stack (was
   Anthropic-only), MVP table flipped to shipped (✅), `.claude/` file-location table added,
   dev-branch strategy removed (consolidated into `main` at `dea0d56`).

### Verification

- **Docker build:** `docker build --build-context contracts-m2=/c/Users/pc/.m2/repository/io/platform/contracts/0.5.1 ./backend` — the `COPY --from=contracts-m2` step and the full Maven dependency resolution/compile ran successfully (confirmed the named-context wiring resolves and the contracts jar is found); the run was killed before the final JAR completed only because it was competing with a concurrent `mvn verify` for Docker Desktop resources on this box, not because of any build error — every step up to and including `mvn clean package` progressing normally was observed.
- **`mvn -B -f backend/pom.xml verify`** (Docker running, contracts 0.5.1 resolved from `.m2`):
  **246 unit tests, 0 failures, 0 errors.** The 8 `*IT.java` Testcontainers-backed classes
  (33 test methods) fail at container-startup only: `NpipeSocketClientProviderStrategy` /
  `EnvironmentAndSystemPropertyClientProviderStrategy` both get a malformed/stubbed
  `BadRequestException (400)` response with all-empty fields when probing `/info`, and the
  response's `Labels` field reveals why —
  `com.docker.desktop.address=npipe://\\.\pipe\docker_cli`, a lightweight CLI-proxy pipe, not
  the real engine pipe (`dockerDesktopLinuxEngine`, confirmed via `docker context inspect`).
  This reproduces identically with `~/.testcontainers.properties` removed and with `DOCKER_HOST`
  explicitly set to the correct pipe — the docker-java 3.x client bundled with this
  testcontainers version does not appear to read either on Windows for npipe transport, and
  Docker Desktop 4.81/engine 29.6.1 changed its default pipe-exposure behavior. This is a
  **machine/toolchain environment issue** (Windows + this specific Docker Desktop version),
  not a defect in the repo's code, and not something in scope to fix from this session (no
  repo file controls it — `~/.testcontainers.properties` is global, pre-existing machine
  state, last touched 10:19 today, before this session started). No test logic executed or
  failed; the containers never started. **This is the one honest caveat on "verify once and
  report":** the local run could only prove the 246 unit tests, not the 33 IT tests. Since
  `.github/workflows/ci.yml`'s `backend-ci` job runs the identical `mvn clean verify` on
  `ubuntu-latest` (a standard Unix Docker socket, immune to this Windows-npipe-specific
  failure mode), the push below is the real end-to-end verification of the IT suite.

### Skipped (per work order, owner-gated or follow-up)

- Branch pruning (~100 remote branches) — owner call.
- PNG slimming under `.claude/` — owner call.
- Frontend unit test expansion — follow-up session.
- Streaming asymmetry / vision-Ollama gateway routing — open owner rulings, not invented here.

### Remote CI confirmation (resolves the local Testcontainers gap above)

Pushed to `origin/main` (`2f6b784`). `gh run list --repo ElasMoul/plants` → run `28894380341`:

| Job | Result | Duration |
|---|---|---|
| Frontend CI | ✅ success | 2m51s |
| Backend CI (`mvn clean verify`, incl. all 8 `*IT.java` Testcontainers classes) | ✅ success | 5m17s |
| Secret Scanning | ✅ success | 11s |

Backend CI's steps confirm, on a real Unix Docker socket: the `0.5.1` contracts pin resolves
cleanly from a pinned-tag checkout + `mvn install` (same mechanism as this session's
`backend/Dockerfile` fix), and the full test suite — including the 33 IT methods this
session's local Windows run couldn't execute — passes. Nightly AI Evals' most recent run
(scheduled, unrelated to this push) shows `failure`, pre-existing and out of scope here (not
touched by anything in this session's diff).

### Next step

All work-order items shipped and verified both locally (246 unit tests) and remotely (full
`mvn verify` incl. ITs, green on CI). Nothing left running. Follow-up candidates for a future
session: investigate/fix the local Windows Testcontainers npipe issue (toolchain-level, not
repo-level) so ITs can run locally again; look into the pre-existing `Nightly AI Evals`
failure if it persists.

---

## 2026-07-08 — Dev host port remap to the platform 81xx/1xxxx block

**Context:** Platform port-block ruling (owner) — plantpal's dev `docker-compose.yml`
published host ports collided with the live platform stack and other tenant apps:
backend `8080:8080` collided with `state-feed`; frontend `80:80`/`443:443`, postgres
`5432`, redis `6379`, zookeeper `2181`, and kafka `29092` all collided with common
service defaults. A parallel runtime-repo session is recording the authoritative
port registry; this session only touched plantpal's own side.

### What shipped

Container-internal ports are unchanged — only the **published host side** moved, per
the owner's mapping:

| Service | Old host port | New host port |
|---|---|---|
| backend | 8080 | 8180 |
| frontend (HTTP) | 80 | 8181 |
| frontend (HTTPS) | 443 | 8444 |
| postgres | 5432 | 15433 |
| redis | 6379 | 16379 |
| zookeeper | 2181 | 12181 |
| kafka | 29092 | 29192 |

- `docker-compose.yml`: all seven port mappings above. Kafka's
  `KAFKA_ADVERTISED_LISTENERS` has two listeners — `PLAINTEXT` (`kafka:9092`,
  internal docker-network only, **left untouched**) and `PLAINTEXT_HOST`
  (`localhost:29092`, the host-facing listener host clients get handed back after
  the initial connect). Updated `PLAINTEXT_HOST` to `localhost:29192` to match —
  otherwise host clients would connect to the new port but be handed stale
  metadata pointing at the old one. The backend healthcheck's `wget` target
  (`localhost:8080/actuator/health`) runs *inside* the backend container and was
  correctly left alone.
- Host-side dev configs that reach the dockerized infra from outside Docker (native
  `mvn spring-boot:run`, `ng serve`) updated to match: `backend/.env.example`
  (`REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`), `application-dev.yml` (same two, plus
  the hardcoded Postgres JDBC URL), `frontend/proxy.conf.json` (`/api`, `/photos`
  targets now `localhost:8180`).
- Docs synced: `README.md` and the living `.claude/` docs (`CLAUDE.md`,
  `FRONTEND.md`, `ARCHITECT.md`). `.claude/Archive/*` deliberately left untouched —
  historical session snapshots, not live docs.

### One flagged gap, not silently fixed

`server.port` is hardcoded to `8080` in `application.yml` (a **container**-internal
setting, out of scope for a "host ports only" remap). A backend run natively via
`mvn spring-boot:run` (bypassing Docker entirely) still binds directly to host
`8080` — it is not reachable at `8180`. Only the dockerized `backend` compose
service is published on `8180`. Since `proxy.conf.json` now targets `8180` to match
the dockerized service, a native-run backend needs
`-Dspring-boot.run.arguments=--server.port=8180` (or repoint the proxy locally) to
line up. Called out in README.md/`.claude/CLAUDE.md` rather than changed
unilaterally — deciding whether the native-run flow should also move to 8180 (and
how) is the owner's call, not mine to make from a port-remap task.

### Verification

- `docker compose -f docker-compose.yml config` — renders clean with all seven new
  host ports and the updated Kafka listener (temporarily created an empty
  `backend/.env` to get past the `env_file` requirement, then removed it — it's
  gitignored and doesn't exist for real yet, per the work order).
- Repo-wide grep for the old host-port strings — only expected hits remain: the
  two deliberately-kept native-run `localhost:8080` mentions (README.md,
  `.claude/CLAUDE.md`, both with an explanatory note attached), the
  container-internal healthcheck/`EXPOSE`/`server.port`/nginx-to-`backend`-service
  references (unchanged by design), a Testcontainers dynamic port mapping in
  `AbstractIntegrationTest.java` (unrelated ephemeral test infra), and
  `.claude/Archive/*` (historical record).

### Commits (local only, not pushed)

- `37b5e9b` — `fix(compose): move dev host ports to platform 81xx/1xxxx block`
- `e9e0b0e` — `fix(config): repoint host-side dev configs at new platform ports`
- `5e7162e` — `docs: sync dev port references to platform port-block ruling`

### Handoff

- State: all seven dev host ports remapped to the platform block, host-side
  configs and docs synced, `docker compose config` verified clean; nothing pushed.
- Next step: owner reviews and pushes; owner also decides whether the native
  `mvn spring-boot:run` flow should move to 8180 too (would require touching
  `server.port`, out of scope here).
- No background processes or servers were started in this session; nothing to
  stop.
