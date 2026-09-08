---
demandId: app-studio-20260907-plantpal-t1-24
worker: plantpal
date: 2026-09-08
status: done
shipped:
  - "commit af18250: JwtInterceptor concurrent-401 defect-characterization suite and exact-file Jest runner"
  - "1 executed JwtInterceptor spec covering the registered PP-AUTH-002 boundary"
  - "structured frontend JUnit, defect-pin execution, mutation, and changed-path evidence"
summaryRef: "commit af18250 on feature/PP-098-jwtinterceptor-characterization (T1.24 JwtInterceptor characterization coverage)"
---

# Fulfillment — PlantPal JwtInterceptor defect-characterization coverage

## What shipped

`frontend/src/app/core/interceptors/jwt.interceptor.spec.ts` executes the real
`JwtInterceptor` through Angular's dependency-injected HTTP interceptor chain and
`HttpTestingController`. It issues three requests and matches all three while they are still in
flight, then flushes each request with a 401 response. The `PP-AUTH-002` characterization asserts
the pinned current behaviour exactly: all three errors reach their subscribers, and every rejection
independently calls `logout()` and navigates to `/login`.

`npm run test:jwt-interceptor` selects that exact spec through
`jest.jwt-interceptor.config.js`, explicitly sets `--passWithNoTests=false`, and emits
`frontend/test-results/jwt-interceptor.xml` through the actual Jest runner. The T1.23 structural
JUnit and assigned-pin verifiers are reused unchanged. This unit adds its own changed-path fixture,
pinned to dispatch base `95db79b`, requiring non-empty test, test-configuration, evidence, and
verifier-asset channels while forbidding production auth and backend paths.

## Acceptance evidence

- `cd frontend && npm run test:jwt-interceptor` exits 0. Jest reports 1 selected suite and 1
  passing spec; `npx jest --config jest.jwt-interceptor.config.js --listTests` resolves exactly
  `src/app/core/interceptors/jwt.interceptor.spec.ts`, so an empty or unrelated selector cannot
  satisfy the gate.
- `python tools/auth-hardening/verify_frontend_junit_report.py --input
  frontend/test-results/jwt-interceptor.xml --expected-suite jwt.interceptor.spec.ts` exits 0 and
  prints `executed_specs=1`, `failures=0`, `errors=0`, and `skipped=0`.
- `python tools/auth-hardening/verify_pin_execution.py --report
  frontend/test-results/jwt-interceptor.xml --pins docs/auth-hardening/defect-pins.md --fixture
  tools/auth-hardening/fixtures/defect-pin-schema.json --assignment jwtinterceptor` exits 0 and
  prints `executed_pin_count=1` plus `executed_pin=PP-AUTH-002`.
- Mutation proof: a temporary `logoutStarted` boolean on the real interceptor gated the 401 side
  effects after their first execution. With all three requests still entering through the HTTP
  testing controller and receiving 401 responses, `npm run test:jwt-interceptor` exited 1 at the
  exact-count assertion: expected 3 `logout()` calls, received 1. The runner-generated XML declared
  1 test, 1 failure, and 0 errors; the frontend XML verifier consumed that report and exited 1 with
  `failures=1`. The latch was reverted with no production-source diff, then the named suite and both
  positive verifiers were rerun successfully.
- `python tools/auth-hardening/verify_allowed_change_paths.py --input . --fixture
  tools/auth-hardening/fixtures/jwt-interceptor-allowed-paths.json` exits 0 only after this evidence
  report is present. Before the report existed it exited 1 naming that missing required evidence
  path. The final positive output reports non-zero test, test-configuration, evidence, and
  verifier-asset group counts with `production_behavior_file_records=0`.
- Regression checks pass: the full frontend Jest suite is 42 suites / 528 tests, and `npm run lint`
  passes both the classic and Atlas projects.

## What the origin must know

The JUnit XML is generated under the ignored `frontend/test-results/` directory, so validation must
run `npm run test:jwt-interceptor` before either report verifier. `PP-AUTH-002` intentionally freezes
the duplicate side-effect fan-out; a later shared-transition hardening change should update the pin
and its exact-count characterization in the same reviewed change.

## Not done / caveats

No production source or authentication behaviour changed. The only production edit was the required
temporary latch mutation, and it was reverted before the clean acceptance run and commits. This
report's `status: done` is the worker claim required by the demand schema; coordinator validation and
owner approval remain the gate.
