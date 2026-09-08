---
demandId: app-studio-20260907-plantpal-t1-23
worker: plantpal
date: 2026-09-08
status: done
shipped:
  - "commit b70452e: AuthGuard defect-characterization suite and exact-file Jest runner"
  - "2 executed AuthGuard specs covering the registered PP-AUTH-001 boundary"
  - "frontend JUnit, defect-pin execution, and changed-path structural verifiers"
summaryRef: "commit b70452e on feature/PP-097-authguard-characterization (T1.23 AuthGuard characterization coverage)"
---

# Fulfillment — PlantPal AuthGuard defect-characterization coverage

## What shipped

`frontend/src/app/core/guards/auth.guard.spec.ts` executes the real `AuthGuard` with the real
shared `AuthService` and Angular Router. Its two tagged cases freeze both sides of registered defect
pin `PP-AUTH-001`: a signed-out protected navigation returns a `/login` `UrlTree`, while a locally
stored forged token with a future `exp` is admitted by the current client-only check. The latter is
characterization of the documented defect, not an endorsement or security fix.

`npm run test:auth-guard` selects that exact spec through `jest.auth-guard.config.js`, explicitly
sets `--passWithNoTests=false`, and emits `frontend/test-results/auth-guard.xml` through the actual
Jest runner. The new Python verifiers consume the JUnit suite/testcase/conclusion elements and the
registered pin table plus schema fixture. The allowed-path verifier now supports required exact
paths and required changed-file groups; this unit's fixture requires non-empty test, test
configuration, evidence, and verifier-asset channels from the pinned dispatch-base diff.

## Acceptance evidence

- `cd frontend && npm run test:auth-guard` exits 0. Jest reports 1 selected suite and 2 passing
  specs; `npx jest --config jest.auth-guard.config.js --listTests` resolves exactly
  `src/app/core/guards/auth.guard.spec.ts`, so the suite cannot pass through an empty or unrelated
  selector.
- `python tools/auth-hardening/verify_frontend_junit_report.py --input
  frontend/test-results/auth-guard.xml --expected-suite auth.guard.spec.ts` exits 0 and prints
  `executed_specs=2`, `failures=0`, `errors=0`, and `skipped=0`.
- `python tools/auth-hardening/verify_pin_execution.py --report
  frontend/test-results/auth-guard.xml --pins docs/auth-hardening/defect-pins.md --fixture
  tools/auth-hardening/fixtures/defect-pin-schema.json --assignment authguard` exits 0 and prints
  `executed_pin_count=1` plus `executed_pin=PP-AUTH-001`. It first rejects unsuccessful JUnit
  conclusions, then requires the assigned registered pin and assignment tags in an executed
  testcase.
- Mutation proof: changing only AuthGuard's signed-out return from the `/login` `UrlTree` to
  `true` made `npm run test:auth-guard` exit 1. The runner-generated XML declared 2 tests, 1
  failure, and 0 errors; the failed testcase was the signed-out `/garden` `PP-AUTH-001` case, whose
  assertion received `true` instead of a `UrlTree`. The frontend XML verifier consumed that report
  and exited 1 with `failures=1`. The mutation was reverted with no diff remaining in
  `auth.guard.ts`, then the named suite and both positive verifiers were rerun successfully.
- `python tools/auth-hardening/verify_allowed_change_paths.py --input . --fixture
  tools/auth-hardening/fixtures/auth-guard-allowed-paths.json` exits 0 only after this evidence
  report is present. Before the report existed it exited 1 naming the missing required evidence
  path. The final positive output reports non-zero test, test-configuration, evidence, and
  verifier-asset group counts with `production_behavior_file_records=0`.
- Regression checks also pass: the full frontend Jest suite is 41 suites / 527 tests, and
  `npm run lint` passes both the classic and Atlas projects.

## What the origin must know

The JUnit XML is generated under the ignored `frontend/test-results/` directory, so validation must
run `npm run test:auth-guard` before either report verifier. `PP-AUTH-001` remains an intentionally
registered current-behaviour pin: subsequent hardening may reject forged or revoked tokens, but it
must update the pin and characterization evidence in the same reviewed change.

## Not done / caveats

No production source or authentication behaviour changed. The only production edit was the
required temporary mutation, and it was reverted before the clean acceptance run and commits. This
report's `status: done` is the worker claim required by the demand schema; coordinator validation
and owner approval remain the gate.
