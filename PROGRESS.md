# plantpal — Progress (platform-delta sessions)

Platform-delta work only. PlantPal's own feature work continues in `.claude/STATE.md`.

---

## 2026-07-10 — ai-gateway full-coverage demand closed: PlantPal's own follow-ups (PP-088)

**Context:** `demands/2026-07-09-ai-gateway-full-ai-coverage.md` came back FULFILLED AND
OWNER-APPROVED (`GET /satisfied/plantpal` on the demand-coordinator, contracts v0.9.1 +
ai-gateway both reported `done`). This session worked the demand's own §5 follow-ups — the ones
now unblocked — on `feature/PP-088-gateway-full-routing` (based on `main`; see branch-base note
below), and archived the demand.

**G5 — model manifest.** Committed `ai-model-manifest.yaml` at repo root (sibling to
`app-manifest.yaml`), matching contracts' final `schemas/ai-gateway/model-manifest.json` shape
(camelCase `downshiftPolicy`/`streamingDesired`, not the demand draft's bare `downshift`/
`streaming` markers). Declarative only for now — ai-gateway's fulfillment report flagged that its
deployed instance doesn't mount `AI_MANIFEST_DIR` yet (needs a `runtime` compose volume mount,
out of scope for either side), and its own contracts pin stays at v0.7.0 (two confirmed v0.9.0
defects — a broken `gen/java/pom.xml` XML comment, and the `capabilities` map-typed field not
codegen'ing — both flagged back to `contracts`, neither fixed from either app repo per the
standing rule).

**G1 follow-up — GitHub-Models vision through the gateway.** Extended the existing D022 gateway
swap's additive if/else pattern (the one already used for `ANTHROPIC_CLAUDE`/`PLANTNET`
identification and every `ReasoningModelPreference`) to the two remaining vision preferences:
`GITHUB_GPT4O`/`GITHUB_GPT41` identification, and the always-on gpt-4o-mini annotation call (new
`runAnnotation()` helper in `IdentificationServiceImpl`, replacing both direct
`visionAnnotationClient.analyzeRegions()` call sites). `GitHubModelsClient` gained
`getIdentificationModel()`/`getGpt41Model()`/`getAnnotationModel()` getters for the gateway's
`modelHint`. The direct `GitHubModelsClient`/`DeepSeekAnnotationClient` path is unchanged and
still serves standalone/dev (D009) — same "retires from the hot path, survives as seed code"
shape the demand's §5 asked for, matching how Anthropic/PlantNet were handled in Chunk 3.

**G4 follow-up — PlantNet auxiliary calls through the gateway.** New
`com.plantpal.gateway.PlantNetGatewayClient` (mirrors `GatewayClient`'s RestClient pattern,
targets ai-gateway's `/ai/plantnet/{projects,languages,quota,disease-check}`, unwraps
ai-gateway's own `ApiResponse<String>` envelope and parses the inner raw-PlantNet-JSON string onto
PlantPal's existing `PlantNetProjectDto`/`PlantNetQuotaDto`/`PlantNetDiseaseResponse` DTOs — same
types the direct path already used, so callers see an identical return type either way).
`PlantNetConfigController` and the disease cross-check in `IdentificationServiceImpl` now branch
on `gatewayProperties.enabled()` exactly like every other gateway call site.

