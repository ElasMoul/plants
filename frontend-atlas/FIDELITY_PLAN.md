# Atlas Fidelity Replan — Phase H

> Owner review (2026-08-18): the served atlas (:8445) was compared against the
> mission's approved round-9 prototype and found far short — "the nodes, the
> settings page, the action buttons, the node sizes, almost everything missing."
> This plan is the result of the demanded deep dive into mission
> `cae942ee-927d-40f5-ad8f-1d72122d1a1f` (design-studio). It supersedes the
> C/D scope of INTEGRATION_PLAN.md; Phases A/B/E/F/G stand.

## What the deep dive established

1. **Our pinned `theme-a/` is byte-identical to the mission's round-9 artifact** —
   the reference is right; the gap is implementation coverage.
2. **The mission is at stage `coverage-gate`** with a `coverage-scope.json` that
   defines implementation **round 1**: the `identify → species → plant` spine
   (`/plants/**`, `/species/**`, `/identifications/**` incl. async polling),
   auth, `app.health`, platform `dimension.event`/`state.event` feeds, and the
   error/empty/loading mechanisms. Care/reminders/treatment-plans are round 2;
   dashboard/notifications/users round 3; chat/plantnet last. ⚠️ My Phase-D
   assembly leaned on `/dashboard` — explicitly deferred by the mission; must be
   reworked onto the spine.
3. A full UI inventory of the 3,559-line prototype was produced (below, as the
   acceptance checklist). The engine (Phase B) is faithful and stays; the
   Angular shell (C/D) covers only a fraction of the surface.

## Standing owner decisions (unchanged)

- Login/landing stay on the classic app (:8444); the "Continue into the Atlas"
  checkbox + fragment session-handoff is the entry path (Phase G — shipped).
  The prototype's in-atlas auth panels (`n-account`) therefore render as
  *informational* state panels, not forms.
- v1 ships the one theme in the folder (Vascular · Sill Line) — but note the
  prototype itself carries the interface/palette Settings switcher (Sill Line ↔
  Glasshouse Table + 7 palettes) as a built surface; H4 implements the switcher
  as it exists in the pinned tokens.css (both interfaces' tokens are present).

## The fidelity checklist (from the prototype inventory)

### H1 — Structural shell, tokens, cards at rank ✅-when
- `#shell`/`#plane`/`#world` frame; body data-attrs (`data-mode/net/speed/drag/motion/measuring`);
  `#motes-wash`; skip-link; focus ring; `data-palette`/`data-ui` on `<html>`.
- Two mote canvases (`#motes` fixed full-page, `#motes-app` inside the shell),
  one painter (engine B5), colours re-read from CSS on palette change.
- Card structure: `.n__head` (thumb glyph well / kind / name / tools), `.n__body`;
  ranks: fringe `width:auto` + cap 7rem/0.62, far 9rem, near 11.5rem, focus
  `clamp(26rem,34vw,32rem)` + `max-height ÷ var(--cam-k)`; signature tick
  (length=rank 1.25/1.75/3/7rem, colour=kind, 2px); kind colours per kindkey;
  hover/passing/dragging states; only the focused body scrolls.
- **Measured clearance**: `measureBoxes()` with `--cam-k` pinned to 1 +
  `body[data-measuring]` transition-kill; clearance runs on real offsetWidth/
  Height (fringe is width:auto — sizes cannot be assumed).
- `fitFocus()` (zoom yields, floor 0.28, mirrors card-reach/air) + `freeCentre()`
  measuring the live rails with the <240×200 fallback.

### H2 — Card material + show modes
- `data-show` recap/full/skel; MIN/AUTO/FULL `.n__modes` pin (survives hops,
  re-runs clearance) + `.n__grip` single-card drag; skeleton shapes
  (`--plate/--name/--sub/--line/--grid/--cell/--row`, breathe animation gated on
  `prefers-reduced-motion`).
- Focus-body material: `.plate` (pure-CSS specimen, score chip, rung-100 name,
  serif italic binomial), `h3.sec`/`h4.subsec`, `.rows/.row/.pair`, `.doc-link`
  (real anchors that travel), `.tag` (5 variants), `.conf` + bar, `.stake`
  (mutation, terracotta) / `.stake--quiet`, `.hop`/`.hop--block` (traversal),
  `.state` panels (base + error/empty/unknown/loading variants, with
  `data-brief-item` ids), `.feed`, `.empty-plot`, per-node `.staleness` copy,
  `.pending` (slow probe).
- The 25-node roster's full bodies ported verbatim from the prototype (n-fig's
  plate + four-questions + about + confidence + activity; n-platform's six
  panels; n-ident's failure card; n-account's three auth panels; etc.).
