# Waves 2–4 candidate — `6981a45` (current)

Review handoff for Factory mission `9b774285-8a9f-4763-9d27-7310127bc931` at the
current branch head. Like `candidate-375dfa9.md`, this is **not** a completion,
release, merge, deployment, or enforcement claim. It supersedes that document as
the current candidate record.

## Candidate identity

- Revision: `6981a45` — *test(auth): align wave-2 evidence and secret scanning*.
- Branch: `feature/PP-100-session-hardening-waves-2-4`, published — the local
  branch and `origin/` are level (0 ahead / 0 behind) at this revision.
- `app.session.enforcement-enabled` remains **`false`** (`application.yml`); it was
  not flipped in producing this candidate.

## Receipts recorded at this exact revision

Recorded separately, as the demand's acceptance criteria require, and re-measured
at `6981a45` rather than inherited from an earlier revision's receipts.

**Continuous integration.** Both required workflows pass at this revision:

| Workflow | Run | Result | Duration |
|---|---|---|---|
| CI (full backend `verify` + frontend suite + auth characterization) | `35437826917` | success | 6m53s |
| Secret Scanning | `35437826943` | success | 7s |

The preceding revision `f2f5a44` had a **red** CI run (`35167709234`): the
Testcontainers correction's new registry test carried an unformatted import order
and one over-long assertion, and `spotless:check` is lifecycle-bound in
`backend/pom.xml`, so the build failed at the check goal even though every test
passed. `a75070a` is the formatting-only fix; `6981a45` is the first green head
since. That failure and its correction are recorded here rather than dropped.

**Local focused re-verification at `6981a45`** (this session, fresh processes,
retained under the ignored `.auth-hardening-ci-reports/session-20260919/`):

| Scope | Result | Exit |
|---|---|---|
| `ProtectedEndpointAuthTest` | 88 passed, 0 failures/errors/skips | 0 |
| `SessionEnforcementFilterTest` | 14 passed, 0 failures/errors/skips | 0 |
| `SessionRegistryServiceImplTest` (6 nested classes) | 9 passed, 0 failures/errors/skips | 0 |
| Frontend session specs — `session-monitor.service`, `jwt.interceptor`, `auth.guard`, `return-url`, `login.component` | 26 passed across 5 suites | 0 |

**Wave-2 return-destination coverage added in this pass.** Reviewing the acceptance
clause *"a safe local destination is restored after login"* against the suite found it
was **not** asserted: `login.component.spec.ts` covered only the no-`returnUrl`
default, `router.navigate(['/garden'])`. The restore path — `navigateByUrl(returnUrl)`
plus handing the same destination to the restarted monitor — was exercised by no test
at all, even though it is the clause's whole subject. Two cases were added: a valid
`/garden/42` is restored (and `navigate` is *not* called), and an unsafe
`https://evil.tld/steal` falls back to `/garden`. The first is confirmed
non-vacuous by mutation: forcing the component back to an unconditional
`navigate(['/garden'])` fails exactly that case and leaves the other two passing
(`1 failed, 2 passed`), and the source was restored byte-identical afterwards.

## What waves 2–4 claim at this revision

- **Wave 2.** Signed-out protected deep links land on `/login` carrying a validated
  `returnUrl`; the unknown-path catch-all makes the same decision in one hop instead
  of two (`auth-catch-all.guard.ts`); concurrent protected-API 401s produce exactly
  one sign-out and one navigation, with the latch re-arming afterwards; both
  in-scope wave-1 defect pins are rewritten to assert the corrected behaviour, with
  the original characterizations preserved immutably in
  `evidence/wave-1-defect-pin-history.md`.
- **Wave 3.** A Redis-backed, server-authoritative registry keyed by the JWT `jti`,
  with a 30-minute sliding record and a signed 12-hour absolute cap, reason-specific
  revocation, and an inert default. `SessionEnforcementFilterTest` is the proof that
  the security clause — *rejected and expired sessions cannot call protected APIs* —
  holds at the filter, which had no test before it.
- **Wave 4.** The client treats `GET /auth/session` as its only expiry truth; that
  endpoint peeks rather than slides, so background polling cannot extend a session;
  the warning threshold is 120 s; renewal happens only on explicit user action; and
  eviction surfaces a reason-specific message.

## Boundary — what is still not done

Wave 5 is **not** delivered and is **not** authorized. There is no release decision:
the mission's recorded plan approval carries `autoRelease: false` and the note that
*"candidate-specific security release decision remains separate"*. Consequently no
enforcement cutover was performed, no deployment ID exists, no rollback evidence was
executed, and no live journey was verified at a deployed revision.

`main` was deliberately not pushed. `.github/workflows/deploy.yml` triggers on
`push: branches: [main]` and the Deploy workflow is active, so a push to `main`
would run `railway up` and `vercel deploy --prod` against production — a release,
which this mission is not authorized to perform.

Also outstanding: the origin agent's review of this candidate, and the owner's
separate wave-5 enforcement/release decision that must precede any merge, cutover,
or flag change.

## Rollout and rollback boundary

Unchanged from `candidate-375dfa9.md`. Enforcement is a configuration flag, so
rollback is a flag flip plus restart: restore `APP_SESSION_ENFORCEMENT_ENABLED=false`
and redeploy the prior approved image/configuration. Because the inert code path
preserves pre-wave JWT behaviour, that restores the previous session semantics while
an incident is investigated. None of this has been exercised, because no cutover has
been authorized.
