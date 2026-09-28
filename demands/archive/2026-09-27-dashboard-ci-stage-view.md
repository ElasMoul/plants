---
id: plantpal-20260927-dashboard-ci-stage-view
date: 2026-09-27
from: plantpal
to: [dashboard]
capability: "A Jenkins-style stage view on the /ci ship wall: each job's steps rendered as a live stage bar (pending/running/passed/failed/skipped, with durations), driven by the new ci.run steps field."
acceptance-criteria:
  - "dashboard re-pins contracts to the release from plantpal-20260927-contracts-ci-run-steps"
  - "The /ci run-detail drawer (and a compact form on the job row) renders the job's steps in order as stages with state and duration, updating live as in_progress ci.run events arrive"
  - "Events without `steps` render exactly as today (no regression for older producers)"
  - "A failed stage is visually distinct and links to the GitHub Actions job log"
  - "A plantpal `sonar-gate` job is recognisable as the merge gate on the wall (e.g. by job name), with both quality-gate stages visible"
needs-owner: false
status: archived
---

# Demand — CI stage view on `/ci` (layer 2)

## What we need

`/ci` (`CiShipWallComponent`, `ci/ci-job-row.component`, the run-detail
drawer) already shows each job's phase and result from `ci.run`. Once
`contracts` adds the optional `steps[]`/`jobId`
(`plantpal-20260927-contracts-ci-run-steps`) and `ci-runner` sends them
(`plantpal-20260927-ci-runner-ci-run-steps`), `dashboard` needs to:

- Render **stages**. Show a job's steps in order: pending, then running (animated), then
  passed / failed / skipped, with elapsed time per stage. Show them in the drawer,
  plus a compact stage strip on the job row.
- **Update live**. Each new `in_progress` `ci.run` for the same `jobId`
  replaces the previous steps snapshot.
- **Degrade**. An event without `steps` renders as today.
- **Link out** from a failed stage to the GitHub job log. `github-links.ts`
  already builds Actions links.

## Why / what's blocked

The owner asked for a Jenkins-like visual of the D112 `sonar-gate` merge gate
running, alongside the rest of CI, on the platform dashboard rather than only
the verdict in SonarQube's UI. Gate *detail* (which condition failed, new-code
coverage) is out of scope here. That is `sonarqube`'s own build order: the scan
bridge `GET /projects`, then its `contracts` set, then its dashboard demand.

## What we do once closed

Open a PR into `dev` and watch its `sonar-gate` run on `/ci` end to end.
Confirm with the owner that it matches the Jenkins-style ask.
