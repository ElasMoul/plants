# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-06-22 (post-T7.1-followup bugfixes: craft-plan/disease-
> description rate-limit handling, model preference save + actual vision/
> reasoning wiring, PlantNet restored to the picker, scan-result treatment CTA
> scoping, species list photo fallback — see new section below; older entries
> are from prior sessions)

## Bugfixes — T7.1 follow-up: rate limiting, model preference wiring, PlantNet, scan CTA, species photos (2026-06-22)
Five user-reported bugs against the just-shipped T7.1/T7.2 Model Control work,
fixed in one session. **Uncommitted** — currently sitting on
`feature/PP-041-batch-scan` (the batch-scan branch from the prior session);
these are unrelated to batch scanning and need their own branch before
committing, not yet done as of this writing. All backend unit tests (71
classes/nested groups, 0 failures) and `ng build`/`ng lint`/`tsc --noEmit`
pass clean.

**1. Craft-plan failing + disease description never generating.** Root cause
was the SAME upstream constraint hitting two different code paths:
GitHub Models/Azure enforces "1 DeepSeek-R1 call per 60s per user per model"
— `Treatment.createTreatment()` fires disease-description generation
immediately, so clicking "Craft Treatment Plan" within ~60s of creating the
treatment collided with that cap. `DeepSeekClient` laundered every upstream
429 into a generic 503 with zero retry, so the collision was unrecoverable
and silent (the async disease-description path just logged a warning and
left the field null forever — no retry, nothing re-triggers it later).
  - Fix: `DeepSeekClient` now classifies a 429 specifically — parses
    `Retry-After` header or the "wait N seconds" text in the error body
    (`extractRetryAfterSeconds()`), falls back to 60s — and throws the
    existing `RateLimitException` (carries `retryAfterSeconds`) instead of a
    generic 503. This reuses T7.2's already-built frontend rate-limit UX
    (actionable snackbar with accurate wait time) for free; new shared
    `toServiceException()` helper collapses the 5 near-identical catch blocks
    that used to exist across `generateCureAdvice`/`generateDiseaseDescription`/
    `generateSpeciesEnrichment`/`generateCarePlan`/`detectDuplicateCareCards`.
  - `craftPlan()` stays fail-fast (no retry) since its controller blocks the
    HTTP thread on `.get()` — sleeping there would hang the request for up to
    a minute. `generateAndSaveDiseaseDescription()` (genuinely fire-and-forget,
    no caller waiting) now does ONE bounded retry after the suggested wait
    (capped at 65s) on `RateLimitException` specifically — this is what
    actually fixes "description never generated".
- **2 & 3. Model preference save 400'ing + vision/reasoning choice doing
  nothing.** `UserPreferencesRequest.aiModelPreference` still had `@NotNull`
  from before the T7.1 vision/reasoning split, but the current frontend
  (`UserService.updateModelPreferences()`) only ever sends
  `visionModelPreference`/`reasoningModelPreference` — every save 400'd
  (`ne doit pas être nul`). Separately, T7.1 had left both new preference
  fields storage-only: `IdentificationServiceImpl.runIdentification()` still
  branched exclusively on the legacy `AiModelPreference`, and nothing read
  `reasoningModelPreference` anywhere — switching the picker visibly saved
  but never changed which model actually ran.
  - Fix: dropped `@NotNull` from the deprecated field +
    `UserServiceImpl.updatePreferences()` now only overwrites it when present
    (matches the pattern already used for the two new fields). `Identification
    ServiceImpl` now has separate `loadVisionPreference()`/
    `loadReasoningPreference()` reading the real columns;
    `runIdentification()`'s switch is retyped from `AiModelPreference` to
    `VisionModelPreference` (with a defensive `parseVisionPreference()`
    fallback to GITHUB_GPT4O for stale/legacy event payloads, e.g. the old
    "DEEPSEEK" value, which was never a real vision model anyway).
    `getCureAdvice()`, `TreatmentServiceImpl.craftPlan()`, and
    `fireDiseaseDescriptionGeneration()` all now branch on
    `ReasoningModelPreference` between `deepSeekClient`/`ollamaClient` — added
    `OllamaClient.generateCureAdvice()`/`generateDiseaseDescription()` (same
    reuse-DeepSeekClient's-prompt-constant pattern as the pre-existing
    `generateSpeciesEnrichment()`) to give Ollama parity. Species enrichment's
    `AiModelPreference` parameter was deliberately left alone (no signature
    change, avoids rippling through tests) — `resolveSpecies()` now just maps
    the real `ReasoningModelPreference` onto it via a tiny
    `toLegacyReasoningPreference()` helper instead of reading the stale field.
    `TreatmentServiceImpl` gained `UserRepository`+`OllamaClient`
    constructor dependencies (test file updated to match).
