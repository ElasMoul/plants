# plantpal — Progress (platform-delta sessions)

> Platform-delta work only — PlantPal's own feature work continues in `.claude/STATE.md`.
> Compacted 2026-07-16: all but the 3 most recent entries were moved to
> `docs/archive/PROGRESS-2026-07-04_2026-07-15.md`, **re-sorted into true chronological
> order** (this file's prior order was non-monotonic — 2026-07-15 entries had been split
> across both the top and the bottom of the file; entry dates were reconstructed from
> `git log` commit timestamps where a heading itself carried no date). Full history is
> also in git log.

## Current state (as of 2026-07-16)

- PlantPal is a live, already-deployed product (Railway backend + Vercel frontend, per
  `HEXAGON.md`) — the platform observes it via `app.health`; it does not gate PlantPal's
  releases.
- AI vision/identification calls can route through `ai-gateway` when the `platform`
  Spring profile is active — added for `ANTHROPIC_CLAUDE`/`PLANTNET`-identify in Chunk 3
  (2026-07-04), extended to full provider coverage (remaining vision preferences +
  PlantNet auxiliary endpoints) 2026-07-10 (PP-088). Gated off by `@DefaultValue
  enabled=false` and never active in Railway prod (D009 loose coupling).
- Dockerized backend reaches platform services (ai-gateway, state-feed) via
  `host.docker.internal:<port>` — sanctioned as the platform's tenant-app networking
  convention in D045 (2026-07-15, closing the D040 demand — see the kept entry below).
- State-feed emitter (`com.plantpal.statefeed`) has emitted `app.status`/`activity.count`
  (dashboard activity) since 2026-07-09 (PP-083), fire-and-forget, gated by the same
  `platform` profile.
- `contracts` pin is **v0.7.0** (java binding — confirmed directly against both
  `backend/pom.xml` and `HEXAGON.md` frontmatter this session). Note: a v0.5.0 figure
  floated for this bullet is stale — that was the pin briefly on 2026-07-04, before the
  0.5.1 (2026-07-07) and 0.7.0 (2026-07-09) re-pins; trusting the repo's own primary
  sources over the stale figure.
- Native dev port move (backend 8080→8180) was evaluated and **parked** 2026-07-14 —
  `server.port` doubles as the dockerized container's internal bind port and touches
  Railway's prod binding assumption, so it wasn't the "trivial config" case; native `mvn
  spring-boot:run` still needs `--server.port=8180` (or a proxy repoint).
- Feature work (Phases 0–10 + DEPLOY) runs entirely on the `.claude/STATE.md`/
  `TASK_PLAN.md` system; this file logs platform-delta work only.

---

## Session — 2026-07-15 (identification AI-JSON fence-parsing bugfix)
- State: Fixed the markdown-fence JSON parsing bug in `IdentificationServiceImpl`
  (backend/src/main/java/com/plantpal/identification/service/impl/IdentificationServiceImpl.java)
  found live via a real identification (fenced ```json response silently fell
  back to "Unknown Plant" 0.3). Added a reusable `extractJson()` helper, used by
  all 4 AI-JSON parse sites in the class (identification, care plan, cure
  advice, duplicate-care-card groups). 4 new regression tests added
  (`IdentificationServiceImplTest$AiJsonFenceRecovery`); full backend unit
  suite: 293 tests, 0 failures, 0 errors. Integration tests (`*IT.java`)
  remain box-blocked on this machine (Testcontainers npipe) — not run, per
  standing note.
- Next step: none required — fix is self-contained and covered by unit tests.
  Consider (not done here) also checking whether other repos/clients rely on
  DeepSeekClient.stripThinkTags() fence-handling that the ai-gateway
  passthrough path bypasses.
- Standing: integration tests remain box-blocked on this Windows machine
  (Testcontainers npipe) — unchanged.
- Vault-sync: none — plantpal-local quality fix.

---

## 2026-07-15 — tenant-app networking demand closed (D045 minted), archived

**Context:** session-start demand check (`GET /satisfied/plantpal` on the demand-coordinator,
`localhost:8082`) showed the D040 tenant-app-networking demand
(`demands/2026-07-15-platform-vault-tenant-app-networking-d040.md`) FULFILLED AND
OWNER-APPROVED by `platform-vault`. Ruling: option 1 — `host.docker.internal:<port>` sanctioned
as THE tenant-app → platform-service address for local-first dev (D045, new decision entry;
`PLATFORM_ARCHITECTURE.md` §11 now documents it with the example
`PLATFORM_GATEWAY_URL=http://host.docker.internal:8085`; D040 cross-references D045). No
`runtime` change needed, no follow-up work generated — PlantPal's own earlier fix (defaulting
platform-profile URLs to `host.docker.internal`) *is* the platform convention now. Archived the
demand file to `demands/archive/`; nothing else owed.

