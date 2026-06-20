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
**Phases 0–4 and 6 are shipped. Phase 5 (Launch prep) is next** — see
TASK_PLAN.md. Full session-by-session history of how each feature was built
lives in STATE.md and git log, not here — this file is a durable reference to
what exists *now*.

## Non-Negotiable Conventions
- NgModules only — no standalone components
- Strict TypeScript — no `any`
- Constructor injection only — no inject()
- All subscriptions unsubscribed in ngOnDestroy (takeUntil pattern)
- No inline styles — Angular Material tokens + SCSS only
- All HTTP calls return Observable<ApiResponse<T>>
- JWT attached automatically by jwt.interceptor.ts
- All requests proxied via proxy.conf.json (/api → localhost:8080)
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
core/         auth service, JWT interceptor, auth guard, models, push-notification.service
shared/       reusable components + constants (see shared/components/ below)
features/
  auth/       login, register
  plant/      list (now at /garden via species/), form, detail (sticky header +
              icon button bar — overview/care log/actions/treatment/scans),
              + treatment.service.ts (the Treatment entity, distinct from
              reminder/'s treatment-plan.service.ts — see ARCHITECT.md)
  identification/ photo upload, results, care plan + actionable cards, disease
              detail, species-confirm-step + plant-select-step (Flow 1 matching)
  reminder/   list, calendar, create dialog, care log, TreatmentPlan detail page
              (/treatment-plans/:id — the OLDER generic plan, not the species/
              treatment Treatment entity)
  treatment/  the disease-level Treatment entity's own page (/treatment/:id) —
              distinct from reminder/'s TreatmentPlan, see ARCHITECT.md's
              "Two Treatment concepts"
  species/    Garden landing page (/garden, species-first cards) + species
              detail page (/garden/species/:id)
  dashboard/  Home page (/home, default landing) + the older Garden Dashboard
              health-trends view (/home/overview, was /dashboard)
  chat/       single-turn chat, plant-context injection via ?plantId= query param
layout/       shell, navbar
```
Bottom nav (5 items): Home | Garden | Identify | Reminders | Chat —
`app.component.ts`'s `navLinks` array, in that order.

### shared/components/
`image-lightbox/` (full-size photo viewer, MatDialog), `mermaid-diagram/`
(dynamic `import('mermaid')`, module-level init guard, renders nothing on
malformed DSL — diagrams are always a bonus), `model-selector/` (AI model
preference dropdown, toolbar), `step-detail-dialog/` (per-step "How to" detail —
moved here from reminder/ once treatment/ needed it too), `treatment-step-list/`
(diagram + step list + mark-done UI, shared by both the TreatmentPlan detail page
and the Treatment page's "plan" section).

## API Contract
Backend base URL proxied to localhost:8080.
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
- **Identification Flow 1 species/plant matching** (Garden FAB entry point
  only — Flows 2/3 from a Species or Plant page already know their context and
  skip this): after a scan completes, `species-confirm-step` then
  `plant-select-step` components walk the user through
  `IdentificationService.getSpeciesMatch()/resolveSpecies()/getPlantMatch()/
  resolvePlant()` before landing on the resulting plant's detail page.

## Test/Build Commands
```bash
ng build              # dev config — run before considering any task done
ng build --configuration production
ng lint
npx tsc --noEmit
```

## Known Issues / Open Items
- `IdentificationResultComponent` is dead code (orphaned — no route or template
  references it anymore, superseded by `identification-preview-section`) —
  cleanup candidate, not urgent
- T3.3 (manual on-device testing — push notification delivery, PWA
  installability, offline reading) has never been done; needs a real phone, not
  something to automate. See STATE.md.
- Chat streaming + conversation history not started (Phase 4 polish, optional)
- No shared `StickyHeaderComponent` extracted yet — Plant page and Treatment
  page each duplicate the CSS/IntersectionObserver wiring. Worth extracting if
  a third page needs this pattern.
- "Recently scanned" filter chip on the Garden species list is rendered but
  disabled (`matTooltip` explains why) — `SpeciesSummaryDto` has no
  last-scan-date field to sort on yet.
- Button success states on care cards (`reminderSet`, `treatmentStarted`) are
  session-only component booleans, not derived from a persisted backend flag —
  a page refresh re-shows the actionable button even after a plan/reminder was
  already created from that exact card.

## Key Files
```
frontend/src/app/core/interceptors/jwt.interceptor.ts
frontend/src/app/core/services/auth.service.ts
frontend/src/app/app-routing.module.ts
frontend/src/app/app.component.ts                              (bottom nav)
frontend/src/app/shared/components/treatment-step-list/
frontend/src/app/features/plant/components/plant-detail/
frontend/src/app/features/treatment/pages/treatment-detail/
frontend/src/app/features/species/pages/species-detail/
frontend/src/app/features/dashboard/pages/home/
frontend/src/app/features/identification/components/care-plan/
```
