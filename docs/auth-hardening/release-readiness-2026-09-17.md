# Bounded release-readiness handoff — Factory mission `9b774285`

## Decision boundary

**Not approved for release.** This is a bounded readiness report, not an approval,
merge, deployment, enforcement change, or demand completion. The owner has delegated
approval judgment to the origin; the origin has not approved this candidate.

- Original implementation candidate: `375dfa9e2ae32fb9e3dfc098b08653e283007fd4`.
- Published candidate documentation head: `f77cc4f5617761a190ffed83b8e34be52c296814`.
- Reviewed successor: `d0737114e1fbc386a5aad158e5327559c3254c36`, which contains
  `375dfa9` plus two focused security/SPA fixes (`0e6c9fa`, `d073711`).
- `app.session.enforcement-enabled` stays **false** in shipped configuration.

## New bounded evidence

Raw transient logs are deliberately kept under ignored
`.auth-hardening-ci-reports/release-readiness-20260917/`, outside the active demand
report scan. Every captured exit code is `0` for the passing commands below.

| Evidence class | Result | Scope |
|---|---|---|
| Focused backend | 23 tests passed | `SessionRegistryServiceImplTest`, `SessionEnforcementFilterTest` |
| Focused frontend | 9 tests passed | login/session-monitor regression tests |
| Existing focused frontend | 23 tests passed | return URL, guard, interceptor concurrent-401, login, monitor |
| Browser **stubs** | 1 Chromium test passed | Protected `/garden?from=release-readiness` → eviction → re-login in the same SPA visit (no `page.goto`/reload between eviction and login) → same safe return route → second monitor request. All API calls are explicitly stubbed, so this is not server evidence. |
| Isolated local full stack | passed | Fresh Postgres + Redis, locally built backend, and frontend proxy `localhost:4301 → localhost:18180`; no API stubs. Register 201, login 200, status 200 twice, TTL 1800 → 1798 on two status polls, explicit renew 1800, logout 204, then protected endpoint 401 with `X-Session-Revoked-Reason: LOGOUT`. |

The isolated backend container emitted its revision before startup:
`RELEASE_READINESS_REVISION=0e6c9fae2598370b8bf96de6b940199d24484323 BASELINE=375dfa9e2ae32fb9e3dfc098b08653e283007fd4`.
This is candidate identity evidence, not an inference from an HTTP 200. The image was
then superseded in source by `d073711` for the SPA race correction.

The previously reconciled broad receipts remain authoritative and were not rerun:
`test-results-backend-clean-verify.log` (465 unit, 34 integration, success) and the
existing full Jest/build receipts captured with `375dfa9`.

## Security review and fixes

1. `SessionRegistryServiceImpl.write()` swallowed Redis persistence failures. With a
   future enforcement cutover, a login could therefore mint a JWT without a session
   record and be rejected on its next protected request. `0e6c9fa` now propagates the
   write failure so existing inert callers can deliberately log-and-continue while
   enforced callers fail closed; regression coverage was added.
2. An immediate first monitor poll after login could preserve the transient `/login`
   route instead of the intended safe deep link. `d073711` carries the already
   sanitized destination into the monitor and uses it only as an eviction fallback;
   successful login also clears its loading state so the same SPA visit can re-login.

The existing interceptor suite remains the proportional concurrent-401 proof: exactly
one logout/navigation for concurrent 401s and latch re-arming after navigation. The
server-side non-renewal proof is additionally exercised in the isolated stack TTL check.

## Exact delta against production branch

The release-scope comparison is `origin/main` (`6fc08ccbb8ba6c36d3a862155bb42abf622ba41d`)
to `d073711`. It contains these 77 paths, exactly as returned by
`git diff --name-only origin/main...d073711`:

