# PlantPal — Task Plan

**Legend:**
- 👤 **Manual** — you do this yourself (external tools, config, real decisions)
- 🤖 **AI** — Claude Code generates the code entirely from the prompt provided
- 🤝 **Assisted** — you lead, Claude Code helps with specific parts
- 💡 **Suggestion** — architectural note worth understanding, not just following

**Branch format:** `feature/PP-{num}-{short-description}`
**Commit format:** `feat(scope): description` (Conventional Commits)

> Completed phases are kept as one-line-per-task status records — the original
> Claude Code prompts aren't needed once shipped (see STATE.md for what was
> actually built, ARCHITECT.md for durable patterns, git history for the rest).
> **Active phases carrying full prompt detail: Phase 8 (PlantNet), Phase 9
> (Quality & Hardening), Phase 10 (Launch).** Recommended execution order:
> **Phase 9 foundation (T9.1–T9.3, T9.5) first or interleaved with Phase 8**, so
> PlantNet's new UI is covered as it's built rather than retroactively; then the
> rest of 8 and 9; **Phase 10 (Launch) last.** 👤 The 8-vs-9 interleave is your call.

---

## ✅ COMPLETED PHASES (summary — see STATE.md / git history for detail)

### PHASE 0 — Project Setup ✅
GitHub repo, local infra (Docker Compose: Postgres 15 + Redis 7), Spring Boot +
Angular skeletons, GitHub Actions CI/CD, VAPID keys + secrets. (T0.1–T0.6)

### PHASE 1 — Auth + Plant Management ✅
Liquibase migrations, User module, Spring Security 6 + JWT, Plant CRUD (backend
+ Angular), unit + integration tests, Auth frontend. (T1.1–T1.8)

### PHASE 2 — AI Plant Identification ✅
Photo → AI identification pipeline, now fully async (Kafka, `POST /analyze` →
202 → poll). gpt-4o vision + DeepSeek-R1 text, polygon disease annotation +
cure-advice, Redis photo storage (SHA-256 dedup), image-dimension locking,
garden health dashboard. (T2.1–T2.11 incl. T2.A–T2.F)

### PHASE 3 — Reminders + Push Notifications ✅ (except T3.3)
Reminder CRUD + scheduler (one daily push per user), web-push (VAPID),
actionable care plans (ROUTINE reminders / multi-step TREATMENT plans with
Mermaid diagrams). (T3.1–T3.5)
- **T3.3 — Manual on-device testing 👤 🔲 NOT DONE** — push delivery, PWA
  installability, offline reading; needs a real phone. (Now folded into Phase 9 /
  Phase 10 beta — see T9.2 PWA journey + T10 beta.)

### PHASE 4 — AI Chat Assistant ✅
Chat backend + Angular frontend, single-turn with plant-context injection
(`?plantId=`), SSE streaming + conversation history (shipped in the pre-Phase-5
cleanup pass). (T4.1–T4.3)

### Pre-Phase-5 cleanup pass ✅
`feature/PP-038-pre-phase5-cleanup` — closed every open gap in
BACKEND.md/FRONTEND.md/STATE.md from Phases 0–4/6 plus 3 live-test bugs (reminder
double-completion, duplicate treatment creation, species AI-preference enrichment
+ structured care cards, chat history + SSE streaming, dead-component removal,
missing controller ITs, real JaCoCo gate at 55%).

### PHASE 6 — Species & Treatment Domain Restructure ✅ (2026-06-20)
Plant-centric → species-centric: shared `Species` entity, per-disease `Treatment`
entity (wraps `TreatmentPlan`), 5-item bottom nav + Home screen, species-first
Garden, redesigned Plant/Treatment pages (sticky header + icon bar), 3-path
identification flow. Durable domain model + lifecycle in ARCHITECT.md. (T6.1–T6.14)

### PHASE 7 — AI Model Control, Batch Scanning, Multi-Treatment UX ✅ (2026-06-22)
- **T7.1** 🤖 — split `AiModelPreference` → `VisionModelPreference` /
  `ReasoningModelPreference`; removed silent model fallbacks; fixed
  `GlobalExceptionHandler` hardcoded-500; `RateLimitException` + `retryAfterSeconds`;
  model-usage tracking fields. (`feature/PP-039`)
- **T7.2** 🤝 — two-dropdown model picker, `AiErrorService` (429 → actionable
  snackbar), "powered by {model}" badges, `/preferences` page. (`feature/PP-040`)
- **T7.3** 🤝 — multi-select batch scan (N independent `/analyze` calls,
  `BatchScanService`, per-item status rows). (`feature/PP-041`)
- **T7.4** 🤝 — multi-treatment picker sheet + fixed disease-description never-shows
  poll bug. (`feature/PP-041`)
  Full detail in STATE.md; durable AI-client + model-preference patterns in ARCHITECT.md.

---