**Handoff:** demand loop fully closed, no code changes this session. Next: resume Phase 10 /
DEPLOY work per `.claude/STATE.md`.

---

## 2026-07-15 — gateway max_tokens truncation bug fixed (identification fallback root cause)

**Context:** live bug — gateway-routed identification JSON was truncated mid-array (fence-fix
from a prior session was already working; this was pure truncation) because ai-gateway's
`AnthropicAdapter` defaults `max_tokens` to 2048 when the request context has no `maxTokens`,
silently halving the direct-client budget of 4096. Fixed in `IdentificationServiceImpl`: added
`GATEWAY_MAX_TOKENS = 4096` and `GATEWAY_IDENTIFICATION_MAX_TOKENS = 8192` (main identify+care-plan
request only — bigger payload), wired into `identificationGatewayRequest`, the cure-advice
reasoning request, and `runAnnotation`. PlantNet's gateway passthrough untouched (REST proxy, not
LLM-capped). 69/69 unit tests pass (`IdentificationServiceImplTest`, 3 new/extended
`context.maxTokens >= 4096` assertions); integration tests skipped (Testcontainers npipe,
box-blocked on Windows). Vault-sync: none — plantpal-local, no spec/decision change.

**Possible future platform demand (not raised — just noting per instructions):** ai-gateway's
`AnthropicAdapter` 2048-token default is low enough to silently truncate any structured-JSON
consumer that forgets to set `context.maxTokens` explicitly — worth a platform-level discussion
about raising the default or having the gateway reject/flag responses that hit the token cap
instead of returning a truncated body. Not raised as a demand this session; PlantPal's own fix
covers its call sites.

**Handoff:** identification/cure-advice/annotation gateway paths now request 4096–8192 tokens;
committed and pushed to `main`. Next: resume Phase 10 / DEPLOY work per `.claude/STATE.md`.

---

## 2026-07-16 — docs compaction (platform cleanup wave)

- State: Compacted `PROGRESS.md` (was 66KB / 15 entries, non-monotonic order — 2026-07-15
  entries sat at both the top and bottom of the file). Reconstructed true chronological
  order using `git log` commit timestamps for the 3 headings that carried no date of
  their own; moved the 12 oldest entries verbatim to
  `docs/archive/PROGRESS-2026-07-04_2026-07-15.md`, kept the 3 newest here. Also dated
  `CHANGELOG.md`'s one undated heading (`[Unreleased]` → confidently attributable via
  `git blame` to the 2026-07-04 Chunk-0 bootstrap commit) and synced
  `.claude/BACKEND.md`/`.claude/FRONTEND.md`'s "Current Status" sections from
  `.claude/STATE.md` (both were badly stale — Jul-6 and Jul-10 snapshots still showing
  Phase 5/7 as next, while STATE.md already has Phases 0–10 and DEPLOY(code) complete).
- Next step: none required — documentation-only pass, part of a platform-wide docs
  cleanup wave. Resume Phase DEPLOY (account-side setup) per `.claude/STATE.md`.
- Standing: the `.claude/` agent-doc system (STATE.md/TASK_PLAN.md/CLAUDE.md) remains
  the live memory layer for PlantPal's own feature work, untouched by this session except
  the two "Current Status" sections named above.
- Vault-sync: none.

## Session 1 (2026-07-30)

Full narrative, decisions, and context trail: `.brain/sessions/2026-07-30_0254_i-reverted-all-the-wave-7-changes-the-st.md`
(the session file is the source of truth in this D052-piloted repo -- the block
below is a **generated projection** of it, produced mechanically by
agent-runner's dispatch supervisor from an auto-drafted, unconfirmed close --
not a second hand-written account, and not yet reviewed by a human or the
worker agent itself).

- State: I REVERTED ALL THE WAVE 7 CHANGES ( THE STAGE PAGE ) and i want you to bump plantpal to contract v0.17.0 -- status: done (close: auto-drafted, unconfirmed).
- Next step: Review this session's auto-drafted close (`.brain/sessions/2026-07-30_0254_i-reverted-all-the-wave-7-changes-the-st.md`) and confirm or correct it.
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none
- Session: 2026-07-30_0254_i-reverted-all-the-wave-7-changes-the-st

## Session 2 (2026-07-31)

Full narrative, decisions, and context trail: `.brain/sessions/2026-07-31_2303_fleet-re-pin-to-brain-toolkit-v0-6-2-ver.md`
(the session file is the source of truth in this D052-piloted repo -- the block
below is a **generated projection** of it, produced mechanically by
`brain session close`, not a second hand-written account).

