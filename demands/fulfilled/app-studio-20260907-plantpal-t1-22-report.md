---
demandId: app-studio-20260907-plantpal-t1-22
worker: plantpal
date: 2026-09-08
status: done
shipped: ["commit 0dae354", "ProtectedEndpointAuthTest: 88 denial cases", "JUnit and structural coverage verifiers", "Maven root wrappers and test-scope Surefire evidence configuration"]
summaryRef: "commit 0dae354 on main (full-stack protected-endpoint denial suite and verifier assets)"
---

# Fulfillment — PlantPal protected-endpoint denial suite

## What shipped

`ProtectedEndpointAuthTest` loads the real `SecurityConfig`, `JwtAuthFilter`, and
`AuthRateLimitFilter` around a test-only endpoint sink. It routes every protected backend row in
`docs/auth-hardening/auth-surface-inventory.md` through two HTTP cases: no credentials and a
malformed bearer token. All 44 protected rows produced both required denials, for 88 executed
cases.

The repository-root `mvnw`/`mvnw.cmd` launch the backend Maven project, and the test-scope Surefire
configuration writes structural evidence to
`backend/target/auth-hardening/protected-endpoint-coverage.json`. The two Python verifiers reject
missing/failed JUnit execution and incomplete or non-denied inventory coverage. The allowed-path
fixture pins the dispatched branch base and permits only test, test-build/configuration, verifier,
evidence, fulfillment, and generated session-record paths; production source paths are explicitly
forbidden.

## Acceptance evidence

- `./mvnw -DfailIfNoTests=true -Dtest=ProtectedEndpointAuthTest test` — exit 0 under Git Bash on
  this Windows worker; Maven reported 88 tests, 0 failures, and 0 errors. The companion Windows
  wrapper runs the same `backend/pom.xml` project. Replacing the selector with
  `ProtectedEndpointAuthTestMissing` exited 1 with Surefire's “No tests matching pattern” failure.
- `python tools/auth-hardening/verify_junit_report.py --input
  backend/target/surefire-reports/TEST-ProtectedEndpointAuthTest.xml --expected-suite
  ProtectedEndpointAuthTest` — exit 0; `executed_tests=88`, `failures=0`, `errors=0`.
- `python tools/auth-hardening/verify_protected_endpoint_coverage.py --input
  backend/target/auth-hardening/protected-endpoint-coverage.json --inventory
  docs/auth-hardening/auth-surface-inventory.md` — exit 0; `protected_rows=44`,
  `executed_cases=88`, `credential_cases_per_row=2`. Its deliberately incomplete fixture exited 1
  with 87 missing endpoint/case pairs.
- Mutation proof: temporarily adding a `GET /api/v1/users/me/preferences` `permitAll` matcher to
  `SecurityConfig` made the named Maven suite exit 1. The generated JUnit XML declared 88 tests, 2
  failures, and 0 errors; the two failures were the missing-credentials and malformed-bearer cases
  for that endpoint. The JUnit verifier consumed that mutated XML and exited 1 with
  `failures=2; errors=0`. The matcher was then removed, `git diff --exit-code` confirmed
  `SecurityConfig.java` was restored, and the clean 88-case suite was rerun successfully.
- `python tools/auth-hardening/verify_allowed_change_paths.py --input . --fixture
  tools/auth-hardening/fixtures/backend-denial-allowed-paths.json` — exit 0;
  `production_behavior_file_records=0`. The final delivery reruns this gate after the report and
  generated session handoff are present.
- `./mvnw -DskipTests spotless:check` — exit 0 after formatting the recovered test file.

## What the origin must know

The coverage JSON and Surefire XML are generated build outputs under `backend/target/`; they are
not committed. CI or coordinator validation must run the named Maven suite before the two positive
verifiers so both inputs describe the current checkout. The inventory remains the source of truth:
adding a protected backend row without adding its two executable cases makes the structural
coverage verifier fail.

## Not done / caveats

No production behavior changed. The only production-source edit was the required temporary
SecurityConfig mutation, and it was reverted before the clean acceptance run and commit. This
report is a `done` claim for coordinator/owner validation, not self-approval.
