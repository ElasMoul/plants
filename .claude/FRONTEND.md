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
core/         — auth service, JWT interceptor, auth guard, models
shared/       — reusable components, directives, pipes
features/
  auth/       — login, register ✅
  plant/      — plant list, form, detail ✅
  identification/ — photo upload, results (in progress)
  reminder/   — care schedule (not started)
  chat/       — AI chat (not started)
layout/       — shell, navbar

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

### Next task
T2.9 — Visual annotation (bounding boxes + disease overlay via Ollama LLaVA)
Branch: feature/PP-017-visual-annotation
Requires migration 007_add_annotation_regions.sql on backend first