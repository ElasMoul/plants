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

## Session Notes (updated 2026-06-12)

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
| identification/ | identification-home (stub) + routing + module | ❌ Placeholder only |
| reminder/ | reminder-list (stub) + routing + module | ❌ Skeleton only |
| chat/ | chat-home (stub) + routing + module | ❌ Skeleton only |
| ai-test/ | ai-test component + module | Dev tool |

### Known convention violations in existing code
- plant-list.component.ts: .subscribe() calls without takeUntil/ngOnDestroy. HTTP observables self-complete so no real leak, but violates the stated convention. Flag if touching that file.

### Next task
T2.4 — Build out identification feature module:
- Photo upload UI
- Call PlantNet-backed backend POST /api/v1/identifications
- Display result: species, common_name, confidence, health_status, health_notes, care_tips
- Active branch: feature/PP-010-identification-frontend