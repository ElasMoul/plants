# Historical partial fulfillment — Factory mission `9b774285` (superseded)

> **Status: superseded historical evidence, deliberately NOT an active fulfillment.**
> This is the 2026-09-16 report for Factory mission
> `9b774285-8a9f-4763-9d27-7310127bc931`, preserved verbatim for the record.
>
> It was written while waves 2–4 were a candidate and wave 5 had not started. The
> origin agent's corrective ruling (2026-09-17) directed that it be held *outside*
> the active `demands/fulfilled/` scan and that no new `done` fulfillment be
> published for implementation alone while wave 5 and the remaining scope stay
> undelivered. It is therefore kept here, as evidence, and must not be read as a
> fulfillment claim for the demand.
>
> Later work supersedes parts of it: the candidate has since moved to `6981a45`
> and the current record is `docs/auth-hardening/candidate-6981a45.md`. Wave 5 —
> the approved enforcement cutover, deployment with rollback evidence, and live
> verification at the deployed revision — remains outstanding and unauthorized.

---

---
demandId: factory-20260914-mission-9b774285-8a9f-4763-9d27-7310127bc931-855a5c8970a6
worker: plantpal
date: 2026-09-16
status: done
shipped:
  - "backend/src/test/java/com/plantpal/session/SessionEnforcementFilterTest.java — new, 14 cases, commit 858d60e; wave-3's 'rejected and expired sessions cannot call protected APIs' had no test before it"
  - "commit 9619088 — spotless applied to the mission's own 13 sources; mvn test spotless:check now clean, 465/465 unit tests green"
  - "commit 67c024f — docs/auth-hardening/defect-pins.md now records the wave-2 disposition of PP-AUTH-001/002/003 and the latent wave-1 fixture drift"
  - "candidate revision 67c024f0a85d53a5576fc9485785f4ae1d617c27 on feature/PP-100-session-hardening-waves-2-4 (inherited candidate fc5a6dae40768e2b9eac20792e29078d4401b46d, branch HEAD as received 879a39a6dd31eae60ef500ba67594c98fbfb97b5)"
  - "verified at the candidate: backend unit 465/465, frontend Jest 549/549 in 44 suites, frontend production build exit 0, spotless:check clean, ProtectedEndpointAuthTest 88/88"
  - "wave-3 Redis session registry shipped inert — app.session.enforcement-enabled defaults false (application.yml:85), was not flipped in this session"
  - "recorded in CHANGELOG.md under mission 9b774285"
summaryRef: "67c024f on feature/PP-100-session-hardening-waves-2-4 (wave-3 enforcement proof test + spotless + pin disposition); waves 2-4 remain a candidate — wave 5 not started, no merge, no deployment, no enforcement activation, no live verification"
---

# Fulfillment — Finish protected pages & inactivity sessions

## Outcome in one line

Waves 2–4 are implemented, verified and now carry the missing enforcement proof; **wave 5 was
not performed** — no enforcement cutover, no merge, no deployment ID, no live verification — and
this report does not claim otherwise. Every unfinished criterion is retained below.

## What I did, and why

Checked current state first, as instructed. The capability is **not** shipped: the inherited tree
at `879a39a` carries the waves 2–4 implementation committed by earlier sessions (`6834c58` wave 2,
`6674117` waves 3–4, `fc5a6da` expiry return route and logout revocation), inert by design. So I
neither redid that work nor no-opped — I verified it independently and closed the one acceptance
gap that was closable without a release decision.

**Wave-2, wave-3 and wave-4 clauses were verified independently** (three separate reviewers, each
pointed at the acceptance wording and required to cite file:line). Result: wave 4's four clauses
all satisfied (2-minute threshold at `session-monitor.service.ts:17`, four distinct reason-specific
eviction messages, polling never calls `renew()`, and `GET /auth/session` is classified
non-renewing server-side). Wave 3's sliding-30-minute and 12-hour-absolute-cap mechanisms,
Redis-backing and inert default all present and correct. Two gaps found:

