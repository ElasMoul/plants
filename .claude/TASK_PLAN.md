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

## PHASE 8 — PlantNet as a First-Class Identification Provider  🟡 PLANNED
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

### T8.1 — Backend: PlantNet v2 client — ranked candidates, organs, reference images, quota 🤖 AI
**Branch:** `feature/PP-043-plantnet-v2-client` · Depends on **D1**, **D2**.

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

### T8.2 — Backend: ranked-candidate species matching (Flow 1) wired to PlantNet 🤖 AI
**Branch:** `feature/PP-044-plantnet-species-match`

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

### T8.3 — Frontend: multi-candidate species confirmation UI (confidence + reference images) 🤝 Assisted
**Branch:** `feature/PP-045-species-confirm-candidates`

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

### T8.4 — Both: organ tagging + geolocation-ranked flora & common-name language 🤝 Assisted
**Branch:** `feature/PP-046-plantnet-organs-projects`

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

### T8.5 — Backend: PlantNet disease/pest cross-check feeding the Treatment flow 🤖 AI
**Branch:** `feature/PP-047-plantnet-disease-crosscheck` · Depends on **D4**. Heaviest — sequence last.

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

### T8.6 — Backend: factual species enrichment (IUCN/POWO free; GBIF deeper) 🤖 AI — secondary
**Branch:** `feature/PP-048-factual-species-enrichment` · Depends on **D3**.

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

### T8.7 — Frontend: PlantNet quota + provider telemetry 🤝 Assisted — small
**Branch:** `feature/PP-049-plantnet-quota`

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

## PHASE 9 — Quality, Testing & Hardening  🟡 PLANNED
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
**Branch:** `feature/PP-050-frontend-test-runner` (verify next free PP)

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
**Branch:** `feature/PP-051-playwright-e2e` (verify)

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
**Branch:** `feature/PP-052-visual-a11y-perf` (verify)

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
**Branch:** `feature/PP-053-ai-visual-review` (verify)
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
**Branch:** `feature/PP-054-backend-test-gates` (verify)

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
**Branch:** `feature/PP-055-error-tracking` (verify)

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
**Branch:** `feature/PP-056-supply-chain-scanning` (verify)

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
**Branch:** `feature/PP-057-ai-evals` (verify)

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

## PHASE 10 — Launch Preparation  🟡 NOT STARTED (was Phase 5)
> Goal: deploy to production, beta test, release v1.0.0. **Runs last** — after
> Phase 8 (PlantNet) and Phase 9 (Quality) so we launch a tested, observable,
> hardened build. Branch numbers below are placeholders — verify next free PP.

### T10.1 — Production configuration 🤖 AI
**Branch:** `feature/PP-058-prod-config`
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
**Branch:** `feature/PP-059-performance`
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
**Branch:** `feature/PP-059-performance` (same branch)
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
**Branch:** `feature/PP-059-performance` (same branch)
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
| 8 — PlantNet First-Class Provider | 🟡 In progress — T8.0 ✅ done; T8.1–T8.7 planned; D1–D4 open |
| 9 — Quality, Testing & Hardening | 🟡 Planned (T9.1–T9.8) |
| 10 — Launch | 🟡 Not started (was Phase 5) |

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
