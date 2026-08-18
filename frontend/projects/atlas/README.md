# Atlas — the Rhizome frontend

A second PlantPal frontend, served on its own port against the **same backend**.
It reimagines PlantPal as an **atlas-class** interface (the Rhizome design system):
one plane, records are **nodes** on a lattice joined by **veins**, and a **camera
travels** instead of routing. Built alongside the classic app in the same Angular
workspace.

## Run it

```bash
cd frontend
npm run start:atlas          # dev server on http://localhost:4300 (proxies /api → :8180)
npm run build:atlas:prod     # production build → dist/atlas/browser
```

The classic app stays on `:4200`. Both share one login (same-origin in prod).

## Architecture

```
projects/
├── rhizome-engine/   Framework-agnostic TS engine — the computable constitution:
│                     geometry (C7-C9), rank BFS (C3/C12), clearance pass, camera
│                     arc-travel (C10/C11/C13/C21), particle field. Pure, test-first.
├── shared-core/      @plantpal/shared-core — AuthService/UserService/models shared
│                     with the classic app (API_BASE_URL token). Shared JWT session.
└── atlas/            This app. Renders engine state; never re-implements the maths.
    └── src/app/
        ├── world/    WorldStore (signals over the engine), fixture, live assembly
        │             (world-graph.service + world.assembly), interop deep-links.
        ├── node/     rz-node card (kind × rank, degradation states).
        └── core/     atlas-auth.interceptor (shared bearer token).
```

- **Rendering:** `rz-world` is one viewport + one plane transformed by the camera
  signal; nodes are positioned at the engine's clearance targets; veins are SVG with
  invisible 20px clickable hit-strokes; chrome is flush furniture that never moves
  with the plane.
- **Data:** the world is assembled **client-side** from the existing endpoints
  (`/dashboard`, `/plants`, `/species/mine`) — no backend change. Falls back to the
  fixture when unauthenticated or offline; the board never blanks.
- **Degradation is per-node** (C22-C25): skeleton / empty / unknown / failure drawn
  inside the node, never a global spinner or banner.

## The constitution gate

Design tokens are a **pin** — `src/styles/tokens.css` is byte-identical to
`frontend-atlas/theme-a/tokens.css`; `tokens-pin.spec.ts` guards it (never hand-edit;
re-pin instead). `world/constitution.spec.ts` asserts the Rhizome laws (determinism
C7-C9, one plane C1/C4, rank C12, camera-on-polyline C10/C11/C21, degradation
invariance C9/C22-C25) against the real fixture + store + engine.

```bash
npm test                     # all unit + constitution tests (both apps + engine)
npm run typecheck:engine     # strict type-check of rhizome-engine
```

## Deploy

`Dockerfile.atlas` builds this project and serves `dist/atlas/browser` behind the
shared `nginx.conf` (`/api → backend:8080`). Compose service `frontend-atlas`
publishes `8182:80` / `8445:443`.