**G3 — no code change.** Re-checked the existing gateway context-assertion coverage
(`IdentificationServiceImplTest$GatewayRouting`'s `systemPrompt`/context assertions) — still
green, confirming the demand's own note that G3 needed no PlantPal-side follow-up.

**Explicitly not touched (per the demand's §5 and this session's brief):** chat streaming stays
direct-to-Ollama (G2 unshipped, needs-owner); Ollama vision routing stays as-is (owner ruling
still open).

**Branch-base note for the architect:** `dev` turned out to be a strict ancestor of `main` (98
commits behind, zero divergent commits) — it predates the entire D022 gateway swap this session
builds on (no `GatewayClient`/`GatewayProperties` on `dev` at all). Branched off `main` instead
since basing off `dev` would have silently reverted the gateway infrastructure; flagging so `dev`
can be fast-forwarded.

**Verification:** `mvn clean test` inside `maven:3.9-eclipse-temurin-21` (Docker, host `.m2`
mounted for the pinned `contracts:0.7.0` jar — no local JDK/Maven on this machine): **289/289
unit tests, 0 failures, BUILD SUCCESS.** Integration/Testcontainers tests not run (environment
can't support them here; the demand-touched code is all unit-covered). Compose stack was not
started (left stopped, per instructions).

Demand archived: `demands/2026-07-09-ai-gateway-full-ai-coverage.md` →
`demands/archive/2026-07-09-ai-gateway-full-ai-coverage.md`, `status: archived`.

**Handoff:** feature branch `feature/PP-088-gateway-full-routing` pushed (never `main`/`dev`) for
architect review/merge. Next step: architect review + merge; then fast-forward `dev` to `main`
(see branch-base note); then, once `runtime`'s `AI_MANIFEST_DIR` volume mount and contracts
v0.9.0's two defects are fixed upstream, swap `ai-model-manifest.yaml` from declarative-only to
actually consumed. No background processes/servers were started this session; nothing to stop.

---

## 2026-07-09 — Chunk E (self-declared business tier) + a second CI break, root-caused and closed (PP-086/PP-087)

**Context:** owner approved chunk E of the integration plan (tier self-declaration UI, D027) after
a doc reread surfaced 3 small drift items (demand-coordinator frontmatter, HEXAGON.md stale pin
prose, STATE.md branch counter) — folded into the same branch. Separately, the owner's post-merge
smoke test of wave 2 surfaced a *second*, unrelated CI break, root-caused and fixed same session.

### Chunk E — [PR #112](https://github.com/ElasMoul/plants/pull/112), merged

Self-declared business/professional tier (D027: honest signal, no client enforcement — enforcement
is a future Treasury concern). Migration 031 (`users.is_business_tier`), `UserPreferencesRequest`/
`Response` field following the existing null-check-to-preserve pattern, a preferences-page toggle
(auto-saves, matching `ModelSelectorComponent`'s pattern), and a dismissible Home-dashboard upgrade
prompt at `!businessTier && totalPlants >= 10` — no new endpoint, reuses the existing
`DashboardResponse.healthSummary.totalPlants` + preferences call. Dismissal persists in
localStorage. **Explicitly out of scope:** the `platform` Spring profile flip — `GET
/satisfied/plantpal` on the live `demand-coordinator` (localhost:8082) was still empty, meaning
ai-gateway hadn't closed the G1–G6 gaps from the earlier demand doc.
Verified independently by the coordinating session (not just the builder agent's own report):
backend 271/271 + `spotless:check` clean fully offline, frontend tsc/build/full-jest 41/41 across
7 suites. Bundled doc fixes: demand envelope frontmatter added (was showing "unstructured" on the
coordinator's board), 3 stale `v0.5.1`/`0.5.1` contracts-pin mentions in `HEXAGON.md` corrected to
`v0.7.0`, `STATE.md`'s branch/migration counters resynced.

### PP-087 — a second, unrelated CI break found and fixed — [PR #113](https://github.com/ElasMoul/plants/pull/113), merged

PP-086's PR showed green backend / red frontend CI on code that never touched `package.json`.
Root cause: **four Dependabot PRs had auto-merged to `main` before this session even started**
(2026-07-04 through 2026-07-09), each bumping *one half* of a version-matched package pair to a
major line the rest of the Angular-16/TS-ESLint-5/Jest-29 stack doesn't support, without checking
the paired package moved too:

| Package | Was | Bumped to | Broke because |
|---|---|---|---|
| `@angular-devkit/build-angular` | 16.2.0 | 22.0.5 | peer-requires `@angular/compiler-cli ^22`; project has `^16` |
| `@angular/service-worker` | 16.2.0 | 22.0.5 | same family mismatch |
| `@angular-eslint/template-parser` | 16.3.1 | 22.0.0 | same |
| `@typescript-eslint/eslint-plugin` | 5.62.0 | 8.62.1 | peer-requires `parser ^8.63`; project has `^5.62` |
| `jest-environment-jsdom` | 29.7.0 | 30.4.1 | major ahead of `jest ^29.7.0` |

`npm install` tolerates this (permissive peer resolution) — why it went unnoticed locally across
several sessions, including this one's earlier chunks. `npm ci` (what CI actually runs) does not.
**Fix:** reverted all five to their pre-bump versions, regenerated `package-lock.json`, verified
`npm ci` reproduces and clears the exact CI failure locally before pushing. Full gate re-run
green (tsc/lint/build/jest 6/6 suites, 35/35 tests). CI on the PR itself confirmed both jobs green
end-to-end, including the backend integration-test suite.

**Cleanup (owner-approved):** the five *original*, still-open, never-merged Dependabot PRs
proposing these exact bumps (#99, #100, #101, #102, #103 — distinct from whichever four auto-merged
earlier and caused the original break) were closed with a comment pointing at #113, so they can't
be accidentally merged later and reintroduce the same failure.

**Not fixed, flagged for the owner:** *why* four Dependabot PRs auto-merged without a peer-dependency
check in the first place is a `.github/dependabot.yml` / branch-protection gap, not a one-off —
four-for-four says systemic. Worth a config review (e.g. grouping the Angular-family packages so
they bump together, or requiring a green CI check before auto-merge) before this recurs.

### Handoff
- **State:** both PRs merged, `main` verified clean post-merge (`frontend/package.json` confirmed
  to hold all five correct versions; a fresh `main`-branch CI run is green). The 5 duplicate
  Dependabot PRs are closed. Working tree clean, nothing running locally.
- **Next step:** owner review of the Dependabot config gap (not actioned this session — a repo
  policy decision, not something to change unprompted). Otherwise: chunk D (runtime/ci-runner,
  deferred), the state-feed port confirmation, and the two block-state parity flags from wave 2
  remain the open items carried forward from earlier entries in this file.

---

## 2026-07-09 — CI red after wave-2 merge: SecretConfigValidator BFPP + spotless (PP-085)

**Context:** owner merged the wave-2 PRs (#108–#110); CI went red on `main` (30/33 ITs
errored, dev container crash-looped). Root-caused and fixed on
`bugfix/PP-085-secret-validator-bfpp-instantiation` → [PR #111](https://github.com/ElasMoul/plants/pull/111).

### Root cause (two stacked, both CI-only gates)

1. **`SecretConfigValidator` (a `BeanFactoryPostProcessor`) took `Environment` by
   constructor.** BFPPs are instantiated in `invokeBeanFactoryPostProcessors`, *before*
   `AutowiredAnnotationBeanPostProcessor` is active → constructor autowiring unavailable →
   Spring falls back to a missing no-arg ctor → `"No default constructor found"` → context
   refresh fails on **every** profile (the 30 IT errors AND the dev-container crash). Added
   2026-07-08 but **never pushed (no CI) nor IT-run locally (Windows npipe)**, so it first
   broke when this wave carried it to `main`. Its unit test only did
   `new SecretConfigValidator(mockEnv)` — never Spring's real BFPP lifecycle.
   **Fix:** `EnvironmentAware` + setter (`ApplicationContextAwareProcessor` is registered in
   `prepareBeanFactory`, earlier than BFPP instantiation, so the setter path works). Added a
   `SpringLifecycle` regression test that boots a real `AnnotationConfigApplicationContext` —
   throws pre-fix, passes post-fix.
2. **Spotless (Google Java Format) gate.** `mvn verify` runs `spotless:check`; `mvn test`
   (what the wave-2 builder agents ran) does not — so unformatted state-feed code +
   the PP-085 fix blocked the build before ITs ran. **Fix:** `mvn spotless:apply` on the 5
   files, no logic change.

### Verification — GREEN on CI (run 28990603046)

- Backend CI 5m39s: **BUILD SUCCESS**, all IT classes ran and passed
  (SpeciesControllerIT 4, AuthControllerIT 4 [was erroring], TreatmentControllerIT 7,
  TreatmentPlanControllerIT 5, IdentificationControllerIT 5, GatewayStandaloneProfileIT 1,
  GatewayPlatformProfileIT 1, PlantControllerIT 3; IdentificationEvalIT 3 skipped by design).
  Frontend CI green. Unit suite 267 locally.

### Standing lesson (applies to all future delta sessions)

**Gate on `mvn verify`, not `mvn test`.** `test` skips BOTH the Testcontainers `*IT.java`
suite and `spotless:check` — the wave-2 agents' "264 green" was true but blind to both gates.
The Windows npipe blocker means ITs can't run locally; a builder agent that can't run `verify`
must say so explicitly and treat the branch as unverified until CI (or a Linux box) runs it —
not report green off `mvn test` alone. When practical, run at least `mvn -o spotless:check`
locally (it needs no Docker) before pushing.

### Handoff

- **Resolved:** [PR #111](https://github.com/ElasMoul/plants/pull/111) merged same day — `main` unblocked (CI + dev boot green again).
- No background processes running.

---

## 2026-07-09 — Integration wave 2: demand doc, contracts 0.7.0, state-feed emitter, block-state UI (+ landing page)

**Context:** owner-approved integration plan (chunks A/B/C-proposal executed this session;
chunk D — runtime/ci-runner wiring — explicitly deferred until those repos are fixed and
authenticated; chunk E — go-commercial tier UI + `platform` profile flip — held for the owner).
Orchestrated: 2 scout agents, 3 builder agents, coordinator-reviewed.

### Branches produced (none pushed, no PRs — owner reviews)

1. **`feature/PP-082-landing-page`** (PlantPal feature work, not platform-delta — recorded here
   only because it shipped in the same session; `ddf9aa1` + `c04f784`). Public marketing page at
   `/`: anonymous visitors get hero/how-it-works/features/CTA; logged-in users still redirect to
   `/home`. Verified: tsc/lint/prod build green, 4/4 Jest, live-render check (a11y tree +
   computed styles; desktop + 375px). Known launch gaps flagged: no SSR (SEO), PWA manifest
   icons still placehold.co placeholders, no real logo asset.

2. **`feature/PP-083-contracts-070-statefeed`** (this branch):
   - `2b54ef7` — **demand doc** `demands/2026-07-09-ai-gateway-full-ai-coverage.md` for the
     platform architect → ai-gateway/contracts. Inventories PlantPal's full AI surface; gaps
     G1–G6 (OpenAI-adapter media support, streaming *gateway sessions* — owner's token+refresh
     sketch with the D022 correction that sessions terminate at the gateway, Ollama context
     loss, PlantNet aux adapters, `ai.model-manifest` contract proposal incl. draft manifest,
     hardening). Fixes land in those repos, reviewed — not from here.
   - `767272c` — **contracts re-pin 0.5.1 → 0.7.0.** All intervening releases additive;
     `DimensionEvent` unchanged; no code adaptation needed. Docker named-context var renamed
     `CONTRACTS_M2_0_5_1` → `CONTRACTS_M2_0_7_0` (compose + DEPLOYMENT.md); CI workflows read
     the pin dynamically off the pom (verified, no change). Manifest/HEXAGON alignment vs
     v0.7.0 schemas checked: already exact, no-op. NOTE: root `PLATFORM_STATE.md` still says
     plantpal pins 0.5.0 — stale twice over now.
   - `e999cfe` — **state-feed emitter** (`com.plantpal.statefeed`): `app.status` on
     `ApplicationReadyEvent` + `activity.count` per completed identification (listens to the
     existing `IdentificationCompletedEvent`, now also published as a Spring event — no new
     cross-package injection). Fire-and-forget: async on `aiTaskExecutor`, 2s/5s timeouts, all
     failures WARN-and-swallow. Config `platform.statefeed.*` in `application-platform.yml`
     only (D009-safe `@DefaultValue enabled=false`). **Open item:** default URL
     `http://localhost:8080` is *inferred* from the 2026-07-08 port-collision note, not a
     state-feed-authored source — confirm against the runtime session's port registry.
   - Unit suite: 256 → **264 green** (`mvn test`; ITs not runnable locally, known npipe issue —
     CI covers them on push).

3. **`feature/PP-084-ai-block-state-ui`** (`ba5f7c4`, built in an isolated worktree, adopted +
   worktree removed): **AI block-state, end to end (D023 / spec delta item 2 — now shipped).**
   Trace findings: chat (sync + stream) and craft-plan already carried the gateway 402 with
   reason; **async identification flattened it into generic `PROVIDER_ERROR`** — fixed by
   tagging `failureReason = "AI_LIMIT_REACHED"` on 402 (reused existing free-text column, no
   migration). New shared `AiBlockNoticeComponent` (compact chat-inline + full identification
   terminal state), `AiErrorService.blockReason()` + raw-string body parsing (fixes the
   stream's `responseType:'text'` case). Backend 257 green (+1), frontend 19 Jest green (+8),
   tsc/lint/prod build clean. **Flagged, deliberately not done:** disease-description async
   generation still collapses 402 → generic `FAILED` (has a "regenerate" terminal state, so no
   spinner violation; parity would need a `GenerationStatus` variant — owner call);
   identification persists only the tag, not the gateway's reason prose (static friendly
   message shown — owner call if exact prose wanted there).

### Merge note
PP-083 and PP-084 both touch `IdentificationServiceImpl` (different methods:
`publishCompletedEvent` vs `classifyFailureReason`) — expect a trivial/clean merge, but merge
PP-083 first, then PP-084, and re-run `mvn test` after the second merge.

### Handoff
- **Resolved:** all three branches merged ([PR #108](https://github.com/ElasMoul/plants/pull/108) landing,
  [PR #109](https://github.com/ElasMoul/plants/pull/109) contracts/state-feed,
  [PR #110](https://github.com/ElasMoul/plants/pull/110) block-state UI) — see the PP-085 entry above for
  the CI breakage the merge surfaced and its fix. Demand doc routed to the ai-gateway session
  (owner confirmed it's being worked). Chunk E shipped in the next session (see top entry).
- **Still open:** state-feed URL port-registry confirmation; the two block-state parity flags
  (disease-description async 402 → generic FAILED; identification not persisting the gateway's
  reason prose); chunk D (runtime/ci-runner) intentionally deferred until those repos are fixed
  and authenticated.
- No background processes left running (preview server from the landing-page check was stopped
  and verified dead; agent worktree removed; `.claude/launch.json` left untracked as a dev
  convenience — owner may commit or delete).

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

### CORRECTION (same day, 2026-07-08) — live regression found and fixed

**Regression, verified live by the coordinator:** dockerized backend health DOWN,
`RedisConnectionException: Unable to connect to redis/<unresolved>:16379 ...
Connection refused: redis/172.19.0.3:16379`. Downstream: healthcheck failed, so
`frontend` (`depends_on: service_healthy`) never started.

**What was wrong:** this session's remap changed `backend/.env.example`
`REDIS_PORT` `6379 → 16379`. That value is correct for **native host runs** (the
host-published port), but `docker-compose.yml` feeds the *same* `.env` into the
backend container via `env_file:`, and inside the compose network Redis still
listens on container port `6379`. The compose `environment:` block overrode the
Redis **host** (`SPRING_DATA_REDIS_HOST: redis`) but not the **port**, so
`application-dev.yml`'s `${REDIS_PORT:...}` placeholder resolved to `16379` inside
the container → `redis:16379` → refused.

**Fix (commit: `fix(compose): override container-internal Redis port for dockerized backend`):**
- `docker-compose.yml` backend `environment:` — added `SPRING_DATA_REDIS_PORT: 6379`
  (mirrors the existing `SPRING_DATA_REDIS_HOST` pattern; a Spring OS-env var
  outranks the yml placeholder, same mechanism that already made the host override
  work). Comment expanded to state the rule: every port that `.env` remaps to a
  host-published value needs an explicit container-internal override here, because
  `environment:` beats `env_file:`.
- `backend/.env.example` — comment added near the remapped values: host-side ports
  for NATIVE runs; the dockerized backend gets container-internal overrides from
  docker-compose.yml.

**Audit of the other remapped `.env`-fed values (same bug class):**
- **Kafka** — NOT luck: compose `environment:` explicitly sets
  `KAFKA_BOOTSTRAP_SERVERS: kafka:9092`, which replaces the env_file's
  `localhost:29192` outright (`environment:` beats `env_file:` at the compose
  level — confirmed in the rendered `docker compose config`, where the merged
  environment shows `kafka:9092`). That's why consumers joined fine tonight.
- **Postgres** — explicitly overridden (`SPRING_DATASOURCE_URL:
  jdbc:postgresql://postgres:5432/...`), and `.env.example` has no DB host/port
  variable anyway (only `DB_USERNAME`/`DB_PASSWORD`, unremapped). Safe.
- **Zookeeper** — no `.env`-fed value exists; all refs are compose-internal
  (`zookeeper:2181`). N/A.
- Redis **port** was the single value with no explicit container-side override —
  the one gap, now closed.

**Verification:** `docker compose -f docker-compose.yml config` (with
`CONTRACTS_M2_0_5_1` set) renders clean; merged backend environment shows
`SPRING_DATA_REDIS_PORT: "6379"` alongside `SPRING_DATA_REDIS_HOST: redis` and
`KAFKA_BOOTSTRAP_SERVERS: kafka:9092`. Nothing was `up`'d — the stack is being
managed live from the root session.

### Follow-up work order (same day) — fail-fast startup validation for secret env vars

**Two more live incidents tonight, same defect class** (placeholder env values pass
boot silently, explode later with cryptic errors):

1. **VAPID keys left as `.env.example` placeholders** → `WebPushServiceImpl`'s
   constructor threw `IllegalArgumentException: Invalid point encoding 0x-36` →
   whole app crash-looped with no hint which env var was wrong.
2. **`JWT_SECRET` left as the SEC-4 placeholder** (`<generate: openssl rand -base64
   64>`) → app booted FINE, then every login/registration 500'd with
   `io.jsonwebtoken.io.DecodingException: Illegal base64 character: ':'` — runtime
   failure, zero indication it's config (`JwtUtil` only decodes at signing time).

**SEC-4 context:** that placeholder was introduced *deliberately* (2026-07-07
session) when the committed real-looking secret was burned — the right call. The
gap was that nothing *enforced* replacing it: the placeholder was designed to be
unusable but nothing made it fail fast and loud.

**Fix — `SecretConfigValidator`** (`backend/src/main/java/com/plantpal/shared/config/`):

- Pattern: `BeanFactoryPostProcessor`. The codebase had no prior startup-validation
  pattern to follow (config is `@Value`/`@Configuration` classes; one
  `@ConfigurationProperties` in gateway), so the deciding constraint was ordering:
  an `ApplicationRunner` runs *after* context refresh (WebPushServiceImpl's
  constructor would still crash-loop first) and `InitializingBean` has no ordering
  guarantee. BFPPs run before ANY regular bean is instantiated, so the aggregated
  named-variable error always wins over the cryptic BouncyCastle one.
- Rules: `JWT_SECRET` — no placeholder markers (`<`, `>`, `:`), standard Base64,
  >= 32 decoded bytes. `VAPID_PUBLIC_KEY` — base64url, exactly 65 bytes starting
  0x04. `VAPID_PRIVATE_KEY` — base64url, exactly 32 bytes; the length check is
  deliberate because the `.env.example` placeholder
  (`your_vapid_private_key_here`) is *accidentally valid base64url* — a
  decode-only check would wave it through.
- Every failure names the exact env var + fix command (`openssl rand -base64 64` /
  `npx web-push generate-vapid-keys`); all failures aggregate into ONE
  single-screen `ApplicationContextException`, and the app refuses to start.
- Unset env vars (unresolvable `${...}` placeholder) get the same named treatment
  instead of Spring's generic placeholder error.

**Tests:** `SecretConfigValidatorTest` (same-package `shared/config`, mirroring
`PromptSanitizerTest`'s convention; `MockEnvironment`, no Spring context): valid
values pass; placeholder JWT names the var; bad VAPID point names the var; missing
var named; multiple failures aggregate (asserts all three var names in one
message). **10/10 green; full unit suite 256/256 green (was 246).** Integration
tests not runnable locally (known Windows Testcontainers npipe blocker, see
2026-07-07 entry) — they boot the full context with `application-test.yml`'s
valid test-only values, so CI's IT run will exercise the validator's pass path.

**Heads-up for the live stack — CORRECTED (coordinator, same night):** an earlier
version of this note claimed the live `backend/.env` still carried the SEC-4 JWT
placeholder and would refuse to start. That was already false when written: the
root session had injected a real `JWT_SECRET` (and real VAPID keys) into
`backend/.env` earlier on 2026-07-08 (machine-local, never committed), and the
dockerized backend is running healthy. The validator's value is guarding FUTURE
machines/migrations — which is exactly how these placeholders got in in the first
place: `.env` was rebuilt from `.env.example` after the last machine migration.

### Cleanup (same night) — untrack the per-machine nginx dev cert

`frontend/nginx/certs/self.crt` was TRACKED (committed from the old machine) while
`self.key` was gitignored — which is why the tree showed `M self.crt` after the
root session regenerated the pair tonight. Certs are per-machine dev artifacts:
`.gitignore` now covers the whole `frontend/nginx/certs/` directory, `self.crt`
was `git rm --cached`-ed (file kept on disk — it is the live one nginx uses), and
DEPLOYMENT.md documents generation for the docker path (frontend Dockerfile COPYs
the dir, so the pair must exist before building):
`openssl req -x509 -newkey rsa:2048 -keyout self.key -out self.crt -days 365
-nodes -subj "/CN=localhost"`. The historically committed `self.crt` is just a
public self-signed cert for localhost — no secret exposure, no history rewrite
needed.

### Handoff

- State: all seven dev host ports remapped to the platform block, host-side
  configs and docs synced; live Redis-port regression corrected with an explicit
  container-side override (the fix(compose) correction commit); fail-fast secret
  validation shipped (`SecretConfigValidator`, 256 unit tests green); per-machine
  nginx dev cert untracked + gitignored, generation documented in DEPLOYMENT.md;
  `docker compose config` verified clean; working tree clean; nothing pushed.
- Next step: owner reviews and pushes (CI will run the ITs the local machine
  can't); owner decides whether the native `mvn spring-boot:run` flow should move
  to 8180 (out of scope here). Live `backend/.env` already has real secrets
  (root session, 2026-07-08) — no action needed there.
- No background processes or servers were started in this session; nothing to
  stop.

## 2026-07-14 — V1-closure verification pass (no code changes needed)

**Context:** launched with a V1-closure work order covering five items. Checked
the demand-coordinator first: `GET /inbox/plantpal` and `GET /satisfied/plantpal`
both empty — no demand traffic pending either direction.

### Findings — three of five items were already shipped in earlier sessions

1. **FIX-12 (Kafka `dimension.event` emitted inside `@Transactional`)** — already
   fixed 2026-07-07. `PlantServiceImpl.createPlant()`/`archivePlant()` only publish
   an intra-JVM `PlantCountChangedEvent`; `PlantCountDimensionEmitter`
   (`@TransactionalEventListener(phase = AFTER_COMMIT)`) is the sole Kafka sender,
   confirmed by reading both classes directly. `PlantCountDimensionEmitterTest`
   still pins the `AFTER_COMMIT` phase by reflection. Re-verified: still correct,
   no regression.
2. **Contracts re-pin 0.5.0 → 0.7.0** — already done via PR #109
   (`feature/PP-083-contracts-070-statefeed`, merged to `main` at `3bba266`).
   `backend/pom.xml` and `HEXAGON.md` frontmatter both read `0.7.0`; jar present in
   `.m2`. Re-verified by re-running the full unit suite against the current pin
   (below).
3. **README refresh** — already done 2026-07-07 (Java 21, real 5-provider AI
   stack, dead branch strategy removed). Read it fully today: still accurate,
   nothing stale found.

### Re-verification done this session

- **`mvn -B test` (excluding `*IT.java`)** against contracts `0.7.0`: **289 unit
  tests, 0 failures, 0 errors** (summed from `target/surefire-reports/*.txt`;
  exit code 0). This is a higher count than the 246 recorded 2026-07-07 — the
  intervening PP-084/086/088 waves added tests, consistent with git log.
- **Item 5, integration tests** — confirmed still box-blocked: `docker context
  inspect` shows the same `npipe:////./pipe/dockerDesktopLinuxEngine` path, and
  `~/.testcontainers.properties` still pins
  `NpipeSocketClientProviderStrategy`. Did not attempt an `*IT.java` run (known
  hang risk on this box, already root-caused 2026-07-07) — logging under
  ownerItems per the work order rather than fighting it again.
- **Item 5, native port move (→ 8180)** — **owner-delegated recommended ruling
  2026-07-14: PARK, not trivial.** Traced the full blast radius before ruling:
  `server.port: 8080` in `application.yml` is also the **container-internal**
  port the dockerized `backend` service binds to (`docker-compose.yml` maps
  `8180:8080`, and the healthcheck's `wget http://localhost:8080/...` runs
  inside that container). Moving it to `8180` would require simultaneously
  changing the compose port mapping, the healthcheck target, and
  `backend/Dockerfile`'s `EXPOSE 8080` — and touches Railway's prod binding
  assumption (`server.port` is a hardcoded literal, not `${PORT:8080}`; unclear
  whether Railway's own PORT injection currently overrides it, and that's
  outside this session's scope to investigate). That's a coordinated
  multi-file config change with a live-service blast radius, not the "trivial
  config" branch of the delegation — so it stays parked exactly as the
  2026-07-08 session left it (native `mvn spring-boot:run` continues needing
  `--server.port=8180` or a proxy repoint, both already documented in
  README.md/`.claude/CLAUDE.md`).
- **Item 4, contribution flow** — confirmed PR-based (`.github/pull_request_template.md`
  present, git log shows merges via GitHub PR, e.g. `3bba266`, `b1160a1`). No code
  changed this session, so no PR was needed — nothing to submit.

### Handoff

- State: all three code/doc closure items (FIX-12, contracts 0.7.0, README) were
  already shipped by prior sessions; re-verified green today (289/289 unit
  tests, contracts 0.7.0 resolved from `.m2`). Working tree clean, nothing to
  commit or push.
- Next step: none required for V1 closure from this repo's side. If the owner
  later confirms Railway's prod PORT-binding behavior, the native-port question
  can be revisited with that missing fact filled in.
- Standing: integration tests remain box-blocked on this Windows machine
  (Testcontainers npipe, root-caused 2026-07-07, unchanged today) — CI's Linux
  runners are the only current source of IT coverage; do not keep re-attempting
  locally.
- Vault-sync: none (no vault-relevant decision changed; the port question was
  parked, not ruled, so nothing new for `platform-vault` to record).
- No background processes or servers were started in this session; nothing to
  stop.
