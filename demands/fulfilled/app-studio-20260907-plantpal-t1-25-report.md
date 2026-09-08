---
demandId: app-studio-20260907-plantpal-t1-25
worker: plantpal
date: 2026-09-08
status: done
shipped:
  - "commit cb97f33: cross-origin session-handoff characterization and exact-file Jest runner"
  - "8 executed session-handoff specs covering the registered PP-AUTH-003 boundary"
  - "structured frontend JUnit, storage-key mutation, defect-pin, and changed-path evidence"
summaryRef: "commit cb97f33 on feature/PP-099-session-handoff-characterization (T1.25 session-handoff characterization coverage)"
---

# Fulfillment — PlantPal cross-origin session-handoff characterization coverage

## What shipped

`frontend/projects/shared-core/src/lib/session-handoff.spec.ts` now characterizes registered defect
pin `PP-AUTH-003` through the real handoff encoder and consumer. The suite freezes the fragment-only
transport, exact `plantpal_token` / `plantpal_user` storage contract, replayability across two fresh
destination windows, single-address-bar consumption through fragment scrubbing, malformed-input
scrubbing, and the existing Unicode/base-URL behavior. Its expected storage literals are independent
of the implementation constants, and its storage assertions read the actual handoff output under
those literals, so changing an implementation key cannot make both the subject and expectation move
together.

`npm run test:session-handoff` selects that exact spec through
`jest.session-handoff.config.js`, explicitly sets `--passWithNoTests=false`, and emits
`frontend/test-results/session-handoff.xml` through the actual Jest runner. The changed-path fixture
is pinned to dispatch base `6fc08cc` and requires non-empty test, test-configuration, evidence, and
verifier-asset channels while forbidding every production session/auth/Atlas boot path.

## Acceptance evidence

- `cd frontend && npm run test:session-handoff` exits 0. Jest reports 1 selected suite and 8
  passing specs; `npx jest --config jest.session-handoff.config.js --listTests` resolves exactly
  `projects/shared-core/src/lib/session-handoff.spec.ts`.
- `python tools/auth-hardening/verify_frontend_junit_report.py --input
  frontend/test-results/session-handoff.xml --expected-suite session-handoff.spec.ts` exits 0 and
  prints `executed_specs=8`, `failures=0`, `errors=0`, and `skipped=0`.
- Mutation proof: changing only the real implementation's `SESSION_TOKEN_KEY` literal from
  `plantpal_token` to `plantpal_token_mutation` made the named runner exit 1 with 8 executed specs,
  2 failures, and 0 errors. The exact-key assertion received the mutated literal, and both replay
  consumers wrote no value under `plantpal_token`. The runner-generated XML declared 2 failures;
  the frontend XML verifier consumed that report and exited 1 with `failures=2`. The literal was
  restored with no production-source diff, then the named suite and XML verifier passed again.
- `python tools/auth-hardening/verify_allowed_change_paths.py --input . --fixture
  tools/auth-hardening/fixtures/session-handoff-allowed-paths.json` exits 0 with positive test,
  test-configuration, evidence, and verifier-asset counts and
  `production_behavior_file_records=0`. Before this report existed it exited 1 naming this exact
  missing required evidence path, so the evidence channel is not an absence assertion.
- The existing assigned-pin verifier also consumes the generated XML successfully and prints
  `assignment=sessionhandoff`, `executed_pin_count=1`, and `executed_pin=PP-AUTH-003`.
- Regression checks pass: the full frontend Jest suite is 42 suites / 529 tests, and `npm run lint`
  passes both the classic and Atlas projects.

## What the origin must know

The JUnit XML is generated under the ignored `frontend/test-results/` directory, so validation must
run `npm run test:session-handoff` before the XML verifier. `PP-AUTH-003` intentionally documents a
replayable bearer-token handoff; later hardening can replace that mechanism, but should update the
pin and characterization evidence in the same reviewed change.

## Not done / caveats

No production source or authentication behavior changed. The only production edit was the required
temporary storage-key mutation, and it was reverted before the clean acceptance run and commits.
This report's `status: done` is the worker claim required by the demand schema; coordinator
validation and owner approval remain external gates.