- go() ordering law: present real → measure → computeTargets → skeleton during
  travel → real content + fitFocus + el.focus() on arrival.

### H3 — Chrome (all fixed furniture)
- `#topbar`: brand ❧, "You are here" + `#crumbs` breadcrumb (path with
  backtrack-truncation, clickable, aria-current), search field (name substring →
  announce + go), `#bell` → n-reminders, account chip.
- `#rail` (6 square app buttons, Settings wired), `#actions` mutation rail
  (per-focus ACTIONS map, terracotta ▪, offline queuing copy, empty for
  account/today/ask), `#navto` travel rail (degree title, per-neighbour buttons
  with recap smalls, hover → `data-hint` on the vein, "Show all connections"),
  `#atlas` minimap (read-only role=img; mm-edges, 4×4 rect dots — near=ink,
  moved=terracotta; viewport rect; you-are-here ring; extent = lattice ∪ live
  positions, camera excluded) + `#zoomstack` (+ / ◎ / − / ✥ arrange), `#camera`
  (⌕− zoom out, ▢ fit-all), `#gesture` legend, `#live` visible readout
  (1800/2600ms), `#probe` (Slow/Offline/Reduced toggles), `#offline-bar`,
  `#drag-banner`. Chrome register law: no shadow, no tick, one key rule per
  attached edge; `#actions` bottom offset derived from `--vs-atlas-h`.

### H4 — Modes, interaction, decoration
- Keyboard on `#world` (role=application): arrows walk veins directionally
  (±40px filter, hint + announce), Enter/Space travel, Tab cycles neighbours,
  Escape = crumb-back / leave arrange.
- Alt+wheel zoom (1.12/0.89, clamp 0.28–1.9, pointer-anchored); bare wheel left
  to the focus body; drag-pan (grab/grabbing, refuses on .n / .vein-hit).
- Arrange mode: full choreography (anchors-only layout, all cards full, chrome
  dimmed/inert, banner, per-card drag ÷ cam.k, dx/dy persisted, minimap dot
  terracotta, camera saved/restored, Escape exit).
- Settings/overview: shell scales to 0.42 (motes swap canvases), `#overview`
  caption + inert toolbar + settings panel (nav 9 items, Appearance pane:
  interface picker with palette coupling, palette pickers filtered by
  `data-for-ui`, motion rows), 4 exits + capture-click on the shell;
  interface switch re-reads pitch from CSS and relayouts (cells never change).
- Probe states: slow (SLOW_NODES pending), offline (staleness + dimmed stakes +
  bar), reduced (1ms transitions, drift/motes stop and restart).
- Veins: mid-point dots, trail on route, markPassing (arc-length driven,
  destination excluded), data-hint; card drift (~30fps, --jx/--jy only).