- **4. PlantNet missing from the vision picker.** Was never actually deleted
  backend-side (`PlantNetClient` is still a live `@Component`, still wired
  into `runIdentification()`'s `PLANTNET` case) — T7.1 just narrowed the new
  `VisionModelPreference` enum/picker to GITHUB_GPT4O/OLLAMA_LLAVA without
  carrying it forward. Re-added `PLANTNET` to the backend enum, the frontend
  `VisionModelPreference` type, and `model-selector.component.ts`'s
  `VISION_OPTIONS` (tooltip notes it's species-ID-only — PlantNet's result
  always sets `healthStatus: UNKNOWN` and has no care plan).
- **5. "Start treatment plan" removed from the scan result, kept on Plant/
  Species pages.** The button lives in the *shared* `care-card.component`
  (used by the scan-result preview, Plant Detail's Care section, AND Species
  Detail) — a blanket removal there would have silently removed it from the
  other two pages too, which the user didn't ask for and which are the
  Plant page's only "start a treatment" entry points outside its separate
  Scans-tab CTA. Added `CarePlanComponent`/`CareCardComponent` `@Input()
  showTreatmentCta = true` (default preserves existing behavior everywhere),
  threaded through to the button's `*ngIf` and to skip the now-pointless
  `checkActiveTreatment()` HTTP call when hidden; `preview-card.component.html`
  (the scan-result page) is the only caller passing `[showTreatmentCta]="false"`.
  "Add to care plan" (`disease-detail-panel.component`) is a separate
  component, untouched, still on the scan-result page.
- **Follow-up correction:** the scan-result "Start treatment plan" removal (item 5) was reverted
  same-session — `preview-card.component.html` no longer passes `[showTreatmentCta]="false"`, so
  the button is back on the scan-result care-card too. The `showTreatmentCta` `@Input` plumbing
  itself was left in place (harmless default-`true`, no current caller sets it `false`) in case a
  future ask is scoped more precisely.
- **Follow-up bug found via live testing: cure-advice "powered by" badge always said DeepSeek,
  even when Ollama actually generated it.** `IdentificationServiceImpl.addCareCard()` hardcoded
  `actionPlanModel(ReasoningModelPreference.DEEPSEEK_R1.name())` unconditionally — no way to know
  which model the earlier `/cure-advice` call actually used. Fixed by threading it through end to
  end: `CureAdviceResponse.reasoningModelUsed` (set from the real `ReasoningModelPreference` in
  `getCureAdvice()`/`parseCureAdvice()`); `AddCareCardRequest.reasoningModelUsed` (nullable, falls
  back to DEEPSEEK_R1 for older/direct API callers); `IdentificationService.addCareCard()` gained a
  5th param; `DiseaseDetailPanelComponent.addToCarePlan()` passes its already-cached
  `reasoningModelUsed` (the frontend's `getCureAdvice()` return type had `reasoningModelUsed?:
  string | null` typed the whole time, just never populated backend-side until now). **Session
  note:** this was built, reverted on user feedback ("you made it worse"), then explicitly
  re-requested ("keep model used to generate description") and re-applied unchanged in the same
  session — the revert wasn't about this fix being wrong, just a moment of back-and-forth: keep
  this fix as a real, intentional fix in any future session, not as one in question.
- **Root cause of the "revert" confusion, found via live testing: cure advice showing raw JSON
  instead of clean text, only for Ollama.** Ollama/llava-phi3 doesn't reliably emit the single
  combined JSON object `CURE_ADVICE_SYSTEM_PROMPT` asks for — observed emitting TWO sibling
  top-level objects back to back instead: `{"advice":"..."}{"actionPlan":{...}}}`. GitHub
  Models/DeepSeek-R1 always emits the correct single-object shape, so this was never hit before
  the reasoning-preference wiring above made Ollama actually reachable for cure advice — it's a
  latent bug this session's other fix exposed, not something the model-badge fix itself caused
  (the user's "abort" request named the wrong commit; the actual regression was the JSON shape).
  `objectMapper.readValue(raw, CureAdviceJson.class)` throws on that shape, and the existing
  fallback dumped the still-JSON-ish raw text as "advice" — ugly and exactly what was reported.
  Fix: new `com.plantpal.identification.util.LenientJsonParser.mergeConcatenatedObjects()` —
  reads a SEQUENCE of root-level JSON values from the raw text via
  `objectMapper.readValues(parser, JsonNode.class)` (a real Jackson feature for exactly this:
  multiple concatenated top-level values) and merges their fields into one node. Wired into both
  `IdentificationServiceImpl.parseCureAdvice()` (cure-advice display) AND
  `TreatmentServiceImpl.parseActionPlan()` (craft-plan) — same prompt, same vulnerability, same
  fix, both call sites can now route to Ollama. Only engages on the existing
  `JsonProcessingException` catch path (zero behavior change for the normal single-object case);
  if the merge itself fails to produce a parseable result, both call sites fall back to their
  original behavior unchanged (raw text / null actionPlan).
- **Bonus: species list images.** `SpeciesSummaryDto.imageUrl` only ever came
  from AI enrichment (`Species.imageUrl`, frequently null). `SpeciesServiceImpl
  .toSummary()` now falls back to the first non-null `photoUrl` among that
  species' plants — zero new queries, the plant list was already batch-fetched
  for health/count. Scoped to `/species/mine` only (already viewer-owned, no
  cross-user query/privacy question); `/species/{id}` (the public single-
  species endpoint) still shows only the species' own `imageUrl` if a future
  session wants the same treatment there.

## Current Phase
**Phases 0–4, 6, and 7 are now fully shipped, plus a pre-Phase-5 cleanup pass
closing every gap flagged below. Phase 5 (Launch prep) is the only phase that
hasn't started** — see TASK_PLAN.md for task breakdowns. One manual item is
stranded from Phase 3: T3.3 (on-device push notification + PWA testing) still
needs a real phone and hasn't been done.

| Phase | Status |
|---|---|
| 0 — Setup | ✅ Complete |
| 1 — Auth + Plant Management | ✅ Complete |
| 2 — AI Plant Identification | ✅ Complete |
| 3 — Reminders + Care Plans | ✅ Complete except T3.3 (manual device testing) |
| 4 — AI Chat | ✅ Complete, incl. streaming + conversation history |
| 5 — Launch prep | 🔲 Not started ← **current focus** |
| 6 — Species & Treatment Domain Restructure | ✅ Complete (T6.1–T6.14, incl. T6.7/T6.8 which had shipped but were previously undocumented here) |
| 7 — Model Control, Batch Scanning, Multi-Treatment UX | ✅ Complete (T7.1–T7.4) |
| — Pre-Phase-5 cleanup pass | ✅ Complete (`feature/PP-038-pre-phase5-cleanup`) |

## Bugfixes — batch scan: lost items, NullInjectorError, sequential trickle (2026-06-22, `feature/PP-041-batch-scan`)
Three user-reported issues against T7.3's just-shipped batch scan, found via
live testing, all fixed in the same session:

**1. Lost items on dialog close/navigation.** Clicking Cancel mid-batch (or
navigating away) only completed the one item already in flight — every other
queued item was silently abandoned. Root cause: `runBatchQueue()` lived
directly in `IdentificationUploadDialogComponent`, piped through
`takeUntil(this.destroy$)` — closing the dialog destroyed the component,
unsubscribing the chain and killing both the in-flight HTTP call and every
not-yet-started item. Fix: moved the entire queue into a new
`BatchScanService` (`features/identification/services/batch-scan.service.ts`)
with no `takeUntil` tied to the dialog at all. `BatchItem` uses a stable
numeric `id` (not object identity) for its immutable `BehaviorSubject`
patches, since identity-based matching breaks the moment an item is replaced
by a new object reference on its first patch.

**2. `NullInjectorError` opening the dialog from Garden/Home/Plant pages.**
`BatchScanService` was initially only listed in `identification.module.ts`'s
`providers:`. Since `IdentificationUploadDialogComponent` is opened from 3
*other* lazy modules too (`plant`/`species`/`dashboard`) without a
`viewContainerRef`, and a module-scoped provider only resolves within the
injector subtree of modules that explicitly list it, any entry point routed
through those other modules hit a hard crash the instant the dialog tried to
construct. **This also disproved an earlier (wrong) assumption written into
FRONTEND.md** that Angular resolves a dialog's DI from its *declaring*
module — it never did; `IdentificationService` only ever worked across all 4
entry points by coincidence, because every one of those modules already
independently provides it for its own unrelated needs. Fix: added
`BatchScanService` to the `providers:` array of all 4 modules
(`identification`/`plant`/`species`/`dashboard`) — corrected FRONTEND.md
accordingly. (A `providedIn: 'root'` service would not have fixed this
either — it'd be constructed via the root injector alone, with no path to
its own module-scoped `IdentificationService` dependency.)

**3. Items only appeared one at a time, in sequence.** The queue was
deliberately sequential — submit one file, wait for its full poll-to-
completion, only then submit the next — reasoning at the time was "avoid
wasting calls if a 429 hits partway through." In practice this meant the 2nd
identification didn't even exist on the backend (so couldn't show up
PENDING anywhere it's listed, e.g. the identify page) until the 1st had
fully resolved. Fix: all N items' `/analyze` calls now fire concurrently at
batch start; each is polled to completion independently. Safe given the
5-image batch cap vs. the 20/hour identification rate limit — a 429 on any
one item still just fails that item via `AiErrorService`, same as before.

`IdentificationUploadDialogComponent` is now a thin view over
`BatchScanService`: `batchActive` (`items.length > 0`) drives the view,
covering "batch finished but the user reopened the dialog" too — reopening
shows the same Done/Failed rows instead of a blank upload form. Buttons:
"Run in background" while running (does NOT cancel — no abort affordance
exists, by design), "Close"/"Retry remaining"/"Scan more"/"Done" once
finished. `BatchScanService.notifyIfDone()` shows a snackbar with the final
tally ("3 added, 1 failed") and a "View" action to `/identify`, regardless of
whether the dialog is even open when the batch finishes.
`ng build`, `ng lint`, `tsc --noEmit` all clean.

## T7.4 — Frontend: multi-treatment picker + disease-description poll fix (2026-06-22, `feature/PP-041-batch-scan`)
Full detail in FRONTEND.md — summary here for cross-session context. Built in
the same branch as T7.3 (per session instruction), not its own
`feature/PP-042-multi-treatment-picker` branch from the original task plan.
- **Discovery before building:** the multi-treatment picker (item 1 of the
  task prompt) and its wiring into `plant-detail.component.ts`'s icon-bar
  "treatment" CTA (item 2's first call site) had *already shipped* — in the
  `c38d1da "cleaning ..."` commit, which predates this Phase 7 work and
  predates the T7.1 investigation notes that assumed neither existed yet.
  `ActiveTreatmentSelectSheetComponent` and `goToActiveTreatment()` (plural
  `getActiveTreatments()`, picker on >1 result) were already correct and
  unchanged in this task — did not rebuild or rename them.
- What was actually still broken and got fixed:
  - `care-card.component.ts`'s `checkActiveTreatment()` (task prompt's second
    named call site) still used the singular `getActiveTreatment()` matched
    against a single most-recent treatment — switched to
    `getActiveTreatments()` + `.find(t => t.diseaseName === card.title)`. No
    picker needed here (the card's disease name already disambiguates which
    treatment it's about); just correct lookup logic.
  - `plant-detail.component.ts`'s *other* active-treatment check,
    `checkActiveTreatment(diseaseName)` (drives the disease-detail-panel's
    "Treatment in Progress" state) — same singular-vs-plural bug, not named
    explicitly in the task prompt but identical root cause sitting right next
    to the call site that was named; fixed the same way for consistency
    rather than leaving a known-buggy sibling unfixed.
  - `TreatmentService.getActiveTreatment()` (singular) deleted — confirmed
    zero remaining callers after the two fixes above.
- Enhanced `ActiveTreatmentSelectSheetComponent` to match the task prompt's
  fuller spec (it pre-existed but was simpler): added a real status chip
  (same `.status-chip`/`.status-{draft,in_progress,completed,dismissed}` CSS
  as `treatment-detail.component.scss`, duplicated locally per the app's
  existing per-page-chip-CSS convention, not extracted into a shared
  component), `startedAt` date, and a scan thumbnail fetched per-row via
  `IdentificationService.getById(identificationId)` when present (falls back
  to the placeholder image).
- `TreatmentDetailComponent` now polls after `loadTreatment()` when
  `status === 'DRAFT' && diseaseDescription == null` — `pollForDescription()`
  mirrors `IdentificationService.pollUntilComplete()`'s convention (3s
  interval via `takeWhile`+`filter`+`take(1)`, `timeout()` bound at 30s so a
  silently-failed generation — see `TreatmentServiceImpl`'s
  catch-and-log-null path — doesn't poll forever; the pending UI just stays
  showing if it times out, no error state).
- `ng build`, `ng lint`, `tsc --noEmit` all clean.

## T7.3 — Frontend: multi-select batch scan mode (2026-06-22, `feature/PP-041-batch-scan`)
Full detail in FRONTEND.md — summary here for cross-session context. No
backend changes — `POST /analyze` was already async (Kafka) and already
rate-limited per user before publish, so batch mode is just N independent
calls to the existing endpoint from the frontend.
- `PhotoUploadComponent` gained a "Scan multiple plants" checkbox and a new
  `lockedSpeciesId` input (parity with the existing `lockedPlantId`) —
  checkbox only renders when `batchModeAvailable` (`lockedPlantId == null &&
  lockedSpeciesId == null`), gating on *context*, not on which page opened
  the dialog. In practice the plain Garden FAB is the main case this affects,
  but other no-context entry points (Home's quick-action, the legacy
  `/plants` list page) get it too — a deliberate generalization of the task
  prompt's "Garden entry point only" framing to the actual condition it was
  describing, not a literal page allowlist.
- When batch mode is on: the per-entry organ select and the "link to existing
  plant" dropdown are hidden (don't apply to N independent new plants).
- `IdentificationUploadDialogComponent` now injects `IdentificationService`
  and `AiErrorService` directly (works because both are provided in
  `identification.module.ts`, where this dialog is declared — confirmed
  empirically via the pre-existing `PhotoUploadComponent`→`PlantService`
  injection already working the same way across all 6 entry-point modules
  that open this dialog without passing a `viewContainerRef`). In batch mode,
  `startIdentification()` no longer emits a single combined payload; it
  drives its own sequential queue (`runBatchQueue()`) — one `/analyze` call
  per file, `pollUntilComplete()` per item (reusing T2's polling), a status
  row per item (Pending/Scanning/Done/Failed) using `AiErrorService.handle()`
  for the failure message. A 429 partway through does not halt the queue —
  every item is still attempted and gets its own status, with a "Retry
  remaining" button once the batch finishes if any failed. The dialog stays
  open until every item resolves or the user cancels; closing unsubscribes
  the in-flight chain via the component's own `destroy$`.
- `ng build`, `ng lint`, `tsc --noEmit` all clean.
- **Not done in T7.3**: no "view results" deep-link after a batch finishes —
  closing just closes the dialog; the new plants show up wherever
  identifications/plants normally appear (garden list, identify list) on
  their own. Out of scope per the task prompt's minimal ask.

## T7.2 — Frontend: model picker, error + rate-limit UX, "powered by" badges (2026-06-22, `feature/PP-040-model-control-frontend`)
Full detail in FRONTEND.md — summary here for cross-session context. Built
directly against T7.1's already-merged backend contract (verified field names
against the actual DTOs before wiring, not assumed from the task prompt).
- `ModelSelectorComponent` now renders two dropdowns (vision/reasoning) bound
  to `visionModelPreference`/`reasoningModelPreference`; `UserService` gained
  `updateModelPreferences()` replacing the old single-field updater.
- New `AiErrorService` (core, root-provided like `UserService`) is the one
  place that turns an `HttpErrorResponse` into UI: a 429 reads
  `retryAfterSeconds` off the response body and shows an actionable snackbar
  ("Rate limit reached — try again in {time}, or switch your AI model in
  Settings") routing to the new `/preferences` page; anything else surfaces
  the real backend `message`. Wired into identification submit, cure advice,
  treatment create+craft-plan, and chat send — replacing four different
  hand-rolled `mapError()`/generic-toast patterns.
- New `/preferences` route (`features/preferences/`) — **didn't exist
  before**; the model picker was toolbar-only. Linked from the user account
  menu ("AI Model Settings"). This is also where the rate-limit snackbar's
  action button routes to.
- New shared `ModelUsageBadgeComponent` ("powered by" caption, renders
  nothing if both inputs are null/undefined — safe to drop in ahead of data)
  wired into 5 surfaces reading T7.1's migration-022 fields: identification
  preview (`visionModelUsed ?? aiModelUsed` + `reasoningModelUsed`),
  disease-detail-panel cure advice, care-card (`actionPlanModel`),
  treatment-detail (`diseaseDescriptionModel` + `treatmentPlanModel`),
  species-detail (`enrichmentModel`).
- `ng build`, `ng lint`, `tsc --noEmit` all clean.
- **Not done in T7.2**: cure-advice's backend response (`CureAdviceResponse`)
  has no model-usage field yet — the disease-detail-panel badge is wired and
  will light up once one's added, but won't show anything today. Also still
  open from T7.1: no service actually reads
  `visionModelPreference`/`reasoningModelPreference` to pick an AI client —
  the new `/preferences` page lets a user set them, but until that wiring
  exists, switching models there doesn't change which model actually runs.

## T7.1 — Backend: Model Control + Structured AI Errors (2026-06-22, `feature/PP-039-model-control-backend`)
Full detail in BACKEND.md's "T7.1" section — summary here for cross-session
context. Landed on its own branch, merging straight to `dev` (no frontend
coupling needed for this part — T7.2 reads the new preference fields and
`retryAfterSeconds`/model-usage fields independently).
- Split AI model choice into `visionModelPreference`/`reasoningModelPreference`
  (migration 021, additive — old `aiModelPreference` kept, deprecated).
- Removed every silent cross-model fallback: `IdentificationServiceImpl`'s
  OLLAMA_LLAVA→GITHUB_GPT4O retry, and `DeepSeekAnnotationClient`'s
  429→Ollama fallback + empty-regions-on-exhaustion swallow. Both now
  propagate a real `PlantPalException`/429 instead.
- `GlobalExceptionHandler` now resolves the actual HTTP status from
  `PlantPalException.getErrorCode()` (was hardcoded to 500) — existing 429s
  reach the frontend correctly for the first time.
- New `RateLimitException` (carries `retryAfterSeconds` via Bucket4j's
  `ConsumptionProbe`) wired at 3 sites, including `TreatmentServiceImpl
  .craftPlan()` which had no rate limit at all before this.
- Model-usage tracking fields added across `CarePlanDto`, `CareCardDto`,
  `TreatmentResponse`, `SpeciesResponse` (migration 022) — these are what
  T7.2's "powered by" badges read.
- 198/198 unit tests pass (one test rewritten to match the no-fallback
  behavior); `mvn spotless:apply` clean.
- **Not done in T7.1**: no service yet actually reads
  `visionModelPreference`/`reasoningModelPreference` to pick a client — they're
  readable/writable via the API but not yet load-bearing. That wiring is
  follow-up work, naturally sequenced after T7.2 ships a UI to set them.

## Pre-Phase-5 Cleanup Pass (2026-06-21)
Closed every gap BACKEND.md/FRONTEND.md had flagged as open, plus three bugs
found via live testing, on one branch (`feature/PP-038-pre-phase5-cleanup`,
backend and frontend changes kept in separate commits):
- **Reminder double-completion**: a one-time reminder (e.g. a treatment step)
  could be completed more than once — backend guard
  (`ReminderServiceImpl.applyCompletionToReminder()` throws on a disabled
  non-recurring reminder) + frontend synchronous in-flight/done guard (see
  FRONTEND.md's new Established Pattern).
- **Duplicate treatment creation**: `DiseaseDetailPanelComponent` and
  `CareCardComponent` each used to call `TreatmentPlanService` directly,
  bypassing the `Treatment` entity's one-active-per-disease protection —
  consolidated onto `CareCardComponent` → `TreatmentService` as the sole path.
- **Species AI-preference routing**: `SpeciesEnrichmentServiceImpl` was
  hardcoded to DeepSeek regardless of the user's saved AI model choice — now
  threads `AiModelPreference` through from `IdentificationServiceImpl.
  resolveSpecies()`.
- **Structured species care cards**: species enrichment now also generates a
  small AI `careCards` array (migration 020), rendered on the Species
  Overview tab via the existing `CarePlanModule`.
- **Plant page care cards moved to Overview** (were previously buried in the
  Scans section, alongside scan-specific UI that has nothing to do with them).
- **Chat conversation history + SSE streaming**: backend `chatStream()` +
  `POST /chat/stream` (SseEmitter), frontend incremental rendering via
  Angular's `HttpDownloadProgressEvent.partialText` (keeps the JWT
  interceptor working, unlike a raw `fetch()` bypass) — closes out the Phase 4
  polish item that had been open since T4.2.
- **Species "recently scanned" filter** now works (`lastScanAt` added).
- **Dead code removed**: `IdentificationResultComponent`.
- **4 missing controller integration tests written**
  (`IdentificationControllerIT`, `TreatmentPlanControllerIT`,
  `TreatmentControllerIT`, `SpeciesControllerIT`) — in writing them, discovered
  the ApplicationContext had *never* successfully booted under the test
  profile at all (`application-test.yml` was missing placeholders for
  `app.plantnet.api-key`/`github.token`, and the VAPID web-push keys were
  non-EC placeholder strings that fail real EC-point decoding) — fixed, so
  these and the pre-existing `PlantControllerIT`/`AuthControllerIT` can now
  actually run. See BACKEND.md's Test Inventory for the "run one at a time,
  not batched" caveat (Testcontainers connection-pool contention observed on
  this dev machine when running 4 in one JVM).
- **JaCoCo gate set to a real number**: 55%, just under the unit suite's
  actual ~58.9% line coverage — was a hardcoded, never-achieved 10% before.
- 198/198 unit tests passing, all 4 new ITs pass individually, `mvn clean
  verify` is BUILD SUCCESS (spotless clean, checkstyle clean, coverage gate
  met).

## What's Built

### Phases 0–1 — Setup, Auth, Plant CRUD
Spring Boot + Angular skeletons, Docker Compose (Postgres 15 + Redis 7), CI/CD,
JWT auth (Spring Security 6), full Plant CRUD with Redis caching. Stable, no open
items.

### Phase 2 — AI Plant Identification
Photo → AI identification pipeline, now fully async: `POST /analyze` publishes to
Kafka and returns 202 immediately, a consumer runs the AI calls off the HTTP
thread, the frontend polls `GET /{id}` every 3s. Identification uses
`GitHubModelsClient` (gpt-4o vision) with `DeepSeekClient` (DeepSeek-R1 text) for
care plans/cure advice; `OllamaClient` (llava-phi3) is the local-dev/fallback
option. Visual annotation draws polygon overlays (disease/plant/healthy-area
regions) on the photo, with a disease detail panel offering AI cure advice.
Photos are stored on disk with a Redis cache + SHA-256 dedup layer. A garden
health dashboard (now folded into the Home page, see Phase 6) aggregates
overdue/today reminders and health trends. See ARCHITECT.md for the Kafka, Redis
photo, and image-dimension-locking patterns in full — they're durable
architecture, not session history.

### Phase 3 — Reminders + Care Plans
Full reminder CRUD + scheduler (one daily push per user, not per reminder) +
web-push (VAPID). Care plan cards generated by AI can carry an `actionPlan`:
either a ROUTINE recurring reminder or a multi-step TREATMENT plan (backed by
one-time `Reminder` rows under a `TreatmentPlan`), with optional Mermaid-diagram
illustrations. `ReminderService.applyCompletionToReminder()` is the single place
"mark done" logic lives — see ARCHITECT.md. **Open:** T3.3, on-device manual
testing of push delivery and PWA installability — needs a human with a phone,
never completed.

### Phase 4 — AI Chat
Single-turn chat wired to Ollama with full garden context, later extended (T6.13)
to accept an optional `plantId` for plant-specific context (last scan health,
active treatment). Streaming responses and conversation history are known,
unstarted polish — not blocking launch.

### Phase 6 — Species & Treatment Domain Restructure
Restructured the domain from plant-centric to species-centric. All 14 tasks
shipped:

| Task | What it added |
|---|---|
| T6.1 | `Species` entity (shared across users) + `GET /species/{id}`, `GET /species/mine` |
| T6.2 | `Treatment` entity (per-plant disease lifecycle) — wraps `TreatmentPlan`, doesn't duplicate it |
| T6.3 | `Plant.speciesId/lastScanId/activeTreatmentId`, `Identification.speciesId` FK columns |
| T6.4 | Async AI species enrichment (description/careOverview/imageUrl) |
| T6.5 | Garden page restructured to species-first cards (`/garden`) |
| T6.6 | Species detail page (`/garden/species/:id`, Overview + Plants tabs) |
| T6.7 | Home page (`/home`) — greeting, quick stats, needs-attention, recent scans |
| T6.8 | Bottom nav expanded to 5 items: Home / Garden / Identify / Reminders / Chat |
| T6.9 | Identification 3-path species/plant matching flow (Garden vs. Species vs. Plant entry points) |
| T6.10 | Plant page rebuilt: sticky header + icon button bar (replaces `mat-tab-group`) |
| T6.11 | Plant page Scans section + "Start Treatment Plan" CTA |
| T6.12 | Treatment page (`/treatment/:id`) |
| T6.13 | Chat plant-context injection (`?plantId=` query param) |
| T6.14 | Backend event-driven sync: `TreatmentPlan` completion now flips the wrapping `Treatment` to COMPLETED automatically |

The full domain model (Species/Treatment shapes, the "two Treatment concepts"
disambiguation, the identification decision tree, the lifecycle state machine) now
lives in ARCHITECT.md as a permanent reference — read that, not this list, before
extending any of this.

## Active Branches
Current branch: **`feature/PP-038-pre-phase5-cleanup`** — see the cleanup pass
section above for what it carries. Not yet merged to `dev`/`master` — needs a PR.

`feature/PP-030-treatment-entity` (T6.2 + T6.14) was already merged to `dev`
via PR #50 (confirmed in git log) — superseded, the old "open a PR for it"
task below is stale and removed.

All other Phase 6 feature branches (`PP-029`, `031`–`037`) are already merged to
`dev` via PR (confirmed in git log). Phases 0–2's feature branches
(`PP-001`–`PP-027`, `AddChooseAi`, `chatfix`, etc.) are long-merged and still
sitting locally/remotely — safe cleanup candidate whenever convenient, not
urgent.

## Next Tasks (in order)
1. Open a PR / merge `feature/PP-038-pre-phase5-cleanup` to `dev`.
2. **Phase 7 — Model Control, Batch Scanning, Multi-Treatment UX** (TASK_PLAN.md
   T7.1–T7.4) — planned 2026-06-22, not yet started. Sequencing vs. Phase 5 is
   an open decision (see below).
3. **Phase 5 — Launch prep** (TASK_PLAN.md T5.1–T5.8): prod config, performance,
   security hardening, API docs, deploy to Railway/Vercel, beta test, release
   v1.0.0.
4. 👤 **Decide Phase 5 vs. Phase 7 ordering** — Phase 7 is real feature work
   (model control, batch scanning, multi-treatment UX), Phase 5 is launch
   infra; neither blocks the other technically.
5. T5.5 needs a decision on Kafka/Zookeeper's production story (managed add-on,
   or fall back to synchronous identification for v1.0.0) — not yet decided, see
   ARCHITECT.md's Kafka pattern note.
6. T3.3 — manual on-device testing (push notifications, PWA installability,
   offline reading) — needs a real phone, can happen any time before launch.
7. Live-verify chat SSE streaming against a real Docker stack (written and
   passes locally but never confirmed end-to-end this session — see
   FRONTEND.md's Open Items).

## Phase 7 — Planning Session (2026-06-22)
Not started, planned only. Full task breakdown in TASK_PLAN.md (T7.1–T7.4).
Three asks, all grounded against the real code before planning (see
TASK_PLAN.md's Phase 7 investigation notes for the full detail — not
duplicated here):
- **Model control:** split the single `AiModelPreference` into independent
  vision/reasoning choices, remove silent cross-model fallback (found in
  `IdentificationServiceImpl.runIdentification()`'s OLLAMA_LLAVA→GITHUB_GPT4O
  catch and `DeepSeekAnnotationClient`'s 429→Ollama fallback +
  empty-regions-on-exhaustion swallow), and fix a real bug:
  `GlobalExceptionHandler.handlePlantPal()` hardcodes HTTP 500 for every
  `PlantPalException`, so the 429s already thrown by `IdentificationServiceImpl`/
  `TreatmentServiceImpl` arrive at the frontend mislabeled as 500 today — there
  is no working rate-limit UX yet because the status code itself is wrong.
- **Batch scanning:** frontend-only — `POST /analyze` is already async/Kafka
  and already rate-limited per user, so queuing N independent scans is N calls
  to the existing endpoint, not a new batch endpoint.
- **Multi-treatment picker:** the backend is mostly already shipped and
  unused — `TreatmentService.getActiveTreatmentsForPlant()` (plural),
  `GET /plants/{id}/active-treatments`, and even
  `TreatmentService.getActiveTreatments()` on the Angular service all exist
  and work; no component calls them. Also found and scoped a real bug:
  `TreatmentDetailComponent` fetches the treatment once in `ngOnInit` and
  never again, so the async-generated `diseaseDescription` (which the backend
  does correctly generate and save within seconds) never appears on-screen —
  not a generation bug, a missing-poll bug.

## Known Tech Debt
See BACKEND.md and FRONTEND.md's own "Open Items" sections for the current,
maintained list (JaCoCo gate at 55% not 80%, ITs not wired into `mvn verify`,
PlantNetClient dead-code cleanup, GITHUB_TOKEN rotation before prod, etc.) —
not duplicated here to avoid the two copies drifting apart.
