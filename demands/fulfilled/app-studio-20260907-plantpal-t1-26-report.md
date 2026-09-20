---
demandId: app-studio-20260907-plantpal-t1-26
worker: plantpal
date: 2026-09-08
status: done
shipped:
  - "commit 89bf6db: blocking GitHub Actions auth-characterization job and structured report artifact"
  - "GitHub Actions baseline run 34287554317 and restored run 34288307111: successful required jobs with 88/2/1/8 executed cases"
  - "GitHub Actions mutation run 34288104586: unsuccessful Auth Characterization CI conclusion from a changed AuthGuard assertion"
  - "structural CI policy, CI-run JSON/XML, and changed-path verification assets"
summaryRef: "commit 89bf6db on feature/AP-018-make-the-auth-characterization-suites-blocking-ci-evidence (T1.26 blocking auth CI evidence)"
---

# Fulfillment — PlantPal blocking auth characterization CI evidence

## What shipped

`.github/workflows/ci.yml` now runs on every push and pull request without branch or path filters.
The existing full `Backend CI` and `Frontend CI` suites remain unchanged and blocking. A dedicated
`Auth Characterization CI` job runs on `ubuntu-latest`, installs the pinned contracts binding and
frontend dependencies, then executes the four exact runners retained from T1.22–T1.25:

- `mvn -DfailIfNoTests=true -Dtest=ProtectedEndpointAuthTest test --batch-mode --no-transfer-progress`
- `npm run test:auth-guard`
- `npm run test:jwt-interceptor`
- `npm run test:session-handoff`

The later runners use `if: ${{ !cancelled() }}` so every characterization still executes after an
earlier assertion failure while the failed step continues to make the job and workflow
unsuccessful. An `always()` upload step publishes the four XML files as the named
`auth-hardening-ci-reports` artifact and uses `if-no-files-found: error`.

`verify_ci_policy.py` parses the workflow as YAML and consumes `ci-policy.json`. It structurally
checks unfiltered push/pull-request events, the approved Linux runner, blocking job and step fields,
all four exact commands, all four exact report paths, strict missing-file handling, the two retained
full-suite commands, and app-only CI input paths. It does not use a textual denylist.

`verify_ci_run.py` consumes GitHub Actions JSON plus the downloaded XML trees. It requires successful
workflow, `Backend CI`, `Frontend CI`, and `Auth Characterization CI` conclusions; finds exactly one
named JUnit suite in each report; reconciles declared and actual testcase counts; rejects failures,
errors, skips, missing files, empty suites, and malformed inputs; and prints a positive executed
count for every suite. Downloaded evidence and generated local Python bytecode are ignored; the
pre-existing frontend and backend ignore rules already cover their generated XML/build outputs.

## Acceptance evidence

- `python tools/auth-hardening/verify_ci_policy.py --workflow-dir .github/workflows --fixture
  tools/auth-hardening/fixtures/ci-policy.json` exits 0 with `required_runner_steps=4`,
  `required_report_paths=4`, and `required_existing_suite_steps=2`.
- Local exact-runner evidence is clean: `ProtectedEndpointAuthTest` executed 88 tests, AuthGuard 2
  specs, JwtInterceptor 1 spec, and session handoff 8 specs, all with zero failures or errors.
- Baseline push commit `89bf6db` produced GitHub Actions run
  `34287554317`; `gh run watch 34287554317 --exit-status` exited 0. `Backend CI`, `Frontend CI`, and
  `Auth Characterization CI` all concluded `success`, and the auth job's four runner steps plus
  report upload each concluded `success`.
- Downloading `auth-hardening-ci-reports` from run `34287554317`, then piping
  `gh run view 34287554317 --json conclusion,jobs` into `verify_ci_run.py`, exits 0 and prints
  `executed_protected_endpoint_auth_tests=88`, `executed_auth_guard_specs=2`,
  `executed_jwt_interceptor_specs=1`, and `executed_session_handoff_specs=8`.
- Mutation commit `423a95d` changed the signed-out AuthGuard spec's expected serialized route from
  `/login` to `/mutation-login`. The real guard result entered that assertion. GitHub Actions run
  `34288104586` concluded `failure`; its structured job record reports
  `Auth Characterization CI = failure`, and its runner step record reports
  `Run AuthGuard characterization = failure`. The downloaded runner XML contains 2 executed specs,
  1 failure, and 0 errors; the XML verifier exits 1 on that artifact. The subsequent
  JwtInterceptor and session-handoff steps still executed successfully, and the report upload
  succeeded.
- Revert commit `88a9b61` restored the assertion. The exact local AuthGuard runner returned to 2/2
  passing specs, and `git diff 89bf6db..88a9b61 -- frontend/src/app/core/guards/auth.guard.spec.ts`
  is empty. Restored GitHub Actions run `34288307111` concluded `success`; all three required jobs
  concluded `success`, and its downloaded artifact passes `verify_ci_run.py` with the same positive
  `88 / 2 / 1 / 8` counts.
- `python tools/auth-hardening/verify_allowed_change_paths.py --input . --fixture
  tools/auth-hardening/fixtures/ci-allowed-paths.json` exits 0 on the complete dispatched-base diff.
  The fixture routes the decision through Git's changed-file records, requires non-empty CI,
  ignore-rule, verification-asset, and evidence groups, and reports zero production-behavior file
  records. The temporary test assertion has no final diff after its explicit revert.

## What the origin must know

Download the artifact before invoking the required CI-run command:

`gh run download <run-id> --name auth-hardening-ci-reports --dir .auth-hardening-ci-reports`

The workflow now intentionally runs on feature-branch pushes as well as `main`/`dev` and pull
requests because the acceptance criterion requires every push. GitHub emitted only its existing
Node-runtime deprecation advisory for older action majors; it did not affect any required
conclusion or report.

## Not done / caveats

No production source, auth behavior, application configuration, deployment workflow, or
platform-only file changed. The only AuthGuard spec edit was the required temporary mutation; it
was committed, provider-tested, and explicitly reverted. This `status: done` is the worker claim
required by the demand schema; coordinator validation and owner approval remain external gates.