### H5 — Data spine per coverage-scope round 1
- Rework world assembly off `/dashboard` onto **plants + species +
  identifications** (dashboard node stays as a deferred-family info panel per
  the prototype's n-today).
- Async identification flow with client polling (the one async family in
  round 1); real `app.health` action on the platform node; `dimension.event` /
  `state.event` feeds surfaced on n-platform (via state-feed if reachable,
  else per-node unknown state).
- In-scope mutations as real stakes (add plant/species, retry scan); offline
  queuing copy. Auth panels informational (owner decision: entry via classic).
- Error/empty/loading exercised against the live backend.

### H6 — Extended QA gate
- Extend constitution.spec: geography diff across ALL probe states incl. slow/
  offline; crumb truncation; go() ordering; fitFocus floor.
- Playwright e2e over the running app: traversal-map walk, degradation-spec
  walk from the probe panel, layout-determinism reload diff. Visual snapshot vs
  prototype at matched viewport for the initial board.

## Sequencing & review points

H1 → H2 (board looks right) → **owner review vs prototype** → H3 (chrome) →
H4 (modes) → **owner review** → H5 (live spine) → H6 (gate) → merge.
Branch-per-wave (`atlas/H1-…`), merge to `atlas-integration`, verify, then main —
same rhythm as A–G.

## Notes for implementers
The design-system component docs are normative and use the `rz-`/`RZ.*` naming —
mirror their API names where possible. 38 "easily missed" behaviors were
cataloged in the deep dive (measure pinning, skeleton-during-travel-only, crumb
truncation, Tab hijack, minimap non-interactivity, LCG seed, drift determinism,
`.stake--queue` unused, kindkey≠kind-label cases, etc.) — treat the checklist
above as the acceptance list; do not re-derive from memory.

---

# Phase I — Rounds 2 & 3 (the care loop, the dashboard, the account, settings)

Shipped on `atlas/R2-care-loop` in nine slices (S0-S8). Same checklist style as
H1-H6: each item is what actually landed, not what was hoped for.

### I1 — The mock garden (S1)
- Seam is **HTTP + AuthService**, chosen once in `main.ts` before
  `bootstrapApplication`. `WorldGraphService`, `WorldActionsService`,
  `world.assembly.ts` and `WorldStore` are untouched by mock mode and cannot tell
  which backend answered.
- `app/core/mock-mode.ts` — `MOCK_MODE` token, `provideMockModeOff()` for specs,
  `resolveMockMode(win, env)`: `?mock=1|garden|day-zero|empty|outage` (enables,
  `empty` maps to `day-zero`) / `?mock=0|off` (disables) wins, then
  `localStorage['atlas_settings'].data.source`, then `environment.mockByDefault`
  (false everywhere). The `mock` param alone is scrubbed with
  `history.replaceState`; every storage access is in a try/catch.
- `app/mock/mock.dataset.ts` — three scenarios built from `Date.now()` offsets so
  the garden is always "today": **garden** (1 user, 6 plants, 5 species, 5 scans
  including one PENDING and one FAILED, 7 routine reminders, 3 plans with step
  reminders, 5 treatments across every status, 10 care logs, preferences with one
  unavailable model), **day-zero** (the user and nothing else), **outage** (the
  garden plus 503s on `/reminders`, `/dashboard` and one plan).
- `app/mock/mock-backend.ts` — a stateful in-memory backend with the server's own
  semantics: envelopes, `Page` shapes vs the bare array on `/reminders`, the bare
  204 on `DELETE /reminders/{id}`, the completion cascade (last step, then plan
  COMPLETED, then treatment COMPLETED, then plant pointer cleared), the 400 on a
  re-completed step, the 429-once on `craft-plan`, poll ticks for pending scans
  and pending descriptions, and `stripNulls` so absent fields are absent.
- `app/mock/mock-api.interceptor.ts` + `mock-auth.service.ts` — the interceptor
  answers only URLs under `API_BASE_URL` and only while enabled; the auth stub
  never writes `plantpal_token`/`plantpal_user`, so the classic app on a shared
  origin is never fooled into thinking it has a session.
- Visibility (day-zero Law 5): while mock is on the topbar sub-label reads
  **"Botanical Network · mock garden"**. Sample records never appear without it.

### I2 — Loader, insertion-stable layout, per-family tolerance (S2, S3)
- `WorldGraphService.load()` fetches rounds 2/3 alongside the spine: `/reminders`
  (bare array), `/dashboard`, `/users/me/preferences`,
  `/plants/{id}/active-treatments` for treated plants, `/treatment-plans/{id}`
  for their plans, and `/care/plant/{id}` for the plants actually drawn. Every
  fan-out is written `list.length ? forkJoin(list) : of([])`.
- Each family is fetched under its own tolerance: a 503 marks that family failed
  and the rest of the board still arrives. A failure becomes `state:'failed'` on
  its own hub with a fact, a time, the fate of the data and a way forward — never
  a banner, never a blank (C25).
- `layoutCells(nodes, edges, rootId, prior)` — an existing node keeps its cell, a
  new node takes the first free row of its depth column. With `prior` absent the
  centred algorithm is byte-identical to round 1, so FIXTURE_WORLD geometry and
  every H-phase test are unchanged.
- `general.keepFinished` ('session' by default) re-fetches remembered treatment
  ids so a finished course stays on its plant instead of vanishing under the
  reader; a 404 forgets the id. Polling covers pending scans *and* pending
  disease descriptions at `general.pollIntervalMs`.
- `dates.ts` — one place for every "when" word, honouring `general.dateStyle` and
  `notifications.dueWindow`.

### I3 — Round-2 material (S4)
Composed only from classes `rhizome.css` already styles. Titles, `state__id`
strings and explanatory notes are verbatim from `world.bodies.ts`.
- **n-reminders** — routine care as `dl.rows`, most overdue first, each row a
  plant; a notifications panel; `state--empty` on day zero.
- **n-care** — the care guide plus the per-plant care history feed.
- **n-journal** + **n-log-N** + **n-journal-more** — entries are nodes, because
  the prototype roster has them and they are append-only.
- **n-treatments** + **n-treatment-N** + **n-treatments-more** — a course is the
  prototype's `dl.rows` carrying `data-course`, its rows carrying
  `data-done`/`data-due`/`data-paused`, its step stakes carrying `data-step-id`
  and `aria-pressed`. Draft, running, paused, finished and rate-limited are all
  first-class states.
- **n-plant-N** — vitals as `dl.rows[data-vitals]` with `.tag` carrying colour
  AND words, plus a care panel. No control inside the vitals list.
- **n-today**, **n-account**, **n-problems** — see I5.

### I4 — Round-2 actions (S5)
Every label dispatches to a real endpoint or an honest device-local behaviour.

| Label | What it does |
| --- | --- |
| Water plant / Fertilize / Log a watering | `POST /care/done` (or `POST /reminders/{id}/complete`, per `care.completeVerb`); with no schedule, `care.logWithoutReminder` either creates one first or refuses in words |
| Done / Mark today done / Mark step N as done | the same completion call on the step's reminder id |
| Add a reminder / Set a watering schedule | `POST /reminders` |
| Change the schedule | `POST /reminders` then `DELETE /reminders/{old}` |
| Water all | every WATERING reminder in `care.waterAllScope` |
| Start a treatment plan | `POST /treatments` (plus `craft-plan` when `ai.craftPlanOnStart`) |
| Craft the treatment plan | `POST /treatments/{id}/craft-plan` (a 429 gives the AI-limit node state) |
| Finish this course | `PATCH /treatments/{id}/complete` |
| Snooze / Snooze all | **device-local**, written into `DeviceStore`, named as such |
| Pause / Resume this course | **device-local**, `treatment.pause` |
| Add a plant / Add note / Try the scan again / Identify | round-1 verbs, unchanged |

A 400 whose message contains "already been completed" is treated as success and
triggers a resync — classic parity. No write path calls `store.go`, `frameFocus`
or `camera.set`; a mutation ends in `store.say(...)` then `reloadRequested++`.

### I5 — Round 3 (S6)
- **n-today** from `GET /dashboard` — a count, not a feed: doc-link rows only, no
  stakes, and when the dashboard fails the client rule is used and the node says
  so in a row rather than going blank.
- **Notifications** — a real device push flow (permission, `push-sw.js`,
  `PushManager.subscribe`, `POST /notifications/subscribe`), subscribed at most
  once per device, with a five-value PushState; an empty VAPID key is refused in
  words in both the pane and the node. "Unread" and "Mark all read" are
  device-local (`notifications.seenAt`); the Email row reads "Not offered by
  PlantPal".
- **n-account** — the session, the preferences round-tripped through
  `GET`/`PUT /users/me/preferences`, and the device-local profile rows.
- **The bell is an arrival** — it announces the count and the distance first,
  then calls the same `store.go()` a card click uses (C21).

### I6 — Settings (S7)
Nine panes behind the verbatim overlay (`overview.html.ts` is never hand-edited):
General, Profile, Notifications, Appearance, Data & Sync, AI Preferences,
Privacy & Security, Integrations, Advanced. Settings apply live; Cancel restores
the snapshot taken on open; Save persists and closes; "Reset to defaults" applies
`DEFAULT_SETTINGS` live. Server-backed keys (the AI/PlantNet preferences and
`businessTier`) PUT immediately and are undone by neither — the pane says so.
Appearance is restored before first paint; layout persists under
`privacy.rememberLayout`. Constitution parameters (density 4 to 2 + N, travel
300 ms, slow threshold 10 s, shell scale 0.42) are read-only rows under
"Fixed by the constitution — not a setting."

### I7 — The gate (S8)
- `world/constitution-live.spec.ts` runs the laws against the **assembled** board
  (the mock garden's sources through `assembleWorld`, plus the real store,
  actions service and mock backend behind HTTP): C4, C7, C8, C9, C15, C16,
  C17/C19, C22-C26, the density rule for all five node families, and the voice
  bans. The C15/C16 sweep enumerates every stake in every body plus every rail
  action, dispatches each, and asserts focus, camera and the node id set are
  byte-identical. Every failure message names the law it broke.
- `world/constitution.spec.ts` keeps the FIXTURE_WORLD suite and gains the
  placement check: a course row or a step stake never appears before the full
  body.
- Playwright over the running mock garden on :4300 — **no backend, no login, no
  `page.route` stubs**: `boot`, `bodies`, `care-loop`, `treatment-course`,
  `reminders-and-journal`, `today-and-notifications`, `settings`, `degradation`
  and `determinism` (36 tests).
- CI (`frontend-ci`) runs on push and pull_request to `main` and `dev`:
  `npm ci`, `build:prod`, `build:atlas:prod`, `typecheck:engine`, `lint`
  (classic **and** atlas, `rz` prefix), `npm test`, `playwright install
  chromium`, `e2e:atlas` — uploading `playwright-screenshots` on any outcome.

## Standing decisions (2026-09)

Recorded so a later contributor does not "improve" them back.

1. **A course is the prototype's `dl.rows`, not `ol.rz-course`.** The normative
   `rz-` API is mirrored in *attributes* — `data-course`, `data-done`,
   `data-due`, `data-paused`, `data-step-id`, `aria-pressed` — because
   `rhizome.css` has no `.rz-course` rules and the verbatim CSS files must not be
   edited (`tokens-pin.spec` enforces byte identity for `tokens.css`). The only
   new CSS is a handful of additive rules in the atlas's own `styles.scss`.
2. **Vitals are `dl.rows[data-vitals]` with `.tag`** carrying colour AND words
   (vitals Law 2). No new classes, and no control inside the list.
3. **Reminders and care logs are ROWS, never nodes.** Per-reminder nodes would
   sit at BFS depth 2 beside plants, scans and species, so every completion or
   creation would change that layer's membership and re-centre its siblings (C9).
   That is the reason; it is not a shortcut.
4. **Treatments and journal entries ARE nodes** (`n-treatment-<id>`,
   `n-log-<id>`), with density collapse and stable ids derived from backend ids.
   Hubs keep their shipped ids, including `n-treatments` (specs, edges and
   interop pin it); the fixture-only `n-treatment` keeps its interop mapping.
5. **Snooze and pause are device-local** behaviours, named as such in the node's
   own words and gated by `reminders.snooze` / `treatment.pause`. PlantPal has no
   endpoint for either, and pretending otherwise would be a lie on the board.
6. **Reschedule, Abandon plan, Dismiss this problem, Mark as resolved and Email
   digest are not offered on live ids at all.** They survive only in the verbatim
   fixture ACTIONS map. "Mark all read" is kept but is explicitly device-local.
7. **The abandoning sentence is the one verbatim line deliberately not
   reproduced.** The prototype's course note is trimmed after "pause it while you
   are away." because the server has no abandon.
8. **Mermaid step diagrams are not rendered in the atlas** (no mermaid in the
   atlas bundle) and the step body SAYS so — "the diagram for this step opens in
   PlantPal" — rather than dropping `stepDiagramContent` silently (focus-panel
   Law 4: a missing answer is written as missing).
9. **Device state is separate from preferences.** `atlas_device` holds paused
   plan ids, snoozed reminders, remembered treatment ids, last focus and the push
   endpoint, **namespaced by data source** (`{live, mock}`) so mock ids can never
   be fetched against the live backend after a source switch. `atlas_settings`
   holds preferences; `atlas_layout` holds cells/offsets/modes.
10. **Mock mode is an explicit, visible switch**, never inferred from an empty
    live garden. A real day zero prints real zeros, and the e2e asserts no sample
    record appears there (day-zero Law 5).
11. **The stake sheet keeps `role="dialog"`** for consistency with round 1, even
    though `action-rail.html` says a value form should open inside the focused
    card's body. This is flagged here for an owner ruling rather than resolved
    unilaterally; the settings overlay itself contains no `[role=dialog]`.
