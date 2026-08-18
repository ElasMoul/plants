# Atlas Frontend — Integration Task Plan

> A second PlantPal frontend ("Atlas"), the **Rhizome** atlas-class UI, served on
> its own port against the **same backend** (`:8180`). This document is the work
> order. Author: planning session 2026-08-17.

---

## Implementation status (2026-08-18)

All phases implemented on `atlas-integration`, each task branched → merged → verified.

| Phase | Status |
|---|---|
| A1 Angular 16→20 + toolchain | ✅ merged |
| A2 scaffold atlas app (:4300) | ✅ merged |
| A3 `@plantpal/shared-core` library | ✅ merged |
| A4 vendor Rhizome tokens | ✅ merged |
| B rhizome-engine (62 tests) | ✅ merged |
| C world shell renders + travels | ✅ merged |
| D live data assembly + degradation | ✅ merged |
| E cross-app auth + interop | ✅ merged |
| F deploy + constitution QA gate | ✅ merged |

Full test suite green (unit + constitution gate); both apps build (dev + prod);
`rhizome-engine` type-checks. Deferred (noted in commits): D3 create/edit mutations
(need a live backend); Playwright visual snapshots (need regen after the Material
16→20 restyle). Run the app: `cd frontend && npm run start:atlas`.

---

## 0. Locked decisions (owner-approved)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Project layout | **Second app in the existing Angular workspace**; shared code extracted to a library both apps import. |
| 2 | Scope | **Full replacement UI** — every node kind live and traversable against the real API. |
| 3 | Angular version | **Upgrade the workspace to the latest Angular** (standalone + signals + `@if`/`@for`). |
| 4 | Auth | **Shared JWT session** — same token storage/format; logged into one app ⇒ logged into both. |

---

## 1. What we are integrating

- **`frontend-atlas/design-system/`** — the **Rhizome** design system, a **pinned registry clone (v1.0.0)**.
  - **Hard rule:** never hand-edit anything under `design-system/`. It is a byte-for-byte pin of
    `plantpal-cae942ee/v1.0.0`. Gaps become a *proposal back to design-studio*, then a re-pin — never
    a local patch. Our Angular code is an **implementation of** this reference, not an edit to it.
  - Contains the law we must satisfy: `constitution.json` (**C1–C26** invariants, grouped as L1–L10),
    `PRINCIPLES.md`, `traversal-map.json`, `layout-determinism.json`, `degradation-spec.json`, plus
    reference HTML for every component and foundation.
- **`frontend-atlas/theme-a/`** — a self-contained **3,559-line HTML/CSS/JS prototype** of the running UI
  (`index.html`), its design tokens (`tokens.css`, 801 lines), and per-theme spec JSON. This is the
  visual + behavioural ground truth to port. Nodes in it are already annotated with the backend they map
  to (e.g. `data-brief-item="action:/api/v1/species/**"`).

### The paradigm (why this isn't a normal Angular app)
One **plane**; domain records are **nodes** on a **lattice**; **veins** join them; a **camera travels**
(rescale + translate) instead of routing. Nothing mounts/unmounts; the place you left stays in sight.
The invariants that constrain every task below:

- **Determinism (C7–C9):** a node's position is a *formula* — `origin + cell × pitch + persisted offset`.
  New data, red status, slow endpoints — none move anything. Only two things move a node: the user
  dragging it in Arrange mode, and a genuinely new node taking a free cell.
- **Rank = computed (C3, C12):** breadth-first graph distance from the focus, recomputed every hop, spent
  on **size** (four card widths: fringe/far/near/focus).
- **One motion (C10, C11, C13, C21):** exactly **300ms**, one code path, camera centre travels **along the
  vein polyline** by arc length; reduced-motion still draws the trail and moves the you-are-here.
- **Per-node truth (C22–C25):** slow/stale/absent/failure are drawn **inside the node**, in its own words —
  never a global banner, spinner, modal, or the word "loading". Unknown ≠ empty.
- **Register (C14–C17):** chrome (edges, minimap, camera) is flush to the viewport, takes no shadow, never
  moves with the camera; nodes float and move with it. Material separation, not a second aesthetic.

---

## 2. Target architecture

