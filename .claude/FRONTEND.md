# Frontend Agent — Restore Prompt
> Paste this as the first message in a new Claude Code conversation.
> Claude Code must have access to the frontend/ directory.

You are the frontend developer on PlantPal.

## Your Role
- Implement Angular features from task prompts
- Diagnose and fix frontend issues
- Follow ALL conventions below without exception

## Stack
Angular 16+, NgModules (NOT standalone components),
TypeScript strict mode, Angular Material, SCSS, @angular/pwa,
RxJS, ReactiveFormsModule

## Non-Negotiable Conventions
- NgModules only — no standalone components
- Strict TypeScript — no `any`
- Constructor injection only — no inject()
- All subscriptions unsubscribed in ngOnDestroy (takeUntil pattern)
- No inline styles — Angular Material tokens + SCSS only
- All HTTP calls return Observable<ApiResponse<T>>
- JWT attached automatically by jwt.interceptor.ts
- All requests proxied via proxy.conf.json (/api → localhost:8080)

## Module Structure
core/         — auth service, JWT interceptor, auth guard, models, push-notification.service
shared/       — reusable components (model-selector, mermaid-diagram), constants
features/
  auth/       — login, register ✅
  plant/      — plant list, form, detail (+ photo timeline, care log, care plan) ✅
  identification/ — photo upload, results, care plan + actionable cards, disease detail ✅
  reminder/   — list, calendar, create dialog, care log, treatment plans ✅
  dashboard/  — garden health landing page (/dashboard) ✅
  chat/       — basic single-turn chat, wired to backend ✅ (Phase 4 polish not started)
layout/       — shell, navbar
(ai-test/ removed in T4.1 — was a dev-only scratch page)

## API Contract
Backend base URL proxied to localhost:8080.
All responses: { success: boolean, data: T, message: string,
  correlationId: string, timestamp: string }

## Current State
See .claude/STATE.md for completed tasks and active branches.

---

## Session Notes (updated 2026-06-14)

### Files read and confirmed
- angular.json — strict mode on, budgets 500kb warn / 1mb error, proxy wired in serve options
- app.module.ts — NgModule, JwtInterceptor registered as HTTP_INTERCEPTORS
- app-routing.module.ts — lazy loads all feature modules; root redirects to /ai-test (dev convenience)
- core/models/api-response.model.ts — ApiResponse<T> + PageResponse<T>
- core/models/user.model.ts — User + AuthTokenPayload
- core/services/auth.service.ts — constructor injection ✅, localStorage session, token expiry check
- core/interceptors/jwt.interceptor.ts — Bearer header + 401 → logout redirect
- core/guards/auth.guard.ts — CanActivate, redirects to /login
- environment.ts — apiUrl: '/api/v1'
- proxy.conf.json — /api → http://localhost:8080
- plant/services/plant.service.ts — @Injectable() (no providedIn), HttpClient, all methods return Observable<ApiResponse<T>> ✅
- plant/models/plant.model.ts — PlantResponse, CreatePlantRequest, UpdatePlantRequest
- plant/components/plant-list/plant-list.component.ts — CONVENTION NOTE: no takeUntil/ngOnDestroy on subscriptions (HTTP so no real leak, but violates rule)