```text
.brain/events/2026-09-08_2006_fulfill-app-studio-20260907-plantpal-t1.events.jsonl
.brain/events/2026-09-08_2336_fulfill-app-studio-20260907-plantpal-t1.events.jsonl
.brain/events/2026-09-09_2121_fulfill-demand-app-studio-20260909-plant.events.jsonl
.brain/events/2026-09-16_2004_recover-timed-out-factory-mission-9b7742.events.jsonl
.brain/events/2026-09-17_0116_recover-usage-limited-run-18b2cb6f-a6fc.events.jsonl
.brain/sessions/2026-09-08_2006_fulfill-app-studio-20260907-plantpal-t1.md
.brain/sessions/2026-09-08_2336_fulfill-app-studio-20260907-plantpal-t1.md
.brain/sessions/2026-09-09_2121_fulfill-demand-app-studio-20260909-plant.md
.brain/sessions/2026-09-14_1453_owner-requested-continuing-factory-missi.md
.brain/sessions/2026-09-14_1816_fulfill-demand-brain-toolkit-20260914-fl.md
.brain/sessions/2026-09-16_2004_recover-timed-out-factory-mission-9b7742.md
.brain/sessions/2026-09-17_0116_recover-usage-limited-run-18b2cb6f-a6fc.md
.brain/toolkit-pin
.claude/CLAUDE.md
.github/workflows/ci.yml
.gitignore
CHANGELOG.md
PROGRESS.md
backend/pom.xml
backend/src/main/java/com/plantpal/session/config/SessionProperties.java
backend/src/main/java/com/plantpal/session/dto/SessionStatusResponse.java
backend/src/main/java/com/plantpal/session/entity/RevokedReason.java
backend/src/main/java/com/plantpal/session/entity/SessionCheckResult.java
backend/src/main/java/com/plantpal/session/entity/SessionRecord.java
backend/src/main/java/com/plantpal/session/service/SessionRegistryService.java
backend/src/main/java/com/plantpal/session/service/impl/SessionRegistryServiceImpl.java
backend/src/main/java/com/plantpal/session/web/NonRenewingRequestMatcher.java
backend/src/main/java/com/plantpal/session/web/SessionController.java
backend/src/main/java/com/plantpal/shared/config/SecurityConfig.java
backend/src/main/java/com/plantpal/shared/filter/JwtAuthFilter.java
backend/src/main/java/com/plantpal/shared/util/JwtUtil.java
backend/src/main/java/com/plantpal/user/service/impl/UserServiceImpl.java
backend/src/main/resources/application-test.yml
backend/src/main/resources/application.yml
backend/src/test/java/ProtectedEndpointAuthTest.java
backend/src/test/java/com/plantpal/session/SessionEnforcementFilterTest.java
backend/src/test/java/com/plantpal/session/service/SessionRegistryServiceImplTest.java
backend/src/test/java/com/plantpal/user/unit/UserServiceTest.java
demands/fulfilled/app-studio-20260907-plantpal-t1-25-report.md
demands/fulfilled/app-studio-20260907-plantpal-t1-26-report.md
demands/fulfilled/app-studio-20260909-plantpal-record-factory-mission-9636b730-report.md
demands/fulfilled/brain-toolkit-20260914-fleet-repin-v063-report.md
docs/auth-hardening/candidate-375dfa9.md
docs/auth-hardening/defect-pins.md
docs/auth-hardening/evidence/wave-1-defect-pin-history.md
frontend/e2e/journeys/session-monitor-resumption.spec.ts
frontend/jest.session-handoff.config.js
frontend/package.json
frontend/playwright.config.ts
frontend/projects/shared-core/src/lib/session-handoff.spec.ts
frontend/proxy.release-readiness.conf.json
frontend/src/app/app.component.ts
frontend/src/app/core/guards/auth-catch-all.guard.ts
frontend/src/app/core/guards/auth.guard.spec.ts
frontend/src/app/core/guards/auth.guard.ts
frontend/src/app/core/interceptors/jwt.interceptor.spec.ts
frontend/src/app/core/interceptors/jwt.interceptor.ts
frontend/src/app/core/return-url.spec.ts
frontend/src/app/core/return-url.ts
frontend/src/app/core/services/session-monitor.service.spec.ts
frontend/src/app/core/services/session-monitor.service.ts
frontend/src/app/features/auth/auth-routing.module.ts
frontend/src/app/features/auth/login/login.component.spec.ts
frontend/src/app/features/auth/login/login.component.ts
test-results-auth-it-docker-api-144.log
test-results-auth-it-testcontainers-1214.log
test-results-backend-clean-verify.exitcode
test-results-backend-clean-verify.log
test-results-frontend-full-jest.log
test-results-frontend-production-build.log
test-results-playwright-auth-chromium.log
test-results-session-monitor-jest.log
tools/auth-hardening/fixtures/ci-allowed-paths.json
tools/auth-hardening/fixtures/ci-policy.json
tools/auth-hardening/fixtures/session-handoff-allowed-paths.json
tools/auth-hardening/verify_ci_policy.py
tools/auth-hardening/verify_ci_run.py
```

`main` is locally at `f27fb5038c992f02e1e6a36780cb94767c24e7d1` with three
unpublished, unrelated demand/report commits (`b0c4240`, `05a5aef`, `f27fb50`).
They must not be pushed or included in this release.

## Deployment and rollback manifest

| Item | Exact record |
|---|---|
| Prior repository-controlled deployment | GitHub Actions Deploy run `34266531889`, successful 2026-09-08, source `6fc08ccbb8ba6c36d3a862155bb42abf622ba41d`; backend Railway, classic Vercel, and Atlas Vercel jobs all succeeded. |
| Prior artifacts | `backend-jar` id `10072054528` and `frontend-dist` id `10072090311` are expired; `atlas-dist` id `10072089169` remains. Therefore there is no reusable exact prior backend/classic binary artifact. |
| Targets | Railway service `plants`; classic Vercel project/domain `plants.moulworks.com`; enabled Atlas target `plants-atlas.moulworks.com`. |
| Required configuration (names only) | Railway: prod profile, JDBC database/Redis, JWT, CORS, storage/AI/push settings; GitHub: deployment credentials and Vercel project identifiers; non-secret repository variables include `BACKEND_PUBLIC_URL`, `DEPLOY_ATLAS=true`, `CLASSIC_PUBLIC_URL`, `ATLAS_PUBLIC_URL`. No secret values were read or recorded. |
| Safe proposed path | Only after approval: branch from `origin/main`/verified deployed source, cherry-pick the reviewed runtime/test commits rather than merging local `main`; run CI; capture Railway/Vercel deployment IDs and a redacted config-name snapshot; then use the existing Deploy workflow once. Do not fast-forward or push local `main`. |
| Rollback | Immediately restore `APP_SESSION_ENFORCEMENT_ENABLED=false` if it was changed, then redeploy a freshly rebuilt `6fc08cc` backend/classic frontend through the controlled workflow. Because the old backend/classic artifacts are expired and active-provider deployment metadata was not queried, an exact binary rollback cannot currently be specified safely. |

## Pending live verification after explicit Wave 5 authorization

1. Owner/origin approval and a separate Wave 5 enforcement/release decision.
2. Capture the currently active Railway/Vercel deployment IDs and redacted configuration names before rollout.
3. On deployed revisions, test protected deep-link return, concurrent 401 single redirect,
   idle and absolute-cap evictions, explicit renewal, logout revocation, and non-renewing
   background polling; verify the frontend proxy and production correlation logs.
4. Keep enforcement false unless that same approved cutover explicitly changes it.