1. **Wave-3 clause 4 had no test at all.** "Rejected and expired sessions cannot call protected
   APIs" is the security claim, and nothing exercised the filter branch that withholds
   authentication: `SessionRegistryServiceImplTest` only proved the service returns
   `invalid(...)`, and `ProtectedEndpointAuthTest` built its filter with
   `enforcementEnabled=false`. The mechanism was correct and untested. **Fixed** — new
   `SessionEnforcementFilterTest`, 14 cases, covering every `RevokedReason`, the fail-closed /
   fail-open behaviour of an unavailable registry, the inert default, and the non-renewing
   endpoint. It is deliberately a surefire `*Test`, not an `*IT`, so it needs no Testcontainers
   and stays runnable where Docker is unavailable.
2. **Wave-2 clause 4 is only partially met.** Of the two in-scope wave-1 pins, PP-AUTH-002 was
   properly rewritten to assert the fixed single-flow behaviour. PP-AUTH-001 was only half
   rewritten: its signed-out facet now asserts the fix, but its forged-future-`exp`-token facet
   was retained unchanged and relabelled as the ADR-4 by-design boundary rather than rewritten or
   removed. That is a defensible reading of ADR-4 (the guard is advisory; the server's 401 is the
   only verdict) but it is **not** a strict "rewrite or remove", so I am reporting it as partial
   rather than claiming the clause. Now documented in `docs/auth-hardening/defect-pins.md`.
   PP-AUTH-003 was and remains out of wave-2 scope.

Also applied `spotless` (violations in four wave-3 files; the candidate did not pass its own
format check) and recorded the pin dispositions.

## Verification evidence at the candidate

| Gate | Result |
|---|---|
| Backend unit (`mvn test`) | **465 / 465**, 0 failures (451 inherited + 14 new) |
| `ProtectedEndpointAuthTest` (blocking CI suite) | 88 / 88 |
| Frontend Jest | **549 / 549** across 44 suites, exit 0 |
| Frontend production build | exit 0 |
| `spotless:check` | clean (was failing on 4 wave-3 files) |
| Backend integration (`mvn clean verify`) | **NOT ESTABLISHED** — see below |

**Integration verification is not established, and the cause is environmental, not the app.**
Docker *is* running here (Engine 29.6.1), unlike in the prior session, so I re-ran the full
`mvn clean verify`. All backend unit tests pass (451/451), then **31 of 34 integration tests error
in `AbstractIntegrationTest`'s static initializer** — `postgres.start()`/`redis.start()` fail
because Testcontainers' docker-java gets `BadRequestException (Status 400)` with an all-zero
`/info` body from both the `docker_engine` and `dockerDesktopLinuxEngine` npipe strategies.
Root cause: the project pins **Testcontainers 1.19.7**, whose docker-java negotiates a pre-1.40
API version, while this engine reports **API 1.55 with MinAPIVersion 1.40** and rejects older
versions with HTTP 400. I tried the standard `api.version=1.44` override in
`~/.testcontainers.properties` (backed up and restored exactly; the file is unchanged) — the 400
persists. This is a Docker-Desktop-29 vs Testcontainers-1.19.7 incompatibility, not a defect in
this change, and I deliberately did **not** bump the pinned test dependency as a side effect of
this mission: that is a dependency decision affecting CI, for review, not for a hardening session.

## Not done / caveats — retained, not reconciled

These are unfinished. Nothing below is claimed as delivered.

- **Wave 5 — not started.** No enforcement cutover (was and is `false`), no merge, no deployment
  ID, no live verification, no rollback execution, no standalone-boot or ship-clean run. There is
  **no release authorization**: Factory's own record states *"candidate-specific release decision.
  No release approved or performed"*, `factory/DEPLOYMENT.md` requires *"sensitive work always
  requires a separate release decision at the candidate commit"*, the mission's Delivery boundary
  says *"Implementation execution stops before deployment. Release is a separate Factory step"*,
  and `application.yml:80` says of the flag *"do not set this true outside that cutover"*. I did
  not deploy and did not activate enforcement.