### Feature module inventory
> ⚠️ This table reflects the ORIGINAL session (2026-06-14). Badly stale — kept for history only.
> For current status, read STATE.md's "Completed Tasks" list top to bottom; everything through
> T3.5 is done as of 2026-06-18. Quick summary: auth/plant/identification/reminder/dashboard are
> all fully built; chat/ has basic single-turn working (Phase 4 polish not started); ai-test/ was
> deleted in T4.1.
| Module | Files present | Real implementation |
|---|---|---|
| auth/ | login, register components + routing + module | ✅ Full |
| plant/ | list, form, detail, card + service + model + routing + module | ✅ Full |
| identification/ | photo-upload, identification-result, identification-page + service + model | ✅ Full (PR #5 merged) |
| identification/care-plan/ | care-card, care-plan components + CarePlanModule | ✅ T2.7 complete |
| reminder/ | reminder-list (stub) + routing + module | ❌ Skeleton only |
| chat/ | chat-home (stub) + routing + module | ❌ Skeleton only |
| ai-test/ | ai-test component + module | Dev tool |

### Module sharing pattern — CarePlanModule
Care plan components live at `features/identification/components/care-plan/` but are declared
in a standalone `CarePlanModule` (not IdentificationModule) so they can be imported by PlantModule
without creating a lazy-module-imports-lazy-module cycle.
- `IdentificationModule` imports `CarePlanModule` → gets `app-care-plan` in identification-result ✅
- `PlantModule` imports `CarePlanModule` + provides `IdentificationService` → gets `app-care-plan` in plant-detail ✅
- `CarePlanModule` depends only on CommonModule + MatIconModule + MatDividerModule (no circular deps) ✅

### Care plan component API
`<app-care-plan [carePlan]="carePlan" [maxCards]="3">` — maxCards optional; null = show all
`<app-care-card [card]="card">` — internal, always rendered by CarePlanComponent

### Known convention violations in existing code
- plant-list.component.ts: .subscribe() calls without takeUntil/ngOnDestroy. HTTP observables self-complete so no real leak, but violates the stated convention. Flag if touching that file.

### T2.8 — Completed (2026-06-14)
**Files changed:**
- `identification/models/identification.model.ts` — added HealthStatus, SavePreviewEditEvent, healthStatus/healthNotes on IdentificationResponse
- `plant/models/plant.model.ts` — added SaveIdentificationAsPlantRequest
- `plant/services/plant.service.ts` — added saveFromIdentification() → POST /api/v1/plants/from-identification
- `identification/components/preview-card/` — new PreviewCardComponent (photo + badges + 3-card care preview + nickname/location form + 3 action buttons)
- `identification/identification.module.ts` — declared PreviewCardComponent
- `identification/pages/identification-page/` — state machine simplified to idle | analyzing | preview | error; removed uploading/result states; old SaveAsNewEvent removed
- `plant/components/plant-form/plant-form.component.ts` — added prefillFromQueryParams() for Edit-before-saving flow
- `proxy.conf.json` — added /photos proxy to localhost:8080 (dev photo display was broken)

**Architecture notes:**
- PreviewCardComponent injects PlantService (provided by IdentificationModule.providers — no new module needed)
- "Edit before saving" emits {nickname, location} up to the page, which navigates to /plants/new with query params; plant-form.prefillFromQueryParams() picks them up
- `[maxCards]="3"` on <app-care-plan> inside preview-card — reuses existing CarePlanComponent without any changes
- Backend endpoint POST /api/v1/plants/from-identification not yet merged (T2.8 backend); frontend will 404 until then

### Mobile responsiveness — Completed (2026-06-14)
**Changes:**
- `app.component.ts` — removed AI Test from `navLinks` (was a duplicate; AI Test has its own element in the toolbar)
- `app.component.html` — added `.bottom-nav` bar (4 main links) hidden on desktop, shown on mobile; added `[class.with-bottom-nav]` to `<main>` so content gets `padding-bottom: 60px` on mobile; AI Test link gets `.ai-test-link` class for easy hiding
- `app.component.scss` — bottom nav styles (fixed, 60px, white bg, border-top); `.nav-links` + `.ai-test-link` hidden at ≤768px; `.with-bottom-nav` adds `padding-bottom: 60px` at ≤768px; `env(safe-area-inset-bottom)` for iOS notch; active state color `#43a047`
- `styles.scss` — global `.page-container` reduces to `padding: 16px 12px` at ≤600px
- `identification-page.component.scss` — top padding reduced to `16px` on mobile; subtitle bottom margin reduced
- `plant-form.component.scss` — `.form-container` padding 16px/12px on mobile; `.form-card` inner padding 16px on mobile
- `plant-detail.component.scss` — `.photo-banner` height 180px (was 240px) on mobile; `.plant-name` font-size 1.4rem on mobile
- `preview-card.component.scss` — `.photo-banner` height 200px (was 260px), border-radius 8px on mobile; `.action-row` stacks vertically at ≤400px

**Architecture note:** Bottom nav uses `routerLinkActive="active"` — active styling is automatic. No Angular Material BottomNav needed; simple CSS fixed-position flex bar.

**proxy.conf.json** — `/photos` proxy entry was added in T2.8 session (fixes photo 404 in dev). Requires `ng serve` restart to take effect.

### T2.9 — Photo annotator — Completed (2026-06-14)
**Files changed:**
- `identification/models/identification.model.ts` — added `AnnotationRegionType`, `AnnotationRegion` interface (normalized 0–1 coords), `annotationRegions: AnnotationRegion[] | null` on `IdentificationResponse`
- `identification/components/photo-annotator/` — new `PhotoAnnotatorComponent`:
  - `@Input() imageUrl: string` + `@Input() regions: AnnotationRegion[] | null`
  - Canvas drawn on `(load)` event + `ResizeObserver` for redraws on viewport change
  - `PLANT` → blue `#1565C0`, `DISEASE` → red `#c62828`, `HEALTHY_AREA` → green `#2e7d32`
  - Label pill (colored rect + white text) drawn above each bounding box
  - "Show/Hide annotations" toggle button only rendered when `hasRegions === true`
  - No canvas rendered at all when regions are null/empty — just plain `<img>`
- `identification/identification.module.ts` — declared `PhotoAnnotatorComponent`
- `identification/components/identification-result/identification-result.component.html` — added `<app-photo-annotator>` at the top of mat-card-content
- `identification/components/identification-result/identification-result.component.scss` — `.photo-section { margin-bottom: 16px }`

**Architecture notes:**
- Canvas is `position: absolute` over the `<img>` (both inside `position: relative` `.photo-frame`)
- `pointer-events: none` on canvas lets users interact with the underlying image
- `canvas.width/height` set to `img.clientWidth/Height` on each draw so pixel coords match rendered size
- `annotationRegions` will be null until backend migration 007 ships — component degrades gracefully to plain img

### T2.9b — Polygon rendering + plant-detail Last Scan tab (Complete 2026-06-15)
**Files changed:**
- `identification/models/identification.model.ts` — added `PolygonPoint { xPct, yPct }`; `boundingBox` made optional (fallback); `polygon?: PolygonPoint[]` added to `AnnotationRegion`
- `identification/components/photo-annotator/photo-annotator.component.ts` — replaced `*ngIf` with `[hidden]` on canvas (ViewChild timing fix); `@ViewChild static:false`; polygon rendering via `drawPolygon()` private method; `drawLabel()` extracted as shared private method; label at centroid for polygon, above top-left for bounding-box; lint error fixed (`imageUrl = ''` no explicit type annotation); `ngOnChanges` handles late-arriving regions with `setTimeout`
- `plant/components/plant-detail/plant-detail.component.ts` — fetches `latestPhotoUrl` + `latestAnnotationRegions` from latest identification
- `plant/components/plant-detail/plant-detail.component.html` — "Last Scan" tab added with `<app-photo-annotator [imageUrl] [regions]>`
- `plant/components/plant-form/plant-form.component.ts` — replaced `this.editId!` with `this.editId ?? 0` (no-non-null-assertion lint fix)

**Architecture notes:**
- Priority: polygon → bounding-box → skip (graceful degradation per region)
- `PhotoAnnotatorComponent` in `CarePlanModule` → exported to both `IdentificationModule` and `PlantModule`
- `drawPolygon` maps `polygon[].{xPct,yPct}` to canvas pixels; closes path; fill + stroke
- `drawLabel` shared between polygon centroid labels and bounding-box pill labels

### T2.9c — Annotation list + disease detail panel (Complete 2026-06-15)
**Files changed:**
- `identification/services/identification.service.ts` — added `getCureAdvice(id, regionLabel, species)` → POST `/{id}/cure-advice`, returns `Observable<string>`
- `identification/components/annotation-list/annotation-list.component.{ts,html,scss}` — NEW: clickable region list, color dot + label + confidence/type badges, toggle-deselect emits `regionSelected(index|null)`
- `identification/components/disease-detail-panel/disease-detail-panel.component.{ts,html,scss}` — NEW: visible only when `region.type === 'DISEASE'`; "Ask for cure" with loading/error/advice states; "Add to care plan" disabled + tooltip
- `identification/components/care-plan/care-plan.module.ts` — declared/exported both new components; added MatCardModule, MatProgressSpinnerModule, MatTooltipModule
- `identification/components/photo-annotator/photo-annotator.component.ts` — `selectedRegionIndex: number|null` @Input; non-selected regions dimmed (low-opacity grey fill + #ccc stroke); selected region 3px stroke + full opacity; ngOnChanges redraws on input change
- `identification/components/preview-card/preview-card.component.{ts,html}` — `selectedRegionIndex` @Input pass-through to inner photo-annotator
- `identification/pages/identification-page/identification-page.component.{ts,html}` — selection state + `onRegionSelected()`; annotation-list + disease-detail-panel wired in preview state; reset clears selection
- `plant/components/plant-detail/plant-detail.component.{ts,html}` — `latestIdentificationId`, selection state, `onRegionSelected()`; annotation-list + disease-detail-panel in Last Scan tab; fixed NG8107 (`plant?.species` → `plant.species`)

**Architecture notes:**
- `AnnotationListComponent` owns internal visual selected state; parent owns `selectedRegionIndex`/`selectedRegion` as business state — avoids circular binding
- `DiseaseDetailPanelComponent` injects `IdentificationService` directly — DI resolves from parent module (IdentificationModule or PlantModule), no CarePlanModule provider needed
- Backend `POST /api/v1/identifications/{id}/cure-advice` is LIVE (T2.9d merged PR #15) — disease panel will work after docker rebuild
- "Add to care plan" is permanently disabled — needs care-plan mutation endpoint (Phase 3)

### AddChooseAi — AI model selector UI (branch: AddChooseAi — assigned)
**Files to create/modify:**
- `core/models/user.model.ts` — add `export type AiModelPreference = 'DEEPSEEK' | 'PLANTNET' | 'OLLAMA_LLAVA'` and `export interface UserPreferences { aiModelPreference: AiModelPreference }`
- `core/services/user.service.ts` — NEW service (constructor-injected HttpClient):
  - `getPreferences()`: cache-first via `sessionStorage` key `'ai_model_preference'`; if cached return `of(cached)`, else GET `/api/v1/users/me/preferences` and store in sessionStorage
  - `updatePreferences(pref)`: PUT `/api/v1/users/me/preferences`, on success update sessionStorage
- `shared/components/model-selector/model-selector.component.{ts,html,scss}` — NEW:
  - `mat-button-toggle-group` with three options: PLANTNET (eco icon), DEEPSEEK (psychology icon), OLLAMA_LLAVA (computer icon)
  - On init: `getPreferences()`, set selected toggle
  - On change: disable group, show spinner (diameter 20), call `updatePreferences()`, snack-bar on success, revert + error snack-bar on failure, re-enable group
  - Constructor injection: UserService, MatSnackBar (no inject())
  - takeUntil + ngOnDestroy
- `shared/shared.module.ts` — declare + export `ModelSelectorComponent`; import `MatButtonToggleModule, MatIconModule, MatSnackBarModule, MatProgressSpinnerModule, CommonModule`
- `app.component.html` — add `<app-model-selector class="model-selector-toolbar">` in mat-toolbar after nav links, before user menu button
- `app.component.scss` — `.model-selector-toolbar { display: none; }` at `@media (max-width: 768px)`
- `app.module.ts` — import `SharedModule` if not already imported

**Architecture notes:**
- `UserService` goes in `core/services/` (not `features/`) — it's a global concern used across the shell
- `ModelSelectorComponent` goes in `shared/` — it's app-shell level, not feature-level
- SessionStorage cache: survives navigation, cleared on browser close; correct for a "session preference"
- Backend API contract: `GET/PUT /api/v1/users/me/preferences` → `ApiResponse<{ aiModelPreference: string }>`

### T2.10 — Garden dashboard + photo timeline ✅ (Complete 2026-06-18)
**Files added:**
- `features/dashboard/` — new lazy DashboardModule: dashboard.module.ts, dashboard-routing.module.ts
  (single route, GardenDashboardComponent), models/dashboard.model.ts (mirrors backend DTOs
  field-for-field), services/dashboard.service.ts (getDashboard() → GET /api/v1/dashboard)
- `pages/garden-dashboard/` — health summary chips, "Needs attention"/"Today"/"Health trends"
  sections (each omitted entirely if empty), full empty-garden state when totalPlants === 0
- `plant/components/plant-photo-timeline/` — horizontally-scrollable strip of every past scan for
  a plant, reverses the API's newest-first order so it reads oldest→newest left→right, health-colored
  thumbnail border, click → `/identify/:id`. Sits at the top of plant-detail's Overview tab.
- `app-routing.module.ts`: root redirect changed from `'plants'` to `'dashboard'`; new guarded
  `'dashboard'` route. Bottom nav left at 4 items on purpose (see T2.D3 history on toolbar squeeze).

### T2.10e — Session polish ✅ (Complete 2026-06-18)
**Annotation overlap (photo-annotator.component.ts):** when a region is selected, non-selected
regions are now skipped entirely (`continue` in the draw loop) instead of drawn dimmed — they
overlap the selected region heavily, so dimming still visually competed. Removed dead `DIMMED_COLORS`.

**CSS gotcha found + fixed (photo-annotator.component.scss):** the toggle's `[hidden]` binding was
toggling correctly the whole time (verified via Playwright: button label + DOM attribute both
flipped, zero console errors) but had no visual effect, because `styles.scss`'s global
`canvas { display: block; }` reset silently overrides the browser's own `[hidden] { display: none; }`
default — author-origin CSS always beats user-agent-origin CSS regardless of selector specificity.
Fix: `canvas[hidden] { display: none !important; }` scoped in the component's own stylesheet (now an
author-vs-author fight, where the attribute selector wins on specificity too). **Any future
`<canvas>`/`<img>`/`<video>`/`<svg>` relying on `[hidden]` needs this same per-tag override.**

**"Add to care plan" made functional:** `IdentificationService.addCareCard(identificationId,
regionLabel, adviceText)` → POST `/{id}/care-plan/cards`. `DiseaseDetailPanelComponent` calls it
after cure advice loads (loading/added button states), emits `@Output() carePlanUpdated` so the
parent replaces its local `carePlan`/`latestCarePlan` reference — wired in both
`identification-preview-section` and `plant-detail`. The pre-advice button stays disabled (nothing
to add yet); tooltip corrected from the stale "Available after saving plant" to "Get cure advice first".

**Garden "add" entry points → identification dialog:** `plant-list`'s FAB and empty-state CTA now
call `openIdentifyDialog()` (opens `IdentificationUploadDialogComponent`, same one identify-page
uses) instead of `routerLink="/plants/new"`. On submit, posts via `IdentificationService.analyze()`,
snackbar, then `router.navigate(['/identify'])`. Required adding `MatDialogModule` to
`plant.module.ts`. Cross-module component import (PlantModule importing a component declared in
IdentificationModule) is fine for Ivy/AOT — each component carries its own compiled scope — but it
did split webpack's `features-plant-plant-module` lazy chunk into two pieces (~68KB extra,
confirmed via `ng build` output). That's an expected trade-off of the cross-module import, not a bug.

### T3.2 — Reminder Angular frontend + PWA push ✅ (Complete 2026-06-18)
**Files changed:**
- `features/reminder/reminder-list/` — rewired from hardcoded mock data to real
  `ReminderService.getUserReminders()` / `completeReminder()`
- `features/reminder/components/create-reminder-form/` — NEW: plant selector, care type, frequency,
  first due date
- `features/reminder/components/care-calendar/` — NEW: 7-day view of what's due each day
- `features/reminder/components/care-log/` — NEW `CareLogModule` + `CareLogComponent` (timeline of
  past care actions per plant); plugged into `plant-detail`'s "Care History" tab, replacing the
  static "coming in Phase 3" placeholder that had been there since T2.9
- `core/services/push-notification.service.ts` — NEW: `requestPermission()`,
  `subscribeToNotifications()` (posts the `ServiceWorkerRegistration` `PushSubscription` object to
  `POST /api/v1/notifications/subscribe`)
- `app.component.ts/html` — inline "Get reminders on your phone" banner on first load (logged in +
  not yet asked), Accept/Dismiss — own UI shown before the native browser permission prompt
- `environment.ts`/`environment.prod.ts` — `+vapidPublicKey`
- `plant.module.ts` — `+CareLogModule`, `+CareLogService`

**Architecture note:** landed on the same branch as T3.1 (`feature/PP-011-reminder-module`) rather
than the separate `feature/PP-012-reminder-frontend` the original plan named — backend and frontend
shipped together in one PR.

**Bug found + fixed (2026-06-18):** `care-log.service.ts`'s `baseUrl` was
`${environment.apiUrl}/care-logs` — the real `CareLogController` is mapped at `/api/v1/care`, not
`/api/v1/care-logs`. Every `getPlantCareLogs()` request 404'd silently (the component's `error`
callback just sets `loading = false`, no message shown), so `plant-detail`'s "Care History" tab
always rendered empty regardless of actual data. Found via live testing, not caught by `ng
build`/`ng lint`/unit tests since none of those exercise the real HTTP route. Fixed by correcting
`baseUrl` to `${environment.apiUrl}/care`. Double-check any other frontend service's `baseUrl`
against its controller's actual `@RequestMapping` rather than assuming the name matches the
feature folder — `reminder.service.ts` and `push-notification.service.ts` were both checked in the
same pass and are correct (`/api/v1/reminders`, `/api/v1/notifications`).

