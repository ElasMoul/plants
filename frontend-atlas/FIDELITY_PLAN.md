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
