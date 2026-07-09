# Frontend Agent — Restore Prompt
> Paste this as the first message in a new Claude Code conversation.
> Claude Code must have access to the frontend/ directory.
> Update this file at the end of every session with anything learned.

You are the frontend developer on PlantPal.

## Your Role
- Implement Angular features from task prompts
- Diagnose and fix frontend issues
- Follow ALL conventions below without exception

## Stack
Angular 16+, NgModules (NOT standalone components),
TypeScript strict mode, Angular Material, SCSS, @angular/pwa,
RxJS, ReactiveFormsModule

## Current Status
**Phases 0–4, 6, and 7 are all shipped, plus a pre-Phase-5 cleanup pass
(`feature/PP-038-pre-phase5-cleanup`) closing every flagged gap. Phase 7's
T7.1 (backend) through T7.4 are all complete — model control UI, structured
AI-error/rate-limit UX, "powered by" badges, multi-select batch scanning, the
multi-treatment picker, and the disease-description poll fix. T7.2 shipped on
its own `feature/PP-040-model-control-frontend` (merged to `dev`); T7.3 and
T7.4 both shipped on `feature/PP-041-batch-scan`. Phase 5 (Launch prep) is the
only phase left unstarted** — see TASK_PLAN.md. Full session-by-session
history of how each feature was built lives in STATE.md and git log, not
here — this file is a durable reference to what exists *now*.