```
frontend/                      # existing Angular workspace (multi-project after Phase A)
├── angular.json               # now declares: plantpal (existing) + atlas (new) + libraries
├── proxy.conf.json            # shared → backend :8180 (/api, /photos)
├── projects/
│   ├── plantpal/              # ← existing app, moved under projects/ (or left at src/, see A2)
│   ├── atlas/                 # ← NEW app: the Rhizome shell, port 4300
│   │   └── src/
│   │       ├── app/
│   │       │   ├── shell/         # #world plane, #shell camera transform, chrome furniture
│   │       │   ├── nodes/         # node component (per kind × rank), density rule
│   │       │   ├── veins/         # SVG edge layer + clickable hit strokes
│   │       │   ├── panels/        # focus-panel, action-rail, minimap, navigate-to
│   │       │   └── world/         # world-assembly service (nodes+edges from the API)
│   │       └── styles/            # imports vendored tokens.css
│   ├── shared-core/           # ← NEW lib: auth.service, jwt interceptor, guard, api models,
│   │                          #   domain API services (plant/species/treatment/reminder/…)
│   └── rhizome-engine/        # ← NEW lib: framework-agnostic pure-TS engine (Phase B)
└── vendor/rhizome-ds/         # read-only pinned copy of design-system/ (CI-guarded, never edited)
```

- **`rhizome-engine`** is **pure TypeScript, zero Angular** — lattice math, rank BFS, clearance pass,
  camera arc-travel, particle field. This is where the constitution's determinism lives and where it is
  unit-tested. Angular only *renders* engine state.
- **`shared-core`** is the seam that makes the JWT session shared: both apps inject the same
  `AuthService` + JWT interceptor + the same token storage key/format.
- **No backend change required for MVP.** The world graph is assembled **client-side** from existing
  endpoints. (Optional later: a `/api/v1/atlas/world` aggregate endpoint on PlantPal's own backend —
  no `contracts`/demand involvement since it's PlantPal's own service.)

---

## 3. Serving & ports

| Env | Existing app | Atlas app | Backend |
|-----|-------------|-----------|---------|
| Dev | `ng serve plantpal` → **:4200** | `ng serve atlas --proxy-config proxy.conf.json` → **:4300** | native :8080 / docker :8180 |
| Prod | compose `frontend` | **new compose service `frontend-atlas`** (own Dockerfile + nginx, distinct published port) | compose `backend` :8180 |

Both dev servers proxy `/api` + `/photos` to `:8180` via the shared `proxy.conf.json`.

---

## 4. Phased task plan

> Chunked to the house working style: each phase is a reviewable boundary — stop for review at each.
> Suggested branch prefix: `feature/PP-ATLAS-<phase>`.

### Phase A — Workspace & foundation *(highest-risk step first, in isolation)*
- **A1 — Angular upgrade.** Step the workspace 16 → latest via successive `ng update` (Angular core/CLI,
  Material, CDK). **Gate:** existing app builds, `jest` green, `playwright` visual/a11y suites green.
  This is the one step that touches the existing app — do it alone, verify, commit.
- **A2 — Add the `atlas` application.** `ng g application atlas`; configure `angular.json` (dev port 4300,
  global styles, budgets), point it at the shared `proxy.conf.json`; wire Jest + Playwright for it.
- **A3 — Extract `shared-core` library.** `ng g library shared-core`; move `auth.service`, JWT interceptor,
  auth guard, `api-response`/`user` models, and the domain API services into it; repoint existing-app
  imports. **Shared session = identical token storage key + format.** Gate: existing app still green.
- **A4 — Vendor Rhizome + tokens.** Copy `theme-a/tokens.css` into atlas global styles; vendor
  `design-system/` into `vendor/rhizome-ds/` read-only; add a CI check (hash/guard) asserting it is never
  hand-edited.

### Phase B — `rhizome-engine` (framework-agnostic TS, test-first)
- **B1 — Lattice geometry.** Port `ORIGIN`, `PITCH`, the three position layers (home → anchor → placed) and
  `cell → {left, top}`. **Tests:** load twice → identical positions; insert a node → others unchanged
  (C7/C8/C9).
- **B2 — Rank.** BFS graph distance from focus → `focus | near | far | fringe`, recomputed per hop.
  **Tests:** rank map matches expected on a fixture graph; blur-invariant hierarchy (C3/C12).
- **B3 — Clearance pass.** Focus pinned/centred between the rails; direct neighbours placed on the
  measured-radius ellipse at their lattice angle; separation pass. **Test:** no neighbour overlaps the
  focus by construction; non-neighbours may leave frame.
- **B4 — Camera travel.** Single 300ms path; centre travels **along the vein polyline** by arc length;
  reduced-motion variant draws + holds the trail and still moves you-are-here. **Test:** sampled camera
  centre lies on the route polyline every frame (C10/C11/C13/C21).
- **B5 — Particle field.** Deterministic fixed-seed painter, token-coloured, "almost invisible" drift for
  non-focused nodes; whole-page canvas vs in-`#shell` canvas swap for the settings view.

### Phase C — Atlas shell & rendering (Angular over the engine)
- **C1 — Shell.** `#world` plane + `#shell` transformed by `--cam-k`/translate bound to signals; chrome
  furniture (edges, minimap, camera controls, live region) as standalone components. **A hop is never a
  router navigation** — the element set is stable (C1/C2/C4).