### T3.5 — Actionable care plans UI: Mermaid diagrams + "Set reminder" / "Start treatment plan" ✅
(Complete 2026-06-18, branch: feature/PP-028-actionable-care-plans-2 — same branch as T3.4 backend)

**Files added:**
- `shared/components/mermaid-diagram/` — `MermaidDiagramComponent`, declared+exported from
  `SharedModule` (not lazy, since both `identification` and `reminder` feature modules need it).
  Dynamically `import('mermaid')`s itself so the package's own internal per-diagram-type chunks
  never touch the initial bundle.
- `identification/components/care-plan/set-reminder-dialog.component.{ts,html,scss}` — small
  ~360px MatDialog, ReactiveForms (FormBuilder, matches `create-reminder-form`'s pattern, not
  ngModel), declared in `CarePlanModule` (not exported — only ever opened programmatically)
- `reminder/services/treatment-plan.service.ts` + `reminder/models/treatment-plan.model.ts` —
  `createFromActionPlan()` → POST `/api/v1/treatment-plans`, `getTreatmentPlan(id)` → GET
  `/api/v1/treatment-plans/{id}`
- `reminder/models/care-icon.util.ts` — single shared `CARE_ICONS` Record + `careIcon()` fn,
  replacing four previously-independent copies (reminder-list, care-calendar, care-log; dashboard
  intentionally kept its own — see below)
