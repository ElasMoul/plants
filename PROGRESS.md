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