- **C2 — Node component.** Renders by `kind` (species/plant/problem/journal/guide/collection/region/
  platform) × `rank`; signature tick channel; recap line/note; **density rule** (<4 draw all, ≥4 draw two
  highest-ranked + one traversable "+N more" aggregate).
- **C3 — Veins & panels.** SVG edge layer with **invisible 20px hit strokes** → click a vein to travel it;
  focus-panel; navigate-to; action-rail.
- **C4 — Engine ↔ Angular wiring.** `hop = engine.go()` → recompute rank/clearance → camera travel; `@for`
  renders the node set. Gate: 10 hops leave the element set identical and `history.length` unmoved (C1/C4).

### Phase D — Live data (map the world to the real API)
- **D1 — World-assembly service.** Build nodes + edges from existing endpoints (`/species`, `/plants`,
  `/treatment-plans`, `/reminders`, care logs/journal, `/dashboard`). Client-side graph; stable node IDs
  so cells persist across loads (C7).
- **D2 — Per-node detail.** Lazy-load each kind's record into the focus-panel (species record, plant record,
  problem/treatment course, journal) via `shared-core` services.
- **D3 — Mutations.** Create species/plant, start treatment, log care, add journal — wired to the action-rail
  through existing service methods. Enforce "these change data, they never move the camera."
- **D4 — Per-node degradation (C22–C25).** Skeleton on slow (>10s, same block shapes, no reflow on arrival);
  unknown (dashed + real fetch) vs empty (dashed + a way to begin), drawn differently; failure card naming
  fact/time/data-state/**two ways forward**. No global spinner or banner anywhere.

### Phase E — Cross-app integration & auth
- **E1 — Shared session verified.** Log in on 4200 → authed on 4300 and vice-versa; guard on atlas; token
  expiry/refresh parity across both apps.
- **E2 — Interop links.** Keep the existing app as the "classic" UI on 4200; define any deep-links between
  the two (atlas node ↔ classic feature page) that a full-replacement rollout still wants as a fallback.
- **E3 — Env/proxy config.** Atlas dev proxy + prod environment files.

### Phase F — Build, deploy & the acceptance gate
- **F1 — Prod build + deploy.** Atlas production build config; Dockerfile + nginx; **new `frontend-atlas`
  compose service** on a distinct published port; CI builds both apps.
- **F2 — Constitution QA harness *(the acceptance gate)*.** Automate the pinned specs as Playwright/e2e
  assertions:
  - `layout-determinism.json` → diff concatenated `left/top` of all nodes across reload / insert / every
    degraded state → **identical**.
  - `traversal-map.json` → every documented hop reaches the right focus with the camera on the polyline.
  - `atlas-degradation-spec.json` → walk every state from the probe panel; geography unchanged each time.
- **F3 — A11y, reduced-motion, performance.** Guidelines pass; particle-field + camera fps budget.
- **F4 — Docs sync.** `FRONTEND.md` (atlas section), `DEPLOYMENT.md`, `STATE.md`, `TASK_PLAN.md`; platform
  `HEXAGON.md`/`app-manifest.yaml` if a second served surface is registered.

---

## 5. Risks & open items

1. **Angular 16 → latest is the one shared-blast-radius step.** Angular Material's v16→v17+ theming/API
   changes hit the *existing* app. Mitigation: Phase A1 alone, full test suite as the gate before anything
   else starts.
2. **`design-system/` is a pin, not source.** Any "the reference is wrong/missing X" finding is a proposal
   back to design-studio + re-pin — not a local edit. Build against the reference, don't mutate it.
3. **theme-a vs theme-b — RESOLVED.** v1 ships **exactly the one theme in `theme-a/` (Vascular · Sill
   Line)**. The interface/palette settings toggle to the "Glasshouse Table" theme is **out of scope** for
   v1 (its assets aren't in this folder and would need sourcing/re-pinning first). Build only what
   `theme-a/index.html` renders.
4. **World graph source.** MVP assembles the graph client-side from existing endpoints (no backend work).
   If hop latency or N+1 fan-out hurts, add a PlantPal-owned `/api/v1/atlas/world` aggregate (no contracts
   demand — it's PlantPal's own service).
5. **Arrange mode persistence.** Per-user node offsets (C: "the user's own persisted offset") need a store —
   decide localStorage (client-only) vs a small backend preferences field.

---

## 6. Suggested sequencing

`A1` (upgrade, verify green) → `A2–A4` (scaffold) → **review** →
`B1–B5` (engine, test-first) → **review** →
`C1–C4` (shell renders a static fixture world) → **review** →
`D1–D4` (real data + degradation) → **review** →
`E` (auth/interop) → `F` (deploy + acceptance gate) → **v1**.

First visible milestone: end of **Phase C** — the Rhizome world renders and travels on a fixture, proving
the engine + shell before any API wiring.