- `reminder/pages/treatment-plan-detail/` — route `/treatment-plans/:id`; numbered-circle step
  timeline (same visual language as `care-log`'s timeline), optional Mermaid "How this works"
  card, status chip, progress line, compact per-step "Mark done"

**Files changed:**
- `identification/models/identification.model.ts` — `ActionPlanDto`/`TreatmentStepDto`/
  `DiagramDto`, `CareCardDto.actionPlan` (mirrors backend T3.4 DTOs field-for-field)
- `identification/services/identification.service.ts` — `getCureAdvice()` return type changed
  `Observable<string>` → `Observable<{advice, actionPlan}>`; `addCareCard()` gained an optional
  `actionPlan` param
- `identification/components/care-plan/care-card.component.{ts,html,scss}` — new action row
  (ROUTINE → dialog → `ReminderService.createReminder()`; TREATMENT → direct
  `TreatmentPlanService.createFromActionPlan()` call), new `@Input() plantId: number | null` +
  `@Input() existingCareTypes: CareType[]`
- `identification/components/care-plan/care-plan.component.{ts,html}` — passes `plantId` +
  `existingCareTypes` through to every card
- `identification/components/care-plan/care-plan.module.ts` — +`SetReminderDialogComponent`,
  +`ReactiveFormsModule`, +`MatDialogModule`, +`MatFormFieldModule`, +`MatInputModule`
- `identification/components/disease-detail-panel/disease-detail-panel.component.{ts,html,scss}`
  — second "Start treatment plan" button (only when advice's actionPlan is TREATMENT), new
  `@Input() plantId: number | null`
- `identification/identification.module.ts` + `plant/plant.module.ts` — both now also provide
  `ReminderService` + `TreatmentPlanService` (CareCardComponent/DiseaseDetailPanelComponent live
  in the shared `CarePlanModule` but their providers come from whichever feature module hosts them
  — same pattern already established for `IdentificationService`/`CareLogService`)
- `plant/components/plant-detail/plant-detail.component.{ts,html}` — fetches
  `ReminderService.getReminders()`, filters to this plant's enabled care types for
  `existingCareTypes`; passes `[plantId]`/`[existingCareTypes]` to `<app-care-plan>` and
  `[plantId]` to `<app-disease-detail-panel>`
- `reminder/models/reminder.model.ts` — **`CareType` widened 4 → 10 values** (now mirrors
  `CareCardType` exactly — confirmed against the backend's T3.4 STATE.md entry mid-session, not
  assumed); `ReminderResponse` gained `treatmentPlanId`/`treatmentPlanTitle`/`stepOrder`/
  `recurring` (confirmed against backend) + `instruction`/`completedAt` (**frontend-invented,
  unconfirmed** — see STATE.md's "Known gaps" note under T3.5)
- `reminder/services/reminder.service.ts` — `completeReminder()` (POST `/reminders/{id}/complete`,
  never matched a real route) replaced with `markCareDone(reminderId)` → POST `/api/v1/care/done`
  `{reminderId}` — the actual `CareLogController` endpoint from T3.1
- `reminder/reminder-list/reminder-list.component.{ts,html,scss}` — mark-done button now calls
  `markCareDone()`; rows with `treatmentPlanId` show a clickable "Step N · Plan Title" chip
  instead of the plain careType text
- `reminder/reminder-routing.module.ts` + `app-routing.module.ts` — **routing restructure**, see
  STATE.md's T3.5 entry for the full reasoning. Short version: `ReminderModule` now mounts at
  `path: ''` instead of `path: 'reminders'` so its own routing module can define `'reminders'`
  and `'treatment-plans/:id'` as siblings, giving the latter a clean top-level URL instead of
  `/reminders/treatment-plans/:id`.
- `reminder/reminder.module.ts` — +`TreatmentPlanDetailComponent`, +`TreatmentPlanService`
- `dashboard/models/dashboard.model.ts` + `garden-dashboard.component.ts` — same `CareType` 4→10
  widening propagated here too, since it had its own independent duplicate of the type

**Architecture notes:**
- **`plantId` is nullable on both `CarePlanComponent`/`CareCardComponent` and
  `DiseaseDetailPanelComponent`** — `<app-care-plan>`/`<app-disease-detail-panel>` are used in
  `preview-card`/`identification-preview-section` before a plant necessarily exists yet (wired to
  `result.plantId`, which actually IS non-null if the user linked an existing plant during
  upload). The action row/treatment button simply don't render when `plantId` is null — no
  separate "no plant" empty state needed.
- `MermaidDiagramComponent` calls `mermaid.initialize()` exactly once via a module-level boolean
  guard (not in the constructor unconditionally) — calling it on every component instantiation
  would silently no-op on the 2nd+ call anyway, but the guard makes the intent explicit.
- Button success states (`reminderSet`, `treatmentStarted`) are plain component booleans, not
  derived from any persisted backend flag — see STATE.md's "Known gaps" note for the page-refresh
  caveat this creates.
- `Location.back()` (not a fixed `routerLink`) for the treatment-plan-detail page's back button —
  deliberately different from `plant-detail`'s hero-back (`routerLink="/plants"`), because this
  page is reachable from three unrelated places (care-card snackbar, disease-panel snackbar,
  reminder-list chip) with no single correct "back" destination.

### Next tasks (in order)
- T3.3 — Manual testing on a real device (push notification delivery, PWA installability, offline
  reading) — needs a human with a phone, not something to automate
- Phase 4 polish (chat streaming/history) — basic T4.1 chat already works