## Non-Negotiable Conventions
- NgModules only — no standalone components
- Strict TypeScript — no `any`
- Constructor injection only — no inject()
- All subscriptions unsubscribed in ngOnDestroy (takeUntil pattern)
- No inline styles — Angular Material tokens + SCSS only
- All HTTP calls return Observable<ApiResponse<T>>
- JWT attached automatically by jwt.interceptor.ts
- All requests proxied via proxy.conf.json (/api → localhost:8180 — the dockerized
  backend's platform-block host port; see docker-compose.yml)
- Every `@Injectable()` service has no `providedIn: 'root'` — each lazy feature
  module that uses a service re-provides it in its own `providers:` array. This
  is deliberate (not an oversight): expect to add a provider line whenever a new
  lazy module starts calling an existing cross-feature service (e.g.
  `treatment.module.ts` provides `PlantService`/`IdentificationService`/
  `ReminderService`/`TreatmentPlanService` even though it doesn't "own" any of them).
- When a second lazy module needs a component/dialog only one other lazy module
  currently declares, move it to `SharedModule` rather than duplicating it (see
  `TreatmentStepListComponent`/`StepDetailDialogComponent` below) — don't
  re-declare across multiple modules.

## Module Structure
```
core/         auth service, JWT interceptor, auth guard, models, push-notification.service,
              user.service.ts (preferences), ai-error.service.ts (T7.2 — see below)
shared/       reusable components + constants (see shared/components/ below)
features/
  landing/    public marketing page at '/' (PP-082) — anonymous-visitor-only; component
              redirects to /home in ngOnInit if AuthService.isLoggedIn(). Root route
              (app-routing.module.ts) now lazy-loads this instead of redirecting
              straight to 'home'. No providers — static content only, no services.
  auth/       login, register
  preferences/ /preferences page (T7.2) — hosts the vision/reasoning model-selector
              full-page; linked from the user account menu and from AiErrorService's
              rate-limit snackbar action. Didn't exist before T7.2 — the model picker
              used to be toolbar-only.
  plant/      list (now at /garden via species/), form, detail (sticky header +
              icon button bar — overview/care log/actions/treatment/scans),
              + treatment.service.ts (the Treatment entity, distinct from
              reminder/'s treatment-plan.service.ts — see ARCHITECT.md)
  identification/ photo upload, results, care plan + actionable cards, disease
              detail, species-confirm-step + plant-select-step (Flow 1 matching),
              batch scan mode (T7.3 — "Scan multiple plants" checkbox, only at
              entry points with no locked plant/species context)
  reminder/   list, calendar, create dialog, care log, TreatmentPlan detail page
              (/treatment-plans/:id — the OLDER generic plan, not the species/
              treatment Treatment entity)
  treatment/  the disease-level Treatment entity's own page (/treatment/:id) —
              distinct from reminder/'s TreatmentPlan, see ARCHITECT.md's
              "Two Treatment concepts"
  species/    Garden landing page (/garden, species-first cards, "recently
              scanned" filter chip sorts by lastScanAt) + species detail page
              (/garden/species/:id, Overview tab now renders AI-generated
              structured care cards via the existing CarePlanModule, plantId=null)
  dashboard/  Home page (/home, default landing) + the older Garden Dashboard
              health-trends view (/home/overview, was /dashboard)
  chat/       multi-turn chat (sends accumulated history), streamed token-by-
              token replies via SSE, plant-context injection via ?plantId=
              query param
layout/       shell, navbar
```
Bottom nav (5 items): Home | Garden | Identify | Reminders | Chat —
`app.component.ts`'s `navLinks` array, in that order.

### shared/components/
`image-lightbox/` (full-size photo viewer, MatDialog), `mermaid-diagram/`
(dynamic `import('mermaid')`, module-level init guard, renders nothing on
malformed DSL — diagrams are always a bonus), `model-selector/` (T7.2 — now two
dropdowns, vision + reasoning, not one; used in the toolbar and on the
`/preferences` page), `model-usage-badge/` (T7.2 — "powered by" caption,
renders nothing if both `visionModel`/`reasoningModel` inputs are null/
undefined, so it's safe ahead of a backend field landing), `step-detail-dialog/`
(per-step "How to" detail — moved here from reminder/ once treatment/ needed it
too), `treatment-step-list/` (diagram + step list + mark-done UI, shared by
both the TreatmentPlan detail page and the Treatment page's "plan" section).

## API Contract
Backend base URL proxied to localhost:8180.
All responses: `{ success: boolean, data: T, message: string, correlationId: string, timestamp: string }`

## Established Patterns
> See ARCHITECT.md for the full design rationale behind these — this is the
> frontend-specific implementation summary.

- **Sticky header + icon button bar** (Plant page, Treatment page): CSS
  `position: sticky` + `IntersectionObserver` on a sentinel element drives a
  `.collapsed` class; section switching is `*ngSwitch` on a component property,
  never a route change. Species detail page deliberately still uses
  `mat-tab-group` instead — this pattern isn't a blanket tab replacement.
- **Mermaid diagrams**: client-rendered from AI-generated DSL text, not raw
  AI-generated SVG (an XSS-shaped risk for no benefit since Mermaid's renderer
  only ever emits valid SVG from valid-or-rejected DSL).
- **`[hidden]` on canvas/img/video/svg needs a `tag[hidden] { display: none
  !important; }` override** in that component's own stylesheet — a global
  `canvas { display: block; }` reset (author-origin CSS) silently beats the
  browser's built-in `[hidden]` default (user-agent-origin CSS) regardless of
  selector specificity. Bit `photo-annotator.component.scss` once; watch for it
  on any new canvas/img-based component.
- **Action plan UI**: care cards with a non-null `actionPlan` show a "Set
  reminder" (ROUTINE → `SetReminderDialogComponent`) or "Start treatment plan"
  (TREATMENT → directly calls `TreatmentPlanService.createFromActionPlan()`)
  button. Both `plantId` and `existingCareTypes` are nullable/optional inputs
  threaded down from wherever the card is rendered (preview-card pre-save vs.
  plant-detail post-save) — the action row simply doesn't render without a
  `plantId`.
- **Synchronous in-flight/done guards on completion buttons**: the
  `[disabled]` template binding alone isn't enough to stop a fast double-click
  (it only takes effect after Angular's next render tick). `reminder-list.
  component.ts`'s `completeReminder()` checks a plain `Set<number>`
  *synchronously* as its first line, before any async call — immune to render
  timing. A non-recurring reminder's id stays in the set permanently after
  success (button becomes "Done", disabled); a recurring one's id is removed
  (the next reload reflects the new schedule). A 400 from the backend's
  already-done guard is treated as success (refresh, no error toast), not a
  failure — the reminder is, after all, done.
- **Identification Flow 1 species/plant matching** (Garden FAB entry point
  only — Flows 2/3 from a Species or Plant page already know their context and
  skip this): after a scan completes, `species-confirm-step` then
  `plant-select-step` components walk the user through
  `IdentificationService.getSpeciesMatch()/resolveSpecies()/getPlantMatch()/
  resolvePlant()` before landing on the resulting plant's detail page.
- **AI-call error handling** (T7.2): `core/services/ai-error.service.ts`
  (`AiErrorService`, root-provided like `UserService` — core cross-cutting
  services are the one exception to the no-`providedIn:'root'` rule) is the
  single place that turns an `HttpErrorResponse` from an AI-calling endpoint
  into UI. `.notify(err)` shows a snackbar (429 gets the actionable
  rate-limit message + Settings action; anything else shows the real backend
  `message`). `.handle(err)` does the same but *returns* the message instead
  of always popping a toast — used where the error also needs to render
  inline (chat's AI message bubble). Use one of these at every new AI-calling
  call site instead of a local `mapError()`/generic-toast pattern.
- **Batch scan mode** (T7.3, queue ownership + concurrency fixed post-launch
  after live testing): gate any "batch" affordance on the *absence of locked
  context* (`lockedPlantId`/`lockedSpeciesId` both null), not on which
  page/route opened the dialog — multiple entry points can legitimately share
  that "no context yet" state. The per-file submission queue lives in
  `BatchScanService` (`features/identification/services/batch-scan.service.ts`),
  **not** in `IdentificationUploadDialogComponent` — a dialog-owned queue
  piped through the dialog's own `takeUntil(this.destroy$)` dies the moment
  the dialog closes, silently abandoning every not-yet-started item (shipped
  bug #1, fixed same day). `IdentificationUploadDialogComponent` is now a
  thin view: `batchActive` reads `batchScan.items.length > 0`, so reopening
  the dialog mid-batch (or after it finished) shows the real state instead of
  a blank form. **Rule for any future "long-running queue driven from inside
  a dialog" feature: own the queue in a service the dialog merely observes,
  never in the dialog component itself** — the dialog is allowed to close at
  any time without that being allowed to cancel real work.
  - All N items' `/analyze` calls now fire **concurrently** at batch start,
    not sequentially-waiting-for-full-poll-completion — the original
    sequential design meant the 2nd identification didn't even exist
    server-side (so couldn't show up as PENDING anywhere it's listed) until
    the 1st had fully resolved (shipped bug #2, fixed same session). A batch
    is capped at `MAX_IMAGES` (5, `PhotoUploadComponent`), well under the
    20/hour identification rate limit, so firing them together is safe.
  - **`BatchScanService` is correctly module-scoped (`@Injectable()`, NOT
    `providedIn: 'root'`)** despite needing to survive navigation across
    different feature areas — and must be listed in the `providers:` array
    of **every** lazy module that opens `IdentificationUploadDialogComponent`
    (currently `identification`/`plant`/`species`/`dashboard` — same 4
    modules that already provide `IdentificationService` for the same
    reason). This was a real shipped bug too: the service initially lived
    only in `identification.module.ts`'s providers, causing a
    `NullInjectorError` the instant the dialog was opened from any of the
    other 3 modules. **Confirms `IdentificationService`'s actual resolution
    mechanism was never "the dialog's declaring module" (an earlier, wrong
    assumption written here and since corrected) — it only ever worked
    because every calling module happens to *also* independently provide
    `IdentificationService` for its own unrelated needs.** A `providedIn:
    'root'` service can't fix this either: it would be constructed via the
    root injector alone, which has no path to the module-scoped
    `IdentificationService`/`AiErrorService` it depends on — so the
    "register it in every module that opens this dialog" rule is the actual
    correct, intentional pattern, not a workaround. (`AiErrorService` itself
    is fine root-provided — its own dependencies, `MatSnackBar`/`Router`, are
    root-available; the blocker is specifically `BatchScanService`'s
    constructor dependency on the module-scoped `IdentificationService`.)
- **Multi-treatment everywhere a plant's active treatment is checked** (T7.4):
  always use `TreatmentService.getActiveTreatments()` (plural) and match by
  `diseaseName`, never the deleted singular `getActiveTreatment()` — a plant
  can have more than one active treatment at once (one per disease), and the
  old singular endpoint only ever returned the single most-recent one, which
  silently hid every other active disease's state. When a lookup has exactly
  one candidate, behave like a direct match always did (no extra click); when
  it has more than one and the UI needs the user to choose,
  `ActiveTreatmentSelectSheetComponent` (`features/plant/components/
  active-treatment-select-sheet/`) is the one picker — don't build a second.

## Test/Build Commands
```bash
ng build              # dev config — run before considering any task done
ng build --configuration production
ng lint
npx tsc --noEmit
```

## Phase 8.5 — Incoming UI Changes
**Identification UI must move from binary COMPLETED/FAILED to stage-aware states (T8.G).**
Update `identification.model.ts` to add: `identificationStatus`, `annotationStatus`,
`candidateStatus` (`'PENDING'|'COMPLETED'|'FAILED'|'SKIPPED'`), `identificationModel`,
`annotationModel`, `failureReason: string | null` — these arrive on `IdentificationResponse`
once T8.A ships.

Display rules once fields are available:
- `status='COMPLETED'` + `annotationStatus='FAILED'` → show care plan + inline
  `"Overlay unavailable"` chip, NOT a red FAILED state. Optional "Retry overlay" link.
- `status='COMPLETED'` + `annotationStatus='PENDING'` or `candidateStatus='PENDING'` →
  continue existing 3s poll — the stages resolve async and the poll re-fetches.
- `status='FAILED'` (`identificationStatus='FAILED'`) → show red FAILED chip as before
  + a **Retry button** calling `POST /identifications/{id}/retry` (T8.F). On 202 response:
  trigger `pollUntilComplete()` — no new polling loop needed. Wire `AiErrorService.handle()`
  to the retry button's error path. Optionally surface `failureReason` as a tooltip.
- Do NOT add Playwright tests here — T9.2 (Phase 9 E2E) will cover identification results
  with stubbed data once that suite exists.

## Known Issues / Open Items
- T3.3 (manual on-device testing — push notification delivery, PWA
  installability, offline reading) has never been done; needs a real phone, not
  something to automate. See STATE.md.
- No shared `StickyHeaderComponent` extracted yet — Plant page and Treatment
  page each duplicate the CSS/IntersectionObserver wiring. Worth extracting if
  a third page needs this pattern.
- Chat streaming (`chat.service.ts`'s `sendMessageStream()`) was written and
  unit-style-verified but never confirmed against a live Docker stack in the
  session it shipped in — re-verify tokens actually arrive incrementally (not
  all at once) the first time anyone touches `nginx.conf`'s
  `/api/v1/chat/stream` block or `ChatController`.
- (T7.2) `CureAdviceResponse` (backend) has no model-usage field yet — the
  disease-detail-panel's "powered by" badge is wired to
  `result.reasoningModelUsed` and will light up the moment one's added, but
  shows nothing today. Every other badge surface (identification preview,
  care-card, treatment-detail, species-detail) already has live backend data
  from T7.1's migration 022.
- (T7.2, inherited from T7.1) Setting a vision/reasoning model preference on
  the new `/preferences` page doesn't yet change which AI client actually
  runs — no backend service reads `visionModelPreference`/
  `reasoningModelPreference` to pick a client. The preference is readable/
  writable and persisted correctly; it's just not load-bearing yet. That
  wiring is unscoped follow-up work, not part of T7.2.
- (T7.3) Batch scan mode has no "view results" deep-link beyond the
  completion snackbar's "View" action (routes to `/identify`, not to the
  specific new plants) — acceptable since the snackbar is the one piece of
  feedback guaranteed to reach the user even if they've navigated away.
  No actual "cancel the rest of the batch" affordance exists either — once
  started, every queued item runs to completion or failure regardless of
  what the dialog does (see `BatchScanService`).
- (T7.4) `TreatmentDetailComponent`'s description poll has no visible "this
  timed out" state — if generation silently fails server-side, the page just
  keeps showing "Generating a description…" forever after the 30s poll gives
  up internally. Matches the task prompt's "don't poll forever" ask but isn't
  a full UX treatment of the failure case.

### Closed out in the pre-Phase-5 cleanup pass (kept here as "don't re-break this")
- `IdentificationResultComponent` was dead code — deleted, not just unreferenced.
- "Recently scanned" filter chip now works — `SpeciesSummaryDto.lastScanAt` was
  added (backend) and the chip wired up (frontend); don't re-disable it.
- Care-card button success states (`treatmentStarted`) are no longer a
  session-only boolean — `care-card.component.ts` now derives it from the real
  `Treatment` entity (`treatmentService.getActiveTreatments(plantId)`, matched
  on `diseaseName` — plural since T7.4, see below) on `ngOnChanges`, so a
  refresh shows the correct state. **Pattern for future "did the user already
  do X" UI state:** derive it from the entity that actually tracks X, don't
  add a new persisted/session flag that can drift out of sync with it.
- Two separate UI paths (`DiseaseDetailPanelComponent` and `CareCardComponent`)
  used to each call `TreatmentPlanService.createFromActionPlan()` directly,
  bypassing the `Treatment` entity's one-active-per-disease protection — could
  start two treatment plans for the same disease. `DiseaseDetailPanelComponent`
  no longer offers its own "start treatment" button at all; `CareCardComponent`
  is now the only path, and it goes through `TreatmentService.createTreatment()`
  → `.craftPlan()` like the Plant-page CTA always did.

## Key Files
```
frontend/src/app/core/interceptors/jwt.interceptor.ts
frontend/src/app/core/services/auth.service.ts
frontend/src/app/core/services/ai-error.service.ts                (T7.2)
frontend/src/app/app-routing.module.ts
frontend/src/app/app.component.ts                              (bottom nav)
frontend/src/app/shared/components/treatment-step-list/
frontend/src/app/shared/components/model-usage-badge/             (T7.2)
frontend/src/app/features/preferences/                            (T7.2)
frontend/src/app/features/identification/services/batch-scan.service.ts  (T7.3)
frontend/src/app/features/plant/components/active-treatment-select-sheet/  (T7.4)
frontend/src/app/features/plant/components/plant-detail/
frontend/src/app/features/treatment/pages/treatment-detail/
frontend/src/app/features/species/pages/species-detail/
frontend/src/app/features/dashboard/pages/home/
frontend/src/app/features/identification/components/care-plan/
```
