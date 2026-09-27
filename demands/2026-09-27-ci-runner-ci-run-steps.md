---
id: plantpal-20260927-ci-runner-ci-run-steps
date: 2026-09-27
from: plantpal
to: [ci-runner]
capability: "state-emitter emits per-step progress on ci.run events — from the workflow_job webhook and by polling the job while it is in progress — so the dashboard can render a live stage view."
acceptance-criteria:
  - "state-emitter re-pins contracts to the release from plantpal-20260927-contracts-ci-run-steps and populates `jobId` and `steps` on every ci.run it emits"
  - "While a job is in_progress, state-emitter polls GET /repos/{repo}/actions/jobs/{jobId} at a bounded interval and emits an in_progress ci.run whenever the steps array changes; polling stops on completion or after a timeout"
  - "Polling is fire-and-forget and never delays or fails webhook handling; with GITHUB_TOKEN unset it degrades to webhook-only steps"
  - "A plantpal sonar-gate run is observable on the state-feed with steps moving through in_progress to completed"
needs-owner: false
status: open
---

# Demand — send CI step progress on `ci.run` (CI stage view, layer 2)

## What we need

`state-emitter/src/webhook-handler.ts` `buildStateEvent()` maps
`workflow_job` to `CiRunPayload` today, but it drops `workflow_job.id` and
`workflow_job.steps`. Once `contracts` publishes the optional fields (demand
`plantpal-20260927-contracts-ci-run-steps`), `state-emitter` needs two changes.

1. **Forward what the webhook already carries.** Set `jobId = workflow_job.id`
   and `steps = workflow_job.steps` (mapped to camelCase) on every event.
2. **Poll for live progress.** GitHub sends `workflow_job` only on
   queued, in_progress and completed, never per step. So a job's steps are only
   fully known when it completes. For a live stage bar:
   - track in-progress jobs;
   - poll the job endpoint with `GITHUB_TOKEN`, every ~5–10 s;
   - emit an `in_progress` `ci.run` only when the steps changed;
   - stop at `completed`, or after a cap such as the job timeout;
   - apply this to jobs from `WEBHOOK_REPOS`.

   Polling must stay off the webhook's response path. The existing HMAC and
   `/build` guards are unchanged.

## Why / what's blocked

The owner wants a Jenkins-style view of the D112 `sonar-gate` merge gate
(checkout → build inputs → backend scan + gate wait → frontend scan + gate
wait) and of every other CI job. The `dashboard` stage view
(`plantpal-20260927-dashboard-ci-stage-view`) has nothing to render until
`ci.run` carries steps.

## What we do once closed

Open a PR into `dev` and confirm that the `sonar-gate` steps show up live on the
`/ci` ship wall. Report anything that looks wrong back as a new demand.