## PHASE 8 — PlantNet as a First-Class Identification Provider  ✅ COMPLETE
> Goal: stop treating PlantNet as a degraded, species-only vision option
> (today: single guess, `healthStatus: UNKNOWN`, no care plan, no organ tags,
> `project=all`, no reference images, quota invisible) and turn it into the
> app's botanical ground-truth layer using the v2 surface we weren't touching:
> a **ranked candidate list with confidence scores + reference images**,
> **organ-tagged multi-image accuracy**, **geolocation-ranked floras**, a
> **dedicated disease/pest classifier**, **GBIF/POWO/IUCN linkage** carried on
> every result, and **daily-quota telemetry**.
>
> **API contract — CONFIRMED against the real swagger (My Pl@ntNet API 2.2.2):**
> - Base `https://my-api.plantnet.org`. **api-key is a query param.** Max POST
>   **50 MB**. Statuses: **404** = non-plant reject, **413**/**415** payload/mime,
>   **429** Too Many Requests (real rate-limit status).
> - `POST /v2/identify/{project}` — multipart, `images`+`organs` 1–5×, **always
>   `type=kt`** (legacy deprecated). Response: `bestMatch`, ranked `results[]`,
>   `switchToProject` (flora hint), `predictedOrgans[]`, `version`,
>   `remainingIdentificationRequests`. Each `Result`: `score`, `species{…}`,
>   `images[]` (reference, with author/license/citation), `gbif{id}`, `powo{id}`,
>   `iucn{id,category}`.
> - `GET /v2/projects?lat&lon&type=kt&lang` (location-ranked floras),
>   `GET /v2/languages`, `GET /v2/diseases` (EPPO codes),
>   `POST /v2/diseases/identify` (multi-image, cultivated-plant; results carry
>   `{name, description, score, images[]}`), `GET /v2/quota/daily` + `/history`.
>
> **Two forks the human must rule on before T8.1 / T8.5 — see "Phase 8 Open
> Decisions". Do not start T8.1 until D1 is answered.**

### T8.0 — Both: expand vision/reasoning model menus to 5 each + add Claude provider ✅ Complete
**Branch:** `feature/PP-042-model-lineup`
> Foundational — establishes the full enum T8.1 layers on. Adds one new provider
> (Claude); everything else is a model-string on an existing client.
> - **Vision:** PLANTNET · GITHUB_GPT41 · GITHUB_GPT4O · OLLAMA_GEMMA3 · ANTHROPIC_CLAUDE
> - **Reasoning:** GITHUB_O4_MINI · DEEPSEEK_R1 · GITHUB_GPT41_MINI · OLLAMA_GEMMA3 · ANTHROPIC_CLAUDE
>
> **Shipped:** new `AnthropicClient` (Apache HttpClient5 `RestClient`, Claude Messages API,
> serves both vision identification/annotation and cure-advice/disease-description text calls;
> `anthropic.api.key` has no required default so the app still boots un-keyed — `isAvailable()`
> gates it). `VisionModelPreference`/`ReasoningModelPreference` both grew to 5 live options +
> `OLLAMA_LLAVA` kept as a `@Deprecated` parseable alias (old stored rows still resolve; routing
> treats it identically to `OLLAMA_GEMMA3`) — no DB migration needed (additive enum values on a
> VARCHAR(30) column). `GitHubModelsClient.identifyPlant` and `DeepSeekClient`'s cure-advice /
> disease-description methods got model-parameterized overloads (`identifyPlantWithGpt41`,
> `generateCureAdviceViaO4Mini`/`ViaGpt41Mini`, etc.) routed through `IdentificationServiceImpl`
> and `TreatmentServiceImpl`'s preference switches; o4-mini's request shape (`max_completion_tokens`
> instead of `temperature`) is handled in `DeepSeekClient`'s shared `chatCompletion` helper.
> `UserPreferencesResponse` gained `visionModelAvailability`/`reasoningModelAvailability` maps
> (keyed by enum name) so the frontend can disable an un-keyed Claude instead of letting it 401.
> `ollama.model` default moved `llava-phi3` → `gemma3:4b`. Frontend: `ModelSelectorComponent`
> options relabeled by intent (Best/Balanced/Frontier/Specialist/Offline) with the real model name
> as a subtitle, unavailable options disabled with a tooltip. D1–D4 (Phase 8 Open Decisions) still
> open — out of scope for T8.0.

**Claude Code prompt:**
```
// Phase 8 — Expand both AI model menus to 5 each and add Claude. Most additions
// are model-strings on existing clients; Claude is the only new client. Keep the
// T7.1 split + loadVisionPreference()/loadReasoningPreference() routing — extend.

BACKEND
1. New AnthropicClient (serves BOTH vision + reasoning — Claude is multimodal):
   Anthropic Messages API (JSON body, base64 image content blocks, system as
   top-level field). Reuse defensive JSON parsing + the ```-fence-stripping half
   of stripThinkTags(). Config: anthropic.api-key, base-url, models.default
   (claude-sonnet-4-6), optional .cheap (claude-haiku-4-5)/.max (claude-opus-4-8).
   Apache HttpClient 5. 429 -> RateLimitException/retryAfterSeconds, never 500.
2. VisionModelPreference += GITHUB_GPT41, OLLAMA_GEMMA3, ANTHROPIC_CLAUDE. Mark
   OLLAMA_LLAVA @Deprecated but parseable (remap stored OLLAMA_LLAVA -> OLLAMA_GEMMA3).
3. ReasoningModelPreference += GITHUB_O4_MINI, GITHUB_GPT41_MINI, OLLAMA_GEMMA3,
   ANTHROPIC_CLAUDE. Same OLLAMA_LLAVA deprecation/remap.
4. Routing: GITHUB_GPT41 -> GitHubModelsClient (gpt-4.1 string); GITHUB_O4_MINI +
   GITHUB_GPT41_MINI -> the Azure text client (currently "DeepSeekClient" — new
   model strings, DO NOT rename, add a class comment it now serves multiple Azure
   text models); OLLAMA_GEMMA3 -> OllamaClient (verify /api/generate vs /api/chat
   for gemma3 vision); ANTHROPIC_CLAUDE -> AnthropicClient.
5. o4-mini request shape — FLAGGED GAP: uses max_completion_tokens (NOT max_tokens),
   no temperature, no <think> tags. Build the Azure text request per-model.
6. Provider availability: per-option `available` flag on the prefs response
   (anthropic.api-key set? ollama host configured?) so an un-keyed Claude is
   unselectable (else every call 401s).
7. Config strings: gpt-4.1, o4-mini, gpt-4.1-mini, gemma3:4b, anthropic block.
   Additive only.
8. OPTIONAL migration 023 (if taken, BUMP T8.1/T8.4/T8.5 to 024/025/026):
   reasoning_model_preference DEFAULT 'DEEPSEEK_R1' -> 'GITHUB_GPT41_MINI'
   (recommended — fast JSON gen, dodges DeepSeek-R1's 60s cap; confirm, changes
   behavior) + backfill OLLAMA_LLAVA -> OLLAMA_GEMMA3. (Adding enum VALUES needs no
   migration — pref columns are VARCHAR(30).)

FRONTEND
9. ModelSelectorComponent: 5 VISION + 5 REASONING options, LABEL BY INTENT
   (Best/Balanced/Offline/Specialist/Frontier) with model name as subtitle +
   tradeoff tooltips. Disable options whose provider isn't available (step 6).
   Update TS union types. "powered by {model}" badge already records the real model.
```

### T8.1 — Backend: PlantNet v2 client — ranked candidates, organs, reference images, quota ✅ Complete
**Branch:** `feature/PP-043-plantnet-v2-client` (merged PR #60)

**Claude Code prompt:**
```
// Phase 8 — Upgrade PlantNetClient to a full v2 ranked-candidate identifier.
// CONFIRM v1-vs-v2 first; keep Apache HttpClient 5 + HTTP/1.1 ALPN workaround.
1. identify(images, organs, project, lang): multipart POST /v2/identify/{project},
   api-key QUERY PARAM, ALWAYS type=kt, organs per image (default auto),
   include-related-images=true, nb-results=<cfg 6>, no-reject=false. Enforce 50 MB
   client-side. Map 404->friendly "not a plant" (404), 429->RateLimitException,
   413/415->clear errors.
2. Parse FULL ranked results[] -> PlantNetCandidate list: score, scientificName(s),
   genus, family, commonNames[], gbifId, powoId, iucnCategory, referenceImages[]
   (url.s/m + author/license/citation, cap 3). Top-level: bestMatch,
   switchToProject, predictedOrgans[], version, remainingIdentificationRequests.
   Defensive parse, never throw on a malformed field.
3. Storage (migration 023 — VERIFY; sequence at 022): JSONB plantnet_candidates on
   identifications + plantnet_version/best_match/switch_to_project/quota_remaining.
   Store raw response. Append to changelog IN ORDER.
4. runIdentification() PLANTNET case returns the candidate list + bestMatch +
   switchToProject. healthStatus stays UNKNOWN (no care plan). Per D1, build EITHER
   always-on-alongside-gpt-4o OR gated-on-VisionModelPreference.PLANTNET.
5. DTO: PlantNetCandidateDto + nullable plantNet* fields on IdentificationResponse.
6. Config: app.plantnet.nb-results (6), .lang (app default); project from prefs (T8.4).
```

### T8.2 — Backend: ranked-candidate species matching (Flow 1) wired to PlantNet ✅ Complete
**Branch:** `feature/PP-044-plantnet-species-match` (merged PR #61)

**Claude Code prompt:**
```
// Phase 8 — Flow-1 species confirmation = ranked pick-list. Extend the existing
// (T6.9) GET /{id}/species-match + POST /{id}/resolve-species.
1. species-match returns the candidate list (scientificName, commonName, score,
   referenceImages[] w/ attribution, gbifId, powoId, iucnCategory) + bestMatch +
   switchToProject. Keep the single-match fast path: one candidate over
   app.plantnet.auto-confirm-score (~0.90) with no close runner-up -> auto-confirmable.
2. resolve-species accepts chosen scientificName (or none/rescan); runs the EXISTING
   SpeciesService.findOrCreate; passes gbifId/powoId/iucnCategory onto Species (free
   factual layer — see T8.6).
3. Flow-1 ONLY (Flows 2/3 untouched). Extend SpeciesMatchDto (additive).
```

### T8.3 — Frontend: multi-candidate species confirmation UI (confidence + reference images) ✅ Complete
**Branch:** `feature/PP-045-species-confirm-candidates` (merged PR #62)

**Claude Code prompt:**
```
// Phase 8 — species-confirm-step from yes/no -> ranked chooser (T8.2 contract).
1. Candidate cards: common name + scientific name + confidence chip + up to 3
   reference thumbnails (lazy). Tap to confirm. Auto-confirmable -> one-tap +
   "Not this one ->". Always "None of these — rescan".
2. ATTRIBUTION required: per-thumbnail author + license caption. No image without it.
3. Surface switchToProject nudge (link to T8.4 flora pref) + organ-quality hint +
   optional IUCN chip. Reuse ModelUsageBadge ("Candidates from Pl@ntNet {version}")
   + AiErrorService (404 -> "not a plant"). Broken thumbnail never blocks confirm.
```

### T8.4 — Both: organ tagging + geolocation-ranked flora & common-name language ✅ Complete
**Branch:** `feature/PP-046-plantnet-organs-projects` (merged PR #63)

**Claude Code prompt:**
```
// Phase 8 — per-image organ tags + location-ranked flora/lang.
FRONTEND
1. Multi-angle (single-plant) mode ONLY: per-image organ selector
   (auto/leaf/flower/fruit/bark/habit, default auto); forward organs[] on analyze.
2. /preferences "Pl@ntNet" subsection: Flora/region (from /v2/projects ranked by
   user location, default-select top) + common-name language (default app lang).
BACKEND
3. Location for ranking: pass lat/lon to /v2/projects?lat&lon. FLAG where lat/lon
   comes from (browser geolocation on prefs page? stored field?). If none, ship
   manual dropdown first. Don't hardcode a region.
4. Migration 024 (VERIFY): users.plantnet_project (default 'all'), .plantnet_lang.
5. Thread project+lang into T8.1 identify() (loadPlantNetPreferences).
6. Cached proxies GET /plantnet/projects (optional lat/lon) + /plantnet/languages
   (@Cacheable 24h, type=kt upstream).
```

### T8.5 — Backend: PlantNet disease/pest cross-check feeding the Treatment flow ✅ Complete
**Branch:** `feature/PP-047-plantnet-disease-crosscheck` (merged PR #65)

**Claude Code prompt:**
```
// Phase 8 — PlantNet disease classifier as a SECOND OPINION on Flow-3, cross-
// checking gpt-4o. NOT a replacement. /v2/diseases/identify = cultivated-plant
// scoped; low/empty = "no corroboration", not "healthy".
1. PlantNetDiseaseClient.identifyDisease(images, organs, lang): multipart POST
   /v2/diseases/identify (multi-image), include-related-images, nb-results, lang.
   Same conventions as T8.1. Results: {name, description, score, images[]}.
   404/empty -> empty, not error. Optionally resolve name vs GET /v2/diseases (EPPO).
2. Flow 3: run alongside gpt-4o annotation (parallel CompletableFuture). Per D4:
   AGREEMENT -> confidence flag, EPPO/taxonomic label for Treatment.diseaseName,
   SEED Treatment.diseaseDescription from PlantNet result.description (saves a
   DeepSeek call). DISAGREEMENT (rec) -> keep gpt-4o's label, attach PlantNet as
   flagged second opinion, mark Treatment NEEDS_REVIEW.
3. Storage (migration 025, VERIFY): JSONB on identification + TreatmentResponse
   second-opinion block + agreement flag + EPPO code.
4. Quota: Flow-3 may hit gpt-4o AND /v2/diseases/identify (double PlantNet quota);
   surface remaining; 429 -> existing RateLimit UX, never swallow.
```

### T8.6 — Backend: factual species enrichment (IUCN/POWO free; GBIF deeper) ✅ Complete
**Branch:** factual enrichment shipped (GBIF/POWO/IUCN free layer; deeper GBIF deferred)

**Claude Code prompt:**
```
// Phase 8 — factual taxonomy over hallucinated DeepSeek enrichment.
1. CHEAP LAYER (no extra call, regardless of D3): persist gbifId/powoId/iucnCategory
   (passed from T8.2 resolve-species) onto Species; set externalDataSource accordingly.
2. DEEPER LAYER (only if D3="now"): GbifClient.fetchSpecies(gbifId) -> GET
   api.gbif.org/v1/species/{id} (no-auth read) for distribution + vernaculars;
   prefer GBIF taxonomy when gbifId present, externalDataSource="GBIF"; DeepSeek
   only as fallback / for care-overview prose.
3. Add new externalDataSource values (migration only if column is constrained).
```

### T8.7 — Frontend: PlantNet quota + provider telemetry ✅ Complete
**Branch:** quota telemetry shipped in /preferences PlantNet subsection

**Claude Code prompt:**
```
// Phase 8 — surface PlantNet daily quota proactively (real endpoints).
1. Backend cached proxy GET /plantnet/quota -> /v2/quota/daily
   ({day, quota.identify.{count,total,remaining}}), TTL ~5min; optional /history.
2. /preferences Pl@ntNet subsection: "Pl@ntNet: {remaining}/{total} left today".
   Unknown != zero. Optional history sparkline.
3. PlantNet 429/exhausted -> AiErrorService snackbar (PlantNet-specific msg +
   "switch model in Settings"). Inline low-quota hint in the vision dropdown.
```

### Phase 8 — Open Decisions
- **D1 — PlantNet always-on vs. only-when-selected** *(blocks T8.1)* — rec:
  always-on for Flow-1 candidates behind a feature flag. **Need the call.**
- **D2 — Candidate storage** *(blocks T8.1)* — JSONB on `identifications` (rec) vs. side table.
- **D3 — Factual-enrichment depth** *(shapes T8.6)* — cheap layer ships regardless; defer GBIF fetch.
- **D4 — Disease cross-check authority** *(blocks T8.5)* — rec: agreement → seed description
  from PlantNet; disagreement → keep gpt-4o, attach PlantNet as flagged second opinion,
  NEEDS_REVIEW. **Need the call.**
- **D-location** *(non-blocking, shapes T8.4)* — location-ranked floras need a lat/lon source
  that may not exist yet; ship manual dropdown first.

---

## PHASE 8.5 — Identification Pipeline Resilience & Partial-Result Recovery  ✅ COMPLETE
> **Execution order:** runs after Phase 8 (T8.0 done, T8.1–T8.7 ongoing), before Phase 9.
> Rationale: Phase 9's E2E suite and nightly eval will test a pipeline that currently
> cannot survive its own happy path — harden the pipeline first, then test the fixed version.
>
> **Triggered by:** a batch of 5 concurrent identifications all surfacing as FAILED despite
> gpt-4o returning valid care plans for 4/5. Three root causes:
> 1. **Partial failure treated as total** — an exception in annotation or PlantNet propagated
>    through `processIdentification()` and failed the entire record.
> 2. **Self-inflicted 429 storm** — 5 concurrent identifications × ~3 full-base64 vision calls
>    each exhausted the GitHub Models 60 000-token/60s ceiling. One identification (id=2) had
>    its core gpt-4o call 429'd — a genuine failure; the other 4 had valid care plans that were
>    discarded because annotation threw.
> 3. **PlantNet DTO regression (Phase 8)** — `PlantNetResponse.predictedOrgans` typed
>    `List<String>` but v2 returns `[{organ, score}]` objects. Jackson throws on every response.
>    The integration has NEVER succeeded in production since Phase 8 shipped.
>
> **Key design decisions:** D5 (per-stage status model — load-bearing, all tasks depend on it)
> and D1 amendment (PlantNet deferred from critical path) are recorded in ARCHITECT.md.
>
> **Branch numbers:** T8.A–T8.G use **PP-050–PP-056**. Phase 9 branches shift to PP-057+
> (already updated in this file).

### T8.A — Architect: per-stage status model on identifications (D5)  ✅ Complete
**Branch:** `feature/PP-050-identification-stage-status` (merged PR #67)
> Migration 027 applied. D5 recorded in ARCHITECT.md.

**Claude Code prompt:**
```
// Phase 8.5 — T8.A: add per-stage status + model tracking to identifications.
// Migration 027 — VERIFY against db.changelog-master.xml (sequence ends at 026).

BACKEND
1. Migration 027: add to identifications:
     identification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
     annotation_status     VARCHAR(30) NOT NULL DEFAULT 'PENDING'
     candidate_status      VARCHAR(30) NOT NULL DEFAULT 'SKIPPED'
     identification_model  VARCHAR(50)   -- VisionModelPreference name for core call
     annotation_model      VARCHAR(50)   -- always 'gpt-4o-mini' today; for eval corpus
     failure_reason        TEXT          -- set only when identification_status='FAILED'
   Backfill:
     COMPLETED → identification_status='COMPLETED', annotation_status='COMPLETED',
       candidate_status='SKIPPED' (PlantNet broken since Phase 8 — SKIPPED is honest)
     FAILED    → identification_status='FAILED', annotation_status='SKIPPED',
       candidate_status='SKIPPED'
     PENDING   → all three 'PENDING'
   Append to db.changelog-master.xml IN ORDER. Never insert between existing entries.

2. New Java enum IdentificationStageStatus { PENDING, COMPLETED, FAILED, SKIPPED }
   in identification/entity/ (distinct from IdentificationStatus — adds SKIPPED).

3. Add the 6 new JPA fields to Identification entity. Update IdentificationMapper and
   IdentificationResponse (additive — never drop existing fields).

4. processIdentification(): populate identification_model from loadVisionPreference()
   (already resolved in T7.1; pass it through to the save). Begin dual-writing:
     core success → identification_status='COMPLETED'
     core failure → identification_status='FAILED',
       failure_reason tag ('RATE_LIMITED'|'PARSE_ERROR'|'PROVIDER_ERROR'|'OTHER')
   Annotation/candidate fields populated in T8.B. Existing status column unchanged.

VERIFY: full unit suite green. IdentificationServiceImplTest updated for any new
constructor params.
```

### T8.B — Backend: make annotation & candidate stages non-fatal  ✅ Complete
**Branch:** `feature/PP-051-non-fatal-enrichment-stages` (merged PR #68)

**Claude Code prompt:**
```
// Phase 8.5 — T8.B: annotation and PlantNet exceptions must NEVER fail the
// identification record. This single change converts 4/5 observed failures to successes.

BACKEND — IdentificationServiceImpl.processIdentification()
1. Annotation stage: wrap analyzeRegions() in try/catch.
     success → annotation_status='COMPLETED'
     exception → log WARN, annotation_status='FAILED'. NEVER re-throw.
   Null/empty annotation regions already handled gracefully by the frontend.

2. PlantNet candidate stage: same pattern.
     success → candidate_status='COMPLETED'
     exception → log WARN, candidate_status='FAILED'. NEVER re-throw.

3. The existing status column set to 'COMPLETED' if and only if
   identification_status='COMPLETED' — regardless of annotation/candidate status.
   A record with core COMPLETED + annotation FAILED still saves as status='COMPLETED'.
   Core failures still set status='FAILED' as before.

VERIFY: IdentificationServiceImplTest — three explicit tests:
  (a) all stages succeed → status=COMPLETED, all stage statuses COMPLETED
  (b) core succeeds, annotation throws → status=COMPLETED, annotation_status=FAILED
  (c) core throws → status=FAILED, identification_status=FAILED, failure_reason set
```

### T8.C — Backend: fix PlantNetResponse predictedOrgans deserialization  ✅ Complete
> PlantNetPredictedOrgan inner class added; deserialization unit test with v2 fixture added.

**Claude Code prompt:**
```
// Phase 8.5 — T8.C: fix PlantNet DTO regression. predictedOrgans typed List<String>
// but PlantNet v2 returns [{organ, score}] objects — Jackson throws on every response.

BACKEND
1. New class PlantNetPredictedOrgan { String organ; Double score; }
   @JsonIgnoreProperties(ignoreUnknown=true). Retype
   PlantNetResponse.predictedOrgans List<String> → List<PlantNetPredictedOrgan>.
   @JsonIgnoreProperties(ignoreUnknown=true) on PlantNetResponse too.

2. Update all downstream consumers of predictedOrgans to the new type.
   Where it surfaces in DTOs (PlantNetCandidateDto etc.), update mapping.

3. Unit test: fixture src/test/resources/fixtures/plantnet_identify_response.json
   (faithful PlantNet v2 /identify response body with predictedOrgans as
   [{organ,score}] objects). Assert: predictedOrgans non-empty, organ != null,
   score > 0. @DisplayName: note this fixture also feeds Phase 9 T9.8 eval corpus.
   This test MUST fail before the fix and pass after — confirm before submitting.

4. No migration needed.
```

### T8.D — Backend: throttle AI vision fan-out + document annotation routing  ✅ Complete
**Branch:** `feature/PP-053-vision-fanout-throttle` (merged PR #69)

**Claude Code prompt:**
```
// Phase 8.5 — T8.D: prevent N concurrent batch identifications from stampeding
// the GitHub Models token ceiling. Three backpressure mechanisms. Also document
// three annotation-routing facts that look like bugs but are deliberate.

BACKEND — backpressure
1. Server-side token-budget Bucket4j bucket on GitHub Models outbound calls:
   Budget: 40 000 tokens/60s (lower observed ceiling). Rough heuristic: chars/4
   for base64 image payloads — goal is backpressure, not accounting precision.
   On exhaustion: throw RateLimitException (reuse toServiceException() from
   DeepSeekClient). Config: app.rate-limit.github-models-tokens-per-minute=40000.

2. Finite aiTaskExecutor queue capacity: set queueCapacity to 20 in AsyncConfig.
   Submissions beyond the queue fail fast with RateLimitException BEFORE the Kafka
   publish. The existing per-user Bucket4j check runs first; this is a global backstop.

3. Exponential backoff with jitter on GOAWAY/EOF retries (DeepSeekAnnotationClient,
   GitHubModelsClient): attempt 1 immediate, attempt 2 after min(retryAfterSeconds,5)
   × 1.5^(attempt-1) + uniform jitter [0,1s]. 429s NOT retried (propagate as
   RateLimitException per T7.1). Cap total retry wait at 30s.

BACKEND.md documentation (write to BACKEND.md "Phase 8.5 — Pending Contract Changes",
NOT as inline code comments — see the section already added there):
4. Annotation model deliberately pinned to gpt-4o-mini regardless of VisionModelPreference.
5. DeepSeekAnnotationClient is NOT an independent-quota fallback — shares the primary's
   token bucket. 429 on annotation → annotation_status='FAILED' (T8.B), no retry.
6. ReasoningModelPreference NOT invoked in processIdentification() — drives only
   standalone cure-advice, disease-description, species-enrichment paths.

VERIFY: unit tests for the token-budget bucket and jittered retry logic.
```

### T8.E — Architect/Backend: defer PlantNet candidates from critical path (D1 amendment)  ✅ Complete
**Branch:** `feature/PP-054-plantnet-async-candidates` (merged PR #71)
> D1 amendment recorded in ARCHITECT.md.

**Claude Code prompt:**
```
// Phase 8.5 — T8.E: move PlantNet candidate enrichment from synchronous-inside-
// processIdentification to fire-and-forget async AFTER core identification succeeds.
// Mirrors SpeciesEnrichmentService.enrich(). Records the D1 amendment (already in
// ARCHITECT.md — read it).

BACKEND
1. After core stage saves as COMPLETED: CompletableFuture.runAsync(aiTaskExecutor):
     → PlantNetClient.identify()
     → success: save plantnet_candidates JSONB + version + candidate_status='COMPLETED'
     → exception: candidate_status='FAILED', log WARN, no re-throw
   Set candidate_status='PENDING' (not 'SKIPPED') when the async call is queued.

2. Exception: when VisionModelPreference.PLANTNET is the user's explicit choice,
   PlantNet IS the core call and runs synchronously unchanged. Only the always-on
   enrichment-alongside-gpt-4o path is deferred.

3. The frontend's existing 3s poll re-fetches the full record — candidates appear
   when candidate_status transitions to COMPLETED. No new polling needed.

VERIFY: test that a PlantNet failure in async enrichment does NOT change
identification status from COMPLETED. Template: SpeciesEnrichmentServiceImplTest.
```

### T8.F — Backend: retry endpoint for failed identifications  ✅ Complete
**Branch:** `feature/PP-055-identification-retry-endpoint` (merged PR #72)

**Claude Code prompt:**
```
// Phase 8.5 — T8.F: POST /api/v1/identifications/{id}/retry — re-run only the
// FAILED stages so genuine failures aren't permanent dead ends.

BACKEND
1. POST /api/v1/identifications/{id}/retry (IdentificationController):
   - 401 if not authenticated.
   - 404 if not found.
   - 403 if userId mismatch (same ownership check as GET /{id}).
   - 409 if status='PENDING' (already in flight — fail with clear error).
   - status='COMPLETED' with failed enrichment stages: re-queue only annotation
     (if annotation_status='FAILED') and/or candidates (if candidate_status='FAILED').
     Set affected fields back to 'PENDING', save, fire async per T8.B/T8.E.
   - status='FAILED' (core failed): reset identification_status='PENDING',
     annotation_status='PENDING', candidate_status='PENDING', failure_reason=null,
     status='PENDING'. Re-publish IdentificationRequestedEvent (reuse existing
     submitIdentification() event-publishing path).
   - Returns 202 Accepted with current identification state.

2. Rate-limit: apply the same per-user ai-calls-per-hour Bucket4j check as /analyze.

3. IdentificationService interface: add retryIdentification(Long id, Long userId).

VERIFY: unit tests covering 409 (PENDING conflict), COMPLETED enrichment-only retry,
FAILED full retry, 403 ownership mismatch.
```

### T8.G — Frontend: stage-aware partial-result UI  ✅ Complete
**Branch:** `feature/PP-056-stage-aware-identification-ui` (merged PR #70)

**Claude Code prompt:**
```
// Phase 8.5 — T8.G: replace binary FAILED/COMPLETED with stage-aware states.
// Check the updated IdentificationResponse shape in BACKEND.md before building.

FRONTEND
Update identification.model.ts:
  Add identificationStatus, annotationStatus, candidateStatus
  ('PENDING'|'COMPLETED'|'FAILED'|'SKIPPED'), identificationModel, annotationModel,
  failureReason (string | null).

Identification result / list views:
1. status='COMPLETED' (identificationStatus='COMPLETED'):
   - Show care plan as before.
   - annotationStatus='FAILED': show inline chip "Overlay unavailable" (no blank
     overlay, no hidden photo). Optional "Retry overlay" link (wires to T8.F —
     re-queues annotation only since core is done).
   - candidateStatus='FAILED': in Flow-1 species-confirm step (T8.3), show
     "PlantNet candidates unavailable"; elsewhere no visible change.
   - annotationStatus='PENDING' or candidateStatus='PENDING': continue existing 3s
     poll — stages resolve async, poll re-fetches the full record.

2. status='FAILED' (identificationStatus='FAILED'):
   - Show red FAILED chip (unchanged).
   - Add "Retry" button → POST /identifications/{id}/retry → on 202: trigger
     pollUntilComplete() so UI transitions PENDING → COMPLETED automatically.
   - Optionally surface failureReason as a tooltip or secondary text line.
   - AiErrorService.handle() already formats 429 responses — wire it to the retry
     button's error path too.

3. status='PENDING': unchanged (existing spinner).

VERIFY: ng build, ng lint, tsc --noEmit clean. Playwright tests deferred to T9.2.
```

---

## PHASE 9 — Quality, Testing & Hardening  ✅ COMPLETE (code, 2026-06-25 — pending CI + merge to dev)
> Goal: solidify the project at enterprise standard *throughout dev*, not as a
> launch afterthought. Closes the real gaps — the frontend has **zero tests**
> (ships on `ng build`/`lint`/`tsc` alone), JaCoCo is at **55%** not 80%, there's
> **no E2E**, **no error tracking**, **no secret scanning** (a `GITHUB_TOKEN`
> already leaked into chat), and **no AI evals** for the recurring malformed-JSON
> failures.
>
> **The layered UI-quality model (read before building):** deterministic checks
> GATE the build; the AI visual reviewer only ADVISES.
> - Layer 1 (gate): Playwright E2E — critical journeys work at all.
> - Layer 2 (gate): visual regression (`toHaveScreenshot`) — UI didn't change unexpectedly.
> - Layer 3 (gate): a11y (axe) + Lighthouse budgets — measurable quality floors.
> - Layer 4 (advisory, NEVER gates): AI visual review posts a PR comment; human/Architect decides.
>
> **Load-bearing principle:** E2E **stubs the external AI/PlantNet calls**
> (`page.route`) for determinism, zero cost, no rate limits. The *real* AI quality
> is tested separately by the nightly eval suite (T9.8). Never let E2E depend on
> live gpt-4o/PlantNet — that's the classic flaky-and-expensive trap.
>
> 💡 **Tooling decision (recorded):** Playwright, not Cypress — it ships WebKit
> (your PWA must work on iOS Safari; Cypress has no WebKit), free parallelization,
> and built-in visual regression. See ARCHITECT.md's Testing & Quality Pipeline.
>
> **Recommended:** T9.1 → T9.2 → T9.3 and T9.5 are foundation — do them first or
> interleaved with Phase 8 so PlantNet's new UI is covered as it's built.

### T9.1 — Frontend: component/unit test runner + first tests 🤝 Assisted
**Branch:** `feature/PP-057-frontend-test-runner` (verify next free PP)

**Claude Code prompt:**
```
// Phase 9 — Stand up a frontend test runner. The frontend has NO tests today.
1. Add Vitest + @analogjs/vitest-angular (the 2026 Angular default; replaces the
   skeleton Karma/Jasmine setup if present) + @testing-library/angular. Wire
   `npm test` + a CI job (alongside the existing ng build/lint/tsc gates).
2. Establish the patterns with 3 real tests, one of each kind:
   - a service test (e.g. AiErrorService 429-handling, or BatchScanService queue),
   - a component test (e.g. ModelSelectorComponent option rendering / disabled
     state from the `available` flag; or species-confirm-step candidate list),
   - a pure util/pipe test (e.g. health-badge.util).
   Use Testing Library queries (getByRole/getByText), not deep DOM selectors.
3. Coverage: start a gate LOW (e.g. 30%) and ratchet up over time — do NOT fake
   80% on day one. Exclude generated/main/env files.
```

### T9.2 — Frontend: Playwright E2E skeleton + critical journeys 🤝 Assisted
**Branch:** `feature/PP-058-playwright-e2e` (verify)

**Claude Code prompt:**
```
// Phase 9 — Playwright E2E. Chromium + WebKit projects minimum (WebKit = iOS
// Safari, mandatory for the PWA). ~10–15 CRITICAL journeys only — resist the
// over-testing trap (fewer, trusted tests beat hundreds of flaky ones).
1. Set up Playwright (TS), projects: chromium + webkit (firefox optional). Trace +
   screenshot on failure. CI job on PR, sharded/parallel (free).
2. STUB all external AI + PlantNet network calls via page.route so journeys are
   deterministic, free, and rate-limit-free — return canned IdentificationResponse /
   species-match / candidate / treatment payloads. E2E must NOT hit live
   gpt-4o/PlantNet (that's T9.8's job).
3. Critical journeys (data-testid selectors, add them where missing):
   - register -> login -> land on Home
   - Flow-1: identify a plant -> ranked species confirm (T8.3) -> land on plant
   - health scan (Flow-3) -> disease -> start treatment -> mark a step done
   - create a reminder; chat single-turn (stubbed stream)
   - PWA: app shell loads; offline reading of a cached page (covers part of T3.3)
4. Page Object pattern for the screens reused across journeys (auth, garden,
   plant, treatment) — keep selectors in one place so a UI refactor is a 1-file fix.
```

### T9.3 — Frontend: visual regression + accessibility + performance budgets (deterministic gates) 🤝 Assisted
**Branch:** `feature/PP-059-visual-a11y-perf` (verify)

**Claude Code prompt:**
```
// Phase 9 — The deterministic UI gates (Layers 2+3). Reuse T9.2's Playwright setup.
1. Visual regression: toHaveScreenshot() baselines on key screens (Home, Garden,
   Plant, Treatment, species-confirm, /preferences model-selector). Commit baselines;
   update them explicitly on intentional change. Mask volatile regions (timestamps,
   AI text) so diffs are stable.
2. Accessibility: @axe-core/playwright on the same screens — contrast, labels,
   tap-target size, ARIA. Fail on serious/critical violations.
3. Performance/PWA: Lighthouse CI with budgets — bundle size, PWA installability
   score, performance threshold. (Folds in T10's Angular bundle checks.)
4. All three GATE the build (deterministic). Document the "update baselines"
   command in the repo README so an intentional UI change isn't mistaken for a regression.
```

### T9.4 — AI visual-review agent (advisory PR comment — NEVER gates) 🤝 Assisted
**Branch:** `feature/PP-060-ai-visual-review` (verify)
> 👤 **Decision:** reuse the app's own vision clients (gpt-4o/Claude) or a standalone
> dev/CI script calling a vision model directly? *Rec:* standalone script with a
> clean boundary from product runtime AI — don't entangle dev tooling with the
> request path. Note the reuse option if you'd rather share one config.

**Claude Code prompt:**
```
// Phase 9 — The "agent reviews the UI" idea, in its proper (advisory) layer.
// MUST NOT fail CI — non-deterministic AI as a gate destroys trust.
1. A CI/dev script takes the screenshots already captured in T9.3, sends each +
   a fixed rubric prompt to a vision model: "flag overflow, overlap, cramped
   spacing, inconsistent alignment, broken responsive breakpoints, low contrast;
   output JSON {issues:[{severity, area, description, suggestion}]}".
2. Post results as a PR COMMENT (advisory). The deterministic layers below decide
   pass/fail; this only proposes. Human / Architect agent dispositions it.
3. Cost control: curated screen set, run on-demand or nightly (a label trigger,
   not every commit) — vision calls cost money + burn quota. Keep this a separate
   dev tool from the product's runtime AI codepaths.
```

### T9.5 — Backend: wire ITs into `mvn verify` + restore JaCoCo to 80% 🤖 AI
**Branch:** `feature/PP-061-backend-test-gates` (verify)

**Claude Code prompt:**
```
// Phase 9 — Close two long-standing enterprise-checklist items.
1. Wire the controller ITs (Testcontainers) into `mvn verify`: maven-failsafe-plugin
   for *IT.java, forkCount=1 / a serial execution to avoid the known Testcontainers
   connection-pool contention on this dev machine (run ITs sequentially, not batched).
   `mvn clean verify` must be green end to end.
2. Restore the JaCoCo gate 55% -> 80% with proper exclusions (DTOs, config, the
   @SpringBootApplication main, generated MapStruct, simple enums). If 80% line is a
   real stretch after exclusions, ratchet (e.g. 70% now + a documented plan) rather
   than gaming exclusions — but the target is 80% per the checklist.
3. Update BACKEND.md's Test Inventory + ARCHITECT.md's JaCoCo line.
```

### T9.6 — Observability: error tracking (Sentry) + correlation 🤝 Assisted
**Branch:** `feature/PP-062-error-tracking` (verify)

**Claude Code prompt:**
```
// Phase 9 — Highest-ROI observability for a small team: error tracking.
1. Sentry SDK for Angular (capture unhandled errors + HTTP failures, upload source
   maps in the prod build) and for Spring Boot (capture exceptions, attach the MDC
   correlationId from the structured-logging setup — see T10 prod config).
2. Propagate ONE correlation id frontend -> backend (a request header the jwt
   interceptor already touches is a natural carrier) -> Sentry, so a frontend error
   and its backend stack trace link up. Free tier; env-gated DSN (off in dev).
3. Metrics (Micrometer/Prometheus) are noted as a later add — error tracking first.
```

### T9.7 — CI supply-chain & secret scanning 🤝 Assisted
**Branch:** `feature/PP-063-supply-chain-scanning` (verify)

**Claude Code prompt:**
```
// Phase 9 — Automated security scanning in CI. gitleaks specifically would have
// caught the GITHUB_TOKEN that leaked into chat.
1. gitleaks — secret scanning on every PR + a full-history scan once; fail on findings.
2. Dependabot (or Renovate) — automated dependency-update PRs for Maven + npm.
3. Trivy — CVE scan of the backend Docker image in CI.
4. OWASP Dependency-Check — Maven plugin in CI, fail on CVSS >= 7 (this absorbs the
   T10 security-hardening item; keep it in ONE place, here).
5. Add a SECURITY note: rotate the leaked GITHUB_TOKEN now, before any of this — a
   scanner doesn't un-leak an exposed credential.
```

### T9.8 — AI eval suite + prompt-injection guardrails 🤝 Assisted
**Branch:** `feature/PP-064-ai-evals` (verify)

**Claude Code prompt:**
```
// Phase 9 — Test the REAL AI separately from E2E (which stubs it). Directly targets
// the recurring malformed-JSON pain (LenientJsonParser saga).
1. Eval suite: a small golden set of fixed inputs (a few identification images, a
   couple of care-plan / cure-advice prompts) run against the live AI clients,
   asserting the response PARSES and meets basic shape checks (required fields
   present, action plan normalizes, care cards have valid CareCardType, Mermaid
   constraints hold). Run NIGHTLY (cost + the DeepSeek-R1 60s cap), not per-commit;
   keep it out of the PR gate. Report pass/fail per model so a provider regression
   is visible.
2. Prompt-injection guardrails: chat messages + any uploaded/OCR'd text flow into
   AI prompts. Add input handling distinct from HTML-XSS sanitization (which stays
   in T10) — delimit user content, keep system instructions authoritative, and
   re-validate AI OUTPUT before it touches the DB (ActionPlanValidator is the
   precedent). Add an eval case that feeds an injection attempt and asserts the
   system prompt isn't overridden.
```

---

# Phase 9.5 — Species Card Harvest + Async-Description Reliability

> **Inserted block.** Runs AFTER Phase 9 (Quality/Testing/Hardening) and BEFORE Phase 10 (Launch).
> Letter-suffix tasks (T9.A–T9.D) per the inserted-sub-phase convention (same shape as the
> Phase 8.5 / T8.A–T8.G block).
> ⚠️ **Numbering is provisional.** Confirm the phase label and renumber the decisions below to
> your current `ARCHITECT.md` D-series high-water mark before logging — the snapshot I worked
> from disagrees with itself on the current D-number (files show D5/D6, the running log shows
> D1–D4 still open). Migration numbers below are `NNN` placeholders — use the next free numbers
> and treat `db.changelog-master.xml` as source of truth, per existing practice.

---

## Why this phase exists

Two bugs from the latest scan-through, **with one shared root cause** plus one wasted-data
problem on top.

The shared root cause: **every async AI-text field in this app is a fire-and-forget call that
degrades to `null` on failure, and the frontend renders `null` as "still generating" with no
terminal state and no retry.** So a single transient failure — very plausibly a DeepSeek-R1
`1-call/60s` upstream rate-limit when two of these fire close together — leaves a permanent
spinner. This bites two different screens identically:

- `Treatment.diseaseDescription` (fired fire-and-forget from `createTreatment()` via
  `CompletableFuture.runAsync`, failures → null) → **Image 1: "Generating a description for this
  disease…" stuck under an IN_PROGRESS treatment.**
- `Species.description` / `careOverview` (T6.4 `SpeciesEnrichmentService.enrich()`, failures →
  `NEEDS_REVIEW` + null fields) → **Image 3: "Gathering info about this species…" on an
  otherwise-blank `/garden/species/4`.**

The wasted-data problem (Image 2): the PlantNet candidate card is **excellent** — confirmed
scientific + common name, family · genus, a real photo per candidate with attribution + license,
a confidence score, provenance (`Pl@ntNet 2026-03-20 (7.4)`). **None of it is persisted onto the
Species.** `findOrCreate()` only copies `scientificName` + `commonName`; image, taxonomy, and the
candidate photos are thrown away the moment the user leaves the preview. So the Species page has
nothing real to show and is left waiting on the separate, failure-prone AI enrichment call for
data we *already had in hand*.

The fix is two moves:
1. **Stop discarding PlantNet data.** Harvest the confirmed candidate (image, family, genus,
   attribution) onto the Species at resolve time, and persist the full candidate list for the
   strip. The overview is then never empty — it has a real photo and taxonomy instantly,
   independent of any AI call.
2. **Make async text honest.** Add a per-field generation status (PENDING / READY / FAILED) to
   both `Species` and `Treatment`, poll while PENDING, and expose a Retry on FAILED. Same
   per-stage-status philosophy already formalized as D5.

---

## Diagnosed problems

### P1 — Treatment description lost / perpetual spinner (Image 1)
`createTreatment()` fires `DeepSeekClient.generateDiseaseDescription(species, diseaseName)`
fire-and-forget; any failure degrades `diseaseDescription` to `null` with no persisted status, no
retry, and no poll. The user then crafts the plan (→ IN_PROGRESS), but the description never
arrives. `TreatmentDetailComponent` renders `diseaseDescription == null` as the
"Generating a description for this disease…" pending state — which never resolves because nothing
will ever set it. The treatment itself is fine; only the description is lost.

### P2 — Species overview empty; PlantNet card data discarded (Images 2 & 3)
`SpeciesService.findOrCreate(scientificName, commonName)` persists a Species with
`description=null, careOverview=null, imageUrl=null` and relies entirely on
`SpeciesEnrichmentService.enrich()` to fill them. When enrichment fails or hasn't completed, the
page is blank with "Gathering info…". Meanwhile the rich PlantNet candidate data that produced
Image 2 — image, family, genus, attribution, the full candidate list — is never written anywhere
durable, so even a successful page load has no photo or taxonomy to fall back on.

---

## Decisions needed (your ruling gates the prompts)

> Assign real sequential D-IDs from your `ARCHITECT.md` high-water mark when you log these; I've
> numbered them 1–5 here only for reference.

**Decision 1 — Species identity/image source: PlantNet candidate (recommended) vs AI enrichment.**
Should the Species' `imageUrl` + taxonomy (`family`, `genus`) come from the confirmed PlantNet
candidate we already have, harvested at resolve time?
→ **Recommend YES.** It's botanical ground truth, already in hand, comes with a real photo +
attribution, and makes the overview non-empty *immediately* regardless of any AI call. This is the
core of the request.

**Decision 2 — Candidate storage shape (resolves the still-open D2).**
Where do the full PlantNet candidates live?
(a) Full candidate list as **JSONB on the Identification row** (provenance + the candidate strip)
+ confirmed candidate's flat fields **denormalized onto Species** (display). ← **Recommend.**
Mirrors the existing `annotation_regions` / `care_plan` JSONB precedent exactly.
(b) Only on Species. (c) New `species_candidates` table.
→ **Recommend (a).** First confirm whether candidates are *already* persisted on Identification
before adding the column — don't assume.

**Decision 3 — Fate of the AI species enrichment (T6.4).**
Now that PlantNet supplies identity + image + taxonomy, what does `SpeciesEnrichmentService` do?
(a) Retire it. (b) **Narrow it to `description` + `careOverview` prose only** (the care-guidance
PlantNet does *not* provide). (c) Leave as-is.
→ **Recommend (b).** Keeps the useful prose, drops the redundant image-fetch, and — combined with
Decision 1 — means a failed enrichment only costs the prose paragraph, never the whole page.

**Decision 4 — Async-text reliability pattern (the shared fix for P1 + P2).**
Adopt a per-field generation-status enum (`PENDING | READY | FAILED`) on both `Species`
(covers description/careOverview) and `Treatment` (covers diseaseDescription); frontend polls
while PENDING and shows a Retry on FAILED; add `regenerate` endpoints.
→ **Recommend YES.** Same per-stage-status model as D5. Without this, every async-text field in
the app can silently strand on a spinner.

**Decision 5 — Phase number + Launch ordering.**
Provisional **Phase 9.5**, tasks **T9.A–T9.D**, inserted between Phase 9 and Phase 10.
Confirm Launch stays Phase 10 (last). If you'd rather this be a full numbered phase that pushes
Launch, say so and I'll relabel.

---

## Tasks

> Standing rules (unchanged): constructor injection only, no `@Autowired`; controllers return
> `ApiResponse<T>`; no `null` returns from services; `Pageable` on all lists; soft-delete only;
> no `@Data` on JPA entities; never block the request on an async AI call; degrade gracefully,
> never throw out of an `@Async`/`runAsync` body. `Species` is **shared** (no ownership check on
> the row); `Treatment` is **ownership-checked** via `plantId → Plant.userId`.
> ⚠️ Do not conflate `Treatment` (disease lifecycle, `com.plantpal.treatment`) with `TreatmentPlan`
> (generic action plan, `com.plantpal.reminder`) — see ARCHITECT.md's "Two Treatment concepts".

---

### T9.A — Harvest PlantNet candidate → Species; persist candidate list 🤖 AI
**Branch:** `feature/PP-0NN-species-card-harvest`
**Depends on:** confirms Decisions 1 & 2 first. Builds on T6.1 (Species), T6.9 (resolve-species).

**Backend Claude Code prompt:**
```
// T9.A — Persist PlantNet candidates + harvest the confirmed candidate onto Species

Read ARCHITECT.md (Species entity + the identification resolve-species flow), STATE.md's T6.9
entries, SpeciesServiceImpl.findOrCreate(), the resolve-species endpoint in
IdentificationServiceImpl, and the current PlantNet response DTOs
(dto/plantnet/PlantNetResponse + PlantNetResult + PlantNetSpecies + PlantNetTaxon) before starting.

STEP 0 — confirm, don't assume:
- Grep for where the PlantNet candidate list currently lives after identify. If the full
  candidate list is ALREADY persisted (e.g. a JSONB column on identifications), DO NOT add a new
  column — reuse it and skip step 1. Report what you find before writing the migration.

1. Migration NNN_add_identification_candidates.sql (only if step 0 shows it's not already stored):
   ALTER TABLE identifications ADD COLUMN candidates JSONB;  -- nullable
   Stores the full PlantNet candidate array as returned: each element
   { scientificName, commonName, family, genus, score, imageUrl, imageAuthor, imageLicense }.
   Register as the next entry in db.changelog-master.xml (append, never insert between shipped ones).

2. Migration NNN+1_add_species_botanical_fields.sql:
   ALTER TABLE species ADD COLUMN family VARCHAR(255);          -- nullable
   ALTER TABLE species ADD COLUMN genus VARCHAR(255);           -- nullable
   ALTER TABLE species ADD COLUMN image_attribution VARCHAR(255);
   ALTER TABLE species ADD COLUMN image_license VARCHAR(64);
   ALTER TABLE species ADD COLUMN identity_source VARCHAR(20);  -- "PLANTNET" | "AI" | "MANUAL"
   (imageUrl already exists on species — reuse it, do NOT add a second image column.)

3. Species entity + SpeciesResponse + SpeciesMapper: add the 5 new fields. Keep @Getter/@Setter/
   @Builder (NOT @Data).

4. Persist candidates: when the PlantNet identification completes, write the full candidate list
   to identifications.candidates (or the existing column from step 0). Add a DTO
   IdentificationCandidateDto mirroring the element shape above; expose candidates on
   IdentificationResponse so the frontend strip (T9.C) can read it. Do not change the existing
   preview behaviour — this is purely making the data durable.

5. Harvest at resolve time — the important change:
   In resolveSpecies(identificationId, confirmed=true), extend the findOrCreate path so the
   CONFIRMED candidate's fields are copied onto the Species row:
   - Overload SpeciesService.findOrCreate(scientificName, commonName, family, genus, imageUrl,
     imageAttribution, imageLicense). On CREATE: set all of them + identitySource="PLANTNET".
     On MATCH of an existing species that still has imageUrl == null: backfill image+taxonomy from
     this candidate (one-time fill; never overwrite a non-null existing image).
   - The confirmed candidate is the one the user accepted in the preview — pass its index/identity
     through resolveSpecies' request, don't re-guess "highest score" on the backend if the user
     could have picked "Not this one → choose differently".

6. Plant attachment (verify, fix only if broken): confirm resolvePlant() / saveFromIdentification
   sets plant.speciesId to the resolved species. If a plant created from this scan ends up with
   speciesId == null, fix that wiring here. This is what makes the species' "Plants" tab populate.

7. Tests (SpeciesServiceTest + the resolve-species test class):
   - findOrCreate(new) → species created with family/genus/imageUrl/attribution/license +
     identitySource="PLANTNET"
   - findOrCreate(existing, imageUrl already set) → image NOT overwritten
   - findOrCreate(existing, imageUrl null) → image backfilled once
   - resolveSpecies(confirmed) → confirmed candidate's fields land on the species; plant.speciesId set
```

**Verify:** Scan → confirm a brand-new species → `GET /api/v1/species/{id}` returns a real
`imageUrl`, `family`, `genus`, `identitySource:"PLANTNET"` **immediately**, before any enrichment
runs. The species' "Plants" tab lists the plant created from that scan. `mvn clean compile` + unit
suite green.

---

### T9.B — Per-field generation status + regenerate endpoints; narrow enrichment 🤖 AI
**Branch:** `feature/PP-0NN-async-text-status`
**Depends on:** T9.A (so enrichment can be narrowed to prose only). Confirms Decisions 3 & 4.

**Backend Claude Code prompt:**
```
// T9.B — Generation-status enum for async text + regenerate endpoints

Read SpeciesEnrichmentServiceImpl (T6.4), TreatmentServiceImpl.createTreatment() +
generateDiseaseDescription wiring, and the existing aiTaskExecutor config before starting.

1. New enum GenerationStatus { PENDING, READY, FAILED } (place in a shared package — both
   species and treatment use it).

2. Migration NNN+2_add_generation_status.sql:
   ALTER TABLE species   ADD COLUMN description_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
   ALTER TABLE treatments ADD COLUMN description_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
   (Existing rows default to PENDING; a one-off data step may set already-populated rows to READY —
   include it as a Liquibase <update> or leave PENDING and let regenerate sort them out. Your call;
   note which you chose in STATE.md.)

3. Species enrichment — rewrite enrich() per Decision 3 + 4:
   - Set description_status=PENDING at start (it already is, by default, on a fresh row).
   - SCOPE NARROWED: generate ONLY description + careOverview. Do NOT fetch imageUrl here anymore —
     identity+image now come from PlantNet (T9.A). Remove imageUrl from the enrichment prompt/holder.
   - On success: set fields + description_status=READY. (Stop using NEEDS_REVIEW as the failure
     signal for this — keep the SpeciesStatus enum for its existing meaning, but description
     liveness is now tracked by description_status.)
   - On ANY failure (single broad catch, still must never propagate): description_status=FAILED,
     fields stay null.

4. Treatment description — same treatment for generateDiseaseDescription:
   - Set description_status=PENDING when the Treatment is created.
   - On success: diseaseDescription set + description_status=READY.
   - On failure: description_status=FAILED, diseaseDescription stays null.

5. Expose description_status on SpeciesResponse and TreatmentResponse.

6. Regenerate endpoints (re-fire the async generation, flip status back to PENDING):
   - POST /api/v1/species/{id}/regenerate-description  → 202, SpeciesResponse
       Species is SHARED — no ownership check. Add a modest Bucket4j bucket (e.g. 5/hour) keyed by
       userId to stop a refresh-spam loop; reuse the existing per-user limiter pattern, do not add
       a global one. Returns immediately; status goes PENDING and the async job runs.
   - POST /api/v1/treatments/{id}/regenerate-description → 202, TreatmentResponse
       Ownership-checked (load Treatment → Plant → verify userId). Same fire-and-forget +
       PENDING flip.

7. Tests:
   - enrich success → description_status READY, fields set, imageUrl untouched (no longer fetched)
   - enrich AI-throws / malformed → description_status FAILED, no exception
   - regenerate-species → status PENDING, async fired; rate-limited path returns 429-style handling
   - treatment description failure → FAILED; regenerate-treatment ownership check (wrong user → 404)
```

**Verify:** Force an enrichment failure (e.g. point the text model at a bad key briefly) →
`GET /api/v1/species/{id}` shows `descriptionStatus:"FAILED"`, not a silent null pretending to be
pending. `POST .../regenerate-description` flips it back to PENDING and, on a good run, to READY.
Same for a treatment.

---

### T9.C — Species overview redesign: PlantNet hero + taxonomy + candidate strip + status-driven prose 🤖 AI
**Branch:** `feature/PP-0NN-species-overview-redesign`
**Depends on:** T9.A + T9.B backend merged & verified first.

**Frontend Claude Code prompt:**
```
// T9.C — Rebuild the Species detail Overview tab around real PlantNet data

Read species-detail.component.ts/html/scss (T6.6), species.model.ts, identification.service.ts,
and DESIGN_PROGRESS.md (use existing tokens — --radius-card, --shadow-card, mint/coral chips,
font-heading serif — do NOT invent new tokens) before starting.

1. species.model.ts: extend SpeciesResponse with family, genus, imageUrl, imageAttribution,
   imageLicense, identitySource, descriptionStatus. Add an IdentificationCandidate type and a
   getter for the source scan's candidate list (via IdentificationService — the candidates the
   species was resolved from, for the strip).

2. Hero: replace the empty gradient placeholder (current blank state in the screenshot) with the
   species imageUrl when present (real PlantNet photo), falling back to the existing illustrated
   placeholder ONLY when imageUrl is null. Show "family · genus" under the scientific/common name.
   Small attribution caption under the hero: "{imageAttribution} · {imageLicense}" when present
   (matches the cc-by-sa credit style already shown on the candidate card).

3. Candidate strip: render the candidate list as a horizontal photo row (same visual language as
   the identification preview card — thumbnail + author credit), so the "card that was very good"
   now lives on the species page too. Read-only here (no re-pick on the species page).

4. Description / careOverview — drive purely off descriptionStatus, NOT off null:
   - PENDING  → spinner + "Gathering care info for this species…", AND poll
     GET /api/v1/species/{id} every 3s (reuse the existing pollUntilComplete pattern from
     identification.service.ts — takeWhile status==='PENDING' inclusive, stop on READY/FAILED,
     timeout ~32s) so it resolves without a manual refresh.
   - READY    → render description + careOverview prose.
   - FAILED   → a small inline "Couldn't generate care info." + a "Retry" button calling
     POST /api/v1/species/{id}/regenerate-description, which flips back to PENDING (re-start poll).
   The key behavioural change: the PAGE is never blank — hero, taxonomy, and candidate strip are
   all present from PlantNet data immediately; only the prose paragraph has a loading/failed state.

5. Plants tab: unchanged data path (getPlants filtered by speciesId, T6.6), but confirm it now
   actually shows the plant(s) attached in T9.A. If empty when it shouldn't be, flag back to T9.A
   rather than papering over it on the frontend.

6. ng build + ng lint clean. Keep mat-tab-group (this page's existing pattern — the icon-button-bar
   is Plant/Treatment-page-only).
```

**Verify:** `/garden/species/:id` for a freshly-confirmed species shows a real photo, family ·
genus, and the candidate strip on first paint — no "Gathering info…" blocking the whole page.
Prose fills in via poll a few seconds later; if generation failed, a Retry button appears and works.

---

### T9.D — Treatment description: status-driven state + poll + retry 🤖 AI
**Branch:** `feature/PP-0NN-treatment-description-state`
**Depends on:** T9.B backend merged & verified first.

**Frontend Claude Code prompt:**
```
// T9.D — Make the treatment disease-description honest (fixes the stuck "Generating…" spinner)

Read treatment-detail.component.ts/html/scss (T6.12), treatment.model.ts, and
identification.service.ts's pollUntilComplete pattern before starting.

1. treatment.model.ts: add descriptionStatus to TreatmentResponse.

2. Overview section — drive the diseaseDescription block off descriptionStatus, not off null:
   - PENDING → keep the "Generating a description for this disease…" spinner, AND poll
     GET /api/v1/treatments/{id} every 3s (same takeWhile/stop/timeout shape as identification
     polling) so it actually resolves. This alone fixes the perpetual spinner for the common case
     where generation simply hadn't finished.
   - READY   → render diseaseDescription.
   - FAILED  → "Couldn't generate a description." + a "Retry" button calling
     POST /api/v1/treatments/{id}/regenerate-description → flips to PENDING → restart poll.

3. Do not touch the craft-plan / plan-section logic — only the description block changes.

4. ng build + ng lint clean.
```

**Verify:** Open the Black-Spot treatment from Image 1: if the description is mid-generation it now
fills in within seconds via poll; if it had failed, a Retry button appears and produces the
description. No path leaves a spinner running forever.

---

## Blast radius & sequencing

- **Order:** T9.A → T9.B (backend, can land in either order but B's enrichment-narrowing assumes
  A's PlantNet harvest exists) → then T9.C + T9.D (frontend, independent of each other, each needs
  its backend half merged first).
- **Migrations:** 3–4 new files (one conditional). All pure `ADD COLUMN` / nullable or
  defaulted — no rewrites of shipped tables, no data loss risk. Append to changelog-master.xml.
- **Touch list:** `com.plantpal.species` (entity/DTO/mapper/service/enrichment/controller),
  `com.plantpal.treatment` (service/controller/DTO), `com.plantpal.identification` (resolve-species
  + candidate persistence + IdentificationResponse), frontend `features/species` + `features/treatment`
  + the shared poll helper. No change to the Kafka pipeline, auth, or reminders.
- **Watch:** the DeepSeek-R1 `1-call/60s` cap is the most likely real cause of the original
  failures. Narrowing species enrichment to prose-only (T9.B) halves the AI calls per new species
  (no more image fetch), and the regenerate + status work means a transient 429 is now recoverable
  instead of permanent. If failures persist after this phase, that's the signal to switch the
  enrichment/description text model to `gpt-4.1-mini` (config-only) per the standing recommendation.
- **Test-coverage interaction with Phase 9:** these add new backend branches (status transitions,
  regenerate endpoints). Write their unit tests in-phase so they count toward the JaCoCo-80%
  restoration Phase 9 is doing, rather than landing as uncovered code right after it.

## Dual-maintenance reminder
Per decision D6, log the 5 decisions above (with real D-IDs) in **both** `ARCHITECT.md` and the
`plants-vault` (the relevant phase/decision pages), and add a Phase 9.5 page to the vault. Update
`STATE.md` and `TASK_PLAN.md` when the block is scheduled.


# Phase 9.5 — Addendum: T9.E (Task Step page) + T9.F (plant-edit back-button)

> Paste these into the Phase 9.5 section of TASK_PLAN.md: two new decisions under
> "Decisions needed", two new task blocks after T9.D, and the updated sequencing note.
> Same house rules as the rest of 9.5 apply (constructor injection, `ApiResponse<T>`,
> design tokens from DESIGN_PROGRESS.md, no `@Data` on entities, etc.).

---

## Decisions needed (additions)

**Decision 6 — How are plan steps addressed? (confirm-first, shapes the route)**
The Task Step page needs a stable URL. Do the steps of a care/treatment plan have
durable IDs, or are they a JSONB array addressed by index inside the plan?
→ **No recommendation — this is a fact to confirm, not a preference.** The agent must
grep the `TreatmentPlan` model first. If steps are JSONB-index-addressed, the route is
`.../steps/:stepIndex` and "mark done"/deep-links key off index; if they're entities
with IDs, it's `.../steps/:stepId`. **Everything in T9.E depends on this answer** — do
not let the agent guess.

**Decision 7 — Scope of "AI on the Mermaid".**
You described three AI entry points. Splitting them by risk:
(a) **Free question about the task** → existing chat with task context pre-injected. Ship in T9.E.
(b) **Tap a diagram node → ask AI about that substep** → seed a chat question from the node
label. Ship in T9.E (node labels are our own generated content, low injection risk, but
still sanitize before seeding).
(c) **AI regenerates / "improves" the Mermaid diagram** → **defer to Phase 10 beta, gated
on T9.8.** Mermaid generation is precisely the malformed-output failure the T9.8 eval suite
exists to catch; auto-replacing a working diagram with an unvalidated AI one before that
suite is trustworthy is backwards. When it does ship, it's *preview-then-apply*, never
auto-replace — a bad response shows an error, it never corrupts the stored step.
→ **Recommend (a)+(b) now, (c) deferred.** Logged below as a Phase 10 item.

---

## Tasks (additions)

### T9.E — Dedicated Task Step page (replaces the cramped popup) + AI deep-links 🤝 Assisted
**Branch:** `feature/PP-0NN-task-step-page`
**Depends on:** Decision 6 confirmed first. Reuses the sticky-hero + icon-bar page pattern
(T6.12 Plant/Treatment detail), the existing Mermaid renderer, and chat context injection (T4).
Independent of T9.A–T9.D — can land in parallel.

**Frontend Claude Code prompt:**
```
// T9.E — Move task/step detail out of the modal into a dedicated page.
// The popup is too small for the instructions + Mermaid; the diagram renders tiny.
// Both the Treatment detail page AND the Reminders flow open this popup today.

STEP 0 — confirm, don't assume (Decision 6):
- Grep TreatmentPlan (com.plantpal.reminder) + the care-plan JSONB shape. Determine
  whether steps have stable IDs or are JSONB array indices. REPORT before picking a route.
- Find the CURRENT popup component (likely a mat-dialog opened from treatment-detail AND
  from reminders). Note both call sites — both must be switched to navigation in step 5.
- Find the EXISTING Mermaid render component/directive used by care plans today — reuse it,
  do NOT add a second Mermaid integration.

1. New route + standalone component for a single plan step. Route keyed off plan + step ref
   per Decision 6 (e.g. /plans/:planId/steps/:stepRef). The page must work regardless of
   whether you arrived from a Treatment or a Reminder — load the plan by id, resolve the step
   by ref on ngOnInit from the route param (NOT from transient router state — see T9.F).
   Accept an optional ?returnUrl= so "back" returns to the originating screen deterministically.

2. Layout — use the sticky-hero + content pattern (consistent with Plant/Treatment detail,
   NOT a dialog), existing design tokens only:
   - Hero: step title + step number / "step N of M" + status chip.
   - Full instructions / description (the cramped part of the popup — give it room).
   - Mermaid diagram FULL-WIDTH (the whole point). For tall/wide diagrams add pan/zoom or
     horizontal scroll so it's legible on a 390px viewport. This is the fix for "tiny in the modal".
   - The step's mark-done / status action (must keep working — it was in the popup).

3. Tappable Mermaid nodes (Decision 7b): render nodes as tappable. Mermaid supports click
   bindings (securityLevel 'loose'); SANITIZE the node label before using it as a seed. Tapping
   a node opens the AI seeded with that node's content (step 4, node variant). If wiring node
   clicks proves brittle for a given diagram, degrade gracefully to a plain "Ask about a step"
   entry — never let a click-binding failure break diagram rendering.

4. AI access (Decision 7a/7b) — two entry points, both reuse existing chat:
   - "Ask about this task" → deep-link to chat with the plant context (?plantId=, existing T4
     mechanism) AND a pre-filled seed referencing this step's text. Lightweight: pre-fill the
     input + pass context, do NOT rebuild the chat context system.
   - Per-node "Ask about this step" → same, seeded with the tapped node's label.
   If the chat context injection can't yet carry a step reference, pass it as the seed message
   text rather than extending the backend context builder in this task (flag it if you think
   the backend seed is worth a follow-up).

5. Retire the popup: switch BOTH call sites (treatment-detail + reminders) from opening the
   dialog to router.navigate to the new page, passing returnUrl. Remove the dialog component
   if nothing else uses it; otherwise leave it unreferenced and note it for cleanup. Confirm
   no other caller depends on it before deleting.

6. ng build + ng lint + tsc --noEmit clean. Add a Vitest component test (T9.1 harness now
   exists): step page renders title + instructions + a mermaid container; mark-done calls the
   service; "Ask about this task" navigates to chat with the seed + plantId.

OUT OF SCOPE (Decision 7c — Phase 10): AI regenerating/replacing the diagram. Do not build a
"suggest a better diagram" action in this task.
```

**Verify:** Click a task in **both** the Treatment page and the Reminders flow → both open the
full page (no modal). The Mermaid is full-width and legible on a phone. Tapping a node opens the
AI seeded with that step. Mark-done still works. Back returns to where you came from.

---

### T9.F — Fix: plant edit → back → empty page 🤖 AI
**Branch:** `feature/PP-0NN-plant-edit-back-nav`
**Depends on:** nothing. Tiny, standalone — good warm-up task. Independent of all other 9.5 tasks.

**Frontend Claude Code prompt:**
```
// T9.F — Bug: editing a plant, then pressing Back, lands on an empty page.

1. Reproduce + diagnose (report findings before fixing):
   - What does the plant-edit component do on SAVE and on CANCEL? (router.navigate(['/plants', id])
     vs location.back() vs a navigate target missing the id.)
   - Does plant-detail load its data from the ROUTE PARAM on ngOnInit / paramMap, or does it rely
     on router state / nav extras that are lost when re-entered via Back?
   - Is the "empty page" the plant-detail route rendering with no data (failed/absent fetch,
     undefined id), or a genuinely different blank route in the history stack?
   - Is there a resolver/guard on plant-detail that silently no-ops on Back re-entry?

2. Fix (likely combination):
   - After a successful edit, navigate to the concrete /plants/:id route with { replaceUrl: true }
     so the edit page is removed from history and Back skips it, landing on detail.
   - Ensure plant-detail fetches from the route param (paramMap subscription or ngOnInit by id),
     not from transient state — so it populates correctly however it's reached, including via Back.

3. Regression test (T9.1 Vitest harness): navigate edit → save → assert lands on a populated
   plant-detail (data fetched by id), and that Back does not surface a blank route.
```

**Verify:** Edit a plant → save → press Back → you land on a populated plant detail page, never a
blank one. Cancel behaves the same.

---

## Sequencing (updated)

- **T9.F** is standalone and trivial — land it first as a warm-up; it touches only plant routing.
- **T9.E** depends only on Decision 6 (confirm step addressing) + reuse of the existing Mermaid
  renderer and chat deep-link. It's independent of the backend tasks T9.A–T9.D and can run in
  parallel with them.
- No new migrations and no backend changes for T9.E/T9.F (unless you choose to extend the chat
  context builder to carry a step reference — flagged as optional inside T9.E step 4). Pure
  frontend + routing.

## Deferred to Phase 10 (beta) — logged here so it isn't lost
- **AI-improved diagrams (Decision 7c).** A *preview-then-apply* "suggest a clearer diagram"
  action on the Task Step page: fire a vision/reasoning call, render the candidate Mermaid in a
  preview, let the user accept or discard — never auto-replace, malformed output shows an error
  only. **Gated on T9.8** (the AI eval suite + ActionPlanValidator-style output validation must be
  in place first, since this is the exact malformed-Mermaid failure mode that suite targets).
  Add as a Phase 10 task when scheduled.


## PHASE 10 — Launch Preparation  🟡 NOT STARTED (was Phase 5)
> Goal: deploy to production, beta test, release v1.0.0. **Runs last** — after
> Phase 8 (PlantNet) and Phase 9 (Quality) so we launch a tested, observable,
> hardened build. Branch numbers below are placeholders — verify next free PP.

### T10.1 — Production configuration 🤖 AI
**Branch:** `feature/PP-065-prod-config`
```
// Generate staging + prod Spring Boot configs.
1. application-staging.yml + application-prod.yml: ${DATABASE_URL}, HikariCP
   (max 20/min 5/timeout 20000), ${REDIS_URL}, Liquibase enabled, show-sql false,
   logging INFO com.plantpal / WARN spring / JSON, Actuator health+info only,
   JPA ddl-auto=validate, CORS from ${ALLOWED_ORIGINS}.
2. logback-spring.xml: JSON in staging/prod, pattern in dev, MDC correlationId on
   every line (timestamp, level, correlationId, userId, message, exception). This
   correlationId is what T9.6's Sentry integration links against.
3. app.rate-limit.* config (ai-calls-per-hour 20, auth-attempts-per-minute 5,
   chat-messages-per-hour 10).
```

### T10.2 — Performance optimizations 🤝 Assisted
**Branch:** `feature/PP-066-performance`
```
1. Migration (VERIFY next free number against the changelog — sequence is well past
   the stale "019/020" the old prompt assumed; Phase 8 reserves 023–025/026):
   composite idx identifications(plant_id, created_at DESC); partial idx
   reminders(next_due_at) WHERE enabled (verify not already in 004); idx
   care_logs(user_id, performed_at DESC); idx treatments(plant_id, status) +
   species(scientific_name) if not already covered (check 016/018).
2. @Cacheable on hot reads not already cached: ChatServiceImpl.buildGardenContext
   ("garden::{userId}", 5min), SpeciesServiceImpl.getSpecies ("species::{id}",
   10min); @CacheEvict on mutations.
3. Angular: verify lazy-loading (ng build --stats-json), OnPush on list components,
   trackBy on *ngFor. (Lighthouse budgets from T9.3 now guard this.)
```

### T10.3 — Security hardening 🤖 AI
**Branch:** `feature/PP-066-performance` (same branch)
```
1. Security headers in SecurityConfig: X-Content-Type-Options nosniff, X-Frame-
   Options DENY, X-XSS-Protection, HSTS (prod only), basic CSP for own origins.
2. Bucket4j on AuthController: login 5/min/IP, register 3/hour/IP, 429 + Retry-After.
3. Input sanitization: plant nickname/notes/location strip HTML (OWASP Java HTML
   Sanitizer); chat messages max 2000 chars at controller level. (Prompt-injection
   handling is separate — T9.8.)
4. (OWASP Dependency-Check now lives in T9.7 — do not duplicate here.)
5. Confirm the GITHUB_TOKEN rotation from T9.7 actually happened before prod.
```

### T10.4 — Complete API documentation 🤖 AI
**Branch:** `feature/PP-066-performance` (same branch)
```
Add @Operation/@ApiResponse/@Parameter to every controller (Plant, Identification,
Reminder, TreatmentPlan, Chat, Auth, CareLog, Notification, Species, Treatment,
Dashboard, Photo + new PlantNet proxy/quota endpoints from Phase 8). @ApiResponse
for 200/201/400/401/403/404/429/500; @Schema examples on DTOs. OpenApiConfig:
title "PlantPal API" v1.0.0, JWT authorize button, dev + prod server URLs.
```

### T10.5 — Production deployment 👤 Manual
Railway (Postgres + Redis add-ons, env vars from `.env.example`, auto-deploy on
`main`) + Vercel (Angular build, prod API URL). Verify `/actuator/health` UP and
the app loads + login works.
> **Decide first:** Kafka/Zookeeper production story — managed add-on, or fall back
> to synchronous identification for v1.0.0 (revisit async later). Still open.

### T10.6 — Beta testing 👤 Manual
5–10 plant owners. Full journey, disease path, mobile (Chrome Android + **Safari
iOS** — now also covered by T9.2's WebKit E2E), PWA install (closes the rest of
**T3.3**), chat. Collect confusing UX, bad IDs, missing features.

### T10.7 — Beta bug fixes 🤝 Assisted
Per bug: `bugfix/PP-{N}` from `dev`, **add a failing test first** (now you have the
frontend + E2E harness to put it in — T9.1/T9.2), then fix, PR with root cause.

### T10.8 — Release v1.0.0 👤 Manual
`release/v1.0.0` from `dev`, `mvn versions:set 1.0.0`, CHANGELOG, merge `--no-ff`
to `main`, tag `v1.0.0`, merge back to `dev`, delete release branch.

---

## Status Summary

| Phase | Status |
|---|---|
| 0 — Setup | ✅ Done |
| 1 — Auth + Plants | ✅ Done |
| 2 — AI Identification | ✅ Done |
| 3 — Reminders | ✅ Done except T3.3 (on-device testing — folded into Phase 9/10) |
| 4 — Chat | ✅ Done (streaming + history shipped) |
| 6 — Species & Treatment Restructure | ✅ Done |
| 7 — Model Control, Batch, Multi-Treatment | ✅ Done |
| 8 — PlantNet First-Class Provider | ✅ Done — T8.0–T8.7 all merged to dev |
| 8.5 — Identification Pipeline Resilience | ✅ Done — T8.A–T8.G all merged to dev |
| 9 — Quality, Testing & Hardening | 🔲 Not started (T9.1–T9.8, PP-057–064) |
| 10 — Launch | 🔲 Not started (was Phase 5, PP-065+) |

---

## Enterprise Patterns Checklist
Final audit before launch — re-check every item against the real code.

Backend / data:
- [ ] All list endpoints paginated (`Pageable`)
- [ ] All deletes are soft deletes (`status = ARCHIVED`)
- [ ] Audit fields on all entities (except documented exceptions — CLAUDE.md Hard Rules)
- [ ] Redis cache on all hot read paths
- [ ] Rate limiting on all AI + auth endpoints
- [ ] Testcontainers ITs wired into `mvn verify` *(→ T9.5)*
- [ ] JaCoCo gate restored to 80% (currently 55%) *(→ T9.5)*
- [ ] Raw AI responses stored for reprocessing
- [ ] `ResourceNotFoundException` never reveals existence vs. ownership
- [ ] Kafka/Zookeeper production story decided *(→ T10.5)*

Security / supply chain:
- [ ] All secrets in env vars — **GITHUB_TOKEN rotated** *(→ T9.7)*
- [ ] Secret scanning (gitleaks) in CI *(→ T9.7)*
- [ ] Dependency scanning — OWASP dep-check + Dependabot/Renovate + Trivy *(→ T9.7)*
- [ ] Security headers on all responses *(→ T10.3)*
- [ ] Input sanitization + prompt-injection guardrails *(→ T10.3 / T9.8)*

Quality / observability:
- [ ] Frontend component/unit tests exist *(→ T9.1 — currently zero)*
- [ ] E2E critical journeys (Playwright, incl. WebKit) *(→ T9.2)*
- [ ] Visual regression + a11y + Lighthouse budgets gating CI *(→ T9.3)*
- [ ] Error tracking (Sentry) + correlation IDs *(→ T9.6 / T10.1)*
- [ ] Structured JSON logging in prod *(→ T10.1)*
- [ ] AI eval suite (parse + shape, nightly) *(→ T9.8)*

Docs / ops:
- [ ] Swagger documents every endpoint with examples *(→ T10.4)*
- [ ] Docker + docker-compose for reproducible local dev
