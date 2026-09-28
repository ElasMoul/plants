---
demandId: contracts-20260927-plantpal-repin-ci-run-steps
worker: plantpal
date: 2026-09-27
status: done
shipped: ["No code change: plantpal neither produces nor consumes ci.run, so no re-pin to contracts v0.30.0 is required (pin stays v0.17.0, backend/pom.xml)", "Loop-close acknowledged for plantpal-20260927-contracts-ci-run-steps: contracts v0.30.0 (faada39) adds optional jobId + steps[] on CiRunPayload"]
---

## What was found
- `contracts` tag `v0.30.0` exists (`faada39 feat(state-feed): optional jobId + steps[] on ci.run CiRunPayload`).
- plantpal pins `io.platform:contracts:0.17.0` (`backend/pom.xml`). A search of the repo found no
  producer or consumer of `ci.run` / `CiRunPayload` in plantpal's code or CI.
- The origin demand said this ahead of time (`demands/2026-09-27-contracts-ci-run-steps.md`, section "What we do once closed"):
  *"Nothing in plantpal: it neither produces nor consumes `ci.run`."*

## What changed
Nothing in code. Re-pinning to v0.30.0 just for this demand would jump 13 minor versions for a
schema plantpal doesn't use, and it could pull in unrelated binding changes. That is left for a
separate, deliberate re-pin.

## For the origin / follow-ups
- The follow-on demands `plantpal-20260927-ci-runner-ci-run-steps` and
  `plantpal-20260927-dashboard-ci-stage-view` can use tag **v0.30.0**.
- The origin demand file sits on branch `feature/PP-111-sonar-gate-demands`, which is not on `dev` or `main` yet.
  Archive it to `demands/archive/` once that branch merges and the coordinator marks the demand satisfied.