- State: fleet re-pin to brain-toolkit v0.6.2 verification -- status: done.
- Next step: See the session file's `## Log` for open follow-ups.
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none
- Session: 2026-07-31_2303_fleet-re-pin-to-brain-toolkit-v0-6-2-ver

## Session 3 (2026-07-31)

Full narrative, decisions, and context trail: `.brain/sessions/2026-07-31_2302_you-are-the-plantpal-hexagon-s-agent-a-d.md`
(the session file is the source of truth in this D052-piloted repo -- the block
below is a **generated projection** of it, produced mechanically by
agent-runner's dispatch supervisor from an auto-drafted, unconfirmed close --
not a second hand-written account, and not yet reviewed by a human or the
worker agent itself).

- State: You are the plantpal hexagon's agent. A demand raised by platform-vault has been dispatched to you. Work it in your own repo only, per your CLAUDE.md standing orders and ../DEMAND_SYSTEM.md. When done, write demands/fulfilled/<demand-id>-report.md and commit+push it. FRONTMATTER RULES -- the coordin... -- status: done (close: auto-drafted, unconfirmed).
- Next step: Review this session's auto-drafted close (`.brain/sessions/2026-07-31_2302_you-are-the-plantpal-hexagon-s-agent-a-d.md`) and confirm or correct it.
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none
- Session: 2026-07-31_2302_you-are-the-plantpal-hexagon-s-agent-a-d

## Session 4 (2026-07-31)

Full narrative, decisions, and context trail: `.brain/sessions/2026-07-31_2341_commit-the-v0-6-2-re-pin-handoff-you-are.md`
(the session file is the source of truth in this D052-piloted repo -- the block
below is a **generated projection** of it, produced mechanically by
`brain session close`, not a second hand-written account).

- State: Commit the v0.6.2 re-pin handoff. You are the plantpal hexagon's agent. A demand from platform-vault has been dispatched to you. Work it in your own repo only. THE WHOLE JOB: your working tree is dirty with a modified .brain/sessions/<...>.md and a modified PROGRESS.md, left by tonight's re-pin run.... -- status: partial.
- Next step: See the session file's `## Log` for open follow-ups.
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none
- Session: 2026-07-31_2341_commit-the-v0-6-2-re-pin-handoff-you-are

## Session 5 (2026-08-02)

Full narrative, decisions, and context trail: `.brain/sessions/2026-08-02_0046_read-only-answer-these-questions-about-p.md`
(the session file is the source of truth in this D052-piloted repo -- the block
below is a **generated projection** of it, produced mechanically by
agent-runner's dispatch supervisor from an auto-drafted, unconfirmed close --
not a second hand-written account, and not yet reviewed by a human or the
worker agent itself).

- State: read-only: answer these questions about plantpal's character and intended audience from its code/docs/README; write nothing. 1. What is this app's core character/personality, in a short paragraph? 2. Who is this app's actual audience -- register, vocabulary, and context of use -- not just its subjec... -- status: done (close: auto-drafted, unconfirmed).
- Next step: Review this session's auto-drafted close (`.brain/sessions/2026-08-02_0046_read-only-answer-these-questions-about-p.md`) and confirm or correct it.
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none
- Session: 2026-08-02_0046_read-only-answer-these-questions-about-p

## Session 6 (2026-08-16)

Full narrative, decisions, and context trail: `.brain/sessions/2026-08-16_2325_write-copy-the-design-system-snapshot-at.md`
(the session file is the source of truth in this D052-piloted repo -- the block
below is a **generated projection** of it, produced mechanically by
agent-runner's dispatch supervisor from an auto-drafted, unconfirmed close --
not a second hand-written account, and not yet reviewed by a human or the
worker agent itself).

- State: WRITE: copy the design-system snapshot at this absolute host path into plantpal's own checkout, at this absolute target path (create it if it does not exist): plantpal/frontend-atlas/design-system Source snapshot (read-only, copy its full contents, do not modify it): C:/Users/pc/Desktop/platform/des... -- status: done (close: auto-drafted, unconfirmed).
- Next step: Review this session's auto-drafted close (`.brain/sessions/2026-08-16_2325_write-copy-the-design-system-snapshot-at.md`) and confirm or correct it.
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none
- Session: 2026-08-16_2325_write-copy-the-design-system-snapshot-at

## Session 7 (2026-08-16)

Full narrative, decisions, and context trail: `.brain/sessions/2026-08-16_2328_read-only-answer-these-questions-about-p.md`
(the session file is the source of truth in this D052-piloted repo -- the block
below is a **generated projection** of it, produced mechanically by
agent-runner's dispatch supervisor from an auto-drafted, unconfirmed close --
not a second hand-written account, and not yet reviewed by a human or the
worker agent itself).

