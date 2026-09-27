---
id: plantpal-20260928-dashboard-ci-skipped-not-failed
date: 2026-09-28
from: plantpal
to: [dashboard]
capability: "The /ci wall shows a skipped or cancelled ci.run job as SKIPPED / CANCELLED, not FAILED, so a conditional merge gate that did not apply is never painted red."
acceptance-criteria:
  - "ciRunStatus() maps conclusion `skipped` to a distinct non-failure status (e.g. SKIPPED) and `cancelled` to CANCELLED; `failure` and `timed_out` stay FAILED"
  - "The repo tile's red/green roll-up does not count skipped jobs as failures"
  - "Spec coverage for each conclusion value in the CiRunPayload enum"
  - "Live check: a plantpal push run's `sonar-gate` (skipped by design on push events) renders as skipped on /ci"
needs-owner: false
status: open
---

# Demand — skipped/cancelled CI jobs shown as FAILED on `/ci`

## What we need

`src/app/ci/ci-run-status.ts` collapses every terminal conclusion other than
`success` into `FAILED`:

```ts
return payload.conclusion === 'success' ? 'PASSED' : 'FAILED';
```

`CiRunPayload.conclusion` has five values: `success`, `failure`, `cancelled`,
`skipped` and `timed_out`. Please give `skipped`, and ideally `cancelled`, their
own non-failure display state, and keep them out of the tile's failure roll-up.

## Why / what's blocked

Observed live on 2026-09-27 at 23:05 UTC on `/ci`. plantpal's push run
`36357194630` shows `CI / sonar-gate` and `CI / AI Visual Review (advisory)`
as **FAILED**. GitHub reports both as `completed / skipped`, because both jobs
are PR-only by design. The state feed carries `conclusion: skipped` correctly,
so ci-runner is not at fault.

Every push to a plantpal branch therefore paints the D112 merge gate red next
to the PR run's real verdict. That undermines the point of the "merge gate"
label that the stage-view demand
(`plantpal-20260927-dashboard-ci-stage-view`) added. The stage view itself works:
it was verified live on run `36357199806`, with 17 stages, state and duration.

## What we do once closed

Push a branch, then confirm on `/ci` that the push run's `sonar-gate` shows as
skipped while the PR run's `sonar-gate` shows its real verdict.