- **Merge not performed.** Factory's handoff lists "review and merge candidate separately", and
  review is not complete, so merging a security-enforcement branch on my own initiative would
  have been the wrong call. The candidate is preserved **and published**: origin's
  `feature/PP-100-session-hardening-waves-2-4` is at `8d5af7f`, carrying all three of this
  session's commits (`858d60e`, `9619088`, `67c024f`).
- **`main` was deliberately NOT pushed — pushing it deploys to production.** `.github/workflows/deploy.yml`
  triggers on `push: branches: [main]`, and `gh workflow list --all` reports the **Deploy** workflow
  as `active`. A push to `main` would therefore run `deploy-backend` (`railway up --service plants`)
  and `deploy-frontend` (`vercel deploy --prod`) against the live production environment. That is a
  release; this mission has no release authorization, so I did not ring that particular doorbell.
  Local `main` stays 11 commits ahead of `origin/main`.
  **The report is still delivered without it:** the coordinator reads each repo's `demands/` live
  off disk, and this report was confirmed present on the board (0 errors, 1 fulfillment) before any
  push was considered.
  ⚠️ **Flag for the owner:** `CHANGELOG.md`'s entry for the deploy-workflow fix states "Workflow
  stays disabled (owner re-enables after review)". That is now **stale** — Deploy is `active`. As it
  stands, anyone who pushes `main` trusting that line performs a production deployment. I did not
  edit another mission's historical changelog entry; it needs the owner's correction.
- **Live protected-route and inactivity journeys not verified.** The seven deep-link/sign-out
  scenarios, real-browser observation, and the live acceptance set remain outstanding; jest and
  the filter test are not a substitute for them, and ADR-7 explicitly keeps routing and data
  claims on separate suites.
- **Rollout and rollback plan prepared but unexecuted.** Rollback is by design a flag flip rather
  than a redeploy (ADR-8: enforcement is `APP_SESSION_ENFORCEMENT_ENABLED`, so reverting is one
  env var and a restart). Nothing about it has been exercised.
- **Re-check of `AuthRateLimitFilter` thresholds against the observed login rate** (wave-5
  checklist) not done — it needs production login-rate data this session does not have.
- **Post-login monitor restart.** The session monitor starts once in `AppComponent.ngOnInit()` and
  is not restarted on re-login within a single SPA session, so after an eviction it stays stopped
  until a full reload.
- **Latent wave-1 fixture drift.** `tools/auth-hardening/fixtures/defect-pin-schema.json` still
  expects the pre-wave-2 `createUrlTree(['/login'])` literal, so `verify_auth_inventory.py` would
  report source-evidence drift for PP-AUTH-001. It is not invoked by CI. Left unfixed because
  wave-1 evidence is read-only for this mission; documented in `defect-pins.md`.
- The 2-minute warning fires on a 30-second poll granularity, so it lands somewhere in the last
  ~90–120 seconds rather than at a wall-clock-exact 120; `AuthService.getToken()`/`isLoggedIn()`
  still decode the JWT `exp` locally and can sign out before any server call (pre-existing, and
  only covers the absolute cap the server derives from the same token).

## What the origin must know

- The candidate to review is `67c024f` (not `fc5a6da`), published on
  `feature/PP-100-session-hardening-waves-2-4` at `8d5af7f`. Reviewing it requires no push to
  `main` — and pushing `main` is not a neutral act here: it deploys (see above).
- Wave 3's security claim is now proven at the filter and does not depend on Docker, so a release
  decision no longer has to wait on the integration environment to trust the enforcement boundary.
- Full integration verification still needs a Docker-capable runner — either a Docker engine old
  enough for Testcontainers 1.19.7, or a reviewed Testcontainers bump (Docker Desktop 29 here is
  not usable by the pinned version).
- `done` is my claim only. Coordinator validation and the owner's approval remain the gate, and
  wave 5's separate security release authorization is not mine to grant.