- State: read-only: answer these questions about plantpal's interface from its code/docs -- data outputs, workflows, settings, and any spec detail that helps -- write nothing. 1. plantpal's self-description exposes '/api/v1/plants/**' as a wildcard family with no per-endpoint detail -- which endpoints, paylo... -- status: done (close: auto-drafted, unconfirmed).
- Next step: Review this session's auto-drafted close (`.brain/sessions/2026-08-16_2328_read-only-answer-these-questions-about-p.md`) and confirm or correct it.
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none
- Session: 2026-08-16_2328_read-only-answer-these-questions-about-p

- State: Atlas second frontend (Rhizome atlas-class, design-studio mission cae942ee) fully integrated on main -- workspace 16->20, rhizome-engine + shared-core libs, atlas app (:4300 dev / :8445 compose, same backend), classic-login Atlas checkbox with cross-origin session handoff, live data spine (plants/species/identifications + polling), all buttons wired (forms/POST/PUT/retry/health), constitution gate 164 tests -- status: done.
- Next step: run the mission's coverage-gate against the shipped round-1 spine; round 2 = care loop (/care, /reminders, /treatment-plans as one slice per coverage-scope notes).
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none (spec-plantpal delta, if any, goes via demand to platform-vault)
- Session: 2026-08-17/18 atlas integration (A-H)

- State: PlantPal IS LIVE IN PRODUCTION (2026-08-24) -- backend https://plants-production-e331.up.railway.app (health UP), frontend https://plants.moulworks.com (Vercel, /api + /photos proxied). First-ever prod boot took 6 layered fixes: deploy.yml Node 18->20 + /photos rewrite; .dockerignore JAR exception; .railwayignore + JAR at snapshot root (railway up filters by .gitignore); Railway service Root Directory cleared; DATABASE_URL rewritten to jdbc form via Railway references (user had concatenated old+new -- fixed in dashboard); LocalFileStorageService @Profile("!prod") removed (prod had NO storage bean, never booted before). BACKEND_PUBLIC_URL repo variable needed https:// scheme (fixed via gh). Vercel config: legacy builds+routes (@vercel/static) -- immune to framework autodetect -- status: done.
- Next step: user-side cleanups -- disconnect Railway service git source (doomed git builds still fire on pushes); Vercel Ignored Build Step = exit 0; optional Railway volume at /tmp/plantpal/photos (photos ephemeral + 30d Redis cache); optional atlas second Vercel project (DEPLOY_ATLAS=true + VERCEL_PROJECT_ID_ATLAS + ATLAS_PUBLIC_URL/CLASSIC_PUBLIC_URL); VAPID_PUBLIC_KEY secret if push wanted. Then: register beta account, run one full identification E2E.
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none
- Session: 2026-08-24 vercel-railway production deploy (H10-H17)

- State: Atlas rounds 2 & 3 built on `atlas/R2-care-loop` (base dev = main 1c1c444): care loop (/care, /reminders, /treatment-plans + treatments as courses with per-step stakes, per-plant vitals), round 3 (Today from /dashboard, notifications + push knock, account + AI preferences), a zero-backend MOCK GARDEN (?mock=1|garden|day-zero|outage, in-memory backend behind an HttpInterceptorFn + MockAuthService swap, every stake mutates in place), nine settings panes with 45 parametrised choices (SettingsStore in localStorage `atlas_settings`, live apply, Save/Cancel/Reset, boot restore), insertion-stable layout (C8, `atlas_layout`), atlas lint target, Playwright atlas harness (36 e2e walks in mock mode, own config on :4300). 22 commits, +12k lines; gates: 431 jest tests, lint both projects, engine typecheck, both prod builds (atlas 537 kB, budget raised to 600 warn/800 error). Docs: FIDELITY_PLAN Phase I, STATE.md, CLAUDE.md build row, FRONTEND.md atlas inventory -- status: done, PR to dev pending merge.
- Next step: merge the PR into dev (CI now runs on dev); run the atlas locally with `npm run start:atlas` and open http://localhost:4300/?mock=1 (no backend, no login); owner rulings still open: stake sheet keeps role=dialog (FIDELITY_PLAN decision 11), n-ask/chat stays deferred (last round per coverage scope), first real push subscription needs VAPID_PUBLIC_KEY in the atlas environment.
- Standing: TODO -- no repo convention recorded yet (seeded 2026-07-23 by brain-toolkit bin/adopt v0.4.0)
- Vault-sync: none (mission cae942ee coverage-gate round 2/3 report is a design-studio concern, not a vault delta)
- Session: 2026-09-03/04 atlas R2/R3 care loop + mock garden + settings (workflow-orchestrated, S0-S8)
