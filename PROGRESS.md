# plantpal — Progress (platform-delta sessions)

Platform-delta work only. PlantPal's own feature work continues in `.claude/STATE.md`.

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
