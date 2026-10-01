---
demandId: factory-20261001-plantpal-review-start-pr-and-branch
worker: plantpal
date: 2026-10-01
status: done
shipped:
  - "tools/dev-delivery/dev_delivery.py: `review start` with REVIEW_PR_NUMBER and REVIEW_BRANCH both in the environment (no flags) resolves the PR by number and verifies REVIEW_BRANCH against the PR head branch (`resolve_review_target(..., expected_branch=)`); a mismatch is refused with 'PR #N head branch is X, not the expected Y' (exit 2)"
  - "Explicit flags still win: --pr and --branch together on the command line stay refused ('give exactly one of --pr or --branch'); a --branch flag ignores an environment PR number; --pr with env REVIEW_BRANCH treats the branch as the expectation"
  - "tools/dev-delivery/test_dev_delivery.py: 4 new tests (env both set == --pr alone, expected-branch accept/mismatch at resolve level, mismatch refused from the command, both CLI flags refused); suite 101 pass"
  - "DEPLOYMENT.md: documented the both-variables behaviour (review-apps.json entry is the plain argv already listed, no env-scrubbing wrapper); CHANGELOG [Unreleased] entry"
---

# Report: review start accepts REVIEW_PR_NUMBER + REVIEW_BRANCH

## State check
Not previously shipped: with both variables set and no flags, `review start` exited 2 with
'give exactly one of --pr or --branch' (the prior env-param work only covered one at a time).

## Verified
- `python -m unittest discover -s tools/dev-delivery`: 101 tests pass (gh/docker mocked).

## Not done / not verified
- No live run against a real PR through the launcher; the owner can now drop any env-scrubbing wrapper from review-apps.json and confirm on a real PR.
